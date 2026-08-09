from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class SignalCreateRequest(BaseModel):
    """Matches Android SignalRequest payload."""

    device_uuid: UUID
    gps_timestamp_utc: datetime
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    accuracy_m: float | None = None
    altitude_m: float | None = None
    speed_mps: float | None = None
    bearing_deg: float | None = None
    battery_percent: int | None = Field(default=None, ge=0, le=100)
    charging: bool | None = None
    battery_temperature: float | None = None
    network_type: str | None = Field(default=None, max_length=30)
    wifi_enabled: bool | None = None
    bluetooth_enabled: bool | None = None
    screen_on: bool | None = None
    power_save_mode: bool | None = None


class SignalCreateResponse(BaseModel):
    signal_id: int
    device_id: int
    message: str = "Signal accepted"

    model_config = ConfigDict(from_attributes=True)
