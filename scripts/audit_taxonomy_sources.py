#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Kaynak kapsama denetimi — tür / etiket / kurum alanları.

Her desteklenen detay kaynağının (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu, Simkl,
TMDB) kod içinde hangi JSON alanlarını **okuduğunu** doğrular. Amaç: bir alan sessizce
kaldırılırsa (refactor sırasında) CI kırılsın ve "kurum/tür eksik" hatası geri dönmesin.

Kullanım:
    python3 scripts/audit_taxonomy_sources.py           # matris yazdır
    python3 scripts/audit_taxonomy_sources.py --strict  # eksik varsa 1 döner (CI)
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/java/com/kitsugi/animelist"

# (kaynak, dosya, alan adı, kod içinde aranan imza, açıklama)
CHECKS = [
    # ── AniList ─────────────────────────────────────────────────────────────
    ("AniList", "data/remote/KitsugiAniListDetailClient.kt", "genres", "genres", "resmî türler"),
    ("AniList", "data/remote/KitsugiAniListDetailClient.kt", "tags", "tags {", "spoiler/rank'lı etiketler"),
    ("AniList", "data/remote/KitsugiAniListDetailClient.kt", "studios", "studios { edges", "stüdyolar (isMain dahil)"),

    # ── MAL / Jikan ─────────────────────────────────────────────────────────
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "genres", '"genres"', "türler"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "themes", '"themes"', "temalar"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "demographics", '"demographics"', "hedef kitle"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "studios", '"studios"', "stüdyolar"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "producers", '"producers"', "yapımcılar"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "licensors", '"licensors"', "lisans verenler"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "serializations", '"serializations"', "dergiler"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "publishers", '"publishers"', "yayıncılar (manga)"),
    ("MAL/Jikan", "data/remote/KitsugiMalDetailClient.kt", "authors", '"authors"', "yazarlar (manga)"),
    ("MAL/Jikan", "data/remote/KitsugiTaxonomySources.kt", "serializations", '"serializations"', "dergi (çapraz toplayıcı)"),
    ("MAL/Jikan", "data/remote/KitsugiTaxonomySources.kt", "publishers", '"publishers"', "yayınevi (çapraz toplayıcı)"),
    ("Shikimori", "data/remote/KitsugiTaxonomySources.kt", "licensors", '"licensors"', "lisans veren (çapraz toplayıcı)"),
    ("Simkl", "data/remote/KitsugiTaxonomySources.kt", "network", '"network"', "kanal (çapraz toplayıcı)"),

    # ── Bangumi ─────────────────────────────────────────────────────────────
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "动画制作", "动画制作", "stüdyo (infobox)"),
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "製作", '"製作"', "yapım komitesi"),
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "播放电视台", "播放电视台", "kanallar"),
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "出版社", '"出版社"', "yayıncı (manga)"),
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "连载杂志", "连载杂志", "dergi"),
    ("Bangumi", "data/remote/KitsugiBangumiDetailClient.kt", "tags", "tags", "kullanıcı etiketleri"),

    # ── Shikimori ───────────────────────────────────────────────────────────
    ("Shikimori", "data/remote/KitsugiShikimoriClient.kt", "genres", '"genres"', "türler (Rusça)"),
    ("Shikimori", "data/remote/KitsugiShikimoriClient.kt", "kind", "kind", "genre/theme/demographic ayrımı"),
    ("Shikimori", "data/remote/KitsugiShikimoriClient.kt", "studios", '"studios"', "stüdyolar"),
    ("Shikimori", "data/remote/KitsugiShikimoriClient.kt", "licensors", '"licensors"', "lisans verenler"),
    ("Shikimori", "data/remote/KitsugiShikimoriClient.kt", "publishers", '"publishers"', "yayınevleri (manga)"),

    # ── Kitsu ───────────────────────────────────────────────────────────────
    ("Kitsu", "data/remote/KitsuClient.kt", "categories", "categories", "kategoriler (tür + etiket)"),
    ("Kitsu", "data/remote/KitsuClient.kt", "animeProductions", "animeProductions", "yapım şirketleri (rol ile)"),

    # ── Simkl ───────────────────────────────────────────────────────────────
    ("Simkl", "data/remote/KitsugiSimklDetailClient.kt", "genres", '"genres"', "türler"),
    ("Simkl", "data/remote/KitsugiSimklDetailClient.kt", "network", '"network"', "yayın kanalı"),

    # ── TMDB ────────────────────────────────────────────────────────────────
    ("TMDB", "data/remote/TmdbMediaDetailClient.kt", "genres", '"genres"', "türler"),
    ("TMDB", "data/remote/TmdbMediaDetailClient.kt", "production_companies", "production_companies", "yapım şirketleri"),
    ("TMDB", "data/remote/TmdbMediaDetailClient.kt", "networks", '"networks"', "kanallar"),
    ("TMDB", "data/remote/TmdbMediaDetailClient.kt", "keywords", "extractKeywords", "anahtar kelimeler (etiket)"),
]

