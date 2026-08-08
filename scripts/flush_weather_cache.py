#!/usr/bin/env python3
"""Flush nomad.weather_cache (intended for daily midnight cron/systemd timer).

Examples:
  python scripts/flush_weather_cache.py
  # crontab (UTC midnight):
  # 0 0 * * * cd /path/to/nomad && .venv/bin/python scripts/flush_weather_cache.py
"""

from __future__ import annotations

import logging
import sys
from pathlib import Path

# Allow running as `python scripts/flush_weather_cache.py` from repo root.
ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sqlalchemy import text

from app.database.session import SessionLocal

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("flush_weather_cache")


def main() -> int:
    with SessionLocal() as db:
        result = db.execute(text("TRUNCATE TABLE nomad.weather_cache"))
        db.commit()
        logger.info("weather_cache truncated (%s)", result.rowcount)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
