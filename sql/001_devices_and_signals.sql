-- Existing Nomad core tables (provided schema).
-- Safe to run once on a fresh database after: CREATE SCHEMA IF NOT EXISTS nomad;

CREATE SCHEMA IF NOT EXISTS nomad;

CREATE TABLE IF NOT EXISTS nomad.devices
(
    device_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_uuid UUID NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    manufacturer VARCHAR(50),
    model VARCHAR(100),
    android_version VARCHAR(30),
    app_version VARCHAR(30),
    first_seen_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_devices_device_uuid UNIQUE (device_uuid)
);

CREATE INDEX IF NOT EXISTS idx_devices_last_seen
    ON nomad.devices(last_seen_utc);

CREATE INDEX IF NOT EXISTS idx_devices_uuid
    ON nomad.devices(device_uuid);

CREATE TABLE IF NOT EXISTS nomad.device_signals
(
    signal_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_id BIGINT NOT NULL,
    gps_timestamp_utc TIMESTAMPTZ NOT NULL,
    received_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_m REAL,
    altitude_m REAL,
    speed_mps REAL,
    bearing_deg REAL,
    battery_percent SMALLINT,
    charging BOOLEAN,
    battery_temperature REAL,
    network_type VARCHAR(30),
    wifi_enabled BOOLEAN,
    bluetooth_enabled BOOLEAN,
    screen_on BOOLEAN,
    power_save_mode BOOLEAN,
    client_ip INET,
    user_agent TEXT,
    CONSTRAINT fk_device_signals_device
        FOREIGN KEY (device_id)
        REFERENCES nomad.devices(device_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_signals_device
    ON nomad.device_signals(device_id);

CREATE INDEX IF NOT EXISTS idx_signals_received
    ON nomad.device_signals(received_utc);

CREATE INDEX IF NOT EXISTS idx_signals_device_timestamp
    ON nomad.device_signals(device_id, gps_timestamp_utc DESC);
