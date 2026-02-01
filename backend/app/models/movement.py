from sqlalchemy import Integer, DateTime, Enum, ForeignKey, func, Index
from sqlalchemy.orm import Mapped, mapped_column, relationship
from app.db.base import Base
from datetime import datetime
from app.models.enums import Source, MovementType

class Movement(Base):
    __tablename__ = "movements"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    product_id: Mapped[int] = mapped_column(ForeignKey("products.id"), nullable=False)
    quantity: Mapped[int] = mapped_column(Integer, nullable=False)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), nullable=True)
    location_id: Mapped[int | None] = mapped_column(ForeignKey("locations.id"), nullable=True)
    location_rel = relationship("Location")
    movement_type: Mapped[MovementType] = mapped_column(Enum(MovementType), nullable=False)
    movement_source: Mapped[Source] = mapped_column(Enum(Source), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        server_default=func.now(),
        nullable=False
    )
    
    __table_args__ = (
        Index("ix_movements_product", "product_id"),
        Index("ix_movements_user", "user_id"),
        Index("ix_movements_type", "movement_type"),
        Index("ix_movements_location", "location_id"),
        Index("ix_movements_created", "created_at"),
    )

@property
def location(self) -> str | None:
        return self.location_rel.code if self.location_rel else None