from fastapi import FastAPI

app = FastAPI(
    title="Nomad API",
    version="0.1.0"
)

@app.get("/")
def root():
    return {
        "status": "ok",
        "service": "Nomad API"
    }

@app.get("/health")
def health():
    return {
        "healthy": True
    }
