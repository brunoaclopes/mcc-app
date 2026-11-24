"""python3 -m unittest discover -s tests   (run from the repository root)"""
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "converters"))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools"))
import build_index  # noqa: E402
import mc2_bundled  # noqa: E402
import mcc_format  # noqa: E402
import web_jsonld  # noqa: E402

PAGE = """<html><head><script type="application/ld+json">
{"@context":"https://schema.org","@graph":[{"@type":"WebPage","name":"x"},{"@type":"Recipe",
 "name":"Sopa de legumes","inLanguage":"pt-PT","recipeYield":"4 porções","totalTime":"PT35M","prepTime":"PT10M",
 "image":["https://example.org/sopa.jpg"],"keywords":"sopa, legumes","recipeCategory":"Sopas",
 "nutrition":{"@type":"NutritionInformation","calories":"180 kcal"},
 "recipeIngredient":["1 cebola","2 cenouras","500 ml de água"],
 "recipeInstructions":[
  {"@type":"HowToStep","text":"Coloque a cebola no copo e pique 5 seg./vel. 5."},
  {"@type":"HowToSection","name":"Cozedura","itemListElement":[
    {"@type":"HowToStep","text":"Junte a água e coza 25 min/100 °C/vel. 1."},
    {"@type":"HowToStep","text":"Triture 1 min/vel. 8 e sirva."}]},
  {"@type":"HowToStep","text":"Refogue 3 min/120°C/vel. colher inversa."},
  {"@type":"HowToStep","text":"Pressione turbo 2 seg."},
  {"@type":"HowToStep","text":"Coza a vapor 20 min/Varoma/vel. 1."}
 ]}]}
</script></head><body></body></html>"""


class WebJsonLd(unittest.TestCase):
    def setUp(self):
        self.r = web_jsonld.convert(PAGE, "https://example.org/sopa")

    def test_valid(self):
        self.assertEqual([], mcc_format.validate(self.r))

    def test_metadata(self):
        r = self.r
        self.assertEqual("Sopa de legumes", r["title"])
        self.assertEqual("pt", r["language"])
        self.assertEqual(35, r["totalMinutes"])
        self.assertEqual("https://example.org/sopa.jpg", r["image"])
        self.assertEqual({"kcal": 180}, r["nutritionPerServing"])
        self.assertEqual(3, len(r["ingredients"][0]["items"]))

    def test_machine_steps(self):
        m = [s for s in self.r["steps"] if s["type"] == "machine"]
        segs = [(s["mode"], s["segments"][0]) for s in m]
        self.assertEqual(("run", {"seconds": 5, "speed": 5, "tempC": 0, "reverse": False}), segs[0])
        self.assertEqual(("run", {"seconds": 1500, "speed": 1, "tempC": 100, "reverse": False}), segs[1])
        self.assertEqual(("run", {"seconds": 60, "speed": 8, "tempC": 0, "reverse": False}), segs[2])
        self.assertEqual(("run", {"seconds": 180, "speed": 1, "tempC": 120, "reverse": True}), segs[3])
        self.assertEqual(("turbo", {"seconds": 2}), segs[4])
        self.assertEqual(("steam", {"seconds": 1200}), segs[5])
        self.assertTrue(any("check" in n or "detected" in n for n in self.r["notes"]))

    def test_heat_speed_clamped(self):
        notes = []
        s = web_jsonld.detect_settings("Cook 10 min/90°C/speed 5.", notes, "t")
        self.assertEqual({"seconds": 600, "speed": 3, "tempC": 90, "reverse": False}, s[0]["segments"][0])

    def test_temperature_rounds_down_to_machine_level(self):
        s = web_jsonld.detect_settings("Derreta 2 min/52 °C/vel. 2.", [], "t")
        self.assertEqual(50, s[0]["segments"][0]["tempC"])


    def test_preset_buttons(self):
        t = "Add the oil and sauté using the roast button/3 minutes. Knead using the kneading function for 2 minutes."
        s = web_jsonld.detect_settings(t, [], "t")
        self.assertEqual([("roast", {"seconds": 180, "tempC": 130}), ("knead", {"seconds": 120})],
                         [(x["mode"], x["segments"][0]) for x in s])


class Mc2(unittest.TestCase):
    def test_ramp_merges_and_reverse_fix(self):
        mv = lambda **k: {"reverse": False, "speed": 0, "temp": 0, "time": 0, "weight": 0, **k}
        src = [
            {"mode": "ramp", "step": 1, "text": "", "machineValues": mv(speed=3, time=10)},
            {"mode": "ramp", "step": 2, "text": "", "machineValues": mv(speed=6, time=10)},
            {"mode": "cooking", "step": 3, "text": "", "machineValues": mv(speed=4, time=10, reverse=True)},
            {"mode": "scale", "step": 4, "text": "add", "machineValues": mv(weight=120)},
        ]
        notes = []
        steps = mc2_bundled.convert_steps(src, notes)
        self.assertEqual(2, len(steps[0]["segments"]))
        self.assertFalse(steps[1]["segments"][0]["reverse"])
        self.assertEqual({"type": "weigh", "text": "add", "grams": 120}, steps[2])
        self.assertEqual(1, len(notes))


class Repository(unittest.TestCase):
    def test_all_recipes_valid(self):
        root = Path(__file__).resolve().parent.parent
        files = list((root / "recipes").glob("*.json"))
        for p in files:
            r = json.loads(p.read_text(encoding="utf-8"))
            self.assertEqual([], mcc_format.validate(r), p.name)

    def test_every_recipe_in_every_language(self):
        root = Path(__file__).resolve().parent.parent
        entries = []
        for p in (root / "recipes").glob("*.json"):
            r = json.loads(p.read_text(encoding="utf-8"))
            self.assertTrue(r["id"].endswith("-" + r["language"]), p.name)
            entries.append({"key": r["id"][:-len(r["language"]) - 1], "language": r["language"]})
        self.assertEqual([], build_index.missing_translations(entries))


class Languages(unittest.TestCase):
    def test_missing_translation_reported(self):
        entries = [{"key": "a", "language": "pt"}, {"key": "a", "language": "en"}, {"key": "b", "language": "pt"}]
        self.assertEqual(["b: no en version"], build_index.missing_translations(entries))


if __name__ == "__main__":
    unittest.main()
