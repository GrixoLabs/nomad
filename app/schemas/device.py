from uuid import UUID

from pydantic import BaseModel, ConfigDict


class DeviceRegisterRequest(BaseModel):
    device_uuid: UUID
    device_name: str
    manufacturer: str | None = None
    model: str | None = None
    android_version: str | None = None
    app_version: str | None = None


class DeviceRegisterResponse(BaseModel):
    device_id: int
    message: str

    model_config = ConfigDict(from_attributes=True)
