"""Travel history: track segments, journal pins, overnight stays."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database.models.device_signal import DeviceSignal
from app.database.models.journal import JournalEntry, NightStay
from app.repositories.device_repository import DeviceRepository
from app.schemas.history import (
    HistoryResponse,
    JournalCreateRequest,
    JournalEntryResponse,
    MapConfigResponse,
    NightStayResponse,
    TrackPoint,
    TrackSegment,
)
from app.security.journal_crypto import decrypt_journal, encrypt_journal
from app.services.night_stay_service import NightStayService
from app.utils.geo import haversine_m
from app.config.settings import get_settings

# Movement between consecutive samples above this → "travel" (crimson line)
TRAVEL_GAP_M = 180.0


def round_coord(value: float) -> float:
    """Keep full float precision in API/DB payloads (UI may display fewer decimals)."""
    return float(value)


class HistoryService:
    def __init__(self) -> None:
        self.devices = DeviceRepository()
        self.nights = NightStayService()

    def map_config(self) -> MapConfigResponse:
        settings = get_settings()
        key = settings.stadia_api
        if not key:
            raise HTTPException(
                status_code=503,
                detail="STADIA_API is not configured on the server",
            )
        style = settings.stadia_style or "alidade_smooth"
        template = (
            f"https://tiles.stadiamaps.com/tiles/{style}/{{z}}/{{x}}/{{y}}@2x.png"
            f"?api_key={key}"
        )
        return MapConfigResponse(tile_url_template=template, style=style)

    def create_journal(
        self, db: Session, request: JournalCreateRequest
    ) -> JournalEntryResponse:
        device = self.devices.get_by_uuid(db, request.device_uuid)
        if not device:
            raise HTTPException(status_code=404, detail="Device not registered")

        body = request.body.strip()
        if not body:
            raise HTTPException(status_code=400, detail="Journal body required")
        if len(body) > 500:
            raise HTTPException(status_code=400, detail="Journal body max 500 characters")

        ct, nonce = encrypt_journal(body)
        entry = JournalEntry(
            device_id=device.device_id,
            latitude=float(request.latitude),
            longitude=float(request.longitude),
            place_label=request.place_label,
            body_ciphertext=ct,
            body_nonce=nonce,
            created_at=datetime.now(timezone.utc),
        )
        db.add(entry)
        db.commit()
        db.refresh(entry)
        return JournalEntryResponse(
            entry_id=entry.entry_id,
            latitude=entry.latitude,
            longitude=entry.longitude,
            place_label=entry.place_label,
            body=body,
            created_at=entry.created_at,
        )

    def get_history(
        self, db: Session, device_uuid: UUID, days: int
    ) -> HistoryResponse:
        days = max(1, min(int(days), 14))
        device = self.devices.get_by_uuid(db, device_uuid)
        if not device:
            raise HTTPException(status_code=404, detail="Device not registered")

        since = datetime.now(timezone.utc) - timedelta(days=days)

        # Refresh night stays for the window before responding
        self.nights.compute_for_device(db, device.device_id, since)

        signals = list(
            db.scalars(
                select(DeviceSignal)
                .where(
                    DeviceSignal.device_id == device.device_id,
                    DeviceSignal.gps_timestamp_utc >= since,
                )
                .order_by(DeviceSignal.gps_timestamp_utc.asc())
            ).all()
        )
        segments = self._build_segments(signals)

        journal_rows = list(
            db.scalars(
                select(JournalEntry)
                .where(
                    JournalEntry.device_id == device.device_id,
                    JournalEntry.created_at >= since,
                )
                .order_by(JournalEntry.created_at.desc())
            ).all()
        )
        pins: list[JournalEntryResponse] = []
        for row in journal_rows:
            try:
                body = decrypt_journal(row.body_ciphertext, row.body_nonce)
            except Exception:  # noqa: BLE001
                body = "[unavailable]"
            pins.append(
                JournalEntryResponse(
                    entry_id=row.entry_id,
                    latitude=row.latitude,
                    longitude=row.longitude,
                    place_label=row.place_label,
                    body=body,
                    created_at=row.created_at,
                )
            )

        night_rows = list(
            db.scalars(
                select(NightStay)
                .where(
                    NightStay.device_id == device.device_id,
                    NightStay.started_at >= since,
                )
                .order_by(NightStay.stay_date.desc())
            ).all()
        )
        nights = [
            NightStayResponse(
                night_stay_id=n.night_stay_id,
                stay_date=n.stay_date,
                latitude=n.latitude,
                longitude=n.longitude,
                started_at=n.started_at,
                ended_at=n.ended_at,
                idle_hours=n.idle_hours,
                weather_summary=n.weather_summary,
                temperature_c=n.temperature_c,
            )
            for n in night_rows
        ]

        return HistoryResponse(
            days=days,
            segments=segments,
            journal_pins=pins,
            night_stays=nights,
        )

    def _build_segments(self, signals: list[DeviceSignal]) -> list[TrackSegment]:
        if not signals:
            return []

        segments: list[TrackSegment] = []
        current_kind: str | None = None
        current_points: list[TrackPoint] = []

        def flush() -> None:
            nonlocal current_kind, current_points
            if current_kind and len(current_points) >= 2:
                segments.append(TrackSegment(kind=current_kind, points=list(current_points)))
            elif current_kind and current_points:
                # Keep singleton so map still shows a point
                segments.append(TrackSegment(kind=current_kind, points=list(current_points)))
            current_points = []
            current_kind = None

        prev = signals[0]
        current_kind = "idle"
        current_points = [
            TrackPoint(
                latitude=round_coord(prev.latitude),
                longitude=round_coord(prev.longitude),
                altitude_m=prev.altitude_m,
                captured_at=prev.gps_timestamp_utc,
                speed_mps=prev.speed_mps,
            )
        ]

        for sig in signals[1:]:
            dist = haversine_m(prev.latitude, prev.longitude, sig.latitude, sig.longitude)
            speed = sig.speed_mps or 0.0
            kind = "travel" if dist >= TRAVEL_GAP_M or speed >= 1.5 else "idle"
            point = TrackPoint(
                latitude=round_coord(sig.latitude),
                longitude=round_coord(sig.longitude),
                altitude_m=sig.altitude_m,
                captured_at=sig.gps_timestamp_utc,
                speed_mps=sig.speed_mps,
            )
            if kind != current_kind:
                # bridge with previous point so line is continuous
                if current_points:
                    flush()
                current_kind = kind
                current_points = [
                    TrackPoint(
                        latitude=round_coord(prev.latitude),
                        longitude=round_coord(prev.longitude),
                        altitude_m=prev.altitude_m,
                        captured_at=prev.gps_timestamp_utc,
                        speed_mps=prev.speed_mps,
                    ),
                    point,
                ]
            else:
                current_points.append(point)
            prev = sig

        flush()
        return segments
