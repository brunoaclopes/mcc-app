# Architecture

```
┌──────────────────────── app (Android, runs as uid system) ────────────────────────┐
│  MainActivity + ui/*  ──commands──▶  MachineService  ◀──stop/pause/tare──  MqttBridge │
│        ▲  reads snapshots every 250 ms     │   100 ms loop        │                    │
│        │                                   ▼                      ▼                    │
│   RecipeStore (HTTP + cache)      RecipeRunner (recipe/)     Led (sysfs)               │
│                                           │                                            │
│                                   MachineController (machine/)  ── all safety limits   │
│                                           │                                            │
│                                   Protocol ⇄ SerialPort (JNI) ⇄ /dev/ttyMT0 ⇄ board    │
└────────────────────────────────────────────────────────────────────────────────────┘
```

## Modules

### `machine/`: pure Kotlin, no Android

- `Protocol.kt`: command and status frames (see [PROTOCOL.md](PROTOCOL.md)).
- `TemperatureTable.kt`: °C ↔ level.
- `MachineController.kt`: the only thing that decides what is sent to the board. `start(RunRequest)` validates a
  request against `SafetyLimits` and returns `Started` or `Rejected(code)`. `tick()` runs every 100 ms; it builds
  the next frame and stops on any stop condition. It also owns tare and the empty-bowl reference.
- Tests replay the real captures in `src/test/resources/captures/`.

### `recipe/`: pure Kotlin, no Android

- `Recipe.kt`: the `mcc-recipe/1` model and parser ([../recipe-repo/FORMAT.md](../recipe-repo/FORMAT.md)).
- `MachinePlan.kt`: turns a machine step into `RunRequest`s (speed ramps, and the knead and roast presets that
  mirror the stock app).
- `Programs.kt`: the built-in automatic programs (eggs, rice, steaming, cleaning…) as recipes in both languages.
- `Soup.kt`: the automatic soup. From the vegetables' weight, how firm they are, the texture (chunky, or blended at
  one of three levels) and an optional onion base, it works out the water, the cooking time and the blending ramp,
  and builds an ordinary recipe.
- `RecipeFit.kt`: fits a recipe from outside (AI import, other machines) to the machine limits and lists each change.
- `RecipeRunner.kt`: the step-by-step state machine (instruction → weigh → machine step → …). A heated step whose
  time runs out before the bowl reaches its temperature keeps heating in 30 s extensions until it does (targets above
  95 °C count as reached at 95 °C, since food with water boils at about 100 °C), at most half the step's time longer
  (2 to 10 min); each extension is a normal start, checked by the machine layer. With hands-free weighing on
  (Settings → Machine), a weigh step moves on once the scale has been steady at 97 % of its amount or more for
  2.5 s; that only changes the step shown. It never starts a
  machine step by itself: the user presses Start. It reports `Event`s; the app turns them into text.

### `link/`: pure Kotlin

`Link.kt`: the MQTT topics and payloads (`MachineState`, `MachineEvent`, `Command`), used by the machine to
publish and by the phone app to read, so the two cannot drift apart ([HOME-ASSISTANT.md](HOME-ASSISTANT.md)).

### `common/`: Android library shared by `app/` and `phone/`

Design tokens (`ui/Theme.kt`), line icons, cover illustrations, the custom Canvas widgets (`ui/Widgets.kt`,
`Glass.kt`, `RoundImage.kt`), `ImageLoader`, `i18n/Strings.kt` (every machine text, English and European
Portuguese), `RecipeStore.kt`, the Manrope font and the program photos. The packages are still `mcc.app.*`.

- `AiClient.kt`: recipe import through any OpenAI-compatible chat API: web pages, pasted text, YouTube pages and
  video transcripts; revisions from the user's feedback; translation. Every result goes through `Recipe.parse` and
  `RecipeFit`.
- `RecipePublisher.kt`: the upload service's client: `POST /v1/recipes` ([RECIPE-UPLOAD.md](RECIPE-UPLOAD.md)) and
  `POST /v1/transcript`.

### `phone/`: the phone companion app

