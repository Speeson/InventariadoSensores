from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models.enums import ActionType, Entity


class AuditLogResponse(BaseModel):
    id: int
    entity: Entity
    action: ActionType
    user_id: int
    details: str | None
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class AuditLogListResponse(BaseModel):
    items: list[AuditLogResponse]
    total: int
    limit: int
    offset: int
