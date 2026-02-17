from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict

class StockBase(BaseModel):
    product_id: int = Field(examples=[1])
    location: str = Field(..., min_length=1, max_length=100, examples=["ALM-CENTRAL"])
    # Permite 0 para no fallar en respuestas cuando hay stock en cero.
    quantity: int = Field(..., ge=0, examples=[10])

class StockCreate(StockBase):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "product_id": 1,
                "location": "ALM-CENTRAL",
                "quantity": 10,
            }
        }
    )

class StockUpdate(BaseModel):
    location: str | None = Field(None, min_length=1, max_length=100)
    quantity: int | None = Field(None, ge=0)
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "location": "ALM-NORTE",
                "quantity": 25,
            }
        }
    )

class StockResponse(StockBase):
    id: int
    created_at: datetime
    updated_at: datetime
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 7,
                "product_id": 1,
                "location": "ALM-CENTRAL",
                "quantity": 10,
                "created_at": "2026-02-17T10:00:00Z",
                "updated_at": "2026-02-17T10:10:00Z",
            }
        },
    )
