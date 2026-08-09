"""Google Routes API → geometry for MapLibre rendering."""

from __future__ import annotations

import logging
import re
from typing import Any

import httpx
from fastapi import HTTPException

from app.config.settings import Settings, get_settings
from app.schemas.place import RoutePoint, RouteRequest, RouteResponse
from app.utils.polyline import decode_polyline

logger = logging.getLogger(__name__)

ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
_DURATION_RE = re.compile(r"^(-?\d+(?:\.\d+)?)s$")


class RouteService:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    def compute_route(self, request: RouteRequest) -> RouteResponse:
        api_key = self.settings.google_api_key
        if not api_key:
            raise HTTPException(
                status_code=503,
                detail="GOOGLE_MAP_API is not configured on the server",
            )

        mode = (request.travel_mode or "DRIVE").upper()
        if mode not in {"DRIVE", "WALK", "TWO_WHEELER", "BICYCLE"}:
            mode = "DRIVE"

        body = {
            "origin": {
                "location": {
                    "latLng": {
                        "latitude": request.origin_lat,
                        "longitude": request.origin_lon,
                    }
                }
            },
            "destination": {
                "location": {
                    "latLng": {
                        "latitude": request.dest_lat,
                        "longitude": request.dest_lon,
                    }
                }
            },
            "travelMode": mode,
            "polylineQuality": "HIGH_QUALITY",
            "polylineEncoding": "ENCODED_POLYLINE",
        }
        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": api_key,
            "X-Goog-FieldMask": (
                "routes.duration,routes.distanceMeters,"
                "routes.polyline.encodedPolyline"
            ),
        }

        timeout = httpx.Timeout(
            self.settings.external_http_timeout_seconds,
            connect=10.0,
        )
        with httpx.Client(timeout=timeout, follow_redirects=True) as client:
            resp = client.post(ROUTES_URL, json=body, headers=headers)
            if resp.status_code >= 400:
                logger.warning(
                    "Google Routes error status=%s body=%s",
                    resp.status_code,
                    resp.text[:400],
                )
                raise HTTPException(
                    status_code=502,
                    detail=f"Google Routes failed ({resp.status_code})",
                )
            payload = resp.json()

        routes = payload.get("routes") or []
        if not routes:
            raise HTTPException(status_code=404, detail="No route found")

        route = routes[0]
        encoded = ((route.get("polyline") or {}).get("encodedPolyline")) or ""
        coords = decode_polyline(encoded)
        if len(coords) < 2:
            raise HTTPException(status_code=502, detail="Route geometry unavailable")

        distance = route.get("distanceMeters")
        duration_seconds = self._parse_duration_seconds(route.get("duration"))
        return RouteResponse(
            points=[RoutePoint(latitude=lat, longitude=lon) for lat, lon in coords],
            distance_m=int(distance) if distance is not None else None,
            duration_seconds=duration_seconds,
            encoded_polyline=encoded or None,
            travel_mode=mode,
        )

    @staticmethod
    def _parse_duration_seconds(value: Any) -> int | None:
        if value is None:
            return None
        if isinstance(value, (int, float)):
            return int(value)
        text = str(value)
        match = _DURATION_RE.match(text)
        if not match:
            return None
        return int(float(match.group(1)))
