# Home Assistant (MQTT)

mcc-app publishes its state to an MQTT broker and uses Home Assistant's **device discovery**, so the machine
appears in Home Assistant with no YAML. The code is in `app/src/main/kotlin/mcc/app/MqttBridge.kt`.

**Home Assistant can watch, stop, pause and tare. It can never choose what runs, heat or set the speed.**
Opening a recipe (`load`) only shows it on the machine; someone still has to start it there.
With the opt-in *remote start*, it can start one run that a person prepared and permitted at the machine.

## Remote start (opt-in)

1. Settings → Home Assistant → **Allow remote start** (off by default), and pick how long a permit lasts
   (15, 30, 60 or 120 min).
2. At the machine, prepare the run: set the dials in **Manual**, or go to a machine step of a recipe in **Cook**.
   Tap **Remote** and confirm.
3. Home Assistant's **Start (permitted run)** button (or `start` on `mcc/cmd`) now starts exactly that run, once.

The permit is cancelled when the window ends, the lid opens, the bowl weight changes by more than 30 g, the
controls are locked, STOP is pressed (on the machine or from Home Assistant), anything is started at the machine,
the recipe moves to another step, or remote start is switched off. The machine layer still checks every limit
when the run starts (lid, content for heating, speed, time).

## Setup

1. A broker, e.g. the Mosquitto add-on, with a user for the machine.
2. On the machine: Settings → Home Assistant → broker address, port (1883), user and password. Optionally set an
   **Area** (e.g. `Kitchen`). Then turn it on and press **Save**.
3. Home Assistant → Settings → Devices: a device named **Monsieur Cuisine** appears.

## Topics

| Topic | Direction | Retained | Content |
|---|---|---|---|
| `homeassistant/device/mcc/config` | machine → HA | yes | discovery for the device and its 15 entities |
| `mcc/state` | machine → HA | yes | JSON state (below), sent on change, at most every 0.5 s, and at least every 60 s |
| `mcc/availability` | machine → HA | yes | `online` / `offline` (offline is the MQTT last will) |
| `mcc/event` | machine → HA | no | one-off events (below), JSON with `event_type` |
| `mcc/cmd` | HA → machine | no | `stop`, `pause`, `tare`, `cancel`, `refresh` or `start`, as a bare word or `{"command": "stop"}` |

## State payload

```json
{
  "status": "running",
  "recipe": "Simple vegetable soup",
  "step": "7/10",
  "step_text": "Cook the soup.",
  "temperature": 95,
  "target_temperature": 100,
  "speed": 1,
  "reverse": "off",
  "finish": "2026-11-20T18:42:10Z",
  "weight": 1040,
  "lid": "closed",
  "error": "none",
  "remote_start": "off",
  "remote_program": null,
  "remote_until": null,
  "recipe_id": "sample-vegetable-soup-en",
  "run_total": 1200,
  "run_left": 712
}
```

| Key | Values |
|---|---|
| `status` | `disconnected`, `idle`, `running`, `paused`, `finished`, `error` |
| `recipe`, `step`, `step_text` | `null` when no recipe is loaded |
| `temperature` | bowl temperature in °C. The board reports it in the same steps as the temperature dial (37, 45, 50 … 130); `0` means below 37 °C |
| `target_temperature`, `speed` | the running request, `0` when idle |
| `reverse` | `on` / `off` |
| `finish` | UTC time when the current run ends, or `null` |
| `weight` | grams on the scale for Home Assistant: `null` below 10 g (empty scale, right after a tare, bowl lifted), and a new value only after a change of 10 g or more, so the history only records real weighings. The live reading is on `mcc/scale` |
| `lid` | `open` / `closed` |
| `error` | `none`, or the power board's error in lower case (e.g. `motor_overheat`) |
| `remote_start` | `ready` while a remote-start permit is valid, else `off` |
| `remote_program`, `remote_until` | what was permitted (text) and when the permit ends (UTC), or `null` |
| `recipe_id` | id of the loaded recipe, or `null` (the phone app uses it to show the recipe) |
| `servings` | who the loaded recipe is cooked for, as the recipe words it (`"6 portions"`); its amounts are already scaled to it. `null` without a recipe |
| `run_total`, `run_left` | length and seconds left of the running machine step or manual run, or `null` when nothing runs |

The payload is `MachineState` in `link/src/main/kotlin/mcc/link/Link.kt`, the same class the phone app reads.

## Other topics

| Topic | Retained | Content |
|---|---|---|
| `mcc/scale` | no | live scale reading in grams (or `null`), up to 4 times a second, for the phone app's live readout. Home Assistant does not use it |
| `mcc/recipe_image` | yes | URL of the loaded recipe's photo; published only when a recipe with a photo is loaded (otherwise it keeps the last one) |

## Entities

Entity IDs are `<platform>.<area>_monsieur_cuisine_<key>`, e.g. `sensor.kitchen_monsieur_cuisine_status`.
Without an Area they are `sensor.monsieur_cuisine_status`, and so on.

