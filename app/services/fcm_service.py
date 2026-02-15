import os
from typing import Iterable

import firebase_admin
from firebase_admin import credentials, messaging

from app.models.alert import Alert
from app.models.enums import AlertType, UserRole
from app.repositories import fcm_token_repo
from app.models.stock import Stock
from app.models.product import Product
from app.models.location import Location
from sqlalchemy.orm import Session
from sqlalchemy import select


_app = None


def _init_firebase() -> bool:
    global _app
    if _app is not None:
        return True
    creds_path = os.getenv("FCM_CREDENTIALS_JSON", "").strip()
    if not creds_path:
        return False
    if not os.path.exists(creds_path):
        return False
    try:
        cred = credentials.Certificate(creds_path)
        _app = firebase_admin.initialize_app(cred)
        return True
    except Exception:
        _app = None
        return False


def _send_to_tokens(tokens: Iterable[str], title: str, body: str, data: dict[str, str] | None = None) -> None:
    if not tokens:
        return
    if not _init_firebase():
        return
    msg = messaging.MulticastMessage(
        notification=messaging.Notification(title=title, body=body),
        tokens=list(tokens),
        data=data or {},
    )
    try:
        messaging.send_each_for_multicast(msg)
    except Exception:
        pass


def send_alert_push(db: Session, alert: Alert) -> None:
    alert_type = alert.alert_type
    if alert_type not in {
        AlertType.LOW_STOCK,
        AlertType.OUT_OF_STOCK,
        AlertType.LARGE_MOVEMENT,
        AlertType.IMPORT_ISSUES,
    }:
        return

    roles = {UserRole.ADMIN, UserRole.MANAGER}
    if alert_type in {AlertType.LOW_STOCK, AlertType.OUT_OF_STOCK}:
        roles.add(UserRole.USER)

    title = {
        AlertType.LOW_STOCK: "Stock bajo",
        AlertType.OUT_OF_STOCK: "Stock agotado",
        AlertType.LARGE_MOVEMENT: "Movimiento grande",
        AlertType.IMPORT_ISSUES: "Importación con errores",
    }[alert_type]

    body = _build_alert_body(db, alert)
    tokens = fcm_token_repo.list_tokens_for_roles(db, roles)
    _send_to_tokens(tokens, title, body, data={"alert_type": alert_type.value})


def _build_alert_body(db: Session, alert: Alert) -> str:
    if alert.alert_type == AlertType.IMPORT_ISSUES:
        return f"Se detectaron {alert.quantity} incidencias en la importación."
    if alert.stock_id is None:
        return "Se generó una alerta de stock."

    row = db.execute(
        select(Stock, Product, Location)
        .join(Product, Stock.product_id == Product.id)
        .join(Location, Stock.location_id == Location.id)
        .where(Stock.id == alert.stock_id)
    ).first()
    if not row:
        return f"Cantidad: {alert.quantity}"

    stock, product, location = row
    if alert.alert_type == AlertType.LOW_STOCK:
        return f"{product.name} ({product.id}) en {location.code}: {alert.quantity} (min {alert.min_quantity})"
    if alert.alert_type == AlertType.OUT_OF_STOCK:
        return f"{product.name} ({product.id}) en {location.code}: agotado"
    if alert.alert_type == AlertType.LARGE_MOVEMENT:
        return f"{product.name} ({product.id}) en {location.code}: {alert.quantity} uds"
    return f"{product.name} ({product.id})"


def send_import_completed_push(db: Session, *, total_rows: int, error_rows: int, review_rows: int) -> None:
    min_rows = int(os.getenv("IMPORT_COMPLETED_PUSH_MIN_ROWS", "50"))
    if total_rows < min_rows:
        return
    roles = {UserRole.ADMIN, UserRole.MANAGER}
    title = "Importación completada"
    body = f"Lote procesado. OK: {max(0, total_rows - error_rows - review_rows)} · Errores: {error_rows} · Reviews: {review_rows}"
    tokens = fcm_token_repo.list_tokens_for_roles(db, roles)
    _send_to_tokens(tokens, title, body, data={"alert_type": "IMPORT_COMPLETED"})
