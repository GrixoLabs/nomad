from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.user import UserRegisterRequest, UserRegisterResponse
from app.services.user_service import UserService

router = APIRouter(
    prefix="/users",
    tags=["Users"],
)

service = UserService()


@router.post(
    "/register",
    response_model=UserRegisterResponse,
    status_code=201,
)
def register_user(
    request: UserRegisterRequest,
    db: Session = Depends(get_db),
):
    user = service.register_user(db, request)
    return UserRegisterResponse(
        user_id=user.user_id,
        journal_enabled=user.journal_enabled,
        message="User registered successfully",
    )
