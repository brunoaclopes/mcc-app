#!/bin/sh
# With MCC_YTDLP_UPDATE=1, installs the newest yt-dlp into the data folder at each start (the image's own copy is
# used when that fails or the setting is off). Useful when YouTube changes and the image hasn't been rebuilt yet.
if [ "${MCC_YTDLP_UPDATE:-0}" = "1" ]; then
  target="${MCC_DATA_DIR:-/data}/pip"
  if HOME=/tmp pip install -q --no-cache-dir --disable-pip-version-check --root-user-action=ignore \
      --upgrade --target "$target" "yt-dlp[default]"; then
    export PYTHONPATH="$target${PYTHONPATH:+:$PYTHONPATH}"
  else
    echo "yt-dlp update failed; using the image's copy" >&2
  fi
fi
exec python3 -u /app/app.py