| Key | Platform | Notes |
|---|---|---|
| `status` | sensor (enum) | |
| `recipe` | sensor | |
| `step` | sensor | attribute `text` = the step's text |
| `temperature`, `target_temperature` | sensor, °C | unknown instead of 0 (below 37 °C / not heating) |
| `speed` | sensor | |
| `reverse` | binary_sensor | |
| `finish` | sensor (timestamp) | |
| `weight` | sensor, g | named "Scale" |
| `lid` | binary_sensor (opening) | |
| `error` | sensor | |
| `remote_start` | binary_sensor | attributes `program`, `until` |
| `event` | event | `event_type` plus the event's fields as attributes (see Events) |
| `running` | binary_sensor (running) | on while the motor or heater runs |
| `problem` | binary_sensor (problem) | on while the board reports an error |
| `time_left` | sensor (duration, s) | seconds left of the running step or manual run, `0` when idle |
| `progress` | sensor, % | how far the running step or manual run is |
| `recipe_progress` | sensor, % | steps done out of the recipe's steps |
| `recipe_image` | image | the loaded recipe's photo, fetched by Home Assistant from the recipe repository (URL on `mcc/recipe_image`, only set for recipes with a photo) |
| `load_recipe` | text | shows the loaded recipe's id; setting it sends `{"command":"load","recipe":"<id>"}` (opens it on the machine, never starts it) |
| `start` | button | publishes `start` to `mcc/cmd`; only does something while `remote_start` is on |
| `pause` | button | publishes `pause` |
| `stop` | button | publishes `stop` |
| `tare` | button | publishes `tare` (named "Tare scale") |
| `cancel` | button | publishes `cancel` (named "Cancel recipe") |

`error` is a diagnostic entity. The payload is built by `Discovery.config()` in `link/src/main/kotlin/mcc/link/Discovery.kt`;
`./gradlew :link:test` writes it to `link/build/discovery.json`.

## Commands

| Command | Effect |
|---|---|
| `stop` | stops the motor and heater immediately |
| `pause` | pauses a recipe step or a manual run (turbo just stops): `status` becomes `paused`, `run_left` keeps the time left and `finish` is `null`. Resume at the machine (Continue; the controls must be unlocked) or permit Home Assistant to continue it (Remote on the paused card, then **Start**). A paused manual run is dropped after an hour (`stopped`, reason `pause_expired`) |
| `tare` | tares the scale |
| `start` | starts the run permitted at the machine (see Remote start); ignored without a valid permit |
| `cancel` | closes the loaded recipe; stops the machine first if a step runs. Works while the controls are locked, like `stop` |
| `refresh` | re-reads the recipe server's index (sent by the phone after it publishes a recipe). Changes nothing else |
| `{"command":"load","recipe":"<id>","servings":6}` | `servings` is optional (1–99): scales the amounts for that many people; times and temperatures stay as written |
| `{"command":"load","recipe":"<id>"}` | opens that recipe on the machine's Cook screen, ready to cook; **never starts it**. Refused while the machine runs or another recipe is under way, or when *Recipes from the phone* is off on the machine |

Anything else is ignored and logged.

## Events

`mcc/event` carries things that happen once, for notifications. They are not retained, so a Home Assistant
restart never replays them, and they are dropped while the broker is unreachable (the retained state still shows
where things are). Every event has `event_type` and `at` (UTC time).

| `event_type` | When | Fields |
|---|---|---|
| `finished` | the last step of a recipe ends | `recipe` |
| `needs_you` | a machine step ends and the recipe moved on (the next step always waits for someone) | `recipe`, `step` (`3/7`), `text` |
| `weighed` | the ingredient of a weigh step reaches its amount | `recipe`, `grams` |
| `run_done` | a manual run (not a recipe step) ends on time | |
| `stopped` | a run ended before its time: `stop_button` (STOP at the machine), `remote_stop` (Home Assistant or the phone), `recipe_cancelled` (a recipe under way was cancelled), `pause_expired`, `lid_opened`, `comm_lost`, `max_runtime`, `board_error`. Turbo (hold to run) never sends it | `reason`, `recipe` |
| `paused` | a recipe step or manual run paused with its time kept: `reason` = `remote`, `machine`, `stop_button` / `remote_stop` (STOP during a recipe step pauses it) | `reason`, `recipe`, `step` |
| `error` | the board reports an error | `error`, `recipe` |

Example: `{"event_type": "needs_you", "recipe": "Risotto", "step": "4/9", "text": "Add the rice", "at": "2026-11-20T12:41:07Z"}`

## Phone notifications

With the Home Assistant Companion app on the phone, import the blueprint
[`home-assistant/mcc_notify.yaml`](home-assistant/mcc_notify.yaml) (Settings → Automations & scenes →
Blueprints → Import blueprint, using the raw file URL), then create an automation from it: pick the
**Monsieur Cuisine Event** entity and your phone. Notifications replace each other (one tag), so the phone shows
only the latest.

## Example automation

```yaml
automation:
  - alias: "Monsieur Cuisine needs you"
    triggers:
      - trigger: state
        entity_id: event.kitchen_monsieur_cuisine_event # the state is the event's time, so every event fires
    conditions:
      - "{{ trigger.from_state.state != 'unavailable' and trigger.to_state.attributes.event_type == 'needs_you' }}"
    actions:
      - action: notify.mobile_app_my_phone
        data:
          title: "The recipe needs you"
          message: "{{ trigger.to_state.attributes.text or 'Next step' }}"
```
