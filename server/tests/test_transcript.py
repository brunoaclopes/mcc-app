"""Subtitle choice and parsing, and the subtitles -> speech-to-text -> description fallbacks (no network)."""
import json
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import transcript
from settings import Settings


class Choice(unittest.TestCase):
    def test_people_before_automatic_and_own_language_first(self):
        info = {"language": "en", "subtitles": {"pt-BR": [{}], "en": [{}]},
                "automatic_captions": {"en-orig": [{}], "pt": [{}]}}
        self.assertEqual(("en", False), transcript.choose_track(info))

    def test_portuguese_before_english_when_language_unknown(self):
        info = {"subtitles": {"en": [{}], "pt-BR": [{}], "pt": [{}]}}
        self.assertEqual(("pt", False), transcript.choose_track(info))

    def test_automatic_original_never_a_translation(self):
        info = {"language": "pt", "automatic_captions": {"en": [{}], "pt-orig": [{}], "pt": [{}], "fr": [{}]}}
        self.assertEqual(("pt-orig", True), transcript.choose_track(info))
        info = {"automatic_captions": {"pt": [{}], "en": [{}], "es-orig": [{}]}}
        self.assertEqual(("es-orig", True), transcript.choose_track(info))

    def test_nothing(self):
        self.assertIsNone(transcript.choose_track({"subtitles": {"live_chat": [{}]}, "automatic_captions": {}}))

    def test_video_key(self):
        for url in ("https://www.youtube.com/watch?v=abcdefghijk&t=3", "https://youtu.be/abcdefghijk",
                    "https://youtube.com/shorts/abcdefghijk?feature=share"):
            self.assertEqual("youtube-abcdefghijk", transcript.video_key(url))
        self.assertTrue(transcript.video_key("https://vimeo.com/1").startswith("url-"))


class Parsing(unittest.TestCase):
    def test_json3(self):
        doc = {"events": [{"tStartMs": 0}, {"segs": [{"utf8": "Hoje vamos "}, {"utf8": "fazer sopa"}]},
                          {"segs": [{"utf8": "\n"}]}, {"segs": [{"utf8": "[Música]"}]},
                          {"segs": [{"utf8": "Hoje vamos fazer sopa"}]}, {"segs": [{"utf8": "com 500 g de água"}]}]}
        self.assertEqual("Hoje vamos fazer sopa\ncom 500 g de água", transcript.parse_json3(json.dumps(doc)))

    def test_vtt(self):
        vtt = ("WEBVTT\nKind: captions\nLanguage: en\n\n00:00:00.000 --> 00:00:02.000 align:start\n"
               "Add the <00:00:01.000><c>onion</c>\n\n00:00:02.000 --> 00:00:04.000\nAdd the onion\n"
               "and chop &amp; stir\n\n3\n00:00:04.000 --> 00:00:05.000\n♪ ♪\n")
        self.assertEqual("Add the onion\nand chop & stir", transcript.parse_vtt(vtt))


class Fallbacks(unittest.TestCase):
    INFO = {"title": "Sopa", "uploader": "Chef", "description": "Receita na descrição", "language": "pt",
            "thumbnail": "https://example.org/t.jpg", "duration": 300}

    def make(self, whisper_host="stt", subs="", stt=("", ""), stt_error=None, info=None):
        d = tempfile.mkdtemp()
        s = Settings.from_env({"MCC_TOKEN": "x", "MCC_DATA_DIR": d, "MCC_WHISPER_HOST": whisper_host})
        t = transcript.Transcriber(s)
        t.calls = []
        t.info = lambda url, tmp, deadline: (t.calls.append("info"), dict(info or self.INFO))[1]
        t.subtitles = lambda track, tmp, deadline: (t.calls.append(("subs", track)), subs)[1]

        def whisper(i, tmp, deadline):
            t.calls.append("whisper")
            if stt_error:
                raise stt_error
            return stt
        t.whisper = whisper
        return t

    def run_(self, t, info_tracks=None):
        info = dict(self.INFO, **(info_tracks or {}))
        t.info = lambda url, tmp, deadline: (t.calls.append("info"), info)[1]
        return t.transcribe("https://example.org/v", Path(tempfile.mkdtemp()), time.monotonic() + 60)

    def test_subtitles_first(self):
        t = self.make(subs="o que é dito")
        result, keep = self.run_(t, {"subtitles": {"pt": [{}]}})
        self.assertEqual(("subtitles", "o que é dito", "pt"), (result["source"], result["transcript"],
                                                               result["language"]))
        self.assertTrue(keep)
        self.assertNotIn("whisper", t.calls)

    def test_speech_to_text_when_no_subtitles(self):
        t = self.make(stt=("texto falado", "pt"))
        result, keep = self.run_(t)
        self.assertEqual(("whisper", "texto falado"), (result["source"], result["transcript"]))
        self.assertEqual("Receita na descrição", result["description"])

    def test_description_only_without_speech_to_text(self):
        t = self.make(whisper_host="")
        result, keep = self.run_(t)
        self.assertEqual(("none", ""), (result["source"], result["transcript"]))
        self.assertEqual(["Sopa", "Chef", "https://example.org/t.jpg"],
                         [result["title"], result["author"], result["thumbnail"]])
        self.assertTrue(keep)

    def test_speech_to_text_down_is_not_cached(self):
        t = self.make(stt_error=ConnectionRefusedError("refused"))
        result, keep = self.run_(t)
        self.assertEqual("none", result["source"])
        self.assertFalse(keep)

    def test_too_long_for_speech_to_text(self):
        t = self.make()
        result, _ = self.run_(t, {"duration": 3 * 3600})
        self.assertEqual("none", result["source"])
        self.assertNotIn("whisper", t.calls)

    def test_cached_for_a_day(self):
        t = self.make(subs="texto")
        t.info = lambda url, tmp, deadline: (t.calls.append("info"), dict(self.INFO, subtitles={"pt": [{}]}))[1]
        with mock.patch.object(transcript, "public_host", lambda url: True):
            first = t.get("https://youtu.be/abcdefghijk")
            second = t.get("https://www.youtube.com/watch?v=abcdefghijk")
        self.assertEqual(first, second)
        self.assertEqual(1, t.calls.count("info"))
        with mock.patch.object(transcript, "public_host", lambda url: False):
            with self.assertRaises(transcript.Problem):
                t.get("http://192.0.2.1/video")


if __name__ == "__main__":
    unittest.main()
