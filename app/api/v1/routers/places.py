from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.database.dependencies import get_db
from app.schemas.place import (
    NearbyPlacesResponse,
    PlaceResolveResponse,
    RouteRequest,
    RouteResponse,
    WeatherResponse,
)
from app.services.place_service import PlaceService
from app.services.route_service import RouteService

router = APIRouter(tags=["Places"])
service = PlaceService()
routes = RouteService()


@router.get("/places/resolve", response_model=PlaceResolveResponse)
def resolve_place(
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
    db: Session = Depends(get_db),
):
    try:
        return service.resolve_place(db, lat, lon)
    except Exception as exc:  # noqa: BLE001 — surface upstream failures as 502/504-ish
        raise HTTPException(status_code=504, detail=f"Place resolve failed: {exc}") from exc


@router.get("/weather", response_model=WeatherResponse)
def get_weather(
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
    db: Session = Depends(get_db),
):
    try:
        return service.get_weather(db, lat, lon)
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=504, detail=f"Weather fetch failed: {exc}") from exc


@router.get("/places/nearby", response_model=NearbyPlacesResponse)
def nearby_places(
    lat: float = Query(..., ge=-90, le=90),
    lon: float = Query(..., ge=-180, le=180),
    limit: int = Query(10, ge=1, le=10),
    sort: str = Query("popularity", pattern="^(popularity|distance)$"),
    db: Session = Depends(get_db),
):
    try:
        return service.nearby_places(db, lat, lon, limit=limit, sort=sort)
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=f"Nearby places failed: {exc}") from exc


@router.post("/routes/compute", response_model=RouteResponse)
def compute_route(request: RouteRequest):
    """Google Routes geometry for MapLibre (Stadia tiles) rendering."""
    return routes.compute_route(request)
