"""
Baslik / aciklama / etiket uretici.
Ollama varsa yerel LLM, yoksa hazir sablon.
"""
from __future__ import annotations

import json
import urllib.request
from pathlib import Path

from core import load_config

TAGS = ["valorant", "cs2", "gaming", "montage", "shorts", "fps", "clutch", "ace",
        "gamingclips", "turkishgamer", "oyun", "highlight", "troll", "edit"]

TEMPLATES = [
    "{t} | En Iyi Anlar",
    "{t} Iste Bu Anlar Icin Oynuyoruz",
    "{t} | Kesin Izle",
    "Bunu Gorecektin: {t}",
]


def generate(topic: str, cfg: dict | None = None, *, use_llm: bool = True,
             log=print) -> dict:
    cfg = cfg or load_config()
    lcfg = cfg.get("llm", {})
    meta: dict | None = None
    if use_llm and lcfg.get("enabled", False):
        meta = _ollama(lcfg.get("url", "http://localhost:11434"),
                       lcfg.get("model", "llama3.1:8b"), topic, log)
    if not meta:
        title = str(lcfg.get("title_template", TEMPLATES[0])).format(t=topic)[:95]
        meta = {
            "title": title,
            "description": (
                f"{topic} kunyesinden sectigim en iyi anlar.\n\n"
                "Hangisi en iyisiydi? Yorumlarda bulalim.\n\n"
                "Abone olursan bir sonraki videoyu kacirmazsin.\n\n"
                "#shorts #valorant #cs2 #gaming #montage"
            ),
            "tags": TAGS,
        }
    meta.setdefault("tags", [])
    return meta


def _ollama(base: str, model: str, topic: str, log) -> dict | None:
    prompt = (
        "Asagidaki konu icin YouTube videosu metni uret. Sadece JSON don.\n"
        'Alanlar: "title" (max 90 karakter, caps harf yok, emoji en fazla 1),\n'
        '"description" (3 kisa paragraf + 8 hashtag, Turkce), '
        '"tags" (12 adet, virgulle).\n'
        f"Konu: {topic}\n"
    )
    body = json.dumps({"model": model, "prompt": prompt, "stream": False,
                       "format": "json", "options": {"temperature": 0.85}}).encode()
    req = urllib.request.Request(base.rstrip("/") + "/api/generate", data=body,
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=90) as resp:
            data = json.loads(resp.read().decode())
        raw = data.get("response", "")
        return json.loads(raw[raw.find("{"): raw.rfind("}") + 1])
    except Exception as exc:
        log(f"  [!] Ollama yok, sablon kullanilacak ({exc})")
        return None
