from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.stock_threshold import StockThreshold
from app.repositories import location_repo


def get(db: Session, threshold_id: int) -> StockThreshold | None:
    return db.get(StockThreshold, threshold_id)


def _get_location_id(db: Session, location_code: str | None, *, create_if_missing: bool = True) -> int | None:
    if location_code is None:
        return None
    loc = location_repo.get_or_create(db, location_code) if create_if_missing else location_repo.get_by_code(db, location_code)
    return loc.id if loc else None


def get_by_product_and_location(db: Session, product_id: int, location: str | None) -> StockThreshold | None:
    loc_id = _get_location_id(db, location) if location is not None else None
    return db.scalar(
        select(StockThreshold).where(
            StockThreshold.product_id == product_id,
            (StockThreshold.location_id == loc_id) if loc_id is not None else StockThreshold.location_id.is_(None),
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
        loc_id = _get_location_id(db, location, create_if_missing=False)
        # Si la ubicación no existe, simplemente no habrá resultados; no se crea
        filters.append(StockThreshold.location_id == loc_id)

    stmt = select(StockThreshold).where(*filters).order_by(StockThreshold.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_threshold(
    db: Session,
    *,
    product_id: int,
    location: str | None,
    min_quantity: int,
) -> StockThreshold:
    loc_id = _get_location_id(db, location) if location is not None else None
    threshold = StockThreshold(
        product_id=product_id,
        location_id=loc_id,
        min_quantity=min_quantity,
    )
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
        threshold.location_id = _get_location_id(db, location)
    if min_quantity is not None:
        threshold.min_quantity = min_quantity
    db.add(threshold)
    db.commit()
    db.refresh(threshold)
    return threshold


def delete_threshold(db: Session, threshold: StockThreshold) -> None:
    db.delete(threshold)
    db.commit()
