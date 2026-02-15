from datetime import datetime
from typing import Iterable, Tuple

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.audit_log import AuditLog
from app.models.enums import ActionType, Entity


def create_log(
    db: Session,
    *,
    entity: Entity,
    action: ActionType,
    user_id: int,
    details: str | None = None,
    commit: bool = True,
) -> AuditLog:
    entry = AuditLog(
        entity=entity,
        action=action,
        user_id=user_id,
        details=details,
    )
    db.add(entry)
    if commit:
        db.commit()
        db.refresh(entry)
    else:
        db.flush()
    return entry


def list_logs(
    db: Session,
    *,
    entity: Entity | None = None,
    action: ActionType | None = None,
    user_id: int | None = None,
    date_from: datetime | None = None,
    date_to: datetime | None = None,
    order_dir: str | None = "desc",
    limit: int = 50,
    offset: int = 0,
) -> Tuple[Iterable[AuditLog], int]:
    filters = []
    if entity is not None:
        filters.append(AuditLog.entity == entity)
    if action is not None:
        filters.append(AuditLog.action == action)
    if user_id is not None:
        filters.append(AuditLog.user_id == user_id)
    if date_from is not None:
        filters.append(AuditLog.created_at >= date_from)
    if date_to is not None:
        filters.append(AuditLog.created_at <= date_to)

    if order_dir == "asc":
        stmt = select(AuditLog).where(*filters).order_by(AuditLog.created_at.asc())
    else:
        stmt = select(AuditLog).where(*filters).order_by(AuditLog.created_at.desc())

    total = db.scalar(select(func.count()).select_from(stmt.subquery())) or 0
    items = db.scalars(stmt.offset(offset).limit(limit)).all()
    return items, total
