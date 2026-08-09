from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.history import (
    HistoryResponse,
    JournalCreateRequest,
    JournalEntryResponse,
    MapConfigResponse,
)
from app.services.history_service import HistoryService

router = APIRouter(tags=["History"])
service = HistoryService()


@router.get("/map/config", response_model=MapConfigResponse)
def map_config():
    return service.map_config()


@router.post("/journal", response_model=JournalEntryResponse, status_code=201)
def create_journal(request: JournalCreateRequest, db: Session = Depends(get_db)):
    return service.create_journal(db, request)


@router.get("/history", response_model=HistoryResponse)
def get_history(
    device_uuid: UUID = Query(...),
    days: int = Query(7, ge=1, le=90),
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
    db: Session = Depends(get_db),
):
    try:
        return service.get_history(
            db, device_uuid, days, start_date=start_date, end_date=end_date
        )
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"History failed: {exc}") from exc
