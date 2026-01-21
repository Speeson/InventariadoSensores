from fastapi import APIRouter, Depends

from app.api.deps import get_current_user, require_roles
from app.models.user import User
from app.schemas.user import UserMeResponse


router = APIRouter(prefix="/users", tags=["users"])

@router.get("/me", response_model=UserMeResponse)
def me(user: User = Depends(get_current_user)):
    role = user.role.value if hasattr(user.role, "value") else user.role
    return UserMeResponse(id=user.id, email=user.email, role=role)

@router.get("/admin-only")
def admin_only(user: User = Depends(require_roles("ADMIN"))):
    role = user.role.value if hasattr(user.role, "value") else user.role
    return {"ok": True, "email": user.email, "role": role}
