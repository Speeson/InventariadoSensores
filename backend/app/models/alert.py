from sqlalchemy import Integer, String, Boolean, DateTime, Enum, ForeignKey, func, Index
from sqlalchemy.orm import Mapped, mapped_column
from app.db.base import Base
from datetime import datetime
from app.models.enums import AlertStatus

class Alert(Base):
    __tablename__ = "alerts"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    stock_id: Mapped[int] = mapped_column(ForeignKey("stocks.id"), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    min_quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    alert_status: Mapped[AlertStatus] = mapped_column(Enum(AlertStatus), nullable=False, default=AlertStatus.PENDING)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )
    ack_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=True
    )
    ack_user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=True)

    notification_sent: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    notification_sent_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=True)
    notification_channel: Mapped[str] = mapped_column(String(50), nullable=True)
    last_error: Mapped[str] = mapped_column(String(255), nullable=True)
    
