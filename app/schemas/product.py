from datetime import datetime
from pydantic import BaseModel, Field, ConfigDict

class ProductBase(BaseModel):
    sku: str = Field(..., min_length=1, max_length=50)
    name: str = Field(..., min_length=1, max_length=100)
    barcode: str | None = Field(None, min_length=1, max_length=100)
    category_id: int
    active: bool | None = True  # opcional en create, en update puedes sobrescribir

class ProductCreate(ProductBase):
    barcode: str = Field(..., min_length=13, max_length=13, pattern=r"^\d{13}$")

class ProductUpdate(BaseModel):
    name: str | None = Field(None, min_length=1, max_length=100)
    barcode: str | None = Field(None, min_length=13, max_length=13, pattern=r"^\d{13}$")
    category_id: int | None = None
    active: bool | None = None

class ProductResponse(ProductBase):
    id: int
    created_at: datetime
    updated_at: datetime
    model_config = ConfigDict(from_attributes=True)
