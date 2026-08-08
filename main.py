"""
Uvicorn entrypoint.

Run either:
  uvicorn main:app --host 0.0.0.0 --port 8001 --reload
  uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
"""

from app.main import app

__all__ = ["app"]
