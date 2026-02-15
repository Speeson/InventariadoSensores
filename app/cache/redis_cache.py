import json
import os
from typing import Any

import redis
from fastapi.encoders import jsonable_encoder


_client: redis.Redis | None = None


def get_redis() -> redis.Redis | None:
    global _client
    if _client is not None:
        return _client
    try:
        redis_url = os.getenv("REDIS_URL") or f"redis://{os.getenv('REDIS_HOST', 'redis')}:{os.getenv('REDIS_PORT', '6379')}/0"
        _client = redis.Redis.from_url(redis_url)
        _client.ping()
        return _client
    except Exception:
        _client = None
        return None


def make_key(prefix: str, user_id: int | None, params: dict[str, Any]) -> str:
    parts = [prefix]
    if user_id is not None:
        parts.append(f"user={user_id}")
    for k in sorted(params.keys()):
        v = params[k]
        parts.append(f"{k}={v}")
    return "|".join(parts)


def cache_get(key: str) -> Any | None:
    client = get_redis()
    if client is None:
        return None
    raw = client.get(key)
    if not raw:
        return None
    try:
        return json.loads(raw)
    except Exception:
        return None


def cache_set(key: str, value: Any, ttl_seconds: int) -> None:
    client = get_redis()
    if client is None:
        return
    payload = json.dumps(jsonable_encoder(value))
    client.setex(key, ttl_seconds, payload)


def cache_invalidate_prefix(prefix: str) -> None:
    client = get_redis()
    if client is None:
        return
    pattern = f"{prefix}*"
    for key in client.scan_iter(match=pattern, count=200):
        client.delete(key)
