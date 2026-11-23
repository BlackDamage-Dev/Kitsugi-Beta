"""
Telifsiz muzik kutuphanesi.

data/music/ altina mp3/wav at, yanina ayni isimle .json koy:
  epic_01.mp3
  epic_01.json -> {"title": "...", "artist": "...", "license": "youtube-audio-library",
                   "source": "https://...", "bpm": 128}

Lisans etiketleri (guvenli olanlar):
  youtube-audio-library, pixabay, mixkit, cc0, original
"""
from __future__ import annotations

import json
import random
from dataclasses import dataclass, asdict
from pathlib import Path

from core import AUDIO_EXT, ensure_dirs

SAFE_LICENSES = {"youtube-audio-library", "pixabay", "mixkit", "cc0", "original", "own"}


@dataclass
class Track:
    path: Path
    title: str
    artist: str
    license: str
    source: str
    bpm: int = 0
    mood: str = ""

    def to_dict(self) -> dict:
        d = asdict(self)
        d["path"] = str(self.path)
        return d

    @property
    def safe(self) -> bool:
        return self.license.lower() in SAFE_LICENSES


def load_tracks(folder: Path) -> list[Track]:
    folder = Path(folder)
    ensure_dirs(folder)
    out: list[Track] = []
    if not folder.exists():
        return out
    for f in sorted(folder.glob("*")):
        if f.suffix.lower() not in AUDIO_EXT:
            continue
        meta: dict = {}
        side = f.with_suffix(".json")
        if side.exists():
            try:
                meta = json.loads(side.read_text(encoding="utf-8"))
            except Exception:
                meta = {}
        out.append(Track(
            path=f,
            title=str(meta.get("title") or f.stem),
            artist=str(meta.get("artist") or ""),
            license=str(meta.get("license") or "unknown").lower(),
            source=str(meta.get("source") or ""),
            bpm=int(meta.get("bpm") or 0),
            mood=str(meta.get("mood") or "").lower(),
        ))
    return out


def write_index(folder: Path, tracks: list[Track]) -> Path:
    idx = Path(folder) / "index.json"
    idx.write_text(json.dumps([t.to_dict() for t in tracks], indent=2, ensure_ascii=False),
                   encoding="utf-8")
    return idx


def pick_track(tracks: list[Track], rng: random.Random | None = None,
               *, safe_only: bool = True, mood: str = "") -> Track | None:
    """Bu video icin bir parca secer.

    Sadece lisansi guvenli olanlar kullanilir; guvenli parca yoksa None doner
    (muziksiz render edilir, claim riski yok). `mood` verilirse (epic, hype,
    scary, chill) o ruhtaki parcalar tercih edilir.
    """
    pool = [t for t in tracks if t.safe] if safe_only else list(tracks)
    if not pool:
        return None
    if rng is None:
        rng = random.Random()
    if mood:
        m = mood.lower()
        pref = [t for t in pool if m in (t.mood + " " + t.title).lower()]
        if pref:
            pool = pref
    return rng.choice(pool)
