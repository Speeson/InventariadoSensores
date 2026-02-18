from uuid import uuid4

from app.core.security import hash_password
from app.models.category import Category
from app.models.enums import UserRole
from app.models.import_batch import ImportBatch
from app.models.import_error import ImportError
from app.models.location import Location
from app.models.product import Product
from app.models.user import User
from app.repositories import product_repo, stock_repo


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _login_with_role(client, db, role: UserRole) -> str:
    email = f"{role.value.lower()}_{uuid4().hex}@example.com"
    username = f"{role.value.lower()}_{uuid4().hex[:8]}"
    password = "Password123!"
    db.add(
        User(
            email=email,
            username=username,
            password_hash=hash_password(password),
            role=role,
        )
    )
    db.commit()

    response = client.post(
        "/auth/login",
        data={"username": email, "password": password},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def _seed_category_and_location(db) -> tuple[Category, Location]:
    category = Category(name=f"Categoria-{uuid4().hex}")
    location = Location(code=f"LOC-{uuid4().hex[:8]}")
    db.add(category)
    db.add(location)
    db.commit()
    db.refresh(category)
    db.refresh(location)
    return category, location


def _post_csv(client, token: str, path: str, csv_text: str, **params):
    return client.post(
        path,
        params=params,
        files={"file": ("import.csv", csv_text.encode("utf-8"), "text/csv")},
        headers=_auth_header(token),
    )


def test_import_events_csv_success_creates_product_stock(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)
    category, location = _seed_category_and_location(db)

    sku = f"SKU-{uuid4().hex[:8]}"
    barcode = f"BC-{uuid4().hex[:10]}"
    csv_text = (
        "type,sku,barcode,name,category_id,location_id,quantity\n"
        f"IN,{sku},{barcode},Sensor Temp,{category.id},{location.id},5\n"
    )

    response = _post_csv(client, token, "/imports/events/csv", csv_text)
    assert response.status_code == 201
    body = response.json()
    assert body["total_rows"] == 1
    assert body["ok_rows"] == 1
    assert body["error_rows"] == 0
    assert body["review_rows"] == 0

    product = product_repo.get_by_sku(db, sku)
    assert product is not None
    stock = stock_repo.get_by_product_and_location(db, product.id, location.code)
    assert stock is not None
    assert stock.quantity == 5


def test_import_events_csv_dry_run_does_not_persist_changes(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)
    category, location = _seed_category_and_location(db)

    sku = f"SKU-{uuid4().hex[:8]}"
    barcode = f"BC-{uuid4().hex[:10]}"
    csv_text = (
        "type,sku,barcode,name,category_id,location_id,quantity\n"
        f"IN,{sku},{barcode},Sensor DRY,{category.id},{location.id},3\n"
    )

    response = _post_csv(client, token, "/imports/events/csv", csv_text, dry_run="true")
    assert response.status_code == 201
    body = response.json()
    assert body["dry_run"] is True
    assert body["ok_rows"] == 1
    assert body["error_rows"] == 0
    assert body["review_rows"] == 0

    product = product_repo.get_by_sku(db, sku)
    assert product is None

    batch = db.get(ImportBatch, body["batch_id"])
    assert batch is not None
    assert batch.dry_run is True


def test_import_events_csv_missing_columns_returns_400(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)

    csv_text = "type,sku,barcode,name,category_id,quantity\nIN,SKU-1,BC-1,Prod,1,2\n"
    response = _post_csv(client, token, "/imports/events/csv", csv_text)
    assert response.status_code == 400
    assert "Faltan columnas" in response.json()["detail"]


def test_import_events_csv_invalid_utf8_returns_400(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)

    response = client.post(
        "/imports/events/csv",
        files={"file": ("bad.csv", b"\xff\xfe\xfd", "text/csv")},
        headers=_auth_header(token),
    )
    assert response.status_code == 400
    assert response.json()["detail"] == "CSV debe estar en UTF-8"


def test_import_events_csv_review_approve_flow(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)
    category, location = _seed_category_and_location(db)

    existing = Product(
        sku=f"SKU-{uuid4().hex[:8]}",
        name="Sensor de temperatura",
        barcode=f"BC-{uuid4().hex[:10]}",
        category_id=category.id,
        active=True,
    )
    db.add(existing)
    db.commit()

    review_sku = f"SKU-{uuid4().hex[:8]}"
    review_barcode = f"BC-{uuid4().hex[:10]}"
    csv_text = (
        "type,sku,barcode,name,category_id,location_id,quantity\n"
        f"IN,{review_sku},{review_barcode},Sensor de temperatura plus,{category.id},{location.id},4\n"
    )

    response = _post_csv(
        client,
        token,
        "/imports/events/csv",
        csv_text,
        fuzzy_threshold="0.6",
    )
    assert response.status_code == 201
    body = response.json()
    assert body["review_rows"] == 1
    assert body["ok_rows"] == 0
    assert body["errors"] == []
    assert body["reviews"][0]["reason"] == "possible_duplicate"
    batch_id = body["batch_id"]

    reviews_response = client.get(
        "/imports/reviews",
        params={"batch_id": batch_id},
        headers=_auth_header(token),
    )
    assert reviews_response.status_code == 200
    reviews = reviews_response.json()["items"]
    assert len(reviews) == 1
    review_id = reviews[0]["id"]

    approve_response = client.post(
        f"/imports/reviews/{review_id}/approve",
        headers=_auth_header(token),
    )
    assert approve_response.status_code == 200
    assert approve_response.json()["ok"] is True

    final_reviews = client.get(
        "/imports/reviews",
        params={"batch_id": batch_id},
        headers=_auth_header(token),
    )
    assert final_reviews.status_code == 200
    assert final_reviews.json()["total"] == 0

    imported = product_repo.get_by_sku(db, review_sku)
    assert imported is not None
    stock = stock_repo.get_by_product_and_location(db, imported.id, location.code)
    assert stock is not None
    assert stock.quantity == 4

    db.expire_all()
    batch = db.get(ImportBatch, batch_id)
    assert batch is not None
    assert batch.review_rows == 0
    assert batch.ok_rows == 1
    assert batch.error_rows == 0


def test_import_events_csv_review_reject_flow(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)
    category, location = _seed_category_and_location(db)

    review_sku = f"SKU-{uuid4().hex[:8]}"
    review_barcode = f"BC-{uuid4().hex[:10]}"
    csv_text = (
        "type,sku,barcode,name,category_id,location_id,quantity\n"
        f"IN,{review_sku},{review_barcode},,{category.id},{location.id},2\n"
    )

    response = _post_csv(client, token, "/imports/events/csv", csv_text)
    assert response.status_code == 201
    body = response.json()
    assert body["review_rows"] == 1
    assert body["reviews"][0]["reason"] == "missing_product_name"
    batch_id = body["batch_id"]

    reviews_response = client.get(
        "/imports/reviews",
        params={"batch_id": batch_id},
        headers=_auth_header(token),
    )
    review_id = reviews_response.json()["items"][0]["id"]

    reject_response = client.post(
        f"/imports/reviews/{review_id}/reject",
        headers=_auth_header(token),
    )
    assert reject_response.status_code == 200
    assert reject_response.json()["ok"] is True

    db.expire_all()
    batch = db.get(ImportBatch, batch_id)
    assert batch is not None
    assert batch.review_rows == 0
    assert batch.ok_rows == 0
    assert batch.error_rows == 1

    rejected = (
        db.query(ImportError)
        .filter(ImportError.batch_id == batch_id)
        .filter(ImportError.error_code == "review_rejected")
        .all()
    )
    assert len(rejected) == 1


def test_import_transfers_csv_validation_error_is_reported(client, db):
    token = _login_with_role(client, db, UserRole.MANAGER)
    category, location = _seed_category_and_location(db)

    csv_text = (
        "sku,barcode,name,category_id,from_location_id,to_location_id,quantity\n"
        f"SKU-T1,BC-T1,Producto T1,{category.id},{location.id},{location.id},5\n"
    )

    response = _post_csv(client, token, "/imports/transfers/csv", csv_text)
    assert response.status_code == 201
    body = response.json()
    assert body["ok_rows"] == 0
    assert body["error_rows"] == 1
    assert body["review_rows"] == 0
    assert body["errors"][0]["error_code"] == "validation_error"
    assert "no pueden ser iguales" in body["errors"][0]["message"]


def test_import_events_csv_forbidden_for_user_role(client, db):
    token = _login_with_role(client, db, UserRole.USER)
    category, location = _seed_category_and_location(db)

    csv_text = (
        "type,sku,barcode,name,category_id,location_id,quantity\n"
        f"IN,SKU-U1,BC-U1,Prod User,{category.id},{location.id},1\n"
    )

    response = _post_csv(client, token, "/imports/events/csv", csv_text)
    assert response.status_code == 403
    assert response.json()["detail"] == "Insufficient permissions"
