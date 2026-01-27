from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

bearer_scheme = HTTPBearer()

def get_bearer_token(
    creds: HTTPAuthorizationCredentials = Depends(bearer_scheme),
) -> str:
    if not creds.scheme or creds.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid auth scheme")
    return creds.credentials
