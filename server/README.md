# mcc-app server

A small server for your own network that the apps use for two things:

- **Recipe uploads** (`POST /v1/recipes`): the phone imports a recipe with the AI, in Portuguese and English, and
  sends it here. The server checks it, adds it to **your private recipe repository** (made from
  [`recipe-repo/`](../recipe-repo)), pushes, and optionally redeploys your recipe server. The phone then finds the
  recipe in `index.json` and opens it on the machine.
- **Video transcripts** (`POST /v1/transcript`): many cooking videos have no recipe in the description, so the AI
  needs what is said in the video. The server reads the video's subtitles with [yt-dlp](https://github.com/yt-dlp/yt-dlp),
  or, without subtitles, sends the audio to a speech-to-text server (Home Assistant's Whisper add-on works).

It never runs the machine. It needs write access to your recipe repository (a deploy key), so the phones don't.
Setup guide for the whole system: [docs/SERVER-SETUP.md](../docs/SERVER-SETUP.md).

## Endpoints

All replies are JSON. Errors are `{"error": "…"}` with a short message the apps show to the user.

| Request | Auth | Replies |
|---|---|---|
| `GET /health` (and `/`) | none | `200 {"ok": true, "version": "…", "transcripts": {"whisper": true}}` |
| `POST /v1/recipes` | Bearer token | the contract in [docs/RECIPE-UPLOAD.md](../docs/RECIPE-UPLOAD.md): `201 {"key", "ids"}`, `401`, `409` key exists, `413` over `MCC_MAX_BODY`, `422` invalid |
| `POST /v1/transcript` | Bearer token | below |

### Transcripts

    POST /v1/transcript
    Authorization: Bearer <token>
    {"url": "https://www.youtube.com/watch?v=…"}

YouTube (watch, `youtu.be`, Shorts) and the other sites yt-dlp supports. The reply:

    200 {"title": "…", "author": "…", "description": "…", "language": "pt", "transcript": "plain text",
         "source": "subtitles" | "whisper" | "none", "thumbnail": "https://…"}

| Code | Meaning |
|---|---|
| `401` | missing or wrong token |
| `422` | not a single public video, or yt-dlp can't read it |
| `504` | it took longer than `MCC_TRANSCRIPT_TIMEOUT` |

How the transcript is found, in order:

1. **Subtitles.** Subtitles written by people before automatic ones, and the video's own language before a
   translation (then Portuguese, then English). They come back as plain text, without timestamps, repeated lines or
   `[Music]` notes. `"source": "subtitles"`.
2. **Speech-to-text.** No subtitles and `MCC_WHISPER_HOST` set: the audio only (at most `MCC_MAX_AUDIO_MINUTES`)
   is converted to 16 kHz mono 16-bit PCM with ffmpeg and sent to the Wyoming server. The audio is deleted right
   after. `"source": "whisper"`.
3. **Description only.** `"source": "none"`, `"transcript": ""`.

The description, title and author come back in every case. Results are cached per video for `MCC_CACHE_HOURS` (a
speech-to-text failure is not cached, so a later try can still work). One transcription runs at a time; another
request waits for it.

## Settings

Environment variables; copy [`.env.example`](.env.example) to `.env`.

| Setting | Default | |
|---|---|---|
| `MCC_TOKEN` | (required) | The token the apps send. Make one with `openssl rand -base64 32`. |
| `MCC_TOKEN_SHA256` | | Instead of `MCC_TOKEN`: its SHA-256 hex digest (`printf %s "<token>" \| sha256sum`), so the server never stores the token itself. |
| `MCC_REPO_URL` | | Your recipe repository over SSH, e.g. `git@github.com:<you>/<your-recipes>.git`. Cloned into `MCC_REPO_DIR` on first start. |
| `MCC_REPO_DIR` | `$MCC_DATA_DIR/repo` | The server's own clone. It resets it to the remote branch before and after every upload, so don't edit it by hand. |
| `MCC_GIT_BRANCH` | `main` | |
| `MCC_GIT_NAME`, `MCC_GIT_EMAIL` | `mcc-upload`, `mcc-upload@localhost` | Author of the server's commits ("Add <title> (phone import)"). |
| `MCC_SSH_KEY`, `MCC_SSH_KNOWN_HOSTS` | `/ssh/id_ed25519`, `/ssh/known_hosts` | The deploy key and the host keys it may connect to (below). |
| `MCC_DEPLOY_CMD` | (none) | Run with `sh -c` in the repository folder after each push, in the background (below). |
| `MCC_DATA_DIR` | `/data` | Writable: the clone, the transcript cache and temporary audio. |
| `MCC_PORT` | `8080` | |
| `MCC_MAX_BODY` | `1048576` | Largest request, in bytes. |
| `MCC_WHISPER_HOST` | (none) | Wyoming speech-to-text server. Empty = skip speech-to-text. |
| `MCC_WHISPER_PORT` | `10300` | |
| `MCC_WHISPER_LANGUAGE` | (none) | e.g. `pt`. Empty = the video's language when yt-dlp knows it, else the server's own setting. |
| `MCC_MAX_AUDIO_MINUTES` | `30` | Longer videos without subtitles get the description only. |
| `MCC_TRANSCRIPT_TIMEOUT` | `600` | Seconds per transcript request, waiting included. |
| `MCC_CACHE_HOURS` | `24` | |
| `MCC_YTDLP_UPDATE` | `0` | `1` = install the newest yt-dlp into `$MCC_DATA_DIR/pip` at every start (below). |

## Run it with Docker Compose

    cd server
    cp .env.example .env          # fill in MCC_TOKEN and MCC_REPO_URL at least
    mkdir -p data ssh             # data must be writable by MCC_UID:MCC_GID (default 1000:1000)
    # put the deploy key and known_hosts in ./ssh (next section)
    docker compose up -d --build

The container runs as an ordinary user with a read-only filesystem and no capabilities. It has no access to
Docker or the host, which matters because it holds a token that people on your network use.

## The deploy key

The server pushes to your recipe repository with an SSH key that can write to **that repository only**:

    ssh-keygen -t ed25519 -N "" -C "mcc-server" -f ssh/id_ed25519
    ssh-keyscan -t ed25519 github.com > ssh/known_hosts   # compare with GitHub's published fingerprints

On GitHub: the recipe repository → Settings → Deploy keys → Add deploy key → paste `ssh/id_ed25519.pub` → tick
**Allow write access**. GitHub lists its host key fingerprints at
<https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints>
(or take the keys from `https://api.github.com/meta`). The key files must be readable by the container's user.

## Deploying after an upload

After each push the server runs `MCC_DEPLOY_CMD` (if set), so the recipe appears where the apps look for it. The
phone polls `<recipe server>/index.json` for up to 5 minutes after an upload.

- **Your web server serves a clone directly**: leave it empty, or `git -C /path/seen/by/the/server pull`.
- **The recipe server is a container** (the recipe repository's `docker compose up -d --build`): the upload
  container can't do that itself, by design. Let it leave a note and have the host act on it:

      MCC_DEPLOY_CMD=touch /data/deploy-requested

  and on the host, a cron job every minute (root, or a user allowed to run your deploy):

      #!/bin/sh
      FLAG=/path/to/server/data/deploy-requested
      exec 9>/run/mcc-deploy.lock; flock -n 9 || exit 0
      [ -e "$FLAG" ] || exit 0
      rm -f "$FLAG"
      cd /path/to/recipe-server && git pull -q && docker compose up -d --build

  Nothing is read from the note, so the container can only cause that fixed command to run.

## On TrueNAS SCALE

1. Datasets, e.g. `<pool>/apps/mcc-server/config` (the deploy key and `known_hosts`, mode 700) and
   `<pool>/apps/mcc-server/data` (writable), owned by `apps` (568:568).
2. Build the image on the NAS with `APP_UID`/`APP_GID` 568, so the container's user exists (ssh needs that):

       git clone <mcc-app> && cd mcc-app/server
       sudo docker build --build-arg APP_UID=568 --build-arg APP_GID=568 -t mcc-server:<version> .

3. Apps → Discover → Custom App → **Install via YAML**:

       services:
         mcc-server:
           image: mcc-server:<version>
           pull_policy: never
           restart: unless-stopped
           user: "568:568"
           ports: ["<nas-ip>:8080:8080"]
           read_only: true
           tmpfs: ["/tmp:uid=568,gid=568,mode=1700"]
           cap_drop: [ALL]
           security_opt: ["no-new-privileges=true"]
           environment:
             MCC_TOKEN_SHA256: "<sha256 of the token>"
             MCC_REPO_URL: "git@github.com:<you>/<your-recipes>.git"
             MCC_DATA_DIR: /data
             MCC_SSH_KEY: /config/ssh/id_ed25519
             MCC_SSH_KNOWN_HOSTS: /config/ssh/known_hosts
             MCC_DEPLOY_CMD: "touch /data/deploy-requested"
             MCC_WHISPER_HOST: "<home-assistant-ip>"
           volumes:
             - /mnt/<pool>/apps/mcc-server/config:/config:ro
             - /mnt/<pool>/apps/mcc-server/data:/data

4. System → Advanced → Cron Jobs: the deploy script above every minute, as root.

## Home Assistant's Whisper add-on

The add-on only listens inside Home Assistant until you open its port: Settings → Add-ons → Whisper →
Configuration → Network → `10300/tcp` = `10300` → Save → restart the add-on. Then `MCC_WHISPER_HOST` is Home
Assistant's address. Transcribing takes roughly as long as the add-on's model and hardware need for the length of
the video; raise `MCC_TRANSCRIPT_TIMEOUT` for a slow machine.

## Test it

    TOKEN=<token>; SERVER=http://<server-ip>:8080
    curl -s $SERVER/health
    curl -s -X POST $SERVER/v1/transcript -H "Authorization: Bearer $TOKEN" \
         -H "Content-Type: application/json" -d '{"url": "https://www.youtube.com/watch?v=<id>"}'
    curl -s -o /dev/null -w "%{http_code}\n" -X POST $SERVER/v1/recipes -d '{}'     # 401 without the token

A full upload test is a JSON file with `{"key": "web-test", "recipes": [<pt recipe>, <en recipe>]}` (ids
`web-test-pt` and `web-test-en`):

    curl -s -X POST $SERVER/v1/recipes -H "Authorization: Bearer $TOKEN" \
         -H "Content-Type: application/json" --data @upload.json        # 201, then a second time: 409

Remove a test recipe with a normal commit to your recipe repository (`git rm recipes/web-test-*.json`,
`python3 tools/build_index.py`, commit, push); the server has no delete endpoint.

## Keeping yt-dlp working

YouTube changes often and yt-dlp follows. The image installs the newest yt-dlp when it is built, so rebuilding
updates it (`docker compose build --pull && docker compose up -d`). With `MCC_YTDLP_UPDATE=1` the container also
installs the newest one into the data folder at every start, so a restart is enough. yt-dlp is used as published:
no cookies, no account. A video that needs a login answers `422`.

## Development

    cd server
    python3 -m unittest          # validation, transcript fallbacks, the Wyoming client; no network needed

`app.py` (HTTP), `upload.py` (recipe uploads), `transcript.py` (subtitles and speech-to-text), `wyoming.py`
(Wyoming client), `settings.py` (environment), `common.py`.
