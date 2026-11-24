"""POST /v1/transcript: what is said in a cooking video, so the AI can write the recipe from it.

1. yt-dlp reads the video's details and subtitles: subtitles written by people before automatic ones, and the
   video's own language before a translation.
2. No subtitles: the audio (at most MCC_MAX_AUDIO_MINUTES) goes through ffmpeg to a Wyoming speech-to-text server
   (MCC_WHISPER_HOST, e.g. Home Assistant's Whisper add-on) in pieces of MCC_WHISPER_CHUNK_SECONDS, one after the
   other. When the next piece would not fit in MCC_TRANSCRIPT_TIMEOUT, the reply has what is done so far with
   "partial": true and the rest is transcribed in the background; the complete transcript then goes to the cache,
   so asking again returns it. The audio is deleted afterwards.
3. Otherwise only the description comes back, with "source": "none".
Results are cached per video for MCC_CACHE_HOURS, and one transcription runs at a time.
"""
from __future__ import annotations

import hashlib
import html
import io
import itertools
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
PIECE_TIMEOUT = 600  # seconds for one piece in the background, where the request's deadline no longer applies


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


class Speech:
    """The outcome of speech-to-text: the text so far, and finish() when the rest still has to be done."""

    def __init__(self, text: str, language: str, finish=None):
        self.text, self.language, self.finish = text, language, finish


