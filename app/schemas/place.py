from pydantic import BaseModel, Field


class PlaceResolveResponse(BaseModel):
    display_name: str
    city: str | None = None
    region: str | None = None
    country: str | None = None
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


class NearbyPlacesResponse(BaseModel):
    places: list[NearbyPlace] = Field(default_factory=list)
    cached: bool = False
