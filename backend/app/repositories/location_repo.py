from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.location import Location


def get_by_code(db: Session, code: str) -> Location | None:
    normalized = code.strip()
    return db.scalar(select(Location).where(Location.code == normalized))


def get_or_create(db: Session, code: str, description: str | None = None) -> Location:
    normalized = code.strip()
    location = db.scalar(select(Location).where(Location.code == normalized))
    if location:
        return location
    location = Location(code=normalized, description=description)
    db.add(location)
    db.commit()
    db.refresh(location)
    return location
