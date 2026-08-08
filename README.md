# Nomad Backend

FastAPI + PostgreSQL API for the Nomad Android client.

## Database setup

Apply in order against your Postgres (schema `nomad`):

```bash
psql "host=HOST port=5432 dbname=DB user=USER password=PASS sslmode=require" -f sql/001_devices_and_signals.sql
psql "host=HOST port=5432 dbname=DB user=USER password=PASS sslmode=require" -f sql/003_auth_users.sql
psql "host=HOST port=5432 dbname=DB user=USER password=PASS sslmode=require" -f sql/004_places_weather_tourism.sql
psql "host=HOST port=5432 dbname=DB user=USER password=PASS sslmode=require" -f sql/005_journal_history_nights.sql
```

`003_auth_users.sql` replaces any early `nomad.users` table with UUID auth users + OTP + refresh tokens.

`004_places_weather_tourism.sql` adds:
- `nomad.place_cache` — reverse geocode (~20 km grid), static
- `nomad.weather_cache` — Open-Meteo cache, flushed daily at midnight UTC
- `nomad.tourist_spots` — Overpass attractions, permanent

## Auth (.env)

Put these in `.env` (e.g. `/MyProjects/nomad/.env`):

```
RESEND_API_KEY=...
RESEND_FROM_EMAIL=Nomad <you@yourdomain.com>
TWILIO_CLIENT_ID=...          # Twilio Account SID
TWILIO_CLIENT_KEY=...         # Twilio Auth Token
TWILIO_FROM_NUMBER=+1...      # required for SMS
JWT_SECRET=long-random-secret
```

If keys are missing, OTP codes are logged server-side for local/dev only.

## API (`/api/v1`)

### Auth
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/auth/register` | Create account (email **or** phone required + password) |
| POST | `/auth/send-email-otp` | Send email OTP (Resend) |
| POST | `/auth/verify-email-otp` | Verify email OTP |
| POST | `/auth/send-sms-otp` | Send SMS OTP (Twilio) |
| POST | `/auth/verify-sms-otp` | Verify SMS OTP |
| POST | `/auth/login` | JWT access + refresh (requires active account) |
| POST | `/auth/forgot-password` | Email password-reset OTP |
| POST | `/auth/reset-password` | Reset password with OTP |

Account becomes `active` (and `journal_enabled=true`) after **at least one** of email/phone is verified.

### Core
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/health` | Liveness |
| POST | `/devices/register` | Upsert device |
| POST | `/signals` | Ingest signal |

### Places / weather / nearby
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/places/resolve?lat=&lon=` | DB → Nominatim → store → return (~20 km grid, 25s client timeout) |
| GET | `/weather?lat=&lon=` | DB → Open-Meteo → store (flushed daily midnight UTC) |
| GET | `/places/nearby?lat=&lon=&limit=10` | DB → Overpass tourism → store permanently; ranked by distance |

Flow for each: check local DB for grid key → else call 3rd party → persist → return.

### Journal / history / map
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/map/config` | Stadia tile URL (uses `STADIA_API` from `.env`) |
| POST | `/journal` | Create journal entry (≤500 chars); **AES-GCM masked at rest** |
| GET | `/history?device_uuid=&days=1..14` | Track segments + journal pins + night stays |

- Track: navy `idle` segments, crimson `travel` segments
- Night stay: idle ≥6h within 22:00–08:00 local → concentric circle + weather
- Developer decrypt: `python scripts/decrypt_journal.py --entry-id N`
- Recompute nights: `python scripts/compute_night_stays.py --days 14`

Nearby places default to **popularity** (top 10 for the city grid); pass `sort=distance` to reorder those 10.

### Weather flush job
```bash
# one-shot
python scripts/flush_weather_cache.py

# systemd timer (UTC midnight) — copy units and enable:
#   sudo cp scripts/nomad-flush-weather.* /etc/systemd/system/
#   sudo systemctl daemon-reload
#   sudo systemctl enable --now nomad-flush-weather.timer
```

## Run

```bash
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\Activate.ps1
pip install -r requirements.txt
# ensure .env is present in the working directory
uvicorn main:app --host 0.0.0.0 --port 8001 --reload
```

Docs: http://127.0.0.1:8001/docs

## Edge / “backend offline” on Android

Android points at `https://nomad.grixo.dev/`. If that host returns **HTTP 502**, the app correctly shows Offline even when `nomad.service` is running on the VM.

502 means the reverse proxy (nginx/Caddy) reached an upstream that failed — typical fixes:
1. Confirm uvicorn is listening on the upstream host/port configured in the proxy (`API_HOST`/`API_PORT`, often `127.0.0.1:8001`).
2. `systemctl status nomad.service` and journalctl for crash loops.
3. Match proxy `proxy_pass` to that port; reload nginx/Caddy.
4. Local check: `curl -sS http://127.0.0.1:8001/api/v1/health` on the server should return `{"status":"ok"}` while the public URL is broken.

## Security notes
- Passwords: Argon2id
- OTPs: HMAC-SHA256 hashes only (never stored plaintext)
- OTP expiry: 10 minutes (configurable)
- Rate limit: 3 OTP sends / 15 minutes / type
- Max verify attempts: 5 per code
