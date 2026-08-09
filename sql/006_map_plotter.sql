-- Map plotter: canonical location cells for history map rendering.
-- Apply after 005:
--   psql ... -f sql/006_map_plotter.sql

CREATE SCHEMA IF NOT EXISTS nomad;

-- One row per device + lat/lon rounded to 3 decimals (~110 m).
CREATE TABLE IF NOT EXISTS nomad.map_plotter
(
    plot_id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id                     BIGINT NOT NULL
        REFERENCES nomad.devices(device_id) ON DELETE CASCADE,
    user_id                       UUID
        REFERENCES nomad.users(user_id) ON DELETE SET NULL,
    latitude                      DOUBLE PRECISION NOT NULL,  -- 3 decimal places
    longitude                     DOUBLE PRECISION NOT NULL,  -- 3 decimal places
    first_gps_timestamp           TIMESTAMPTZ NOT NULL,
    last_gps_timestamp            TIMESTAMPTZ NOT NULL,
    total_time_at_location_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    night_time_seconds            DOUBLE PRECISION NOT NULL DEFAULT 0,
    visit_count                   INTEGER NOT NULL DEFAULT 1,
    signal_count                  INTEGER NOT NULL DEFAULT 1,
    journal_count                 INTEGER NOT NULL DEFAULT 0,
    night_stayed                  BOOLEAN NOT NULL DEFAULT FALSE,
    place_label                   VARCHAR(240),
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_map_plotter_device_cell UNIQUE (device_id, latitude, longitude),
    CONSTRAINT ck_map_plotter_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_map_plotter_lon CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_map_plotter_time CHECK (total_time_at_location_seconds >= 0),
    CONSTRAINT ck_map_plotter_night_time CHECK (night_time_seconds >= 0)
);

CREATE INDEX IF NOT EXISTS idx_map_plotter_device_last
    ON nomad.map_plotter(device_id, last_gps_timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_map_plotter_device_night
    ON nomad.map_plotter(device_id, night_stayed)
    WHERE night_stayed IS TRUE;

CREATE INDEX IF NOT EXISTS idx_map_plotter_device_journals
    ON nomad.map_plotter(device_id, journal_count)
    WHERE journal_count > 0;

-- Per-device sync watermark so new devices backfill; existing skip full rebuild.
CREATE TABLE IF NOT EXISTS nomad.map_plotter_devices
(
    device_id        BIGINT PRIMARY KEY
        REFERENCES nomad.devices(device_id) ON DELETE CASCADE,
    last_signal_id   BIGINT,
    last_synced_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
