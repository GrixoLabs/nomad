from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    ForeignKey,
    REAL,
    SMALLINT,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import INET
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base


class DeviceSignal(Base):
    __tablename__ = "device_signals"
    __table_args__ = {"schema": "nomad"}

    signal_id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True
    )

    device_id: Mapped[int] = mapped_column(
        ForeignKey("nomad.devices.device_id", ondelete="CASCADE")
    )

    gps_timestamp_utc: Mapped[datetime]

    received_utc: Mapped[datetime]

    latitude: Mapped[float]

    longitude: Mapped[float]

    accuracy_m: Mapped[float | None] = mapped_column(REAL)

    altitude_m: Mapped[float | None] = mapped_column(REAL)

    speed_mps: Mapped[float | None] = mapped_column(REAL)

    bearing_deg: Mapped[float | None] = mapped_column(REAL)

    battery_percent: Mapped[int | None] = mapped_column(SMALLINT)

    charging: Mapped[bool | None]

    battery_temperature: Mapped[float | None] = mapped_column(REAL)

    network_type: Mapped[str | None] = mapped_column(String(30))

    wifi_enabled: Mapped[bool | None]

    bluetooth_enabled: Mapped[bool | None]

    screen_on: Mapped[bool | None]

    power_save_mode: Mapped[bool | None]

    client_ip: Mapped[str | None] = mapped_column(INET)

    user_agent: Mapped[str | None] = mapped_column(Text)

    device = relationship(
        "Device",
        back_populates="signals"
    )
