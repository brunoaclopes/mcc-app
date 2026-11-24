# Releases, images and CI

How the apps and the server images get built and published. Most of it is automatic; a maintainer only bumps
versions and pushes a tag.

## What runs, and when

| Workflow | When | What it does |
|---|---|---|
| [CI](../.github/workflows/ci.yml) | every pull request and push to `master` | Kotlin tests (link, machine, recipe, common), machine and phone builds, recipe template and server tests |
| [Release](../.github/workflows/release.yml) | a tag `vX.Y.Z` on `master` | the tests again, both APKs signed, a GitHub Release with the APKs, `SHA256SUMS.txt`, the signing fingerprints and notes from the merged pull requests |
| [Images](../.github/workflows/images.yml) | push to `master` that touches `server/` or the recipe tools, a tag `vX.Y.Z`, every Monday, or by hand | `ghcr.io/brunoaclopes/mcc-server` and `ghcr.io/brunoaclopes/mcc-recipe-server` for `linux/amd64` and `linux/arm64` |
| [Dependabot](../.github/dependabot.yml) | weekly | one pull request per ecosystem (Gradle, GitHub Actions, Docker base images) |

### Image tags

| Tag | Points to |
|---|---|
| `latest` | the newest build from `master`, rebuilt every week |
| `0.7.0`, `0.7` | the release with that version tag |
| `sha-<commit>` | one exact build |

The weekly build starts from scratch, so the server always has a recent yt-dlp (YouTube changes often) and current
Alpine security fixes. Servers that use `latest` get it by pulling the image again (TrueNAS shows an update).

Packages of a private repository are private. After the repository goes public, make each package public once:
GitHub → your profile → Packages → the package → Package settings → Change visibility.

## Versions

Each app has its own version in its build file; the release tag names the release as a whole.

| What | Where | Example |
|---|---|---|
| Machine app | `app/build.gradle.kts`: `versionName`, `versionCode` | 1.1, 5 |
| Phone app | `phone/build.gradle.kts`: `versionName`, `versionCode` | 0.7.0, 18 |
| Server | `server/app.py`: `VERSION` | 2.0.0 |
| Release | the git tag | `v0.7.0` |

Rules: raise `versionCode` by one for every release of that app (Android refuses an update with a lower or equal
code), and use [semantic versions](https://semver.org) for the tag. An app that didn't change keeps its version;
the release still carries its APK.

## Cutting a release

1. On a branch: bump the versions of what changed, and note anything people must do (a new setting, a reinstall)
   in the pull request description. Merge it once CI is green.
2. Tag `master` and push the tag:

       git checkout master && git pull
       git tag -a v0.7.0 -m "v0.7.0"
       git push origin v0.7.0

3. Watch Actions → Release. When it is done, the release page has `mcc-machine-<version>.apk`,
   `mcc-phone-<version>.apk` and `SHA256SUMS.txt`. Images → the same version tag appears on both images.
4. Check the release notes and edit them if a pull request title says too little.

A tag that is not on `master`, or a release without the release key set up, fails before anything is published.
To redo a failed release: delete the tag (`git push origin :v0.7.0` and `git tag -d v0.7.0`) and any half-made
release, fix, and tag again.

## Signing

| App | Key | Why |
|---|---|---|
| Machine (`mcc.app`) | the public AOSP platform test key in [`signing/`](../signing) | The machine's firmware is signed with it, so the app may run as the system user and open the serial port. Public on purpose; no secret. |
| Phone (`io.github.brunoaclopes.mcc.phone`) | the maintainer's release key | Android only installs an update signed with the same key as the installed app. |

Builds without the release key (anyone's own builds, CI on pull requests) sign the phone app with the local debug
key. They work, but can't update an app installed from a release, and the other way round: uninstall first.

### The release key

Created once with [`tools/make-release-key.sh`](../tools/make-release-key.sh) (RSA 4096, valid 30 years). The
script never prints the passwords. It writes:

| File | Content |
|---|---|
| `~/.mcc-release/mcc-phone-release.jks` | the key (PKCS12) |
| `~/.mcc-release/keystore.properties` | its passwords; `phone/build.gradle.kts` reads it, so local release builds are signed like GitHub's |
| `~/.mcc-release/FINGERPRINT.txt` | the certificate's SHA-256 fingerprint (public) |

and sets these secrets on the repository's `release` environment, which only `v*` tags may use (on plans without
environments for private repositories, as repository secrets):

| Secret | Value |
|---|---|
| `MCC_KEYSTORE_BASE64` | the `.jks` file, base64 |
| `MCC_KEYSTORE_PASSWORD`, `MCC_KEY_PASSWORD` | its password (the same for both in PKCS12) |
| `MCC_KEY_ALIAS` | `mcc-phone` |

**Keep two copies outside the computer**: the `.jks` file and `keystore.properties` in a password manager, and the
whole `~/.mcc-release` folder on an offline backup. There is no way to recover a lost key: a new key means every
user has to uninstall and reinstall the phone app.

Re-sending the secrets (e.g. after deleting them), from the folder:

    base64 -w0 ~/.mcc-release/mcc-phone-release.jks | gh secret set MCC_KEYSTORE_BASE64 --env release
    # MCC_KEYSTORE_PASSWORD, MCC_KEY_PASSWORD and MCC_KEY_ALIAS: from keystore.properties, the same way

Anyone can check that an APK is the maintainer's: the release notes list the certificate fingerprint, and

    apksigner verify --print-certs mcc-phone-0.7.0.apk

must show the same `SHA-256 digest`. Obtainium and AppVerifier can pin it.

### Google Play Protect

Phones with Google services may warn when installing an app that is not from Play. Registering the package name
and the release key with Google's Android developer verification (a free limited-distribution account covers up to
20 devices) makes the app known to Google; it is optional and not needed for the app to work.

## CI on your fork

Forks run CI as is. The Release workflow needs the four secrets above with your own key; the Images workflow
publishes to your own `ghcr.io/<you>/…`.
