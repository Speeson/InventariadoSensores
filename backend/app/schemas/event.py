from pydantic import BaseModel, Field
from typing import Literal, Optional

class EventCreate(BaseModel):
    type: Literal["IN", "OUT"]
    product_id: int
    delta: int = Field(gt=0)
    source: Optional[str] = "sensor_simulado"

class EventOut(BaseModel):
    id: int
    type: Literal["IN", "OUT"]
    product_id: int
    delta: int
    source: str
    created_at: str