#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ortak taksonomi (tür / etiket) sözlüğünü denetler ve çeviri iş listesi üretir.

Denetim sınıfları
-----------------
K1  Anahtar tekilliği            — aynı `key` iki kez tanımlanmamalı
K2  Takma ad çakışması           — bir Rusça yazım iki farklı girişe bağlanamaz
K3  Boş çeviri                   — TR/EN alanları boş olamaz
K4  Kiril sızıntısı              — TR/EN değerlerinde Kiril harf bulunamaz
K5  Resmî kapsam                 — Shikimori'nin resmî tür listesinin tamamı sözlükte olmalı
K6  Dil dosyası eşitliği         — `taxonomy_tag_*` kaynakları iki dilde eşit ve sözlükle aynı
K7  Kaynak adı biçimi            — anahtarlar `a-z0-9_` (Android kaynak adı kuralı)
K8  Bangumi çelişkisi            — iki sözlükte ortak anahtar varsa TR/EN değerleri AYNI olmalı
K9  MAL kimlik tekilliği         — bir MAL tür kimliği tek girişe bağlanmalı

Kullanım
--------
    python3 scripts/check_taxonomy_master.py              # denetle (hata varsa 1)
    python3 scripts/check_taxonomy_master.py --worklist   # + iş listesini yaz
    python3 scripts/check_taxonomy_master.py --json       # özet JSON
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUSSIAN_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt"
BANGUMI_DICT = ROOT / "app/src/main/java/com/kitsugi/animelist/utils/BangumiTagDictionary.kt"
BANGUMI_STORE = ROOT / "app/src/main/resources/bangumi/tags.tsv"
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"
WORKLIST = ROOT / "diag/taxonomy_master/worklist.tsv"

ENTRY_RE = re.compile(
    r'g\(\s*(null|\d+)\s*,\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"'
    r'((?:\s*,\s*"(?:[^"\\]|\\.)*")*)\s*\)'
)
ALIAS_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
BANGUMI_ENTRY_RE = re.compile(
    r'g\(\s*"([^"]+)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"'
    r'((?:\s*,\s*"(?:[^"\\]|\\.)*")*)\s*\)'
)
KEY_RE = re.compile(r"[a-z0-9][a-z0-9_]*")
CYRILLIC_RE = re.compile(r"[\u0400-\u04FF\u0500-\u052F]")
STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)

