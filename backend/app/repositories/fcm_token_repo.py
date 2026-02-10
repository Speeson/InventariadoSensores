from datetime import datetime
from typing import Iterable

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.models.fcm_token import FcmToken
from app.models.user import User
from app.models.enums import UserRole


def upsert_token(
    db: Session,
    *,
    user_id: int,
    token: str,
    device_id: str | None,
    platform: str,
) -> FcmToken:
    existing = None
    if device_id:
        existing = db.scalar(
            select(FcmToken).where(FcmToken.user_id == user_id, FcmToken.device_id == device_id)
        )
    if existing is None:
        existing = db.scalar(select(FcmToken).where(FcmToken.token == token))

    if existing:
        existing.user_id = user_id
        existing.token = token
        existing.device_id = device_id
        existing.platform = platform
        existing.updated_at = datetime.utcnow()
        db.add(existing)
        db.commit()
        db.refresh(existing)
        return existing

    row = FcmToken(
        user_id=user_id,
        token=token,
        device_id=device_id,
        platform=platform,
    )
    db.add(row)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        existing = db.scalar(select(FcmToken).where(FcmToken.token == token))
        if existing:
            existing.user_id = user_id
            existing.device_id = device_id
            existing.platform = platform
            existing.updated_at = datetime.utcnow()
            db.add(existing)
            db.commit()
            db.refresh(existing)
            return existing
        raise
    db.refresh(row)
    return row


def list_tokens_for_roles(db: Session, roles: set[UserRole]) -> list[str]:
    if not roles:
        return []
    stmt = (
        select(FcmToken.token)
        .join(User, FcmToken.user_id == User.id)
        .where(User.role.in_(roles))
    )
    return [row[0] for row in db.execute(stmt).all()]
