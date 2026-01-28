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
    movement_type: MovementType
    movement_source: Source
    user_id: int
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
