# Phone app (`phone/`)

A companion app for Android phones (Android 10 and newer). It talks to the machine through the **same MQTT broker
as Home Assistant**, so it needs no server on the tablet and no Google services: it works on phones without them
(e.g. Huawei) as well as on Pixels and Samsungs.

## What it does

- **Machine**: live status, temperature, speed, scale, lid, time left and "ready at" (or the time so far of a
  stopwatch run), the step the recipe is on.
  **Stop** (always) and **Pause**. Zero the scale. When a run was prepared and permitted at the machine (remote
  start), a **hold-to-start** button.
- **Notifications**, with the app closed:
  - *The recipe needs you* (with the step's text), *Ready!*, *Weight reached* (optional), unexpected stops and
    board errors;
  - while cooking, a live notification with a progress bar, a countdown, and **Pause** / **Stop** buttons. On
    Android 16 it asks to be shown as a live update.
- **Recipes**: the same recipe repository as the machine (search, language and tag filters, photos cached for
  offline use), plus, with **Monsieur Cuisine online recipes** on (Settings → Recipe server), the official recipes
  of the Monsieur Cuisine website, and with **Include member recipes** also the ones members published (tagged
  "Community"), and with **Moulinex Companion recipes** the Moulinex website's Companion recipes converted for the
  machine. Turn them on on the machine too, so **Open on the machine** finds those recipes. The recipe page lists ingredients and steps. While the machine cooks that recipe, it highlights
  the current step and follows along. **Open on the machine** shows the recipe on the machine's Cook screen, ready
  to cook: it never starts anything.
- **Favourites**: the star on a recipe page; favourites show first in Recipes and as a filter.
- **Accessories**: the recipe page lists the original accessories it uses, and each step shows what to put in or take
  out (also in cook along).
- **Improve with AI**: the spark on a recipe page; say what to change and review the result like an import, then add
  it to the recipe server (where it replaces the original for everyone) or keep it on this phone (shown instead of the
  original here).
- **Cook along**: one step per page in big type, with the screen kept on. While the recipe is open on the machine,
  the pages follow the machine's step.
- **Recipe import**: share a recipe page or a cooking video (YouTube, TikTok, Instagram reels, Vimeo, Facebook) to
  the app, or paste text. The AI server turns it into a machine recipe in both languages; you review the changes it
  made to fit the machine, ask for corrections, then **add it to the recipe server** (through the upload service) or
  keep it on this phone only. If adding it or a correction fails, the reason is shown at the top of the review, and
  the recipe stays there to try again. Videos are transcribed by the upload service first
  ([RECIPE-UPLOAD.md](RECIPE-UPLOAD.md#video-transcripts)).
- **Recipes** open at once from the copy on the phone and refresh from the recipe server in the background (at start
  and when the app comes back after 15 minutes). Photos show blurred while they load.
- **Shopping list** from a recipe's ingredients, grouped by recipe, tick off as you shop, share as text. Removing an
  item or clearing the bought ones can be undone, and items go back where they were. Discarding an imported recipe
  can be undone too.
- **Home-screen widget** and **Quick Settings tile**: what the machine is doing and when it is ready, with Stop
  while it runs (the tile asks first). App shortcuts (long-press the icon) open Machine, Recipes or the list.
- **Foldables and tablets**: on a wide screen (e.g. Galaxy Z Fold unfolded) a side rail and two panes. The machine
  sits next to the recipe it is cooking, and the recipe list next to the open recipe.
- Light and dark (follows the phone), English and European Portuguese.

## What it cannot do

Exactly what Home Assistant cannot: it can watch, stop and pause, start only a run permitted at the machine,
and open a recipe (which only shows it). It never chooses heat, speed or time. The machine checks every limit
whatever asks. Opening recipes from the phone can be switched off on the machine (Settings → Home Assistant →
*Recipes from the phone*). It is refused while another recipe is under way.

## Setup

1. Home Assistant (or any MQTT broker) already connected to the machine: [HOME-ASSISTANT.md](HOME-ASSISTANT.md).
   A separate broker user for the phones is a good idea.
2. Install `mcc-phone-<version>.apk` from the [latest release](https://github.com/brunoaclopes/mcc-app/releases/latest)
   (Android 10 or newer), or build it (below). Android asks to allow installs from your browser or file manager
   once. See [Installing and updating](#installing-and-updating).
3. Settings → **Connection**: broker address, port, user and password. **Recipe server**: the same address as on
   the machine, and **Monsieur Cuisine online recipes** (and **Include member recipes**) as on the machine.
4. Optional, for recipe import: Settings → **Recipe import** → AI server address, model and key, and the upload
   service's address and token ([SERVER-SETUP.md](SERVER-SETUP.md)) → **Save and test**.
5. Allow notifications when asked. For notifications with the app closed, keep **Stay connected in the
   background** on, and tap **Allow running in the background**.
   - *Huawei / Honor*: also Settings → Battery → App launch → Monsieur Cuisine → *Manage manually*, all on.
     Otherwise the phone closes the app and notifications stop.
   - *Samsung*: if notifications stop after a while, Settings → Battery → Background usage limits → *Never
     sleeping apps* → add Monsieur Cuisine.

Away from home the phone needs to reach the broker: a VPN to your home network (WireGuard, Tailscale), or a
broker exposed with TLS (turn on *Secure connection* and use port 8883). For notifications anywhere without
either, use the Home Assistant Companion app and the blueprint in [HOME-ASSISTANT.md](HOME-ASSISTANT.md#phone-notifications).

## How it works

- `LinkClient.kt`: one MQTT connection (Paho), subscribes to `mcc/state`, `mcc/availability` and `mcc/event`,
  publishes to `mcc/cmd`. The payloads are the shared classes in `link/` (`MachineState`, `MachineEvent`,
  `Command`), the same ones the machine uses to publish them.
- `LinkService.kt`: a foreground service that keeps the connection while background mode is on, and owns the
  live notification. Without background mode the connection lives only while the app is open.
- `Notifier.kt`: the live notification and the alerts.
- `Importer.kt`: the recipe import (transcript, AI, translation, review, publish), with `AiClient` and
  `RecipePublisher` from `common/`.
- `widget/MachineWidget.kt` (Glance), `MachineTile.kt` and `Surfaces.kt`: the widget and the tile, which show the
  same state as the app.
- `ui/`: Jetpack Compose with Material 3, coloured with the machine's design tokens (`T` from `common/`). It uses
  the machine's own Manrope font, line icons, cover illustrations, `ClockRing` and `HoldButton` views, and its
  translations for everything both apps say (step sentences, board errors, units).

## Installing and updating

- **Updates**: install the newer `mcc-phone-<version>.apk` over the old one; settings stay. To get updates
  automatically, add `https://github.com/brunoaclopes/mcc-app` to [Obtainium](https://github.com/ImranR98/Obtainium)
  (filter the APKs by `mcc-phone`). The release tag is the release as a whole (`v0.10.0`), not the phone app's
  version, so let Obtainium read the version from the release title (`v0.11.2 · phone 0.10.2 · machine 1.7.2`). In
  the app's additional options:

  | Option | Value |
  |---|---|
  | Filter links by regular expression | `mcc-phone` |
  | Use release title as version string | on |
  | Trim version string with RegEx | `phone ([0-9.]+)` |
  | Match group to use for "Trim version string" | `1` |

  Obtainium then offers an update only when the phone app's version changes.
- **Check a download**: the release notes list the phone app's signing certificate (SHA-256);
  `apksigner verify --print-certs mcc-phone-<version>.apk` must show the same digest.
- **From 0.6.x or older (one-time reinstall)**: since 0.7.0 the app is `io.github.brunoaclopes.mcc.phone` and signed
  with the release key; older builds were `mcc.phone`, signed with a local debug key. Android sees a different app:
  uninstall the old one, install the new one and enter the settings again (Connection, Recipe server, Recipe
  import).
- **Your own builds** are signed with your debug key, so they can't update a release build (or the other way
  round): uninstall first.
- **Play Protect** may warn because the app is not from Google Play; that is expected for apps installed from
  GitHub ([RELEASING.md](RELEASING.md#google-play-protect)).

## Build

```
JAVA_HOME=/path/to/jdk17 ./gradlew :phone:assembleRelease
```

The release build is minified. It is signed with the release key when `~/.mcc-release/keystore.properties` (or the
`MCC_KEYSTORE_*` variables) exist, which only the maintainer has ([RELEASING.md](RELEASING.md#signing)); otherwise
with your local debug key, which is fine for your own phones.