# Çapraz kaynak toplayıcı: hangi kaynaklardan veri çekildiği (hepsi aynı havuzda birleşir)
CROSS_SOURCES = [
    ("Jikan", "fetchJikan"),
    ("AniList", "fetchTagsAndAiringInfo"),
    ("Shikimori", "fetchShikimori"),
    ("Kitsu", "fetchKitsu"),
    ("Bangumi", "fetchBangumi"),
    ("TMDB", "fetchTmdb"),
    ("Simkl", "fetchSimkl"),
]

# Birleştirme tavanları — "sayısal olarak az geldi" geri dönmesin diye alt sınırlar.
# (dosya, kodda aranan imza, en az değer, açıklama)
LIMIT_CHECKS = [
    ("data/remote/KitsugiTaxonomyMerge.kt", "fun mergeGenres(", 160, "tür tavanı"),
    ("data/remote/KitsugiTaxonomyMerge.kt", "fun mergeTags(", 600, "etiket tavanı"),
    ("data/remote/KitsugiTaxonomyMerge.kt", "fun mergeOrgs(", 256, "kurum tavanı"),
    ("utils/KitsugiSpoilerRules.kt", "fun mergeTagsPreservingSpoilers(", 400, "spoiler korumalı birleştirme"),
    ("data/remote/KitsugiBangumiDetailClient.kt", "subject.tags.localizedDistinctTags(", 400, "Bangumi etiket tavanı"),
]

# Kurum listesini eksilten çağrılar yasak (yalnızca tanımı kalabilir).
FORBIDDEN_CALLS = [
    ("dropProducersThatAreStudios(", "data/remote/KitsugiTaxonomyMerge.kt",
     "yapımcı listesinden stüdyoları siliyor — kurum eksiltir"),
]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="eksik varsa 1 döner")
    args = parser.parse_args()

    missing = []
    rows: list[tuple[str, str, str, bool]] = []
    cache: dict[str, str] = {}

    for source, rel, field, needle, _desc in CHECKS:
        path = SRC / rel
        if rel not in cache:
            cache[rel] = path.read_text(encoding="utf-8") if path.exists() else ""
        text = cache[rel]
        ok = bool(text) and needle in text
        rows.append((source, field, rel, ok))
        if not ok:
            missing.append(f"{source}:{field} ({rel})")

    cross_path = SRC / "data/remote/KitsugiTaxonomySources.kt"
    cross_text = cross_path.read_text(encoding="utf-8") if cross_path.exists() else ""
    for name, marker in CROSS_SOURCES:
        if marker not in cross_text:
            missing.append(f"Çapraz kaynak:{name} ({marker})")

    # ── Tavan denetimi ────────────────────────────────────────────────────────
    for rel, signature, minimum, desc in LIMIT_CHECKS:
        path = SRC / rel
        if rel not in cache:
            cache[rel] = path.read_text(encoding="utf-8") if path.exists() else ""
        text = cache[rel]
        idx = text.find(signature)
        window = text[idx: idx + 400] if idx >= 0 else ""
        found = [int(n) for n in __import__("re").findall(r"limit(?::\s*Int)?\s*=\s*(\d+)", window)]
        found += [int(n) for n in __import__("re").findall(r"localizedDistinctTags\((\d+)\)", window)]
        if not found or max(found) < minimum:
            missing.append(f"{desc}: {rel} beklenen ≥{minimum}, bulunan {found or 'yok'}")

    # ── Yasaklı kurum eleme çağrıları ─────────────────────────────────────────
    for needle, allowed_rel, desc in FORBIDDEN_CALLS:
        for rel in cache:
            if needle in cache[rel]:
                occurrences = cache[rel].count(needle)
                # tanım satırı: "fun <needle>" — tek çağrı hakkı yalnızca tanım dosyasında
                definitions = cache[rel].count("fun " + needle)
                if occurrences - definitions > 0 and not rel.endswith(allowed_rel):
                    missing.append(f"{desc}: {rel}")

    # Matris
    sources = ["AniList", "MAL/Jikan", "Bangumi", "Shikimori", "Kitsu", "Simkl", "TMDB"]
    width = max(len(f) for _, f, _, _ in rows)
    print("KAYNAK       ALAN".ljust(12) + " " * width + "  DURUM")
    print("-" * (14 + width + 8))
    for source, field, _rel, ok in rows:
        print(f"{source:<12}{field:<{width}}  {'✔ okundu' if ok else '✖ EKSİK'}")
    print()
    print("Çapraz kaynak toplayıcı (KitsugiTaxonomySources):")
    for name, marker in CROSS_SOURCES:
        print(f"  {name:<10} {'✔' if marker in cross_text else '✖'}")
    print()
    print(f"Toplam {len(rows)} alan denetlendi · {len(sources)} kaynak")

    if missing:
        print("\nEKSİKLER:", file=sys.stderr)
        for item in missing:
            print(f"  - {item}", file=sys.stderr)
        if args.strict:
            return 1
    else:
        print("TÜM KAYNAKLAR BEKLENEN ALANLARI OKUYOR — eksik yok.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
