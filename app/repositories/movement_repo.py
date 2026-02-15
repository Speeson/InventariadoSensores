from datetime import datetime
from typing import Iterable, Tuple
from prometheus_client import Histogram, Counter
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.enums import Source, MovementType
from app.models.movement import Movement

# Métricas para movement_repo
MOVEMENT_QUERIES = Counter("repo_movement_queries_total", "Total movement repository queries", ["operation"])
MOVEMENT_QUERY_TIME = Histogram("repo_movement_query_duration_seconds", "Movement query durations", ["operation"])

def get(db: Session, movement_id: int) -> Movement | None:
    MOVEMENT_QUERIES.labels(operation="get").inc()
    with MOVEMENT_QUERY_TIME.labels(operation="get").time():
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
    order_dir: str | None = "desc",
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Movement], int]:
    MOVEMENT_QUERIES.labels(operation="list_movements").inc()
    with MOVEMENT_QUERY_TIME.labels(operation="list_movements").time():
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

        if order_dir == "asc":
            stmt = select(Movement).where(*filters).order_by(Movement.created_at.asc())
        else:
            stmt = select(Movement).where(*filters).order_by(Movement.created_at.desc())
        total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
        items = db.scalars(stmt.offset(offset).limit(limit)).all()
        return items, total


def create_movement(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    delta: int,
    user_id: int | None,
    movement_type: MovementType,
    movement_source: Source,
    location_id: int | None = None,
    transfer_id: str | None = None,
    commit: bool = True,
) -> Movement:
    MOVEMENT_QUERIES.labels(operation="create_movement").inc()
    with MOVEMENT_QUERY_TIME.labels(operation="create_movement").time():
        movement = Movement(
            product_id=product_id,
            quantity=quantity,
            delta=delta,
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