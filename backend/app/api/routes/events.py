from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.api.deps import get_current_user
from app.db.deps import get_db
from app.models.enums import EventType, MovementSource
from app.models.user import User
from app.repositories import event_repo, product_repo
from app.schemas.event import EventCreate, EventResponse
from app.services import inventory_service


class EventListResponse(BaseModel):
    items: list[EventResponse]
    total: int
    limit: int
    offset: int


router = APIRouter(prefix="/events", tags=["events"])


@router.get("/", response_model=EventListResponse, dependencies=[Depends(get_current_user)])
def list_events(
    db: Session = Depends(get_db),
    event_type: EventType | None = Query(None),
    product_id: int | None = Query(None),
    processed: bool | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
):
    items, total = event_repo.list_events(
        db,
        event_type=event_type,
        product_id=product_id,
        processed=processed,
        limit=limit,
        offset=offset,
    )
    return EventListResponse(items=items, total=total, limit=limit, offset=offset)


@router.post(
    "/",
    response_model=EventResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_event(
    payload: EventCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    product = product_repo.get(db, payload.product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")

    try:
        if payload.event_type == EventType.SENSOR_IN:
            inventory_service.increase_stock(
                db,
                product_id=payload.product_id,
                quantity=payload.delta,
                user_id=user.id,
                location=payload.location,
                source=MovementSource.MANUAL,
            )
        else:
            inventory_service.decrease_stock(
                db,
                product_id=payload.product_id,
                quantity=payload.delta,
                user_id=user.id,
                location=payload.location,
                source=MovementSource.MANUAL,
            )
    except inventory_service.InventoryError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc))

    event = event_repo.create_event(
        db,
        event_type=payload.event_type,
        product_id=payload.product_id,
        delta=payload.delta,
        source=payload.source,
        processed=True,
    )
    return event
