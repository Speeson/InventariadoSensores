from fastapi import FastAPI
from app.api.routers.events import router as events_router

app = FastAPI(title="Inventory Backend - S1 (Events without DB)")

app.include_router(events_router)

@app.get("/health")
def health():
    return {"status": "ok"}