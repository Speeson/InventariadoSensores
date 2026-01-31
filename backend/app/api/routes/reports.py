from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from app.api.deps import require_roles
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import report_repo
from app.schemas.report import TopConsumedResponse, TopConsumedItem

router = APIRouter(prefix="/reports", tags=["reports"])

@router.get("/top-consumed", response_model=TopConsumedResponse,
            dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def top_consumed(
    db: Session = Depends(get_db),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    limit: int = Query(10, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    if date_from and date_to and date_from > date_to:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="date_from no puede ser mayor que date_to")
    rows, total = report_repo.list_top_consumed(
        db, date_from=date_from, date_to=date_to, limit=limit, offset=offset
    )
    items = [TopConsumedItem(product_id=r.product_id, sku=r.sku, name=r.name, total_out=r.total_out) for r in rows]
    return TopConsumedResponse(items=items, total=total, limit=limit, offset=offset)
