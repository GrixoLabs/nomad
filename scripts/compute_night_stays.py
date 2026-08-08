#!/usr/bin/env python3
"""Recompute overnight idle stays for all (or one) devices.

  python scripts/compute_night_stays.py --days 14
  python scripts/compute_night_stays.py --device-uuid UUID --days 7
"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sqlalchemy import select

from app.database.models.device import Device
from app.database.session import SessionLocal
from app.services.night_stay_service import NightStayService


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--days", type=int, default=14)
    parser.add_argument("--device-uuid", type=UUID)
    parser.add_argument("--tz", default="Asia/Kolkata")
    args = parser.parse_args()

    since = datetime.now(timezone.utc) - timedelta(days=max(1, min(args.days, 60)))
    svc = NightStayService()
    with SessionLocal() as db:
        q = select(Device).where(Device.is_active.is_(True))
        if args.device_uuid:
            q = q.where(Device.device_uuid == args.device_uuid)
        devices = list(db.scalars(q).all())
        total = 0
        for device in devices:
            n = svc.compute_for_device(db, device.device_id, since, tz_name=args.tz)
            print(f"device {device.device_uuid}: {n} night stay(s)")
            total += n
        print(f"done, upserted={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
