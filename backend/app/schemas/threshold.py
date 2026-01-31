from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator

class ThresholdBase(BaseModel):
    product_id: int
    location: str | None = Field(None, min_length=1, max_length=100)
    min_quantity: int = Field(..., ge=0)

    @field_validator("location")
    @classmethod
    def normalize_location(cls, v):
        return v.strip() if v is not None else v

class ThresholdCreate(ThresholdBase):
    pass

class ThresholdUpdate(BaseModel):
    location: str | None = Field(None, min_length=1, max_length=100)
    min_quantity: int | None = Field(None, ge=0)

    @field_validator("location")
    @classmethod
    def normalize_location(cls, v):
        return v.strip() if v is not None else v

class ThresholdResponse(ThresholdBase):
    id: int
    created_at: datetime
    updated_at: datetime | None
    model_config = ConfigDict(from_attributes=True)
