from pydantic import BaseModel, EmailStr, Field

class RegisterRequest(BaseModel):
    username: str = Field(min_length=3, max_length=50)
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    # Por seguridad, en prod NO dejaría elegir role en registro.
    # Para el proyecto, lo dejamos opcional y luego lo quitamos si queréis.
    role: str | None = None

class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
