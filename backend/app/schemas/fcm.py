from pydantic import BaseModel, ConfigDict, Field


class FcmTokenUpsert(BaseModel):
    token: str = Field(..., min_length=10, examples=["fcm-token-xyz"])
    device_id: str | None = Field(default=None, examples=["pixel-7"])
    platform: str = Field(default="android", min_length=2, max_length=30, examples=["android"])
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "token": "fcm-token-xyz",
                "device_id": "pixel-7",
                "platform": "android",
            }
        }
    )
