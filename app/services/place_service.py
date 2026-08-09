"""Place resolve (Nominatim), weather (Open-Meteo), nearby POIs (Google Places)."""

from __future__ import annotations

import logging
import math
import re
from datetime import datetime, timedelta, timezone
from typing import Any

import httpx
from fastapi import HTTPException
from sqlalchemy import delete, select
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

WEATHER_CACHE_MAX_AGE = timedelta(hours=4)

NEARBY_RADIUS_M = 25_000
GOOGLE_PLACES_NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby"
GOOGLE_POI_TYPES = (
    "tourist_attraction",
    "museum",
    "park",
    "zoo",
    "amusement_park",
    "aquarium",
    "art_gallery",
    "hindu_temple",
    "church",
    "mosque",
)
_NAME_STOPWORDS = (
    "zoological",
    "park",
    "the",
    "and",
    "of",
    "at",
    "in",
    "national",
    "international",
    "memorial",
    "complex",
    "centre",
    "center",
)

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

    @staticmethod
    def _area_label(locality: str | None, city: str | None) -> str | None:
        if locality and city and locality.lower() != city.lower():
            return f"{locality} in {city}"
        return locality or city

    def resolve_place(self, db: Session, lat: float, lon: float) -> PlaceResolveResponse:
        key = grid_key(lat, lon)
        existing = db.get(PlaceCache, key)
        if existing:
            locality = getattr(existing, "locality", None)
            return PlaceResolveResponse(
                display_name=existing.display_name,
                locality=locality,
                city=existing.city,
                region=existing.region,
                country=existing.country,
                area_label=self._area_label(locality, existing.city),
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
        )
        locality = (
            address.get("suburb")
            or address.get("neighbourhood")
            or address.get("neighborhood")
            or address.get("quarter")
            or address.get("city_district")
            or address.get("residential")
            or address.get("hamlet")
        )
        # Avoid duplicating city into locality
        if locality and city and locality.lower() == city.lower():
            locality = None
        region = address.get("state") or address.get("region") or address.get("county")
        country = address.get("country")
        area = self._area_label(locality, city)
        display = area or payload.get("display_name") or ", ".join(
            x for x in (city, region, country) if x
        ) or f"Near {lat_c:.2f}, {lon_c:.2f}"

        row = PlaceCache(
            grid_key=key,
            lat_center=lat_c,
            lon_center=lon_c,
            display_name=display,
            locality=locality,
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
            locality=locality,
            city=city,
            region=region,
            country=country,
            area_label=area,
            grid_key=key,
            cached=False,
        )

    def get_weather(self, db: Session, lat: float, lon: float) -> WeatherResponse:
        key = grid_key(lat, lon)
        existing = db.get(WeatherCache, key)
        if existing is not None:
            fetched = existing.fetched_at
            if fetched.tzinfo is None:
                fetched = fetched.replace(tzinfo=timezone.utc)
            if datetime.now(timezone.utc) - fetched < WEATHER_CACHE_MAX_AGE:
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
        self,
        db: Session,
        lat: float,
        lon: float,
        limit: int = 10,
        sort: str = "popularity",
    ) -> NearbyPlacesResponse:
        """Top popular spots within 25 km via Google Places. Distance is visual only."""
        key = grid_key(lat, lon)
        limit = max(1, min(limit, 10))
        sort = sort if sort in {"popularity", "distance"} else "popularity"

        cached_rows = list(
            db.scalars(
                select(TouristSpot).where(
                    TouristSpot.grid_key == key,
                    TouristSpot.source == "google_places",
                )
            ).all()
        )
        from_cache = bool(cached_rows)

        if not cached_rows:
            if not self.settings.google_api_key:
                raise HTTPException(
                    status_code=503,
                    detail="GOOGLE_MAP_API is not configured on the server",
                )
            fetched = self._fetch_google_places(lat, lon)
            # Drop legacy Overpass rows for this grid so UI only sees Google POIs.
            db.execute(
                delete(TouristSpot).where(
                    TouristSpot.grid_key == key,
                    TouristSpot.source == "overpass",
                )
            )
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
                        popularity_score=item.get("popularity_score", 0),
                        raw_json=item.get("raw"),
                        created_at=now,
                    )
                    .on_conflict_do_nothing(constraint="uq_tourist_spots_source")
                )
                db.execute(stmt)
            db.commit()
            cached_rows = list(
                db.scalars(
                    select(TouristSpot).where(
                        TouristSpot.grid_key == key,
                        TouristSpot.source == "google_places",
                    )
                ).all()
            )

        scored: list[NearbyPlace] = []
        for row in cached_rows:
            dist = haversine_m(lat, lon, row.latitude, row.longitude)
            if dist > NEARBY_RADIUS_M:
                continue
            scored.append(
                NearbyPlace(
                    name=row.name,
                    category=row.category,
                    latitude=row.latitude,
                    longitude=row.longitude,
                    distance_m=round(dist, 1),
                    popularity_score=getattr(row, "popularity_score", 0) or 0,
                )
            )

        scored = self._dedupe_similar_places(scored)
        # Primary listing: popularity. Distance stays on the card as a visual.
        scored.sort(key=lambda p: (-p.popularity_score, p.distance_m or 1e12))
        top = scored[:limit]
        if sort == "distance":
            top.sort(key=lambda p: p.distance_m if p.distance_m is not None else 1e12)
        return NearbyPlacesResponse(places=top, cached=from_cache, sort=sort)

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
            # Higher zoom → suburb/neighbourhood for locality labels
            "zoom": 14,
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

    def _fetch_google_places(self, lat: float, lon: float) -> list[dict[str, Any]]:
        """Famous / popular POIs within 25 km via Google Places Nearby Search (New)."""
        api_key = self.settings.google_api_key
        body = {
            "includedTypes": list(GOOGLE_POI_TYPES),
            "maxResultCount": 20,
            "rankPreference": "POPULARITY",
            "locationRestriction": {
                "circle": {
                    "center": {"latitude": lat, "longitude": lon},
                    "radius": float(NEARBY_RADIUS_M),
                }
            },
        }
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": (
                "places.id,places.displayName,places.location,"
                "places.types,places.rating,places.userRatingCount"
            ),
        }
        with self._client() as client:
            resp = client.post(GOOGLE_PLACES_NEARBY_URL, json=body, headers=headers)
            if resp.status_code >= 400:
                logger.warning(
                    "Google Places error status=%s body=%s",
                    resp.status_code,
                    resp.text[:400],
                )
                resp.raise_for_status()
            payload = resp.json()

        results: list[dict[str, Any]] = []
        for place in payload.get("places") or []:
            display = place.get("displayName") or {}
            name = display.get("text") if isinstance(display, dict) else None
            if not name:
                continue
            location = place.get("location") or {}
            if "latitude" not in location or "longitude" not in location:
                continue
            elat = float(location["latitude"])
            elon = float(location["longitude"])
            if haversine_m(lat, lon, elat, elon) > NEARBY_RADIUS_M:
                continue
            types = [str(t) for t in (place.get("types") or []) if t]
            category = next(
                (t for t in types if t in GOOGLE_POI_TYPES),
                types[0] if types else "tourist_attraction",
            )
            place_id = str(place.get("id") or f"{elat:.5f},{elon:.5f}")
            rating = place.get("rating")
            rating_count = place.get("userRatingCount") or 0
            results.append(
                {
                    "name": name,
                    "category": str(category)[:80],
                    "latitude": elat,
                    "longitude": elon,
                    "source": "google_places",
                    "source_id": place_id[:80],
                    "popularity_score": self._google_popularity_score(rating, rating_count),
                    "raw": {
                        "id": place_id,
                        "types": types,
                        "rating": rating,
                        "userRatingCount": rating_count,
                    },
                }
            )
        results.sort(key=lambda x: -x["popularity_score"])
        return results

    @staticmethod
    def _google_popularity_score(rating: Any, rating_count: Any) -> int:
        try:
            r = float(rating) if rating is not None else 0.0
        except (TypeError, ValueError):
            r = 0.0
        try:
            count = int(rating_count or 0)
        except (TypeError, ValueError):
            count = 0
        # Rating-weighted log popularity so well-known places rank first.
        score = int(round(r * 20.0 + math.log1p(max(0, count)) * 12.0))
        return max(1, score)

    @staticmethod
    def _normalize_place_name(name: str) -> str:
        text = name.lower()
        text = re.sub(r"[^a-z0-9\s]", " ", text)
        tokens = [t for t in text.split() if t and t not in _NAME_STOPWORDS]
        return "".join(tokens)

    @classmethod
    def _dedupe_similar_places(cls, places: list[NearbyPlace]) -> list[NearbyPlace]:
        """Collapse near-duplicates like 'Tata Zoo' vs 'Tata Zoological Park'."""
        best: dict[str, NearbyPlace] = {}
        for place in places:
            key = cls._normalize_place_name(place.name)
            if not key:
                key = place.name.strip().lower()
            current = best.get(key)
            if current is None:
                best[key] = place
                continue

            # Prefer higher popularity, then closer, then shorter display name.
            place_key = (
                place.popularity_score,
                -(place.distance_m if place.distance_m is not None else 1e12),
                -len(place.name),
            )
            current_key = (
                current.popularity_score,
                -(current.distance_m if current.distance_m is not None else 1e12),
                -len(current.name),
            )
            winner = place if place_key > current_key else current
            loser = current if winner is place else place
            dist_candidates = [
                d for d in (winner.distance_m, loser.distance_m) if d is not None
            ]
            best[key] = NearbyPlace(
                name=winner.name if len(winner.name) <= len(loser.name) else loser.name,
                category=winner.category or loser.category,
                latitude=winner.latitude,
                longitude=winner.longitude,
                distance_m=min(dist_candidates) if dist_candidates else None,
                popularity_score=max(winner.popularity_score, loser.popularity_score),
            )
        return list(best.values())

    @staticmethod
    def _popularity_score(tags: dict[str, Any], category: str) -> int:
        score = 1
        if tags.get("wikipedia") or tags.get("wikidata"):
            score += 40
        if tags.get("tourism") in {"attraction", "museum", "theme_park", "zoo", "viewpoint"}:
            score += 25
        if tags.get("historic"):
            score += 15
        if tags.get("amenity") in {"theatre", "cinema"}:
            score += 10
        if tags.get("amenity") == "place_of_worship":
            score += 5
        # Named landmarks with multiple languages tend to be notable
        score += min(10, sum(1 for k in tags if str(k).startswith("name:")))
        if category:
            score += 1
        return score
