from datetime import datetime, timezone

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.database.models.device_signal import DeviceSignal
from app.repositories.device_repository import DeviceRepository
from app.repositories.signal_repository import SignalRepository
from app.schemas.signal import SignalCreateRequest


class SignalService:
    def __init__(self) -> None:
        self.device_repository = DeviceRepository()
        self.signal_repository = SignalRepository()

    def create_signal(
        self,
        db: Session,
        request: SignalCreateRequest,
        client_ip: str | None = None,
        user_agent: str | None = None,
    ) -> DeviceSignal:
        device = self.device_repository.get_by_uuid(db, request.device_uuid)
        if device is None:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Device not registered. Call /devices/register first.",
            )

        gps_ts = request.gps_timestamp_utc
        if gps_ts.tzinfo is None:
            gps_ts = gps_ts.replace(tzinfo=timezone.utc)

        signal = DeviceSignal(
            device_id=device.device_id,
            gps_timestamp_utc=gps_ts,
            received_utc=datetime.now(timezone.utc),
            # Store full floating precision from the client/GPS (UI may show fewer decimals).
            latitude=float(request.latitude),
            longitude=float(request.longitude),
            accuracy_m=request.accuracy_m,
            altitude_m=request.altitude_m,
            speed_mps=request.speed_mps,
            bearing_deg=request.bearing_deg,
            battery_percent=request.battery_percent,
            charging=request.charging,
            battery_temperature=request.battery_temperature,
            network_type=request.network_type,
            wifi_enabled=request.wifi_enabled,
            bluetooth_enabled=request.bluetooth_enabled,
            screen_on=request.screen_on,
            power_save_mode=request.power_save_mode,
            client_ip=client_ip,
            user_agent=user_agent,
        )

        created = self.signal_repository.create(db, signal)
        self.device_repository.touch_last_seen(db, device)
        return created
