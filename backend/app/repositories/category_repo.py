from typing import Iterable, Tuple
from sqlalchemy import func, select
from sqlalchemy.orm import Session
from app.models.category import Category

def _normalize(name: str) -> str:
    return name.strip()

def get(db: Session, category_id: int) -> Category | None:
    return db.get(Category, category_id)

def get_by_name(db: Session, name: str) -> Category | None:
    normalized = _normalize(name)
    return db.scalar(
        select(Category).where(func.lower(Category.name) == normalized.lower())
    )

def list_categories(
    db: Session, *, name: str | None = None, limit: int = 50, offset: int = 0
) -> Tuple[Iterable[Category], int]:
    filters = []
    if name:
        filters.append(func.lower(Category.name).ilike(f"%{_normalize(name).lower()}%"))
    stmt = select(Category).where(*filters).order_by(Category.created_at.desc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total

def create_category(db: Session, *, name: str) -> Category:
    normalized = _normalize(name)
    category = Category(name=normalized)
    db.add(category)
    db.commit()
    db.refresh(category)
    return category

def update_category(db: Session, category: Category, *, name: str) -> Category:
    category.name = _normalize(name)
    db.add(category)
    db.commit()
    db.refresh(category)
    return category

def delete_category(db: Session, category: Category) -> None:
    db.delete(category)
    db.commit()
