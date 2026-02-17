from datetime import datetime, timezone
from pydantic import BaseModel, Field, ConfigDict, field_serializer, field_validator

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

    @field_serializer("created_at", "updated_at", when_used="json")
    def serialize_datetime(self, value: datetime | None):
        if value is None:
            return None
        dt = value if value.tzinfo else value.replace(tzinfo=timezone.utc)
        return dt.isoformat()
