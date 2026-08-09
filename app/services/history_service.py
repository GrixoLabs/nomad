"""Travel history built from nomad.map_plotter (plus Stadia map config / journals)."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import UUID

from fastapi import HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config.settings import get_settings
from app.database.models.journal import JournalEntry
from app.repositories.device_repository import DeviceRepository
from app.schemas.history import (
    HistoryResponse,
    JournalCreateRequest,
    JournalEntryResponse,
    MapConfigResponse,
    NightStayResponse,
    PlotPointResponse,
    TrackPoint,
    TrackSegment,
)
from app.security.journal_crypto import decrypt_journal, encrypt_journal
from app.services.map_plotter_service import MIN_PLOT_SECONDS, MapPlotterService, round_cell


class HistoryService:
    def __init__(self) -> None:
        self.devices = DeviceRepository()
        self.plotter = MapPlotterService()

    def map_config(self) -> MapConfigResponse:
        settings = get_settings()
        key = (settings.stadia_api or "").strip()
        if key:
            style = settings.stadia_style or "alidade_smooth"
            template = (
                f"https://tiles.stadiamaps.com/tiles/{style}/{{z}}/{{x}}/{{y}}.png"
                f"?api_key={key}"
            )
            style_url = (
                f"https://tiles.stadiamaps.com/styles/{style}.json?api_key={key}"
            )
            return MapConfigResponse(
                tile_url_template=template,
                style=style,
                style_url=style_url,
                attribution="© Stadia Maps © OpenMapTiles © OpenStreetMap",
            )
        return MapConfigResponse(
            tile_url_template="https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            style="osm",
            style_url=None,
            attribution="© OpenStreetMap contributors",
        )

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

        # Keep map_plotter journal_count in sync.
        try:
            self.plotter.sync_device(db, device.device_id)
        except Exception:  # noqa: BLE001
            pass

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

        # Refresh map_plotter from new signals / journals.
        self.plotter.sync_device(db, device.device_id)

        since = datetime.now(timezone.utc) - timedelta(days=days)
        plots = self.plotter.plots_for_device(db, device.device_id, since=since)

        journals = list(
            db.scalars(
                select(JournalEntry)
                .where(
                    JournalEntry.device_id == device.device_id,
                    JournalEntry.created_at >= since,
                )
                .order_by(JournalEntry.created_at.asc())
            ).all()
        )
        journals_by_cell: dict[tuple[float, float], list[JournalEntryResponse]] = {}
        journal_pins: list[JournalEntryResponse] = []
        for row in journals:
            try:
                body = decrypt_journal(row.body_ciphertext, row.body_nonce)
            except Exception:  # noqa: BLE001
                body = "[unavailable]"
            item = JournalEntryResponse(
                entry_id=row.entry_id,
                latitude=row.latitude,
                longitude=row.longitude,
                place_label=row.place_label,
                body=body,
                created_at=row.created_at,
            )
            journal_pins.append(item)
            key = (round_cell(row.latitude), round_cell(row.longitude))
            journals_by_cell.setdefault(key, []).append(item)

        # Plot points = cells with meaningful dwell (> 30 minutes).
        plot_points: list[PlotPointResponse] = []
        night_stays: list[NightStayResponse] = []
        for plot in plots:
            if plot.total_time_at_location_seconds < MIN_PLOT_SECONDS:
                continue
            key = (plot.latitude, plot.longitude)
            cell_journals = journals_by_cell.get(key, [])
            plot_points.append(
                PlotPointResponse(
                    plot_id=plot.plot_id,
                    latitude=plot.latitude,
                    longitude=plot.longitude,
                    first_gps_timestamp=plot.first_gps_timestamp,
                    last_gps_timestamp=plot.last_gps_timestamp,
                    total_time_hours=round(plot.total_time_at_location_seconds / 3600.0, 2),
                    visit_count=plot.visit_count,
                    journal_count=plot.journal_count,
                    night_stayed=plot.night_stayed,
                    place_label=plot.place_label,
                    journals=cell_journals,
                )
            )
            if plot.night_stayed:
                night_stays.append(
                    NightStayResponse(
                        night_stay_id=plot.plot_id,
                        stay_date=plot.first_gps_timestamp.date(),
                        latitude=plot.latitude,
                        longitude=plot.longitude,
                        started_at=plot.first_gps_timestamp,
                        ended_at=plot.last_gps_timestamp,
                        idle_hours=round(plot.night_time_seconds / 3600.0, 2),
                        weather_summary=None,
                        temperature_c=None,
                    )
                )

        segments = self._segments_from_plots(plot_points)

        return HistoryResponse(
            days=days,
            plot_points=plot_points,
            segments=segments,
            journal_pins=journal_pins,
            night_stays=night_stays,
        )

    def _segments_from_plots(
        self, plots: list[PlotPointResponse]
    ) -> list[TrackSegment]:
        """Connect plot cells in time order for a simple path on the map."""
        if len(plots) < 2:
            if len(plots) == 1:
                p = plots[0]
                return [
                    TrackSegment(
                        kind="idle",
                        points=[
                            TrackPoint(
                                latitude=p.latitude,
                                longitude=p.longitude,
                                captured_at=p.last_gps_timestamp,
                            )
                        ],
                    )
                ]
            return []

        ordered = sorted(plots, key=lambda p: p.last_gps_timestamp)
        points = [
            TrackPoint(
                latitude=p.latitude,
                longitude=p.longitude,
                captured_at=p.last_gps_timestamp,
            )
            for p in ordered
        ]
        # Night cells get idle styling; otherwise travel between distinct cells.
        kind = "idle" if any(p.night_stayed for p in ordered) else "travel"
        return [TrackSegment(kind=kind, points=points)]
