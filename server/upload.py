"""POST /v1/recipes: adds a phone-imported recipe (pt + en) to the private recipe repository.

Contract: docs/RECIPE-UPLOAD.md. Checks here first (key, one pt and one en, ids, machine limits, same structure and
photo in both languages), then the repository's own tools/build_index.py decides, as the repository build does.
The result is committed and pushed, and MCC_DEPLOY_CMD (if set) runs in the background.
"""
from __future__ import annotations

import copy
import hashlib
import importlib
import io
import json
import os
import re
import subprocess
import sys
import threading
import urllib.request
from pathlib import Path

from common import Problem, PublicOnlyRedirects, log, public_host

LANGUAGES = ("pt", "en")
KEY_RE = re.compile(r"^web-[a-z0-9-]{1,60}$")
MAX_IMAGE = 10 * 1024 * 1024
W, H = 640, 400  # photo size used by the recipe repository (tools/fetch_images.py)

# obvious tag synonyms -> a tag the repository may have. A synonym is used only when the repository's index already
# has that tag (written with "&" or "and"/"e", in any case), so every repository keeps its own tag names; anything
# else is matched to the repository's tags case-insensitively, or kept as the AI wrote it.
TAG_SYNONYMS = {
    "en": {"vegetarian": "Vegetarian & Vegan", "vegan": "Vegetarian & Vegan", "dessert": "Desserts",
           "sauce": "Sauces & dips", "sauces": "Sauces & dips", "dip": "Sauces & dips", "dips": "Sauces & dips",
           "bread": "Baking", "cake": "Baking", "pastry": "Baking", "seafood": "Fish", "fish & seafood": "Fish",
           "salad": "Salads", "pasta & noodles": "Pasta", "noodles": "Pasta", "beef": "Meat", "pork": "Meat",
           "chicken": "Meat", "lamb": "Meat", "one pot": "One-Pot", "all in one": "All-in-One"},
    "pt": {"vegetariano": "Vegetariano e vegano", "vegano": "Vegetariano e vegano", "sobremesa": "Sobremesas",
           "doces": "Sobremesas", "molho": "Molhos", "pão": "Pastelaria e padaria", "bolos": "Pastelaria e padaria",
           "pastelaria": "Pastelaria e padaria", "marisco": "Peixe", "peixe e marisco": "Peixe", "salada": "Saladas",
           "massa": "Massas", "vaca": "Carne", "porco": "Carne", "frango": "Carne", "borrego": "Carne",
           "one pot": "One-Pot", "all in one": "All-in-One"},
}


# ---------------------------------------------------------------- checks (before the repository's own)

def machine_limits(r: dict) -> list[str]:
    """The machine's hard limits (FORMAT.md). The apps fit recipes to them already; this is the second line."""
    out = []
    for i, s in enumerate(r.get("steps") or [], 1):
        if not isinstance(s, dict) or s.get("type") != "machine":
            continue
        for j, g in enumerate(s.get("segments") or [], 1):
            if not isinstance(g, dict):
                continue
            speed, temp, sec = g.get("speed", 0) or 0, g.get("tempC", 0) or 0, g.get("seconds", 0) or 0
            if s.get("mode") == "run" and temp and speed > 3:
                out.append(f"step {i}.{j}: speed {speed} while heating (max 3)")
            if g.get("reverse") and speed > 3:
                out.append(f"step {i}.{j}: speed {speed} in reverse (max 3)")
            if speed > 6 and sec > 900:
                out.append(f"step {i}.{j}: more than 15 min above speed 6")
            if s.get("mode") == "turbo" and sec > 10:
                out.append(f"step {i}.{j}: turbo segments last at most 10 s")
    return out


def structure(r: dict):
    steps = [{k: v for k, v in s.items() if k != "text"} if isinstance(s, dict) else s for s in r.get("steps") or []]
    groups = [len(g.get("items") or []) if isinstance(g, dict) else 0 for g in r.get("ingredients") or []]
    return steps, groups


