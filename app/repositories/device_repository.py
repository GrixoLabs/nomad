from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.database.models.device import Device


class DeviceRepository:

    def get_by_uuid(
        self,
        db: Session,
        device_uuid,
    ) -> Device | None:

        return (
            db.query(Device)
            .filter(Device.device_uuid == device_uuid)
            .first()
        )

    def create(
        self,
        db: Session,
        device: Device,
    ) -> Device:

        db.add(device)
        db.commit()
        db.refresh(device)

        return device

    def update(
        self,
        db: Session,
        device: Device,
    ) -> Device:

        device.last_seen_utc = datetime.now(timezone.utc)

        db.commit()
        db.refresh(device)

        return device
