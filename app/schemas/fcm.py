from pydantic import BaseModel, Field


class FcmTokenUpsert(BaseModel):
    token: str = Field(..., min_length=10)
    device_id: str | None = None
    platform: str = Field(default="android", min_length=2, max_length=30)
