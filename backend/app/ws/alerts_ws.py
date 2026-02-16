import asyncio
import contextlib
import json
import os
from typing import Any

from fastapi import WebSocket
from fastapi.encoders import jsonable_encoder
from jose import jwt, JWTError
import redis.asyncio as aioredis
import anyio

from app.core.config import settings
from app.models.enums import AlertType
from app.schemas.alert import AlertResponse
from app.cache.redis_cache import get_redis


ALERTS_CHANNEL = "alerts:ws"


class AlertConnection:
    def __init__(self, websocket: WebSocket, role: str):
        self.websocket = websocket
        self.role = role.upper()


class AlertWSManager:
    def __init__(self) -> None:
        self._connections: list[AlertConnection] = []
        self._lock = asyncio.Lock()

    async def connect(self, websocket: WebSocket, role: str) -> None:
        await websocket.accept()
        async with self._lock:
            self._connections.append(AlertConnection(websocket, role))

    async def disconnect(self, websocket: WebSocket) -> None:
        async with self._lock:
            self._connections = [c for c in self._connections if c.websocket != websocket]

    async def broadcast(self, payload: dict[str, Any]) -> None:
        payload = jsonable_encoder(payload)
        alert_type = payload.get("alert_type")
        async with self._lock:
            connections = list(self._connections)
        for conn in connections:
            if conn.role == "USER":
                if alert_type not in {AlertType.LOW_STOCK.value, AlertType.OUT_OF_STOCK.value}:
                    continue
            try:
                await conn.websocket.send_json(payload)
            except Exception:
                await self.disconnect(conn.websocket)



manager = AlertWSManager()


def decode_token_role(token: str) -> str:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        role = payload.get("role") or "USER"
        return str(role)
    except JWTError:
        return "USER"


def publish_alert(alert: AlertResponse) -> None:
    payload_dict = jsonable_encoder(alert)
    client = get_redis()
    if client is not None:
        try:
            payload = json.dumps(payload_dict)
            client.publish(ALERTS_CHANNEL, payload)
        except Exception:
            pass
    try:
        anyio.from_thread.run(manager.broadcast, payload_dict)
    except Exception:
        pass


async def start_redis_listener() -> None:
    redis_url = os.getenv("REDIS_URL") or f"redis://{os.getenv('REDIS_HOST', 'redis')}:{os.getenv('REDIS_PORT', '6379')}/0"
    client = aioredis.from_url(redis_url)
    pubsub = client.pubsub()
    await pubsub.subscribe(ALERTS_CHANNEL)
    try:
        async for message in pubsub.listen():
            if message is None or message.get("type") != "message":
                continue
            data = message.get("data")
            if not data:
                continue
            try:
                if isinstance(data, bytes):
                    data = data.decode("utf-8")
                payload = json.loads(data)
            except Exception:
                continue
            await manager.broadcast(payload)
    except asyncio.CancelledError:
        # Graceful shutdown on app stop.
        raise
    finally:
        with contextlib.suppress(Exception):
            await pubsub.unsubscribe(ALERTS_CHANNEL)
        with contextlib.suppress(Exception):
            await pubsub.close()
        with contextlib.suppress(Exception):
            await client.close()
