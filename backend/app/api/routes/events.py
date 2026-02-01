from fastapi import APIRouter, Depends, HTTPException, Query, status, Response
from pydantic import BaseModel
from sqlalchemy.orm import Session
from app.tasks import process_event
from app.api.deps import get_current_user
from app.db.deps import get_db
from app.models.enums import EventType
from app.models.user import User
from app.repositories import event_repo, product_repo, location_repo
from app.schemas.event import EventCreate, EventResponse
from sqlalchemy.exc import IntegrityError


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
    response: Response,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    # 0) Validar producto
    product = product_repo.get(db, payload.product_id)
    if not product:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Producto no encontrado")

    # 1) Validar location si en vuestro flujo es obligatorio
    if not payload.location:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="location es obligatorio")

    # 2) Idempotencia "rapida": si ya existe esa key, devolver el mismo evento
    # (Esto evita duplicados cuando el cliente reintenta el POST)
    existing = event_repo.get_by_idempotency_key(db, payload.idempotency_key)
    if existing:
        response.status_code = status.HTTP_200_OK
        return existing

    # 3) Asegurar location (crear si no existe)
    location_obj = location_repo.get_or_create(db, payload.location)

    # 4) Crear evento PENDING y manejar carrera por key duplicada
    try:
        event = event_repo.create_event(
            db,
            event_type=payload.event_type,
            product_id=payload.product_id,
            delta=payload.delta,
            source=payload.source,
            location_id=location_obj.id,
            processed=False,
            idempotency_key=payload.idempotency_key,
        )
    except IntegrityError:
        # Si entraron dos peticiones a la vez con la misma key,
        # una puede fallar por unique constraint. Recuperamos el existente.
        db.rollback()
        existing = event_repo.get_by_idempotency_key(db, payload.idempotency_key)
        if existing:
            response.status_code = status.HTTP_200_OK
            return existing
        raise

    # 5) Encolar para que lo procese el worker (S2)
    try:
        process_event.delay(event.id)
    except Exception as exc:
        # El evento queda PENDING. El enqueue fallo (infra).
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"No se pudo encolar el evento: {exc}",
        )

    return event
