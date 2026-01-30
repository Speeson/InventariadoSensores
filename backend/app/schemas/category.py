from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict, field_validator

class CategoryBase(BaseModel):
    name: str = Field(..., min_length=1, max_length=50)

    @field_validator("name")
    @classmethod
    def normalize_non_empty(cls, v: str) -> str:
        normalized = v.strip()
        if not normalized:
            raise ValueError("El nombre no puede estar vacío")
        return normalized

class CategoryCreate(CategoryBase):
    pass

class CategoryUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=50)

    @field_validator("name")
    @classmethod
    def normalize_non_empty(cls, v: str | None) -> str | None:
        if v is None:
            return v
        normalized = v.strip()
        if not normalized:
            raise ValueError("El nombre no puede estar vacío")
        return normalized

class CategoryResponse(CategoryBase):
    id: int
    created_at: datetime
    updated_at: datetime | None
    model_config = ConfigDict(from_attributes=True)
