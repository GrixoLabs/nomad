from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class UserRegisterRequest(BaseModel):
    """Matches Android UserRegistrationRequest. Email OR phone required."""

    device_uuid: UUID | None = None
    name: str = Field(min_length=1, max_length=100)
    email: str | None = Field(default=None, max_length=255)
    phone: str | None = Field(default=None, max_length=30)
    age: int = Field(ge=13, le=120)
    gender: str = Field(min_length=1, max_length=30)

    @field_validator("email")
    @classmethod
    def normalize_email(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip().lower()
        return cleaned or None

    @field_validator("phone")
    @classmethod
    def normalize_phone(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = "".join(ch for ch in value.strip() if ch.isdigit() or ch == "+")
        return cleaned or None

    @field_validator("name")
    @classmethod
    def normalize_name(cls, value: str) -> str:
        return value.strip()

    @field_validator("gender")
    @classmethod
    def normalize_gender(cls, value: str) -> str:
        return value.strip().upper()

    @model_validator(mode="after")
    def require_email_or_phone(self) -> "UserRegisterRequest":
        if not self.email and not self.phone:
            raise ValueError("Either email or phone is required")
        return self


class UserRegisterResponse(BaseModel):
    user_id: int
    journal_enabled: bool
    message: str = "User registered successfully"

    model_config = ConfigDict(from_attributes=True)