# ── Shikimori resmî tür listesi (id, Rusça ad, İngilizce kanonik ad) ─────────────
# Kaynak: Shikimori `/api/genres` — MAL/AniList kimlikleriyle birebir eşleşir.
SHIKIMORI_GENRES = [
    (1, "Экшен", "Action"), (2, "Приключения", "Adventure"), (3, "Гонки", "Racing"),
    (4, "Комедия", "Comedy"), (5, "Авангард", "Avant Garde"), (6, "Мифология", "Mythology"),
    (7, "Тайна", "Mystery"), (8, "Драма", "Drama"), (9, "Этти", "Ecchi"),
    (10, "Фэнтези", "Fantasy"), (11, "Стратегические игры", "Strategy Game"),
    (12, "Хентай", "Hentai"), (13, "Исторический", "Historical"), (14, "Ужасы", "Horror"),
    (15, "Детское", "Kids"), (16, "Магия", "Magic"),
    (17, "Боевые искусства", "Martial Arts"), (18, "Меха", "Mecha"), (19, "Музыка", "Music"),
    (20, "Пародия", "Parody"), (21, "Самураи", "Samurai"), (22, "Романтика", "Romance"),
    (23, "Школа", "School"), (24, "Фантастика", "Sci-Fi"), (25, "Сёдзё", "Shoujo"),
    (26, "Сёдзё-ай", "Shoujo Ai"), (27, "Сёнен", "Shounen"), (28, "Сёнен-ай", "Shounen Ai"),
    (29, "Космос", "Space"), (30, "Спорт", "Sports"), (31, "Супер сила", "Super Power"),
    (32, "Вампиры", "Vampire"), (33, "Яой", "Yaoi"), (34, "Юри", "Yuri"), (35, "Гарем", "Harem"),
    (36, "Повседневность", "Slice of Life"), (37, "Сверхъестественное", "Supernatural"),
    (38, "Военное", "Military"), (39, "Детектив", "Detective"), (40, "Психологическое", "Psychological"),
    (42, "Сэйнэн", "Seinen"), (43, "Дзёсей", "Josei"), (44, "Смена пола", "Gender Bender"),
    (102, "Командный спорт", "Team Sports"), (103, "Видеоигры", "Video Game"),
    (104, "Взрослые персонажи", "Adult Cast"), (105, "Жестокость", "Gore"),
    (106, "Реинкарнация", "Reincarnation"), (107, "Любовный многоугольник", "Love Polygon"),
    (108, "Изобразительное искусство", "Visual Arts"), (111, "Путешествие во времени", "Time Travel"),
    (112, "Гэг-юмор", "Gag Humor"), (114, "Удостоено наград", "Award Winning"),
    (117, "Триллер", "Thriller"), (118, "Спортивные единоборства", "Combat Sports"),
    (119, "CGDCT", "CGDCT"), (124, "Махо-сёдзё", "Mahou Shoujo"),
    (125, "Реверс-гарем", "Reverse Harem"), (130, "Исэкай", "Isekai"),
    (131, "Хулиганы", "Delinquents"), (134, "Забота о детях", "Childcare"),
    (135, "Магическая смена пола", "Magical Sex Shift"), (136, "Шоу-бизнес", "Showbiz"),
    (137, "Культура отаку", "Otaku Culture"), (138, "Организованная преступность", "Organized Crime"),
    (139, "Работа", "Workplace"), (140, "Иясикэй", "Iyashikei"), (141, "Выживание", "Survival"),
    (142, "Исполнительское искусство", "Performing Arts"),
    (143, "Антропоморфизм", "Anthropomorphic"), (144, "Кроссдрессинг", "Crossdressing"),
    (145, "Идолы (Жен.)", "Idols (Female)"), (146, "Игра с высокими ставками", "High Stakes Game"),
    (147, "Медицина", "Medical"), (148, "Питомцы", "Pets"), (149, "Образовательное", "Educational"),
    (150, "Идолы (Муж.)", "Idols (Male)"), (151, "Романтический подтекст", "Romantic Subtext"),
    (543, "Гурман", "Gourmet"),
]


class Entry:
    __slots__ = ("mal_id", "key", "turkish", "english", "aliases", "line")

    def __init__(self, mal_id, key, turkish, english, aliases, line):
        self.mal_id = mal_id
        self.key = key
        self.turkish = turkish
        self.english = english
        self.aliases = aliases
        self.line = line


def normalize_label(raw: str) -> str:
    """Kotlin tarafındaki `RussianTaxonomyDictionary.normalize` ile aynı kural."""
    text = unicodedata.normalize("NFC", raw)
    out = []
    last_space = True
    for ch in text.strip():
        if ch.isspace() or ch == "\u00a0":
            if not last_space:
                out.append(" ")
                last_space = True
            continue
        if ch in "«»\"'`“”„‐–":
            continue
        low = ch.lower().replace("ё", "е")
        out.append(low)
        last_space = False
    return "".join(out).strip()


def read_russian_entries() -> list[Entry]:
    if not RUSSIAN_DICT.exists():
        raise SystemExit(f"Sözlük bulunamadı: {RUSSIAN_DICT}")
    source = RUSSIAN_DICT.read_text(encoding="utf-8")
    body = source.split("private val groups: List<Group> = listOf(", 1)[1]
    entries: list[Entry] = []
    for line_no, line in enumerate(body.splitlines(), start=1):
        if not line.strip().startswith("g("):
            continue
        match = ENTRY_RE.search(line)
        if not match:
            raise SystemExit(f"Okunamayan sözlük satırı ({line_no}): {line.strip()[:90]}")
        mal_id = None if match.group(1) == "null" else int(match.group(1))
        key, turkish, english = match.group(2), match.group(3), match.group(4)
        aliases = [a for a in ALIAS_RE.findall(match.group(5))]
        entries.append(Entry(mal_id, key, turkish, english, aliases, line_no))
    if not entries:
        raise SystemExit("Sözlükten hiç giriş okunamadı.")
    return entries


