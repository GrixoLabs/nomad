-- Place / weather / tourism caches for Nomad tracking card.
-- Apply after 001 + 003:
--   psql ... -f sql/004_places_weather_tourism.sql

CREATE SCHEMA IF NOT EXISTS nomad;

-- (a) Reverse-geocode cache (~20 km grid). Static — no scheduled deletes.
CREATE TABLE IF NOT EXISTS nomad.place_cache
(
    grid_key      VARCHAR(32) PRIMARY KEY,
    lat_center    DOUBLE PRECISION NOT NULL,
    lon_center    DOUBLE PRECISION NOT NULL,
    display_name  TEXT NOT NULL,
    city          VARCHAR(120),
    region        VARCHAR(120),
    country       VARCHAR(120),
    raw_json      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_place_cache_updated
    ON nomad.place_cache(updated_at);

-- (b) Weather cache (~20 km grid). Truncated every 4 hours (UTC) by systemd timer.
CREATE TABLE IF NOT EXISTS nomad.weather_cache
(
    grid_key           VARCHAR(32) PRIMARY KEY,
    lat_center         DOUBLE PRECISION NOT NULL,
    lon_center         DOUBLE PRECISION NOT NULL,
    summary            VARCHAR(160) NOT NULL,
    temperature_c      DOUBLE PRECISION,
    feels_like_c       DOUBLE PRECISION,
    humidity_percent   SMALLINT,
    wind_speed_kmh     DOUBLE PRECISION,
    weather_code       SMALLINT,
    raw_json           JSONB,
    fetched_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weather_cache_fetched
    ON nomad.weather_cache(fetched_at);

-- (c) Tourist / nearby spots. Permanent cache — no deletes.
CREATE TABLE IF NOT EXISTS nomad.tourist_spots
(
    spot_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grid_key      VARCHAR(32) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    category      VARCHAR(80),
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    source        VARCHAR(40) NOT NULL DEFAULT 'overpass',
    source_id     VARCHAR(80),
    raw_json      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tourist_spots_source UNIQUE (source, source_id)
);

CREATE INDEX IF NOT EXISTS idx_tourist_spots_grid
    ON nomad.tourist_spots(grid_key);

CREATE INDEX IF NOT EXISTS idx_tourist_spots_coords
    ON nomad.tourist_spots(latitude, longitude);
