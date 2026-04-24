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

class KeepList(BaseModel):
    id: str
    title: str


class AddItemRequest(BaseModel):
    text: str


# --- Endpoints ---

@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/lists", response_model=list[KeepList])
def get_lists():
    """Return all Google Keep lists (notes of type List)."""
    k = get_keep()
    k.sync()
    lists = [
        KeepList(id=node.id, title=node.title or "(Untitled)")
        for node in k.all()
        if isinstance(node, gkeepapi.node.List) and not node.trashed and not node.archived
    ]
    lists.sort(key=lambda x: x.title.lower())
    return lists


@app.post("/lists/{list_id}/items", status_code=201)
def add_item(list_id: str, request: AddItemRequest):
    """Add an item to an existing Keep list."""
    k = get_keep()
    # Find the list by ID
    node = next((n for n in k.all() if n.id == list_id and isinstance(n, gkeepapi.node.List)), None)
    if node is None:
        raise HTTPException(status_code=404, detail=f"List '{list_id}' not found")

    node.add(request.text, False)  # False = not checked
    k.sync()
    return {"message": f"Added '{request.text}' to '{node.title}'"}