def repo_validator(repo: Path):
    """The recipe repository's own converters/mcc_format.validate, if the template still has it."""
    if not (repo / "converters" / "mcc_format.py").is_file():
        return None
    sys.path.insert(0, str(repo / "converters"))
    try:
        mod = importlib.reload(importlib.import_module("mcc_format"))
        return getattr(mod, "validate", None)
    except Exception as e:  # noqa: BLE001 - build_index.py still decides
        log(f"repository validator not usable: {e}")
        return None
    finally:
        sys.path.pop(0)


def check(body, repo: Path):
    """Returns (key, {"pt": recipe, "en": recipe}) or raises Problem(422)."""
    if not isinstance(body, dict):
        raise Problem(422, "the body must be a JSON object with key and recipes")
    key, recipes = body.get("key"), body.get("recipes")
    if not isinstance(key, str) or not KEY_RE.fullmatch(key):
        raise Problem(422, "key must look like web-<lowercase-words-with-dashes> (at most 60 characters)")
    if not isinstance(recipes, list) or len(recipes) != 2 or not all(isinstance(r, dict) for r in recipes):
        raise Problem(422, "recipes must hold exactly two recipes, one in Portuguese and one in English")
    by_lang = {r.get("language"): r for r in recipes}
    if set(by_lang) != set(LANGUAGES):
        raise Problem(422, "recipes must hold exactly one Portuguese (pt) and one English (en) recipe")
    validate = repo_validator(repo)
    for lang, r in by_lang.items():
        if r.get("id") != f"{key}-{lang}":
            raise Problem(422, f"the {lang} recipe's id must be {key}-{lang}")
        problems = (validate(r) if validate else []) + machine_limits(r)
        if problems:
            raise Problem(422, f"the {lang} recipe is not valid: {'; '.join(problems[:3])}")
    if structure(by_lang["pt"]) != structure(by_lang["en"]):
        raise Problem(422, "the Portuguese and English recipes must have the same steps, amounts and settings")
    if by_lang["pt"].get("image") != by_lang["en"].get("image"):
        raise Problem(422, "both languages must use the same photo")
    return key, by_lang


def _loose(tag: str) -> str:
    """'Vegetarian & Vegan', 'vegetarian and vegan' and 'Vegetariano e vegano' style spellings compare equal."""
    words = re.sub(r"[^\w&]+", " ", tag.casefold()).split()
    return " ".join("&" if w in ("&", "and", "e") else w for w in words)


def map_tags(by_lang: dict, repo: Path):
    try:
        index = json.loads((repo / "index.json").read_text(encoding="utf-8")).get("recipes") or []
    except (OSError, ValueError, AttributeError):
        index = []
    for lang, r in by_lang.items():
        known = {_loose(t): t for e in index if isinstance(e, dict) and e.get("language") == lang
                 for t in e.get("tags") or [] if isinstance(t, str) and t.strip()}
        out = []
        for t in r.get("tags") or []:
            if isinstance(t, str) and t.strip():
                synonym = TAG_SYNONYMS.get(lang, {}).get(t.strip().casefold())
                out.append(known.get(_loose(t)) or (synonym and known.get(_loose(synonym))) or t.strip())
        r["tags"] = list(dict.fromkeys(out))[:12]


# ---------------------------------------------------------------- photo

def store_photo(url: str, repo: Path) -> str:
    """Downloads a public http(s) photo into images/ as a 640x400 JPEG; returns its path in the repository."""
    from PIL import Image  # only needed when a recipe has a photo
    Image.MAX_IMAGE_PIXELS = 40_000_000
    if not public_host(url):
        raise OSError("not a public address")
    req = urllib.request.Request(url, headers={"User-Agent": "mcc-recipes/1"})
    with urllib.request.build_opener(PublicOnlyRedirects).open(req, timeout=20) as resp:
        if not resp.headers.get_content_type().startswith("image/"):
            raise OSError(f"not an image ({resp.headers.get_content_type()})")
        if int(resp.headers.get("Content-Length") or 0) > MAX_IMAGE:
            raise OSError("image too large")
        data = resp.read(MAX_IMAGE + 1)
    if len(data) > MAX_IMAGE:
        raise OSError("image too large")
    Image.open(io.BytesIO(data)).verify()
    im = Image.open(io.BytesIO(data)).convert("RGB")
    scale = max(W / im.width, H / im.height)
    im = im.resize((round(im.width * scale), round(im.height * scale)), Image.Resampling.LANCZOS)
    left, top = (im.width - W) // 2, (im.height - H) // 2
    rel = f"images/{hashlib.sha1(url.encode()).hexdigest()[:16]}.jpg"
    (repo / "images").mkdir(exist_ok=True)
    im.crop((left, top, left + W, top + H)).save(repo / rel, "JPEG", quality=80, optimize=True, progressive=False)
    return rel


