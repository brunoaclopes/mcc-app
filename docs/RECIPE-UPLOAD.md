# Recipe upload service (phone → recipe repository)

The phone app imports recipes with the AI server, in both languages, then sends them here. The service ([`server/`](../server))
runs next to the recipe server, because it needs write access to your recipes repository (no repository token on
phones). The phone sets its address and token in Settings → Recipe import. Setup: [SERVER-SETUP.md](SERVER-SETUP.md).

## Request

    POST <address>/v1/recipes
    Authorization: Bearer <token>
    Content-Type: application/json

    {"key": "web-legumes-salteados-no-azeite",
     "recipes": [ <mcc-recipe/1, language "pt", id "<key>-pt">, <mcc-recipe/1, language "en", id "<key>-en"> ]}

- `key`: `web-<slug>` (lower case, a–z 0–9 and `-`). The phone tries `<key>-2`, `<key>-3`… on 409.
- `recipes`: exactly one `pt` and one `en`, same structure (only the words differ). Each one has been parsed and fitted to
  the machine limits by the app (`Recipe.parse`, `RecipeFit`); the service still validates them, like the repo build.
- `image` may be an absolute `http(s)` URL of the source page's photo, or absent. `source` may hold
  `{"kind": "ai-import", "url": "…"}`.

## Responses

| Code | Body | Meaning |
|------|------|---------|
| 201 | `{"key": "…", "ids": ["…-pt", "…-en"]}` | committed; the server is being rebuilt and redeployed |
| 401 | `{"error": "…"}` | missing or wrong token |
| 409 | `{"error": "…"}` | the key already exists |
| 413 | `{"error": "…"}` | body over 1 MB |
| 422 | `{"error": "…"}` | invalid: the message is shown to the user, so make it short and specific |

After a 201, the phone polls `<recipe server>/index.json` every 10 s (up to 5 min) until `<key>-<lang>` is listed,
then sends `refresh` on `mcc/cmd` so the machine re-reads the index, and opens the recipe.

## Video transcripts

`POST <address>/v1/transcript {"url": "…"}` with the same token returns a video's title, description and transcript
(`source`: `subtitles`, `whisper` or `none`; `partial: true` when only the start is done). The full contract is in
[server/README.md](../server/README.md#transcripts).

What the phone does with it:

- Calls it for YouTube links and other video sites (TikTok, Instagram reels, Vimeo, Facebook videos) before asking the
  AI, showing "Reading the video…". It waits up to 660 s, longer than the server's 600 s.
- `partial: true`: asks again (up to twice); the server finishes in the background, so a repeat returns the rest.
  Still partial after that: the review says the end of the recipe may be missing.
- `source: none`: the review says the recipe was made from the title and description only.
- 422 and 504: the server's message is shown as is and the import stops. 401: "the upload token is wrong".
- Server unreachable or any other error: the phone reads the YouTube page itself (title and description), or the link
  as a web page.
