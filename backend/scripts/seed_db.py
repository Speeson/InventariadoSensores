# backend/scripts/seed2_db.py

from app.db.session import SessionLocal
from app.models.audit_log import ActionType, AuditLog
from app.models.category import Category
from app.models.entity import Entity
from app.models.event import Event, EventType
from app.models.movement import Movement, MovementSource, MovementType
from app.models.product import Product
from app.models.stock import Stock
from app.models.user import User


def run_seed():
    db = SessionLocal()

    try:
        categories = [
            Category(name="Sensores IoT"),
            Category(name="Gateways"),
            Category(name="Actuadores"),
            Category(name="Energia"),
            Category(name="Accesorios"),
            Category(name="Redes"),
            Category(name="Controladores"),
            Category(name="Monitoreo"),
            Category(name="Mantenimiento"),
            Category(name="Calibracion"),
            Category(name="Seguridad"),
            Category(name="Climatizacion"),
            Category(name="Iluminacion"),
            Category(name="Agua y Caudal"),
            Category(name="Vibracion"),
            Category(name="GPS y Localizacion"),
            Category(name="Baterias"),
            Category(name="Antenas"),
            Category(name="Montaje"),
            Category(name="Herramientas"),
        ]
        db.add_all(categories)
        db.commit()

        entities = [
            Entity(name="Oficina Central"),
            Entity(name="Planta Norte"),
            Entity(name="Planta Sur"),
            Entity(name="Laboratorio I+D"),
            Entity(name="Cliente Demo"),
        ]
        db.add_all(entities)
        db.commit()

        products = [
            Product(
                sku="S-TH-100",
                name="Sensor temp/humedad LoRa",
                barcode="100001",
                category_id=categories[0].id,
                active=True,
            ),
            Product(
                sku="S-CO2-200",
                name="Sensor CO2 Zigbee",
                barcode="100002",
                category_id=categories[0].id,
                active=True,
            ),
            Product(
                sku="GW-LORA-01",
                name="Gateway LoRaWAN",
                barcode="200001",
                category_id=categories[1].id,
                active=True,
            ),
            Product(
                sku="ACT-RELE-01",
                name="Actuador rele DIN",
                barcode="300001",
                category_id=categories[2].id,
                active=True,
            ),
            Product(
                sku="PWR-UPS-01",
                name="UPS 12V para nodo IoT",
                barcode="400001",
                category_id=categories[3].id,
                active=True,
            ),
        ]
        db.add_all(products)
        db.commit()

        stocks = [
            Stock(product_id=products[0].id, quantity=120, location="Oficina Central"),
            Stock(product_id=products[1].id, quantity=80, location="Planta Norte"),
            Stock(product_id=products[2].id, quantity=15, location="Planta Sur"),
            Stock(product_id=products[3].id, quantity=40, location="Laboratorio I+D"),
            Stock(product_id=products[4].id, quantity=25, location="Cliente Demo"),
        ]
        db.add_all(stocks)
        db.commit()

        events = [
            Event(
                event_type=EventType.SENSOR_IN,
                product_id=products[0].id,
                delta=12,
                source="SIMULADOR",
                processed=True,
            ),
            Event(
                event_type=EventType.SENSOR_IN,
                product_id=products[1].id,
                delta=8,
                source="SIMULADOR",
                processed=True,
            ),
            Event(
                event_type=EventType.SENSOR_OUT,
                product_id=products[2].id,
                delta=-2,
                source="APP",
                processed=True,
            ),
            Event(
                event_type=EventType.SENSOR_IN,
                product_id=products[3].id,
                delta=5,
                source="INGESTOR",
                processed=True,
            ),
            Event(
                event_type=EventType.SENSOR_OUT,
                product_id=products[4].id,
                delta=-1,
                source="APP",
                processed=True,
            ),
        ]
        db.add_all(events)
        db.commit()

        users = db.query(User).order_by(User.id).all()
        if users:
            user_ids = [user.id for user in users]

            def pick_user(index: int) -> int:
                return user_ids[index % len(user_ids)]

            movements = [
                Movement(
                    product_id=products[0].id,
                    quantity=6,
                    user_id=pick_user(0),
                    movement_type=MovementType.IN,
                    movement_source=MovementSource.SCAN,
                ),
                Movement(
                    product_id=products[1].id,
                    quantity=3,
                    user_id=pick_user(1),
                    movement_type=MovementType.IN,
                    movement_source=MovementSource.MANUAL,
                ),
                Movement(
                    product_id=products[2].id,
                    quantity=1,
                    user_id=pick_user(2),
                    movement_type=MovementType.OUT,
                    movement_source=MovementSource.MANUAL,
                ),
                Movement(
                    product_id=products[3].id,
                    quantity=2,
                    user_id=pick_user(3),
                    movement_type=MovementType.ADJUST,
                    movement_source=MovementSource.SCAN,
                ),
                Movement(
                    product_id=products[4].id,
                    quantity=1,
                    user_id=pick_user(4),
                    movement_type=MovementType.OUT,
                    movement_source=MovementSource.SCAN,
                ),
            ]
            db.add_all(movements)
            db.commit()

            audit_logs = [
                AuditLog(
                    entity_id=entities[0].id,
                    action=ActionType.CREATE,
                    user_id=pick_user(0),
                    details="Alta de sensor IoT",
                ),
                AuditLog(
                    entity_id=entities[1].id,
                    action=ActionType.UPDATE,
                    user_id=pick_user(1),
                    details="Ajuste de stock por calibracion",
                ),
                AuditLog(
                    entity_id=entities[2].id,
                    action=ActionType.UPDATE,
                    user_id=pick_user(2),
                    details="Movimiento por mantenimiento",
                ),
                AuditLog(
                    entity_id=entities[3].id,
                    action=ActionType.CREATE,
                    user_id=pick_user(3),
                    details="Registro de gateway",
                ),
                AuditLog(
                    entity_id=entities[4].id,
                    action=ActionType.DELETE,
                    user_id=pick_user(4),
                    details="Baja de accesorio defectuoso",
                ),
            ]
            db.add_all(audit_logs)
            db.commit()
        else:
            print("Sin usuarios existentes, se omiten movimientos y auditoria")

        print("Seed2 ejecutado correctamente")

    except Exception as e:
        db.rollback()
        print("Error en seed2:", e)

    finally:
        db.close()


if __name__ == "__main__":
    run_seed()
