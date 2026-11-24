# Third-party components

| Component | Where | Licence |
|---|---|---|
| [Manrope](https://github.com/googlefonts/manrope) font | `common/src/main/assets/fonts/` | SIL Open Font License 1.1 (`OFL.txt` next to the fonts) |
| [Eclipse Paho MQTT v3](https://github.com/eclipse/paho.mqtt.java) | Gradle dependency, inside the APK | EPL-2.0 / EDL-1.0 |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) | Gradle dependency, inside the APK | Apache-2.0 |
| Kotlin standard library | Gradle dependency, inside the APK | Apache-2.0 |
| AOSP platform **test** key | `signing/` | Part of AOSP (Apache-2.0); public, not a secret |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp), [Pillow](https://python-pillow.org), [FFmpeg](https://ffmpeg.org), [Deno](https://deno.com) | installed into the `server/` Docker image when you build it; not in this repository | Unlicense, MIT-CMU, LGPL/GPL, MIT |
| nginx, Python | base images of the `server/` and `recipe-repo/` Docker images | BSD-2-Clause, PSF |

Not included, on purpose: anything from the stock firmware or the stock cooking app (code, recipes, images).
The protocol notes in `docs/PROTOCOL.md` describe behaviour; no stock code is copied.

## Program photos

From Wikimedia Commons and Flickr (found through Openverse), cropped and resized to 640×400, in `common/src/main/assets/programs/`. Each file stays under
its own licence below (the MIT licence of this project does not apply to them); the CC BY-SA crops are shared
under the same CC BY-SA licence.

| File | Photo | Author | Licence |
|---|---|---|---|
| `eggs-soft.jpg` | [Jajka na miękko-02.jpg](https://commons.wikimedia.org/wiki/File:Jajka_na_mi%C4%99kko-02.jpg) | a_b_normal123 | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `eggs-medium.jpg` | [Soft boiled egg cut in half in Japan - 2024 July 9.jpeg](https://commons.wikimedia.org/wiki/File:Soft_boiled_egg_cut_in_half_in_Japan_-_2024_July_9.jpeg) | Nesnad | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) |
| `eggs-hard.jpg` | [Boiled Egg - Crossection.jpg](https://commons.wikimedia.org/wiki/File:Boiled_Egg_-_Crossection.jpg) | Ramesh NG | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) |
| `rice.jpg` | [A bowl of rice.jpg](https://commons.wikimedia.org/wiki/File:A_bowl_of_rice.jpg) | Douglas Perkins | [CC0](http://creativecommons.org/publicdomain/zero/1.0/deed.en) |
| `steam-veg.jpg` | [Steamed vegetables in the breakfast buffet (43887013644).jpg](https://commons.wikimedia.org/wiki/File:Steamed_vegetables_in_the_breakfast_buffet_(43887013644).jpg) | Eric Fischer | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `melt-chocolate.jpg` | [Melted chocolate (6547776815).jpg](https://commons.wikimedia.org/wiki/File:Melted_chocolate_(6547776815).jpg) | Lenore Edman from Sunnyvale, CA | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `smoothie.jpg` | [Strawberry and lemon smoothie (14430283996).jpg](https://commons.wikimedia.org/wiki/File:Strawberry_and_lemon_smoothie_(14430283996).jpg) | Vanessa p. from near Savannah, GA | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `proof-dough.jpg` | [Craisin bread dough.jpg](https://commons.wikimedia.org/wiki/File:Craisin_bread_dough.jpg) | Ruth Hartnup | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `reheat.jpg` | [Vegetable soup in Switzerland.jpg](https://commons.wikimedia.org/wiki/File:Vegetable_soup_in_Switzerland.jpg) | Tiia Monto | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `clean.jpg` | [Sponge-for-washing-1212612.jpg](https://commons.wikimedia.org/wiki/File:Sponge-for-washing-1212612.jpg) | Robert Fotograf | [CC0](http://creativecommons.org/publicdomain/zero/1.0/deed.en) |
| `knead.jpg` | [Baker woman's hands kneading bread dough.jpg](https://commons.wikimedia.org/wiki/File%3ABaker_woman%27s_hands_kneading_bread_dough.jpg) | Shixart1985 | [CC BY 2.0](https://creativecommons.org/licenses/by/2.0) |
| `brown.jpg` | [Fried onion with seasoning.JPG](https://commons.wikimedia.org/wiki/File%3AFried_onion_with_seasoning.JPG) | Gran | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0) |
| `varoma.jpg` | [Chinese soup dumplings.jpg](https://commons.wikimedia.org/wiki/File%3AChinese_soup_dumplings.jpg) | Phil Denton | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) |
| `clean-deep.jpg` | [Cleaning Kitchen Counter](https://www.flickr.com/photos/184594136@N08/51486539352) (Flickr) | Alabama Extension | [CC0](https://creativecommons.org/publicdomain/zero/1.0/) |
| `clean-smell.jpg` | [Lemons](https://www.flickr.com/photos/101300267@N05/12950385095) (Flickr) | Tim-Hoggarth | [CC BY-SA 2.0](https://creativecommons.org/licenses/by-sa/2.0) |

To use nicer photos in your own build, `tools/fetch_program_photos.py` downloads them from Pixabay (free API key)
into `common/src/main/assets/programs-pixabay/`, which git ignores: the Pixabay Content License allows them inside
your app but not as files shared on their own, so they are never committed. The app prefers them when present.
