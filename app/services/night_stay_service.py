"""Detect overnight idle stays: ≥6h idle between 22:00–08:00 local."""

from __future__ import annotations

import logging
from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.database.models.device_signal import DeviceSignal
from app.database.models.journal import NightStay
from app.database.models.place import WeatherCache
from app.services.place_service import PlaceService
from app.utils.geo import grid_key, haversine_m

logger = logging.getLogger(__name__)

IDLE_RADIUS_M = 250.0
MIN_IDLE_HOURS = 6.0
NIGHT_START = time(22, 0)
NIGHT_END = time(8, 0)


def round_coord_local(value: float) -> float:
    return round(float(value), 5)


class NightStayService:
    def __init__(self) -> None:
        self.places = PlaceService()

    def compute_for_device(
        self,
        db: Session,
        device_id: int,
        since: datetime,
        tz_name: str = "Asia/Kolkata",
    ) -> int:
        """Scan signals and upsert night stays. Returns number upserted."""
        try:
            tz = ZoneInfo(tz_name)
        except Exception:  # noqa: BLE001
            tz = timezone.utc

        signals = list(
            db.scalars(
                select(DeviceSignal)
                .where(
                    DeviceSignal.device_id == device_id,
                    DeviceSignal.gps_timestamp_utc >= since - timedelta(days=1),
                )
                .order_by(DeviceSignal.gps_timestamp_utc.asc())
            ).all()
        )
        if len(signals) < 2:
            return 0

        # Group by local "night key": the calendar date of the 22:00 start
        by_night: dict[date, list[DeviceSignal]] = {}
        for sig in signals:
            local = sig.gps_timestamp_utc.astimezone(tz)
            t = local.time()
            if t >= NIGHT_START:
                night_key = local.date()
            elif t < NIGHT_END:
                night_key = local.date() - timedelta(days=1)
            else:
                continue
            by_night.setdefault(night_key, []).append(sig)

        upserted = 0
        for night_key, night_sigs in by_night.items():
            stay = self._detect_idle_cluster(night_sigs)
            if not stay:
                continue
            lat, lon, started, ended, idle_hours = stay
            weather_summary = None
            temperature_c = None
            try:
                weather = self.places.get_weather(db, lat, lon)
                weather_summary = weather.summary
                temperature_c = weather.temperature_c
            except Exception:  # noqa: BLE001
                # Fall back to cache only
                cached = db.get(WeatherCache, grid_key(lat, lon))
                if cached:
                    weather_summary = cached.summary
                    temperature_c = cached.temperature_c

            stmt = (
                pg_insert(NightStay)
                .values(
                    device_id=device_id,
                    stay_date=night_key,
                    latitude=round_coord_local(lat),
                    longitude=round_coord_local(lon),
                    started_at=started,
                    ended_at=ended,
                    idle_hours=round(idle_hours, 2),
                    weather_summary=weather_summary,
                    temperature_c=temperature_c,
                )
                .on_conflict_do_update(
                    constraint="uq_night_stays_device_date",
                    set_={
                        "latitude": round_coord_local(lat),
                        "longitude": round_coord_local(lon),
                        "started_at": started,
                        "ended_at": ended,
                        "idle_hours": round(idle_hours, 2),
                        "weather_summary": weather_summary,
                        "temperature_c": temperature_c,
                    },
                )
            )
            db.execute(stmt)
            upserted += 1
        if upserted:
            db.commit()
        return upserted

    def _detect_idle_cluster(
        self, signals: list[DeviceSignal]
    ) -> tuple[float, float, datetime, datetime, float] | None:
        """Find longest cluster within IDLE_RADIUS_M; require ≥ MIN_IDLE_HOURS span."""
        best: tuple[float, float, datetime, datetime, float] | None = None
        n = len(signals)
        for i in range(n):
            cluster = [signals[i]]
            for j in range(i + 1, n):
                dist = haversine_m(
                    signals[i].latitude,
                    signals[i].longitude,
                    signals[j].latitude,
                    signals[j].longitude,
                )
                if dist <= IDLE_RADIUS_M:
                    cluster.append(signals[j])
                else:
                    # allow brief gaps but break if far
                    if (
                        signals[j].gps_timestamp_utc - cluster[-1].gps_timestamp_utc
                    ).total_seconds() > 45 * 60:
                        break
            started = cluster[0].gps_timestamp_utc
            ended = cluster[-1].gps_timestamp_utc
            idle_hours = (ended - started).total_seconds() / 3600.0
            if idle_hours < MIN_IDLE_HOURS:
                continue
            lat = sum(s.latitude for s in cluster) / len(cluster)
            lon = sum(s.longitude for s in cluster) / len(cluster)
            if best is None or idle_hours > best[4]:
                best = (lat, lon, started, ended, idle_hours)
        return best
