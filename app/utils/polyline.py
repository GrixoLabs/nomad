"""Google Encoded Polyline Algorithm decoder."""

from __future__ import annotations


def decode_polyline(encoded: str) -> list[tuple[float, float]]:
    """Decode an encoded polyline into (lat, lon) pairs."""
    if not encoded:
        return []

    coordinates: list[tuple[float, float]] = []
    index = 0
    lat = 0
    lon = 0
    length = len(encoded)

    while index < length:
        result = 0
        shift = 0
        while True:
            if index >= length:
                return coordinates
            b = ord(encoded[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlat = ~(result >> 1) if result & 1 else (result >> 1)
        lat += dlat

        result = 0
        shift = 0
        while True:
            if index >= length:
                return coordinates
            b = ord(encoded[index]) - 63
            index += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        dlon = ~(result >> 1) if result & 1 else (result >> 1)
        lon += dlon

        coordinates.append((lat / 1e5, lon / 1e5))

    return coordinates