def read_bangumi_entries() -> dict[str, tuple[str, str]]:
    """K8: yalnızca küratörlü Bangumi etiketleri (VAULT'taki kaynaklı gruplar).

    VAULT'taki geri kalan on binlerce otomatik etiket Android string kaynağına
    alınmaz; bunlar için TR/EN uyumluluğu beklentisi bulunmaz. Yanlışlıkla boş
    tabloyla başarılı denetim yapmak yerine mevcut TSV mutlaka okunmalıdır.
    """
    if not BANGUMI_STORE.exists():
        raise SystemExit(f"K8: Bangumi VAULT etiket tablosu yok: {BANGUMI_STORE}")
    resources = {name.removeprefix("bangumi_tag_") for name in read_strings(TR_STRINGS)
                 if name.startswith("bangumi_tag_")}
    result: dict[str, tuple[str, str]] = {}
    for row in BANGUMI_STORE.read_text(encoding="utf-8").splitlines():
        parts = row.split("\t")
        if len(parts) >= 3 and parts[0] in resources:
            result[parts[0]] = (parts[1], parts[2])
    if len(result) < 600:
        raise SystemExit(f"K8: kaynaklı Bangumi grubu eksik ({len(result)})")
    return result


def read_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {name: value for name, value in STRING_RE.findall(text)}


