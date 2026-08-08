from datetime import datetime
from uuid import UUID

from sqlalchemy import BigInteger, Boolean, DateTime, String
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base


class Device(Base):
    __tablename__ = "devices"
    __table_args__ = {"schema": "nomad"}

    device_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True
    )

    device_uuid: Mapped[UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        unique=True,
        nullable=False
    )

    device_name: Mapped[str] = mapped_column(String(100))

    manufacturer: Mapped[str | None] = mapped_column(String(50))

    model: Mapped[str | None] = mapped_column(String(100))

    android_version: Mapped[str | None] = mapped_column(String(30))

    app_version: Mapped[str | None] = mapped_column(String(30))

    first_seen_utc: Mapped[datetime]

    last_seen_utc: Mapped[datetime]

    is_active: Mapped[bool] = mapped_column(
        Boolean,
        default=True
    )

    signals = relationship(
        "DeviceSignal",
        back_populates="device",
        cascade="all, delete-orphan"
    )
