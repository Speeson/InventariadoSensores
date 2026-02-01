from datetime import datetime

from pydantic import BaseModel, ConfigDict


class LocationResponse(BaseModel):
    id: int
    code: str
    description: str | None
    created_at: datetime
    model_config = ConfigDict(from_attributes=True)
