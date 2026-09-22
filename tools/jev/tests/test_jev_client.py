"""provider 路由 / 请求头 / 错误映射 的单测。不联网；用 _urlopen 注入点捕获请求。"""
from __future__ import annotations

import io
import json
import os
import sys
import unittest
import urllib.error
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import jev_client  # noqa: E402


class FakeResponse:
    def __init__(self, payload: dict) -> None:
        self._raw = json.dumps(payload).encode("utf-8")

    def read(self) -> bytes:
        return self._raw

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class Capture:
    """替换 _urlopen：记录 Request，回放给定响应。"""

    def __init__(self, status: int = 200, payload: dict | None = None, body: bytes = b"{}"):
        self.calls: list = []
        self.status = status
        self.payload = payload
        self.body = body

    def __call__(self, req, timeout=None):
        self.calls.append(req)
        if self.status >= 400:
            raise urllib.error.HTTPError(req.full_url, self.status, "err", {}, io.BytesIO(self.body))
        return FakeResponse(self.payload or {})


Q = {"q": {"type": "noul", "instructions": "Is this about a probe?"}}


class ProviderTests(unittest.TestCase):
    def setUp(self):
        os.environ["OPENCODE_API_KEY"] = "test-opencode-key"
        os.environ["TYPESAFE_API_KEY"] = "test-typesafe-key"

    def test_zen_config(self):
        cfg = jev_client.provider_config("zen")
        self.assertEqual(cfg["url"], "https://opencode.ai/zen/v1/systemone")
        self.assertEqual(cfg["env"], "OPENCODE_API_KEY")
        self.assertEqual(cfg["model"], "jev-1.13-free")

    def test_typesafe_config(self):
        cfg = jev_client.provider_config("typesafe")
        self.assertEqual(cfg["url"], "https://api.typesafe.ai/v1/systemone")
        self.assertEqual(cfg["env"], "TYPESAFE_API_KEY")
        self.assertEqual(cfg["model"], "jev-latest")

    def test_ask_sends_self_describing_ua_and_model(self):
        cap = Capture(payload={"answers": {"q": {"type": "noul", "noul": 0.9}}})
        jev_client._urlopen = cap
        jev_client.ask({"chat": {}}, Q)
        req = cap.calls[0]
        self.assertIn("opencode.ai", req.full_url)
        ua = req.get_header("User-agent") or ""
        self.assertTrue(ua)
        self.assertNotIn("urllib", ua.lower())
        self.assertEqual(json.loads(req.data.decode("utf-8"))["model"], "jev-1.13-free")

    def test_typesafe_uses_typesafe_key(self):
        cap = Capture(payload={"answers": {}})
        jev_client._urlopen = cap
        jev_client.ask({"chat": {}}, Q, provider="typesafe")
        req = cap.calls[0]
        self.assertEqual(req.get_header("Authorization"), "Bearer test-typesafe-key")
        self.assertIn("api.typesafe.ai", req.full_url)

    def test_error_402_is_readable(self):
        cap = Capture(status=402, body=b'{"error":{"message":"Insufficient account funds"}}')
        jev_client._urlopen = cap
        with self.assertRaises(jev_client.JevError) as ctx:
            jev_client.ask({"chat": {}}, Q)
        self.assertIn("余额", str(ctx.exception))

    def test_redacts_both_keys(self):
        self.assertEqual(jev_client.redact_secrets("k=test-opencode-key"), "k=[REDACTED]")
        self.assertEqual(jev_client.redact_secrets("k=test-typesafe-key"), "k=[REDACTED]")


if __name__ == "__main__":
    unittest.main()
