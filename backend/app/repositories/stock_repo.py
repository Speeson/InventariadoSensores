from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.stock import Stock


def get(db: Session, stock_id: int) -> Stock | None:
    return db.get(Stock, stock_id)


def get_by_product_and_location(db: Session, product_id: int, location: str) -> Stock | None:
    return db.scalar(
        select(Stock).where(
            Stock.product_id == product_id,
            Stock.location == location,
        )
    )


def list_stocks(
    db: Session,
    *,
    product_id: int | None = None,
    location: str | None = None,
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Stock], int]:
    filters = []
    if product_id is not None:
        filters.append(Stock.product_id == product_id)
    if location:
        filters.append(Stock.location.ilike(f"%{location}%"))

    stmt = select(Stock).where(*filters).order_by(Stock.updated_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_stock(
    db: Session,
    *,
    product_id: int,
    location: str,
    quantity: int,
) -> Stock:
    stock = Stock(
        product_id=product_id,
        location=location,
        quantity=quantity,
    )
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


def adjust_stock_quantity(db: Session, stock: Stock, delta: int) -> Stock:
    stock.quantity = stock.quantity + delta
    db.add(stock)
    db.commit()
    db.refresh(stock)
    return stock
