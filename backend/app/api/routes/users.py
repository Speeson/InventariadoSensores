from fastapi import APIRouter, Depends

from app.api.deps import get_current_user, require_roles
from app.models.user import User
from app.schemas.user import UserMeResponse
from app.schemas.fcm import FcmTokenUpsert
from app.repositories import fcm_token_repo
from app.db.deps import get_db
from sqlalchemy.orm import Session


router = APIRouter(prefix="/users", tags=["users"])

@router.get(
    "/me",
    response_model=UserMeResponse,
    responses={
        401: {
            "description": "Token invalido o expirado",
            "content": {"application/json": {"example": {"detail": "Not authenticated"}}},
        }
    },
)
def me(user: User = Depends(get_current_user)):
    role = user.role.value if hasattr(user.role, "value") else user.role
    return UserMeResponse(id=user.id, username=user.username, email=user.email, role=role)

@router.get("/admin-only")
def admin_only(user: User = Depends(require_roles("ADMIN"))):
    role = user.role.value if hasattr(user.role, "value") else user.role
    return {"ok": True, "email": user.email, "role": role}


@router.post(
    "/fcm-token",
    responses={
        200: {
            "description": "Token FCM registrado",
            "content": {"application/json": {"example": {"ok": True}}},
        }
    },
)
def register_fcm_token(
    payload: FcmTokenUpsert,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    fcm_token_repo.upsert_token(
        db,
        user_id=user.id,
        token=payload.token,
        device_id=payload.device_id,
        platform=payload.platform,
    )
    return {"ok": True}
