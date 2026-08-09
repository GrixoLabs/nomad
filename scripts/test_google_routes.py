#!/usr/bin/env python3
"""Diagnose Google routing from this host (bypasses Cloudflare).

  python scripts/test_google_routes.py
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import httpx

from app.config.settings import get_settings

ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
DIRECTIONS_URL = "https://maps.googleapis.com/maps/api/directions/json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--olat", type=float, default=22.82)
    parser.add_argument("--olon", type=float, default=86.22)
    parser.add_argument("--dlat", type=float, default=22.8173)
    parser.add_argument("--dlon", type=float, default=86.1948)
    args = parser.parse_args()

    key = get_settings().google_api_key
    if not key:
        print("FAIL: GOOGLE_MAP_API empty")
        return 2
    print(f"key_suffix=...{key[-4:]}")

    timeout = httpx.Timeout(8.0, connect=3.0)

    print("\n=== Routes API (New) ===")
    body = {
        "origin": {"location": {"latLng": {"latitude": args.olat, "longitude": args.olon}}},
        "destination": {
            "location": {"latLng": {"latitude": args.dlat, "longitude": args.dlon}}
        },
        "travelMode": "DRIVE",
        "polylineQuality": "HIGH_QUALITY",
        "polylineEncoding": "ENCODED_POLYLINE",
    }
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": key,
        "X-Goog-FieldMask": "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline",
    }
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(ROUTES_URL, json=body, headers=headers)
        print(f"status={resp.status_code}")
        print(resp.text[:1200])
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: {exc}")

    print("\n=== Directions API (legacy) ===")
    params = {
        "origin": f"{args.olat},{args.olon}",
        "destination": f"{args.dlat},{args.dlon}",
        "mode": "driving",
        "key": key,
    }
    try:
        with httpx.Client(timeout=timeout) as client:
            resp = client.get(DIRECTIONS_URL, params=params)
        payload = resp.json()
        print(f"http={resp.status_code}")
        print(json.dumps({
            "status": payload.get("status"),
            "error_message": payload.get("error_message"),
            "has_overview": bool((payload.get("routes") or [{}])[0].get("overview_polyline")),
            "legs": len(((payload.get("routes") or [{}])[0].get("legs") or [])),
        }, indent=2))
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: {exc}")
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
