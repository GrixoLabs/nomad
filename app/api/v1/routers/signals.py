from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.signal import SignalCreateRequest, SignalCreateResponse
from app.services.signal_service import SignalService

router = APIRouter(
    prefix="/signals",
    tags=["Signals"],
)

service = SignalService()


@router.post(
    "",
    response_model=SignalCreateResponse,
    status_code=201,
)
def create_signal(
    request: SignalCreateRequest,
    http_request: Request,
    db: Session = Depends(get_db),
):
    client_ip = http_request.client.host if http_request.client else None
    forwarded = http_request.headers.get("x-forwarded-for")
    if forwarded:
        client_ip = forwarded.split(",")[0].strip()

    user_agent = http_request.headers.get("user-agent")

    signal = service.create_signal(
        db,
        request,
        client_ip=client_ip,
        user_agent=user_agent,
    )

    return SignalCreateResponse(
        signal_id=signal.signal_id,
        device_id=signal.device_id,
        message="Signal accepted",
    )
