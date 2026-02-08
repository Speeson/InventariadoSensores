from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.stock import Stock
from app.models.location import Location
from app.repositories import location_repo


def get(db: Session, stock_id: int) -> Stock | None:
    return db.get(Stock, stock_id)


def _get_location_id(db: Session, location_code: str) -> int | None:
    location = location_repo.get_by_code(db, location_code)
    return location.id if location else None


def get_by_product_and_location(db: Session, product_id: int, location: str) -> Stock | None:
    location_id = _get_location_id(db, location)
    if location_id is None:
        return None
    return db.scalar(
        select(Stock).where(
            Stock.product_id == product_id,
            Stock.location_id == location_id,
        )
    )


def select_by_product_and_location_id(*, product_id: int, location_id: int):
    return select(Stock).where(
        Stock.product_id == product_id,
        Stock.location_id == location_id,
    )


def list_stocks(
    db: Session,
    *,
    product_id: int | None = None,
    location: str | None = None,
    order_dir: str | None = "asc",
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Stock], int]:
    stmt = select(Stock)
    filters = []
    if product_id is not None:
        filters.append(Stock.product_id == product_id)
    if location:
        stmt = stmt.join(Location, Stock.location_id == Location.id)
        filters.append(Location.code.ilike(f"%{location}%"))

    stmt = stmt.where(*filters)
    if order_dir == "desc":
        stmt = stmt.order_by(Stock.id.desc())
    else:
        stmt = stmt.order_by(Stock.id.asc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_stock(
    db: Session,
    *,
    product_id: int,
    location: str,
    quantity: int,
    commit: bool = True,
) -> Stock:
    location_obj = location_repo.get_or_create(db, location)
    stock = Stock(
        product_id=product_id,
        location_id=location_obj.id,
        quantity=quantity,
    )
    db.add(stock)

    if commit:
        db.commit()
        db.refresh(stock)
    else:
        db.flush()  # deja el id listo dentro de la transaccion

    return stock


def update_stock_location(db: Session, stock: Stock, location: str) -> Stock:
    location_obj = location_repo.get_or_create(db, location)
    stock.location_id = location_obj.id
    db.add(stock)
    db.commit()
    db.refresh(stock)
    return stock


def update_stock_quantity(db: Session, stock: Stock, quantity: int) -> Stock:
    stock.quantity = quantity
    db.add(stock)
    db.commit()
    db.refresh(stock)
    return stock


def adjust_stock_quantity(db: Session, stock: Stock, delta: int, commit: bool = True) -> Stock:
    stock.quantity = stock.quantity + delta
    db.add(stock)

    if commit:
        db.commit()
        db.refresh(stock)
    else:
        db.flush()

    return stock
