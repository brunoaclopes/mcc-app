# Server setup: recipes, phone uploads and video import

Everything here is optional. The machine works without a server. Add the parts you want:

| Part | What it gives you | Needs |
|---|---|---|
| **Recipe server** | guided recipes on the machine and the phone | Docker (image `ghcr.io/brunoaclopes/mcc-recipe-server`), or any static web server |
| **AI server** | importing recipes from web pages, pasted text and videos, on the machine and the phone | any OpenAI-compatible API |
| **Upload service** ([`server/`](../server)) | the phone publishes imported recipes to your recipe server; video transcripts | Docker (image `ghcr.io/brunoaclopes/mcc-server`), a git repository you can push to |
| **Whisper** | transcripts of videos that have no subtitles | a Wyoming Whisper server (Home Assistant add-on or container) |

```
 phone ──share link──► AI server                       (turns the page or transcript into a recipe)
   │
   ├──POST /v1/transcript──► upload service ──► yt-dlp subtitles, or audio ──► Whisper
   │
   └──POST /v1/recipes────► upload service ──► git commit + push ──► your recipes repo
                                                                               │ git pull, every minute
                                                    machine, phone ──GET──► recipe server
```

Both images are built by this repository's GitHub Actions for `linux/amd64` and `linux/arm64`, tagged `latest`
(rebuilt weekly) and with each release's version ([RELEASING.md](RELEASING.md#image-tags)). Pin a version tag if
you prefer updating by hand.

Keep all of it on your home network. None of these services is meant to be reachable from the internet.

## 1. Your recipes repository

The recipes live in a git repository of your own, made from [`recipe-repo/`](../recipe-repo). Make it **private** if
it will hold recipes you didn't write (converted stock-app recipes, or recipes copied from websites).

```sh
cp -r mcc-app/recipe-repo my-recipes
cd my-recipes
python3 tools/build_index.py                 # validates the samples, writes index.json
git init -b main && git add . && git commit -m "Recipe repository"
# create an empty private repository on GitHub, Gitea, Forgejo… then:
git remote add origin git@github.com:<you>/my-recipes.git
git push -u origin main
```

The format is in [recipe-repo/FORMAT.md](../recipe-repo/FORMAT.md); how to add recipes by hand or with the
converters is in [recipe-repo/README.md](../recipe-repo/README.md). Every recipe needs a Portuguese and an English
file (`<key>-pt.json`, `<key>-en.json`), and the build fails if one is missing.

When the template gets new tools, copy `tools/`, `converters/`, `tests/`, `docker/` and `Dockerfile` from
`mcc-app/recipe-repo/` into your repository. Your `recipes/` and `images/` stay as they are.

## 2. Recipe server

Use the ready-made image **`ghcr.io/brunoaclopes/mcc-recipe-server`** (about 70 MB, for PCs, NAS boxes and
Raspberry Pi). It never contains recipes: it serves yours, checks them with the template's tools and writes
`index.json`. A broken recipe or a missing translation is reported in its log and the last good `index.json` stays,
so the machine never gets a broken recipe. It also adds each photo's blurred placeholder (`blurhash`, FORMAT.md),
worked out once per photo and kept in `/tmp`, so a read-only mount is fine.

**A. It pulls your repository (recommended).** New recipes appear about a minute after they are pushed, also the
ones the phone publishes through the upload service, so nothing has to be rebuilt or redeployed.

For a private repository, make a **read-only** deploy key for the recipe server (GitHub: the repository →
Settings → Deploy keys → Add, without write access):

```sh
mkdir -p mcc-recipes/ssh && cd mcc-recipes
ssh-keygen -t ed25519 -N "" -C "mcc-recipe-server" -f ssh/id_ed25519      # add ssh/id_ed25519.pub as the deploy key
ssh-keyscan -t ed25519 github.com > ssh/known_hosts                        # compare with GitHub's published fingerprints
```

`docker-compose.yml`:

