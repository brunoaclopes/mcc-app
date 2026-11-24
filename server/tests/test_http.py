"""The HTTP layer: auth and request bodies, before any upload or transcript work."""
import http.client
import json
import tempfile
import threading
import unittest
from http.server import ThreadingHTTPServer

import app
from settings import Settings


class Http(unittest.TestCase):
    def setUp(self):
        app.Handler.settings = Settings.from_env({"MCC_TOKEN": "t0k", "MCC_DATA_DIR": tempfile.mkdtemp()})
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), app.Handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def post(self, length: str, token="t0k"):
        c = http.client.HTTPConnection("127.0.0.1", self.server.server_address[1], timeout=5)
        c.putrequest("POST", "/v1/transcript")
        c.putheader("Authorization", f"Bearer {token}")
        c.putheader("Content-Length", length)
        c.endheaders()
        r = c.getresponse()
        out = r.status, json.loads(r.read())
        c.close()
        return out

    def test_negative_or_bad_length_is_refused_at_once(self):  # used to wait for a body that never comes
        self.assertEqual(400, self.post("-1")[0])
        self.assertEqual(400, self.post("abc")[0])

    def test_wrong_token(self):
        self.assertEqual((401, {"error": "missing or wrong token"}), self.post("0", token="nope"))


if __name__ == "__main__":
    unittest.main()
