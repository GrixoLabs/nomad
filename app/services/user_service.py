from datetime import datetime, timezone

from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from app.database.models.user import User
from app.repositories.device_repository import DeviceRepository
from app.repositories.user_repository import UserRepository
from app.schemas.user import UserRegisterRequest


class UserService:
    def __init__(self) -> None:
        self.user_repository = UserRepository()
        self.device_repository = DeviceRepository()

    def register_user(self, db: Session, request: UserRegisterRequest) -> User:
        if request.device_uuid is not None:
            device = self.device_repository.get_by_uuid(db, request.device_uuid)
            if device is None:
                raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail="Device not registered. Call /devices/register first.",
                )

        existing = self.user_repository.get_by_email_or_phone(
            db,
            email=request.email,
            phone=request.phone,
        )
        now = datetime.now(timezone.utc)

        if existing:
            existing.name = request.name
            existing.age = request.age
            existing.gender = request.gender
            existing.journal_enabled = True
            if request.email:
                existing.email = request.email
            if request.phone:
                existing.phone = request.phone
            if request.device_uuid is not None:
                existing.device_uuid = request.device_uuid
            existing.updated_utc = now
            return self.user_repository.update(db, existing)

        user = User(
            device_uuid=request.device_uuid,
            name=request.name,
            email=request.email,
            phone=request.phone,
            age=request.age,
            gender=request.gender,
            journal_enabled=True,
            created_utc=now,
            updated_utc=now,
        )
        return self.user_repository.create(db, user)
