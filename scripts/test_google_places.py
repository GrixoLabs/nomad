#!/usr/bin/env python3
"""Diagnose Google Places Nearby from this host (bypasses Cloudflare).

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

URL = "https://places.googleapis.com/v1/places:searchNearby"


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
    body = {
        "includedTypes": ["tourist_attraction", "museum", "park"],
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
        with httpx.Client(timeout=httpx.Timeout(8.0, connect=5.0)) as client:
            resp = client.post(URL, json=body, headers=headers)
    except Exception as exc:  # noqa: BLE001
        print(f"FAIL: transport error: {exc}")
        return 3

    print(f"status={resp.status_code}")
    try:
        payload = resp.json()
    except Exception:  # noqa: BLE001
        print(resp.text[:500])
        return 4

    print(json.dumps(payload, indent=2)[:2000])
    if resp.status_code >= 400:
        return 5
    places = payload.get("places") or []
    print(f"OK: places={len(places)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
