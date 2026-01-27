from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.event import Event
from app.models.enums import EventType


def create_event(
    db: Session,
    *,
    event_type: EventType,
    product_id: int,
    delta: int,
    source: str,
    processed: bool = False,
) -> Event:
    event = Event(
        event_type=event_type,
        product_id=product_id,
        delta=delta,
        source=source,
        processed=processed,
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
        filters.append(Event.processed == processed)

    stmt = select(Event).where(*filters).order_by(Event.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total
