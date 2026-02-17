from uuid import uuid4

from app.models.alert import Alert
from app.models.category import Category
from app.models.enums import AlertStatus, AlertType
from app.models.product import Product
from app.repositories import stock_repo


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _register_user(client) -> str:
    email = f"user_{uuid4().hex}@example.com"
    username = f"user_{uuid4().hex[:8]}"
    password = "Password123!"
    response = client.post(
        "/auth/register",
        json={"email": email, "username": username, "password": password, "role": "USER"},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_alerts_for_user_role_only_include_low_and_out_of_stock(client, db):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    product = Product(
        sku=f"SKU-{uuid4().hex[:8]}",
        name="Producto alertas",
        barcode=f"{uuid4().hex[:12]}",
        category_id=category.id,
        active=True,
    )
    db.add(product)
    db.commit()
    db.refresh(product)

    stock = stock_repo.create_stock(
        db,
        product_id=product.id,
        location="ALM-USER",
        quantity=3,
    )

    db.add_all(
        [
            Alert(
                stock_id=stock.id,
                quantity=3,
                min_quantity=5,
                alert_type=AlertType.LOW_STOCK,
                alert_status=AlertStatus.PENDING,
            ),
            Alert(
                stock_id=stock.id,
                quantity=20,
                min_quantity=0,
                alert_type=AlertType.LARGE_MOVEMENT,
                alert_status=AlertStatus.PENDING,
            ),
        ]
    )
    db.commit()

    token = _register_user(client)
    headers = _auth_header(token)

    response = client.get("/alerts/", headers=headers)
    assert response.status_code == 200

    items = response.json()["items"]
    assert any(item["alert_type"] == AlertType.LOW_STOCK.value for item in items)
    assert all(item["alert_type"] in {AlertType.LOW_STOCK.value, AlertType.OUT_OF_STOCK.value} for item in items)
