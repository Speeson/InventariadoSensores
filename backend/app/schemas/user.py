from pydantic import BaseModel, ConfigDict, EmailStr, Field

class UserMeResponse(BaseModel):
    id: int
    username: str = Field(examples=["admin"])
    email: EmailStr = Field(examples=["admin@example.com"])
    role: str = Field(examples=["ADMIN"])
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "id": 1,
                "username": "admin",
                "email": "admin@example.com",
                "role": "ADMIN",
            }
        }
    )
