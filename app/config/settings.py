from functools import lru_cache
from urllib.parse import quote_plus

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application configuration loaded from environment / .env."""

    database_host: str = "127.0.0.1"
    database_port: int = 5432
    database_name: str = "nomad"
    database_user: str = "nomad"
    database_password: str = "nomad"

    api_host: str = "127.0.0.1"
    api_port: int = 8001
    log_level: str = "INFO"
    cors_origins: str = "*"

    # Auth / JWT
    jwt_secret: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    refresh_token_expire_days: int = 30

    # OTP
    otp_length: int = 6
    otp_expire_minutes: int = 10
    otp_max_attempts: int = 5
    otp_rate_limit_count: int = 3
    otp_rate_limit_window_minutes: int = 15

    # Resend (email)
    resend_api_key: str | None = None
    resend_from_email: str = "Nomad <onboarding@resend.dev>"

    # Twilio (SMS) — mapped from your .env names
    twilio_client_id: str | None = None  # Account SID
    twilio_client_key: str | None = None  # Auth Token
    twilio_from_number: str | None = None  # E.164 sender, e.g. +1...

    # External place / weather / tourism APIs
    nominatim_url: str = "https://nominatim.openstreetmap.org/reverse"
    nominatim_user_agent: str = "NomadApp/1.0 (contact: ops@grixo.dev)"
    open_meteo_url: str = "https://api.open-meteo.com/v1/forecast"
    overpass_url: str = "https://overpass-api.de/api/interpreter"
    # Android client waits ~25s; keep server outbound slightly under that.
    external_http_timeout_seconds: float = 22.0

    model_config = SettingsConfigDict(
        env_file=".env",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def database_url(self) -> str:
        user = quote_plus(self.database_user)
        password = quote_plus(self.database_password)
        return (
            f"postgresql+psycopg2://{user}:{password}"
            f"@{self.database_host}:{self.database_port}/{self.database_name}"
        )

    @property
    def cors_origin_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
