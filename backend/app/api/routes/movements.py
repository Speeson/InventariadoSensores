from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from app.api.deps import require_roles
from app.db.deps import get_db
from app.models.enums import MovementSource, MovementType, UserRole
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
    movement_source: MovementSource


class MovementAdjustOperation(BaseModel):
    product_id: int
    delta: int = Field(..., ne=0)
    location: str = Field(..., min_length=1, max_length=100)
    movement_source: MovementSource


class MovementWithStockResponse(BaseModel):
    stock: StockResponse
    movement: MovementResponse


router = APIRouter(prefix="/movements", tags=["movements"])


@router.get("/", response_model=MovementListResponse, dependencies=[Depends(get_current_user)])
def list_movements(
    db: Session = Depends(get_db),
    product_id: int | None = Query(None),
    movement_type: MovementType | None = Query(None),
    movement_source: MovementSource | None = Query(None),
    user_id: int | None = Query(None),
    date_from: datetime | None = Query(None),
    date_to: datetime | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = movement_repo.list_movements(
        db,
        product_id=product_id,
        movement_type=movement_type,
        movement_source=movement_source,
        user_id=user_id,
        date_from=date_from,
        date_to=date_to,
        limit=limit,
        offset=offset,
    )
    return MovementListResponse(items=items, total=total, limit=limit, offset=offset)


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
        return MovementWithStockResponse(stock=stock, movement=movement)
    except inventory_service.InventoryError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