def photo(by_lang: dict, repo: Path):
    """Stores the recipe's photo in the repository and points both languages at it; drops it when that fails."""
    image = by_lang["pt"].get("image")
    rel = None
    if isinstance(image, str) and image.startswith(("http://", "https://")):
        try:
            rel = store_photo(image, repo)
        except Exception as e:  # noqa: BLE001 - any failure just means no photo
            log(f"photo dropped: {type(e).__name__}: {str(e)[:120]}")
    elif isinstance(image, str) and re.fullmatch(r"images/[A-Za-z0-9._-]+", image) and (repo / image).is_file():
        rel = image
    for r in by_lang.values():
        r.pop("image", None)
        if rel:
            r["image"] = rel
            if rel != image:
                r["imageSource"] = image
    return rel


# ---------------------------------------------------------------- repository and deploy

class Uploader:
    def __init__(self, s):
        self.s = s
        self.lock = threading.Lock()  # one upload at a time; the others wait their turn
        self.deploy_lock = threading.Lock()
        self.deploy_pending = False
        self.deploying = False
        env = {**os.environ, "HOME": "/tmp", "GIT_TERMINAL_PROMPT": "0", "PYTHONDONTWRITEBYTECODE": "1",
               "GIT_AUTHOR_NAME": s.git_name, "GIT_COMMITTER_NAME": s.git_name,
               "GIT_AUTHOR_EMAIL": s.git_email, "GIT_COMMITTER_EMAIL": s.git_email}
        if Path(s.ssh_key).is_file():
            ssh = f"ssh -i {s.ssh_key} -o IdentitiesOnly=yes -o BatchMode=yes"
            if Path(s.ssh_known_hosts).is_file():
                ssh += f" -o StrictHostKeyChecking=yes -o UserKnownHostsFile={s.ssh_known_hosts}"
            env["GIT_SSH_COMMAND"] = ssh
        self.env = env
        sys.dont_write_bytecode = True  # importing the repository's converters must not leave files in it

    def git(self, *args, check=True):
        r = subprocess.run(["git", "-C", str(self.s.repo_dir), *args], env=self.env, capture_output=True,
                           text=True, timeout=120)
        if check and r.returncode:
            raise RuntimeError(f"git {args[0]} failed: {(r.stderr or r.stdout).strip()[-300:]}")
        return r

    def sync(self):
        """Makes the clone exactly the remote branch: nothing left over from an earlier failed upload."""
        repo = self.s.repo_dir
        if not (repo / ".git").exists():
            if not self.s.repo_url:
                raise RuntimeError(f"{repo} is not a git clone and MCC_REPO_URL is not set")
            r = subprocess.run(["git", "clone", "-q", "--branch", self.s.git_branch, self.s.repo_url, str(repo)],
                               env=self.env, capture_output=True, text=True, timeout=600)
            if r.returncode:
                raise RuntimeError(f"git clone failed: {r.stderr.strip()[-300:]}")
        self.git("fetch", "-q", "origin", self.s.git_branch)
        self.git("reset", "-q", "--hard", f"origin/{self.s.git_branch}")
        self.git("clean", "-qfd")

    def add(self, body, upload_key: str | None = None) -> dict:
        """Adds the recipe. With [upload_key] (the client's Idempotency-Key), a request that was already committed
        (its reply lost to a timeout, say) gets the same answer again instead of a second copy."""
        with self.lock:
            try:
                return self._add(body, upload_key)
            finally:
                try:
                    self.sync()
                except Exception as e:  # noqa: BLE001
                    log(f"cleanup sync failed: {e}")

    def earlier(self, upload_key: str) -> dict | None:
        """The answer to an upload already committed with this key (the "Upload-Key:" line of its commit)."""
        h = self.git("log", "-n", "1", "-F", f"--grep=Upload-Key: {upload_key}", "--format=%H",
                     f"origin/{self.s.git_branch}").stdout.strip()
        if not h:
            return None
        names = self.git("show", "--name-only", "--format=", h).stdout.split()
        m = next((re.fullmatch(r"recipes/(.+)-pt\.json", n) for n in names if re.fullmatch(r"recipes/.+-pt\.json", n)), None)
        if not m:
            return None
        key = m.group(1)
        return {"key": key, "ids": [f"{key}-{lang}" for lang in LANGUAGES]}

    def _add(self, body, upload_key: str | None = None) -> dict:
        repo = self.s.repo_dir
        self.sync()
        if upload_key:
            done = self.earlier(upload_key)
            if done:
                log(f"upload {upload_key[:12]} was already added as {done['key']}")
                return done
        key, original = check(body, repo)
        files = [f"recipes/{key}-{lang}.json" for lang in LANGUAGES]
        for attempt in range(2):
            by_lang = copy.deepcopy(original)  # a retry starts again from the uploaded recipes (photo URL included)
            if attempt:
                self.sync()
            if any((repo / f).exists() for f in files):
                raise Problem(409, "a recipe with this key already exists")
            map_tags(by_lang, repo)
            rel = photo(by_lang, repo)
            (repo / "recipes").mkdir(exist_ok=True)
            for lang in LANGUAGES:
                text = json.dumps(by_lang[lang], ensure_ascii=False, indent=1) + "\n"
                (repo / f"recipes/{key}-{lang}.json").write_text(text, encoding="utf-8")
            b = subprocess.run([sys.executable, "-I", "tools/build_index.py"], cwd=repo, env=self.env,
                               capture_output=True, text=True, timeout=300)
            if b.returncode:
                for f in files + ([rel] if rel else []):
                    (repo / f).unlink(missing_ok=True)
                first = next((ln.strip() for ln in (b.stderr or b.stdout).splitlines() if ln.strip()), "")
                raise Problem(422, f"the recipe repository rejected it: {first[:200] or 'build_index.py failed'}")
            self.git("add", *files, "index.json", *([rel] if rel else []))
            title = " ".join(str(by_lang["en"].get("title") or key).split())[:80]
            msg = f"Add {title} (phone import)" + (f"\n\nUpload-Key: {upload_key}" if upload_key else "")
            self.git("commit", "-q", "-m", msg)
            if self.git("push", "-q", "origin", f"HEAD:{self.s.git_branch}", check=False).returncode == 0:
                self.deploy()
                return {"key": key, "ids": [f"{key}-{lang}" for lang in LANGUAGES]}
            log(f"push rejected for {key} (attempt {attempt + 1}), retrying on the new {self.s.git_branch}")
        raise RuntimeError("could not push to the recipe repository")

    def deploy(self):
        """Runs MCC_DEPLOY_CMD in the background. While it runs, later uploads share one more run after it."""
        if not self.s.deploy_cmd:
            return
        with self.deploy_lock:
            if self.deploying:
                self.deploy_pending = True
                return
            self.deploying = True
        threading.Thread(target=self._deploy_loop, daemon=True).start()

    def _deploy_loop(self):
        while True:
            r = subprocess.run(["sh", "-c", self.s.deploy_cmd], cwd=self.s.repo_dir, capture_output=True, text=True)
            if r.returncode:
                log(f"deploy command failed ({r.returncode}): {(r.stderr or r.stdout).strip()[-300:]}")
            else:
                log("deploy command finished")
            with self.deploy_lock:
                if not self.deploy_pending:
                    self.deploying = False
                    return
                self.deploy_pending = False
