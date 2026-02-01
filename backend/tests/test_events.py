from uuid import uuid4

from app.models.category import Category
from app.models.product import Product
from app.models.movement import Movement
from app.models.event import Event
from app.models.enums import EventType
from app.repositories import stock_repo, event_repo, location_repo
from app.tasks import process_event


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


def test_event_creates_pending_and_enqueues(client, db, monkeypatch):
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

    enqueued = {}

    def _fake_delay(event_id: int):
        enqueued["event_id"] = event_id

    from app.api.routes import events as events_routes
    monkeypatch.setattr(events_routes.process_event, "delay", _fake_delay)

    response = client.post(
        "/events/",
        json={
            "event_type": "SENSOR_IN",
            "product_id": product.id,
            "delta": 3,
            "source": "sensor_simulado",
            "location": "Laboratorio",
            "idempotency_key": uuid4().hex,
        },
        headers=headers,
    )
    assert response.status_code == 201
    body = response.json()
    assert body["processed"] is False
    assert body["event_status"] == "PENDING"
    assert enqueued["event_id"] == body["id"]

    stock = stock_repo.get_by_product_and_location(db, product.id, "Laboratorio")
    assert stock is None


def test_event_idempotency_returns_existing(client, db, monkeypatch):
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

    def _fake_delay(event_id: int):
        return None

    from app.api.routes import events as events_routes
    monkeypatch.setattr(events_routes.process_event, "delay", _fake_delay)

    key = uuid4().hex
    payload = {
        "event_type": "SENSOR_IN",
        "product_id": product.id,
        "delta": 2,
        "source": "sensor_simulado",
        "location": "Laboratorio",
        "idempotency_key": key,
    }

    first = client.post("/events/", json=payload, headers=headers)
    assert first.status_code == 201
    second = client.post("/events/", json=payload, headers=headers)
    assert second.status_code == 200
    assert second.json()["id"] == first.json()["id"]


def test_process_event_creates_stock_and_movement(db):
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

    location = location_repo.get_or_create(db, "Laboratorio")

    event = event_repo.create_event(
        db,
        event_type=EventType.SENSOR_IN,
        product_id=product.id,
        delta=3,
        source="sensor_simulado",
        location_id=location.id,
        processed=False,
        idempotency_key=uuid4().hex,
    )

    result = process_event(event.id)
    assert result["ok"] is True

    event_db = db.get(Event, event.id)
    assert event_db is not None
    assert event_db.event_status.value == "PROCESSED"

    stock = stock_repo.get_by_product_and_location(db, product.id, "Laboratorio")
    assert stock is not None
    assert stock.quantity == 3

    movement = db.query(Movement).filter(Movement.product_id == product.id).first()
    assert movement is not None