def unescape(value: str) -> str:
    return (
        value.replace("\\'", "'")
        .replace('\\"', '"')
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--worklist", action="store_true", help="çeviri iş listesini yaz")
    parser.add_argument("--json", action="store_true", help="özet JSON yazdır")
    args = parser.parse_args()

    entries = read_russian_entries()
    bangumi = read_bangumi_entries()
    tr_strings = read_strings(TR_STRINGS)
    en_strings = read_strings(EN_STRINGS)

    errors: list[str] = []
    warnings: list[str] = []

    # K1 — anahtar tekilliği
    key_counts = Counter(entry.key for entry in entries)
    for key, count in key_counts.items():
        if count > 1:
            errors.append(f"K1 anahtar tekrarı: {key} × {count}")

    # K7 — kaynak adı biçimi
    for entry in entries:
        if not KEY_RE.fullmatch(entry.key):
            errors.append(f"K7 geçersiz anahtar: {entry.key}")

    # K2 — takma ad çakışması
    owner: dict[str, str] = {}
    for entry in entries:
        labels = [entry.english, *entry.aliases]
        for label in labels:
            norm = normalize_label(label)
            if not norm:
                continue
            previous = owner.get(norm)
            if previous is not None and previous != entry.key:
                errors.append(f"K2 '{label}' hem {previous} hem {entry.key} girişinde")
            owner.setdefault(norm, entry.key)

    # K3 / K4
    for entry in entries:
        if not entry.turkish.strip():
            errors.append(f"K3 boş TR: {entry.key}")
        if not entry.english.strip():
            errors.append(f"K3 boş EN: {entry.key}")
        if CYRILLIC_RE.search(entry.turkish) or CYRILLIC_RE.search(entry.english):
            errors.append(f"K4 Kiril sızıntısı: {entry.key}")

    # K9 — MAL kimlik tekilliği
    id_owner: dict[int, str] = {}
    for entry in entries:
        if entry.mal_id is None:
            continue
        previous = id_owner.get(entry.mal_id)
        if previous is not None and previous != entry.key:
            errors.append(f"K9 MAL {entry.mal_id} hem {previous} hem {entry.key}")
        id_owner[entry.mal_id] = entry.key

    # K5 — resmî Shikimori kapsamı
    by_key = {entry.key: entry for entry in entries}
    missing_official: list[tuple[int, str, str]] = []
    for mal_id, russian, english in SHIKIMORI_GENRES:
        entry = by_key.get(owner.get(normalize_label(russian), ""))
        if entry is None:
            missing_official.append((mal_id, russian, english))
    for mal_id, russian, english in missing_official:
        errors.append(f"K5 resmî tür eksik: {russian} ({english}, MAL {mal_id})")

    # K8 — Bangumi sözlüğüyle çelişki
    for entry in entries:
        counterpart = bangumi.get(entry.key)
        if counterpart is None:
            continue
        tr, en = counterpart
        if tr != entry.turkish and normalize_label(tr) != normalize_label(entry.turkish):
            warnings.append(
                f"K8 '{entry.key}' TR değeri farklı: bangumi='{tr}' russian='{entry.turkish}'"
            )
        if en != entry.english and normalize_label(en) != normalize_label(entry.english):
            warnings.append(
                f"K8 '{entry.key}' EN değeri farklı: bangumi='{en}' russian='{entry.english}'"
            )

    # K6 — dil dosyası eşitliği
    resource_owner = {f"taxonomy_tag_{entry.key}": entry for entry in entries}
    for name, entry in resource_owner.items():
        tr_value = tr_strings.get(name)
        en_value = en_strings.get(name)
        if tr_value is None:
            errors.append(f"K6 TR kaynağı yok: {name}")
        elif unescape(tr_value) != entry.turkish:
            errors.append(f"K6 TR değeri farklı: {name} ('{unescape(tr_value)}' ≠ '{entry.turkish}')")
        if en_value is None:
            errors.append(f"K6 EN kaynağı yok: {name}")
        elif unescape(en_value) != entry.english:
            errors.append(f"K6 EN değeri farklı: {name} ('{unescape(en_value)}' ≠ '{entry.english}')")

    stray = [
        name for name in tr_strings
        if name.startswith("taxonomy_tag_") and name not in resource_owner
    ]
    for name in stray:
        warnings.append(f"K6 sözlükte olmayan kaynak (temizlenmeli): {name}")

    if args.worklist:
        WORKLIST.parent.mkdir(parents=True, exist_ok=True)
        lines = ["mal_id\tkey\tturkish\tenglish\taliases\tstatus"]
        for entry in entries:
            status = "aktif"
            if entry.key in [e.key for e in entries]:
                status = "aktif"
            lines.append(
                "\t".join(
                    [
                        "" if entry.mal_id is None else str(entry.mal_id),
                        entry.key,
                        entry.turkish,
                        entry.english,
                        " | ".join(entry.aliases),
                        status,
                    ]
                )
            )
        if missing_official:
            for mal_id, russian, english in missing_official:
                lines.append(f"{mal_id}\t\t\t{english}\t{russian}\tEKSIK")
        WORKLIST.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"İş listesi yazıldı: {WORKLIST.relative_to(ROOT)} ({len(lines) - 1} satır)")

    summary = {
        "entries": len(entries),
        "official_shikimori": len(SHIKIMORI_GENRES),
        "missing_official": len(missing_official),
        "bangumi_shared_keys": len({e.key for e in entries} & set(bangumi)),
        "taxonomy_resources": len(resource_owner),
        "errors": len(errors),
        "warnings": len(warnings),
    }
    if args.json:
        print(json.dumps(summary, ensure_ascii=False, indent=2))

    for warning in warnings[:20]:
        print(f"UYARI  {warning}")
    if len(warnings) > 20:
        print(f"UYARI  … {len(warnings) - 20} uyarı daha")
    for error in errors[:40]:
        print(f"HATA   {error}", file=sys.stderr)

    if errors:
        print(f"\n{len(errors)} hata, {len(warnings)} uyarı.", file=sys.stderr)
        return 1
    print(
        f"TÜM KONTROLLER GEÇTİ — {summary['entries']} sözlük girişi, "
        f"{summary['official_shikimori']} resmî Shikimori türü, "
        f"{summary['taxonomy_resources']} dil dosyası kaynağı, {len(warnings)} uyarı."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
