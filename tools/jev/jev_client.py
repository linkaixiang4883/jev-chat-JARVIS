"""Jev System One 客户端 —— 后端可选（opencode zen / typesafe 官方）。标准库 only；密钥永不进日志/文件。

实测事实（2026-09-22）：
- zen      POST https://opencode.ai/zen/v1/systemone   model=jev-1.13-free（免费）  env=OPENCODE_API_KEY
- typesafe POST https://api.typesafe.ai/v1/systemone   model=jev-latest             env=TYPESAFE_API_KEY
- 必须自述 User-Agent：默认 Python-urllib 会被 Cloudflare 1010 拦（403）
"""

from __future__ import annotations

import json
import os
import socket
import time
import urllib.error
import urllib.request

PROVIDERS = {
    "zen": {
        "url": "https://opencode.ai/zen/v1/systemone",
        "env": "OPENCODE_API_KEY",
        "model": "jev-1.13-free",
    },
    "typesafe": {
        "url": "https://api.typesafe.ai/v1/systemone",
        "env": "TYPESAFE_API_KEY",
        "model": "jev-latest",
    },
}
DEFAULT_PROVIDER = "zen"
USER_AGENT = "jev-tools/1.0 (+https://github.com/linkaixiang4883/jev-chat-JARVIS)"
MAX_RETRIES = 3

_urlopen = urllib.request.urlopen  # 单测注入点（不要直接调 urllib.request.urlopen）


class JevError(Exception):
    def __init__(self, message: str, status: int | None = None) -> None:
        super().__init__(message)
        self.status = status


def provider_config(provider: str | None = None) -> dict:
    """返回 {name,url,env,model}；优先级 参数 > 环境变量 JEV_PROVIDER > 默认 zen。"""
    name = (provider or os.environ.get("JEV_PROVIDER") or DEFAULT_PROVIDER).strip().lower()
    if name not in PROVIDERS:
        raise JevError(f"unknown Jev provider {name!r}; use one of {sorted(PROVIDERS)}")
    return dict(PROVIDERS[name], name=name)


def redact_secrets(text: str) -> str:
    """把任一已配置的 key 从字符串里抹掉（两个后端都盖）。"""
    if not isinstance(text, str):
        text = str(text)
    for env in ("OPENCODE_API_KEY", "TYPESAFE_API_KEY"):
        key = os.environ.get(env) or ""
        if key:
            text = text.replace(key, "[REDACTED]")
    return text


def _api_key(env: str) -> str:
    key = (os.environ.get(env) or "").strip()
    if not key:
        raise JevError(f"{env} is not set. Export it in the environment; do not put the key in a file.")
    return key


def _error_body(exc: urllib.error.HTTPError) -> str:
    try:
        raw = exc.read().decode("utf-8", errors="replace")
    except Exception:
        raw = ""
    return redact_secrets(raw)[:800]


def _readable_error(status: int, body: str, env: str) -> str:
    if status == 401:
        return f"Jev HTTP 401: API key rejected. Check {env}."
    if status == 402:
        return f"Jev HTTP 402: Zen 余额不足（付费 Jev 需充值；免费档请用 jev-1.13-free）。{body}"
    if status == 403 and "1010" in body:
        return f"Jev HTTP 403: 被 Cloudflare 拦截 —— User-Agent 必须自述，别用默认 Python-urllib。{body}"
    if status == 403:
        return f"Jev HTTP 403: 该模型可能只允许在 OpenCode 客户端内使用。{body}"
    if status == 400 and "MissingSessionID" in body:
        return f"Jev HTTP 400: 缺少 x-opencode-session 头（OpenCode Go 端点需要）。{body}"
    if status == 422:
        return f"Jev HTTP 422: request body rejected. {body}"
    if status == 429:
        return f"Jev HTTP 429: rate limited after {MAX_RETRIES} retries. {body}"
    if status == 529:
        return f"Jev HTTP 529: provider overloaded after {MAX_RETRIES} retries. {body}"
    return f"Jev HTTP {status}: {body}"


def ask(state: dict, questions: dict, timeout: float = 20,
        provider: str | None = None, model: str | None = None) -> dict:
    """POST state+questions 到所选 Jev 后端，返回解析后的 JSON。429/529 指数退避重试 3 次。"""
    cfg = provider_config(provider)
    key = _api_key(cfg["env"])
    payload = json.dumps(
        {"model": (model or cfg["model"]), "state": state, "questions": questions},
        ensure_ascii=False,
    ).encode("utf-8")

    last_status: int | None = None
    last_body = ""
    for attempt in range(MAX_RETRIES + 1):
        req = urllib.request.Request(
            cfg["url"],
            data=payload,
            method="POST",
            headers={
                "Authorization": f"Bearer {key}",
                "Content-Type": "application/json; charset=utf-8",
                "Accept": "application/json",
                "User-Agent": USER_AGENT,   # 缺了这条会被 Cloudflare 1010 挡（实测）
            },
        )
        try:
            with _urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            last_status = exc.code
            last_body = _error_body(exc)
            if last_status in (429, 529) and attempt < MAX_RETRIES:
                time.sleep(2 ** attempt)
                continue
            raise JevError(_readable_error(last_status, last_body, cfg["env"]), last_status) from None
        except (TimeoutError, socket.timeout) as exc:
            if attempt < MAX_RETRIES:
                time.sleep(2 ** attempt)
                continue
            raise JevError(f"Jev request timed out after {timeout}s") from exc
        except urllib.error.URLError as exc:
            reason = redact_secrets(getattr(exc, "reason", exc))
            if attempt < MAX_RETRIES:
                time.sleep(2 ** attempt)
                continue
            raise JevError(f"Jev request failed: {reason}") from None

    raise JevError(f"Jev HTTP {last_status}: exhausted retries. {last_body}", last_status)
