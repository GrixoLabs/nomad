from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.auth import (
    ForgotPasswordRequest,
    LoginRequest,
    MessageResponse,
    RegisterRequest,
    RegisterResponse,
    ResetPasswordRequest,
    SendEmailOtpRequest,
    SendSmsOtpRequest,
    TokenResponse,
    VerifyEmailOtpRequest,
    VerifySmsOtpRequest,
)
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["Auth"])
service = AuthService()


@router.post("/register", response_model=RegisterResponse, status_code=201)
def register(request: RegisterRequest, db: Session = Depends(get_db)):
    user = service.register(db, request)
    return RegisterResponse(
        user_id=user.user_id,
        account_status=user.account_status,
        email_verified=user.email_verified,
        phone_verified=user.phone_verified,
        journal_enabled=user.journal_enabled,
        message=(
            "Account created and verification code sent. "
            "Enter the OTP to activate."
        ),
    )


@router.post("/send-email-otp", response_model=MessageResponse)
def send_email_otp(request: SendEmailOtpRequest, db: Session = Depends(get_db)):
    service.send_email_otp(db, request)
    return MessageResponse(message="If the account exists, an email OTP was sent.")


@router.post("/verify-email-otp", response_model=MessageResponse)
def verify_email_otp(request: VerifyEmailOtpRequest, db: Session = Depends(get_db)):
    user = service.verify_email_otp(db, request)
    return MessageResponse(
        message=(
            "Email verified."
            if user.account_status == "active"
            else "Email verified. Account still pending additional verification."
        )
    )


@router.post("/send-sms-otp", response_model=MessageResponse)
def send_sms_otp(request: SendSmsOtpRequest, db: Session = Depends(get_db)):
    service.send_sms_otp(db, request)
    return MessageResponse(message="If the account exists, an SMS OTP was sent.")


@router.post("/verify-sms-otp", response_model=MessageResponse)
def verify_sms_otp(request: VerifySmsOtpRequest, db: Session = Depends(get_db)):
    user = service.verify_sms_otp(db, request)
    return MessageResponse(
        message=(
            "Phone verified."
            if user.account_status == "active"
            else "Phone verified. Account still pending additional verification."
        )
    )


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    return service.login(db, request)


@router.post("/forgot-password", response_model=MessageResponse)
def forgot_password(request: ForgotPasswordRequest, db: Session = Depends(get_db)):
    service.forgot_password(db, request)
    return MessageResponse(
        message="If the account exists, a reset code was sent to email or phone."
    )


@router.post("/reset-password", response_model=MessageResponse)
def reset_password(request: ResetPasswordRequest, db: Session = Depends(get_db)):
    service.reset_password(db, request)
    return MessageResponse(message="Password updated successfully.")
