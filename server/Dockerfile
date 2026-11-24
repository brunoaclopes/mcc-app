# mcc-app server: recipe uploads and video transcripts. Build: docker compose build (or docker build .)
FROM python:3.13-alpine

# APP_UID/APP_GID: the user the container runs as. ssh needs it to exist, so it is created here: match it to the
# owner of your data folder. User 568 (TrueNAS apps) always exists too, so the published image
# (ghcr.io/brunoaclopes/mcc-server) runs as 1000 or, with `user: "568:568"`, on TrueNAS.
ARG APP_UID=1000
ARG APP_GID=1000

# git + ssh: push to the recipe repository; ffmpeg: audio for speech-to-text; deno: yt-dlp's JavaScript runtime
# for YouTube. yt-dlp is unpinned on purpose: rebuilding the image updates it (see README.md).
RUN apk add --no-cache git openssh-client ffmpeg deno \
 && pip install --no-cache-dir --root-user-action=ignore "pillow==11.3.0" "yt-dlp[default]" \
 && addgroup -g "$APP_GID" mcc && adduser -D -H -u "$APP_UID" -G mcc -h /tmp -s /sbin/nologin mcc \
 && if [ "$APP_UID" != 568 ]; then addgroup -g 568 apps && adduser -D -H -u 568 -G apps -h /tmp -s /sbin/nologin apps; fi

COPY app.py common.py settings.py transcript.py upload.py wyoming.py entrypoint.sh /app/
USER ${APP_UID}:${APP_GID}
EXPOSE 8080
HEALTHCHECK --interval=60s --timeout=5s CMD wget -q -O /dev/null "http://127.0.0.1:${MCC_PORT:-8080}/health" || exit 1
ENTRYPOINT ["/bin/sh", "/app/entrypoint.sh"]
