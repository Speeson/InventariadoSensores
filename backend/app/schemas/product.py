from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict

class ProductBase(BaseModel):
    sku: str = Field(..., min_length=1, max_length=50, examples=["SKU-001"])
    name: str = Field(..., min_length=1, max_length=100, examples=["Sensor temperatura"])
    barcode: str | None = Field(None, min_length=1, max_length=100, examples=["1234567890123"])
    category_id: int = Field(examples=[1])
    active: bool | None = True  # opcional en create, en update puedes sobrescribir

class ProductCreate(ProductBase):
    barcode: str = Field(..., min_length=13, max_length=13, pattern=r"^\d{13}$")
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "sku": "SKU-001",
                "name": "Sensor temperatura",
                "barcode": "1234567890123",
                "category_id": 1,
                "active": True,
            }
        }
    )

class ProductUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=100)
    barcode: str | None = Field(None, min_length=13, max_length=13, pattern=r"^\d{13}$")
    category_id: int | None = None
    active: bool | None = None
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "name": "Sensor temperatura v2",
                "barcode": "1234567890999",
                "category_id": 1,
                "active": True,
            }
        }
    )

class ProductResponse(ProductBase):
    id: int
    created_at: datetime
    updated_at: datetime
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 10,
                "sku": "SKU-001",
                "name": "Sensor temperatura",
                "barcode": "1234567890123",
                "category_id": 1,
                "active": True,
                "created_at": "2026-02-17T10:00:00Z",
                "updated_at": "2026-02-17T10:00:00Z",
            }
        },
    )
