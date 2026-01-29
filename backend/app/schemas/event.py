from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import EventType


class EventCreate(BaseModel):
    event_type: EventType
    product_id: int
    delta: int = Field(..., gt=0)
    source: str = "sensor_simulado"
    location: str = Field("default", min_length=1, max_length=100)
    idempotency_key: str | None = None


class EventResponse(BaseModel):
    id: int
    event_type: EventType
    product_id: int
    delta: int
    source: str
    processed: bool
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
