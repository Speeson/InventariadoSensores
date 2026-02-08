from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.cache.redis_cache import cache_get, cache_set, cache_invalidate_prefix, make_key
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import category_repo
from app.schemas.category import CategoryCreate, CategoryUpdate, CategoryResponse

class CategoryListResponse(BaseModel):
    items: list[CategoryResponse]
    total: int
    limit: int
    offset: int

router = APIRouter(prefix="/categories", tags=["categories"])

@router.get("/", response_model=CategoryListResponse, dependencies=[Depends(get_current_user)])
def list_categories(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
    name: str | None = Query(None),
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
        "categories:list",
        user.id if user else None,
        {"name": name, "order_by": order_by, "order_dir": order_dir, "limit": limit, "offset": offset},
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached

    items, total = category_repo.list_categories(
        db,
        name=name,
        order_dir=order_dir,
        limit=limit,
        offset=offset,
    )
    payload = CategoryListResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=3600)
    return payload

@router.get("/{category_id}", response_model=CategoryResponse, dependencies=[Depends(get_current_user)])
def get_category(category_id: int, db: Session = Depends(get_db), user=Depends(get_current_user)):
    cache_key = make_key("categories:detail", user.id if user else None, {"id": category_id})
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    category = category_repo.get(db, category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Categoría no encontrada")
    cache_set(cache_key, category, ttl_seconds=3600)
    return category

@router.post(
    "/",
    response_model=CategoryResponse,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def create_category(payload: CategoryCreate, db: Session = Depends(get_db)):
    if category_repo.get_by_name(db, payload.name):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="La categoría ya existe")
    category = category_repo.create_category(db, name=payload.name)
    cache_invalidate_prefix("categories:list")
    cache_invalidate_prefix("categories:detail")
    return category

@router.patch(
    "/{category_id}",
    response_model=CategoryResponse,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def update_category(category_id: int, payload: CategoryUpdate, db: Session = Depends(get_db)):
    category = category_repo.get(db, category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Categoría no encontrada")
    if payload.name:
        existing = category_repo.get_by_name(db, payload.name)
        if existing and existing.id != category_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="La categoría ya existe")
        category = category_repo.update_category(db, category, name=payload.name)
    cache_invalidate_prefix("categories:list")
    cache_invalidate_prefix("categories:detail")
    return category

@router.delete(
    "/{category_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))],
)
def delete_category(category_id: int, db: Session = Depends(get_db)):
    category = category_repo.get(db, category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Categoría no encontrada")
    category_repo.delete_category(db, category)
    cache_invalidate_prefix("categories:list")
    cache_invalidate_prefix("categories:detail")
    return None
