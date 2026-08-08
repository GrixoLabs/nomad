from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.device import DeviceRegisterRequest, DeviceRegisterResponse
from app.services.device_service import DeviceService

router = APIRouter(
    prefix="/devices",
    tags=["Devices"],
)

service = DeviceService()


@router.post(
    "/register",
    response_model=DeviceRegisterResponse,
)
def register_device(
    request: DeviceRegisterRequest,
    db: Session = Depends(get_db),
):
    device = service.register_device(db, request)
    return DeviceRegisterResponse(
        device_id=device.device_id,
        message="Device registered successfully",
    )