Compose and Material 3, minSdk 29, application id `io.github.brunoaclopes.mcc.phone` (the Kotlin package stays
`mcc.phone`). `Importer.kt` runs the recipe import (transcript, AI, translation, review, publish). See
[PHONE.md](PHONE.md).

### `app/`: Android (minSdk / targetSdk 23)

| File | Role |
|---|---|
| `MachineService.kt` | foreground service; owns the port, runs the 100 ms loop at urgent priority, exposes snapshots and command methods |
| `SerialPort.kt` + `jni/mccserial.c` | opens `/dev/ttyMT0` at 4800 baud; refuses while the stock machine service runs (`PortGuard`) |
| `Led.kt` | RGB LED patterns on their own thread |
| `MqttBridge.kt` | Home Assistant discovery, state and commands ([HOME-ASSISTANT.md](HOME-ASSISTANT.md)) |
| `RecipeStore.kt` (in `common/`) | downloads `index.json` and recipes (sha256-checked) and caches them for offline use; adds the built-in programs and the recipes imported on the machine (`mine/`) |
| `AiClient.kt` (in `common/`) | OpenAI-compatible chat client for the recipe import; output goes through `Recipe.parse` and `RecipeFit` |
| `Prefs.kt` | settings |
| `Sounds.kt` | small synthesized sounds (start, stop, step, weighed, done, alerts, knob ticks), generated at start-up and played on the alarm stream |
| `Sun.kt` | sunrise and sunset for the Auto look |
| `i18n/Strings.kt` (in `common/`) | every user-visible text, English and European Portuguese |
| `MainActivity.kt` | the shell: rail, top bar with STOP, screen host, dialogs, toasts, sounds |
| `ui/*Screen.kt` | Home, Manual, Recipes, Cook, Settings |
| `ui/RunOverlay.kt` | the full-screen countdown shown while the machine runs |
| `ui/Widgets.kt`, `Glass.kt`, `RoundImage.kt`, `Theme.kt`, `Icons.kt` (in `common/`) | custom Canvas views and design tokens. No AndroidX or Material: plain Views, for speed on this tablet |

### Outside the apps

- [`recipe-repo/`](../recipe-repo): the template for a recipe repository (format, validator, converters, index
  builder) and the generic recipe server image (`docker/serve.Dockerfile`).
- [`server/`](../server): Python (standard library, plus yt-dlp and Pillow in the image); recipe uploads (commit and push to the recipe repository)
  and video transcripts (yt-dlp, Wyoming speech-to-text). Docker image `ghcr.io/brunoaclopes/mcc-server`.
- [`.github/`](../.github): CI, releases and images ([RELEASING.md](RELEASING.md)).

## Safety

`MachineController` checks everything on every 100 ms tick.

**It stops** when there's no valid board frame for 1.5 s, the lid opens, the board reports any error, or the
maximum runtime or the requested duration is reached.

**It refuses to start** when:

- there's no communication, the lid is open, or the board reports an error;
- the speed is above 3 with heat, or above 3 in reverse;
- the temperature is above 130 °C;
- the duration is above 99 min (15 min above speed 6). A manual run without a set time (time 0:00, a stopwatch)
  gets exactly that limit as its duration and ends there (`MAX_RUNTIME`); only normal runs can be open;
- heat is requested without a confirmed empty-bowl reference, or with less than 200 g in the bowl (roast: 50 g);
- the run mode isn't enabled. The controller only enables NORMAL (`01`) by itself (`SafetyLimits.enabledModes`);
  turbo, knead, roast and steam are in `SafetyLimits.userEnableableModes`, and the app enables them
  (`MachineService.applyModes`) because each passed its supervised test ([TEST-PLAN.md](TEST-PLAN.md)). Their own
  limits always apply: turbo only while held, at most 10 s, never with heat, reverse or a bowl at 60 °C or more;
  roast at speed 1 at most. A new mode needs a supervised test before it is added there.

The **tare** shifts the empty-bowl reference, so the content measurement survives a tare. The weight is "unknown"
for 1.5 s after a tare. The board reports 0 g while the motor runs.

**At app level:**

- Controls start locked each time the app starts, and unlocking needs a confirmation. Locking never stops a run,
  and STOP is always visible (dialogs never cover it).
