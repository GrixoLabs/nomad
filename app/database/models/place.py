from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    DateTime,
    Double,
    Identity,
    Index,
    SmallInteger,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class PlaceCache(Base):
    __tablename__ = "place_cache"
    __table_args__ = (
        Index("idx_place_cache_updated", "updated_at"),
        {"schema": "nomad"},
    )

    grid_key: Mapped[str] = mapped_column(String(32), primary_key=True)
    lat_center: Mapped[float] = mapped_column(Double, nullable=False)
    lon_center: Mapped[float] = mapped_column(Double, nullable=False)
    display_name: Mapped[str] = mapped_column(Text, nullable=False)
    city: Mapped[str | None] = mapped_column(String(120))
    region: Mapped[str | None] = mapped_column(String(120))
    country: Mapped[str | None] = mapped_column(String(120))
    raw_json: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )


class WeatherCache(Base):
    __tablename__ = "weather_cache"
    __table_args__ = (
        Index("idx_weather_cache_fetched", "fetched_at"),
        {"schema": "nomad"},
    )

    grid_key: Mapped[str] = mapped_column(String(32), primary_key=True)
    lat_center: Mapped[float] = mapped_column(Double, nullable=False)
    lon_center: Mapped[float] = mapped_column(Double, nullable=False)
    summary: Mapped[str] = mapped_column(String(160), nullable=False)
    temperature_c: Mapped[float | None] = mapped_column(Double)
    feels_like_c: Mapped[float | None] = mapped_column(Double)
    humidity_percent: Mapped[int | None] = mapped_column(SmallInteger)
    wind_speed_kmh: Mapped[float | None] = mapped_column(Double)
    weather_code: Mapped[int | None] = mapped_column(SmallInteger)
    raw_json: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    fetched_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )


class TouristSpot(Base):
    __tablename__ = "tourist_spots"
    __table_args__ = (
        UniqueConstraint("source", "source_id", name="uq_tourist_spots_source"),
        Index("idx_tourist_spots_grid", "grid_key"),
        Index("idx_tourist_spots_coords", "latitude", "longitude"),
        {"schema": "nomad"},
    )

    spot_id: Mapped[int] = mapped_column(
        BigInteger, Identity(always=True), primary_key=True
    )
    grid_key: Mapped[str] = mapped_column(String(32), nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    category: Mapped[str | None] = mapped_column(String(80))
    latitude: Mapped[float] = mapped_column(Double, nullable=False)
    longitude: Mapped[float] = mapped_column(Double, nullable=False)
    source: Mapped[str] = mapped_column(String(40), nullable=False, default="overpass")
    source_id: Mapped[str | None] = mapped_column(String(80))
    raw_json: Mapped[dict[str, Any] | None] = mapped_column(JSONB)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )
