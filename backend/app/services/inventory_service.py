from typing import Tuple

from sqlalchemy.orm import Session

from app.models.enums import Source, MovementType
from app.models.product import Product
from app.models.stock import Stock
from app.models.movement import Movement
from app.repositories import product_repo, stock_repo, movement_repo


class InventoryError(Exception):
    """Errores de dominio para operaciones de inventario."""
    pass


def _get_product_or_fail(db: Session, product_id: int) -> Product:
    product = product_repo.get(db, product_id)
    if not product:
        raise InventoryError("Producto no encontrado")
    return product


def _get_or_create_stock(db: Session, product_id: int, location: str) -> Stock:
    stock = stock_repo.get_by_product_and_location(db, product_id, location)
    if stock:
        return stock
    return stock_repo.create_stock(db, product_id=product_id, location=location, quantity=0)


def increase_stock(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    user_id: int,
    location: str,
    source: Source,
) -> Tuple[Stock, Movement]:
    _get_product_or_fail(db, product_id)
    stock = _get_or_create_stock(db, product_id, location)

    updated_stock = stock_repo.adjust_stock_quantity(db, stock, delta=quantity)
    movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=MovementType.IN,
        movement_source=source,
    )
    return updated_stock, movement


def decrease_stock(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    user_id: int,
    location: str,
    source: Source,
) -> Tuple[Stock, Movement]:
    _get_product_or_fail(db, product_id)
    stock = _get_or_create_stock(db, product_id, location)

    if stock.quantity < quantity:
        raise InventoryError("Stock insuficiente para la salida")

    updated_stock = stock_repo.adjust_stock_quantity(db, stock, delta=-quantity)
    movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=MovementType.OUT,
        movement_source=source,
    )
    return updated_stock, movement


def adjust_stock(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    user_id: int,
    location: str,
    source: Source,
) -> Tuple[Stock, Movement]:
    """
    Ajuste directo de stock (positivo o negativo). Úsalo para correcciones manuales.
    """
    _get_product_or_fail(db, product_id)
    stock = _get_or_create_stock(db, product_id, location)

    new_qty = stock.quantity + quantity
    if new_qty < 0:
        raise InventoryError("Stock resultante no puede ser negativo")

    updated_stock = stock_repo.adjust_stock_quantity(db, stock, delta=quantity)
    movement_type = MovementType.ADJUST
    movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=abs(quantity),
        user_id=user_id,
        movement_type=movement_type,
        movement_source=source,
    )
    return updated_stock, movement
