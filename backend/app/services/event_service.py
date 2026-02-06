from fastapi import HTTPException
from app.schemas.event import EventCreate

def apply_event(repo, payload: EventCreate, *, user_id=None) -> dict:
    """
    S1:
    - recibir evento IN/OUT
    - actualizar stock
    - registrar event + movement
    """

    current = repo.get_stock(payload.product_id)
    if current is None:
        raise HTTPException(status_code=404, detail="Stock not found for product")

    effective_delta = payload.delta if payload.type == "IN" else -payload.delta
    new_qty = current + effective_delta

    if new_qty < 0:
        raise HTTPException(status_code=409, detail="Insufficient stock")

    event = repo.insert_event(
        type=payload.type,
        product_id=payload.product_id,
        delta=payload.delta,  # guardamos delta positivo
        source=payload.source or "sensor_simulado",
    )

    repo.set_stock(payload.product_id, new_qty)

    movement = repo.insert_movement(
        product_id=payload.product_id,
        quantity=effective_delta,  # aquí sí +/- para auditoría
        delta=effective_delta,
        user_id=user_id,
        event_id=event["id"],
    )

    return {
        "event": event,
        "movement": movement,
        "new_stock": new_qty,
    }
