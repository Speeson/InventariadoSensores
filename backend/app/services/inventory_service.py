from typing import Tuple

from sqlalchemy.orm import Session

from app.models.enums import Source, MovementType
from app.models.product import Product
from app.models.stock import Stock
from app.models.movement import Movement
from app.repositories import product_repo, stock_repo, movement_repo, location_repo


class InventoryError(Exception):
    """Errores de dominio para operaciones de inventario."""
    pass


def _get_product_or_fail(db: Session, product_id: int) -> Product:
    product = product_repo.get(db, product_id)
    if not product:
        raise InventoryError("Producto no encontrado")
    return product


def _resolve_location_id(db: Session, location: str) -> int:
    loc = location_repo.get_or_create(db, location)
    return loc.id


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
    location_id = _resolve_location_id(db, location)

    updated_stock = stock_repo.adjust_stock_quantity(db, stock, delta=quantity)
    movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=MovementType.IN,
        movement_source=source,
        location_id=location_id,
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
    location_id = _resolve_location_id(db, location)

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
        location_id=location_id,
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
    Ajuste directo de stock (positivo o negativo).
    """
    _get_product_or_fail(db, product_id)
    stock = _get_or_create_stock(db, product_id, location)
    location_id = _resolve_location_id(db, location)

    new_qty = stock.quantity + quantity
    if new_qty < 0:
        raise InventoryError("Stock resultante no puede ser negativo")

    updated_stock = stock_repo.adjust_stock_quantity(db, stock, delta=quantity)
    movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=abs(quantity),
        user_id=user_id,
        movement_type=MovementType.ADJUST,
        movement_source=source,
        location_id=location_id,
    )
    return updated_stock, movement


def transfer_stock(
    db: Session,
    *,
    product_id: int,
    quantity: int,
    user_id: int,
    from_location: str,
    to_location: str,
    source: Source,
) -> Tuple[Stock, Stock, Movement, Movement]:
    if from_location.strip().lower() == to_location.strip().lower():
        raise InventoryError("La ubicación origen y destino no pueden ser iguales")

    _get_product_or_fail(db, product_id)

    from_stock = stock_repo.get_by_product_and_location(db, product_id, from_location)
    if not from_stock:
        from_stock = stock_repo.create_stock(
            db, product_id=product_id, location=from_location, quantity=0, commit=False
        )

    to_stock = stock_repo.get_by_product_and_location(db, product_id, to_location)
    if not to_stock:
        to_stock = stock_repo.create_stock(
            db, product_id=product_id, location=to_location, quantity=0, commit=False
        )

    from_location_id = _resolve_location_id(db, from_location)
    to_location_id = _resolve_location_id(db, to_location)

    if from_stock.quantity < quantity:
        raise InventoryError("Stock insuficiente en la ubicación origen")

    stock_repo.adjust_stock_quantity(db, from_stock, delta=-quantity, commit=False)
    stock_repo.adjust_stock_quantity(db, to_stock, delta=quantity, commit=False)

    out_movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=MovementType.OUT,
        movement_source=source,
        location_id=from_location_id,
        commit=False,
    )
    in_movement = movement_repo.create_movement(
        db,
        product_id=product_id,
        quantity=quantity,
        user_id=user_id,
        movement_type=MovementType.IN,
        movement_source=source,
        location_id=to_location_id,
        commit=False,
    )

    db.commit()
    db.refresh(from_stock)
    db.refresh(to_stock)
    db.refresh(out_movement)
    db.refresh(in_movement)

    return from_stock, to_stock, out_movement, in_movement
