from fastapi import APIRouter, Depends, HTTPException, Form, status
from sqlalchemy.orm import Session

from app.db.deps import get_db
from app.schemas.auth import RegisterRequest, TokenResponse
from app.services import auth_service

router = APIRouter(prefix="/auth", tags=["auth"])

@router.post("/register", response_model=TokenResponse)
def register(payload: RegisterRequest, db: Session = Depends(get_db)):
    try:
        token = auth_service.register(
            db,
            payload.email,
            payload.password,
            payload.role,
            payload.username,
        )
        return TokenResponse(access_token=token)
    except auth_service.AuthError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))

@router.post("/login", response_model=TokenResponse)
def login(
    email: str | None = Form(None),
    username: str | None = Form(None),
    password: str = Form(...),
    db: Session = Depends(get_db),
):
    try:
        login_email = email or username
        if not login_email:
            raise auth_service.AuthError("Email is required")
        token = auth_service.login(db, login_email, password)
        return TokenResponse(access_token=token)
    except auth_service.AuthError as e:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=str(e))
