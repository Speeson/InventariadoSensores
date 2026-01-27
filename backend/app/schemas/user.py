from pydantic import BaseModel, EmailStr

class UserMeResponse(BaseModel):
    id: int
    email: EmailStr
    role: str
