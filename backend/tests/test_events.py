from uuid import uuid4

from app.models.category import Category
from app.models.product import Product
from app.repositories import stock_repo


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _register_user(client) -> str:
    email = f"user_{uuid4().hex}@test.local"
    password = "Password123!"
    response = client.post(
        "/auth/register",
        json={"email": email, "password": password},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_event_creates_stock(client, db):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    product = Product(
        sku=f"SKU-{uuid4().hex[:8]}",
        name="Sensor de CO2",
        barcode=f"{uuid4().hex[:12]}",
        category_id=category.id,
        active=True,
    )
    db.add(product)
    db.commit()
    db.refresh(product)

    token = _register_user(client)
    headers = _auth_header(token)

    response = client.post(
        "/events/",
        json={
            "event_type": "SENSOR_IN",
            "product_id": product.id,
            "delta": 3,
            "source": "sensor_simulado",
            "location": "Laboratorio",
        },
        headers=headers,
    )
    assert response.status_code == 201
    assert response.json()["processed"] is True

    stock = stock_repo.get_by_product_and_location(db, product.id, "Laboratorio")
    assert stock is not None
    assert stock.quantity == 3
