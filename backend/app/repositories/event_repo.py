from typing import Iterable, Tuple
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from app.models.event import Event
from app.models.enums import EventType, EventStatus, Source
import uuid


def create_event(
    db: Session,
    *,
    event_type: EventType,
    product_id: int,
    delta: int,
    source: Source | str,
    location_id: int | None = None,
    processed: bool = False,
    idempotency_key: str | None = None,
) -> Event:
    try:
        normalized_source = source if isinstance(source, Source) else Source(source)
    except ValueError:
        normalized_source = Source.MANUAL
    status = EventStatus.PROCESSED if processed else EventStatus.PENDING
    event = Event(
        event_type=event_type,
        product_id=product_id,
        delta=delta,
        source=normalized_source,
        location_id=location_id,
        event_status=status,
        idempotency_key=idempotency_key or str(uuid.uuid4()),
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


def list_events(
    db: Session,
    *,
    event_type: EventType | None = None,
    product_id: int | None = None,
    processed: bool | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Event], int]:
    filters = []
    if event_type is not None:
        filters.append(Event.event_type == event_type)
    if product_id is not None:
        filters.append(Event.product_id == product_id)
    if processed is not None:
        status = EventStatus.PROCESSED if processed else EventStatus.PENDING
        filters.append(Event.event_status == status)

    stmt = select(Event).where(*filters).order_by(Event.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total

#Esto te permite "si ya existe, devuelvo el mismo".
def get_by_idempotency_key(db: Session, key: str) -> Event | None:
    return db.scalar(select(Event).where(Event.idempotency_key == key))

