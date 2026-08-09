from datetime import datetime, timezone
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database.models.device import Device


class DeviceRepository:
    def get_by_uuid(self, db: Session, device_uuid: UUID) -> Device | None:
        return db.scalar(select(Device).where(Device.device_uuid == device_uuid))

    def get_by_id(self, db: Session, device_id: int) -> Device | None:
        return db.get(Device, device_id)

    def create(self, db: Session, device: Device) -> Device:
        db.add(device)
        db.commit()
        db.refresh(device)
        return device

    def update(self, db: Session, device: Device) -> Device:
        device.last_seen_utc = datetime.now(timezone.utc)
        db.commit()
        db.refresh(device)
        return device

    def touch_last_seen(self, db: Session, device: Device) -> Device:
        device.last_seen_utc = datetime.now(timezone.utc)
        db.commit()
        db.refresh(device)
        return device
