from fastapi import APIRouter

router = APIRouter(prefix="/users", tags=["Users"])


@router.post("/register", deprecated=True)
def register_user_deprecated():
    """
    Deprecated. Use the auth flow:
      POST /api/v1/auth/register
      POST /api/v1/auth/send-email-otp | send-sms-otp
      POST /api/v1/auth/verify-email-otp | verify-sms-otp
    """
    return {
        "message": (
            "Deprecated. Use /api/v1/auth/register and OTP verification endpoints."
        ),
        "docs": "/docs",
    }
