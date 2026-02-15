from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator

class ThresholdBase(BaseModel):
    product_id: int
    location: str | None = Field(None, min_length=1, max_length=100)
    min_quantity: int = Field(..., ge=0)

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
    pass

class ThresholdUpdate(BaseModel):
    location: str | None = Field(None, min_length=1, max_length=100)
    min_quantity: int | None = Field(None, ge=0)

    
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
    model_config = ConfigDict(from_attributes=True)
