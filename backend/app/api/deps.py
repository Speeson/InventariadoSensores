from fastapi import Depends, HTTPException, status
from jose import jwt, JWTError
from sqlalchemy.orm import Session

from app.api.security import get_bearer_token
from app.core.config import settings
from app.db.deps import get_db
from app.repositories.user_repo import get_by_email
from app.models.user import User

def get_current_user(db: Session = Depends(get_db), token: str = Depends(get_bearer_token)) -> User:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
        email: str | None = payload.get("sub")
        if not email:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    except JWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    user = get_by_email(db, email)
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    return user

def require_roles(*allowed: str):
    def _checker(user: User = Depends(get_current_user)) -> User:
        if user.role not in allowed:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Insufficient permissions")
        return user
    return _checker
