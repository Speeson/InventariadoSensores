from app.db.session import SessionLocal
from app.services import label_service
import os
from app.core.security import hash_password
from app.models.audit_log import AuditLog
from app.models.category import Category
from app.models.event import Event
from app.models.movement import Movement
from app.models.product import Product
from app.models.stock import Stock
from app.models.stock_threshold import StockThreshold
from app.models.user import User
from app.models.enums import (
    ActionType,
    Entity,
    EventStatus,
    EventType,
    MovementType,
    Source,
    UserRole,
)
from app.repositories import location_repo
from datetime import datetime, timedelta


def run_seed(generate_labels: bool = False):
    db = SessionLocal()

    try:
        def get_or_create(model, defaults=None, **kwargs):
            instance = db.query(model).filter_by(**kwargs).first()
            if instance:
                return instance, False
            params = dict(kwargs)
            if defaults:
                params.update(defaults)
            instance = model(**params)
            db.add(instance)
            return instance, True

        categories = []
        for name in [
            "Procesadores", "Placas base", "Memoria RAM", "Almacenamiento", "Tarjetas graficas",
            "Fuentes de alimentacion", "Cajas y chasis", "Refrigeracion", "Perifericos", "Redes",
            "Audio", "Monitores", "Cables y adaptadores", "Sistemas operativos", "Software",
            "Herramientas", "Componentes portatiles", "Servidores", "Accesorios", "Limpieza y mantenimiento",
        ]:
            category, _ = get_or_create(Category, name=name)
            categories.append(category)
        db.commit()

        if generate_labels:
            label_failures = 0
            for product in products + extra_products:
                if not product.barcode:
                    continue
                try:
                    label_service.generate_and_store_label(
                        product_id=product.id,
                        barcode_value=product.barcode,
                        sku=product.sku,
                    )
                except Exception:
                    label_failures += 1
            if label_failures:
                print(f"Etiquetas: {label_failures} fallos al generar")

        users = []
        for username, email, role in [
            ("admin", "admin@example.com", UserRole.ADMIN),
            ("manager", "manager@example.com", UserRole.MANAGER),
            ("user", "user@example.com", UserRole.USER),
        ]:
            user, _ = get_or_create(
                User,
                email=email,
                defaults={
                    "username": username,
                    "password_hash": hash_password("Pass123!"),
                    "role": role,
                },
            )
            users.append(user)
        db.commit()

        # Locations
        loc_codes = [
            "Oficina Central",
            "Planta Norte",
            "Planta Sur",
            "Laboratorio I+D",
            "Almacen A",
            "Almacen B",
            "Almacen C",
            "Sala Pruebas",
            "Taller",
            "Recepcion",
        ]
        locations = {code: location_repo.get_or_create(db, code) for code in loc_codes}
        db.commit()

        category_by_name = {category.name: category for category in categories}
        products = []
        for sku, name, barcode, category_name in [
            ("CPU-R5-5600", "Procesador AMD Ryzen 5 5600", "8403951724081", "Procesadores"),
            ("CPU-I5-12400", "Procesador Intel Core i5-12400", "8406179042356", "Procesadores"),
            ("MB-B550-ATX", "Placa base B550 ATX", "8402819467532", "Placas base"),
            ("MB-B760-MATX", "Placa base B760 mATX", "8407395281460", "Placas base"),
            ("RAM-16-3200", "Memoria RAM 16GB DDR4 3200", "8404928571634", "Memoria RAM"),
            ("RAM-32-3600", "Memoria RAM 32GB DDR4 3600", "8401659078423", "Memoria RAM"),
            ("SSD-1TB-NVME", "SSD NVMe 1TB", "8408735204197", "Almacenamiento"),
            ("HDD-2TB-7200", "HDD 2TB 7200rpm", "8402589043618", "Almacenamiento"),
            ("GPU-RTX-4060", "Tarjeta grafica RTX 4060", "8406903175249", "Tarjetas graficas"),
            ("GPU-RX-7600", "Tarjeta grafica RX 7600", "8403146892057", "Tarjetas graficas"),
            ("PSU-650W-80P", "Fuente alimentacion 650W 80Plus", "8409051736428", "Fuentes de alimentacion"),
            ("CASE-MID-ATX", "Caja ATX mid tower", "8404716298053", "Cajas y chasis"),
            ("COOL-AIR-120", "Ventilador 120mm PWM", "8401275948306", "Refrigeracion"),
            ("COOL-AIO-240", "Refrigeracion liquida 240mm", "8405620817941", "Refrigeracion"),
            ("KBD-MECH-01", "Teclado mecanico USB", "8403987612540", "Perifericos"),
            ("MSE-ERG-01", "Raton ergonomico", "8407462158039", "Perifericos"),
            ("NIC-PCI-1G", "Tarjeta red PCIe 1Gb", "8402349075162", "Redes"),
            ("MON-27-IPS", "Monitor 27\" IPS 144Hz", "8408196734255", "Monitores"),
            ("CAB-USB-C", "Cable USB-C 1m", "8405063218974", "Cables y adaptadores"),
            ("TOOL-PS-SET", "Kit destornilladores precision", "8406739502186", "Herramientas"),
        ]:
            product, _ = get_or_create(
                Product,
                sku=sku,
                defaults={
                    "name": name,
                    "barcode": barcode,
                    "category_id": category_by_name[category_name].id,
                    "active": True,
                },
            )
            products.append(product)
        db.commit()

        # Productos extra para reportes (no tocar los 20 anteriores)
        extra_products = []
        for sku, name, barcode, category_name in [
            ("CPU-R7-7700", "Procesador AMD Ryzen 7 7700", "8410010203001", "Procesadores"),
            ("GPU-RTX-4070", "Tarjeta grafica RTX 4070", "8410010203002", "Tarjetas graficas"),
            ("SSD-2TB-NVME", "SSD NVMe 2TB", "8410010203003", "Almacenamiento"),
            ("RAM-64-5200", "Memoria RAM 64GB DDR5 5200", "8410010203004", "Memoria RAM"),
            ("MON-34-UW", "Monitor 34\" Ultrawide", "8410010203005", "Monitores"),
            ("NIC-PCI-10G", "Tarjeta red PCIe 10Gb", "8410010203006", "Redes"),
        ]:
            product, _ = get_or_create(
                Product,
                sku=sku,
                defaults={
                    "name": name,
                    "barcode": barcode,
                    "category_id": category_by_name[category_name].id,
                    "active": True,
                },
            )
            extra_products.append(product)
        db.commit()

        all_products = products + extra_products

        for product, quantity, location in [
            (products[0], 120, "Oficina Central"),
            (products[1], 80, "Planta Norte"),
            (products[2], 15, "Planta Sur"),
            (products[3], 40, "Laboratorio I+D"),
            (products[4], 25, "Almacen A"),
            (products[5], 18, "Almacen B"),
            (products[6], 22, "Almacen C"),
            (products[7], 12, "Sala Pruebas"),
            (products[8], 30, "Taller"),
            (products[9], 9, "Recepcion"),
            # stocks extra para nuevos productos
            (extra_products[0], 55, "Oficina Central"),
            (extra_products[1], 14, "Almacen A"),
            (extra_products[2], 60, "Almacen B"),
            (extra_products[3], 20, "Planta Norte"),
            (extra_products[4], 8, "Sala Pruebas"),
            (extra_products[5], 6, "Laboratorio I+D"),
        ]:
            location_obj = locations[location]
            get_or_create(
                Stock,
                product_id=product.id,
                location_id=location_obj.id,
                defaults={"quantity": quantity},
            )
        db.commit()

        for product, location, min_qty in [
            (products[0], "Oficina Central", 20),
            (products[1], "Planta Norte", 15),
            (products[2], "Planta Sur", 5),
            # thresholds extra para demo de alertas
            (products[9], "Recepcion", 12),
            (extra_products[1], "Almacen A", 18),
            (extra_products[5], "Laboratorio I+D", 8),
        ]:
            loc_obj = locations[location]
            get_or_create(
                StockThreshold,
                product_id=product.id,
                location_id=loc_obj.id,
                defaults={"min_quantity": min_qty},
            )
        db.commit()

        for event_type, product, delta, source, key, loc in [
            (EventType.SENSOR_IN, products[0], 12, Source.SCAN, "evt-1001", "Oficina Central"),
            (EventType.SENSOR_IN, products[1], 8, Source.SCAN, "evt-1002", "Planta Norte"),
            (EventType.SENSOR_OUT, products[2], 2, Source.MANUAL, "evt-1003", "Planta Sur"),
            (EventType.SENSOR_IN, products[3], 5, Source.MANUAL, "evt-1004", "Laboratorio I+D"),
            (EventType.SENSOR_OUT, products[4], 1, Source.SCAN, "evt-1005", "Almacen A"),
        ]:
            loc_obj = locations[loc]
            get_or_create(
                Event,
                idempotency_key=key,
                defaults={
                    "event_type": event_type,
                    "product_id": product.id,
                    "delta": delta,
                    "source": source,
                    "event_status": EventStatus.PROCESSED,
                    "retry_count": 0,
                    "location_id": loc_obj.id,
                },
            )
        db.commit()

        user_ids = [user.id for user in users]

        def pick_user(index: int) -> int:
            return user_ids[index % len(user_ids)]

        now = datetime.utcnow()
        movements = [
            {
                "product_id": products[0].id,
                "quantity": 6,
                "user_id": pick_user(0),
                "movement_type": MovementType.IN,
                "movement_source": Source.SCAN,
                "location_id": locations["Oficina Central"].id,
                "created_at": now - timedelta(days=12),
            },
            {
                "product_id": products[1].id,
                "quantity": 3,
                "user_id": pick_user(1),
                "movement_type": MovementType.IN,
                "movement_source": Source.MANUAL,
                "location_id": locations["Planta Norte"].id,
                "created_at": now - timedelta(days=10),
            },
            {
                "product_id": products[2].id,
                "quantity": 1,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Planta Sur"].id,
                "created_at": now - timedelta(days=9),
            },
            {
                "product_id": products[3].id,
                "quantity": 2,
                "user_id": pick_user(0),
                "movement_type": MovementType.ADJUST,
                "movement_source": Source.SCAN,
                "location_id": locations["Laboratorio I+D"].id,
                "created_at": now - timedelta(days=8),
            },
            {
                "product_id": products[4].id,
                "quantity": 1,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Almacen A"].id,
                "created_at": now - timedelta(days=7),
            },
            {
                "product_id": products[0].id,
                "quantity": 12,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Oficina Central"].id,
                "created_at": now - timedelta(days=6),
            },
            {
                "product_id": products[1].id,
                "quantity": 9,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Planta Norte"].id,
                "created_at": now - timedelta(days=5),
            },
            {
                "product_id": products[2].id,
                "quantity": 5,
                "user_id": pick_user(0),
                "movement_type": MovementType.IN,
                "movement_source": Source.MANUAL,
                "location_id": locations["Planta Sur"].id,
                "created_at": now - timedelta(days=4),
            },
            {
                "product_id": products[3].id,
                "quantity": 4,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Laboratorio I+D"].id,
                "created_at": now - timedelta(days=3),
            },
            {
                "product_id": products[4].id,
                "quantity": 2,
                "user_id": pick_user(2),
                "movement_type": MovementType.IN,
                "movement_source": Source.SCAN,
                "location_id": locations["Almacen A"].id,
                "created_at": now - timedelta(days=2),
            },
            {
                "product_id": products[0].id,
                "quantity": 4,
                "user_id": pick_user(0),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Oficina Central"].id,
                "transfer_id": "11111111-1111-1111-1111-111111111111",
                "created_at": now - timedelta(days=1),
            },
            {
                "product_id": products[0].id,
                "quantity": 4,
                "user_id": pick_user(0),
                "movement_type": MovementType.IN,
                "movement_source": Source.MANUAL,
                "location_id": locations["Planta Norte"].id,
                "transfer_id": "11111111-1111-1111-1111-111111111111",
                "created_at": now - timedelta(days=1),
            },
            # movimientos extra para reportes (top consumidos / turnover)
            {
                "product_id": extra_products[0].id,
                "quantity": 25,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Oficina Central"].id,
                "created_at": now - timedelta(days=20),
            },
            {
                "product_id": extra_products[1].id,
                "quantity": 18,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Almacen A"].id,
                "created_at": now - timedelta(days=18),
            },
            {
                "product_id": extra_products[2].id,
                "quantity": 30,
                "user_id": pick_user(0),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Almacen B"].id,
                "created_at": now - timedelta(days=16),
            },
            {
                "product_id": extra_products[3].id,
                "quantity": 12,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Planta Norte"].id,
                "created_at": now - timedelta(days=14),
            },
            {
                "product_id": extra_products[4].id,
                "quantity": 10,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Sala Pruebas"].id,
                "created_at": now - timedelta(days=13),
            },
            {
                "product_id": extra_products[2].id,
                "quantity": 40,
                "user_id": pick_user(0),
                "movement_type": MovementType.IN,
                "movement_source": Source.MANUAL,
                "location_id": locations["Almacen B"].id,
                "created_at": now - timedelta(days=22),
            },
            {
                "product_id": extra_products[1].id,
                "quantity": 8,
                "user_id": pick_user(1),
                "movement_type": MovementType.IN,
                "movement_source": Source.MANUAL,
                "location_id": locations["Almacen A"].id,
                "created_at": now - timedelta(days=21),
            },
            {
                "product_id": extra_products[0].id,
                "quantity": 15,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Oficina Central"].id,
                "created_at": now - timedelta(days=11),
            },
            {
                "product_id": extra_products[1].id,
                "quantity": 22,
                "user_id": pick_user(0),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Almacen A"].id,
                "created_at": now - timedelta(days=10),
            },
            {
                "product_id": extra_products[2].id,
                "quantity": 35,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Almacen B"].id,
                "created_at": now - timedelta(days=9),
            },
            {
                "product_id": extra_products[3].id,
                "quantity": 14,
                "user_id": pick_user(2),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Planta Norte"].id,
                "created_at": now - timedelta(days=8),
            },
            {
                "product_id": extra_products[4].id,
                "quantity": 9,
                "user_id": pick_user(0),
                "movement_type": MovementType.OUT,
                "movement_source": Source.SCAN,
                "location_id": locations["Sala Pruebas"].id,
                "created_at": now - timedelta(days=7),
            },
            {
                "product_id": extra_products[5].id,
                "quantity": 11,
                "user_id": pick_user(1),
                "movement_type": MovementType.OUT,
                "movement_source": Source.MANUAL,
                "location_id": locations["Laboratorio I+D"].id,
                "created_at": now - timedelta(days=6),
            },
        ]
        for movement in movements:
            if "delta" not in movement:
                qty = movement["quantity"]
                mtype = movement["movement_type"]
                if mtype == MovementType.OUT:
                    movement["delta"] = -qty
                else:
                    movement["delta"] = qty
            get_or_create(Movement, **movement)
        db.commit()

        audit_logs = [
            {
                "entity": Entity.PRODUCT,
                "action": ActionType.CREATE,
                "user_id": pick_user(0),
                "details": "Alta de sensor IoT",
            },
            {
                "entity": Entity.STOCK,
                "action": ActionType.UPDATE,
                "user_id": pick_user(1),
                "details": "Ajuste de stock por calibracion",
            },
            {
                "entity": Entity.MOVEMENT,
                "action": ActionType.UPDATE,
                "user_id": pick_user(2),
                "details": "Movimiento por mantenimiento",
            },
            {
                "entity": Entity.PRODUCT,
                "action": ActionType.CREATE,
                "user_id": pick_user(0),
                "details": "Registro de gateway",
            },
            {
                "entity": Entity.PRODUCT,
                "action": ActionType.DELETE,
                "user_id": pick_user(1),
                "details": "Baja de accesorio defectuoso",
            },
        ]
        for audit_log in audit_logs:
            get_or_create(AuditLog, **audit_log)
        db.commit()

        print("Seed ejecutado correctamente")

    except Exception as e:
        db.rollback()
        print("Error en seed:", e)

    finally:
        db.close()


if __name__ == "__main__":
    generate_labels = os.getenv("SEED_LABELS", "").strip() in {"1", "true", "TRUE", "yes", "YES"}
    run_seed(generate_labels=generate_labels)
