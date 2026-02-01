from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user, require_roles
from app.db.deps import get_db
from app.models.enums import UserRole
from app.repositories import threshold_repo, product_repo
from app.schemas.threshold import ThresholdCreate, ThresholdUpdate, ThresholdResponse

class ThresholdListResponse(BaseModel):
    items: list[ThresholdResponse]
    total: int
    limit: int
    offset: int

router = APIRouter(prefix="/thresholds", tags=["thresholds"])

@router.get("/", response_model=ThresholdListResponse, dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def list_thresholds(
    db: Session = Depends(get_db),
    product_id: int | None = Query(None),
    location: str | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = threshold_repo.list_thresholds(db, product_id=product_id, location=location, limit=limit, offset=offset)
    return ThresholdListResponse(items=items, total=total, limit=limit, offset=offset)

@router.get("/{threshold_id}", response_model=ThresholdResponse, dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def get_threshold(threshold_id: int, db: Session = Depends(get_db)):
    threshold = threshold_repo.get(db, threshold_id)
    if not threshold:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Threshold no encontrado")
    return threshold

@router.post("/", response_model=ThresholdResponse, status_code=status.HTTP_201_CREATED, dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def create_threshold(payload: ThresholdCreate, db: Session = Depends(get_db)):
    if not product_repo.get(db, payload.product_id):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Producto no existe")
    dup =  threshold_repo.get_by_product_and_location(db, payload.product_id, payload.location)
    if dup:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ya existe un threshold para esa ubicación")
    return threshold_repo.create_threshold(db, product_id=payload.product_id, location=payload.location, min_quantity=payload.min_quantity)

@router.patch("/{threshold_id}", response_model=ThresholdResponse, dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def update_threshold(threshold_id: int, payload: ThresholdUpdate, db: Session = Depends(get_db)):
    threshold = threshold_repo.get(db, threshold_id)
    if not threshold:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Threshold no encontrado")
    if payload.location is not None:
        dup =  threshold_repo.get_by_product_and_location(db, threshold.product_id, payload.location)
        if dup and dup.id != threshold.id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Ya existe un threshold para esa ubicación")
    return threshold_repo.update_threshold(db, threshold, location=payload.location, min_quantity=payload.min_quantity)

@router.delete("/{threshold_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[Depends(require_roles(UserRole.MANAGER.value, UserRole.ADMIN.value))])
def delete_threshold(threshold_id: int, db: Session = Depends(get_db)):
    threshold = threshold_repo.get(db, threshold_id)
    if not threshold:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Threshold no encontrado")
    threshold_repo.delete_threshold(db, threshold)
    return None
