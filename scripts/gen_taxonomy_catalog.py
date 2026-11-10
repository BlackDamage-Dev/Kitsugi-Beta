#!/usr/bin/env python3
"""Produce the compact, offline search catalog from the existing taxonomy sources.

Sources of truth (never hand-edit the output): RussianTaxonomyDictionary.kt for the
114 canonical genres, the resource-backed groups in bangumi/tags.tsv for tags,
org_latin.tsv + bangumi/orgs.tsv for organizations, and TR/EN Android strings for
localized display. CJK/Cyrillic may occur ONLY in hidden search aliases, not names.

Usage:
    python3 scripts/gen_taxonomy_catalog.py          # generate
    python3 scripts/gen_taxonomy_catalog.py --check  # verify byte-for-byte, no writes
"""
from __future__ import annotations

import argparse
import gzip
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

from check_taxonomy_master import read_russian_entries

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app/src/main/res/raw/taxonomy_catalog.json"
TAGS = ROOT / "app/src/main/resources/bangumi/tags.tsv"
ORGS = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
LATIN = ROOT / "diag/bangumi_master/org_latin.tsv"
TR = ROOT / "app/src/main/res/values/strings.xml"
EN = ROOT / "app/src/main/res/values-en/strings.xml"
ORG_ROLE = re.compile(r"^bangumi_(studio|producer|publisher|network|magazine)_[a-z0-9_]+$")
FOREIGN = re.compile(r"[\u3040-\u30ff\u3400-\u9fff\uac00-\ud7af\u0400-\u052f]")


def android_strings(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for node in ET.parse(path).getroot():
        if node.tag != "string":
            continue
        key = node.attrib["name"]
        if key in values:
            raise ValueError(f"{path}: duplicate string: {key}")
        values[key] = ("".join(node.itertext()).replace("\\'", "'")
                       .replace('\\"', '"').replace("\\n", " "))
    return values


def aliases(candidates: list[str], *names: str) -> list[str]:
    """Retain source-language aliases for INPUT only; never use them as UI text."""
    excluded = {name.casefold() for name in names}
    seen: set[str] = set()
    out: list[str] = []
    for raw in candidates:
        name = raw.strip()
        folded = name.casefold()
        if name and len(name) <= 120 and folded not in excluded and folded not in seen:
            out.append(name)
            seen.add(folded)
    return out


def latin_label(value: str, key: str) -> str:
    if not value.strip() or FOREIGN.search(value):
        raise ValueError(f"{key}: empty or non-Latin display name: {value!r}")
    return value


def build() -> dict[str, object]:
    tr, en = android_strings(TR), android_strings(EN)
    genres: list[dict[str, object]] = []
    for source in read_russian_entries():
        name = f"taxonomy_tag_{source.key}"
        title_tr = latin_label(tr[name], name)
        title_en = latin_label(en[name], name)
        if title_tr != source.turkish or title_en != source.english:
            raise ValueError(f"{name}: dictionary and resources differ")
        genres.append({
            "key": source.key, "tr": title_tr, "en": title_en,
            "malId": source.mal_id,
            "aliases": aliases(source.aliases, title_tr, title_en),
        })
    if len(genres) != 114 or len({item["key"] for item in genres}) != len(genres):
        raise ValueError(f"genre coverage/unique key: {len(genres)} (expected 114)")

    tags: list[dict[str, object]] = []
    for number, line in enumerate(TAGS.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("\t")
        if len(fields) < 3:
            raise ValueError(f"tags.tsv:{number}: missing key/tr/en")
        key, source_tr, source_en, *raw_aliases = fields
        resource = f"bangumi_tag_{key}"
        if resource not in tr and resource not in en:
            continue  # VAULT-only groups must not inflate Android resource IDs.
        if resource not in tr or resource not in en:
            raise ValueError(f"{resource}: TR/EN key mismatch")
        title_tr = latin_label(tr[resource], resource)
        title_en = latin_label(en[resource], resource)
        if (title_tr, title_en) != (source_tr, source_en):
            raise ValueError(f"{resource}: tag store / XML drift")
        tags.append({
            "key": resource, "tr": title_tr, "en": title_en,
            "aliases": aliases(raw_aliases, title_tr, title_en),
        })
    if len(tags) < 600 or len({item["key"] for item in tags}) != len(tags):
        raise ValueError(f"tag coverage/unique key: {len(tags)} (expected >=600)")

    latin: dict[str, str] = {}
    for line in LATIN.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        key, name = line.split("\t", 1)
        if key in latin or not ORG_ROLE.fullmatch(key):
            raise ValueError(f"duplicate/invalid organization key: {key}")
        latin[key] = latin_label(name, key)
    source_aliases: dict[str, list[str]] = defaultdict(list)
    covered: set[str] = set()
    for number, line in enumerate(ORGS.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("\t")
        if len(fields) < 3:
            raise ValueError(f"orgs.tsv:{number}: missing label/key/en")
        label, key, source_name, *rest = fields
        if key not in latin:
            continue
        covered.add(key)
        if source_name != latin[key] or (rest and rest[0] != latin[key]):
            raise ValueError(f"orgs.tsv:{number}: {key} differs from Latin source")
        source_aliases[key].append(label)
    if len(latin) < 9457 or covered != latin.keys():
        raise ValueError(f"organization coverage: map={len(latin)}, store={len(covered)}")

    organizations: list[dict[str, object]] = []
    for key, name in sorted(latin.items(), key=lambda item: (item[1].casefold(), item[0])):
        if tr.get(key) != name or en.get(key) != name:
            raise ValueError(f"{key}: proper name must match in TR and EN")
        role = ORG_ROLE.fullmatch(key).group(1)  # already checked above
        organizations.append({
            "key": key, "role": role, "name": name,
            "aliases": aliases(source_aliases[key], name),
        })
    return {"version": 1, "genres": genres, "tags": tags, "organizations": organizations}


def render() -> bytes:
    return (json.dumps(build(), ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="no writes; fail if output differs")
    args = parser.parse_args()
    try:
        expected = render()
    except (ValueError, KeyError, OSError) as error:
        print(f"Catalog source error: {error}", file=sys.stderr)
        return 1
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_bytes() != expected:
            print(f"Stale catalog: {OUTPUT}; run python3 scripts/gen_taxonomy_catalog.py",
                  file=sys.stderr)
            return 1
    elif not OUTPUT.exists() or OUTPUT.read_bytes() != expected:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(expected)
    payload = json.loads(expected)
    print(f"Catalog OK: {len(payload['genres'])} genres, {len(payload['tags'])} tags, "
          f"{len(payload['organizations'])} organizations; "
          f"{len(expected):,} bytes (gzip {len(gzip.compress(expected, mtime=0)):,}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
