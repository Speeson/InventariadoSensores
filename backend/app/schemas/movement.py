from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import Source, MovementType


class MovementCreate(BaseModel):
    product_id: int = Field(examples=[1])
    quantity: int = Field(..., gt=0, examples=[5])
    movement_type: MovementType = Field(examples=["IN"])
    movement_source: Source = Field(examples=["SCAN"])
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "product_id": 1,
                "quantity": 5,
                "movement_type": "IN",
                "movement_source": "SCAN",
            }
        }
    )


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
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 100,
                "product_id": 1,
                "quantity": 5,
                "delta": 5,
                "movement_type": "IN",
                "movement_source": "SCAN",
                "transfer_id": None,
                "user_id": 2,
                "location_id": 1,
                "location": "ALM-CENTRAL",
                "created_at": "2026-02-17T10:20:00Z",
            }
        },
    )
