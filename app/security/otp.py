import hashlib
import hmac
import secrets
from datetime import datetime, timedelta, timezone

from app.config.settings import get_settings


def generate_otp(length: int | None = None) -> str:
    settings = get_settings()
    digits = length or settings.otp_length
    # cryptographically secure numeric OTP
    upper = 10**digits
    value = secrets.randbelow(upper)
    return str(value).zfill(digits)


def hash_otp(otp: str) -> str:
    settings = get_settings()
    digest = hmac.new(
        settings.jwt_secret.encode("utf-8"),
        otp.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return digest


def verify_otp(otp: str, otp_hash: str) -> bool:
    return hmac.compare_digest(hash_otp(otp), otp_hash)


def otp_expiry() -> datetime:
    settings = get_settings()
    return datetime.now(timezone.utc) + timedelta(minutes=settings.otp_expire_minutes)