```yaml
services:
  recipes:
    image: ghcr.io/brunoaclopes/mcc-recipe-server:latest
    restart: unless-stopped
    ports: ["8088:80"]
    environment:
      MCC_GIT_URL: git@github.com:<you>/my-recipes.git    # a public repository can use its https:// address, no key
      MCC_GIT_BRANCH: main
      MCC_REINDEX_SECONDS: "60"
    volumes:
      - ./ssh:/ssh:ro
```

**B. It serves a folder** you keep up to date yourself (a clone, a synced folder):

```sh
docker run -d --name recipes --restart unless-stopped -p 8088:80 \
  -v /path/to/my-recipes:/recipes:ro ghcr.io/brunoaclopes/mcc-recipe-server:latest
```

It checks the folder again every 60 s (`MCC_REINDEX_SECONDS`, `0` = only at start).

**C. Recipes built into your own image**: `docker compose up -d --build` in your recipes repository (its
`Dockerfile`). The build fails on an invalid recipe; after new recipes, `git pull && docker compose up -d --build`.

**D. No Docker**: `python3 tools/build_index.py`, then serve the folder with any web server (for a quick test,
`python3 -m http.server 8088`).

Check it: `curl http://<server-ip>:8088/index.json` lists the recipes; `docker logs recipes` shows what was
checked. On **TrueNAS SCALE**: Apps → Discover → Custom App → Install via YAML, with the compose file above and the
`ssh` folder on a dataset.

Then on the **machine**: Settings → **Recipe server** → `http://<server-ip>:8088/` → **Save & sync**.
On the **phone**: Settings → **Recipe server** → the same address.

## 3. AI server

Recipe import needs a chat model behind an OpenAI-compatible API (`GET /v1/models` and
`POST /v1/chat/completions`). The machine and the phone call it directly; the upload service doesn't. Any of these
works:

- **OpenAI** or another hosted provider with an OpenAI-compatible endpoint: address `https://api.openai.com/v1`, a
  model such as `gpt-4o-mini`, and an API key.
- **Your own server**: Ollama (`http://<host>:11434/v1`), LM Studio, vLLM, llama.cpp server, or a proxy such as
  LiteLLM in front of any provider. The key can be empty if your server doesn't ask for one.

Use a capable model: it has to read a whole recipe page or video transcript, convert other machines' settings
("10 min/100 °C/speed 1") and answer in strict JSON. Small local models often get the JSON or the quantities wrong.

Give the machine and the phone **separate keys**, so you can revoke one without the other. The AI only writes
recipes: it can't start the machine, and every recipe goes through the app's limits (`RecipeFit`) before it is
shown.

- **Machine**: Settings → **AI assistant** → address, model, key → **Save & test** (it shows how many models the
  server lists).
- **Phone**: Settings → **Recipe import** → the same three fields → **Save and test**.

## 4. Upload service

The upload service lets the phone add recipes to your repository: it checks them, writes both languages, commits
and pushes. It also fetches video transcripts for the import. Use the ready-made image
**`ghcr.io/brunoaclopes/mcc-server`** (rebuilt every week, so yt-dlp stays current), or build it from
[`server/`](../server).

Everything about running it is in **[server/README.md](../server/README.md)**: the settings (`.env.example`),
Docker Compose, the deploy key with write access and TrueNAS. In short, you need:

