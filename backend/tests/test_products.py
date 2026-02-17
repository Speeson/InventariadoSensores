from uuid import uuid4

from app.core.security import hash_password
from app.models.category import Category
from app.models.enums import UserRole
from app.models.user import User
from sqlalchemy.exc import IntegrityError


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _barcode13() -> str:
    return str(uuid4().int % 10**13).zfill(13)


def _login_admin(client, db) -> str:
    email = f"admin_{uuid4().hex}@example.com"
    username = f"admin_{uuid4().hex[:8]}"
    password = "Password123!"
    db.add(
        User(
            email=email,
            username=username,
            password_hash=hash_password(password),
            role=UserRole.ADMIN,
        )
    )
    db.commit()

    response = client.post(
        "/auth/login",
        data={"username": email, "password": password},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def test_products_requires_auth(client):
    response = client.get("/products/")
    assert response.status_code == 401


def test_products_crud(client, db):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    token = _login_admin(client, db)
    headers = _auth_header(token)

    create_response = client.post(
        "/products/",
        json={
            "sku": f"SKU-{uuid4().hex[:8]}",
            "name": "Sensor de prueba",
            "barcode": _barcode13(),
            "category_id": category.id,
            "active": True,
        },
        headers=headers,
    )
    assert create_response.status_code == 201
    product_id = create_response.json()["id"]

    list_response = client.get("/products/", headers=headers)
    assert list_response.status_code == 200
    assert list_response.json()["total"] >= 1

    update_response = client.patch(
        f"/products/{product_id}",
        json={"name": "Sensor actualizado"},
        headers=headers,
    )
    assert update_response.status_code == 200
    assert update_response.json()["name"] == "Sensor actualizado"

    delete_response = client.delete(f"/products/{product_id}", headers=headers)
    assert delete_response.status_code == 204


def test_delete_product_with_history_returns_conflict(client, db, monkeypatch):
    category = Category(name=f"Categoria-{uuid4().hex}")
    db.add(category)
    db.commit()
    db.refresh(category)

    token = _login_admin(client, db)
    headers = _auth_header(token)

    create_response = client.post(
        "/products/",
        json={
            "sku": f"SKU-{uuid4().hex[:8]}",
            "name": "Sensor con historial",
            "barcode": _barcode13(),
            "category_id": category.id,
            "active": True,
        },
        headers=headers,
    )
    assert create_response.status_code == 201
    product_id = create_response.json()["id"]

    class _FakeOrig(Exception):
        sqlstate = "23503"

    def _raise_fk_error(_db, _product):
        raise IntegrityError("DELETE FROM products ...", {"id": product_id}, _FakeOrig("fk"))

    from app.api.routes import products as products_routes

    monkeypatch.setattr(products_routes.product_repo, "delete_product", _raise_fk_error)

    delete_response = client.delete(f"/products/{product_id}", headers=headers)
    assert delete_response.status_code == 409
    assert "No se puede eliminar el producto" in delete_response.json()["detail"]
