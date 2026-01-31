from typing import Iterable, Tuple
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from app.models.stock_threshold import StockThreshold

def _norm(location: str | None) -> str | None:
    return location.strip() if location else None

def get(db: Session, threshold_id: int) -> StockThreshold | None:
    return db.get(StockThreshold, threshold_id)

def get_by_product_location(db: Session, product_id: int, location: str | None):
    norm_loc = _norm(location)
    return db.scalar(
        select(StockThreshold).where(
            StockThreshold.product_id == product_id,
            (StockThreshold.location == norm_loc) if norm_loc is not None else StockThreshold.location.is_(None),
        )
    )

def list_thresholds(
    db: Session,
    *,
    product_id: int | None = None,
    location: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[StockThreshold], int]:
    filters = []
    if product_id is not None:
        filters.append(StockThreshold.product_id == product_id)
    if location:
        filters.append(func.lower(StockThreshold.location).ilike(f"%{_norm(location).lower()}%"))
    stmt = select(StockThreshold).where(*filters).order_by(StockThreshold.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total

def create_threshold(db: Session, *, product_id: int, location: str | None, min_quantity: int) -> StockThreshold:
    threshold = StockThreshold(product_id=product_id, location=_norm(location), min_quantity=min_quantity)
    db.add(threshold)
    db.commit()
    db.refresh(threshold)
    return threshold

def update_threshold(
    db: Session,
    threshold: StockThreshold,
    *,
    location: str | None = None,
    min_quantity: int | None = None,
) -> StockThreshold:
    if location is not None:
        threshold.location = _norm(location)
    if min_quantity is not None:
        threshold.min_quantity = min_quantity
    db.add(threshold)
    db.commit()
    db.refresh(threshold)
    return threshold

def delete_threshold(db: Session, threshold: StockThreshold) -> None:
    db.delete(threshold)
    db.commit()
