from datetime import datetime
from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.enums import Source, MovementType
from app.models.movement import Movement


def get(db: Session, movement_id: int) -> Movement | None:
    return db.get(Movement, movement_id)


def list_movements(
    db: Session,
    *,
    product_id: int | None = None,
    movement_type: MovementType | None = None,
    movement_source: Source | None = None,
    user_id: int | None = None,
    location_id: int | None = None,
    date_from: datetime | None = None,
    date_to: datetime | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Movement], int]:
    filters = []
    if product_id is not None:
        filters.append(Movement.product_id == product_id)
    if movement_type is not None:
        filters.append(Movement.movement_type == movement_type)
    if movement_source is not None:
        filters.append(Movement.movement_source == movement_source)
    if user_id is not None:
        filters.append(Movement.user_id == user_id)
    if location_id is not None:
        filters.append(Movement.location_id == location_id)
    if date_from is not None:
        filters.append(Movement.created_at >= date_from)
    if date_to is not None:
        filters.append(Movement.created_at <= date_to)

    stmt = select(Movement).where(*filters).order_by(Movement.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_movement(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    user_id: int | None,
    movement_type: MovementType,
    movement_source: Source,
    location_id: int | None = None,
    transfer_id: str | None = None,
    commit: bool = True,
) -> Movement:
    movement = Movement(
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=movement_type,
        movement_source=movement_source,
        location_id=location_id,
        transfer_id=transfer_id,
    )
    db.add(movement)

    if commit:
        db.commit()
        db.refresh(movement)
    else:
        db.flush()

    return movement
