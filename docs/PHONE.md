# Phone app (`phone/`)

A companion app for Android phones (Android 10 and newer). It talks to the machine through the **same MQTT broker
as Home Assistant**, so it needs no server on the tablet and no Google services: it works on phones without them
(e.g. Huawei) as well as on Pixels and Samsungs.

## What it does

- **Machine**: live status, temperature, speed, scale, lid, time left and "ready at", the step the recipe is on.
  **Stop** (always) and **Pause**. Zero the scale. When a run was prepared and permitted at the machine (remote
  start), a **hold-to-start** button.
- **Notifications**, with the app closed:
  - *The recipe needs you* (with the step's text), *Ready!*, *Weight reached* (optional), unexpected stops and
    board errors;
  - while cooking, a live notification with a progress bar, a countdown, and **Pause** / **Stop** buttons. On
    Android 16 it asks to be shown as a live update.
- **Recipes**: the same recipe repository as the machine (search, language and tag filters, photos cached for
  offline use). The recipe page lists ingredients and steps. While the machine cooks that recipe, it highlights
  the current step and follows along. **Open on the machine** shows the recipe on the machine's Cook screen, ready
  to cook: it never starts anything.
- **Shopping list** from a recipe's ingredients, grouped by recipe, tick off as you shop, share as text.
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
2. Install the APK (`phone/build/outputs/apk/release/phone-release.apk`, or build it, below).
3. Settings → **Connection**: broker address, port, user and password. **Recipe server**: the same address as on
   the machine.
4. Allow notifications when asked. For notifications with the app closed, keep **Stay connected in the
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
- `ui/`: Jetpack Compose with Material 3, coloured with the machine's design tokens (`T` from `common/`). It uses
  the machine's own Manrope font, line icons, cover illustrations, `ClockRing` and `HoldButton` views, and its
  translations for everything both apps say (step sentences, board errors, units).

## Build

```
JAVA_HOME=/path/to/jdk17 ./gradlew :phone:assembleRelease
```

The release build is minified and signed with your local debug key (fine for installing on your own phones;
configure your own key to publish it).
