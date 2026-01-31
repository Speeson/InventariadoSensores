from typing import Iterable, Tuple
from sqlalchemy import select, func
from sqlalchemy.orm import Session
from app.models.movement import Movement
from app.models.product import Product
from app.models.enums import MovementType

def list_top_consumed(
    db: Session,
    *,
    date_from=None,
    date_to=None,
    limit: int = 10,
    offset: int = 0,
) -> Tuple[Iterable[tuple], int]:
    filters = [Movement.movement_type == MovementType.OUT]
    if date_from:
        filters.append(Movement.created_at >= date_from)
    if date_to:
        filters.append(Movement.created_at <= date_to)

    base = (
        select(
            Movement.product_id,
            Product.sku,
            Product.name,
            func.sum(Movement.quantity).label("total_out"),
        )
        .join(Product, Product.id == Movement.product_id)
        .where(*filters)
        .group_by(Movement.product_id, Product.sku, Product.name)
        .order_by(func.sum(Movement.quantity).desc())
    )

    total = db.scalar(select(func.count()).select_from(base.subquery())) or 0
    rows = db.execute(base.offset(offset).limit(limit)).all()
    return rows, total
