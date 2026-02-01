from app.db.session import SessionLocal
from app.repositories import location_repo
from app.models.audit_log import AuditLog
from app.models.category import Category
from app.models.event import Event
from app.models.movement import Movement
from app.models.product import Product
from app.models.stock import Stock
from app.models.user import User
from app.models.enums import (
    ActionType,
    Entity,
    EventType,
    EventStatus,
    MovementType,
    Source,
)


def run_seed():
    db = SessionLocal()

    try:
        # Categories
        categories = [
            Category(name="Sensores"),
            Category(name="Actuadores"),
            Category(name="Controladores"),
        ]
        db.add_all(categories)
        db.commit()

        # Locations
        loc_codes = ["Almacen Central", "Planta Produccion"]
        locations = {code: location_repo.get_or_create(db, code) for code in loc_codes}
        db.commit()

        # Products
        products = [
            Product(
                sku="TEMP-001",
                name="Sensor de temperatura",
                barcode="111111",
                category_id=categories[0].id,
                active=True,
            ),
            Product(
                sku="HUM-001",
                name="Sensor de humedad",
                barcode="222222",
                category_id=categories[0].id,
                active=True,
            ),
        ]
        db.add_all(products)
        db.commit()

        # Stocks
        stocks = [
            Stock(
                product_id=products[0].id,
                quantity=100,
                location_id=locations["Almacen Central"].id,
            ),
            Stock(
                product_id=products[1].id,
                quantity=50,
                location_id=locations["Planta Produccion"].id,
            ),
        ]
        db.add_all(stocks)
        db.commit()

        # Events
        events = [
            Event(
                event_type=EventType.SENSOR_IN,
                product_id=products[0].id,
                delta=10,
                source=Source.SCAN,
                event_status=EventStatus.PROCESSED,
                retry_count=0,
                idempotency_key="evt-2001",
                location_id=locations["Almacen Central"].id,
            ),
            Event(
                event_type=EventType.SENSOR_OUT,
                product_id=products[1].id,
                delta=-5,
                source=Source.MANUAL,
                event_status=EventStatus.PROCESSED,
                retry_count=0,
                idempotency_key="evt-2002",
                location_id=locations["Planta Produccion"].id,
            ),
        ]
        db.add_all(events)
        db.commit()

        # Movements + Audit Log (only if users already exist)
        users = db.query(User).order_by(User.id).all()
        if users:
            admin_user = users[0]
            manager_user = users[1] if len(users) > 1 else users[0]
            normal_user = users[2] if len(users) > 2 else users[0]

            movements = [
                Movement(
                    product_id=products[0].id,
                    quantity=10,
                    user_id=manager_user.id,
                    movement_type=MovementType.IN,
                    movement_source=Source.SCAN,
                    location_id=locations["Almacen Central"].id,
                ),
                Movement(
                    product_id=products[1].id,
                    quantity=5,
                    user_id=normal_user.id,
                    movement_type=MovementType.OUT,
                    movement_source=Source.MANUAL,
                    location_id=locations["Planta Produccion"].id,
                ),
            ]
            db.add_all(movements)
            db.commit()

            audit_logs = [
                AuditLog(
                    entity=Entity.PRODUCT,
                    action=ActionType.CREATE,
                    user_id=admin_user.id,
                    details="Creacion de producto",
                ),
                AuditLog(
                    entity=Entity.STOCK,
                    action=ActionType.UPDATE,
                    user_id=manager_user.id,
                    details="Actualizacion de stock",
                ),
            ]
            db.add_all(audit_logs)
            db.commit()
        else:
            print("Sin usuarios existentes, se omiten movimientos y auditoria")

        print("Seed ejecutado correctamente")

    except Exception as e:
        db.rollback()
        print("Error en seed:", e)

    finally:
        db.close()


if __name__ == "__main__":
    run_seed()
