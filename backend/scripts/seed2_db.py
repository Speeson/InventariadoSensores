from app.db.session import SessionLocal
from app.models.import_batch import ImportBatch
from app.models.import_error import ImportError
from app.models.import_review import ImportReview
from app.models.user import User
from app.models.product import Product
from app.models.category import Category
from app.models.location import Location


def run_seed2():
    db = SessionLocal()
    try:
        user = db.query(User).first()
        if not user:
            print("Seed2: no hay usuarios, ejecuta seed_db primero")
            return

        category = db.query(Category).first()
        location = db.query(Location).first()

        if not category or not location:
            print("Seed2: faltan categorias o locations, ejecuta seed_db primero")
            return

        product = db.query(Product).first()

        batch = ImportBatch(
            kind="EVENTS",
            user_id=user.id,
            dry_run=True,
            total_rows=3,
            ok_rows=1,
            error_rows=1,
            review_rows=1,
        )
        db.add(batch)
        db.flush()

        payload_ok = {
            "type": "IN",
            "sku": product.sku if product else "SKU-SEED-1",
            "barcode": product.barcode if product else "9999999999999",
            "name": product.name if product else "Producto Seed",
            "category_id": str(category.id),
            "location_id": str(location.id),
            "quantity": "5",
        }
        payload_error = {
            "type": "OUT",
            "sku": "SKU-ERROR-1",
            "barcode": "0000000000000",
            "name": "Producto Incorrecto",
            "category_id": "9999",
            "location_id": str(location.id),
            "quantity": "3",
        }
        payload_review = {
            "type": "IN",
            "sku": "SKU-REV-1",
            "barcode": "1111111111111",
            "name": "Ryzen 7 9099",
            "category_id": str(category.id),
            "location_id": str(location.id),
            "quantity": "7",
        }

        db.add(
            ImportError(
                batch_id=batch.id,
                row_number=3,
                error_code="validation_error",
                message="category_id no existe",
                payload=payload_error,
            )
        )
        db.add(
            ImportReview(
                batch_id=batch.id,
                row_number=4,
                reason="possible_duplicate",
                payload=payload_review,
                suggestions={
                    "matches": [
                        {
                            "product_id": product.id if product else None,
                            "name": product.name if product else "Procesador AMD Ryzen 7 9900",
                            "sku": product.sku if product else "CPU-R7-9900",
                            "barcode": product.barcode if product else "8400000000000",
                            "similarity": 0.91,
                        }
                    ]
                },
            )
        )

        db.commit()
        print("Seed2: import_batches/import_errors/import_reviews creados")
    except Exception as exc:
        db.rollback()
        print("Seed2 error:", exc)
    finally:
        db.close()


if __name__ == "__main__":
    run_seed2()
