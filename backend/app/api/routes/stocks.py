from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import stock_repo, product_repo
from app.schemas.stock import StockResponse, StockUpdate, StockCreate


class StockListResponse(BaseModel):
    items: list[StockResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/stocks", tags=["stocks"])


@router.get("/", response_model=StockListResponse, dependencies=[Depends(get_current_user)])
def list_stocks(
    db: Session = Depends(get_db),
    product_id: int | None = Query(None),
    location: str | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = stock_repo.list_stocks(
        db,
        product_id=product_id,
        location=location,
        limit=limit,
        offset=offset,
    )
    return StockListResponse(items=items, total=total, limit=limit, offset=offset)


@router.get("/{stock_id}", response_model=StockResponse, dependencies=[Depends(get_current_user)])
def get_stock(stock_id: int, db: Session = Depends(get_db)):
    stock = stock_repo.get(db, stock_id)
    if not stock:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Stock no encontrado")
    return stock


@router.post(
    "/",
    response_model=StockResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def create_stock(payload: StockCreate, db: Session = Depends(get_db)):
    product = product_repo.get(db, payload.product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Producto no existe")

    existing = stock_repo.get_by_product_and_location(db, payload.product_id, payload.location)
    if existing:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ya existe stock para esta ubicación")
    stock = stock_repo.create_stock(
        db,
        product_id=payload.product_id,
        location=payload.location,
        quantity=payload.quantity,
    )
    return stock


@router.patch(
    "/{stock_id}",
    response_model=StockResponse,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def update_stock(stock_id: int, payload: StockUpdate, db: Session = Depends(get_db)):
    stock = stock_repo.get(db, stock_id)
    if not stock:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Stock no encontrado")

    new_quantity = payload.quantity if payload.quantity is not None else stock.quantity
    new_location = payload.location if payload.location is not None else stock.location

    if payload.location and new_location != stock.location:
        duplicate = stock_repo.get_by_product_and_location(db, stock.product_id, payload.location)
        if duplicate and duplicate.id != stock.id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ya existe stock para esta ubicación")

    stock.location = new_location
    stock.quantity = new_quantity
    db.add(stock)
    db.commit()
    db.refresh(stock)
    return stock
