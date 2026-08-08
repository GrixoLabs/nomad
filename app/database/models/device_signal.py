from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    Identity,
    Index,
    REAL,
    SmallInteger,
    String,
    Text,
    func,
)
from sqlalchemy.dialects.postgresql import INET
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base


class DeviceSignal(Base):
    __tablename__ = "device_signals"
    __table_args__ = (
        Index("idx_signals_device", "device_id"),
        Index("idx_signals_received", "received_utc"),
        Index("idx_signals_device_timestamp", "device_id", "gps_timestamp_utc"),
        {"schema": "nomad"},
    )

    signal_id: Mapped[int] = mapped_column(
        BigInteger,
        Identity(always=True),
        primary_key=True,
    )

    device_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("nomad.devices.device_id", ondelete="CASCADE"),
        nullable=False,
    )

    gps_timestamp_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    received_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    latitude: Mapped[float] = mapped_column(nullable=False)

    longitude: Mapped[float] = mapped_column(nullable=False)

    accuracy_m: Mapped[float | None] = mapped_column(REAL)

    altitude_m: Mapped[float | None] = mapped_column(REAL)

    speed_mps: Mapped[float | None] = mapped_column(REAL)

    bearing_deg: Mapped[float | None] = mapped_column(REAL)

    battery_percent: Mapped[int | None] = mapped_column(SmallInteger)

    charging: Mapped[bool | None] = mapped_column(Boolean)

    battery_temperature: Mapped[float | None] = mapped_column(REAL)

    network_type: Mapped[str | None] = mapped_column(String(30))

    wifi_enabled: Mapped[bool | None] = mapped_column(Boolean)

    bluetooth_enabled: Mapped[bool | None] = mapped_column(Boolean)

    screen_on: Mapped[bool | None] = mapped_column(Boolean)

    power_save_mode: Mapped[bool | None] = mapped_column(Boolean)

    client_ip: Mapped[str | None] = mapped_column(INET)

    user_agent: Mapped[str | None] = mapped_column(Text)

    device = relationship("Device", back_populates="signals")
