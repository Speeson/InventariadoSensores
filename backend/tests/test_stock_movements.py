from uuid import uuid4

from app.core.security import hash_password
from app.models.category import Category
from app.models.enums import UserRole
from app.models.product import Product
from app.models.user import User


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _login_manager(client, db) -> str:
    email = f"manager_{uuid4().hex}@example.com"
    username = f"manager_{uuid4().hex[:8]}"
    password = "Password123!"
    db.add(
        User(
            email=email,
            username=username,
            password_hash=hash_password(password),
            role=UserRole.MANAGER,
        )
    )
    db.commit()

    response = client.post(
        "/auth/login",
        data={"username": email, "password": password},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_stock_and_movement_in(client, db):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    product = Product(
        sku=f"SKU-{uuid4().hex[:8]}",
        name="Gateway de prueba",
        barcode=f"{uuid4().hex[:12]}",
        category_id=category.id,
        active=True,
    )
    db.add(product)
    db.commit()
    db.refresh(product)

    token = _login_manager(client, db)
    headers = _auth_header(token)

    stock_response = client.post(
        "/stocks/",
        json={
            "product_id": product.id,
            "location": "Oficina Central",
            "quantity": 10,
        },
        headers=headers,
    )
    assert stock_response.status_code == 201
    assert stock_response.json()["quantity"] == 10

    movement_response = client.post(
        "/movements/in",
        json={
            "product_id": product.id,
            "quantity": 5,
            "location": "Oficina Central",
            "movement_source": "SCAN",
        },
        headers=headers,
    )
    assert movement_response.status_code == 201
    assert movement_response.json()["stock"]["quantity"] == 15
