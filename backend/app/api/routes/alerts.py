from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.db.deps import get_db
from app.models.enums import AlertStatus, UserRole
from app.models.user import User
from app.repositories import alert_repo
from app.schemas.alert import AlertResponse


class AlertListResponse(BaseModel):
    items: list[AlertResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/alerts", tags=["alerts"])


@router.get("/", response_model=AlertListResponse, dependencies=[Depends(get_current_user)])
def list_alerts(
    db: Session = Depends(get_db),
    alert_status: AlertStatus | None = Query(None, alias="status"),
    product_id: int | None = Query(None),
    location: str | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    if date_from and date_to and date_from > date_to:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="date_from no puede ser mayor que date_to")

    items, total = alert_repo.list_alerts(
        db,
        status=alert_status,
        product_id=product_id,
        location=location,
        date_from=date_from,
        date_to=date_to,
        limit=limit,
        offset=offset,
    )
    return AlertListResponse(items=items, total=total, limit=limit, offset=offset)


@router.post(
    "/{alert_id}/ack",
    response_model=AlertResponse,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def ack_alert(
    alert_id: int,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    alert = alert_repo.get(db, alert_id)
    if not alert:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Alerta no encontrada")

    if alert.alert_status == AlertStatus.RESOLVED:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="La alerta ya esta resuelta")

    return alert_repo.ack_alert(db, alert, user_id=user.id)
