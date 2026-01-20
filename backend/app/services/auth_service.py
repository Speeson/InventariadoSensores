from sqlalchemy.orm import Session

from app.core.security import hash_password, verify_password, create_access_token
from app.models.enums import UserRole
from app.repositories.user_repo import get_by_email, create_user

class AuthError(Exception):
    pass

def register(db: Session, email: str, password: str, role: str | None = None) -> str:
    existing = get_by_email(db, email)
    if existing:
        raise AuthError("Email already registered")

    role_to_set = role or UserRole.USER.value
    try:
        user = create_user(db, email=email, password_hash=hash_password(password), role=role_to_set)
    except ValueError:
        raise AuthError("Invalid role")
    return create_access_token(subject=user.email, role=user.role.value)

def login(db: Session, email: str, password: str) -> str:
    user = get_by_email(db, email)
    if not user or not verify_password(password, user.password_hash):
        raise AuthError("Invalid credentials")

    return create_access_token(subject=user.email, role=user.role.value)
