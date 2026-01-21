from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict
from app.models.enums import MovementType, MovementSource

class MovementCreate(BaseModel):
    product_id: int
    quantity: int = Field(..., gt=0)
    movement_type: MovementType
    movement_source: MovementSource
    # Si más adelante quieres una nota/motivo, añade aquí un campo opcional.

class MovementResponse(BaseModel):
    id: int
    product_id: int
    quantity: int
    movement_type: MovementType
    movement_source: MovementSource
    user_id: int
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
