# Recipe repository

A folder of recipe files that the machine downloads over plain HTTP. Serve **this folder** (or your own copy with
your own recipes) from any static web server, then enter its address in the app under Settings → Recipe server.

```
index.json            list of recipes (generated, don't edit)
recipes/<id>.json     one recipe per file, format mcc-recipe/1 (FORMAT.md)
images/               optional photos, referenced by relative path
converters/           turn other recipe sources into mcc-recipe/1
tools/                build_index.py, fetch_images.py
tests/                python3 -m unittest discover -s tests
```

The sample recipes in `recipes/` (`sample-*`) were written for this project and are MIT-licensed like the rest.

## Adding a recipe

1. **Write it by hand**: copy a `sample-*.json`, change the `id` (the file name must be `<id>.json`), and follow
   [FORMAT.md](FORMAT.md). Steps are `instruction`, `weigh` (with a target in grams) or `machine` (time, speed,
   temperature and reverse per segment).
2. **Or convert it from a website** that embeds schema.org Recipe data (most do):

   ```sh
   python3 converters/web_jsonld.py https://example.org/some-recipe recipes/ --lang en
   ```

   It recognises machine settings written like "20 sec/speed 5", "25 min/100 °C/speed 1", "turbo 2 sec",
   "roast button/3 minutes" or "Varoma" (→ steam). **Read the notes it prints and check every detected step
   before cooking.**
3. Then:

   ```sh
   python3 tools/fetch_images.py     # optional: downloads remote photos into images/ as 640x400 JPEGs (needs Pillow)
   python3 tools/build_index.py      # validates everything and rewrites index.json
   ```

### Recipes from your own machine

`converters/mc2_bundled.py` converts the recipe JSON that ships inside the stock app on **your** machine
(`res/raw*/recipes.json` in the APK) for use on that machine. Those recipes and photos are copyrighted by their
publisher, so **keep the converted files private**. Don't commit them to a public repository; keep a separate
private folder and point the app at it.

## Serving

```sh
python3 -m http.server 8088          # quick test, from this folder
```

For a permanent server, mount this folder read-only into any web server, e.g. `nginx:alpine` at
`/usr/share/nginx/html`. The app only does `GET <base>/index.json`, `GET <base>/recipes/<id>.json` and image
requests. It caches everything and keeps working offline.

## What the machine enforces anyway

Whatever a recipe says, the app refuses speed > 3 with heat or in reverse, more than 130 °C, more than 99 min
(15 min above speed 6), and heat with less than 200 g in the bowl. Only `run` steps are enabled for now; `turbo`,
`knead`, `roast` and `steam` steps are refused until each mode has had a supervised test.
