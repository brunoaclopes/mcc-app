"""POST /v1/transcript: what is said in a cooking video, so the AI can write the recipe from it.

1. yt-dlp reads the video's details and subtitles: subtitles written by people before automatic ones, and the
   video's own language before a translation.
2. No subtitles: the audio (at most MCC_MAX_AUDIO_MINUTES) goes through ffmpeg to a Wyoming speech-to-text server
   (MCC_WHISPER_HOST, e.g. Home Assistant's Whisper add-on). The audio is deleted afterwards.
3. Otherwise only the description comes back, with "source": "none".
Results are cached per video for MCC_CACHE_HOURS, and one transcription runs at a time.
"""
from __future__ import annotations

import hashlib
import html
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

import wyoming
from common import Problem, log, public_host

YOUTUBE_ID = re.compile(r"(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})")
ANNOTATION = re.compile(r"^[\[(♪].*[\])♪]$|^♪+$")  # [Music], (risos), ♪ ... ♪


def video_key(url: str) -> str:
    m = YOUTUBE_ID.search(url)
    return f"youtube-{m.group(1)}" if m else "url-" + hashlib.sha1(url.encode()).hexdigest()[:20]


def _base(lang: str) -> str:
    return (lang or "").split("-")[0].split("_")[0].lower()


def choose_track(info: dict):
    """Returns (subtitle key, automatic?) or None. Order: subtitles by people in the video's language, then pt,
    then en; then automatic captions of what is actually said (YouTube marks them "<lang>-orig"); never a machine
    translation while the original is there."""
    orig = _base(info.get("language") or "")
    wanted = [lang for lang in dict.fromkeys((orig, "pt", "en")) if lang]
    manual = {k: v for k, v in (info.get("subtitles") or {}).items() if v and k != "live_chat"}
    auto = {k: v for k, v in (info.get("automatic_captions") or {}).items() if v}

    def rank(k):  # "pt" before "pt-PT" before "pt-BR"
        low = k.lower()
        return (low != _base(low), "br" in low, low)

    for lang in wanted:
        for k in sorted(manual, key=rank):
            if _base(k) == lang:
                return k, False
    origs = sorted(k for k in auto if k.endswith("-orig"))
    for lang in wanted:
        for k in origs:
            if _base(k) == lang:
                return k, True
    if origs:
        return origs[0], True  # what is said, even when it's neither pt nor en
    for lang in wanted:  # sites without the -orig marker: their automatic track is the original
        if lang in auto:
            return lang, True
    return None


def clean(lines) -> str:
    out = []
    for line in lines:
        line = re.sub(r"\s+", " ", html.unescape(line)).strip()
        if not line or ANNOTATION.match(line) or (out and out[-1] == line):
            continue
        out.append(line)
    return "\n".join(out)


def parse_json3(text: str) -> str:
    events = json.loads(text).get("events") or []
    return clean("".join(s.get("utf8", "") for s in ev.get("segs") or []) for ev in events if ev.get("segs"))


def parse_vtt(text: str) -> str:
    lines = []
    for raw in text.splitlines():
        s = raw.strip()
        if (not s or s.startswith(("WEBVTT", "NOTE", "Kind:", "Language:", "STYLE", "REGION")) or "-->" in s
                or s.isdigit()):
            continue
        lines.append(re.sub(r"<[^>]+>", "", s))  # inline timestamps and styling tags
    return clean(lines)


def subtitle_text(path: Path) -> str:
    raw = path.read_text(encoding="utf-8", errors="replace")
    return parse_json3(raw) if path.suffix == ".json3" else parse_vtt(raw)


