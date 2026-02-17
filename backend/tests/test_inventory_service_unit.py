from uuid import uuid4

import pytest

from app.core.security import hash_password
from app.models.category import Category
from app.models.enums import ActionType, Entity, MovementType, Source, UserRole
from app.models.movement import Movement
from app.models.user import User
from app.repositories import stock_repo
from app.services import inventory_service


def _create_user(db, role: UserRole = UserRole.MANAGER) -> User:
    user = User(
        username=f"user_{uuid4().hex[:8]}",
        email=f"user_{uuid4().hex}@test.local",
        password_hash=hash_password("Password123!"),
        role=role,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def _create_product(db):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    from app.models.product import Product

    product = Product(
        sku=f"SKU-{uuid4().hex[:8]}",
        name="Producto de prueba",
        barcode=f"{uuid4().hex[:12]}",
        category_id=category.id,
        active=True,
    )
    db.add(product)
    db.commit()
    db.refresh(product)
    return product


def test_increase_stock_creates_movement_and_audit_log(db):
    user = _create_user(db)
    product = _create_product(db)

    stock, movement = inventory_service.increase_stock(
        db,
        product_id=product.id,
        quantity=5,
        user_id=user.id,
        location="LAB-A",
        source=Source.SCAN,
    )

    assert stock.quantity == 5
    assert movement.product_id == product.id
    assert movement.delta == 5
    assert movement.quantity == 5
    assert movement.movement_type == MovementType.IN
    assert movement.movement_source == Source.SCAN

    from app.models.audit_log import AuditLog

    logs = db.query(AuditLog).filter(AuditLog.user_id == user.id).all()
    assert any(log.entity == Entity.MOVEMENT and log.action == ActionType.CREATE for log in logs)


def test_decrease_stock_with_insufficient_quantity_raises_error(db):
    user = _create_user(db)
    product = _create_product(db)

    stock_repo.create_stock(
        db,
        product_id=product.id,
        location="LAB-B",
        quantity=1,
    )

    with pytest.raises(inventory_service.InventoryError, match="Stock insuficiente"):
        inventory_service.decrease_stock(
            db,
            product_id=product.id,
            quantity=2,
            user_id=user.id,
            location="LAB-B",
            source=Source.MANUAL,
        )

    movements = db.query(Movement).filter(Movement.product_id == product.id).all()
    assert movements == []


def test_transfer_stock_rejects_same_origin_and_destination(db):
    user = _create_user(db)
    product = _create_product(db)

    with pytest.raises(inventory_service.InventoryError, match="origen y destino no pueden ser iguales"):
        inventory_service.transfer_stock(
            db,
            product_id=product.id,
            quantity=1,
            user_id=user.id,
            from_location="LAB-C",
            to_location="lab-c",
            source=Source.SCAN,
        )
