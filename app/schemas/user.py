"""Legacy user schemas retained for reference. Prefer app.schemas.auth."""

from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class UserRegisterRequest(BaseModel):
    device_uuid: UUID | None = None
    name: str = Field(min_length=1, max_length=100)
    email: str | None = Field(default=None, max_length=255)
    phone: str | None = Field(default=None, max_length=30)
    age: int = Field(ge=13, le=120)
    gender: str = Field(min_length=1, max_length=30)

    @model_validator(mode="after")
    def require_email_or_phone(self) -> "UserRegisterRequest":
        if not self.email and not self.phone:
            raise ValueError("Either email or phone is required")
        return self


class UserRegisterResponse(BaseModel):
    user_id: UUID | None = None
    message: str = "Use /api/v1/auth/register"

    model_config = ConfigDict(from_attributes=True)
