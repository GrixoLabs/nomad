from datetime import datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import (
    BigInteger,
    Boolean,
    DateTime,
    Double,
    ForeignKey,
    Identity,
    Index,
    Integer,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class MapPlotter(Base):
    __tablename__ = "map_plotter"
    __table_args__ = (
        UniqueConstraint("device_id", "latitude", "longitude", name="uq_map_plotter_device_cell"),
        Index("idx_map_plotter_device_last", "device_id", "last_gps_timestamp"),
        {"schema": "nomad"},
    )

    plot_id: Mapped[int] = mapped_column(
        BigInteger, Identity(always=True), primary_key=True
    )
    device_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("nomad.devices.device_id", ondelete="CASCADE"),
        nullable=False,
    )
    user_id: Mapped[Optional[UUID]] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("nomad.users.user_id", ondelete="SET NULL"),
        nullable=True,
    )
    latitude: Mapped[float] = mapped_column(Double, nullable=False)
    longitude: Mapped[float] = mapped_column(Double, nullable=False)
    first_gps_timestamp: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False
    )
    last_gps_timestamp: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False
    )
    total_time_at_location_seconds: Mapped[float] = mapped_column(
        Double, nullable=False, default=0.0
    )
    night_time_seconds: Mapped[float] = mapped_column(Double, nullable=False, default=0.0)
    visit_count: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    signal_count: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    journal_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0)
    night_stayed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    place_label: Mapped[str | None] = mapped_column(String(240))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )


class MapPlotterDevice(Base):
    __tablename__ = "map_plotter_devices"
    __table_args__ = ({"schema": "nomad"},)

    device_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("nomad.devices.device_id", ondelete="CASCADE"),
        primary_key=True,
    )
    last_signal_id: Mapped[int | None] = mapped_column(BigInteger)
    last_synced_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
