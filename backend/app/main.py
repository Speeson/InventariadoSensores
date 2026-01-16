from fastapi import FastAPI

from app.api.routes import auth, users

app = FastAPI(title="Sistema Inventariado Sensores")

app.include_router(auth.router)
app.include_router(users.router)

@app.get("/health")
def health():
    return {"status": "ok"}



# uvicorn app.main:app --reload para ejecutar el servidor en modo desarrollo
# .venv\Scripts\activate para activar el entorno virtual

# docker exec -it sis_postgres psql -U sis -d sis para entrar en la bd postgres del contenedor Docker
# select id, email, role from users;
