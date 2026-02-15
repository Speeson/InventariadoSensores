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

class TurnoverItem(BaseModel):
    product_id: int
    sku: str
    name: str
    turnover: float | None
    outs: int
    stock_initial: float
    stock_final: float
    stock_average: float
    location: str | None = None

class TurnoverResponse(BaseModel):
    items: list[TurnoverItem]
    total: int
    limit: int
    offset: int
