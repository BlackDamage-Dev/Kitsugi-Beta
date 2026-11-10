#!/usr/bin/env python3
"""Search taxonomy contract: generated source parity, UI wiring, and alias behavior.

Run `python3 scripts/check_search_catalog.py --check` in CI. This is a static
contract; a device/API test is still needed for T55–T64 provider filtering.
"""
from __future__ import annotations

import argparse
import gzip
import json
import re
import sys
import unicodedata
from pathlib import Path

import gen_taxonomy_catalog as generator

ROOT = Path(__file__).resolve().parents[1]
SEARCH = ROOT / "app/src/main/java/com/kitsugi/animelist/ui/screens/search"


def fold(value: str) -> str:
    return "".join(ch for ch in unicodedata.normalize("NFD", value.lower().replace("ı", "i"))
                   if unicodedata.category(ch) != "Mn").strip()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="CI-compatible no-write check")
    parser.parse_args()
    problems: list[str] = []
    try:
        expected = generator.render()
        actual = generator.OUTPUT.read_bytes()
    except (OSError, ValueError, KeyError) as error:
        print(f"Catalog unreadable: {error}", file=sys.stderr)
        return 1
    if actual != expected:
        problems.append("res/raw/taxonomy_catalog.json differs from its source data")
    catalog = json.loads(actual)
    genres, tags, orgs = (catalog[k] for k in ("genres", "tags", "organizations"))
    if len(genres) != 114 or len(tags) < 600 or len(orgs) < 9457:
        problems.append(f"incomplete catalog: {len(genres)}/{len(tags)}/{len(orgs)}")
    if len(gzip.compress(actual, mtime=0)) > 400_000:
        problems.append("compressed catalog exceeds the 400 KB APK budget")

    for query in ("aksiyon", "action", "экшен"):
        matched = [entry for entry in genres if any(
            fold(query) in fold(label) for label in [entry["tr"], entry["en"], *entry["aliases"]]
        )]
        if not any(entry["key"] == "action" for entry in matched):
            problems.append(f"action alias not found: {query}")
    if not any(row["key"] == "bangumi_studio_mappa" and row["name"] == "MAPPA"
               for row in orgs):
        problems.append("MAPPA studio missing from organization catalog")
    if not any(row["key"] == "bangumi_studio_kyoto_animation" and "京阿尼" in row["aliases"]
               for row in orgs):
        problems.append("Kyoto Animation source-language alias missing")

    sheet = (SEARCH / "GenresTagsSheet.kt").read_text(encoding="utf-8-sig")
    if re.search(r"\bval\s+all(?:Genres|Tags)\s*=\s*listOf\(", sheet):
        problems.append("GenresTagsSheet still has hardcoded genre/tag lists")
    if "TaxonomyCatalog.load" not in sheet or "it.matchesFolded(needle)" not in sheet:
        problems.append("GenresTagsSheet does not load/search the shared catalog")
    state = (SEARCH / "SearchUiState.kt").read_text()
    if "val organizations: List<OrgFilter> = emptyList()" not in state or "organizations.isEmpty()" not in state:
        problems.append("SearchFilters has no backwards-compatible organization field/default")
    screen = (SEARCH / "SearchScreen.kt").read_text()
    if "OrgFilterSheet(" not in screen or "SearchOrganizationChips(" not in screen:
        problems.append("organization picker or filter-bar chip is not wired to search UI")
    for problem in problems:
        print("FAIL:", problem, file=sys.stderr)
    if problems:
        return 1
    print(f"Search catalog OK: {len(genres)} genres, {len(tags)} tags, {len(orgs)} Latin orgs; "
          "TR/EN/RU aliases and UI wiring present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
