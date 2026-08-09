"""Night stays are derived from map_plotter (night_stayed / night_time_seconds).

Kept as a thin wrapper so existing scripts/timers keep working.
"""

from __future__ import annotations

import logging
from datetime import datetime

from sqlalchemy.orm import Session

from app.services.map_plotter_service import MapPlotterService

logger = logging.getLogger(__name__)


class NightStayService:
    def __init__(self) -> None:
        self.plotter = MapPlotterService()

    def compute_for_device(
        self,
        db: Session,
        device_id: int,
        since: datetime,
        tz_name: str = "Asia/Kolkata",
    ) -> int:
        """Sync map_plotter for the device; return count of night_stayed cells."""
        del since  # window handled by map_plotter incremental sync
        self.plotter.sync_device(db, device_id, tz_name=tz_name)
        from sqlalchemy import func, select

        from app.database.models.map_plotter import MapPlotter

        count = db.scalar(
            select(func.count())
            .select_from(MapPlotter)
            .where(
                MapPlotter.device_id == device_id,
                MapPlotter.night_stayed.is_(True),
            )
        )
        return int(count or 0)
