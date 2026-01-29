from sqlalchemy import Integer, String, DateTime, Enum, ForeignKey, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base
from datetime import datetime
from app.models.enums import Source, EventType, EventStatus

class Event(Base):
    __tablename__ = "events"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    event_type: Mapped[EventType] = mapped_column(Enum(EventType), nullable=False)
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id"), nullable=False)
    delta: Mapped[int] = mapped_column(Integer, nullable=False)
    location_id: Mapped[int] = mapped_column(ForeignKey("locations.id"), nullable=True)
    source: Mapped[Source] = mapped_column(Enum(Source), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )
    event_status: Mapped[EventStatus] = mapped_column(Enum(EventStatus), nullable=False, default=EventStatus.PENDING)
    processed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=True
    )
    retry_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    last_error: Mapped[str] = mapped_column(String(255), nullable=True)
    idempotency_key: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    
    __table_args__ = (
        Index("ix_events_product", "product_id"),
        Index("ix_events_type", "event_type"),
        Index("ix_events_location", "location_id"),
        Index("ix_events_status", "event_status"),
        Index("ix_events_created", "created_at"),
        Index("ix_events_idempotency", "idempotency_key"),
    )