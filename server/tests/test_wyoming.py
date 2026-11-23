"""The Wyoming client against a fake speech-to-text server on a local socket."""
import io
import socket
import threading
import unittest

import wyoming


def fake_server(reply_events):
    """Accepts one connection, records the client's events, then sends reply_events."""
    srv = socket.socket()
    srv.bind(("127.0.0.1", 0))
    srv.listen(1)
    seen = {"events": [], "audio": 0, "language": None}

    def serve():
        conn, _ = srv.accept()
        with conn, conn.makefile("rwb") as f:
            while True:
                ev = wyoming.read_event(f)
                if ev is None:
                    return
                etype, data, payload = ev
                seen["events"].append(etype)
                if etype == "transcribe":
                    seen["language"] = data.get("language")
                seen["audio"] += len(payload)
                if etype == "audio-stop":
                    for etype2, data2 in reply_events:
                        wyoming.write_event(f, etype2, data2)
                    f.flush()
                    return
    threading.Thread(target=serve, daemon=True).start()
    return srv.getsockname()[1], seen, srv


class Client(unittest.TestCase):
    def test_round_trip(self):
        port, seen, srv = fake_server([("transcript-start", None), ("transcript-chunk", {"text": "olá"}),
                                       ("transcript", {"text": " olá mundo ", "language": "pt"})])
        with srv:
            pcm = [b"\x00\x01" * 16000, b"\x00\x01" * 8000]
            text, lang = wyoming.transcribe("127.0.0.1", port, pcm, "pt", timeout=5)
        self.assertEqual(("olá mundo", "pt"), (text, lang))
        self.assertEqual(["transcribe", "audio-start", "audio-chunk", "audio-chunk", "audio-stop"], seen["events"])
        self.assertEqual((48000, "pt"), (seen["audio"], seen["language"]))

    def test_closed_without_transcript(self):
        port, _, srv = fake_server([])
        with srv, self.assertRaises(OSError):
            wyoming.transcribe("127.0.0.1", port, [b"\x00\x00"], timeout=5)

    def test_chunks(self):
        self.assertEqual([b"ab", b"cd", b"e"], list(wyoming.chunks(io.BytesIO(b"abcde"), 2)))


if __name__ == "__main__":
    unittest.main()
