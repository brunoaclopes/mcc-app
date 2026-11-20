# Supervised live tests (do these together, in order)

Rules for every test:
- Someone watches the machine.
- Hand ready on the app's red STOP button. The power switch is the last resort.
- Lid on and locked.

Setup: open the app → tap **Unlock** (top right) → confirm. Then **Manual** for T1–T6.

| # | Bowl | Settings | Expected | Checks |
|---|------|----------|----------|--------|
| T1 | empty | speed 1, 10 s | motor turns ~10 s and stops by itself | first motor run from our own app |
| T2 | empty | speed 2, 60 s; press STOP after ~5 s | stops immediately | STOP path |
| T3 | empty | speed 1, 60 s; from the laptop run `adb shell am force-stop mcc.app` after ~5 s | **unknown**: does the board stop when command frames stop, and how fast? | firmware watchdog; decides how much we rely on our own timer |
| T4 | empty | speed 2, reverse, 10 s | turns the other way | direction byte |
| T5 | empty | speed 1, 50 °C | refused: "set the empty-bowl reference" | heat interlock |
| T6 | empty → press "Empty bowl", then add 500 g water | speed 1, 50 °C, 3 min | heats, temperature rises to 50, stops after 3 min | first heat from our app |
| T7 | water | recipe step test: any recipe with only `run` steps | Start → runs → advances to the next step | recipe runner |
| T8 | empty | turbo, held 2 s (switch on Settings → Machine → Turbo button first) | short burst | turbo frame (C6) speed byte unverified |
| T8b | 500 g water | program "Boiled eggs, soft" (speed 0 with heat) | heats without the blade turning, boils | heat with motor off in normal mode |
| T8c | 1000 g water | program "Clean the bowl" | warm soak, then short bursts up to speed 6, no foam out of the lid | cleaning program |
| T9 | dough / water | knead, roast, steam, one per session, enabled one at a time | like the stock app | compare against stock-app sniffs |

After T3: if the board does NOT stop by itself, then the tablet app is the only thing ending a run.
In that case, keep runs short until we decide on an extra safeguard (e.g. a watchdog process).
