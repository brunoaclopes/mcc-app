# Security policy

This software switches a 1000 W heater and a blade motor, so safety problems count as security problems.

## Reporting

Report privately: **Security → Report a vulnerability** on this repository (GitHub's private vulnerability
reporting). Please don't open a public issue for anything that could hurt someone or expose a home network.

Most urgent, in this order:

1. The machine moving or heating when it shouldn't: a run that starts without the user's hold on Start, a limit in
   `SafetyLimits` that can be exceeded, a stop that doesn't stop.
2. Anything that lets someone else start or change a run: over MQTT, the phone app, an imported recipe or an AI
   reply.
3. The server (`server/`): getting around its token, reaching the local network through it, writing outside the
   recipe repository.
4. Leaks: tokens, keys or passwords in the code, the apps' logs or the release files.

You'll get an answer within a week. Fixes go out as a new release; the release notes say what was fixed once
people have had time to update.

## Supported versions

Only the [latest release](https://github.com/brunoaclopes/mcc-app/releases/latest) and `master` get fixes.

## Not a vulnerability

- The AOSP platform test key in `signing/` is public on purpose (see `signing/README.txt`).
- The services are meant for your home network only. Putting the MQTT broker, the recipe server, the upload
  service or the AI server on the internet without your own protection is outside this project.
