from sqlalchemy import select
from datetime import datetime, timezone, timedelta
from prometheus_client import Histogram, Counter
from app.celery_app import celery_app
from app.db.session import SessionLocal
from app.models.enums import EventStatus, MovementType
from app.models.event import Event
from app.models.product import Product
from app.models.movement import Movement
from app.models.alert import Alert
from app.models.enums import AlertStatus, AlertType
from app.models.location import Location
from app.services.notification_service import send_low_stock_email
from app.models.stock import Stock
from app.models.stock_threshold import StockThreshold
from app.repositories import alert_repo

# Métricas para tasks
TASK_OPS = Counter("task_operations_total", "Task operations", ["task_name", "status"])
TASK_OP_TIME = Histogram("task_operation_duration_seconds", "Task operation durations", ["task_name"])
EVENTS_PROCESSED = Counter("task_events_processed_total", "Events processed by tasks", ["status"])
ALERTS_CREATED_TASKS = Counter("task_alerts_created_total", "Alerts created in tasks", ["task_name"])

# Numero maximo de intentos para reintentar una tarea en caso de error retryable
MAX_RETRIES = 3

@celery_app.task(name="app.tasks.scan_low_stock")
def scan_low_stock() -> dict:
    TASK_OPS.labels(task_name="scan_low_stock", status="started").inc()
    with TASK_OP_TIME.labels(task_name="scan_low_stock").time():
        try:
            created = 0
            to_notify: list[tuple[int, str | None, str, int, int]] = []
            with SessionLocal() as db:
                thresholds = db.scalars(select(StockThreshold)).all()
                if not thresholds:
                    TASK_OPS.labels(task_name="scan_low_stock", status="success").inc()
                    return {"created": 0}

                threshold_map: dict[tuple[int, str | None], StockThreshold] = {}
                for threshold in thresholds:
                    threshold_map[(threshold.product_id, threshold.location)] = threshold

                stocks = db.execute(
                    select(Stock, Location, Product)
                    .join(Location, Stock.location_id == Location.id)
                    .join(Product, Stock.product_id == Product.id)
                ).all()
                for stock, location, product in stocks:
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

                    alert_repo.create_alert(
                        db,
                        stock_id=stock.id,
                        quantity=stock.quantity,
                        min_quantity=threshold.min_quantity,
                        alert_type=AlertType.LOW_STOCK,
                        status=AlertStatus.PENDING,
                    )
                    created += 1
                    ALERTS_CREATED_TASKS.labels(task_name="scan_low_stock").inc()
                    to_notify.append(
                        (stock.product_id, product.name, location.code, stock.quantity, threshold.min_quantity)
                    )

            if to_notify:
                for product_id, product_name, location_code, quantity, min_quantity in to_notify:
                    send_low_stock_email(
                        product_id=product_id,
                        product_name=product_name,
                        location=location_code,
                        quantity=quantity,
                        min_quantity=min_quantity,
                    )

            TASK_OPS.labels(task_name="scan_low_stock", status="success").inc()
            return {"created": created}
        except Exception as exc:
            TASK_OPS.labels(task_name="scan_low_stock", status="error").inc()
            raise


# Permite justificar fácilmente en la memoria del proyecto
def is_retryable_error(exc: Exception) -> bool:
    msg = str(exc).lower()
    return any(
        keyword in msg
        for keyword in [
            "deadlock",
            "lock",
            "timeout",
            "could not serialize",
        ]
    )


