from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.deps import get_db
from app.repositories import location_repo
from app.schemas.location import LocationResponse


class LocationListResponse(BaseModel):
    items: list[LocationResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/locations", tags=["locations"])


@router.get("/", response_model=LocationListResponse, dependencies=[Depends(get_current_user)])
def list_locations(
    db: Session = Depends(get_db),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    items, total = location_repo.list_locations(db, limit=limit, offset=offset)
    return LocationListResponse(items=items, total=total, limit=limit, offset=offset)
