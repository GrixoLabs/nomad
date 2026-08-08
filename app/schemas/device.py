from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class DeviceRegisterRequest(BaseModel):
    device_uuid: UUID
    device_name: str = Field(min_length=1, max_length=100)
    manufacturer: str | None = Field(default=None, max_length=50)
    model: str | None = Field(default=None, max_length=100)
    android_version: str | None = Field(default=None, max_length=30)
    app_version: str | None = Field(default=None, max_length=30)


class DeviceRegisterResponse(BaseModel):
    device_id: int
    message: str

    model_config = ConfigDict(from_attributes=True)
