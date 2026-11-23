"""Server settings, all from environment variables (see .env.example and README.md)."""
from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass
from pathlib import Path


def _int(env, name, default):
    raw = (env.get(name) or "").strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        raise SystemExit(f"{name} must be a whole number, not {raw!r}") from None


@dataclass(frozen=True)
class Settings:
    token_sha256: str        # the apps' Bearer token, as its SHA-256 hex digest
    repo_dir: Path           # clone of the private recipe repository (made from mcc-app's recipe-repo/)
    repo_url: str            # cloned into repo_dir when it is not a clone yet
    git_branch: str
    git_name: str
    git_email: str
    ssh_key: str
    ssh_known_hosts: str
    deploy_cmd: str          # run after each push (e.g. rebuild the recipe server); empty = none
    port: int
    max_body: int
    data_dir: Path           # writable: transcript cache and temporary audio
    whisper_host: str        # Wyoming speech-to-text server (e.g. Home Assistant's Whisper add-on); empty = off
    whisper_port: int
    whisper_language: str    # empty = the server decides
    transcript_timeout: int  # seconds for one transcript request
    max_audio_minutes: int
    cache_hours: int

    @classmethod
    def from_env(cls, env=os.environ) -> "Settings":
        token = (env.get("MCC_TOKEN") or "").strip()
        digest = (env.get("MCC_TOKEN_SHA256") or "").strip().lower()
        if token:
            digest = hashlib.sha256(token.encode()).hexdigest()
        if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise SystemExit("set MCC_TOKEN (the apps' upload token) or MCC_TOKEN_SHA256 (its SHA-256 hex digest)")
        data = Path(env.get("MCC_DATA_DIR") or "/data")
        return cls(
            token_sha256=digest,
            repo_dir=Path(env.get("MCC_REPO_DIR") or data / "repo"),
            repo_url=(env.get("MCC_REPO_URL") or "").strip(),
            git_branch=(env.get("MCC_GIT_BRANCH") or "main").strip(),
            git_name=(env.get("MCC_GIT_NAME") or "mcc-upload").strip(),
            git_email=(env.get("MCC_GIT_EMAIL") or "mcc-upload@localhost").strip(),
            ssh_key=(env.get("MCC_SSH_KEY") or "/ssh/id_ed25519").strip(),
            ssh_known_hosts=(env.get("MCC_SSH_KNOWN_HOSTS") or "/ssh/known_hosts").strip(),
            deploy_cmd=(env.get("MCC_DEPLOY_CMD") or "").strip(),
            port=_int(env, "MCC_PORT", 8080),
            max_body=_int(env, "MCC_MAX_BODY", 1024 * 1024),
            data_dir=data,
            whisper_host=(env.get("MCC_WHISPER_HOST") or "").strip(),
            whisper_port=_int(env, "MCC_WHISPER_PORT", 10300),
            whisper_language=(env.get("MCC_WHISPER_LANGUAGE") or "").strip(),
            transcript_timeout=_int(env, "MCC_TRANSCRIPT_TIMEOUT", 600),
            max_audio_minutes=_int(env, "MCC_MAX_AUDIO_MINUTES", 30),
            cache_hours=_int(env, "MCC_CACHE_HOURS", 24),
        )
