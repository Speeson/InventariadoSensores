from uuid import uuid4

from app.models.category import Category
from app.models.product import Product


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _register_manager(client) -> str:
    email = f"manager_{uuid4().hex}@test.local"
    password = "Password123!"
    response = client.post(
        "/auth/register",
        json={"email": email, "password": password, "role": "MANAGER"},
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

    token = _register_manager(client)
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
