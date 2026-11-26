# Third-party components

| Component | Where | Licence |
|---|---|---|
| [Manrope](https://github.com/googlefonts/manrope) font | `common/src/main/assets/fonts/` | SIL Open Font License 1.1 (`OFL.txt` next to the fonts) |
| [Eclipse Paho MQTT v3](https://github.com/eclipse/paho.mqtt.java) | Gradle dependency, inside the APK | EPL-2.0 / EDL-1.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Gradle dependency, inside the APK | Apache-2.0 |
| Kotlin standard library | Gradle dependency, inside the APK | Apache-2.0 |
| AndroidX: Jetpack Compose, Material 3, Glance, Activity, Core, Lifecycle, Window, SplashScreen | Gradle dependencies, inside the phone APK | Apache-2.0 |
| AOSP platform **test** key | `signing/` | Part of AOSP (Apache-2.0); public, not a secret |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp), [Pillow](https://python-pillow.org), [FFmpeg](https://ffmpeg.org), [Deno](https://deno.com) | installed into the `server/` Docker image (the published one and your own builds); not in this repository | Unlicense, MIT-CMU, LGPL/GPL, MIT |
| Alpine Linux, nginx, Python, git, OpenSSH | base images and packages of the `server/` and `recipe-repo/` Docker images | various (MIT, BSD-2-Clause, PSF, GPL-2.0, BSD) |

Not included, on purpose: anything from the stock firmware or the stock cooking app (code, recipes, images).
The protocol notes in `docs/PROTOCOL.md` describe behaviour; no stock code is copied.

## Program photos

Cropped and resized to 640×400, in `common/src/main/assets/programs/`. Each file stays under its own licence
below (the licence of this project does not apply to them).

From [Unsplash](https://unsplash.com/?utm_source=mcc-app&utm_medium=referral), under the
[Unsplash License](https://unsplash.com/license) (free to use, including in apps; credited here by choice):

| File | Photo | Photographer |
|---|---|---|
| `eggs-soft.jpg` | [Two boiled eggs on a plate on a table](https://unsplash.com/photos/two-boiled-eggs-on-a-plate-on-a-table-kzzx_gCE75s?utm_source=mcc-app&utm_medium=referral) | [Alex 張飛](https://unsplash.com/@zhangfeialex?utm_source=mcc-app&utm_medium=referral) |
| `eggs-hard.jpg` | [White egg lot on brown wooden table](https://unsplash.com/photos/white-egg-lot-on-brown-wooden-table-rKUK4EB8F7s?utm_source=mcc-app&utm_medium=referral) | [Mustafa Bashari](https://unsplash.com/@mustafabashari?utm_source=mcc-app&utm_medium=referral) |
| `rice.jpg` | [Rice in bowl](https://unsplash.com/photos/rice-in-bowl-xmuIgjuQG0M?utm_source=mcc-app&utm_medium=referral) | [Pille R. Priske](https://unsplash.com/@pillepriske?utm_source=mcc-app&utm_medium=referral) |
| `steam-veg.jpg` | [A plate of broccoli and carrots on a table](https://unsplash.com/photos/a-plate-of-broccoli-and-carrots-on-a-table-m0tdvKoq0CU?utm_source=mcc-app&utm_medium=referral) | [The Design Lady](https://unsplash.com/@sarah35?utm_source=mcc-app&utm_medium=referral) |
| `melt-chocolate.jpg` | [A close up view of chocolate flowing in a bowl](https://unsplash.com/photos/a-close-up-view-of-chocolate-flowing-in-a-bowl-67j_Tv-Ub-8?utm_source=mcc-app&utm_medium=referral) | [Has Tahishi](https://unsplash.com/@dark_lens?utm_source=mcc-app&utm_medium=referral) |
| `smoothie.jpg` | [Strawberry shake in clear drinking glass](https://unsplash.com/photos/strawberry-shake-in-clear-drinking-glass-n73TTunlskE?utm_source=mcc-app&utm_medium=referral) | [Denis](https://unsplash.com/@denis96?utm_source=mcc-app&utm_medium=referral) |
| `puree.jpg` | [Yellow cream in clear glass bowl](https://unsplash.com/photos/yellow-cream-in-clear-glass-bowl-CJj2iQn6IsE?utm_source=mcc-app&utm_medium=referral) | [Rachel Loughman](https://unsplash.com/@rachelloughman?utm_source=mcc-app&utm_medium=referral) |
| `proof-dough.jpg` | [A bowl with a ball in it sitting on a table](https://unsplash.com/photos/a-bowl-with-a-ball-in-it-sitting-on-a-table-S1fZUOr3OUI?utm_source=mcc-app&utm_medium=referral) | [Abdul Raheem Kannath](https://unsplash.com/@raheemblacksnows?utm_source=mcc-app&utm_medium=referral) |
| `reheat.jpg` | [Bowl of creamy orange soup with swirl and herb garnish](https://unsplash.com/photos/bowl-of-creamy-orange-soup-with-swirl-and-herb-garnish-6EYlAbV2F3M?utm_source=mcc-app&utm_medium=referral) | [Rob Wingate](https://unsplash.com/@robwingate?utm_source=mcc-app&utm_medium=referral) |
| `knead.jpg` | [White dough on black textile](https://unsplash.com/photos/white-dough-on-black-textile-SQLOsc0HGDI?utm_source=mcc-app&utm_medium=referral) | [Tamara Gak](https://unsplash.com/@tamara_photography?utm_source=mcc-app&utm_medium=referral) |
| `brown.jpg` | [Brown food on white and green ceramic bowl](https://unsplash.com/photos/brown-food-on-white-and-green-ceramic-bowl-oMif_mjCPyY?utm_source=mcc-app&utm_medium=referral) | [Keriliwi](https://unsplash.com/@keriliwi?utm_source=mcc-app&utm_medium=referral) |
| `slow-cook.jpg` | [A bowl of food](https://unsplash.com/photos/a-bowl-of-food-y_7KLDoPlAc?utm_source=mcc-app&utm_medium=referral) | [Laura Ohlman](https://unsplash.com/@ohlmanphotography?utm_source=mcc-app&utm_medium=referral) |
| `sous-vide.jpg` | [Sliced meat on black surface](https://unsplash.com/photos/sliced-meat-on-black-surface-Vr6Su7dd2qs?utm_source=mcc-app&utm_medium=referral) | [Stu Moffat](https://unsplash.com/@stumoffatimages?utm_source=mcc-app&utm_medium=referral) |
| `yoghurt.jpg` | [A close up of a bowl of food on a table](https://unsplash.com/photos/a-close-up-of-a-bowl-of-food-on-a-table-4B3eEzUYD6o?utm_source=mcc-app&utm_medium=referral) | [Marie Martin](https://unsplash.com/@marie_martin1?utm_source=mcc-app&utm_medium=referral) |
| `clean.jpg` | [Person in green shirt washing stainless steel sink](https://unsplash.com/photos/person-in-green-shirt-washing-stainless-steel-sink--VhH4S1Lur8?utm_source=mcc-app&utm_medium=referral) | [Marek Studzinski](https://unsplash.com/@jccards?utm_source=mcc-app&utm_medium=referral) |
| `clean-deep.jpg` | [A close up of a sponge on a blue and yellow surface](https://unsplash.com/photos/a-close-up-of-a-sponge-on-a-blue-and-yellow-surface-p20KXTbxwVM?utm_source=mcc-app&utm_medium=referral) | [Volodymyr Hryshchenko](https://unsplash.com/@lunarts?utm_source=mcc-app&utm_medium=referral) |
| `clean-smell.jpg` | [Yellow lemon fruit on white ceramic plate](https://unsplash.com/photos/yellow-lemon-fruit-on-white-ceramic-plate-09tUNHKUSrk?utm_source=mcc-app&utm_medium=referral) | [Myfanwy Owen](https://unsplash.com/@myfanwyo22?utm_source=mcc-app&utm_medium=referral) |
| `eggs-medium.jpg` | [A blue plate topped with eggs and pasta](https://unsplash.com/photos/a-blue-plate-topped-with-eggs-and-pasta-ROoTn5W_WmI?utm_source=mcc-app&utm_medium=referral) | [Stephanie Sarlos](https://unsplash.com/@studiostephaniesarlos?utm_source=mcc-app&utm_medium=referral) |
| `varoma.jpg` | [Cooked food](https://unsplash.com/photos/cooked-food-kp0z_XbfxdI?utm_source=mcc-app&utm_medium=referral) | [topcools tee](https://unsplash.com/@topcools?utm_source=mcc-app&utm_medium=referral) |

To use nicer photos in your own build, `tools/fetch_program_photos.py` downloads them from Pixabay (free API key)
into `common/src/main/assets/programs-pixabay/`, which git ignores: the Pixabay Content License allows them inside
your app but not as files shared on their own, so they are never committed. The app prefers them when present.
