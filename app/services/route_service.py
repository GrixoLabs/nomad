"""Google Routes API → geometry for MapLibre rendering.

Tries Routes API (New) first, then legacy Directions API fallback.
"""

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
DIRECTIONS_LEGACY_URL = "https://maps.googleapis.com/maps/api/directions/json"
ROUTES_TIMEOUT_SECONDS = 8.0
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

        errors: list[str] = []
        try:
            return self._compute_routes_new(request, api_key, mode)
        except HTTPException as exc:
            errors.append(f"Routes API (New): {exc.detail}")
            logger.warning("Routes API (New) failed: %s", exc.detail)

        try:
            return self._compute_directions_legacy(request, api_key, mode)
        except HTTPException as exc:
            errors.append(f"Directions API: {exc.detail}")
            logger.warning("Legacy Directions failed: %s", exc.detail)

        raise HTTPException(
            status_code=502,
            detail=(
                "Google routing failed. "
                + " | ".join(errors)
                + ". Enable Routes API and/or Directions API, and use a server key."
            ),
        )

    def _compute_routes_new(
        self, request: RouteRequest, api_key: str, mode: str
    ) -> RouteResponse:
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
        timeout = httpx.Timeout(ROUTES_TIMEOUT_SECONDS, connect=3.0)
        try:
            with httpx.Client(timeout=timeout, follow_redirects=True) as client:
                resp = client.post(ROUTES_URL, json=body, headers=headers)
        except httpx.TimeoutException as exc:
            raise HTTPException(
                status_code=504,
                detail=f"Routes API timed out after {ROUTES_TIMEOUT_SECONDS:.0f}s",
            ) from exc
        except httpx.HTTPError as exc:
            raise HTTPException(
                status_code=502, detail=f"Routes API unreachable: {exc}"
            ) from exc

        if resp.status_code >= 400:
            raise HTTPException(status_code=502, detail=self._google_error_detail(resp))

        payload = resp.json()
        routes = payload.get("routes") or []
        if not routes:
            raise HTTPException(status_code=404, detail="No route found (Routes API)")

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

    def _compute_directions_legacy(
        self, request: RouteRequest, api_key: str, mode: str
    ) -> RouteResponse:
        """Legacy Directions API — commonly enabled when Routes API (New) is not."""
        legacy_mode = {
            "DRIVE": "driving",
            "WALK": "walking",
            "BICYCLE": "bicycling",
            "TWO_WHEELER": "driving",  # not supported on legacy; closest match
        }.get(mode, "driving")

        params = {
            "origin": f"{request.origin_lat},{request.origin_lon}",
            "destination": f"{request.dest_lat},{request.dest_lon}",
            "mode": legacy_mode,
            "key": api_key,
        }
        timeout = httpx.Timeout(ROUTES_TIMEOUT_SECONDS, connect=3.0)
        try:
            with httpx.Client(timeout=timeout, follow_redirects=True) as client:
                resp = client.get(DIRECTIONS_LEGACY_URL, params=params)
        except httpx.TimeoutException as exc:
            raise HTTPException(
                status_code=504,
                detail=f"Directions API timed out after {ROUTES_TIMEOUT_SECONDS:.0f}s",
            ) from exc
        except httpx.HTTPError as exc:
            raise HTTPException(
                status_code=502, detail=f"Directions API unreachable: {exc}"
            ) from exc

        if resp.status_code >= 400:
            raise HTTPException(status_code=502, detail=self._google_error_detail(resp))

        payload = resp.json()
        status = str(payload.get("status") or "")
        if status != "OK":
            detail = f"{status}: {payload.get('error_message') or status}"
            raise HTTPException(status_code=502, detail=detail)

        routes = payload.get("routes") or []
        if not routes:
            raise HTTPException(status_code=404, detail="No route found (Directions API)")

        route = routes[0]
        overview = (route.get("overview_polyline") or {}).get("points") or ""
        coords = decode_polyline(overview)
        if len(coords) < 2:
            # Fall back to stitching leg steps if overview missing.
            coords = []
            for leg in route.get("legs") or []:
                for step in leg.get("steps") or []:
                    poly = (step.get("polyline") or {}).get("points") or ""
                    coords.extend(decode_polyline(poly))
        if len(coords) < 2:
            raise HTTPException(status_code=502, detail="Route geometry unavailable")

        # Sum leg distance/duration.
        distance_m = 0
        duration_seconds = 0
        for leg in route.get("legs") or []:
            distance_m += int((leg.get("distance") or {}).get("value") or 0)
            duration_seconds += int((leg.get("duration") or {}).get("value") or 0)

        return RouteResponse(
            points=[RoutePoint(latitude=lat, longitude=lon) for lat, lon in coords],
            distance_m=distance_m or None,
            duration_seconds=duration_seconds or None,
            encoded_polyline=overview or None,
            travel_mode=mode,
        )

    @staticmethod
    def _google_error_detail(resp: httpx.Response) -> str:
        try:
            payload = resp.json()
        except Exception:  # noqa: BLE001
            text = (resp.text or "").strip()
            return text[:300] or f"HTTP {resp.status_code}"
        err = payload.get("error") if isinstance(payload, dict) else None
        if isinstance(err, dict):
            message = err.get("message") or err.get("status") or str(err)
            status = err.get("status")
            if status:
                return f"{status}: {message}"
            return str(message)[:300]
        if payload.get("status"):
            msg = payload.get("error_message") or payload.get("status")
            return f"{payload.get('status')}: {msg}"
        return str(payload)[:300]

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
