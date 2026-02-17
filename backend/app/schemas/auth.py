from pydantic import BaseModel, ConfigDict, EmailStr, Field

class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=50, examples=["admin"])
    email: EmailStr = Field(examples=["admin@example.com"])
    password: str = Field(min_length=8, max_length=128, examples=["Pass123!"])
    # Por seguridad, en prod NO dejaría elegir role en registro.
    # Para el proyecto, lo dejamos opcional y luego lo quitamos si queréis.
    role: str | None = Field(default=None, examples=["ADMIN"])
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "username": "admin",
                "email": "admin@example.com",
                "password": "Pass123!",
                "role": "ADMIN",
            }
        }
    )

class TokenResponse(BaseModel):
    access_token: str = Field(examples=["eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."])
    token_type: str = "bearer"
    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                "token_type": "bearer",
            }
        }
    )
