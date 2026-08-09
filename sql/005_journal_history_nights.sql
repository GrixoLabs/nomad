-- Journal (encrypted at rest), night stays, place locality + popularity.
-- Apply after 004:
--   psql ... -f sql/005_journal_history_nights.sql

CREATE SCHEMA IF NOT EXISTS nomad;

-- Locality for "Mango in Jamshedpur" style labels
ALTER TABLE nomad.place_cache
    ADD COLUMN IF NOT EXISTS locality VARCHAR(160);

-- Popularity for tourist spots (higher = more notable)
ALTER TABLE nomad.tourist_spots
    ADD COLUMN IF NOT EXISTS popularity_score INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_tourist_spots_popularity
    ON nomad.tourist_spots(grid_key, popularity_score DESC);

-- Journal entries: ciphertext only in DB (AES-GCM). App receives plaintext via API.
CREATE TABLE IF NOT EXISTS nomad.journal_entries
(
    entry_id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id         BIGINT NOT NULL REFERENCES nomad.devices(device_id) ON DELETE CASCADE,
    user_id           UUID REFERENCES nomad.users(user_id) ON DELETE SET NULL,
    latitude          DOUBLE PRECISION NOT NULL,  -- store 5 decimal places (~1 m)
    longitude         DOUBLE PRECISION NOT NULL,
    place_label       VARCHAR(240),
    body_ciphertext   BYTEA NOT NULL,
    body_nonce        BYTEA NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_journal_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_journal_lon CHECK (longitude BETWEEN -180 AND 180)
);

CREATE INDEX IF NOT EXISTS idx_journal_device_created
    ON nomad.journal_entries(device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_journal_user_created
    ON nomad.journal_entries(user_id, created_at DESC);

-- Overnight idle stays (computed; navy concentric circles on map)
CREATE TABLE IF NOT EXISTS nomad.night_stays
(
    night_stay_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id         BIGINT NOT NULL REFERENCES nomad.devices(device_id) ON DELETE CASCADE,
    stay_date         DATE NOT NULL,              -- local calendar night start date
    latitude          DOUBLE PRECISION NOT NULL,
    longitude         DOUBLE PRECISION NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ NOT NULL,
    idle_hours        DOUBLE PRECISION NOT NULL,
    weather_summary   VARCHAR(160),
    temperature_c     DOUBLE PRECISION,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_night_stays_device_date UNIQUE (device_id, stay_date),
    CONSTRAINT ck_night_idle_hours CHECK (idle_hours >= 6.0)
);

CREATE INDEX IF NOT EXISTS idx_night_stays_device_date
    ON nomad.night_stays(device_id, stay_date DESC);
