from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.cache.redis_cache import cache_get, cache_set, cache_invalidate_prefix, make_key
from app.db.deps import get_db
from app.models.enums import Source, MovementType, UserRole
from app.schemas.movement import MovementResponse
from app.schemas.stock import StockResponse
from app.services import inventory_service
from app.repositories import movement_repo
from app.models.user import User


class MovementListResponse(BaseModel):
    items: list[MovementResponse]
    total: int
    limit: int
    offset: int


class MovementOperation(BaseModel):
    product_id: int
    quantity: int = Field(..., gt=0)
    location: str = Field(..., min_length=1, max_length=100)
    movement_source: Source


class MovementAdjustOperation(BaseModel):
    product_id: int
    delta: int = Field(..., ne=0)
    location: str = Field(..., min_length=1, max_length=100)
    movement_source: Source


class MovementTransferOperation(BaseModel):
    product_id: int
    quantity: int = Field(..., gt=0)
    from_location: str = Field(..., min_length=1, max_length=100)
    to_location: str = Field(..., min_length=1, max_length=100)
    movement_source: Source


class MovementWithStockResponse(BaseModel):
    stock: StockResponse
    movement: MovementResponse


class MovementTransferResponse(BaseModel):
    from_stock: StockResponse
    to_stock: StockResponse
    out_movement: MovementResponse
    in_movement: MovementResponse


router = APIRouter(prefix="/movements", tags=["movements"])


@router.get("/", response_model=MovementListResponse)
def list_movements(
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    product_id: int | None = Query(None),
    movement_type: MovementType | None = Query(None),
    movement_source: Source | None = Query(None),
    user_id: int | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    order_by: str | None = Query("created_at"),
    order_dir: str | None = Query("desc"),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    if date_from and date_to and date_from > date_to:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="date_from no puede ser mayor que date_to")
    if order_by != "created_at":
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="order_by debe ser 'created_at'")
    if order_dir not in {"asc", "desc"}:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="order_dir debe ser 'asc' o 'desc'")

    cache_key = make_key(
        "movements:list",
        user.id,
        {
            "product_id": product_id,
            "movement_type": movement_type,
            "movement_source": movement_source,
            "user_id": user_id,
            "date_from": date_from.isoformat() if date_from else None,
            "date_to": date_to.isoformat() if date_to else None,
            "order_by": order_by,
            "order_dir": order_dir,
            "limit": limit,
            "offset": offset,
        },
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    items, total = movement_repo.list_movements(
        db,
        product_id=product_id,
        movement_type=movement_type,
        movement_source=movement_source,
        user_id=user_id,
        date_from=date_from,
        date_to=date_to,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    payload = MovementListResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload


@router.post(
    "/in",
    response_model=MovementWithStockResponse,
    status_code=status.HTTP_201_CREATED,
)
def movement_in(
    payload: MovementOperation,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    try:
        stock, movement = inventory_service.increase_stock(
            db,
            product_id=payload.product_id,
            quantity=payload.quantity,
            user_id=user.id,
            location=payload.location,
            source=payload.movement_source,
        )
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")
        return MovementWithStockResponse(stock=stock, movement=movement)
    except inventory_service.InventoryError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))


@router.post(
    "/out",
    response_model=MovementWithStockResponse,
    status_code=status.HTTP_201_CREATED,
)
def movement_out(
    payload: MovementOperation,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    try:
        stock, movement = inventory_service.decrease_stock(
            db,
            product_id=payload.product_id,
            quantity=payload.quantity,
            user_id=user.id,
            location=payload.location,
            source=payload.movement_source,
        )
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")
        return MovementWithStockResponse(stock=stock, movement=movement)
    except inventory_service.InventoryError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))


@router.post(
    "/adjust",
    response_model=MovementWithStockResponse,
    status_code=status.HTTP_201_CREATED,
)
def movement_adjust(
    payload: MovementAdjustOperation,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    try:
        stock, movement = inventory_service.adjust_stock(
            db,
            product_id=payload.product_id,
            quantity=payload.delta,
            user_id=user.id,
            location=payload.location,
            source=payload.movement_source,
        )
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")
        return MovementWithStockResponse(stock=stock, movement=movement)
    except inventory_service.InventoryError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))


@router.post(
    "/transfer",
    response_model=MovementTransferResponse,
    status_code=status.HTTP_201_CREATED,
)
def movement_transfer(
    payload: MovementTransferOperation,
    db: Session = Depends(get_db),
    user: User = Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value)),
):
    try:
        from_stock, to_stock, out_movement, in_movement = inventory_service.transfer_stock(
            db,
            product_id=payload.product_id,
            quantity=payload.quantity,
            user_id=user.id,
            from_location=payload.from_location,
            to_location=payload.to_location,
            source=payload.movement_source,
        )
        cache_invalidate_prefix("movements:list")
        cache_invalidate_prefix("stocks:list")
        cache_invalidate_prefix("stocks:detail")
        cache_invalidate_prefix("reports:top-consumed")
        cache_invalidate_prefix("reports:turnover")
        return MovementTransferResponse(
            from_stock=from_stock,
            to_stock=to_stock,
            out_movement=out_movement,
            in_movement=in_movement,
        )
    except inventory_service.InventoryError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