@celery_app.task(name="app.tasks.process_event")
def process_event(event_id: int) -> dict:
    TASK_OPS.labels(task_name="process_event", status="started").inc()
    with TASK_OP_TIME.labels(task_name="process_event").time():
        try:
            with SessionLocal() as db:
                event = db.get(Event, event_id)

                # 1) Validaciones basicas del evento
                if not event:
                    EVENTS_PROCESSED.labels(status="not_found").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "event_not_found", "event_id": event_id}

                if event.event_status != EventStatus.PENDING:
                    # idempotencia: si ya esta PROCESSED/ERROR no lo reproceses
                    EVENTS_PROCESSED.labels(status="already_processed").inc()
                    TASK_OPS.labels(task_name="process_event", status="success").inc()
                    return {"ok": True, "reason": "already_processed", "status": event.event_status.value}

                if event.delta <= 0:
                    event.event_status = EventStatus.ERROR
                    event.last_error = "Delta invalido (debe ser > 0)"
                    event.retry_count += 1
                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()
                    EVENTS_PROCESSED.labels(status="invalid_delta").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "invalid_delta"}

                # Validar producto
                product = db.get(Product, event.product_id)
                if not product:
                    event.event_status = EventStatus.ERROR
                    event.last_error = "Producto no encontrado"
                    event.retry_count += 1
                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()
                    EVENTS_PROCESSED.labels(status="product_not_found").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "product_not_found"}

                # Validar ubicacion
                if not event.location_id:
                    event.event_status = EventStatus.ERROR
                    event.last_error = "Ubicacion no informada"
                    event.retry_count += 1
                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()
                    EVENTS_PROCESSED.labels(status="location_missing").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "location_missing"}

                location = db.get(Location, event.location_id)
                if not location:
                    event.event_status = EventStatus.ERROR
                    event.last_error = "Ubicacion no encontrada"
                    event.retry_count += 1
                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()
                    EVENTS_PROCESSED.labels(status="location_not_found").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "location_not_found"}

                # 2) Resolver tipo de movimiento y delta a aplicar
                if event.event_type.value == "SENSOR_IN":
                    movement_type = MovementType.IN
                    stock_delta = event.delta
                elif event.event_type.value == "SENSOR_OUT":
                    movement_type = MovementType.OUT
                    stock_delta = -event.delta
                else:
                    event.event_status = EventStatus.ERROR
                    event.last_error = f"Tipo de evento no soportado: {event.event_type}"
                    event.retry_count += 1
                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()
                    EVENTS_PROCESSED.labels(status="invalid_event_type").inc()
                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {"ok": False, "reason": "invalid_event_type"}

                # 3) Transaccion: ajustar stock + crear movement + marcar evento PROCESSED
                try:
                    # >>> CAMBIO ANADIDO: lock del evento para idempotencia real
                    event = db.scalar(
                        select(Event).where(Event.id == event_id).with_for_update()
                    )

                    if not event:
                        EVENTS_PROCESSED.labels(status="not_found").inc()
                        TASK_OPS.labels(task_name="process_event", status="error").inc()
                        return {"ok": False, "reason": "event_not_found", "event_id": event_id}

                    if event.event_status != EventStatus.PENDING:
                        EVENTS_PROCESSED.labels(status="already_processed").inc()
                        TASK_OPS.labels(task_name="process_event", status="success").inc()
                        return {"ok": True, "reason": "already_processed", "status": event.event_status.value}
                    # <<< FIN CAMBIO ANADIDO

                    # Lock del stock para evitar carreras
                    stock = db.scalar(
                        select(Stock)
                        .where(
                            Stock.product_id == event.product_id,
                            Stock.location_id == event.location_id,
                        )
                        .with_for_update()
                    )

                    if not stock:
                        # crear stock 0 dentro de la transaccion
                        stock = Stock(product_id=event.product_id, location_id=event.location_id, quantity=0)
                        db.add(stock)
                        db.flush()

                    new_qty = stock.quantity + stock_delta
                    if new_qty < 0:
                        raise ValueError("Stock insuficiente para aplicar SENSOR_OUT")

                    stock.quantity = new_qty
                    db.add(stock)

                    # movement (user_id puede ser None en eventos de sensor)
                    movement = Movement(
                        product_id=event.product_id,
                        quantity=event.delta,
                        delta=stock_delta,
                        user_id=None,
                        movement_type=movement_type,
                        movement_source=event.source,
                        location_id=event.location_id,
                    )
                    db.add(movement)

                    # marcar evento como procesado
                    event.event_status = EventStatus.PROCESSED
                    event.last_error = None
                    event.processed_at = datetime.now(timezone.utc)
                    db.add(event)

                    db.commit()
                    EVENTS_PROCESSED.labels(status="success").inc()
                    TASK_OPS.labels(task_name="process_event", status="success").inc()
                    return {"ok": True, "event_id": event.id, "status": event.event_status.value}

                except Exception as exc:
                    db.rollback()

                    event.retry_count += 1
                    event.last_error = str(exc)[:255]

                    if is_retryable_error(exc) and event.retry_count < MAX_RETRIES:
                        # Se reintentara
                        event.event_status = EventStatus.PENDING
                    else:
                        # Error definitivo (equivale a FAILED en Jira)
                        event.event_status = EventStatus.ERROR

                    event.processed_at = datetime.now(timezone.utc)
                    db.commit()

                    # Si sigue siendo PENDING, reencolamos
                    if event.event_status == EventStatus.PENDING:
                        process_event.delay(event.id)
                        EVENTS_PROCESSED.labels(status="retrying").inc()
                    else:
                        EVENTS_PROCESSED.labels(status="failed").inc()

                    TASK_OPS.labels(task_name="process_event", status="error").inc()
                    return {
                        "ok": False,
                        "event_id": event.id,
                        "status": event.event_status.value,
                        "retry_count": event.retry_count,
                        "error": event.last_error,
                    }
        except Exception as exc:
            TASK_OPS.labels(task_name="process_event", status="error").inc()
            raise


@celery_app.task(name="app.tasks.requeue_pending_events")
def requeue_pending_events() -> dict:
    TASK_OPS.labels(task_name="requeue_pending_events", status="started").inc()
    with TASK_OP_TIME.labels(task_name="requeue_pending_events").time():
        try:
            requeued = 0
            cutoff = datetime.now(timezone.utc) - timedelta(minutes=2)
            with SessionLocal() as db:
                events = db.scalars(
                    select(Event)
                    .where(
                        Event.event_status == EventStatus.PENDING,
                        Event.retry_count < MAX_RETRIES,
                        (Event.processed_at.is_(None)) | (Event.processed_at < cutoff),
                    )
                    .limit(100)
                ).all()

                for event in events:
                    process_event.delay(event.id)
                    requeued += 1

            TASK_OPS.labels(task_name="requeue_pending_events", status="success").inc()
            return {"requeued": requeued}
        except Exception as exc:
            TASK_OPS.labels(task_name="requeue_pending_events", status="error").inc()
            raise