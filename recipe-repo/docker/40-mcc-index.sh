#!/bin/sh
# Runs before nginx starts (nginx image's /docker-entrypoint.d). Serves a recipe folder: the one mounted at
# MCC_RECIPES (/recipes), or, with MCC_GIT_URL, a clone of your recipe repository that it pulls itself.
# Writes index.json and keeps it up to date: every MCC_REINDEX_SECONDS (0 = only at start) it pulls (git mode),
# re-validates, and replaces index.json only when the recipes are valid, so a broken recipe never reaches the machine.
set -eu
SRC="${MCC_RECIPES:-/recipes}"
WEB=/usr/share/nginx/html
STAGE=/tmp/mcc-index
BRANCH="${MCC_GIT_BRANCH:-main}"

if [ -n "${MCC_GIT_URL:-}" ]; then
  SRC=/srv/recipes
  # a read-only deploy key for private repositories: /ssh/id_ed25519 and /ssh/known_hosts
  if [ -f /ssh/id_ed25519 ]; then
    install -m 600 /ssh/id_ed25519 /tmp/mcc-key
    GIT_SSH_COMMAND="ssh -i /tmp/mcc-key -o IdentitiesOnly=yes -o BatchMode=yes"
    if [ -f /ssh/known_hosts ]; then
      GIT_SSH_COMMAND="$GIT_SSH_COMMAND -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/ssh/known_hosts"
    fi
    export GIT_SSH_COMMAND
  fi
  if [ ! -d "$SRC/.git" ]; then
    echo "mcc: cloning $BRANCH of the recipe repository"
    git clone -q --depth 1 --branch "$BRANCH" "$MCC_GIT_URL" "$SRC" || { echo "mcc: git clone failed" >&2; exit 1; }
  fi
fi

pull() {
  [ -n "${MCC_GIT_URL:-}" ] || return 0
  git -C "$SRC" fetch -q --depth 1 origin "$BRANCH" && git -C "$SRC" reset -q --hard FETCH_HEAD \
    || echo "mcc: git pull failed, serving what is there" >&2
}

if [ ! -d "$SRC/recipes" ]; then
  echo "mcc: $SRC/recipes not found: mount your recipe folder at $SRC (see recipe-repo/README.md)" >&2
  exit 1
fi
mkdir -p "$SRC/images" 2>/dev/null || true
ln -sfn "$SRC/recipes" "$WEB/recipes"
[ -d "$SRC/images" ] && ln -sfn "$SRC/images" "$WEB/images"
mkdir -p "$STAGE"
ln -sfn "$SRC/recipes" "$STAGE/recipes"
# the photos, for their blurred placeholders (computed once, then kept in $STAGE/.mcc-blurhash.json)
[ -d "$SRC/images" ] && ln -sfn "$SRC/images" "$STAGE/images"

index() {
  if python3 -I /opt/mcc/tools/build_index.py "$STAGE" >/tmp/mcc-index.log 2>&1; then
    # the "generated" time changes on every run; only a change in the recipes replaces the served index
    if [ "$(grep -v '"generated"' "$STAGE/index.json")" != "$(grep -v '"generated"' "$WEB/index.json" 2>/dev/null)" ]; then
      cp "$STAGE/index.json" "$WEB/index.json.new" && mv "$WEB/index.json.new" "$WEB/index.json"
      echo "mcc: $(tail -n 1 /tmp/mcc-index.log)"
    fi
    rm -f /tmp/mcc-index.failed
  else
    # said once per problem, not on every check
    if ! cmp -s /tmp/mcc-index.log /tmp/mcc-index.failed; then
      echo "mcc: recipes not valid, index.json kept as it was:" >&2
      grep -E "INVALID|MISSING" /tmp/mcc-index.log | head -5 | sed 's/^/mcc:   /' >&2 || true
      cp /tmp/mcc-index.log /tmp/mcc-index.failed
    fi
    return 1
  fi
}

# the first index must work, or there is nothing to serve
index || { echo "mcc: fix the recipes above and restart" >&2; exit 1; }
if [ "${MCC_REINDEX_SECONDS:-60}" -gt 0 ]; then
  ( while sleep "$MCC_REINDEX_SECONDS"; do pull; index || true; done ) &
fi
