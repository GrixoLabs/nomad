#!/usr/bin/env python3
"""Diagnose Google Places from this host (bypasses Cloudflare).

Tests Places API (New) and legacy Places Nearby Search.

  python scripts/test_google_places.py
  python scripts/test_google_places.py --lat 22.82 --lon 86.22
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

NEW_URL = "https://places.googleapis.com/v1/places:searchNearby"
LEGACY_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lat", type=float, default=22.82)
    parser.add_argument("--lon", type=float, default=86.22)
    args = parser.parse_args()

    settings = get_settings()
    key = settings.google_api_key
    if not key:
        print("FAIL: GOOGLE_MAP_API is empty / not loaded from .env")
        return 2

    print(f"key_loaded=yes key_suffix=...{key[-4:]} lat={args.lat} lon={args.lon}")
    timeout = httpx.Timeout(6.0, connect=3.0)

    print("\n=== Places API (New) ===")
    body = {
        "includedTypes": ["tourist_attraction"],
        "maxResultCount": 5,
        "rankPreference": "POPULARITY",
        "locationRestriction": {
            "circle": {
                "center": {"latitude": args.lat, "longitude": args.lon},
                "radius": 25000.0,
            }
        },
    }
    headers = {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": key,
        "X-Goog-FieldMask": (
            "places.id,places.displayName,places.location,"
            "places.rating,places.userRatingCount"
        ),
    }
    try:
        with httpx.Client(timeout=timeout, follow_redirects=True) as client:
            resp = client.post(NEW_URL, json=body, headers=headers)
        print(f"status={resp.status_code}")
        print(resp.text[:1200])
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL transport: {exc}")

    print("\n=== Legacy Places Nearby Search ===")
    params = {
        "location": f"{args.lat},{args.lon}",
        "radius": "25000",
        "type": "tourist_attraction",
        "key": key,
    }
    try:
        with httpx.Client(timeout=timeout, follow_redirects=True) as client:
            resp = client.get(LEGACY_URL, params=params)
        print(f"status={resp.status_code}")
        payload = resp.json()
        print(json.dumps({
            "status": payload.get("status"),
            "error_message": payload.get("error_message"),
            "results": [
                {"name": r.get("name"), "rating": r.get("rating")}
                for r in (payload.get("results") or [])[:5]
            ],
        }, indent=2))
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL transport: {exc}")
        return 3

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
