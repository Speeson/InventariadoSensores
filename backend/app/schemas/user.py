from pydantic import BaseModel, EmailStr

class UserMeResponse(BaseModel):
    id: int
    username: str
    email: EmailStr
    role: str
