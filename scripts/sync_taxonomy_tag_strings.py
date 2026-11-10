#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ortak taksonomi (Rusça/Shikimori) etiketlerini dil dosyalarıyla eşitler.

`RussianTaxonomyDictionary.kt` içindeki HER giriş için
`res/values/strings.xml` (Türkçe) ve `res/values-en/strings.xml` (İngilizce)
dosyalarına `taxonomy_tag_<key>` kaynağı yazar.

Neden kaynak dosya:
  • Çeviri ekibi sözlüğü (Kotlin) değiştirmeden metni düzeltebilir; çalışma zamanında
    dil dosyası sözlük tablosundan önceliklidir (`taxonomyStringOverride`).
  • `--check` modu CI'da "sözlük değişti ama dil dosyaları güncellenmedi" hatasını yakalar.

Kullanım:
    python3 scripts/sync_taxonomy_tag_strings.py          # dosyaları günceller
    python3 scripts/sync_taxonomy_tag_strings.py --check  # fark varsa 1 döner (CI)
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DICTIONARY = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt"
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

TR_MARKER = "    <!-- Ortak Taksonomi Etiketleri (Rusça kaynak değerler) -->"
EN_MARKER = "    <!-- Shared Taxonomy Tags (Russian source values) -->"
# Blok, Bangumi etiket bloğunun **önüne** yazılır (sabit çapa, dil dosyasına göre değişir).
TR_ANCHOR = "    <!-- Bangumi Etiketleri (Çince/Japonca kaynak değerler) -->"
EN_ANCHOR = "    <!-- Bangumi Tags (Chinese/Japanese source values) -->"

ENTRY_RE = re.compile(r"g\(\s*(?:\d+|null)\s*,\s*(\"(?:[^\"\\\\]|\\\\.)*\"(?:\s*,\s*\"(?:[^\"\\\\]|\\\\.)*\")*)\s*\)")
STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
KEY_RE = re.compile(r"[a-z0-9][a-z0-9_]*")

TAXONOMY_TAG_PREFIX = '<string name="taxonomy_tag_'
AUTO_COMMENT_PREFIXES = ("<!-- OTOMATİK ÜRETİLDİ", "<!-- AUTO-GENERATED")


def read_entries() -> list[tuple[str, str, str]]:
    """Sözlükten (key, turkish, english) üçlülerini sırayla okur."""
    source = DICTIONARY.read_text(encoding="utf-8")
    body = source.split("private val groups: List<Group> = listOf(", 1)[1]
    body = body.split("\n    // ── İndeksler", 1)[0]

    entries: list[tuple[str, str, str]] = []
    for raw in ENTRY_RE.findall(body):
        values = STRING_RE.findall(raw)
        if len(values) < 3:
            raise SystemExit(f"Eksik sözlük girişi: {values}")
        key, turkish, english = values[0], values[1], values[2]
        if not KEY_RE.fullmatch(key):
            raise SystemExit(f"Geçersiz kaynak anahtarı (yalnızca a-z0-9_): {key}")
        entries.append((key, turkish, english))

    duplicates = [k for k, c in Counter(k for k, _, _ in entries).items() if c > 1]
    if duplicates:
        raise SystemExit(f"Yinelenen sözlük anahtarı: {duplicates}")
    if not entries:
        raise SystemExit("Sözlükten hiç giriş okunamadı — ayrıştırma deseni eskimiş olabilir.")
    return entries


def escape(value: str) -> str:
    """Android string kaynağı için kaçış."""
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", r"\'")
        .replace('"', r"\"")
    )


def render(entries: list[tuple[str, str, str]], marker: str, turkish: bool) -> str:
    lines = [marker]
    lines.append(
        "    <!-- OTOMATİK ÜRETİLDİ: scripts/sync_taxonomy_tag_strings.py -->"
        if turkish
        else "    <!-- AUTO-GENERATED: scripts/sync_taxonomy_tag_strings.py -->"
    )
    for key, tr, en in entries:
        lines.append(f'    <string name="taxonomy_tag_{key}">{escape(tr if turkish else en)}</string>')
    return "\n".join(lines)


def _collapse_blank_runs(lines: list[str], max_blank: int = 2) -> list[str]:
    out: list[str] = []
    blanks = 0
    for line in lines:
        if line.strip():
            blanks = 0
            out.append(line)
        else:
            blanks += 1
            if blanks <= max_blank:
                out.append(line)
    return out


def patch(path: Path, marker: str, block: str, check: bool, anchor: str = TR_ANCHOR) -> bool:
    """Üretilen bloğu **yerinde** yazar; dosyanın geri kalanına dokunmaz.

    1. Dosyadaki tüm `taxonomy_tag_*` kaynakları ve bu betiğin otomatik yorumu toplanır,
    2. bulundukları yerden çıkarılır,
    3. işaretçi satırı yerine tek parça hâlinde yeniden yazılır.

    İşaretçi yoksa blok, Bangumi etiket bloğunun **önüne** yerleştirilir (sabit çapa),
    böylece dosya sonundaki başka bloklar (kurum kaynakları, wiki, stream) etkilenmez.
    """
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()

    if marker not in text:
        if anchor not in lines:
            raise SystemExit(f"{path} içinde çapa satırı bulunamadı: {anchor}")
        anchor_idx = lines.index(anchor)
        rebuilt = lines[:anchor_idx] + block.splitlines() + [""] + lines[anchor_idx:]
        updated = "\n".join(_collapse_blank_runs(rebuilt))
        if text.endswith("\n"):
            updated += "\n"
        if updated == text:
            return False
        if not check:
            path.write_text(updated, encoding="utf-8")
        return True

    marker_idx = lines.index(marker)
    rebuilt: list[str] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        if index != marker_idx and (
            stripped.startswith(TAXONOMY_TAG_PREFIX)
            or ("sync_taxonomy_tag_strings.py" in stripped and stripped.startswith(AUTO_COMMENT_PREFIXES))
        ):
            continue
        if index == marker_idx:
            rebuilt.extend(block.splitlines())
            continue
        rebuilt.append(line)

    updated = "\n".join(_collapse_blank_runs(rebuilt))
    if text.endswith("\n"):
        updated += "\n"
    if updated == text:
        return False
    if not check:
        path.write_text(updated, encoding="utf-8")
    return True


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="yalnızca doğrula, yazma")
    args = parser.parse_args()

    entries = read_entries()
    changed = False
    changed |= patch(TR_STRINGS, TR_MARKER, render(entries, TR_MARKER, True), args.check)
    changed |= patch(
        EN_STRINGS, EN_MARKER, render(entries, EN_MARKER, False), args.check, anchor=EN_ANCHOR
    )

    print(f"{len(entries)} taksonomi etiketi işlendi.")
    if args.check and changed:
        print(
            "Dil dosyaları sözlükle uyumsuz: scripts/sync_taxonomy_tag_strings.py çalıştırın.",
            file=sys.stderr,
        )
        return 1
    print("Güncellendi." if changed else "Zaten güncel.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
