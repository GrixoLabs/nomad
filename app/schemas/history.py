from datetime import date, datetime
from uuid import UUID

from pydantic import BaseModel, Field


class JournalCreateRequest(BaseModel):
    device_uuid: UUID
    body: str = Field(min_length=1, max_length=500)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    place_label: str | None = Field(default=None, max_length=240)


class JournalEntryResponse(BaseModel):
    entry_id: int
    latitude: float
    longitude: float
    place_label: str | None = None
    body: str
    created_at: datetime


class TrackPoint(BaseModel):
    latitude: float
    longitude: float
    altitude_m: float | None = None
    captured_at: datetime
    speed_mps: float | None = None


class TrackSegment(BaseModel):
    """Navy = calm/idle travel; crimson = moving/travelling."""

    kind: str = Field(description="idle | travel")
    points: list[TrackPoint]


class NightStayResponse(BaseModel):
    night_stay_id: int
    stay_date: date
    latitude: float
    longitude: float
    started_at: datetime
    ended_at: datetime
    idle_hours: float
    weather_summary: str | None = None
    temperature_c: float | None = None


class HistoryResponse(BaseModel):
    days: int
    segments: list[TrackSegment] = Field(default_factory=list)
    journal_pins: list[JournalEntryResponse] = Field(default_factory=list)
    night_stays: list[NightStayResponse] = Field(default_factory=list)


class MapConfigResponse(BaseModel):
    tile_url_template: str
    attribution: str = "© Stadia Maps © OpenMapTiles © OpenStreetMap"
    style: str = "alidade_smooth"
    # MapLibre style JSON URL when STADIA_API is configured (preferred by Android).
    style_url: str | None = None
