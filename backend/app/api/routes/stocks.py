from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.cache.redis_cache import cache_get, cache_set, cache_invalidate_prefix, make_key
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import stock_repo, product_repo
from app.schemas.stock import StockResponse, StockUpdate, StockCreate
from app.services import inventory_service


class StockListResponse(BaseModel):
    items: list[StockResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/stocks", tags=["stocks"])


@router.get("/", response_model=StockListResponse, dependencies=[Depends(get_current_user)])
def list_stocks(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
    product_id: int | None = Query(None),
    location: str | None = Query(None),
    order_by: str | None = Query("id"),
    order_dir: str | None = Query("asc"),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    if order_by != "id":
        raise HTTPException(status_code=400, detail="order_by debe ser 'id'")
    if order_dir not in {"asc", "desc"}:
        raise HTTPException(status_code=400, detail="order_dir debe ser 'asc' o 'desc'")
    cache_key = make_key(
        "stocks:list",
        user.id if user else None,
        {
            "product_id": product_id,
            "location": location,
            "order_by": order_by,
            "order_dir": order_dir,
            "limit": limit,
            "offset": offset,
        },
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    items, total = stock_repo.list_stocks(
        db,
        product_id=product_id,
        location=location,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    payload = StockListResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload


@router.get("/{stock_id}", response_model=StockResponse, dependencies=[Depends(get_current_user)])
def get_stock(stock_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    cache_key = make_key("stocks:detail", user.id if user else None, {"id": stock_id})
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    stock = stock_repo.get(db, stock_id)
    if not stock:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Stock no encontrado")
    payload = StockResponse(
        id=stock.id,
        product_id=stock.product_id,
        location=stock.location or "N/D",
        quantity=stock.quantity,
        created_at=stock.created_at,
        updated_at=stock.updated_at,
    )
    cache_set(cache_key, payload, ttl_seconds=300)
    return payload


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
    cache_invalidate_prefix("stocks:list")
    cache_invalidate_prefix("stocks:detail")
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

    old_quantity = stock.quantity
    new_quantity = payload.quantity if payload.quantity is not None else stock.quantity
    new_location = payload.location if payload.location is not None else stock.location

    if payload.location and new_location != stock.location:
        duplicate = stock_repo.get_by_product_and_location(db, stock.product_id, payload.location)
        if duplicate and duplicate.id != stock.id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ya existe stock para esta ubicación")

    if payload.location and new_location != stock.location:
        stock = stock_repo.update_stock_location(db, stock, new_location)
    if payload.quantity is not None:
        stock = stock_repo.update_stock_quantity(db, stock, new_quantity)
        inventory_service.maybe_create_alerts_for_stock_update(db, stock=stock, old_quantity=old_quantity)
    cache_invalidate_prefix("stocks:list")
    cache_invalidate_prefix("stocks:detail")
    return stock
