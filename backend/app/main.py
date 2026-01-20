import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import auth, users, products, stocks, movements

app = FastAPI(title="Sistema Inventariado Sensores")

cors_origins_env = os.getenv("CORS_ORIGINS", "")
allowed_origins = [o.strip() for o in cors_origins_env.split(",") if o.strip()] or [
    "http://localhost:3000",
    "http://127.0.0.1:3000", # Modificar si es necesario
    "http://10.0.2.2:3000",  # Android emulator
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(products.router)
app.include_router(stocks.router)
app.include_router(movements.router)

@app.get("/health")
def health():
    return {"status": "ok"}



# uvicorn app.main:app --reload para ejecutar el servidor en modo desarrollo
# cd c:\dam2\gigaproyecto\inventariadosensores\backend
# .\.venv313\Scripts\Activate.ps1
# deactivate para salir del entorno virtual

# docker exec -it sis_postgres psql -U sis -d sis para entrar en la bd postgres del contenedor Docker
# select id, email, role from users;
