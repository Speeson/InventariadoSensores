from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.api.deps import require_roles
from app.db.deps import get_db
from app.models.enums import ActionType, Entity, UserRole
from app.repositories import audit_log_repo
from app.schemas.audit_log import AuditLogListResponse


router = APIRouter(prefix="/audit", tags=["audit"])


@router.get(
    "/",
    response_model=AuditLogListResponse,
    dependencies=[Depends(require_roles(UserRole.ADMIN.value))],
)
def list_audit_logs(
    db: Session = Depends(get_db),
    entity: Entity | None = Query(None),
    action: ActionType | None = Query(None),
    user_id: int | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    order_dir: str | None = Query("desc"),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    if date_from and date_to and date_from > date_to:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="date_from no puede ser mayor que date_to",
        )
    if order_dir not in {"asc", "desc"}:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="order_dir debe ser 'asc' o 'desc'",
        )

    items, total = audit_log_repo.list_logs(
        db,
        entity=entity,
        action=action,
        user_id=user_id,
        date_from=date_from,
        date_to=date_to,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    return AuditLogListResponse(items=items, total=total, limit=limit, offset=offset)
