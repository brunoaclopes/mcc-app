#!/usr/bin/env bash
# Creates the phone app's release signing key, once, and gives it to GitHub's Release workflow.
# Maintainers only; see docs/RELEASING.md. Never prints the passwords.
#
#   bash tools/make-release-key.sh [owner/repo]
#
# Makes ~/.mcc-release/ with
#   mcc-phone-release.jks    the key (RSA 4096, valid 30 years)
#   keystore.properties      its passwords, read by phone/build.gradle.kts for local release builds
#   FINGERPRINT.txt          the certificate's SHA-256 fingerprint (public)
# and sets the secrets MCC_KEYSTORE_BASE64, MCC_KEYSTORE_PASSWORD, MCC_KEY_ALIAS and MCC_KEY_PASSWORD on the
# repository's "release" environment (or on the repository when the plan has no environments).
set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
DIR="$HOME/.mcc-release"
JKS="$DIR/mcc-phone-release.jks"
ALIAS="mcc-phone"
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

command -v "$KEYTOOL" >/dev/null || { echo "keytool not found: set JAVA_HOME to a JDK" >&2; exit 1; }
command -v gh >/dev/null && gh auth status >/dev/null 2>&1 || { echo "gh is not logged in (gh auth login)" >&2; exit 1; }
if [ -e "$JKS" ]; then
  echo "$JKS already exists. This script never replaces a release key: a new key means no app update" >&2
  echo "can be installed over the old one. To only re-send the secrets, see docs/RELEASING.md." >&2
  exit 1
fi

umask 077
mkdir -p "$DIR"
# PKCS12 keystores use one password for the store and the key
MCC_PW="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 40)"
export MCC_PW
"$KEYTOOL" -genkeypair -keystore "$JKS" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 \
  -validity 10950 -dname "CN=mcc-app phone app, O=mcc-app" -storepass:env MCC_PW -keypass:env MCC_PW >/dev/null 2>&1
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=%s\nkeyPassword=%s\n' "$JKS" "$MCC_PW" "$ALIAS" "$MCC_PW" > "$DIR/keystore.properties"
FP="$("$KEYTOOL" -list -v -keystore "$JKS" -storepass:env MCC_PW | sed -n 's/^[[:space:]]*SHA256: //p' | head -1)"
echo "$FP" > "$DIR/FINGERPRINT.txt"

# the "release" environment: secrets only for workflows running on a v* tag
WHERE=(--env release)
if gh api -X PUT "repos/$REPO/environments/release" --input - >/dev/null 2>&1 <<'JSON'
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
then
  gh api -X POST "repos/$REPO/environments/release/deployment-branch-policies" -f name='v*' -f type=tag >/dev/null 2>&1 || true
  echo "Environment \"release\" on $REPO: only v* tags can use its secrets."
else
  WHERE=()
  echo "This plan has no environments for private repositories: using repository secrets instead."
fi

base64 -w0 "$JKS" | gh secret set MCC_KEYSTORE_BASE64 --repo "$REPO" "${WHERE[@]}"
printf %s "$MCC_PW" | gh secret set MCC_KEYSTORE_PASSWORD --repo "$REPO" "${WHERE[@]}"
printf %s "$MCC_PW" | gh secret set MCC_KEY_PASSWORD --repo "$REPO" "${WHERE[@]}"
printf %s "$ALIAS" | gh secret set MCC_KEY_ALIAS --repo "$REPO" "${WHERE[@]}"
unset MCC_PW

cat <<EOF

Release key created and given to GitHub.
  Key:          $JKS
  Passwords:    $DIR/keystore.properties
  Fingerprint:  $FP

Now, before anything else:
  1. Put mcc-phone-release.jks and keystore.properties in your password manager.
  2. Copy the folder $DIR to an offline backup (a USB stick).
  If this key is lost, no update can ever be installed over the phone app again.
EOF
