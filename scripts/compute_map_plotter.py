#!/usr/bin/env python3
"""Rebuild / incrementally sync nomad.map_plotter from device_signals.

Intended to run every 30 minutes via nomad-sync-map-plotter.timer, and also
on demand after signal ingest / history reads.

  python scripts/compute_map_plotter.py
  python scripts/compute_map_plotter.py --device-uuid UUID --full
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from uuid import UUID

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from sqlalchemy import select

from app.database.models.device import Device
from app.database.session import SessionLocal
from app.services.map_plotter_service import MapPlotterService


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-uuid", type=UUID)
    parser.add_argument("--tz", default="Asia/Kolkata")
    parser.add_argument(
        "--full",
        action="store_true",
        help="Force full rebuild for selected device(s)",
    )
    args = parser.parse_args()

    svc = MapPlotterService()
    with SessionLocal() as db:
        q = select(Device).where(Device.is_active.is_(True))
        if args.device_uuid:
            q = q.where(Device.device_uuid == args.device_uuid)
        devices = list(db.scalars(q).all())
        total = 0
        for device in devices:
            n = svc.sync_device(
                db,
                device.device_id,
                tz_name=args.tz,
                force_full=args.full,
            )
            print(f"device {device.device_uuid}: processed {n} signal(s)")
            total += n
        print(f"done, signals_processed={total}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
