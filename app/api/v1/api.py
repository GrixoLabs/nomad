from fastapi import APIRouter

from app.api.v1.routers import auth, devices, health, signals, users

api_router = APIRouter()

api_router.include_router(health.router)
api_router.include_router(auth.router)
api_router.include_router(devices.router)
api_router.include_router(signals.router)
api_router.include_router(users.router)
