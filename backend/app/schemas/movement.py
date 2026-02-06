from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import Source, MovementType


class MovementCreate(BaseModel):
    product_id: int
    quantity: int = Field(..., gt=0)
    movement_type: MovementType
    movement_source: Source


class MovementResponse(BaseModel):
    id: int
    product_id: int
    quantity: int
    delta: int
    movement_type: MovementType
    movement_source: Source
    transfer_id: str | None = None
    user_id: int | None
    location_id: int | None
    location: str | None  # devuelto a partir de la relación Location
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
