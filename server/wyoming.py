"""Minimal Wyoming protocol client for speech-to-text (e.g. Home Assistant's Whisper add-on, TCP 10300).

An event is one JSON line {"type", "version", "data_length", "payload_length"} followed by data_length bytes of JSON
data and payload_length bytes of payload. For speech-to-text the client sends transcribe, audio-start, audio-chunk
(raw PCM) ... audio-stop, and the server answers with transcript {"text"}.
"""
from __future__ import annotations

import json
import socket

VERSION = "1.5.4"
RATE, WIDTH, CHANNELS = 16000, 2, 1  # 16 kHz, 16-bit, mono
CHUNK = RATE * WIDTH * CHANNELS  # one second of audio per audio-chunk


def write_event(f, etype: str, data: dict | None = None, payload: bytes = b""):
    header = {"type": etype, "version": VERSION}
    body = json.dumps(data, ensure_ascii=False).encode() if data else b""
    if body:
        header["data_length"] = len(body)
    if payload:
        header["payload_length"] = len(payload)
    f.write(json.dumps(header, ensure_ascii=False).encode() + b"\n" + body + payload)


def read_event(f):
    """Returns (type, data, payload), or None when the server closed the connection."""
    line = f.readline()
    if not line:
        return None
    header = json.loads(line)
    data = dict(header.get("data") or {})
    if header.get("data_length"):
        data.update(json.loads(f.read(header["data_length"])))
    payload = f.read(header["payload_length"]) if header.get("payload_length") else b""
    return header.get("type"), data, payload


def transcribe(host: str, port: int, pcm_chunks, language: str = "", timeout: float = 600) -> tuple[str, str]:
    """Sends 16 kHz mono 16-bit PCM (an iterable of byte chunks) and returns (text, language)."""
    with socket.create_connection((host, port), timeout=timeout) as sock, sock.makefile("rwb") as f:
        write_event(f, "transcribe", {"language": language} if language else None)
        fmt = {"rate": RATE, "width": WIDTH, "channels": CHANNELS}
        write_event(f, "audio-start", fmt)
        for chunk in pcm_chunks:
            if chunk:
                write_event(f, "audio-chunk", fmt, chunk)
        write_event(f, "audio-stop")
        f.flush()
        while True:  # newer servers also send transcript-start/-chunk/-stop; the final transcript is what counts
            ev = read_event(f)
            if ev is None:
                raise OSError("the speech-to-text server closed the connection without a transcript")
            etype, data, _ = ev
            if etype == "transcript":
                return (data.get("text") or "").strip(), data.get("language") or language
            if etype == "error":
                raise OSError(f"speech-to-text error: {data.get('text') or data}")


def chunks(stream, size: int = CHUNK):
    """Reads a binary stream (e.g. ffmpeg's stdout) in one-second chunks."""
    while True:
        b = stream.read(size)
        if not b:
            return
        yield b
