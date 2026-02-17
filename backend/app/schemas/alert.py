from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models.enums import AlertStatus, AlertType


class AlertResponse(BaseModel):
    id: int
    stock_id: int | None
    quantity: int
    min_quantity: int
    alert_status: AlertStatus
    alert_type: AlertType
    created_at: datetime
    ack_at: datetime | None
    ack_user_id: int | None
    model_config = ConfigDict(
        from_attributes=True,
        json_schema_extra={
            "example": {
                "id": 12,
                "stock_id": 5,
                "quantity": 2,
                "min_quantity": 5,
                "alert_status": "PENDING",
                "alert_type": "LOW_STOCK",
                "created_at": "2026-02-17T10:40:00Z",
                "ack_at": None,
                "ack_user_id": None,
            }
        },
    )
