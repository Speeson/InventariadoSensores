from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator

class ThresholdBase(BaseModel):
    product_id: int = Field(examples=[1])
    location: str | None = Field(None, min_length=1, max_length=100, examples=["ALM-CENTRAL"])
    min_quantity: int = Field(..., ge=0, examples=[5])

    @field_validator("location")
    @classmethod
    def normalize_location(cls, v):
        if v is None:
            return v
        normalized = v.strip()
        if not normalized:
            raise ValueError("La ubicación no puede estar vacía")
        return normalized

class ThresholdCreate(ThresholdBase):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "product_id": 1,
                "location": "ALM-CENTRAL",
                "min_quantity": 5,
            }
        }
    )

class ThresholdUpdate(BaseModel):
    location: str | None = Field(None, min_length=1, max_length=100)
    min_quantity: int | None = Field(None, ge=0)
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "location": "ALM-NORTE",
                "min_quantity": 7,
            }
        }
    )

    
    @field_validator("location")
    @classmethod
    def normalize_location(cls, v):
        if v is None:
            return v
        normalized = v.strip()
        if not normalized:
            raise ValueError("La ubicación no puede estar vacía")
        return normalized

class ThresholdResponse(BaseModel):
    id: int
    product_id: int
    location_id: int | None
    location: str | None
    min_quantity: int
    created_at: datetime
    updated_at: datetime | None
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 3,
                "product_id": 1,
                "location_id": 1,
                "location": "ALM-CENTRAL",
                "min_quantity": 5,
                "created_at": "2026-02-17T10:00:00Z",
                "updated_at": "2026-02-17T10:20:00Z",
            }
        },
    )
