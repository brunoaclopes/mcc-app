# Server setup: recipes, phone uploads and video import

Everything here is optional. The machine works without a server. Add the parts you want:

| Part | What it gives you | Needs |
|---|---|---|
| **Recipe server** | guided recipes on the machine and the phone | any static web server (Docker image included) |
| **AI server** | importing recipes from web pages, pasted text and videos, on the machine and the phone | any OpenAI-compatible API |
| **Upload service** ([`server/`](../server)) | the phone publishes imported recipes to your recipe server; video transcripts | Docker, a git repository you can push to |
| **Whisper** | transcripts of videos that have no subtitles | a Wyoming Whisper server (Home Assistant add-on or container) |

```
 phone ──share link──► AI server                       (turns the page or transcript into a recipe)
   │
   ├──POST /v1/transcript──► upload service ──► yt-dlp subtitles, or audio ──► Whisper
   │
   └──POST /v1/recipes────► upload service ──► git commit + push ──► your recipes repo
                                    │
                                    └──► redeploy ──► recipe server ◄── GET index.json ── machine, phone
```

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

On any machine with Docker (a NAS, a Raspberry Pi, a home server):

```sh
git clone git@github.com:<you>/my-recipes.git && cd my-recipes
docker compose up -d --build                 # http://<server-ip>:8088/
curl http://<server-ip>:8088/index.json      # should list the recipes
```

The image is about 60 MB: nginx serving `index.json`, `recipes/` and `images/`. The build runs
`tools/build_index.py`, so an invalid recipe or a missing translation fails the build and the old version keeps
running. To update after new recipes: `git pull && docker compose up -d --build`.

- **TrueNAS SCALE**: Apps → Discover → Custom App, or install with the YAML from `docker-compose.yml`, with the
  repository cloned to a dataset and a free host port.
- **No Docker**: run `python3 tools/build_index.py`, then serve the folder with any web server (for a quick test,
  `python3 -m http.server 8088`).

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

The upload service lets the phone add recipes to your repository: it checks them, writes both languages, commits,
pushes and redeploys the recipe server. It also fetches video transcripts for the import. It is in
[`server/`](../server); its README has the full list of settings and a `docker-compose.yml` example.

Everything about running it is in **[server/README.md](../server/README.md)**: the settings (`.env.example`),
Docker Compose, the deploy key with write access, TrueNAS, and how the recipe server gets redeployed after an upload.
In short, you need:

1. **A deploy key** with write access to your recipes repository only (not your personal key).
2. **A token** for the phone: a long random string (`openssl rand -base64 32`), only in the service's `.env` and in
   the phone.
3. **A way to redeploy** the recipe server after a push (`MCC_DEPLOY_CMD`; nothing if a web server serves the clone
   directly).
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
| Upload service | `.env` ([server/README.md](../server/README.md)) | `MCC_TOKEN`, `MCC_REPO_URL` and the deploy key, `MCC_DEPLOY_CMD`, `MCC_WHISPER_HOST` (optional) |

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Machine shows no recipes | wrong address, or `index.json` not reachable: open `http://<server-ip>:8088/index.json` in a browser |
| Recipe server build fails | an invalid recipe or a missing translation: run `python3 tools/build_index.py` in the repository, it names the file |
| Phone: "HTTP 401" when publishing | the upload token in the phone doesn't match the service |
| Phone: "HTTP 422 …" | the service rejected the recipe; the message says why |
| Published, but "not on the server yet" | the push worked but the redeploy didn't: check the redeploy command and the recipe server's logs |
| AI test fails | address must end in `/v1`; check the key and that the phone or machine can reach that host |
| Video import uses only the description | yt-dlp found no subtitles and Whisper isn't set up, or yt-dlp needs updating |
