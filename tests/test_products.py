from uuid import uuid4

from app.models.category import Category


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _register_admin(client) -> str:
    email = f"admin_{uuid4().hex}@test.local"
    password = "Password123!"
    response = client.post(
        "/auth/register",
        json={"email": email, "password": password, "role": "ADMIN"},
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

    token = _register_admin(client)
    headers = _auth_header(token)

    create_response = client.post(
        "/products/",
        json={
            "sku": f"SKU-{uuid4().hex[:8]}",
            "name": "Sensor de prueba",
            "barcode": f"{uuid4().hex[:12]}",
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
