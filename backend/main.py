import os
import gkeepapi
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI(title="Keep Quick Add API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Authenticate once on startup
keep = gkeepapi.Keep()
_authenticated = False


def get_keep() -> gkeepapi.Keep:
    global _authenticated
    if not _authenticated:
        email = os.environ.get("GOOGLE_EMAIL")
        token = os.environ.get("GOOGLE_MASTER_TOKEN")
        if not email or not token:
            raise HTTPException(
                status_code=500,
                detail="GOOGLE_EMAIL and GOOGLE_MASTER_TOKEN environment variables must be set",
            )
        keep.authenticate(email, token)
        keep.sync()
        _authenticated = True
    return keep


# --- Models ---

class AddItemRequest(BaseModel):
    text: str
    list_name: str


# --- Endpoints ---

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/items", status_code=201)
def add_item(request: AddItemRequest):
    """Add an item to a Keep list identified by name."""
    k = get_keep()
    k.sync()

    # Find the list by title (case-insensitive)
    node = next(
        (n for n in k.all()
         if isinstance(n, gkeepapi.node.List)
         and not n.trashed
         and not n.archived
         and n.title.lower() == request.list_name.lower()),
        None
    )
    if node is None:
        raise HTTPException(status_code=404, detail=f"List '{request.list_name}' not found")

    node.add(request.text, False)  # False = not checked
    k.sync()
    return {"message": f"Added '{request.text}' to '{node.title}'"}
