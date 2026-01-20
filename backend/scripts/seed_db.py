# backend/scripts/seed_db.py

from app.db.session import SessionLocal
from app.models.category import Category
from app.models.entity import Entity
from app.models.user import User, UserRole
from app.models.product import Product
from app.models.stock import Stock
from app.models.event import Event, EventType
from app.models.movement import Movement, MovementType, MovementSource
from app.models.audit_log import AuditLog, ActionType


def run_seed():
    db = SessionLocal()

    try:
        # 1️⃣ Categories
        categories = [
            Category(name="Sensores"),
            Category(name="Actuadores"),
            Category(name="Controladores")
        ]
        db.add_all(categories)
        db.commit()

        # 2️⃣ Entities
        entities = [
            Entity(name="Almacén Central"),
            Entity(name="Planta Producción")
        ]
        db.add_all(entities)
        db.commit()

        # 3️⃣ Users
        users = [
            User(
                username="admin",
                email="admin@inventory.com",
                password_hash="hashed_admin",
                role=UserRole.ADMIN
            ),
            User(
                username="manager",
                email="manager@inventory.com",
                password_hash="hashed_manager",
                role=UserRole.MANAGER
            ),
            User(
                username="user",
                email="user@inventory.com",
                password_hash="hashed_user",
                role=UserRole.USER
            )
        ]
        db.add_all(users)
        db.commit()

        # 4️⃣ Products
        products = [
            Product(
                sku="TEMP-001",
                name="Sensor de temperatura",
                barcode="111111",
                category_id=categories[0].id,
                active=True
            ),
            Product(
                sku="HUM-001",
                name="Sensor de humedad",
                barcode="222222",
                category_id=categories[0].id,
                active=True
            )
        ]
        db.add_all(products)
        db.commit()

        # 5️⃣ Stocks
        stocks = [
            Stock(
                product_id=products[0].id,
                quantity=100,
                location="Almacén Central"
            ),
            Stock(
                product_id=products[1].id,
                quantity=50,
                location="Planta Producción"
            )
        ]
        db.add_all(stocks)
        db.commit()

        # 6️⃣ Events
        events = [
            Event(
                event_type=EventType.SENSOR_IN,
                product_id=products[0].id,
                delta=10,
                source="SENSOR",
                processed=True
            ),
            Event(
                event_type=EventType.SENSOR_OUT,
                product_id=products[1].id,
                delta=-5,
                source="MANUAL",
                processed=True
            )
        ]
        db.add_all(events)
        db.commit()

        # 7️⃣ Movements
        movements = [
            Movement(
                product_id=products[0].id,
                quantity=10,
                user_id=users[1].id,
                movement_type=MovementType.IN,
                movement_source=MovementSource.SCAN
            ),
            Movement(
                product_id=products[1].id,
                quantity=5,
                user_id=users[2].id,
                movement_type=MovementType.OUT,
                movement_source=MovementSource.MANUAL
            )
        ]
        db.add_all(movements)
        db.commit()

        # 8️⃣ Audit Log
        audit_logs = [
            AuditLog(
                entity_id=entities[0].id,
                action=ActionType.CREATE,
                user_id=users[0].id,
                details="Creación de producto"
            ),
            AuditLog(
                entity_id=entities[1].id,
                action=ActionType.UPDATE,
                user_id=users[1].id,
                details="Actualización de stock"
            )
        ]
        db.add_all(audit_logs)
        db.commit()

        print("✅ Seed ejecutado correctamente")

    except Exception as e:
        db.rollback()
        print("❌ Error en seed:", e)

    finally:
        db.close()


if __name__ == "__main__":
    run_seed()
