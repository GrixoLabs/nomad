from sqlalchemy.orm import Session

from app.database.models.device_signal import DeviceSignal


class SignalRepository:
    def create(self, db: Session, signal: DeviceSignal) -> DeviceSignal:
        db.add(signal)
        db.commit()
        db.refresh(signal)
        return signal
