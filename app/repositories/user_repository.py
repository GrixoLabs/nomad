from datetime import datetime, timedelta, timezone
from uuid import UUID

from sqlalchemy import and_, func, or_, select
from sqlalchemy.orm import Session

from app.config.settings import get_settings
from app.database.models.user import RefreshToken, User, VerificationCode


class UserRepository:
    def get_by_id(self, db: Session, user_id: UUID) -> User | None:
        return db.get(User, user_id)

    def get_by_email(self, db: Session, email: str) -> User | None:
        return db.scalar(select(User).where(User.email == email.lower()))

    def get_by_phone(self, db: Session, phone: str) -> User | None:
        return db.scalar(select(User).where(User.phone_number == phone))

    def get_by_email_or_phone(
        self,
        db: Session,
        email: str | None,
        phone: str | None,
    ) -> User | None:
        clauses = []
        if email:
            clauses.append(User.email == email.lower())
        if phone:
            clauses.append(User.phone_number == phone)
        if not clauses:
            return None
        return db.scalar(select(User).where(or_(*clauses)))

    def create(self, db: Session, user: User) -> User:
        db.add(user)
        db.commit()
        db.refresh(user)
        return user

    def save(self, db: Session, user: User) -> User:
        user.updated_at = datetime.now(timezone.utc)
        db.commit()
        db.refresh(user)
        return user


class VerificationRepository:
    def create(self, db: Session, code: VerificationCode) -> VerificationCode:
        db.add(code)
        db.commit()
        db.refresh(code)
        return code

    def latest_active(
        self,
        db: Session,
        user_id: UUID,
        verification_type: str,
    ) -> VerificationCode | None:
        now = datetime.now(timezone.utc)
        return db.scalar(
            select(VerificationCode)
            .where(
                and_(
                    VerificationCode.user_id == user_id,
                    VerificationCode.verification_type == verification_type,
                    VerificationCode.verified.is_(False),
                    VerificationCode.expires_at > now,
                )
            )
            .order_by(VerificationCode.created_at.desc())
            .limit(1)
        )

    def count_recent(
        self,
        db: Session,
        user_id: UUID,
        verification_type: str,
    ) -> int:
        settings = get_settings()
        window_start = datetime.now(timezone.utc) - timedelta(
            minutes=settings.otp_rate_limit_window_minutes
        )
        count = db.scalar(
            select(func.count())
            .select_from(VerificationCode)
            .where(
                and_(
                    VerificationCode.user_id == user_id,
                    VerificationCode.verification_type == verification_type,
                    VerificationCode.created_at >= window_start,
                )
            )
        )
        return int(count or 0)

    def save(self, db: Session, code: VerificationCode) -> VerificationCode:
        db.commit()
        db.refresh(code)
        return code


class RefreshTokenRepository:
    def create(self, db: Session, token: RefreshToken) -> RefreshToken:
        db.add(token)
        db.commit()
        db.refresh(token)
        return token

    def get_valid(self, db: Session, token_hash: str) -> RefreshToken | None:
        now = datetime.now(timezone.utc)
        return db.scalar(
            select(RefreshToken).where(
                and_(
                    RefreshToken.token_hash == token_hash,
                    RefreshToken.revoked.is_(False),
                    RefreshToken.expires_at > now,
                )
            )
        )

    def revoke(self, db: Session, token: RefreshToken) -> None:
        token.revoked = True
        db.commit()
