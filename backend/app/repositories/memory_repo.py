from datetime import datetime, timezone
from typing import Dict, List, Optional

class MemoryRepo:
    """
    Simula BD en memoria.
    Cuando tengáis PostgreSQL/SQLAlchemy, se sustituye por SqlAlchemyRepo
    sin cambiar el endpoint.
    """

    def __init__(self):
        self._event_id = 1
        self._movement_id = 1

        # Stock actual por producto
        self.stocks: Dict[int, int] = {
            1: 10,
            2: 5,
            3: 0,
        }

        # Historial
        self.events: List[dict] = []
        self.movements: List[dict] = []

    def get_stock(self, product_id: int) -> Optional[int]:
        return self.stocks.get(product_id)

    def set_stock(self, product_id: int, quantity: int) -> None:
        self.stocks[product_id] = quantity

    def insert_event(self, *, type: str, product_id: int, delta: int, source: str) -> dict:
        ev = {
            "id": self._event_id,
            "type": type,
            "product_id": product_id,
            "delta": delta,
            "source": source,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        self._event_id += 1
        self.events.append(ev)
        return ev

    def insert_movement(self, *, product_id: int, quantity: int, user_id: Optional[int], event_id: int) -> dict:
        mv = {
            "id": self._movement_id,
            "product_id": product_id,
            "quantity": quantity,
            "user_id": user_id,
            "event_id": event_id,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }
        self._movement_id += 1
        self.movements.append(mv)
        return mv

    def list_events(self, limit: int = 50) -> List[dict]:
        return list(reversed(self.events))[:limit]

    def list_movements(self, limit: int = 50) -> List[dict]:
        return list(reversed(self.movements))[:limit]