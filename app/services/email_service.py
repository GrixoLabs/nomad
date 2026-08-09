import logging

import httpx

from app.config.settings import get_settings

logger = logging.getLogger(__name__)


class EmailService:
    def send_otp(self, to_email: str, otp: str) -> None:
        settings = get_settings()
        if not settings.resend_api_key:
            logger.warning("RESEND_API_KEY missing — email OTP not sent (dev fallback log)")
            logger.info("DEV email OTP for %s: %s", to_email, otp)
            return

        payload = {
            "from": settings.resend_from_email,
            "to": [to_email],
            "subject": "Your Nomad verification code",
            "text": (
                f"Your Nomad verification code is {otp}.\n"
                f"It expires in {settings.otp_expire_minutes} minutes.\n"
                "If you did not request this, ignore this email."
            ),
        }
        response = httpx.post(
            "https://api.resend.com/emails",
            headers={
                "Authorization": f"Bearer {settings.resend_api_key}",
                "Content-Type": "application/json",
            },
            json=payload,
            timeout=20.0,
        )
        if response.status_code >= 400:
            logger.error("Resend error %s: %s", response.status_code, response.text)
            raise RuntimeError("Failed to send verification email")
