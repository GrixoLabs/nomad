from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator


def _normalize_phone(value: str | None) -> str | None:
    """Normalize to E.164-ish form for Twilio (+countrycode…)."""
    if value is None:
        return None
    raw = value.strip()
    if not raw:
        return None
    # Keep leading +; strip spaces/dashes/parens.
    cleaned = "".join(ch for ch in raw if ch.isdigit() or ch == "+")
    if cleaned.startswith("00"):
        cleaned = "+" + cleaned[2:]
    digits = "".join(ch for ch in cleaned if ch.isdigit())
    if cleaned.startswith("+"):
        # Already international
        return f"+{digits}" if len(digits) >= 8 else None
    # Common India local 10-digit mobiles → +91
    if len(digits) == 10 and digits[0] in "6789":
        return f"+91{digits}"
    # Already includes country code without +
    if len(digits) >= 11:
        return f"+{digits}"
    return cleaned or None


class RegisterRequest(BaseModel):
    name: str | None = Field(default=None, max_length=100)
    email: EmailStr | None = None
    phone_number: str | None = Field(default=None, max_length=20)
    password: str = Field(min_length=8, max_length=128)
    confirm_password: str = Field(min_length=8, max_length=128)
    age: int | None = Field(default=None, ge=13, le=120)
    gender: str | None = Field(default=None, max_length=30)
    device_uuid: UUID | None = None

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str | None) -> str | None:
        return _normalize_phone(value)

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str | None) -> str | None:
        if value is None:
            return None
        cleaned = value.strip()
        return cleaned or None

    @field_validator("gender")
    @classmethod
    def validate_gender(cls, value: str | None) -> str | None:
        if value is None:
            return None
        return value.strip().upper() or None

    @model_validator(mode="after")
    def validate_register(self) -> "RegisterRequest":
        if not self.email and not self.phone_number:
            raise ValueError("Either email or phone_number is required")
        if self.password != self.confirm_password:
            raise ValueError("password and confirm_password do not match")
        return self


class RegisterResponse(BaseModel):
    user_id: UUID
    account_status: str
    email_verified: bool
    phone_verified: bool
    journal_enabled: bool
    message: str


class SendEmailOtpRequest(BaseModel):
    email: EmailStr


class SendSmsOtpRequest(BaseModel):
    phone_number: str = Field(min_length=8, max_length=20)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str) -> str:
        phone = _normalize_phone(value)
        if not phone:
            raise ValueError("Invalid phone number")
        return phone


class VerifyEmailOtpRequest(BaseModel):
    email: EmailStr
    otp: str = Field(min_length=4, max_length=10)


class VerifySmsOtpRequest(BaseModel):
    phone_number: str = Field(min_length=8, max_length=20)
    otp: str = Field(min_length=4, max_length=10)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str) -> str:
        phone = _normalize_phone(value)
        if not phone:
            raise ValueError("Invalid phone number")
        return phone


class MessageResponse(BaseModel):
    message: str


class LoginRequest(BaseModel):
    email: EmailStr | None = None
    phone_number: str | None = Field(default=None, max_length=20)
    password: str = Field(min_length=8, max_length=128)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str | None) -> str | None:
        return _normalize_phone(value)

    @model_validator(mode="after")
    def require_identifier(self) -> "LoginRequest":
        if not self.email and not self.phone_number:
            raise ValueError("Either email or phone_number is required")
        return self


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user_id: UUID
    account_status: str
    email_verified: bool
    phone_verified: bool
    journal_enabled: bool
    name: str | None = None
    email: str | None = None
    phone_number: str | None = None
    age: int | None = None
    gender: str | None = None


class ForgotPasswordRequest(BaseModel):
    email: EmailStr | None = None
    phone_number: str | None = Field(default=None, max_length=20)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str | None) -> str | None:
        return _normalize_phone(value)

    @model_validator(mode="after")
    def require_identifier(self) -> "ForgotPasswordRequest":
        if not self.email and not self.phone_number:
            raise ValueError("Either email or phone_number is required")
        return self


class ResetPasswordRequest(BaseModel):
    email: EmailStr | None = None
    phone_number: str | None = Field(default=None, max_length=20)
    otp: str = Field(min_length=4, max_length=10)
    new_password: str = Field(min_length=8, max_length=128)
    confirm_password: str = Field(min_length=8, max_length=128)

    @field_validator("phone_number")
    @classmethod
    def validate_phone(cls, value: str | None) -> str | None:
        return _normalize_phone(value)

    @model_validator(mode="after")
    def passwords_match(self) -> "ResetPasswordRequest":
        if not self.email and not self.phone_number:
            raise ValueError("Either email or phone_number is required")
        if self.new_password != self.confirm_password:
            raise ValueError("new_password and confirm_password do not match")
        return self


class UserPublic(BaseModel):
    user_id: UUID
    email: str | None
    phone_number: str | None
    name: str | None
    account_status: str
    email_verified: bool
    phone_verified: bool
    journal_enabled: bool

    model_config = ConfigDict(from_attributes=True)
