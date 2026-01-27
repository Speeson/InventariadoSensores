from app.db.session import SessionLocal
from app.models.category import Category
from app.models.product import Product

def main():
    db = SessionLocal()

    try:
        # 1️⃣ Crear categoría
        category = Category(name="Sensores")
        db.add(category)
        db.commit()
        db.refresh(category)

        print("✅ Categoría creada:", category.id, category.name)

        # 2️⃣ Crear producto
        product = Product(
            name="Sensor de temperatura",
            sku="TEMP-001",
            category_id=category.id,
            active=True
        )

        db.add(product)
        db.commit()
        db.refresh(product)

        print("✅ Producto creado:", product.id, product.name)

        # 3️⃣ Leer datos
        products = db.query(Product).all()
        print("📦 Productos en BD:")
        for p in products:
            print("-", p.id, p.name, p.active)

    except Exception as e:
        db.rollback()
        print("❌ ERROR:", e)

    finally:
        db.close()

if __name__ == "__main__":
    main()
