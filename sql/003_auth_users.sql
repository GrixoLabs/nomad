-- Auth-ready users + OTP + refresh tokens.
-- Run after 001_devices_and_signals.sql
-- Replaces the early bigint nomad.users table from 002_users.sql if present.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DROP TABLE IF EXISTS nomad.verification_codes CASCADE;
DROP TABLE IF EXISTS nomad.refresh_tokens CASCADE;
DROP TABLE IF EXISTS nomad.users CASCADE;

CREATE TABLE nomad.users
(
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255),
    phone_number VARCHAR(20),
    password_hash TEXT,

    name VARCHAR(100),
    age SMALLINT,
    gender VARCHAR(30),
    device_uuid UUID,

    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    journal_enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_phone UNIQUE (phone_number),
    CONSTRAINT ck_users_email_or_phone
        CHECK ((email IS NOT NULL) OR (phone_number IS NOT NULL)),
    CONSTRAINT ck_users_age
        CHECK (age IS NULL OR age BETWEEN 13 AND 120),
    CONSTRAINT ck_users_status
        CHECK (account_status IN ('pending', 'active', 'disabled')),
    CONSTRAINT fk_users_device_uuid
        FOREIGN KEY (device_uuid)
        REFERENCES nomad.devices(device_uuid)
        ON DELETE SET NULL
);

CREATE INDEX idx_users_device_uuid ON nomad.users(device_uuid);
CREATE INDEX idx_users_status ON nomad.users(account_status);

CREATE TABLE nomad.verification_codes
(
    verification_id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES nomad.users(user_id) ON DELETE CASCADE,
    verification_type VARCHAR(20) NOT NULL, -- EMAIL | SMS | PASSWORD_RESET
    otp_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_verification_type
        CHECK (verification_type IN ('EMAIL', 'SMS', 'PASSWORD_RESET'))
);

CREATE INDEX idx_verification_user_type
    ON nomad.verification_codes(user_id, verification_type, created_at DESC);

CREATE TABLE nomad.refresh_tokens
(
    token_id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES nomad.users(user_id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user
    ON nomad.refresh_tokens(user_id);
