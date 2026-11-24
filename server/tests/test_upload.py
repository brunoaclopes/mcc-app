"""Upload checks and the repository flow, against a local bare git repository (no network)."""
import copy
import json
import subprocess
import tempfile
import textwrap
import time
import unittest
from pathlib import Path

import upload
from common import Problem
from settings import Settings


def recipe(lang, key="web-test-soup", speed=1):
    return {"format": "mcc-recipe/1", "id": f"{key}-{lang}", "title": "Sopa" if lang == "pt" else "Soup",
            "language": lang, "tags": ["sopa" if lang == "pt" else "soups"],
            "ingredients": [{"items": [{"text": "500 g água" if lang == "pt" else "500 g water"}]}],
            "steps": [{"type": "weigh", "text": "Água." if lang == "pt" else "Water.", "grams": 500},
                      {"type": "machine", "text": "", "mode": "run",
                       "segments": [{"seconds": 600, "speed": speed, "tempC": 100, "reverse": False}]}]}


def body(key="web-test-soup", **kw):
    return {"key": key, "recipes": [recipe("pt", key, **kw), recipe("en", key, **kw)]}


class Checks(unittest.TestCase):
    repo = Path("/nonexistent")

    def problem(self, b):
        with self.assertRaises(Problem) as cm:
            upload.check(b, self.repo)
        return cm.exception

    def test_valid(self):
        key, by_lang = upload.check(body(), self.repo)
        self.assertEqual("web-test-soup", key)
        self.assertEqual({"pt", "en"}, set(by_lang))

    def test_key_pattern(self):
        self.assertEqual(422, self.problem(body(key="Not A Key")).status)

    def test_one_language_each(self):
        b = body()
        b["recipes"][1]["language"] = "pt"
        self.assertIn("exactly one", str(self.problem(b)))

    def test_ids(self):
        b = body()
        b["recipes"][1]["id"] = "web-other-en"
        self.assertIn("web-test-soup-en", str(self.problem(b)))

    def test_machine_limits(self):
        self.assertIn("speed 5 while heating", str(self.problem(body(speed=5))))
        b = body()
        for r in b["recipes"]:
            r["steps"][1]["segments"][0].update(tempC=0, speed=4, reverse=True)
        self.assertIn("in reverse", str(self.problem(b)))

    def test_same_structure(self):
        b = body()
        b["recipes"][1]["steps"][0]["grams"] = 400
        self.assertIn("same steps", str(self.problem(b)))

    def test_same_photo(self):
        b = body()
        b["recipes"][0]["image"] = "https://example.org/a.jpg"
        self.assertIn("same photo", str(self.problem(b)))

    def test_tags_mapped_to_repository_tags(self):
        with tempfile.TemporaryDirectory() as d:
            Path(d, "index.json").write_text(json.dumps({"recipes": [
                {"language": "pt", "tags": ["Sopas"]}, {"language": "en", "tags": ["Soups"]}]}))
            by_lang = {"pt": {"tags": ["SOPAS", "sobremesa", "Natal"]}, "en": {"tags": ["soups", "vegan"]}}
            upload.map_tags(by_lang, Path(d))
        self.assertEqual(["Sopas", "Sobremesas", "Natal"], by_lang["pt"]["tags"])
        self.assertEqual(["Soups", "Vegetarian & Vegan"], by_lang["en"]["tags"])


# a stand-in for the template's tools/build_index.py: rejects titles containing REJECT, else writes index.json
BUILD_INDEX = textwrap.dedent('''
    import json, sys
    from pathlib import Path
    bad = [p.name for p in sorted(Path("recipes").glob("*.json")) if "REJECT" in json.loads(p.read_text())["title"]]
    for name in bad:
        print(f"INVALID {name}: rejected by the test", file=sys.stderr)
    ids = [p.stem for p in sorted(Path("recipes").glob("*.json"))]
    Path("index.json").write_text(json.dumps({"format": "mcc-index/1", "recipes": [{"id": i} for i in ids]}))
    sys.exit(1 if bad else 0)
''')


def sh(*args, cwd=None):
    return subprocess.run(args, cwd=cwd, check=True, capture_output=True, text=True).stdout


class RepositoryFlow(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        t = Path(self.tmp.name)
        self.remote = t / "remote.git"
        sh("git", "init", "-q", "--bare", "-b", "main", str(self.remote))
        seed = t / "seed"
        sh("git", "clone", "-q", str(self.remote), str(seed))
        (seed / "tools").mkdir()
        (seed / "tools" / "build_index.py").write_text(BUILD_INDEX)
        (seed / "index.json").write_text("{}")
        sh("git", "add", ".", cwd=seed)
        sh("git", "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "-m", "seed", cwd=seed)
        sh("git", "push", "-q", "origin", "HEAD:main", cwd=seed)
        self.flag = t / "deployed"
        self.s = Settings.from_env({"MCC_TOKEN": "x", "MCC_DATA_DIR": str(t / "data"),
                                    "MCC_REPO_URL": str(self.remote), "MCC_SSH_KEY": str(t / "none"),
                                    "MCC_DEPLOY_CMD": f"touch {self.flag}"})
        self.up = upload.Uploader(self.s)

    def tearDown(self):
        self.tmp.cleanup()

    def remote_files(self):
        return sh("git", "--git-dir", str(self.remote), "ls-tree", "-r", "--name-only", "main").split()

    def test_add_then_409(self):
        self.assertEqual({"key": "web-test-soup", "ids": ["web-test-soup-pt", "web-test-soup-en"]},
                         self.up.add(body()))
        files = self.remote_files()
        self.assertIn("recipes/web-test-soup-pt.json", files)
        self.assertIn("recipes/web-test-soup-en.json", files)
        log = sh("git", "--git-dir", str(self.remote), "log", "-1", "--format=%an|%s", "main").strip()
        self.assertEqual("mcc-upload|Add Soup (phone import)", log)
        for _ in range(50):  # the deploy command runs in the background
            if self.flag.exists():
                break
            time.sleep(0.05)
        self.assertTrue(self.flag.exists())
        with self.assertRaises(Problem) as cm:
            self.up.add(body())
        self.assertEqual(409, cm.exception.status)

    def test_rejected_by_build_index(self):
        b = body()
        for r in b["recipes"]:
            r["title"] = "REJECT me"
        with self.assertRaises(Problem) as cm:
            self.up.add(copy.deepcopy(b))
        self.assertEqual(422, cm.exception.status)
        self.assertIn("INVALID web-test-soup-en.json: rejected by the test", str(cm.exception))
        self.assertNotIn("recipes/web-test-soup-pt.json", self.remote_files())
        self.assertEqual("", sh("git", "status", "--porcelain", cwd=self.s.repo_dir))  # clone left clean


if __name__ == "__main__":
    unittest.main()
