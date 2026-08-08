from datetime import date, datetime
from typing import Optional
from uuid import UUID

from sqlalchemy import (
    BigInteger,
    Date,
    DateTime,
    Double,
    ForeignKey,
    Identity,
    Index,
    LargeBinary,
    String,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class JournalEntry(Base):
    __tablename__ = "journal_entries"
    __table_args__ = (
        Index("idx_journal_device_created", "device_id", "created_at"),
        Index("idx_journal_user_created", "user_id", "created_at"),
        {"schema": "nomad"},
    )

    entry_id: Mapped[int] = mapped_column(
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
    place_label: Mapped[str | None] = mapped_column(String(240))
    body_ciphertext: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    body_nonce: Mapped[bytes] = mapped_column(LargeBinary, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )


class NightStay(Base):
    __tablename__ = "night_stays"
    __table_args__ = (
        UniqueConstraint("device_id", "stay_date", name="uq_night_stays_device_date"),
        Index("idx_night_stays_device_date", "device_id", "stay_date"),
        {"schema": "nomad"},
    )

    night_stay_id: Mapped[int] = mapped_column(
        BigInteger, Identity(always=True), primary_key=True
    )
    device_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("nomad.devices.device_id", ondelete="CASCADE"),
        nullable=False,
    )
    stay_date: Mapped[date] = mapped_column(Date, nullable=False)
    latitude: Mapped[float] = mapped_column(Double, nullable=False)
    longitude: Mapped[float] = mapped_column(Double, nullable=False)
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ended_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    idle_hours: Mapped[float] = mapped_column(Double, nullable=False)
    weather_summary: Mapped[str | None] = mapped_column(String(160))
    temperature_c: Mapped[float | None] = mapped_column(Double)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
