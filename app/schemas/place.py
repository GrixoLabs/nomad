from pydantic import BaseModel, Field


class PlaceResolveResponse(BaseModel):
    display_name: str
    locality: str | None = None
    city: str | None = None
    region: str | None = None
    country: str | None = None
    # e.g. "Mango in Jamshedpur"
    area_label: str | None = None
    grid_key: str
    cached: bool = False


class WeatherResponse(BaseModel):
    summary: str
    temperature_c: float | None = None
    feels_like_c: float | None = None
    humidity_percent: int | None = None
    wind_speed_kmh: float | None = None
    weather_code: int | None = None
    cached: bool = False


class NearbyPlace(BaseModel):
    name: str
    category: str | None = None
    latitude: float
    longitude: float
    distance_m: float | None = None
    popularity_score: int = 0


class NearbyPlacesResponse(BaseModel):
    places: list[NearbyPlace] = Field(default_factory=list)
    cached: bool = False
    sort: str = "popularity"


class RoutePoint(BaseModel):
    latitude: float
    longitude: float


class RouteRequest(BaseModel):
    origin_lat: float = Field(..., ge=-90, le=90)
    origin_lon: float = Field(..., ge=-180, le=180)
    dest_lat: float = Field(..., ge=-90, le=90)
    dest_lon: float = Field(..., ge=-180, le=180)
    travel_mode: str = Field(default="DRIVE", max_length=20)


class RouteResponse(BaseModel):
    points: list[RoutePoint] = Field(default_factory=list)
    distance_m: int | None = None
    duration_seconds: int | None = None
    encoded_polyline: str | None = None
    travel_mode: str = "DRIVE"
