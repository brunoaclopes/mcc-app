#!/usr/bin/env bash
# Applies the repository's protections (maintainers only; docs/RELEASING.md#repository-protections).
# Safe to run again: it updates what is there. Branch and tag rules and secret scanning need a public repository
# (or GitHub Pro); on a private repository on the free plan those steps say so and the others still apply.
#
#   bash tools/protect-repo.sh [owner/repo]
set -uo pipefail
REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
R="repos/$REPO"
ok()   { echo "  ok    $*"; }
skip() { echo "  skip  $*"; }
try()  { local what="$1"; shift; if out=$("$@" 2>&1); then ok "$what"; else skip "$what: $(echo "$out" | tail -1)"; fi; }

echo "Settings"
try "merge commits only, delete branches after merge, no auto-merge" \
  gh api -X PATCH "$R" -F allow_merge_commit=true -F allow_squash_merge=false -F allow_rebase_merge=false \
    -F delete_branch_on_merge=true -F allow_auto_merge=false --silent
try "Dependabot alerts" gh api -X PUT "$R/vulnerability-alerts" --silent
try "Dependabot security updates" gh api -X PUT "$R/automated-security-fixes" --silent
try "private vulnerability reporting" gh api -X PUT "$R/private-vulnerability-reporting" --silent
try "secret scanning and push protection" gh api -X PATCH "$R" --silent --input - <<'J'
{"security_and_analysis": {"secret_scanning": {"status": "enabled"}, "secret_scanning_push_protection": {"status": "enabled"}}}
J

echo "Actions"
try "only GitHub's, verified creators' and gradle/docker actions; SHA pins required" \
  gh api -X PUT "$R/actions/permissions" --silent --input - <<'J'
{"enabled": true, "allowed_actions": "selected", "sha_pinning_required": true}
J
try "allowed action list" gh api -X PUT "$R/actions/permissions/selected-actions" --silent --input - <<'J'
{"github_owned_allowed": true, "verified_allowed": true, "patterns_allowed": ["gradle/actions/*", "docker/*"]}
J
try "workflows get a read-only token and can't approve pull requests" \
  gh api -X PUT "$R/actions/permissions/workflow" -f default_workflow_permissions=read -F can_approve_pull_request_reviews=false --silent
try "workflows from outside contributors' pull requests wait for approval" \
  gh api -X PUT "$R/actions/permissions/fork-pr-contributor-approval" -f approval_policy=all_external_contributors --silent

echo "Rules"
ruleset() {   # name, JSON: creates the ruleset or updates the one with that name
  local id; id=$(gh api "$R/rulesets" -q ".[] | select(.name == \"$1\") | .id" 2>/dev/null)
  if [ -n "$id" ]; then try "$1 (updated)" gh api -X PUT "$R/rulesets/$id" --silent --input - <<<"$2"
  else try "$1" gh api -X POST "$R/rulesets" --silent --input - <<<"$2"; fi
}
ruleset "master" '{
  "name": "master", "target": "branch", "enforcement": "active",
  "conditions": {"ref_name": {"include": ["~DEFAULT_BRANCH"], "exclude": []}},
  "rules": [
    {"type": "deletion"}, {"type": "non_fast_forward"},
    {"type": "pull_request", "parameters": {"required_approving_review_count": 0, "dismiss_stale_reviews_on_push": false,
      "require_code_owner_review": false, "require_last_push_approval": false, "required_review_thread_resolution": false}},
    {"type": "required_status_checks", "parameters": {"strict_required_status_checks_policy": false,
      "required_status_checks": [{"context": "Apps (Kotlin tests, machine and phone builds)"},
                                 {"context": "Recipe template and server tests"}]}}],
  "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "pull_request"}]}'
ruleset "release tags" '{
  "name": "release tags", "target": "tag", "enforcement": "active",
  "conditions": {"ref_name": {"include": ["refs/tags/v*"], "exclude": []}},
  "rules": [{"type": "creation"}, {"type": "update"}, {"type": "deletion"}],
  "bypass_actors": [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}]}'
