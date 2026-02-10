from datetime import datetime
from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.alert import Alert
from app.models.enums import AlertStatus, AlertType
from app.schemas.alert import AlertResponse
from app.ws.alerts_ws import publish_alert
from app.services import fcm_service
from app.services.notification_service import send_stock_alert_email
from app.models.stock import Stock
from app.models.location import Location
from app.models.product import Product


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
    alert_types: set[AlertType] | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Alert], int]:
    stmt = select(Alert)

    if product_id is not None or location is not None:
        stmt = stmt.join(Stock, Alert.stock_id == Stock.id)

    filters = []
    if status is not None:
        filters.append(Alert.alert_status == status)
    if alert_types is not None:
        filters.append(Alert.alert_type.in_(alert_types))
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


def get_active_by_type(db: Session, stock_id: int, alert_type: AlertType) -> Alert | None:
    return db.scalar(
        select(Alert).where(
            Alert.stock_id == stock_id,
            Alert.alert_type == alert_type,
            Alert.alert_status.in_([AlertStatus.PENDING, AlertStatus.ACK]),
        )
    )


def create_alert(
    db: Session,
    *,
    stock_id: int | None,
    quantity: int,
    min_quantity: int,
    alert_type: AlertType = AlertType.LOW_STOCK,
    status: AlertStatus = AlertStatus.PENDING,
) -> Alert:
    alert = Alert(
        stock_id=stock_id,
        quantity=quantity,
        min_quantity=min_quantity,
        alert_type=alert_type,
        alert_status=status,
    )
    db.add(alert)
    db.commit()
    db.refresh(alert)
    try:
        publish_alert(AlertResponse.model_validate(alert))
    except Exception:
        pass
    if alert.alert_type in (AlertType.LOW_STOCK, AlertType.OUT_OF_STOCK) and alert.stock_id is not None:
        try:
            row = db.execute(
                select(Stock, Location, Product)
                .join(Location, Stock.location_id == Location.id)
                .join(Product, Stock.product_id == Product.id)
                .where(Stock.id == alert.stock_id)
            ).first()
            if row:
                stock, location, product = row
                sent = send_stock_alert_email(
                    alert_type=alert.alert_type,
                    product_id=product.id,
                    product_name=product.name,
                    location=location.code,
                    quantity=alert.quantity,
                    min_quantity=alert.min_quantity,
                )
                if sent:
                    alert.notification_sent = True
                    alert.notification_sent_at = datetime.utcnow()
                    alert.notification_channel = "email"
                    db.add(alert)
                    db.commit()
        except Exception:
            pass
    try:
        fcm_service.send_alert_push(db, alert)
    except Exception:
        pass
    return alert


def ack_alert(db: Session, alert: Alert, user_id: int) -> Alert:
    alert.alert_status = AlertStatus.ACK
    alert.ack_user_id = user_id
    alert.ack_at = datetime.utcnow()
    db.add(alert)
    db.commit()
    db.refresh(alert)
    return alert
