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
from datetime import datetime, time, timezone
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


def cell_key(lat: float, lon: float) -> tuple[str, str]:
    """Stable dict key for 3-decimal cells (avoids float identity mismatches)."""
    return (f"{round_cell(lat):.{COORD_DECIMALS}f}", f"{round_cell(lon):.{COORD_DECIMALS}f}")


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

        cells = self._load_cells(db, device_id)

        last_signal_id = watermark.last_signal_id
        for sig in signals:
            lat = round_cell(sig.latitude)
            lon = round_cell(sig.longitude)
            key = cell_key(lat, lon)
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
                # Normalize stored coords to the canonical 3-decimal values.
                row.latitude = lat
                row.longitude = lon
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
                    if cell_key(prev_lat, prev_lon) == key:
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

        # Persist signal cells before journal recount so unique lookups see them.
        db.flush()
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

    def _load_cells(self, db: Session, device_id: int) -> dict[tuple[str, str], MapPlotter]:
        cells: dict[tuple[str, str], MapPlotter] = {}
        for row in db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all():
            key = cell_key(row.latitude, row.longitude)
            # If duplicates already exist from older buggy runs, keep the oldest plot_id.
            existing = cells.get(key)
            if existing is None or row.plot_id < existing.plot_id:
                cells[key] = row
        return cells

    def _refresh_journal_counts(self, db: Session, device_id: int) -> None:
        journals = list(
            db.scalars(select(JournalEntry).where(JournalEntry.device_id == device_id)).all()
        )
        counts: dict[tuple[str, str], int] = {}
        journals_by_cell: dict[tuple[str, str], list[JournalEntry]] = {}
        for entry in journals:
            key = cell_key(entry.latitude, entry.longitude)
            counts[key] = counts.get(key, 0) + 1
            journals_by_cell.setdefault(key, []).append(entry)

        cells = self._load_cells(db, device_id)

        # Reset then apply counts for known cells.
        for key, row in cells.items():
            row.latitude = round_cell(row.latitude)
            row.longitude = round_cell(row.longitude)
            row.journal_count = counts.get(key, 0)
            row.updated_at = datetime.now(timezone.utc)

        # Journals at cells with no signals yet → create a plot row.
        for key, count in counts.items():
            if key in cells:
                continue
            matching = journals_by_cell.get(key) or []
            if not matching:
                continue
            matching.sort(key=lambda j: j.created_at)
            lat = round_cell(matching[0].latitude)
            lon = round_cell(matching[0].longitude)
            first = matching[0].created_at
            last = matching[-1].created_at
            row = MapPlotter(
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
            db.add(row)
            cells[key] = row

    def _recompute_night_flags(self, db: Session, device_id: int) -> None:
        rows = list(db.scalars(select(MapPlotter).where(MapPlotter.device_id == device_id)).all())
        for row in rows:
            row.night_stayed = row.night_time_seconds >= MIN_NIGHT_SECONDS
