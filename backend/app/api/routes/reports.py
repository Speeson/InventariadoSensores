from datetime import datetime, date, time
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from app.api.deps import require_roles
from app.cache.redis_cache import cache_get, cache_set, make_key
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import report_repo
from app.schemas.report import (
    TopConsumedResponse, TopConsumedItem,
    TurnoverResponse, TurnoverItem,
)

router = APIRouter(prefix="/reports", tags=["reports"])

@router.get(
    "/top-consumed",
    response_model=TopConsumedResponse,
)
def top_consumed(
    db: Session = Depends(get_db),
    user = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
    date_from: date | None = Query(None, description="YYYY-MM-DD"),
    date_to: date | None = Query(None, description="YYYY-MM-DD"),
    location: str | None = Query(None),
    order_dir: str | None = Query("desc"),
    limit: int = Query(10, ge=1, le=100),
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

    cache_key = make_key(
        "reports:top-consumed",
        user.id if user else None,
        {
            "date_from": date_from.isoformat() if date_from else None,
            "date_to": date_to.isoformat() if date_to else None,
            "location": location,
            "order_dir": order_dir,
            "limit": limit,
            "offset": offset,
        },
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    dt_from = datetime.combine(date_from, time.min) if date_from else None
    dt_to = datetime.combine(date_to, time.max) if date_to else None

    rows, total = report_repo.list_top_consumed(
        db,
        date_from=dt_from,
        date_to=dt_to,
        location=location,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    items = [
        TopConsumedItem(product_id=r.product_id, sku=r.sku, name=r.name, total_out=r.total_out)
        for r in rows
    ]
    payload = TopConsumedResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload





@router.get(
    "/turnover",
    response_model=TurnoverResponse,
)
def turnover_report(
    db: Session = Depends(get_db),
    user = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
    date_from: date | None = Query(None, description="YYYY-MM-DD"),
    date_to: date | None = Query(None, description="YYYY-MM-DD"),
    location: str | None = Query(None),
    order_dir: str | None = Query("desc"),
    limit: int = Query(10, ge=1, le=100),
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

    cache_key = make_key(
        "reports:turnover",
        user.id if user else None,
        {
            "date_from": date_from.isoformat() if date_from else None,
            "date_to": date_to.isoformat() if date_to else None,
            "location": location,
            "order_dir": order_dir,
            "limit": limit,
            "offset": offset,
        },
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    dt_from = datetime.combine(date_from, time.min) if date_from else None
    dt_to = datetime.combine(date_to, time.max) if date_to else None

    rows, total, loc_code = report_repo.list_turnover(
        db,
        date_from=dt_from,
        date_to=dt_to,
        location=location,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )

    items = [
        TurnoverItem(
            product_id=r.product_id,
            sku=r.sku,
            name=r.name,
            turnover=r.turnover,
            outs=r.outs,
            stock_initial=r.stock_initial,
            stock_final=r.stock_final,
            stock_average=r.stock_average,
            location=loc_code,
        )
        for r in rows
    ]
    payload = TurnoverResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload
