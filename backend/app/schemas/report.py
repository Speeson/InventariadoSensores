from pydantic import BaseModel
from datetime import datetime

class TopConsumedItem(BaseModel):
    product_id: int
    sku: str
    name: str
    total_out: int

class TopConsumedResponse(BaseModel):
    items: list[TopConsumedItem]
    total: int
    limit: int
    offset: int
