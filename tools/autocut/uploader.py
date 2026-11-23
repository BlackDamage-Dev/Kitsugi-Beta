"""
YouTube Data API v3 yukleyici (resumable + zamanlama + thumbnail).
"""
from __future__ import annotations

import json
from pathlib import Path

SCOPES = [
    "https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube",
]


def get_credentials(secret: Path, token: Path):
    from google.auth.transport.requests import Request
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow

    creds = None
    if token.exists():
        try:
            creds = Credentials.from_authorized_user_file(str(token), SCOPES)
        except Exception:
            creds = None
    if creds and creds.expired and creds.refresh_token:
        try:
            creds.refresh(Request())
        except Exception:
            creds = None
    if not creds or not creds.valid:
        if not secret.exists():
            raise FileNotFoundError(
                f"{secret} bulunamadi. Google Cloud'dan OAuth client (Desktop app) indir."
            )
        flow = InstalledAppFlow.from_client_secrets_file(str(secret), SCOPES)
        creds = flow.run_local_server(port=0)
        token.write_text(creds.to_json(), encoding="utf-8")
    return creds


def upload(file: Path, *, title: str, description: str = "", tags: list[str] | None = None,
           category_id: str = "20", privacy: str = "private",
           publish_at: str | None = None, thumbnail: Path | None = None,
           made_for_kids: bool = False, secret: Path | None = None,
           token: Path | None = None, log=print) -> str:
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    here = Path(__file__).resolve().parent
    secret = secret or (here / "client_secret.json")
    token = token or (here / "token.json")

    creds = get_credentials(secret, token)
    service = build("youtube", "v3", credentials=creds)

    if publish_at:
        privacy = "private"

    body = {
        "snippet": {
            "title": title[:100],
            "description": (description or "")[:5000],
            "tags": [t for t in (tags or []) if t][:500],
            "categoryId": str(category_id),
        },
        "status": {
            "privacyStatus": privacy,
            "selfDeclaredMadeForKids": bool(made_for_kids),
        },
    }
    if publish_at:
        body["status"]["publishAt"] = publish_at

    media = MediaFileUpload(str(file), chunksize=8 * 1024 * 1024, resumable=True)
    req = service.videos().insert(part="snippet,status", body=body, media_body=media)

    resp = None
    last = -1
    while resp is None:
        status, resp = req.next_chunk(num_retries=5)
        if status:
            pct = int(status.progress() * 100)
            if pct != last:
                log(f"  yukleniyor %{pct}")
                last = pct

    vid = resp["id"]
    log(f"  video: https://youtu.be/{vid}  ({privacy})")

    if thumbnail:
        try:
            service.thumbnails().set(
                videoId=vid, media_body=MediaFileUpload(str(thumbnail))
            ).execute()
            log("  thumbnail yuklendi")
        except Exception as exc:
            log(f"  [!] thumbnail: {exc}")
    return vid


def channel_name(secret: Path | None = None, token: Path | None = None) -> str:
    from googleapiclient.discovery import build

    here = Path(__file__).resolve().parent
    secret = secret or (here / "client_secret.json")
    token = token or (here / "token.json")
    creds = get_credentials(secret, token)
    service = build("youtube", "v3", credentials=creds)
    resp = service.channels().list(part="snippet", mine=True).execute()
    items = resp.get("items", [])
    return items[0]["snippet"]["title"] if items else ""
