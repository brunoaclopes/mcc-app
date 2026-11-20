# Tablet ↔ power-board protocol

The Android tablet talks to the power board (motor, heater, scale, lid sensor) over a UART. Everything below was
worked out by reading the stock app's behaviour and by capturing its traffic live on power-board firmware
**4921** (stock app v292). The captures are in `machine/src/test/resources/captures/`, and the tests replay them.
The code is in `machine/src/main/kotlin/mcc/machine/Protocol.kt`.

The [hack repository's protocol notes](https://github.com/EliasKotlyar/Monsieur-Cuisine-Connect-Hack/blob/master/informations/protocol.md)
were the starting point. The main correction is that `C6` is **turbo**, not "turn the motor once".

## Port and timing

- `/dev/ttyMT0`, **4800 baud, 8N1**. The device node is world-writable, so any app that the firmware lets open it
  can drive the machine. That's why mcc-app refuses to open it while the stock machine service is running.
- The tablet sends a command frame **every 100 ms**, repeating the full desired state each time.
- The board answers with a status frame about **every 500 ms**, often two per `read()`.
- If no valid status frame arrives for 1.5 s, mcc-app stops. Whether the board stops on its own when command frames
  stop is **not yet known** (test T3 in [TEST-PLAN.md](TEST-PLAN.md)).

## Command frame (tablet → board, 15 bytes)

| Byte | Meaning |
|---|---|
| 0–2 | `55 0F A1` (start, length 15, type) |
| 3 | work command: `00` off, `01` run (motor and/or heater), `C6` turbo, `03` steam, `04` roast ("toast"), `05` knead, `0F` sleep |
| 4 | `A9` = tare the scale, sent for **one** frame only; otherwise `00` |
| 5 | speed level 0–10 |
| 6 | temperature level 0–19 (table below) |
| 7–10 | `00` |
| 11 | direction: `00` normal, `01` reverse |
| 12 | scale calibration: `00` none, `E6` enter, `E7` user calibration, `E9` start (bytes 3–11 are 0 then) |
| 13 | checksum = sum of bytes 0–12, & `FF` |
| 14 | `AA` |

Stop = work command `00` with speed and temperature 0. Example, run at speed 1 without heat:
`55 0F A1 01 00 01 00 00 00 00 00 00 00 07 AA`.

## Status frame (board → tablet, 27 bytes)

Valid when byte 0 = `55`, byte 1 = `1B`, byte 2 = `B1`, byte 25 = sum of bytes 0–24 & `FF`, byte 26 = `AA`.

| Byte | Meaning |
|---|---|
| 3 | bits: 0–1 state (0 off, 1 running, 2 heating only, 3 turbo), 2 scale stable, 3 reverse, 4 error present, 5 bowl above target temperature, 6 temperature sensor present, **7 lid open** |
| 4 | extended state: 3 steam, 4 roast, 5 knead |
| 5 | error code: 1 serial communication, 3 overvoltage, 4 undervoltage, 5 motor, 6 no water (dry heating), 7 motor overheat, 8 max runtime exceeded |
| 6 | target speed level |
| 7–8 | target RPM, big-endian (559 at speed 1, 1440 at speed 3) |
| 9–10 | actual RPM, but **always 0 on firmware 4921** |
| 11 | target temperature level |
| 12–13 | not a setpoint on firmware 4921 (meaning unknown) |
| 14–15 | bowl temperature in °C, big-endian, rounded down to the level table (0 below 37 °C) |
| 16–19 | scale, signed 32-bit big-endian, **grams** (the bowl and lid weigh about 2.1 kg before a tare; 0 while the motor runs) |
| 20–21 | power-board firmware version (e.g. 4921) |
| 22 | scale bits: 0 calibrating, 1 normal, 2 overload |
| 23 | calibration step |
| 24 | unknown |

## Temperature levels

| Level | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| °C | off | 37 | 45 | 50 | 55 | 60 | 65 | 70 | 75 | 80 | 85 | 90 | 95 | 100 | 105 | 110 | 115 | 120 | 125 | 130 |

Level 3 = 50 °C was verified live. mcc-app rounds a requested temperature **down** to a level; the stock app
rounds up.

## Limits the stock app applies

The board has its own protections (lid, dry heating, overheat, max runtime), but the stock app also enforces the
limits below, and mcc-app does the same in `SafetyLimits`:

- speed 0–10; at most 3 while heating; at most 3 in reverse (4 when kneading);
- at most 99 min, and at most 15 min above speed 6;
- turbo at most 600 s, and not when the bowl is at 60 °C or more;
- kneading at most 600 g; at most 130 °C.

## Other hardware on the tablet

- **RGB LED**: `/sys/class/leds/{red,green,blue}/brightness`, 0–255, writable by uid system. The stock idle
  colour is (0, 0, 50).
- **Jog knob**: key events 136 (turn right), 137 (turn left) and 23 (push).
