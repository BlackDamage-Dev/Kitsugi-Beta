"""
Meme kutuphanesi.

data/memes/ altina resim at, yanina ayni isimle bir .json koy:
  ace_01.png
  ace_01.json  ->  {"tags": ["ace"], "text": "ACE", "callout": "ACE!"}

json yoksa dosya adindan etiket cikarilir:  ace_01.png -> "ace"
Etiketler: ace, clutch, hype, whiff, fail, chill, scary, troll
"""
from __future__ import annotations

import json
import random
import re
from dataclasses import dataclass
from pathlib import Path

from core import IMG_EXT, ensure_dirs, load_config

DEFAULT_TAGS = ["ace", "clutch", "hype", "whiff", "fail", "chill", "scary", "troll"]

# Resim yoksa kullanilacak yazi sablonlari (Turkce, gaming kulturu)
TEXT_MEMES: dict[str, list[str]] = {
    "ace": ["ACE", "5 KISI 1 SISE", "SISTEM COKTU", "NOLUYOR KARDESIM"],
    "clutch": ["1v4", "SOGUK KANLI", "BU ADAM NASIL", "KALP KRIZI"],
    "hype": ["AMAN TANRIM", "REKOR", "BU NASIL ATIŞ", "INTERNET KOPTU"],
    "whiff": ["KACTI", "GOZDEN KACTI", "BIRAK BENI", "OFS"],
    "fail": ["ISTE BU", "KOTUYDU", "SILINSIN", "YOK SAYALIM"],
    "chill": ["SAKIN", "BASIT", "ISlem TAMAM", "EZBER"],
    "scary": ["KOS", "ARKANA BAK", "SES GELIYOR", "BURASI DEGIL"],
    "troll": ["TROL", "SANA DEMISTIM", "BUNA DEGER", "HAHAHA"],
}

CALLOUTS: dict[str, str] = {
    "ace": "ACE!",
    "clutch": "CLUTCH!",
    "hype": "AMAN TANRIM!",
    "whiff": "KACTI!",
    "fail": "OF!",
    "chill": "EZBER.",
    "scary": "KOS!",
    "troll": "TROL!",
}


@dataclass
class MemeDef:
    path: Path | None
    text: str
    tags: list[str]
    callout: str = ""

    def to_dict(self) -> dict:
        return {"path": str(self.path) if self.path else None, "text": self.text,
                "tags": self.tags, "callout": self.callout}


def _read_sidecar(img: Path) -> dict:
    for ext in (".json", ".txt"):
        side = img.with_suffix(ext)
        if side.exists():
            try:
                if ext == ".json":
                    return json.loads(side.read_text(encoding="utf-8"))
                return {"text": side.read_text(encoding="utf-8").strip()}
            except Exception:
                pass
    return {}


def load_library(folder: Path) -> list[MemeDef]:
    folder = Path(folder)
    ensure_dirs(folder)
    out: list[MemeDef] = []
    if not folder.exists():
        return out
    for img in sorted(folder.glob("*")):
        if img.suffix.lower() not in IMG_EXT:
            continue
        meta = _read_sidecar(img)
        tags = [t.lower().strip() for t in meta.get("tags", []) if t] if meta.get("tags") else []
        if not tags:
            stem = re.split(r"[_\-.\s]", img.stem)[0].lower()
            tags = [stem] if stem in DEFAULT_TAGS else ["hype"]
        text = str(meta.get("text") or img.stem.replace("_", " ").upper())
        callout = str(meta.get("callout") or CALLOUTS.get(tags[0], ""))
        out.append(MemeDef(path=img, text=text, tags=tags, callout=callout))

    # yazi meme'leri: resim olsa da olmasa da her zaman yedek olarak dursun.
    # Boylece kutuphane bosken bile "uygun yerlere uygun meme" calisir.
    have_img = {t for m in out if m.path for t in m.tags}
    for tag, texts in TEXT_MEMES.items():
        for t in texts[:2]:
            out.append(MemeDef(path=None, text=t, tags=[tag],
                               callout=CALLOUTS.get(tag, "")))
    return out


def pick(memedefs: list[MemeDef], tag: str, rng: random.Random,
         *, prefer_image: bool = True) -> MemeDef | None:
    cands = [m for m in memedefs if tag in m.tags]
    if not cands:
        cands = [m for m in memedefs if "hype" in m.tags] or memedefs
    if not cands:
        return None
    if prefer_image:
        with_img = [m for m in cands if m.path]
        if with_img:
            cands = with_img
    return rng.choice(cands)
