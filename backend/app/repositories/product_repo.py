from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.product import Product


def get(db: Session, product_id: int) -> Product | None:
    return db.get(Product, product_id)


def get_by_sku(db: Session, sku: str) -> Product | None:
    return db.scalar(select(Product).where(Product.sku == sku))


def get_by_barcode(db: Session, barcode: str) -> Product | None:
    return db.scalar(select(Product).where(Product.barcode == barcode))


def list_products(
    db: Session,
    *,
    sku: str | None = None,
    name: str | None = None,
    barcode: str | None = None,
    category_id: int | None = None,
    active: bool | None = None,
    order_by: str | None = "id",
    order_dir: str | None = "asc",
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[Product], int]:
    filters = []
    if sku:
        filters.append(Product.sku.ilike(f"%{sku}%"))
    if name:
        filters.append(Product.name.ilike(f"%{name}%"))
    if barcode:
        filters.append(Product.barcode.ilike(f"%{barcode}%"))
    if category_id is not None:
        filters.append(Product.category_id == category_id)
    if active is not None:
        filters.append(Product.active == active)

    order_col = Product.created_at if order_by == "created_at" else Product.id
    if order_dir == "asc":
        stmt = select(Product).where(*filters).order_by(order_col.asc())
    else:
        stmt = select(Product).where(*filters).order_by(order_col.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total


def create_product(
    db: Session,
    *,
    sku: str,
    name: str,
    barcode: str | None,
    category_id: int,
    active: bool = True,
) -> Product:
    product = Product(
        sku=sku,
        name=name,
        barcode=barcode,
        category_id=category_id,
        active=active,
    )
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def update_product(
    db: Session,
    product: Product,
    *,
    name: str | None = None,
    barcode: str | None = None,
    category_id: int | None = None,
    active: bool | None = None,
) -> Product:
    if name is not None:
        product.name = name
    if barcode is not None:
        product.barcode = barcode
    if category_id is not None:
        product.category_id = category_id
    if active is not None:
        product.active = active

    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def delete_product(db: Session, product: Product) -> None:
    db.delete(product)
    db.commit()
