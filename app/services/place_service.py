"""Place resolve (Nominatim), weather (Open-Meteo), nearby spots (Overpass)."""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Any

import httpx
from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.config.settings import Settings, get_settings
from app.database.models.place import PlaceCache, TouristSpot, WeatherCache
from app.schemas.place import (
    NearbyPlace,
    NearbyPlacesResponse,
    PlaceResolveResponse,
    WeatherResponse,
)
from app.utils.geo import grid_center, grid_key, haversine_m

logger = logging.getLogger(__name__)

WMO_SUMMARY = {
    0: "Clear",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Depositing rime fog",
    51: "Light drizzle",
    53: "Drizzle",
    55: "Heavy drizzle",
    61: "Light rain",
    63: "Rain",
    65: "Heavy rain",
    71: "Light snow",
    73: "Snow",
    75: "Heavy snow",
    80: "Rain showers",
    81: "Rain showers",
    82: "Violent rain showers",
    95: "Thunderstorm",
    96: "Thunderstorm with hail",
    99: "Thunderstorm with hail",
}


class PlaceService:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    def resolve_place(self, db: Session, lat: float, lon: float) -> PlaceResolveResponse:
        key = grid_key(lat, lon)
        existing = db.get(PlaceCache, key)
        if existing:
            return PlaceResolveResponse(
                display_name=existing.display_name,
                city=existing.city,
                region=existing.region,
                country=existing.country,
                grid_key=key,
                cached=True,
            )

        lat_c, lon_c = grid_center(key)
        payload = self._fetch_nominatim(lat, lon)
        address = payload.get("address") or {}
        city = (
            address.get("city")
            or address.get("town")
            or address.get("village")
            or address.get("municipality")
            or address.get("suburb")
        )
        region = address.get("state") or address.get("region") or address.get("county")
        country = address.get("country")
        display = payload.get("display_name") or ", ".join(
            x for x in (city, region, country) if x
        ) or f"Near {lat_c:.2f}, {lon_c:.2f}"

        row = PlaceCache(
            grid_key=key,
            lat_center=lat_c,
            lon_center=lon_c,
            display_name=display,
            city=city,
            region=region,
            country=country,
            raw_json=payload,
            updated_at=datetime.now(timezone.utc),
        )
        db.merge(row)
        db.commit()
        return PlaceResolveResponse(
            display_name=display,
            city=city,
            region=region,
            country=country,
            grid_key=key,
            cached=False,
        )

    def get_weather(self, db: Session, lat: float, lon: float) -> WeatherResponse:
        key = grid_key(lat, lon)
        existing = db.get(WeatherCache, key)
        if existing:
            return WeatherResponse(
                summary=existing.summary,
                temperature_c=existing.temperature_c,
                feels_like_c=existing.feels_like_c,
                humidity_percent=existing.humidity_percent,
                wind_speed_kmh=existing.wind_speed_kmh,
                weather_code=existing.weather_code,
                cached=True,
            )

        lat_c, lon_c = grid_center(key)
        payload = self._fetch_open_meteo(lat, lon)
        current = payload.get("current") or {}
        code = current.get("weather_code")
        summary = WMO_SUMMARY.get(int(code), "Weather") if code is not None else "Weather"
        temp = current.get("temperature_2m")
        feels = current.get("apparent_temperature")
        humidity = current.get("relative_humidity_2m")
        wind = current.get("wind_speed_10m")

        row = WeatherCache(
            grid_key=key,
            lat_center=lat_c,
            lon_center=lon_c,
            summary=summary,
            temperature_c=temp,
            feels_like_c=feels,
            humidity_percent=int(humidity) if humidity is not None else None,
            wind_speed_kmh=wind,
            weather_code=int(code) if code is not None else None,
            raw_json=payload,
            fetched_at=datetime.now(timezone.utc),
        )
        db.merge(row)
        db.commit()
        return WeatherResponse(
            summary=summary,
            temperature_c=temp,
            feels_like_c=feels,
            humidity_percent=int(humidity) if humidity is not None else None,
            wind_speed_kmh=wind,
            weather_code=int(code) if code is not None else None,
            cached=False,
        )

    def nearby_places(
        self, db: Session, lat: float, lon: float, limit: int = 10
    ) -> NearbyPlacesResponse:
        key = grid_key(lat, lon)
        cached_rows = list(
            db.scalars(select(TouristSpot).where(TouristSpot.grid_key == key)).all()
        )
        from_cache = bool(cached_rows)

        if not cached_rows:
            fetched = self._fetch_overpass(lat, lon)
            now = datetime.now(timezone.utc)
            for item in fetched:
                stmt = (
                    pg_insert(TouristSpot)
                    .values(
                        grid_key=key,
                        name=item["name"][:200],
                        category=item.get("category"),
                        latitude=item["latitude"],
                        longitude=item["longitude"],
                        source=item["source"],
                        source_id=item["source_id"],
                        raw_json=item.get("raw"),
                        created_at=now,
                    )
                    .on_conflict_do_nothing(constraint="uq_tourist_spots_source")
                )
                db.execute(stmt)
            db.commit()
            cached_rows = list(
                db.scalars(select(TouristSpot).where(TouristSpot.grid_key == key)).all()
            )

        ranked: list[NearbyPlace] = []
        for row in cached_rows:
            dist = haversine_m(lat, lon, row.latitude, row.longitude)
            ranked.append(
                NearbyPlace(
                    name=row.name,
                    category=row.category,
                    latitude=row.latitude,
                    longitude=row.longitude,
                    distance_m=round(dist, 1),
                )
            )
        ranked.sort(key=lambda p: p.distance_m if p.distance_m is not None else 1e12)
        return NearbyPlacesResponse(places=ranked[: max(1, min(limit, 25))], cached=from_cache)

    def _client(self) -> httpx.Client:
        timeout = httpx.Timeout(
            self.settings.external_http_timeout_seconds,
            connect=10.0,
        )
        return httpx.Client(timeout=timeout, follow_redirects=True)

    def _fetch_nominatim(self, lat: float, lon: float) -> dict[str, Any]:
        headers = {
            "User-Agent": self.settings.nominatim_user_agent,
            "Accept": "application/json",
        }
        params = {
            "lat": lat,
            "lon": lon,
            "format": "json",
            "addressdetails": 1,
            "zoom": 10,
        }
        with self._client() as client:
            resp = client.get(self.settings.nominatim_url, params=params, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            if not isinstance(data, dict):
                raise ValueError("Unexpected Nominatim response")
            return data

    def _fetch_open_meteo(self, lat: float, lon: float) -> dict[str, Any]:
        params = {
            "latitude": lat,
            "longitude": lon,
            "current": "temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m",
            "wind_speed_unit": "kmh",
            "timezone": "UTC",
        }
        with self._client() as client:
            resp = client.get(self.settings.open_meteo_url, params=params)
            resp.raise_for_status()
            data = resp.json()
            if not isinstance(data, dict):
                raise ValueError("Unexpected Open-Meteo response")
            return data

    def _fetch_overpass(self, lat: float, lon: float) -> list[dict[str, Any]]:
        # Tourism / amenity attractions within ~20 km.
        radius_m = 20_000
        query = f"""
        [out:json][timeout:25];
        (
          node["tourism"~"attraction|museum|viewpoint|gallery|zoo|theme_park|artwork"](around:{radius_m},{lat},{lon});
          way["tourism"~"attraction|museum|viewpoint|gallery|zoo|theme_park"](around:{radius_m},{lat},{lon});
          node["historic"](around:{radius_m},{lat},{lon});
          node["amenity"~"place_of_worship|theatre|cinema"](around:{radius_m},{lat},{lon});
        );
        out center 40;
        """
        with self._client() as client:
            resp = client.post(
                self.settings.overpass_url,
                data={"data": query},
                headers={"User-Agent": self.settings.nominatim_user_agent},
            )
            resp.raise_for_status()
            payload = resp.json()

        results: list[dict[str, Any]] = []
        for el in payload.get("elements") or []:
            tags = el.get("tags") or {}
            name = tags.get("name")
            if not name:
                continue
            if "lat" in el and "lon" in el:
                elat, elon = float(el["lat"]), float(el["lon"])
            else:
                center = el.get("center") or {}
                if "lat" not in center or "lon" not in center:
                    continue
                elat, elon = float(center["lat"]), float(center["lon"])
            category = (
                tags.get("tourism")
                or tags.get("historic")
                or tags.get("amenity")
                or "place"
            )
            source_id = f"{el.get('type', 'n')}/{el.get('id')}"
            results.append(
                {
                    "name": name,
                    "category": str(category)[:80],
                    "latitude": elat,
                    "longitude": elon,
                    "source": "overpass",
                    "source_id": source_id[:80],
                    "raw": {"id": el.get("id"), "type": el.get("type"), "tags": tags},
                }
            )
        return results
