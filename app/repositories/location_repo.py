from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.models.location import Location


def get_by_code(db: Session, code: str) -> Location | None:
    normalized = code.strip()
    return db.scalar(
        select(Location).where(func.lower(Location.code) == normalized.lower())
    )

def get_or_create(db: Session, code: str, description: str | None = None) -> Location:
    normalized = code.strip()
    location = db.scalar(
        select(Location).where(func.lower(Location.code) == normalized.lower())
    )
    if location:
        return location
    location = Location(code=normalized, description=description)
    db.add(location)
    db.commit()
    db.refresh(location)
    return location


def list_locations(
    db: Session,
    *,
    limit: int = 50,
    offset: int = 0,
) -> tuple[list[Location], int]:
    stmt = select(Location).order_by(Location.code.asc())
    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total
