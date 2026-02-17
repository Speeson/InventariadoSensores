from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import EventType


class EventCreate(BaseModel):
    event_type: EventType = Field(examples=["SENSOR_IN"])
    product_id: int = Field(examples=[1])
    delta: int = Field(..., gt=0, examples=[3])
    source: str = Field(default="sensor_simulado", examples=["sensor_simulado"])
    location: str = Field("default", min_length=1, max_length=100, examples=["ALM-CENTRAL"])
    idempotency_key: str = Field(..., min_length=1, max_length=100, examples=["evt-001"])
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "event_type": "SENSOR_IN",
                "product_id": 1,
                "delta": 3,
                "source": "sensor_simulado",
                "location": "ALM-CENTRAL",
                "idempotency_key": "evt-001",
            }
        }
    )


class EventResponse(BaseModel):
    id: int
    event_type: EventType
    product_id: int
    delta: int
    source: str
    processed: bool
    event_status: str
    created_at: datetime
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 55,
                "event_type": "SENSOR_IN",
                "product_id": 1,
                "delta": 3,
                "source": "sensor_simulado",
                "processed": False,
                "event_status": "PENDING",
                "created_at": "2026-02-17T10:30:00Z",
            }
        },
    )
