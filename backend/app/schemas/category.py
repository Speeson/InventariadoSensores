from datetime import datetime, timezone
from pydantic import BaseModel, Field, ConfigDict, field_serializer, field_validator

class CategoryBase(BaseModel):
    name: str = Field(..., min_length=1, max_length=50, examples=["Sensores"])

    @field_validator("name")
    @classmethod
    def normalize_non_empty(cls, v: str) -> str:
        normalized = v.strip()
        if not normalized:
            raise ValueError("El nombre no puede estar vacío")
        return normalized

class CategoryCreate(CategoryBase):
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "Sensores"
            }
        }
    )

class CategoryUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=50)
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "Sensores IoT"
            }
        }
    )

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
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 1,
                "name": "Sensores",
                "created_at": "2026-02-17T10:00:00Z",
                "updated_at": "2026-02-17T10:05:00Z",
            }
        },
    )

    @field_serializer("created_at", "updated_at", when_used="json")
    def serialize_datetime(self, value: datetime | None):
        if value is None:
            return None
        dt = value if value.tzinfo else value.replace(tzinfo=timezone.utc)
        return dt.isoformat()
