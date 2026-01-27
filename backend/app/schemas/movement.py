from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import MovementSource, MovementType


class MovementCreate(BaseModel):
    product_id: int
    quantity: int = Field(..., gt=0)
    movement_type: MovementType
    movement_source: MovementSource


class MovementResponse(BaseModel):
    id: int
    product_id: int
    quantity: int
    movement_type: MovementType
    movement_source: MovementSource
    user_id: int
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
