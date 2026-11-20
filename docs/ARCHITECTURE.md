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
- `RecipeFit.kt`: fits a recipe from outside (AI import, other machines) to the machine limits and lists each change.
- `RecipeRunner.kt`: the step-by-step state machine (instruction → weigh → machine step → …). It never starts a
  machine step by itself: the user presses Start. It reports `Event`s; the app turns them into text.

### `link/`: pure Kotlin

`Link.kt`: the MQTT topics and payloads (`MachineState`, `MachineEvent`, `Command`), used by the machine to
publish and by the phone app to read, so the two cannot drift apart ([HOME-ASSISTANT.md](HOME-ASSISTANT.md)).

### `common/`: Android library shared by `app/` and `phone/`

Design tokens (`ui/Theme.kt`), line icons, cover illustrations, the custom Canvas widgets (`ui/Widgets.kt`,
`Glass.kt`, `RoundImage.kt`), `ImageLoader`, `i18n/Strings.kt` (every machine text, English and European
Portuguese), `RecipeStore.kt`, the Manrope font and the program photos. The packages are still `mcc.app.*`.

### `phone/`: the phone companion app

Compose and Material 3, minSdk 29. See [PHONE.md](PHONE.md).

### `app/`: Android (minSdk / targetSdk 23)

| File | Role |
|---|---|
| `MachineService.kt` | foreground service; owns the port, runs the 100 ms loop at urgent priority, exposes snapshots and command methods |
| `SerialPort.kt` + `jni/mccserial.c` | opens `/dev/ttyMT0` at 4800 baud; refuses while the stock machine service runs (`PortGuard`) |
| `Led.kt` | RGB LED patterns on their own thread |
| `MqttBridge.kt` | Home Assistant discovery, state and commands ([HOME-ASSISTANT.md](HOME-ASSISTANT.md)) |
| `RecipeStore.kt` (in `common/`) | downloads `index.json` and recipes (sha256-checked) and caches them for offline use; adds the built-in programs and the recipes imported on the machine (`mine/`) |
| `AiClient.kt` | OpenAI-compatible chat client for the recipe import; output goes through `Recipe.parse` and `RecipeFit` |
| `Prefs.kt` | settings |
| `Sun.kt` | sunrise and sunset for the Auto look |
| `i18n/Strings.kt` (in `common/`) | every user-visible text, English and European Portuguese |
| `MainActivity.kt` | the shell: rail, top bar with STOP, screen host, dialogs, toasts, sounds |
| `ui/*Screen.kt` | Home, Manual, Recipes, Cook, Settings |
| `ui/RunOverlay.kt` | the full-screen countdown shown while the machine runs |
| `ui/Widgets.kt`, `Glass.kt`, `RoundImage.kt`, `Theme.kt`, `Icons.kt` (in `common/`) | custom Canvas views and design tokens. No AndroidX or Material: plain Views, for speed on this tablet |

## Safety

`MachineController` checks everything on every 100 ms tick.

**It stops** when there's no valid board frame for 1.5 s, the lid opens, the board reports any error, or the
maximum runtime or the requested duration is reached.

**It refuses to start** when:

- there's no communication, the lid is open, or the board reports an error;
- the speed is above 3 with heat, or above 3 in reverse;
- the temperature is above 130 °C;
- the duration is above 99 min (15 min above speed 6);
- heat is requested without a confirmed empty-bowl reference, or with less than 200 g in the bowl (roast: 50 g);
- the run mode isn't enabled. Only NORMAL (`01`) is enabled by default. Turbo can be switched on in Settings
  after its supervised test (`SafetyLimits.userEnableableModes`); it runs only while held, at most 10 s, never with
  heat, reverse or a bowl at 60 °C or more. Knead, roast and steam need `SafetyLimits.enabledModes` and a
  supervised test first.

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
- MQTT accepts `stop`, `pause`, `tare` and `start`. `start` only runs what was permitted at the machine (opt-in,
  single use, time-limited, cancelled by lid, weight change, lock or STOP). The AI client can only produce recipe files.
- A partial wake lock is held while running.

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
  Settings → Machine → Serial loop shows live figures.
