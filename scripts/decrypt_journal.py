#!/usr/bin/env python3
"""Developer-only tool: demask journal bodies stored as AES-GCM ciphertext.

IMPORTANT:
  - `--entry-id` is the database row id (nomad.journal_entries.entry_id), NOT the crypto key.
  - The mask/encryption key is `JOURNAL_MASK_KEY` in `.env` (falls back to `JWT_SECRET`).
  - Without the correct JOURNAL_MASK_KEY, ciphertext cannot be read — that is intentional.

Usage (from repo root, with .env loaded):
  python scripts/decrypt_journal.py --entry-id 12
  python scripts/decrypt_journal.py --device-uuid UUID --limit 20
  python scripts/decrypt_journal.py --limit 5
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
from app.database.models.journal import JournalEntry
from app.database.session import SessionLocal
from app.security.journal_crypto import decrypt_journal


def main() -> int:
    parser = argparse.ArgumentParser(description="Decrypt Nomad journal entries")
    parser.add_argument("--entry-id", type=int)
    parser.add_argument("--device-uuid", type=UUID)
    parser.add_argument("--limit", type=int, default=20)
    args = parser.parse_args()

    with SessionLocal() as db:
        q = select(JournalEntry).order_by(JournalEntry.created_at.desc())
        if args.entry_id:
            q = q.where(JournalEntry.entry_id == args.entry_id)
        if args.device_uuid:
            device = db.scalar(select(Device).where(Device.device_uuid == args.device_uuid))
            if not device:
                print("Device not found", file=sys.stderr)
                return 1
            q = q.where(JournalEntry.device_id == device.device_id)
        rows = list(db.scalars(q.limit(args.limit)).all())
        if not rows:
            print("No entries")
            return 0
        for row in rows:
            try:
                body = decrypt_journal(row.body_ciphertext, row.body_nonce)
            except Exception as exc:  # noqa: BLE001
                body = f"<decrypt failed: {exc}>"
            print(
                f"#{row.entry_id} {row.created_at.isoformat()} "
                f"({row.latitude:.5f},{row.longitude:.5f}) "
                f"{row.place_label or ''}\n{body}\n---"
            )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
