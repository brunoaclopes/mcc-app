# Setup

From a stock Monsieur Cuisine Connect to mcc-app running on it. Allow an afternoon the first time.

## What you need

- The machine (SKMC 1200), a small Torx screwdriver, and a USB-A to USB-A cable (or adapter plus extension).
- A Linux or Windows PC with `adb` and SP Flash Tool. You need these for step 1 only.
- To build the app: JDK 17, the Android SDK (platform 34) and NDK `26.3.11579264`. Android Studio installs all of
  them, or you can use the command-line tools.

> [!CAUTION]
> The service cover has two USB ports. **Only use the right-hand one, labelled "Android USB".** The left one is
> wired to the power board, and connecting a PC to it can destroy the machine.

## 1. Get ADB on the machine

Follow the community guide:
**[EliasKotlyar/Monsieur-Cuisine-Connect-Hack](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack)**
([English](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/blob/master/install_en/readme.md),
[Deutsch](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/blob/master/install_de/readme.md)).
It uses SP Flash Tool to boot TWRP, then adds ADB properties to `/system/build.prop`.

Tips from doing it on firmware 4921 / app v292:

- **Back up first**, as the guide says: at least `boot` and `recovery`, and ideally the whole eMMC. SP Flash Tool's
  readback is slow (about 2 MB/s) but reliable. On Linux, remove `modemmanager` first, or it grabs the port.
- After boot, USB sometimes appears only after you unplug and re-plug the cable. `adb devices` must show the
  machine, and the screen asks you to accept the PC's key the first time.
- Optional, in `/system/build.prop` (from TWRP):
  - `qemu.hw.mainkeys=0` shows the Android navigation bar. mcc-app is full screen; swipe up from the bottom
    edge to reach it.
  - `service.adb.tcp.port=5555` turns on ADB over Wi-Fi (`adb connect <machine-ip>:5555`), so you can close the
    cover. Only do this on a network you trust: anyone on it can then reach ADB.
- Root (e.g. Magisk) is **not** needed for mcc-app. It only helps with debugging.
- The stock updaters (`com.adups.tgifota`, `de.silpion.mcupdater`) can overwrite the system. Consider blocking
  them on your router.

## 2. Build

```sh
git clone <this repository> && cd mcc-app
echo "sdk.dir=$HOME/Android/Sdk" > local.properties     # or set ANDROID_HOME
./gradlew :machine:test :recipe:test                     # pure Kotlin tests, no device needed
./gradlew :app:assembleDebug                             # app/build/outputs/apk/debug/app-debug.apk
```

The APK is signed with the **public AOSP platform test key** in `signing/`. The machine's firmware is signed with
the same key, so the app may run as `android.uid.system`, which can open `/dev/ttyMT0` and the LED files
without root. That key is public knowledge and gives no access to anything else; see `signing/README.txt`.

## 3. Install and run

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop de.silpion.mc2         # the stock app must not hold the serial port
adb shell am start -n mcc.app/.MainActivity
```

If the stock app's machine service starts again, for example after a reboot, mcc-app detects it, shows
"Stock app" in the top bar and does not touch the port. Force-stop the stock app again.

**Make it the home screen** (optional): mcc-app can act as the launcher. Choose it in Android
**Settings → Home**. On this firmware the "complete action using" chooser always picks the stock app, so use the
Settings screen. To go back, pick the stock app there again, or uninstall mcc-app
(`adb uninstall mcc.app`). The stock app itself is never modified.

## 4. First run

1. The app opens **locked**. Tap **Unlock** (top right) and confirm.
2. With the empty bowl and lid in place: **Manual → Empty bowl**. Heating stays refused until the app knows
   the empty-bowl weight.
3. Do the supervised tests in [TEST-PLAN.md](TEST-PLAN.md), in order, before cooking anything.

Settings → **Language** (Português / English) and **Appearance** (Auto / Day / Night). For Auto, enter your
location as `latitude, longitude` so sunrise and sunset are right. Without it, the app estimates them from the
time zone.

## 5. Recipes (optional)

The machine downloads recipes from any static web server: a NAS, a Raspberry Pi, or `python3 -m http.server`.

```sh
cd recipe-repo
python3 tools/build_index.py              # validates recipes/*.json and writes index.json
python3 -m http.server 8088               # quick test from a laptop
```

On the machine: Settings → **Recipe server** → `http://<server-ip>:8088/` → **Save & sync**. Everything
downloaded is cached, so it keeps working offline. To add recipes, write your own or convert them (see
[../recipe-repo/README.md](../recipe-repo/README.md)).

For a permanent server, any web server that can serve the folder works, for example an `nginx:alpine` container
with the folder mounted read-only at `/usr/share/nginx/html`.

## 6. Home Assistant (optional)

You need an MQTT broker (e.g. the Mosquitto add-on) and the MQTT integration in Home Assistant. On the machine:
Settings → **Home Assistant** → broker address, port, user, password and, optionally, an **Area** such as
`Kitchen` → turn it on → **Save**. The device appears in Home Assistant by itself (MQTT discovery). Topics and
payloads: [HOME-ASSISTANT.md](HOME-ASSISTANT.md).

## Going back to stock

`adb uninstall mcc.app` and reboot. To undo step 1, restore your backups as described in the hack repository.
