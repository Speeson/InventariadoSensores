from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
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
    name: str | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = category_repo.list_categories(db, name=name, limit=limit, offset=offset)
    return CategoryListResponse(items=items, total=total, limit=limit, offset=offset)

@router.get("/{category_id}", response_model=CategoryResponse, dependencies=[Depends(get_current_user)])
def get_category(category_id: int, db: Session = Depends(get_db)):
    category = category_repo.get(db, category_id)
    if not category:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Categoría no encontrada")
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
    return category_repo.create_category(db, name=payload.name)

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
    return None
