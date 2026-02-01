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
