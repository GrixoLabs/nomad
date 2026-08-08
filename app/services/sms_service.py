import logging

from twilio.base.exceptions import TwilioRestException
from twilio.rest import Client

from app.config.settings import get_settings

logger = logging.getLogger(__name__)


class SmsService:
    def send_otp(self, to_phone: str, otp: str) -> None:
        settings = get_settings()
        if not (
            settings.twilio_client_id
            and settings.twilio_client_key
            and settings.twilio_from_number
        ):
            logger.warning(
                "Twilio not fully configured (TWILIO_CLIENT_ID/KEY/FROM_NUMBER) — SMS OTP not sent"
            )
            logger.info("DEV SMS OTP for %s: %s", to_phone, otp)
            return

        try:
            client = Client(settings.twilio_client_id, settings.twilio_client_key)
            client.messages.create(
                body=(
                    f"Your Nomad code is {otp}. "
                    f"Expires in {settings.otp_expire_minutes} minutes."
                ),
                from_=settings.twilio_from_number,
                to=to_phone,
            )
        except TwilioRestException as exc:
            logger.error("Twilio error: %s", exc)
            raise RuntimeError("Failed to send verification SMS") from exc
