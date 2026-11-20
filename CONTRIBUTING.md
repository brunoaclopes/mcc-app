# Contributing

Thanks for helping! A few ground rules, because this software drives a motor and a heater.

- **Safety limits live in one place**: `machine/src/main/kotlin/mcc/machine/MachineController.kt` (`SafetyLimits`).
  Changes there need a test in `machine/src/test` and a clear reason in the pull request.
- **No machine command without a person present.** Any change that alters the bytes sent to the power board must
  say how it was tested (see `docs/TEST-PLAN.md`). Enabling a new run mode (turbo, knead, roast, steam) needs a
  supervised live test first.
- **No stock content.** Don't commit code, recipes or images taken from the stock firmware or the stock app.
  Recipes you wrote yourself are welcome in `recipe-repo/recipes/`.
- **Translations**: every text is in `app/src/main/kotlin/mcc/app/i18n/Strings.kt`. A new language is a new
  `object Xx : Strings()`; the compiler tells you what's missing.
- Keep the UI cheap: the tablet is a 2015 MT6580 with a Mali-400 GPU. See "Performance rules" in
  `docs/ARCHITECTURE.md`.

Run before sending a pull request:

    ./gradlew :machine:test :recipe:test :app:assembleDebug
    (cd recipe-repo && python3 -m unittest discover -s tests && python3 tools/build_index.py)
