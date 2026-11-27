# mcc-recipe/1

One recipe per JSON file (UTF-8). The machine app reads `index.json` and the files it lists from a
plain HTTP server (any static file server works). The app caches what it downloads in `/sdcard/Android/data/mcc.app/files/recipes-repo/`.

```json
{
  "format": "mcc-recipe/1",
  "id": "sample-vegetable-soup-en",
  "title": "Simple vegetable soup",
  "language": "en",
  "servings": "4 portions",
  "activeMinutes": 15,
  "totalMinutes": 45,
  "difficulty": "easy",
  "tags": ["Soups", "Vegetarian and vegan"],
  "image": "images/vegetable-soup.jpg",
  "imageSource": "https://example.org/photos/soup.jpg",
  "source": { "kind": "original", "ref": null, "url": null },
  "nutritionPerServing": { "kcal": 140, "proteinG": 3, "carbsG": 20, "fatG": 7 },
  "ingredients": [
    { "group": null, "items": [ { "text": "1 onion (100 g)" }, { "text": "700 g water" } ] }
  ],
  "steps": [
    { "type": "instruction", "text": "Peel the onion and the garlic." },
    { "type": "weigh", "text": "Put the onion and the garlic in the bowl.", "grams": 120 },
    { "type": "machine", "text": "Chop.", "mode": "run",
      "segments": [ { "seconds": 5, "speed": 5, "tempC": 0, "reverse": false } ] },
    { "type": "machine", "text": "Blend gradually.", "mode": "run",
      "segments": [ { "seconds": 10, "speed": 4, "tempC": 0, "reverse": false },
                    { "seconds": 30, "speed": 8, "tempC": 0, "reverse": false } ] }
  ],
  "notes": []
}
```

Required: `format`, `id`, `title`, `steps`. Everything else is optional.

`image` is a path relative to the repository root (served by the same server; `tools/fetch_images.py` downloads
remote pictures into `images/` as 640x400 JPEGs) or an absolute URL. `imageSource` keeps the original URL.

## Steps

| type | fields | what the app does |
|---|---|---|
| `instruction` | `text` | shows the text, user taps Next |
| `weigh` | `text`, `grams`, optional `measure` | tares the scale, shows a progress bar towards `grams`, user taps Next. With `"measure": true` the amount is up to the cook (`grams` is the most it takes) and the scale measures what goes in; the app's generated programs (the automatic soup) work out the following steps from that weight |
| `machine` | `text`, `mode`, `segments[]` | user taps Start; the segments run back to back |

`mode`:

* `run`: normal motor/heater (work command 01). Each segment: `seconds` (> 0), `speed` 0-10,
  `tempC` 0 or 37-130, `reverse`. Several segments = a speed ramp.
* `turbo`: turbo pulse(s). `seconds` per segment; speed/temperature ignored.
* `knead`: `seconds` = total kneading time (the app expands it like the stock preset).
* `roast`: `seconds`, `tempC` (default 130).
* `steam`: `seconds` = steaming time after preheat.

A `run` segment heated to 95 °C or less at speed 0 or 1 may last up to 43200 s (12 h): slow cooking, sous-vide,
fermentation. The machine runs it only with **Long programs** on (Settings → Machine); otherwise it says so when the
step starts. Every other segment: 5940 s (99 min) at most.

A heated `run` segment whose time ends before the bowl reaches `tempC` keeps heating until it does (95 °C counts
for targets above that), at most half its time longer (2 to 10 min), so write the time the food needs once hot
plus a normal heat-up, as for the stock app.

The machine layer enforces its own limits whatever the recipe says: speed ≤ 3 while heating,
speed ≤ 3 in reverse, 99 min max, 15 min max above speed 6, and heating only with
≥ 50 g in the bowl. `knead`, `roast` and `steam` use the stock presets with the same limits.

## Accessories

The apps work out which original accessories a recipe uses from its step text (`recipe/Accessories.kt`), so write
them with the names Lidl's own recipes use, in a step of their own when one goes in or comes out:

| accessory | English | Portuguese | rules the apps apply |
|---|---|---|---|
| mixer attachment (Rühraufsatz) | `mixer attachment` | `acessório batedor` | speed 1-4 only, never Turbo or kneading: before a faster step the machine asks whether it is out |
| cooking pot (Kocheinsatz) | `cooking pot` | `acessório de cozer` | in the bowl over at least 500 g of water; "on the lid" means a splash guard, not in the bowl |
| deep steamer basket | `deep steamer basket` | `acessório para vapor fundo` | locks onto the bowl in place of the lid, 1000 g of water in the bowl |
| shallow steamer basket | `shallow steamer basket` | `acessório para vapor plano` | only on top of the deep one |
| measuring beaker | `measuring beaker` | `copo de medição` | shown when a step puts it in or takes it out |

Say "Put in the mixer attachment." / "Coloque o acessório batedor." and "Remove the mixer attachment." / "Retire o
acessório batedor."; the recipe page lists the accessories, and the cook screen shows what to put in or take out at
each step.

## Improved copies

A recipe improved with the AI ("Improve with AI" in the apps) has `"source": {"kind": "ai-tweak", "ref": "<key of the
original>"}`. The apps show it instead of the original, and `tools/build_index.py` leaves the original out of
`index.json` when the improved copy is in the same repository.

## Languages

Every recipe exists in Portuguese (`pt`) and English (`en`). Each language version is its own file with the id
`<key>-<language>`, e.g. `sample-hummus-pt` and `sample-hummus-en`, with the same steps, grams and machine settings;
only the text differs. `index.json` gives each entry's `key`, so the app shows the version that matches its own
language, and favourites or history can follow a recipe across languages. `tools/build_index.py`, and with it the
Docker build, fails when a key is missing one of the languages.

Keys are free-form (lower case, a–z 0–9 and `-`). Recipes added from the phone app use `web-<slug>`.

## Repository layout

```
index.json                 generated by tools/build_index.py
recipes/<key>-<lang>.json
images/<hash>.jpg          generated by tools/fetch_images.py
```

`index.json`:

```json
{ "format": "mcc-index/1", "generated": "…", "recipes": [
  { "id": "sample-hummus-en", "key": "sample-hummus", "title": "…", "language": "en", "tags": [],
    "totalMinutes": 10, "image": "images/….jpg", "file": "recipes/sample-hummus-en.json", "sha256": "…",
    "blurhash": "LMKKo.IVI-xG%M8^aM-;-=9s$*xu" } ] }
```

`blurhash` (optional): a [BlurHash](https://blurha.sh) of the photo (4 x 3 components), only for photos stored in
`images/`. The apps paint it blurred while the photo loads. `build_index.py` computes it when Pillow is installed
(kept in `.mcc-blurhash.json` between runs, so a rebuild only works on new or changed photos); without Pillow the
field is left out. Apps must ignore fields they don't know, and the format stays `mcc-index/1`.
