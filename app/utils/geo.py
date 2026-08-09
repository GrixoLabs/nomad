"""Coarse ~20 km grid helpers for place/weather/tourism caching."""

from __future__ import annotations

import math

# ~0.18° latitude ≈ 20 km. Longitude uses the same step for a stable key;
# at mid-latitudes this is still roughly 15–20 km.
GRID_STEP_DEG = 0.18


def grid_key(lat: float, lon: float, step: float = GRID_STEP_DEG) -> str:
    lat_c = round(round(lat / step) * step, 4)
    lon_c = round(round(lon / step) * step, 4)
    return f"{lat_c:.4f}:{lon_c:.4f}"


def grid_center(key: str) -> tuple[float, float]:
    lat_s, lon_s = key.split(":")
    return float(lat_s), float(lon_s)


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))
