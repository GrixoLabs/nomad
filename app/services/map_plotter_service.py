"""Build and maintain nomad.map_plotter from device_signals + journal_entries.

Pipeline:
1. Register device in map_plotter_devices (skip insert if already present).
2. Scan new device_signals (incremental via last_signal_id).
3. Round lat/lon to 3 decimals → upsert cell; accumulate total_time_at_location.
4. Accumulate night-window time (22:00–06:00 local); night_stayed if > 6h.
5. Recount journals at the same 3-decimal cell into journal_count.
"""

from __future__ import annotations

import logging
from datetime import datetime, time, timedelta, timezone
from uuid import UUID
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database.models.device import Device
from app.database.models.device_signal import DeviceSignal
from app.database.models.journal import JournalEntry
from app.database.models.map_plotter import MapPlotter, MapPlotterDevice
from app.database.models.user import User

logger = logging.getLogger(__name__)

COORD_DECIMALS = 3
MAX_GAP_SECONDS = 2 * 60 * 60  # ignore gaps > 2h when accumulating dwell time
NIGHT_START = time(22, 0)
NIGHT_END = time(6, 0)
MIN_NIGHT_SECONDS = 6 * 60 * 60
MIN_PLOT_SECONDS = 30 * 60  # dwell threshold for history plot points


def round_cell(value: float) -> float:
    return round(float(value), COORD_DECIMALS)


def is_night_local(ts: datetime, tz: ZoneInfo) -> bool:
    local = ts.astimezone(tz).time()
    return local >= NIGHT_START or local < NIGHT_END


