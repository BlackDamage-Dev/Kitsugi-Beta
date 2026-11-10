#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bangumi etiket sözlüğünü dil dosyalarıyla eşitler.

Bangumi VAULT'un `app/src/main/resources/bangumi/tags.tsv` tablosundaki
kaynak dosyası olan (`bangumi_tag_<key>`) küratörlü girişleri TR/EN XML ile eşitler.
VAULT'taki on binlerce otomatik etikete yeni Android kaynak kimliği açmaz. Kaynak
olmayan elle yazılmış çevirileri de silmez.

Kullanım:
    python3 scripts/sync_bangumi_tag_strings.py          # dosyaları günceller
    python3 scripts/sync_bangumi_tag_strings.py --check  # fark varsa 1 döner (CI)
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TAG_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

TR_MARKER = "    <!-- Bangumi Etiketleri (Çince/Japonca kaynak değerler) -->"
EN_MARKER = "    <!-- Bangumi Tags (Chinese/Japanese source values) -->"

KEY_RE = re.compile(r"[a-z0-9][a-z0-9_]*")
RESOURCE_RE = re.compile(r'<string name="bangumi_tag_([a-z0-9_]+)"')


def read_entries() -> list[tuple[str, str, str]]:
    """VAULT tablosundaki kaynaklı girişleri sırasıyla okur (Android ID tavanı)."""
    tr_keys = set(RESOURCE_RE.findall(TR_STRINGS.read_text(encoding="utf-8")))
    en_keys = set(RESOURCE_RE.findall(EN_STRINGS.read_text(encoding="utf-8")))
    if tr_keys != en_keys:
        raise SystemExit("Bangumi etiket anahtarları TR/EN dosyalarında farklı")
    entries: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for number, raw in enumerate(TAG_STORE.read_text(encoding="utf-8").splitlines(), 1):
        fields = raw.split("\t")
        if len(fields) < 3:
            raise SystemExit(f"tags.tsv satır {number}: key/tr/en eksik")
        key, tr, en = fields[:3]
        if key in seen:
            raise SystemExit(f"tags.tsv yinelenen anahtar: {key}")
        seen.add(key)
        if key in tr_keys:
            if not KEY_RE.fullmatch(key) or not tr or not en:
                raise SystemExit(f"tags.tsv geçersiz kaynak: {key}")
            entries.append((key, tr, en))
    if len(entries) < 600:
        raise SystemExit(f"Küratörlü Bangumi etiketi eksik: {len(entries)} < 600")
    return entries


def escape(value: str) -> str:
    """Android string kaynağı için kaçış."""
    out = (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", r"\'")
        .replace('"', r"\"")
    )
    return out


def render(entries: list[tuple[str, str, str]], marker: str, turkish: bool) -> str:
    lines = [marker]
    lines.append("    <!-- OTOMATİK ÜRETİLDİ: scripts/sync_bangumi_tag_strings.py -->"
                 if turkish else
                 "    <!-- AUTO-GENERATED: scripts/sync_bangumi_tag_strings.py -->")
    for key, tr, en in entries:
        value = escape(tr if turkish else en)
        lines.append(f'    <string name="bangumi_tag_{key}">{value}</string>')
    return "\n".join(lines)


AUTO_COMMENT_PREFIXES = ("<!-- OTOMATİK ÜRETİLDİ", "<!-- AUTO-GENERATED")

BANGUMI_TAG_PREFIX = '<string name="bangumi_tag_'


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


def patch(path: Path, marker: str, block: str, generated: set[str], check: bool) -> bool:
    """Üretilen bloğu **yerinde** değiştirir; dosyanın geri kalanına dokunmaz.

    Eski sürüm, işaretçiden sonraki HER ŞEYİ silip yerine `</resources>` yazıyordu.
    Bangumi etiket blokları artık dosyanın sonu değil (araya kurum bloğu ve başka
    bölümler girmiş durumda) — bu davranış binlerce satırı siliyordu.

    1. Yalnızca VAULT'ta bulunan kaynaklı `bangumi_tag_*` kayıtlarını ve üretim
       yorumlarını kaldırır (elle eklenen çeviriler korunur),
    2. işaretçi satırının yerine **tek parça** halinde yeniden yazar.

    Böylece blok araya giren kurum kaynaklarını (`bangumi_studio_*` vb.) ezmeden
    yeniden birleştirilir ve `--check` idempotent kalır.
    """
    text = path.read_text(encoding="utf-8")
    if marker not in text:
        raise SystemExit(f"{path} içinde işaret satırı bulunamadı: {marker}")

    lines = text.splitlines()
    marker_idx = next(i for i, line in enumerate(lines) if line == marker)

    rebuilt: list[str] = []
    for index, line in enumerate(lines):
        stripped = line.strip()
        # Yalnızca BU betiğin otomatik yorumu + kendi kaynakları kaldırılır; taksonomi
        # üretecinin yorumuna dokunulmaz (iki betik art arda çalışabilir).
        found = RESOURCE_RE.match(stripped)
        if index != marker_idx and (
            (found is not None and found.group(1) in generated)
            or ("sync_bangumi_tag_strings.py" in stripped and stripped.startswith(AUTO_COMMENT_PREFIXES))
        ):
            continue
        if index == marker_idx:
            rebuilt.extend(block.splitlines())
            continue
        rebuilt.append(line)

    rebuilt = _collapse_blank_runs(rebuilt)
    updated = "\n".join(rebuilt)
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
    generated = {key for key, _, _ in entries}
    changed = False
    changed |= patch(TR_STRINGS, TR_MARKER, render(entries, TR_MARKER, True), generated, args.check)
    changed |= patch(EN_STRINGS, EN_MARKER, render(entries, EN_MARKER, False), generated, args.check)

    print(f"{len(entries)} Bangumi etiketi işlendi.")
    if args.check and changed:
        print("Dil dosyaları sözlükle uyumsuz: scripts/sync_bangumi_tag_strings.py çalıştırın.", file=sys.stderr)
        return 1
    print("Güncellendi." if changed else "Zaten güncel.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
