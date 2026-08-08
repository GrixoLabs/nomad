from fastapi import APIRouter

from app.api.v1.routers import health
from app.api.v1.routers import devices

api_router = APIRouter()

api_router.include_router(health.router)
api_router.include_router(devices.router)
