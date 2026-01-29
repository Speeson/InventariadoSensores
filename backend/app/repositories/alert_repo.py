from datetime import datetime
from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.alert import Alert
from app.models.enums import AlertStatus
from app.models.stock import Stock
from app.models.location import Location


def get(db: Session, alert_id: int) -> Alert | None:
    return db.get(Alert, alert_id)


def list_alerts(
    db: Session,
    *,
    status: AlertStatus | None = None,
    product_id: int | None = None,
    location: str | None = None,
    date_from: datetime | None = None,
    date_to: datetime | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Alert], int]:
    stmt = select(Alert)

    if product_id is not None or location is not None:
        stmt = stmt.join(Stock, Alert.stock_id == Stock.id)

    filters = []
    if status is not None:
        filters.append(Alert.alert_status == status)
    if product_id is not None:
        filters.append(Stock.product_id == product_id)
    if location is not None:
        stmt = stmt.join(Location, Stock.location_id == Location.id)
        filters.append(Location.code == location)
    if date_from is not None:
        filters.append(Alert.created_at >= date_from)
    if date_to is not None:
        filters.append(Alert.created_at <= date_to)

    stmt = stmt.where(*filters).order_by(Alert.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_alert(
    db: Session,
    *,
    stock_id: int,
    quantity: int,
    min_quantity: int,
    status: AlertStatus = AlertStatus.PENDING,
) -> Alert:
    alert = Alert(
        stock_id=stock_id,
        quantity=quantity,
        min_quantity=min_quantity,
        alert_status=status,
    )
    db.add(alert)
    db.commit()
    db.refresh(alert)
    return alert


def ack_alert(db: Session, alert: Alert, user_id: int) -> Alert:
    alert.alert_status = AlertStatus.ACK
    alert.ack_user_id = user_id
    alert.ack_at = datetime.utcnow()
    db.add(alert)
    db.commit()
    db.refresh(alert)
    return alert
