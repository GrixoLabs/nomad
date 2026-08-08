-- User profiles for Android registration (email OR phone required).
-- Journal is enabled for registered users; skipped guests are not persisted.

CREATE TABLE IF NOT EXISTS nomad.users
(
    user_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    device_uuid UUID,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(30),
    age SMALLINT NOT NULL,
    gender VARCHAR(30) NOT NULL,
    journal_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_utc TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_users_email_or_phone
        CHECK ((email IS NOT NULL) OR (phone IS NOT NULL)),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone),

    CONSTRAINT fk_users_device_uuid
        FOREIGN KEY (device_uuid)
        REFERENCES nomad.devices(device_uuid)
        ON DELETE SET NULL,

    CONSTRAINT ck_users_age
        CHECK (age BETWEEN 13 AND 120)
);

CREATE INDEX IF NOT EXISTS idx_users_device_uuid
    ON nomad.users(device_uuid);