class Transcriber:
    def __init__(self, s):
        self.s = s
        self.lock = threading.Lock()  # one transcription at a time
        self.cache_dir = s.data_dir / "cache" / "transcripts"
        self.env = {**os.environ, "HOME": "/tmp", "XDG_CACHE_HOME": str(s.data_dir / "cache")}

    # ------------------------------------------------------------ cache

    def cached(self, key: str):
        p = self.cache_dir / f"{key}.json"
        try:
            entry = json.loads(p.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None
        if time.time() - entry.get("saved", 0) > self.s.cache_hours * 3600:
            p.unlink(missing_ok=True)
            return None
        return entry.get("result")

    def store(self, key: str, result: dict):
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        tmp = self.cache_dir / f".{key}.{uuid.uuid4().hex}"
        tmp.write_text(json.dumps({"saved": time.time(), "result": result}, ensure_ascii=False), encoding="utf-8")
        tmp.replace(self.cache_dir / f"{key}.json")

    # ------------------------------------------------------------ request

    def get(self, url) -> dict:
        if not isinstance(url, str) or not url.startswith(("http://", "https://")):
            raise Problem(422, "send the video's web address as url")
        if not public_host(url):
            raise Problem(422, "that is not a public video address")
        key = video_key(url)
        hit = self.cached(key)
        if hit:
            return hit
        deadline = time.monotonic() + self.s.transcript_timeout
        if not self.lock.acquire(timeout=self.s.transcript_timeout):
            raise Problem(504, "another video is being transcribed; try again in a moment")
        try:
            hit = self.cached(key)
            if hit:
                return hit
            tmp = self.s.data_dir / "tmp" / uuid.uuid4().hex
            tmp.mkdir(parents=True)
            try:
                result, keep = self.transcribe(url, tmp, deadline)
            finally:
                shutil.rmtree(tmp, ignore_errors=True)  # audio, subtitles and details are never kept
            if keep:
                self.store(key, result)
            return result
        finally:
            self.lock.release()

    def transcribe(self, url: str, tmp: Path, deadline: float):
        """Returns (result, whether to cache it)."""
        info = self.info(url, tmp, deadline)
        result = {"title": info.get("title") or "", "author": info.get("uploader") or info.get("channel") or "",
                  "description": info.get("description") or "", "language": _base(info.get("language") or ""),
                  "transcript": "", "source": "none", "thumbnail": info.get("thumbnail") or ""}
        track = choose_track(info)
        if track:
            text = self.subtitles(track, tmp, deadline)
            if text:
                return {**result, "language": _base(track[0]), "transcript": text, "source": "subtitles"}, True
        if not self.s.whisper_host:
            return result, True
        duration = info.get("duration") or 0
        if duration > self.s.max_audio_minutes * 60:
            log(f"no subtitles and {duration // 60} min of audio (limit {self.s.max_audio_minutes}): description only")
            return result, True
        try:
            text, lang = self.whisper(info, tmp, deadline)
        except Problem:
            raise
        except Exception as e:  # noqa: BLE001 - the server may be back soon, so this answer is not cached
            log(f"speech-to-text failed: {type(e).__name__}: {str(e)[:200]}")
            return result, False
        if text:
            return {**result, "language": _base(lang) or result["language"], "transcript": text,
                    "source": "whisper"}, True
        return result, True

    # ------------------------------------------------------------ steps

    def run(self, cmd, deadline, **kw):
        left = deadline - time.monotonic()
        if left <= 0:
            raise Problem(504, "reading the video took too long")
        try:
            return subprocess.run(cmd, env=self.env, capture_output=True, text=True, timeout=left, **kw)
        except subprocess.TimeoutExpired:
            raise Problem(504, "reading the video took too long") from None

    def ytdlp(self, *args):
        return [sys.executable, "-m", "yt_dlp", "--ignore-config", "--no-playlist", "--no-progress",
                "--socket-timeout", "30", *args]

    def info(self, url: str, tmp: Path, deadline: float) -> dict:
        r = self.run(self.ytdlp("--dump-single-json", "--skip-download", url), deadline)
        if r.returncode:
            err = next((ln for ln in reversed(r.stderr.splitlines()) if ln.startswith("ERROR:")), r.stderr.strip())
            err = re.sub(r"^ERROR:\s*(\[[^\]]+\]\s*)?([A-Za-z0-9_-]+:\s*)?", "", err)[:150]
            raise Problem(422, f"can't read this video: {err or 'yt-dlp failed'}")
        info = json.loads(r.stdout)
        if info.get("_type") in ("playlist", "multi_video") or not (info.get("formats") or info.get("url")):
            raise Problem(422, "that address is not a single video")
        (tmp / "info.json").write_text(r.stdout, encoding="utf-8")
        return info

    def subtitles(self, track, tmp: Path, deadline: float) -> str:
        key, auto = track
        r = self.run(self.ytdlp("--load-info-json", str(tmp / "info.json"), "--skip-download",
                                "--write-auto-subs" if auto else "--write-subs", "--sub-langs", key,
                                "--sub-format", "json3/vtt/best", "--paths", f"temp:{tmp}",
                                "-o", str(tmp / "sub.%(ext)s")), deadline)
        files = sorted(tmp.glob("sub.*.json3")) + sorted(tmp.glob("sub.*.vtt"))
        if r.returncode or not files:
            log(f"subtitles {key} not downloaded: {r.stderr.strip()[-200:]}")
            return ""
        return subtitle_text(files[0])

    def whisper(self, info: dict, tmp: Path, deadline: float):
        # is the speech-to-text server there at all? (no point downloading the audio otherwise)
        socket.create_connection((self.s.whisper_host, self.s.whisper_port), timeout=10).close()
        r = self.run(self.ytdlp("--load-info-json", str(tmp / "info.json"), "-f", "bestaudio/best",
                                "--max-filesize", "200M", "--no-part", "--paths", f"temp:{tmp}",
                                "-o", str(tmp / "audio.%(ext)s")), deadline)
        audio = next(iter(sorted(tmp.glob("audio.*"))), None)
        if r.returncode or not audio:
            raise OSError(f"audio not downloaded: {r.stderr.strip()[-200:]}")
        language = self.s.whisper_language or _base(info.get("language") or "")
        ff = subprocess.Popen(["ffmpeg", "-nostdin", "-v", "error", "-i", str(audio),
                               "-t", str(self.s.max_audio_minutes * 60), "-ac", "1", "-ar", str(wyoming.RATE),
                               "-f", "s16le", "-"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        try:
            left = deadline - time.monotonic()
            if left <= 0:
                raise Problem(504, "reading the video took too long")
            return wyoming.transcribe(self.s.whisper_host, self.s.whisper_port, wyoming.chunks(ff.stdout),
                                      language, timeout=left)
        except socket.timeout:
            raise Problem(504, "speech-to-text took too long") from None
        finally:
            ff.kill()
            ff.wait()
