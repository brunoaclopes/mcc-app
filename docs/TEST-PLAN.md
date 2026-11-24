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
| T8 | empty | turbo, held 2 s (the switch has since been removed: turbo is always available) | short burst | turbo frame (C6) speed byte unverified |
| T8b | 500 g water | program "Boiled eggs, soft" (speed 0 with heat) | heats without the blade turning, boils | heat with motor off in normal mode |
| T8c | 1000 g water | program "Clean the bowl" | warm soak, then short bursts up to speed 6, no foam out of the lid | cleaning program |
| T9 | dough / water | knead, roast, steam, one per session, enabled one at a time | like the stock app | compare against stock-app sniffs |
| T10 | empty | Manual, time 0:00, speed 2, 20 s, then STOP | counts up from 0:00, stops on STOP; Pause from the phone keeps the count | stopwatch run |
| T11 | 1500 g cold water | a recipe step "100 °C, 2 min, speed 1" | after 2 min it says "heating on to 100°" and moves on once the bowl reads 95 °C or more | heat extension |
| T12 | vegetables | Programs → Any-vegetable soup, mixed, smooth, onion base; put in ~800 g of vegetables without weighing them | Next works it out: the water step asks for the matching amount, the ingredients show the weight, cooks, blends 4→10 gradually; with 100 g or 1.5 kg it asks to add or take some out | automatic soup |
| T13 | 300 g water | a recipe with a 250 g weigh step, hands-free weighing on | moves on 2–3 s after the amount is in; the next machine step waits for Start | hands-free weighing |
| T14 | none (phone only) | phone: import a recipe with normal cooking steps (a page or video), Add to the recipe server | accepted (no "bad mode"), shows on the machine within about a minute; a failed add shows its reason at the top of the review | phone upload |

**T3 result (2026-11-21):** the board has its own watchdog. With the app killed mid-run (speed 1), the motor stopped almost
immediately, so a crashed or killed tablet app does not leave the motor running.

## Results 2026-11-21 (real machine, supervised)

| Test | Result |
|------|--------|
| Manual pause from the phone, Continuar, PARAR | pass: `paused` (remote) then `stopped` (stop_button); the Manual screen now shows the paused run |
| Recipe step: phone Pause, Iniciar resumes, PARAR pauses, phone Cancel | pass: `paused` remote, `paused` stop_button, `stopped` recipe_cancelled |
| Open on the machine from the phone at 6 servings | pass: amounts scaled on the machine; fixed the ingredients sheet staying open after a remote cancel |
| Phone "Ready" notification swiped away | pass: stays away until cooking starts |
| T3 watchdog | pass: board stops by itself almost at once |
| T8 turbo | pass: short burst, stops on release |
| T8c cleaning | pass: soak + bursts to speed 6, no foam; program now one wash step + rinse |
| T9a knead | pass: 15 s reverse, 35 s rest, 70 s forward (stock preset) |
| T9b roast | pass after fix: the board's roast mode does not stir by itself; the app now repeats 5 s stir + 30 s heat |
| T9c steam | pass: boils and steams at speed 1 (now in reverse, as the stock preset) |

Fixed during these runs: special modes showed "not enabled" on the Cook screen; the empty-bowl weight is kept across app
restarts (same boot only); heated restarts right after a pause no longer fail the weight check; the running view can be
reopened from the status pill; "stopped" only when the recipe ran something; "weighed" only on a steady reading.
