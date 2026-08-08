"""Quick DB connectivity check: python test_db.py"""

from sqlalchemy import text

from app.database.session import engine


def main() -> None:
    with engine.connect() as conn:
        version = conn.execute(text("SELECT version();")).scalar()
        schema = conn.execute(
            text("SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'nomad'")
        ).scalar()
        print("Postgres:", version)
        print("nomad schema:", schema or "MISSING — run sql/001_devices_and_signals.sql")


if __name__ == "__main__":
    main()
