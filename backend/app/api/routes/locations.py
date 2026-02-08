from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.cache.redis_cache import cache_get, cache_set, make_key
from app.db.deps import get_db
from app.repositories import location_repo
from app.schemas.location import LocationResponse


class LocationListResponse(BaseModel):
    items: list[LocationResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/locations", tags=["locations"])


@router.get("/", response_model=LocationListResponse)
def list_locations(
    db: Session = Depends(get_db),
    user = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    cache_key = make_key(
        "locations:list",
        user.id if user else None,
        {"limit": limit, "offset": offset},
    )
    cached = cache_get(cache_key)
    if cached is not None:
        return cached
    items, total = location_repo.list_locations(db, limit=limit, offset=offset)
    payload = LocationListResponse(items=items, total=total, limit=limit, offset=offset)
    cache_set(cache_key, payload, ttl_seconds=3600)
    return payload