- Start buttons need a 900 ms hold, and the release after a hold-to-start is never taken as a tap on STOP.
- The top-bar weight is calmed by `ScaleFilter` (screen taps press on the load cell); safety checks and the
  weighing gauge use the raw reading. The jog knob never starts anything and is ignored while the running view is
  shown.
- Any stop other than completion pauses a recipe step and keeps its remaining time.
- Keep warm (offered when a heated recipe finishes) is an ordinary manual run, 30 min at 70 °C, speed 1 in reverse,
  held to start like any other.
- MQTT accepts `stop`, `pause`, `tare` and `start`. `start` only runs what was permitted at the machine (opt-in,
  single use, time-limited, cancelled by lid, weight change, lock or STOP). The AI client can only produce recipe files.
- The service holds a partial wake lock and a Wi-Fi lock for as long as it runs (the machine is mains powered): the
  100 ms command stream never stalls, the lid and scale are watched in standby, and Home Assistant stays connected
  with the screen off.

## Jog knob

| Screen | Turn | Push |
|---|---|---|
| Home | – | open Cook (or Recipes) |
| Manual | adjust the selected dial | next dial (time → speed → temperature) |
| Recipes | scroll a row | in a recipe: Start cooking |
| Cook | previous / next step (not while running) | Next on preparation and weigh steps |

## RGB LED

| State | LED |
|---|---|
| idle | steady white (stock value 0,0,50) |
| running | pulsing white; pulsing amber while heating |
| recipe step paused | steady amber |
| weighing target reached (≥ 95 %) | steady green |
| run completed | green blink, 3 s |
| stopped by lid, board error or lost contact | red blink, 3 s |
| board error present | red blink |
| screen off, or no link to the board | off |
| stock app owns the machine | not touched |

Settings → Light and sound: **Animated light ring** (on by default) follows the blade speed and the heat, fills
green while you weigh and plays a rainbow when a recipe ends; off keeps the plain colours above. **Ring
brightness**: 25, 50 or 100 %.

## UI

- **Languages**: all text is in `i18n/Strings.kt`, an abstract class with one object per language, so a missing
  translation fails the build. The machine and recipe layers return codes, never sentences. The first-run
  language follows the tablet (Portuguese on a Portuguese tablet, otherwise English). Changing it rebuilds the
  screens and also sets the recipe filter.
- **Day / night**: `T.apply(night)` swaps the whole palette. Auto follows sunrise and sunset for the location in
  Settings (or an estimate from the time zone) and is checked every 30 s, but never while a field is being edited
  or a dialog is open. A switch re-creates the activity behind a cross-fade; the machine state lives in the
  service, so nothing is interrupted. Text on photos and solid buttons stays light (`T.ON_PHOTO`), and text on
  bright accent fills stays dark (`T.ON_ACCENT`).
- **Glass without blur**: a backdrop rendered once (half resolution, 16-bit, film grain) as the window background;
  `GlassDrawable` panels (translucent tint, sheen, gradient rim); `BackdropSlice` for full-screen overlays.
- **Motion**: screens slide in the direction of travel; tiles, cards and ingredients come in staggered; values
  count and cross-fade; the lid chip shakes when the lid opens; there's confetti when a recipe is finished. Every
  animation is short. The few that repeat (pulsing dot, current step) run only while visible.

## Performance rules

The tablet is an MT6580 (4 × 1.3 GHz) with a Mali-400 GPU. Measured: idle CPU 1–6 %; recipe-grid scrolling at a
90th-percentile frame of 17–22 ms; serial loop average 100 ms, worst 103–115 ms under UI stress; cold start
about 1 s; 42–75 MB of memory.

- Redraw only on change: `TextView.put()` and change-checked setters.
- Endless animators run only while visible (`syncAnim`).
- Photos go through `RoundImage`: one draw pass, no outline clipping, no stacked overlay views.
- The rail and top bar are hardware layers.
- The serial threads run at urgent priority on a fixed-rate schedule. The loop logs any gap over 250 ms, and
  Settings → About this machine → Serial loop shows live figures.
