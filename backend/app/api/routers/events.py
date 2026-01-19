from fastapi import APIRouter
from app.schemas.event import EventCreate
from app.services.event_service import apply_event

router = APIRouter(prefix="/events", tags=["events"])

# Repo global (en memoria). Luego lo cambiáis por DB repo.
from app.repositories.memory_repo import MemoryRepo
repo = MemoryRepo()

@router.post("", status_code=201)
def create_event(payload: EventCreate):
    return apply_event(repo, payload)

@router.get("")
def list_events(limit: int = 50):
    return repo.list_events(limit=limit)