from pydantic import BaseModel
from typing import Optional

class MovementOut(BaseModel):
    id: int
    product_id: int
    quantity: int  # +IN / -OUT
    user_id: Optional[int]
    created_at: str
    event_id: int