1. **A deploy key** with **write** access to your recipes repository only (not your personal key, and not the
   recipe server's read-only one).
2. **A token** for the phone: a long random string (`openssl rand -base64 32`), only in the service's `.env` and in
   the phone.
3. **Nothing to redeploy** when the recipe server pulls the repository itself (option A above): leave
   `MCC_DEPLOY_CMD` empty. With options B or C, `MCC_DEPLOY_CMD` updates the folder or rebuilds the image
   ([server/README.md](../server/README.md#deploying-after-an-upload)).
4. Check it answers: `curl http://<server-ip>:<port>/health`.

The request and response format is in [RECIPE-UPLOAD.md](RECIPE-UPLOAD.md).

Then on the **phone**: Settings → **Recipe import** → **Upload service address** (`http://<server-ip>:<port>/`) and
**Upload token** → **Save and test**.

## 5. Video import and Whisper (optional)

Share a YouTube link (or another video site yt-dlp supports) to the phone app and it imports the recipe from what is
said in the video, not only the description. The upload service gets the text in this order:

1. **Subtitles** with yt-dlp: the uploader's own subtitles first, then YouTube's automatic ones. Fast, and enough
   for most videos.
2. **Whisper**, if the video has no subtitles and you set up a Whisper server: the service downloads the audio only,
   converts it with ffmpeg and sends it over the Wyoming protocol. Takes from a few seconds to a few minutes
   depending on the video length, the model and your hardware.
3. **The description only**, when neither works. Many videos have the recipe there anyway.

Without the upload service, the phone and the machine fall back to reading the video page themselves (title and
description only).

**Whisper with Home Assistant**: install the **Whisper** add-on (Settings → Add-ons), which listens for Wyoming on
port **10300**. In the add-on's configuration:

- **Model**: a bigger model (`small`, `medium`) understands cooking videos (music, fast talk, ingredient names) much
  better than `tiny` or `base`, but is slower on a CPU. Try `small` first.
- **Language**: the language most of your videos are in, or auto-detect if they vary.

Then set `MCC_WHISPER_HOST` (the Home Assistant address) and `MCC_WHISPER_PORT` (10300) in the upload service; make
sure the add-on's port is exposed (add-on → Configuration → Network). Without Home Assistant, run the
`rhasspy/wyoming-whisper` container instead:

```sh
docker run -d --name whisper -p 10300:10300 -v whisper-data:/data rhasspy/wyoming-whisper --model small --language pt
```

The service sends the audio in 60 s pieces, one after the other. If a long video doesn't finish within 10 minutes,
it answers with the start of the transcript and finishes the rest in the background; the phone asks again to get it.
Videos with only music come back as song lyrics or nothing, and the AI is told to ignore that.

yt-dlp has to follow YouTube's changes. If transcripts stop working, rebuild the upload service image to get the
latest yt-dlp, or set `MCC_YTDLP_UPDATE=1` (see `server/README.md`).

## 6. Checklist

| Where | Setting | Value |
|---|---|---|
| Machine | Recipe server | `http://<server-ip>:8088/` |
| Machine | AI assistant | AI address, model, machine key |
| Phone | Recipe server | `http://<server-ip>:8088/` |
| Phone | Recipe import | AI address, model, phone key, upload service address, upload token |
| Recipe server | compose file | `MCC_GIT_URL` and a read-only deploy key (or a mounted folder) |
| Upload service | `.env` ([server/README.md](../server/README.md)) | `MCC_TOKEN`, `MCC_REPO_URL` and a write deploy key, `MCC_WHISPER_HOST` (optional); `MCC_DEPLOY_CMD` only without git mode |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Machine shows no recipes | wrong address, or `index.json` not reachable: open `http://<server-ip>:8088/index.json` in a browser |
| A new recipe doesn't appear | an invalid recipe or a missing translation: `docker logs <recipe server>` names the file (or run `python3 tools/build_index.py` in the repository) |
| Phone: "HTTP 401" when publishing | the upload token in the phone doesn't match the service |
| Phone: "HTTP 422 …" | the service rejected the recipe; the message says why, and the service's log has the same reason after the key |
| Phone: "The upload token is wrong" on a video | the same token mismatch, found when fetching the transcript |
| Published, but "not on the server yet" | the push worked but the recipe server didn't pick it up: its logs (git pull failing: the read-only deploy key), or the redeploy command without git mode |
| AI test fails | address must end in `/v1`; check the key and that the phone or machine can reach that host |
| Video import uses only the description | yt-dlp found no subtitles and Whisper isn't set up, or yt-dlp needs updating |