class Transcriber:
    def __init__(self, s):
        self.s = s
        self.lock = threading.Lock()  # one transcription at a time (background completions included)
        self.in_progress = {}  # video key -> its partial result, kept up to date while the rest is transcribed
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
            progress = self.in_progress.get(key)
            if progress:  # this video is still being finished in the background: what is done so far
                return dict(progress)
            raise Problem(504, "another video is being transcribed; try again in a moment")
        background = False
        tmp = None
        try:
            hit = self.cached(key)
            if hit:
                return hit
            tmp = self.s.data_dir / "tmp" / uuid.uuid4().hex
            tmp.mkdir(parents=True)
            result, keep, finish = self.transcribe(url, tmp, deadline)
            if finish:
                background = True  # the thread finishes the transcript, caches it, cleans up and releases the lock
                self.in_progress[key] = dict(result)
                threading.Thread(target=self.complete, args=(key, result, finish, tmp), daemon=True).start()
            elif keep:
                self.store(key, result)
            return result
        finally:
            if not background:
                if tmp:
                    shutil.rmtree(tmp, ignore_errors=True)  # audio, subtitles and details are never kept
                self.lock.release()

    def complete(self, key: str, partial: dict, finish, tmp: Path):
        def progress(text):
            self.in_progress[key] = {**partial, "transcript": text}
        try:
            text = finish(progress)
            self.store(key, {**partial, "transcript": text, "partial": False})
            log(f"{key}: transcript completed in the background ({len(text.split())} words)")
        except Exception as e:  # noqa: BLE001 - the next request transcribes again
            log(f"{key}: background transcript failed: {type(e).__name__}: {str(e)[:200]}")
        finally:
            self.in_progress.pop(key, None)
            shutil.rmtree(tmp, ignore_errors=True)
            self.lock.release()

    def transcribe(self, url: str, tmp: Path, deadline: float):
        """Returns (result, whether to cache it, finish-in-background callable or None)."""
        info = self.info(url, tmp, deadline)
        result = {"title": info.get("title") or "", "author": info.get("uploader") or info.get("channel") or "",
                  "description": info.get("description") or "", "language": _base(info.get("language") or ""),
                  "transcript": "", "source": "none", "partial": False, "thumbnail": info.get("thumbnail") or ""}
        track = choose_track(info)
        if track:
            text = self.subtitles(url, track, tmp, deadline)
            if text:
                return {**result, "language": _base(track[0]), "transcript": text, "source": "subtitles"}, True, None
        if not self.s.whisper_host:
            return result, True, None
        duration = info.get("duration") or 0
        if duration > self.s.max_audio_minutes * 60:
            log(f"no subtitles and {duration // 60} min of audio (limit {self.s.max_audio_minutes}): description only")
            return result, True, None
        try:
            speech = self.whisper(url, info, tmp, deadline)
        except Problem:
            raise
        except Exception as e:  # noqa: BLE001 - the server may be back soon, so this answer is not cached
            log(f"speech-to-text failed: {type(e).__name__}: {str(e)[:200]}")
            return result, False, None
        if not speech.text and not speech.finish:
            return result, True, None
        result = {**result, "language": _base(speech.language) or result["language"], "transcript": speech.text,
                  "source": "whisper", "partial": bool(speech.finish)}
        if speech.finish:
            def finish(progress):
                join = lambda rest: "\n".join(t for t in (speech.text, rest) if t)  # noqa: E731
                return join(speech.finish(lambda rest: progress(join(rest))))
            return result, False, finish
        return result, True, None

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

    def fetch(self, url: str, tmp: Path, deadline: float, pattern: str, *args) -> Path | None:
        """Downloads with the details read earlier; when that fails (YouTube sometimes answers 403), once more with
        a fresh yt-dlp run. Returns the first file matching pattern, or None."""
        for source in (("--load-info-json", str(tmp / "info.json")), (url,)):
            r = self.run(self.ytdlp(*args, "--paths", f"temp:{tmp}", *source), deadline)
            found = sorted(tmp.glob(pattern))
            if r.returncode == 0 and found:
                return found[0]
            log(f"download failed ({'saved details' if len(source) == 2 else 'fresh run'}): "
                f"{r.stderr.strip()[-200:]}")
        return None

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

    def subtitles(self, url: str, track, tmp: Path, deadline: float) -> str:
        key, auto = track
        found = self.fetch(url, tmp, deadline, "sub.*", "--skip-download",
                           "--write-auto-subs" if auto else "--write-subs", "--sub-langs", key,
                           "--sub-format", "json3/vtt/best", "-o", str(tmp / "sub.%(ext)s"))
        return subtitle_text(found) if found and found.suffix in (".json3", ".vtt") else ""

    def whisper(self, url: str, info: dict, tmp: Path, deadline: float) -> Speech:
        # is the speech-to-text server there at all? (no point downloading the audio otherwise)
        socket.create_connection((self.s.whisper_host, self.s.whisper_port), timeout=10).close()
        audio = self.fetch(url, tmp, deadline, "audio.*", "-f", "bestaudio/best", "--max-filesize", "200M",
                           "--no-part", "-o", str(tmp / "audio.%(ext)s"))
        if not audio:
            raise OSError("audio not downloaded")
        language = self.s.whisper_language or _base(info.get("language") or "")
        return self.speech(self.pieces(audio), language, deadline)

    def pieces(self, audio: Path):
        """The audio as 16 kHz mono 16-bit PCM, MCC_WHISPER_CHUNK_SECONDS at a time."""
        size = max(5, self.s.whisper_chunk_seconds) * wyoming.RATE * wyoming.WIDTH * wyoming.CHANNELS
        ff = subprocess.Popen(["ffmpeg", "-nostdin", "-v", "error", "-i", str(audio),
                               "-t", str(self.s.max_audio_minutes * 60), "-ac", "1", "-ar", str(wyoming.RATE),
                               "-f", "s16le", "-"], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        try:
            while True:
                piece = ff.stdout.read(size)
                if not piece:
                    return
                yield piece
        finally:
            ff.kill()
            ff.wait()

    def stt(self, piece: bytes, language: str, timeout: float):
        return wyoming.transcribe(self.s.whisper_host, self.s.whisper_port, wyoming.chunks(io.BytesIO(piece)),
                                  language, timeout=timeout)

    def speech(self, pieces, language: str, deadline: float) -> Speech:
        """Transcribes the pieces one after the other. When the next one would not fit before the deadline (twice
        the slowest piece so far, plus a little), stops and returns the rest as a finish() for the background."""
        it = iter(pieces)
        texts, slowest, lang = [], 0.0, language
        for piece in it:
            left = deadline - time.monotonic()
            if texts and left < slowest * 2 + 5:
                return Speech("\n".join(t for t in texts if t), lang, self.rest(piece, it, language))
            started = time.monotonic()
            try:
                text, reported = self.stt(piece, language, timeout=max(1.0, left))
            except (socket.timeout, TimeoutError):
                if not texts:
                    raise Problem(504, "speech-to-text took too long") from None
                return Speech("\n".join(t for t in texts if t), lang, self.rest(piece, it, language))
            slowest = max(slowest, time.monotonic() - started)
            texts.append(text)
            lang = lang or reported
        return Speech("\n".join(t for t in texts if t), lang)

    def rest(self, first: bytes, it, language: str):
        """finish(progress): transcribes the remaining pieces, calling progress(text so far) after each one."""
        def finish(progress=lambda text: None):
            out = []
            for piece in itertools.chain([first], it):  # one piece in memory at a time
                text, _ = self.stt(piece, language, timeout=PIECE_TIMEOUT)
                out.append(text)
                progress("\n".join(t for t in out if t))
            return "\n".join(t for t in out if t)
        return finish
