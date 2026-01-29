from sqlalchemy import select

from app.celery_app import celery_app
from app.db.session import SessionLocal
from app.models.alert import Alert
from app.models.enums import AlertStatus
from app.models.location import Location
from app.models.stock import Stock
from app.models.stock_threshold import StockThreshold


@celery_app.task(name="app.tasks.scan_low_stock")
def scan_low_stock() -> dict:
    created = 0
    with SessionLocal() as db:
        thresholds = db.scalars(select(StockThreshold)).all()
        if not thresholds:
            return {"created": 0}

        threshold_map: dict[tuple[int, str | None], StockThreshold] = {}
        for threshold in thresholds:
            threshold_map[(threshold.product_id, threshold.location)] = threshold

        stocks = db.execute(select(Stock, Location).join(Location, Stock.location_id == Location.id)).all()
        for stock, location in stocks:
            threshold = threshold_map.get((stock.product_id, location.code)) or threshold_map.get(
                (stock.product_id, None)
            )
            if not threshold:
                continue

            if stock.quantity >= threshold.min_quantity:
                continue

            existing = db.scalar(
                select(Alert).where(
                    Alert.stock_id == stock.id,
                    Alert.alert_status.in_([AlertStatus.PENDING, AlertStatus.ACK]),
                )
            )
            if existing:
                continue

            alert = Alert(
                stock_id=stock.id,
                quantity=stock.quantity,
                min_quantity=threshold.min_quantity,
                alert_status=AlertStatus.PENDING,
            )
            db.add(alert)
            created += 1

        if created:
            db.commit()

    return {"created": created}
