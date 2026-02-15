import json
import logging
import threading
import time
import uuid
from collections import defaultdict
from typing import DefaultDict

from fastapi import Request
from jose import jwt
from starlette.middleware.base import BaseHTTPMiddleware


logger = logging.getLogger("app.observability")


def _escape_label(value: str) -> str:
    return value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


class MetricsRegistry:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.http_requests_total = 0
        self.http_request_errors_5xx_total = 0
        self.http_request_duration_ms_sum = 0.0
        self.http_request_duration_ms_count = 0

        self.requests_by_route_method_status: DefaultDict[tuple[str, str, int], int] = defaultdict(int)
        self.duration_sum_by_route_method: DefaultDict[tuple[str, str], float] = defaultdict(float)
        self.duration_count_by_route_method: DefaultDict[tuple[str, str], int] = defaultdict(int)

    def observe(
        self,
        method: str,
        path: str,
        status_code: int,
        duration_ms: float,
        include_global: bool = True,
    ) -> None:
        with self._lock:
            if include_global:
                self.http_requests_total += 1
                if status_code >= 500:
                    self.http_request_errors_5xx_total += 1
                self.http_request_duration_ms_sum += duration_ms
                self.http_request_duration_ms_count += 1

            self.requests_by_route_method_status[(path, method, status_code)] += 1
            self.duration_sum_by_route_method[(path, method)] += duration_ms
            self.duration_count_by_route_method[(path, method)] += 1

    def render_prometheus(self) -> str:
        lines: list[str] = []
        with self._lock:
            lines.append("# HELP http_requests_total Total number of HTTP requests processed.")
            lines.append("# TYPE http_requests_total counter")
            lines.append(f"http_requests_total {self.http_requests_total}")

            lines.append("# HELP http_request_errors_5xx_total Total number of HTTP 5xx responses.")
            lines.append("# TYPE http_request_errors_5xx_total counter")
            lines.append(f"http_request_errors_5xx_total {self.http_request_errors_5xx_total}")

            lines.append("# HELP http_request_duration_ms_sum Sum of request durations in milliseconds.")
            lines.append("# TYPE http_request_duration_ms_sum counter")
            lines.append(f"http_request_duration_ms_sum {self.http_request_duration_ms_sum:.3f}")

            lines.append("# HELP http_request_duration_ms_count Number of observed request durations.")
            lines.append("# TYPE http_request_duration_ms_count counter")
            lines.append(f"http_request_duration_ms_count {self.http_request_duration_ms_count}")

            lines.append("# HELP http_requests_by_route_method_status HTTP requests split by route, method and status code.")
            lines.append("# TYPE http_requests_by_route_method_status counter")
            for (path, method, status_code), count in sorted(self.requests_by_route_method_status.items()):
                lines.append(
                    f'http_requests_by_route_method_status{{path="{_escape_label(path)}",method="{method}",status="{status_code}"}} {count}'
                )

            lines.append("# HELP http_request_duration_ms_by_route_method_sum Sum of duration per route/method.")
            lines.append("# TYPE http_request_duration_ms_by_route_method_sum counter")
            for (path, method), total in sorted(self.duration_sum_by_route_method.items()):
                lines.append(
                    f'http_request_duration_ms_by_route_method_sum{{path="{_escape_label(path)}",method="{method}"}} {total:.3f}'
                )

            lines.append("# HELP http_request_duration_ms_by_route_method_count Count of duration samples per route/method.")
            lines.append("# TYPE http_request_duration_ms_by_route_method_count counter")
            for (path, method), count in sorted(self.duration_count_by_route_method.items()):
                lines.append(
                    f'http_request_duration_ms_by_route_method_count{{path="{_escape_label(path)}",method="{method}"}} {count}'
                )

        return "\n".join(lines) + "\n"


def _extract_subject_from_auth(request: Request) -> str | None:
    auth = request.headers.get("authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:].strip()
    if not token:
        return None
    try:
        claims = jwt.get_unverified_claims(token)
    except Exception:
        return None
    sub = claims.get("sub")
    return str(sub) if sub else None


class ObservabilityMiddleware(BaseHTTPMiddleware):
    def __init__(self, app, registry: MetricsRegistry, exclude_paths: set[str] | None = None) -> None:
        super().__init__(app)
        self.registry = registry
        self.exclude_paths = exclude_paths or set()

    async def dispatch(self, request: Request, call_next):
        request_id = request.headers.get("x-request-id") or str(uuid.uuid4())
        request.state.request_id = request_id
        request.state.request_start_monotonic = time.perf_counter()

        method = request.method
        path = request.url.path
        client_ip = request.client.host if request.client else None
        subject = _extract_subject_from_auth(request)

        try:
            response = await call_next(request)
            status_code = response.status_code
            response.headers["X-Request-ID"] = request_id
        except Exception:
            duration_ms = (time.perf_counter() - request.state.request_start_monotonic) * 1000.0
            self.registry.observe(
                method=method,
                path=path,
                status_code=500,
                duration_ms=duration_ms,
                include_global=(path not in self.exclude_paths),
            )
            logger.exception(
                json.dumps(
                    {
                        "event": "http_request",
                        "request_id": request_id,
                        "method": method,
                        "path": path,
                        "status_code": 500,
                        "duration_ms": round(duration_ms, 3),
                        "client_ip": client_ip,
                        "subject": subject,
                    },
                    ensure_ascii=False,
                )
            )
            raise

        duration_ms = (time.perf_counter() - request.state.request_start_monotonic) * 1000.0
        self.registry.observe(
            method=method,
            path=path,
            status_code=status_code,
            duration_ms=duration_ms,
            include_global=(path not in self.exclude_paths),
        )
        logger.info(
            json.dumps(
                {
                    "event": "http_request",
                    "request_id": request_id,
                    "method": method,
                    "path": path,
                    "status_code": status_code,
                    "duration_ms": round(duration_ms, 3),
                    "client_ip": client_ip,
                    "subject": subject,
                },
                ensure_ascii=False,
            )
        )
        return response
