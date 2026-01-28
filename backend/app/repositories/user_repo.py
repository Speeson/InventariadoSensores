from sqlalchemy.orm import Session
from sqlalchemy import select

from app.models.enums import UserRole
from app.models.user import User

def normalize_email(email: str) -> str:
    return email.lower().strip()

def get_by_email(db: Session, email: str) -> User | None:
    return db.scalar(select(User).where(User.email == normalize_email(email)))

def get_by_username(db: Session, username: str) -> User | None:
    normalized_username = username.strip()
    return db.scalar(select(User).where(User.username == normalized_username))

def create_user(db: Session, email: str, username: str, password_hash: str, role: str = "USER") -> User:
    normalized_email = normalize_email(email)
    role_enum = UserRole(role)
    user = User(
        username=username.strip(),
        email=normalized_email,
        password_hash=password_hash,
        role=role_enum,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user