class MapPlotterService:
    def sync_all_devices(self, db: Session, tz_name: str = "Asia/Kolkata") -> int:
        devices = list(db.scalars(select(Device).where(Device.is_active.is_(True))).all())
        total = 0
        for device in devices:
            total += self.sync_device(db, device.device_id, tz_name=tz_name)
        return total

    def sync_device(
        self,
        db: Session,
        device_id: int,
        tz_name: str = "Asia/Kolkata",
        force_full: bool = False,
    ) -> int:
        try:
            tz = ZoneInfo(tz_name)
        except Exception:  # noqa: BLE001
            tz = timezone.utc

        device = db.get(Device, device_id)
        if device is None:
            return 0

        user_id = self._resolve_user_id(db, device.device_uuid)
        watermark = db.get(MapPlotterDevice, device_id)
        if watermark is None:
            watermark = MapPlotterDevice(device_id=device_id, last_signal_id=None)
            db.add(watermark)
            db.flush()
            # New device → full backfill.
            force_full = True
        # Existing watermark row: skip full rebuild unless forced; still scan new signals.

        q = select(DeviceSignal).where(DeviceSignal.device_id == device_id)
        if not force_full and watermark.last_signal_id is not None:
            q = q.where(DeviceSignal.signal_id > watermark.last_signal_id)
        q = q.order_by(
            DeviceSignal.gps_timestamp_utc.asc(),
            DeviceSignal.signal_id.asc(),
        )
        signals = list(db.scalars(q).all())

        if not signals and not force_full:
            self._refresh_journal_counts(db, device_id)
            self._recompute_night_flags(db, device_id)
            db.commit()
            return 0

        # Need previous signal context for dwell deltas when incremental.
        prev_sig: DeviceSignal | None = None
        if not force_full and watermark.last_signal_id is not None:
            prev_sig = db.scalar(
                select(DeviceSignal)
                .where(
                    DeviceSignal.device_id == device_id,
                    DeviceSignal.signal_id == watermark.last_signal_id,
                )
            )

        if force_full:
            # Clear stale cells for a clean rebuild of this device.
            existing = list(
                db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all()
            )
            for row in existing:
                db.delete(row)
            db.flush()
            prev_sig = None

        cells: dict[tuple[float, float], MapPlotter] = {}
        for row in db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all():
            cells[(row.latitude, row.longitude)] = row

        last_signal_id = watermark.last_signal_id
        for sig in signals:
            lat = round_cell(sig.latitude)
            lon = round_cell(sig.longitude)
            key = (lat, lon)
            ts = sig.gps_timestamp_utc
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=timezone.utc)

            row = cells.get(key)
            if row is None:
                row = MapPlotter(
                    device_id=device_id,
                    user_id=user_id,
                    latitude=lat,
                    longitude=lon,
                    first_gps_timestamp=ts,
                    last_gps_timestamp=ts,
                    total_time_at_location_seconds=0.0,
                    night_time_seconds=0.0,
                    visit_count=1,
                    signal_count=1,
                    journal_count=0,
                    night_stayed=False,
                )
                db.add(row)
                cells[key] = row
            else:
                row.signal_count += 1
                if ts < row.first_gps_timestamp:
                    row.first_gps_timestamp = ts
                if ts > row.last_gps_timestamp:
                    row.last_gps_timestamp = ts
                row.updated_at = datetime.now(timezone.utc)
                if user_id and row.user_id is None:
                    row.user_id = user_id

            if prev_sig is not None:
                prev_lat = round_cell(prev_sig.latitude)
                prev_lon = round_cell(prev_sig.longitude)
                prev_ts = prev_sig.gps_timestamp_utc
                if prev_ts.tzinfo is None:
                    prev_ts = prev_ts.replace(tzinfo=timezone.utc)
                gap = (ts - prev_ts).total_seconds()
                if 0 < gap <= MAX_GAP_SECONDS:
                    if prev_lat == lat and prev_lon == lon:
                        row.total_time_at_location_seconds += gap
                        if is_night_local(prev_ts, tz) and is_night_local(ts, tz):
                            row.night_time_seconds += gap
                    else:
                        # Left a cell and arrived at a new one → new visit on the new cell.
                        row.visit_count += 1

            row.night_stayed = row.night_time_seconds >= MIN_NIGHT_SECONDS
            prev_sig = sig
            last_signal_id = sig.signal_id

        if last_signal_id is not None:
            watermark.last_signal_id = last_signal_id
        watermark.last_synced_at = datetime.now(timezone.utc)

        self._refresh_journal_counts(db, device_id)
        self._recompute_night_flags(db, device_id)
        db.commit()
        return len(signals)

    def sync_device_by_uuid(
        self, db: Session, device_uuid: UUID, tz_name: str = "Asia/Kolkata"
    ) -> int:
        device = db.scalar(select(Device).where(Device.device_uuid == device_uuid))
        if device is None:
            return 0
        return self.sync_device(db, device.device_id, tz_name=tz_name)

    def on_new_signal(self, db: Session, signal: DeviceSignal) -> None:
        """Lightweight incremental update after a signal insert."""
        try:
            self.sync_device(db, signal.device_id)
        except Exception:  # noqa: BLE001
            logger.exception("map_plotter sync failed for device_id=%s", signal.device_id)
            db.rollback()

    def plots_for_device(
        self,
        db: Session,
        device_id: int,
        since: datetime | None = None,
    ) -> list[MapPlotter]:
        q = select(MapPlotter).where(MapPlotter.device_id == device_id)
        if since is not None:
            q = q.where(MapPlotter.last_gps_timestamp >= since)
        q = q.order_by(MapPlotter.last_gps_timestamp.asc())
        return list(db.scalars(q).all())

    def _resolve_user_id(self, db: Session, device_uuid: UUID) -> UUID | None:
        user = db.scalar(select(User).where(User.device_uuid == device_uuid))
        return user.user_id if user else None

    def _refresh_journal_counts(self, db: Session, device_id: int) -> None:
        journals = list(
            db.scalars(select(JournalEntry).where(JournalEntry.device_id == device_id)).all()
        )
        counts: dict[tuple[float, float], int] = {}
        for entry in journals:
            key = (round_cell(entry.latitude), round_cell(entry.longitude))
            counts[key] = counts.get(key, 0) + 1

        rows = list(db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all())
        for row in rows:
            row.journal_count = counts.get((row.latitude, row.longitude), 0)
            row.updated_at = datetime.now(timezone.utc)

        # Journals at cells with no signals yet → create a plot row.
        existing = {(r.latitude, r.longitude) for r in rows}
        for (lat, lon), count in counts.items():
            if (lat, lon) in existing:
                continue
            # Use earliest journal time as gps timestamps.
            matching = [
                j
                for j in journals
                if round_cell(j.latitude) == lat and round_cell(j.longitude) == lon
            ]
            if not matching:
                continue
            matching.sort(key=lambda j: j.created_at)
            first = matching[0].created_at
            last = matching[-1].created_at
            db.add(
                MapPlotter(
                    device_id=device_id,
                    latitude=lat,
                    longitude=lon,
                    first_gps_timestamp=first,
                    last_gps_timestamp=last,
                    total_time_at_location_seconds=0.0,
                    night_time_seconds=0.0,
                    visit_count=1,
                    signal_count=0,
                    journal_count=count,
                    night_stayed=False,
                    place_label=matching[-1].place_label,
                )
            )

    def _recompute_night_flags(self, db: Session, device_id: int) -> None:
        rows = list(db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all())
        for row in rows:
            row.night_stayed = row.night_time_seconds >= MIN_NIGHT_SECONDS
