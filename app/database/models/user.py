from datetime import datetime
from uuid import UUID, uuid4

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
    Text,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import UUID as PG_UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        CheckConstraint(
            "(email IS NOT NULL) OR (phone_number IS NOT NULL)",
            name="ck_users_email_or_phone",
        ),
        CheckConstraint(
            "age IS NULL OR (age BETWEEN 13 AND 120)",
            name="ck_users_age",
        ),
        CheckConstraint(
            "account_status IN ('pending', 'active', 'disabled')",
            name="ck_users_status",
        ),
        UniqueConstraint("email", name="uq_users_email"),
        UniqueConstraint("phone_number", name="uq_users_phone"),
        Index("idx_users_device_uuid", "device_uuid"),
        Index("idx_users_status", "account_status"),
        {"schema": "nomad"},
    )

    user_id: Mapped[UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        primary_key=True,
        default=uuid4,
    )

    email: Mapped[str | None] = mapped_column(String(255), nullable=True)
    phone_number: Mapped[str | None] = mapped_column(String(20), nullable=True)
    password_hash: Mapped[str | None] = mapped_column(Text, nullable=True)

    name: Mapped[str | None] = mapped_column(String(100), nullable=True)
    age: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    gender: Mapped[str | None] = mapped_column(String(30), nullable=True)
    device_uuid: Mapped[UUID | None] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("nomad.devices.device_uuid", ondelete="SET NULL"),
        nullable=True,
    )

    email_verified: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false"), default=False
    )
    phone_verified: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false"), default=False
    )
    account_status: Mapped[str] = mapped_column(
        String(20), nullable=False, server_default=text("'pending'"), default="pending"
    )
    journal_enabled: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false"), default=False
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    device = relationship("Device", back_populates="users")
    verification_codes = relationship(
        "VerificationCode",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    refresh_tokens = relationship(
        "RefreshToken",
        back_populates="user",
        cascade="all, delete-orphan",
    )


class VerificationCode(Base):
    __tablename__ = "verification_codes"
    __table_args__ = (
        CheckConstraint(
            "verification_type IN ('EMAIL', 'SMS', 'PASSWORD_RESET')",
            name="ck_verification_type",
        ),
        Index("idx_verification_user_type", "user_id", "verification_type", "created_at"),
        {"schema": "nomad"},
    )

    verification_id: Mapped[int] = mapped_column(
        BigInteger, Identity(), primary_key=True
    )
    user_id: Mapped[UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("nomad.users.user_id", ondelete="CASCADE"),
        nullable=False,
    )
    verification_type: Mapped[str] = mapped_column(String(20), nullable=False)
    otp_hash: Mapped[str] = mapped_column(Text, nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    attempts: Mapped[int] = mapped_column(nullable=False, server_default=text("0"), default=0)
    verified: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false"), default=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    user = relationship("User", back_populates="verification_codes")


class RefreshToken(Base):
    __tablename__ = "refresh_tokens"
    __table_args__ = (
        Index("idx_refresh_tokens_user", "user_id"),
        {"schema": "nomad"},
    )

    token_id: Mapped[int] = mapped_column(BigInteger, Identity(), primary_key=True)
    user_id: Mapped[UUID] = mapped_column(
        PG_UUID(as_uuid=True),
        ForeignKey("nomad.users.user_id", ondelete="CASCADE"),
        nullable=False,
    )
    token_hash: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked: Mapped[bool] = mapped_column(
        Boolean, nullable=False, server_default=text("false"), default=False
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    user = relationship("User", back_populates="refresh_tokens")
