from datetime import datetime
from uuid import UUID

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Identity,
    Index,
    SmallInteger,
    String,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base


class User(Base):
    """
    App user profile. Email OR phone is required (DB check constraint).
    Journal is enabled for registered users; skipped guests are not stored here.
    """

    __tablename__ = "users"
    __table_args__ = (
        CheckConstraint(
            "(email IS NOT NULL) OR (phone IS NOT NULL)",
            name="ck_users_email_or_phone",
        ),
        UniqueConstraint("email", name="uq_users_email"),
        UniqueConstraint("phone", name="uq_users_phone"),
        Index("idx_users_device_uuid", "device_uuid"),
        {"schema": "nomad"},
    )

    user_id: Mapped[int] = mapped_column(
        BigInteger,
        Identity(always=True),
        primary_key=True,
    )

    device_uuid: Mapped[UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("nomad.devices.device_uuid", ondelete="SET NULL"),
        nullable=True,
    )

    name: Mapped[str] = mapped_column(String(100), nullable=False)

    email: Mapped[str | None] = mapped_column(String(255), nullable=True)

    phone: Mapped[str | None] = mapped_column(String(30), nullable=True)

    age: Mapped[int] = mapped_column(SmallInteger, nullable=False)

    gender: Mapped[str] = mapped_column(String(30), nullable=False)

    journal_enabled: Mapped[bool] = mapped_column(
        Boolean,
        nullable=False,
        server_default=text("true"),
        default=True,
    )

    created_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    updated_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    device = relationship("Device", back_populates="users")
