from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.database.models.device import Device
from app.repositories.device_repository import DeviceRepository
from app.schemas.device import DeviceRegisterRequest


class DeviceService:

    def __init__(self):

        self.repository = DeviceRepository()

    def register_device(
        self,
        db: Session,
        request: DeviceRegisterRequest,
    ) -> Device:

        device = self.repository.get_by_uuid(
            db,
            request.device_uuid,
        )

        if device:

            device.device_name = request.device_name
            device.manufacturer = request.manufacturer
            device.model = request.model
            device.android_version = request.android_version
            device.app_version = request.app_version

            return self.repository.update(
                db,
                device,
            )

        device = Device(

            device_uuid=request.device_uuid,

            device_name=request.device_name,

            manufacturer=request.manufacturer,

            model=request.model,

            android_version=request.android_version,

            app_version=request.app_version,

            first_seen_utc=datetime.now(timezone.utc),

            last_seen_utc=datetime.now(timezone.utc),

            is_active=True,
        )

        return self.repository.create(
            db,
            device,
        )
