from datetime import datetime, timezone
from uuid import uuid4

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.config.settings import get_settings
from app.database.models.user import RefreshToken, User, VerificationCode
from app.repositories.device_repository import DeviceRepository
from app.repositories.user_repository import (
    RefreshTokenRepository,
    UserRepository,
    VerificationRepository,
)
from app.schemas.auth import (
    ForgotPasswordRequest,
    LoginRequest,
    RegisterRequest,
    ResetPasswordRequest,
    SendEmailOtpRequest,
    SendSmsOtpRequest,
    TokenResponse,
    VerifyEmailOtpRequest,
    VerifySmsOtpRequest,
)
from app.security.jwt_tokens import (
    create_access_token,
    create_refresh_token_value,
    hash_token,
    refresh_expiry,
)
from app.security.otp import generate_otp, hash_otp, otp_expiry, verify_otp
from app.security.passwords import hash_password, verify_password
from app.services.email_service import EmailService
from app.services.sms_service import SmsService


class AuthService:
    def __init__(self) -> None:
        self.users = UserRepository()
        self.codes = VerificationRepository()
        self.refresh_tokens = RefreshTokenRepository()
        self.devices = DeviceRepository()
        self.email = EmailService()
        self.sms = SmsService()

    def _maybe_activate(self, user: User) -> None:
        """
        Active when at least one channel is verified.
        Journal unlocks when the account is active.
        """
        if user.email_verified or user.phone_verified:
            user.account_status = "active"
            user.journal_enabled = True
        else:
            user.account_status = "pending"
            user.journal_enabled = False

    def register(self, db: Session, request: RegisterRequest) -> User:
        email = str(request.email).lower() if request.email else None
        phone = request.phone_number

        existing = self.users.get_by_email_or_phone(db, email=email, phone=phone)
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="An account with this email or phone already exists",
            )

        if request.device_uuid is not None:
            device = self.devices.get_by_uuid(db, request.device_uuid)
            if device is None:
                raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail="Device not registered. Call /devices/register first.",
                )

        user = User(
            user_id=uuid4(),
            email=email,
            phone_number=phone,
            password_hash=hash_password(request.password),
            name=request.name,
            age=request.age,
            gender=request.gender,
            device_uuid=request.device_uuid,
            email_verified=False,
            phone_verified=False,
            account_status="pending",
            journal_enabled=False,
        )
        return self.users.create(db, user)

    def _enforce_rate_limit(
        self, db: Session, user_id, verification_type: str
    ) -> None:
        settings = get_settings()
        recent = self.codes.count_recent(db, user_id, verification_type)
        if recent >= settings.otp_rate_limit_count:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=(
                    f"Too many OTP requests. Try again in "
                    f"{settings.otp_rate_limit_window_minutes} minutes."
                ),
            )

    def send_email_otp(self, db: Session, request: SendEmailOtpRequest) -> None:
        user = self.users.get_by_email(db, str(request.email).lower())
        if user is None:
            # Avoid account enumeration
            return

        self._enforce_rate_limit(db, user.user_id, "EMAIL")
        otp = generate_otp()
        code = VerificationCode(
            user_id=user.user_id,
            verification_type="EMAIL",
            otp_hash=hash_otp(otp),
            expires_at=otp_expiry(),
        )
        self.codes.create(db, code)
        self.email.send_otp(user.email, otp)  # type: ignore[arg-type]

    def send_sms_otp(self, db: Session, request: SendSmsOtpRequest) -> None:
        user = self.users.get_by_phone(db, request.phone_number)
        if user is None:
            return

        self._enforce_rate_limit(db, user.user_id, "SMS")
        otp = generate_otp()
        code = VerificationCode(
            user_id=user.user_id,
            verification_type="SMS",
            otp_hash=hash_otp(otp),
            expires_at=otp_expiry(),
        )
        self.codes.create(db, code)
        self.sms.send_otp(user.phone_number, otp)  # type: ignore[arg-type]

    def _verify_code(
        self,
        db: Session,
        user: User,
        verification_type: str,
        otp: str,
    ) -> None:
        settings = get_settings()
        code = self.codes.latest_active(db, user.user_id, verification_type)
        if code is None:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="No active verification code. Request a new OTP.",
            )
        if code.attempts >= settings.otp_max_attempts:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Too many invalid attempts. Request a new OTP.",
            )

        if not verify_otp(otp.strip(), code.otp_hash):
            code.attempts += 1
            self.codes.save(db, code)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid verification code",
            )

        code.verified = True
        self.codes.save(db, code)

        if verification_type == "EMAIL":
            user.email_verified = True
        elif verification_type == "SMS":
            user.phone_verified = True

        self._maybe_activate(user)
        self.users.save(db, user)

    def verify_email_otp(self, db: Session, request: VerifyEmailOtpRequest) -> User:
        user = self.users.get_by_email(db, str(request.email).lower())
        if user is None:
            raise HTTPException(status_code=404, detail="User not found")
        self._verify_code(db, user, "EMAIL", request.otp)
        return user

    def verify_sms_otp(self, db: Session, request: VerifySmsOtpRequest) -> User:
        user = self.users.get_by_phone(db, request.phone_number)
        if user is None:
            raise HTTPException(status_code=404, detail="User not found")
        self._verify_code(db, user, "SMS", request.otp)
        return user

    def _issue_tokens(self, db: Session, user: User) -> TokenResponse:
        access = create_access_token(user.user_id)
        refresh = create_refresh_token_value()
        self.refresh_tokens.create(
            db,
            RefreshToken(
                user_id=user.user_id,
                token_hash=hash_token(refresh),
                expires_at=refresh_expiry(),
            ),
        )
        return TokenResponse(
            access_token=access,
            refresh_token=refresh,
            user_id=user.user_id,
            account_status=user.account_status,
            email_verified=user.email_verified,
            phone_verified=user.phone_verified,
            journal_enabled=user.journal_enabled,
            name=user.name,
            email=user.email,
            phone_number=user.phone_number,
            age=user.age,
            gender=user.gender,
        )

    def login(self, db: Session, request: LoginRequest) -> TokenResponse:
        email = str(request.email).lower() if request.email else None
        user = self.users.get_by_email_or_phone(
            db, email=email, phone=request.phone_number
        )
        if user is None or not user.password_hash:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid credentials",
            )
        if not verify_password(user.password_hash, request.password):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid credentials",
            )
        if user.account_status == "disabled":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Account disabled",
            )
        if user.account_status != "active":
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Verify email or phone before logging in",
            )
        return self._issue_tokens(db, user)

    def forgot_password(self, db: Session, request: ForgotPasswordRequest) -> None:
        email = str(request.email).lower() if request.email else None
        user = self.users.get_by_email_or_phone(
            db, email=email, phone=request.phone_number
        )
        if user is None:
            return

        self._enforce_rate_limit(db, user.user_id, "PASSWORD_RESET")
        otp = generate_otp()
        code = VerificationCode(
            user_id=user.user_id,
            verification_type="PASSWORD_RESET",
            otp_hash=hash_otp(otp),
            expires_at=otp_expiry(),
        )
        self.codes.create(db, code)

        # Prefer the channel the client asked for; otherwise use whichever is on file.
        if request.email and user.email:
            self.email.send_otp(user.email, otp)
        elif request.phone_number and user.phone_number:
            self.sms.send_otp(user.phone_number, otp)
        elif user.email:
            self.email.send_otp(user.email, otp)
        elif user.phone_number:
            self.sms.send_otp(user.phone_number, otp)

    def reset_password(self, db: Session, request: ResetPasswordRequest) -> None:
        email = str(request.email).lower() if request.email else None
        user = self.users.get_by_email_or_phone(
            db, email=email, phone=request.phone_number
        )
        if user is None:
            raise HTTPException(status_code=404, detail="User not found")

        settings = get_settings()
        code = self.codes.latest_active(db, user.user_id, "PASSWORD_RESET")
        if code is None:
            raise HTTPException(
                status_code=400,
                detail="No active reset code. Request a new one.",
            )
        if code.attempts >= settings.otp_max_attempts:
            raise HTTPException(
                status_code=400,
                detail="Too many invalid attempts. Request a new OTP.",
            )
        if not verify_otp(request.otp.strip(), code.otp_hash):
            code.attempts += 1
            self.codes.save(db, code)
            raise HTTPException(status_code=400, detail="Invalid verification code")

        code.verified = True
        self.codes.save(db, code)
        user.password_hash = hash_password(request.new_password)
        user.updated_at = datetime.now(timezone.utc)
        self.users.save(db, user)
