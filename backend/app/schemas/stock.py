from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict

class StockBase(BaseModel):
    product_id: int
    location: str = Field(..., min_length=1, max_length=100)
    # Permite 0 para no fallar en respuestas cuando hay stock en cero.
    quantity: int = Field(..., ge=0)

class StockCreate(StockBase):
    pass

class StockUpdate(BaseModel):
    location: str | None = Field(None, min_length=1, max_length=100)
    quantity: int | None = Field(None, ge=0)

class StockResponse(StockBase):
    id: int
    created_at: datetime
    updated_at: datetime
    model_config = ConfigDict(from_attributes=True)
