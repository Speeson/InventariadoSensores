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
    model_config = ConfigDict(from_attributes=True)
