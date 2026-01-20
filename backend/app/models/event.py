from sqlalchemy import ForeignKey, Integer, DateTime, func
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base
from app.models.enums import EventType
from datetime import datetime


class Event(Base):
    __tablename__ = "events"

    id: Mapped[int] = mapped_column(primary_key=True)
    event_type: Mapped[EventType] = mapped_column(nullable=False)
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id"), nullable=False)
    delta: Mapped[int] = mapped_column(Integer, nullable=False)
    source: Mapped[str] = mapped_column(nullable=False)
    processed: Mapped[bool] = mapped_column(nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )