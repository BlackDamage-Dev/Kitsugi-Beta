#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Kitsugi sekme/pager performans denetleyicisi  (KASMA FIX — 2026-11-05)

Neden var?
----------
Detay/profile ekranlarındaki sekmeler `HorizontalPager` ile kaydırılıyor. Compose'da
`PagerState.currentPageOffsetFraction` **kaydırma boyunca her karede** değişir. Bu
değer KOMPOZİSYON fazında okunursa, parmak her hareket ettiğinde ekranın (ve görünür
sekmenin) tamamı yeniden kompoze edilir. Cihaz raporlarında bu, 1263 ms'lik kare
süreleri olarak görüldü.

Kural: `currentPageOffsetFraction` / `currentPage` yalnızca şu fazlarda okunabilir:
    * `graphicsLayer { }` / `drawBehind { }` / `drawWithContent { }`  → ÇİZİM fazı
    * `derivedStateOf { }` (+ layout fazında okuma)                    → YERLEŞİM fazı
    * `snapshotFlow { }` / `LaunchedEffect { }` / `SideEffect { }`     → kompozisyon dışı
    * `Modifier.layout { }` içinde `derivedStateOf` üzerinden okuma    → YERLEŞİM fazı

Kullanım:
    python3 scripts/kitsugi_pager_perf_guard.py            # tüm ağaç
    python3 scripts/kitsugi_pager_perf_guard.py --path app/src/main/java/com/kitsugi/animelist/ui
    python3 scripts/kitsugi_pager_perf_guard.py --strict   # INFO'ları da hata say

Çıkış kodu: 0 = temiz, 1 = ihlal var.
"""

from __future__ import annotations

import argparse
import os
import re
import sys

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_PATH = os.path.join(ROOT, "app", "src", "main", "java", "com", "kitsugi", "animelist")

# Çizim/yerleşim fazı veya kompozisyon-dışı bloklar (güvenli okuma)
SAFE_BLOCKS = (
    "graphicsLayer",
    "drawBehind",
    "drawWithContent",
    "drawWithCache",
    "derivedStateOf",
    "snapshotFlow",
    "LaunchedEffect",
    "SideEffect",
    "DisposableEffect",
    "Modifier.layout",
    "layout {",
)

# YÜKSEK RİSK: kaydırma boyunca her karede değişen değer → kompozisyonda okunamaz.
HOT_STATE = re.compile(r"\b(pagerState|tabPagerState)\s*\.\s*currentPageOffsetFraction\b")

# DÜŞÜK RİSK (bilgi): yalnızca sayfa DEĞİŞİMİNDE tetiklenir; tab bar vurgusu vb. için normaldir.
LOW_STATE = re.compile(r"\b(pagerState|tabPagerState)\s*\.\s*currentPage\b")

# Zaten onaylı istisnalar (gerekçesiyle birlikte)
ALLOW_FILES = {
    # Yükseklik politikasının tek kaynağı: derivedStateOf içinde okuyor.
    "DetailPagerHeightPolicy.kt",
    # Çizim fazında okuyan yardımcı (hero göstergeleri).
    "HomeHeroGestureModifiers.kt",
}

# Kaba ama işe yarar: bir satırın ait olduğu en yakın blok açıcısını geriye doğru ara.
def enclosing_block(lines: list[str], idx: int, max_lookback: int = 80):
    indent = len(lines[idx]) - len(lines[idx].lstrip())
    for j in range(idx - 1, max(-1, idx - max_lookback), -1):
        raw = lines[j]
        line = raw.strip()
        if not line or line.startswith("//") or line.startswith("*"):
            continue
        j_indent = len(raw) - len(raw.lstrip())
        if j_indent < indent:
            return line, j_indent
        # Aynı girintide lambda açıcıları (.graphicsLayer { ... } gibi) da olabilir
    return "", -1


def safe_reason(lines: list[str], idx: int):
    """Satır güvenli bir blok içindeyse gerekçe döndürür, değilse None."""
    indent = len(lines[idx]) - len(lines[idx].lstrip())
    for j in range(idx, max(-1, idx - 100), -1):
        raw = lines[j]
        j_indent = len(raw) - len(raw.lstrip())
        if j_indent > indent:
            continue  # daha derin satırlar (önceki ifade parçaları)
        for marker in SAFE_BLOCKS:
            if marker in raw:
                return marker
        stripped = raw.strip()
        # Aynı girintide başka bir lambda/ifade başladıysa yukarı çıkmayı bırak
        if j != idx and stripped.endswith("{") and j_indent == indent:
            return None
    return None


def check_file(path: str, strict: bool):
    issues = []
    name = os.path.basename(path)
    lines = open(path, encoding="utf-8").read().split("\n")

    for i, raw in enumerate(lines):
        code = raw.split("//")[0]
        if not code.strip() or code.strip().startswith("*"):
            continue

        # ── Kural 1: pager state'in kompozisyonda okunması ──
        if HOT_STATE.search(code) and name not in ALLOW_FILES:
            if safe_reason(lines, i) is None:
                issues.append(("FAIL", i + 1,
                               "`currentPageOffsetFraction` KOMPOZİSYONDA okunuyor → her karede tam yeniden "
                               "kompozisyon. graphicsLayer/drawBehind/derivedStateOf(Layout)/snapshotFlow "
                               "içine taşı. Ortak çözüm: ui/utils/DetailPagerHeightPolicy.kt"))
        if strict and LOW_STATE.search(code) and name not in ALLOW_FILES:
            if safe_reason(lines, i) is None:
                issues.append(("INFO", i + 1,
                               "`currentPage` kompozisyonda okunuyor — sayfa başına 1 kez tetiklenir, "
                               "kasma sebebi DEĞİL; yalnızca çok ağır bir ekranın kök kapsamında okunuyorsa "
                               "ilgili alt bileşene taşımak iyi olur."))

        # ── Kural 2: lazy item içinde indexOf (O(n²)) ──
        if ".indexOf(" in code:
            opener, _ind = enclosing_block(lines, i, max_lookback=120)
            if re.search(r"\b(items|itemsIndexed|item)\s*\(", opener):
                issues.append(("WARN", i + 1,
                               "Lazy item içinde `indexOf` → O(n²). Öğeleri `withIndex()` ile bir kez indeksle."))

        # ── Kural 3: LazyListState, anahtarsız döngü içinde ──
        if "rememberLazyListState()" in code:
            opener, _ind = enclosing_block(lines, i, max_lookback=40)
            if re.search(r"\.forEach\s*[:{]|for\s*\(", opener) and "key(" not in opener:
                issues.append(("WARN", i + 1,
                               "Anahtarsız döngü içinde `rememberLazyListState()` → liste değişince kaydırma "
                               "konumu yanlış satıra atlar. `remember(<anahtar>) { LazyListState() }` kullan."))

    # ── Kural 4 (dosya düzeyi): Infinity ölçüm + dikey kaydırılabilir çocuk ──
    whole = "\n".join(lines)
    if "Constraints.Infinity" in whole and re.search(r"LazyColumn\s*\(|LazyVerticalGrid\s*\(|verticalScroll\s*\(", whole):
        issues.append(("INFO", 0,
                       "Dosyada hem `Constraints.Infinity` hem dikey kaydırılabilir bileşen var. "
                       "Sonsuz yükseklik verilen bir pager SAYFASI LazyColumn/verticalScroll içerirse "
                       "uygulama `IllegalStateException` ile kapanır — sayfa içerik ağacını doğrula."))

    return issues


def main() -> int:
    ap = argparse.ArgumentParser(description="Kitsugi pager/sekme performans denetleyicisi")
    ap.add_argument("--path", default=DEFAULT_PATH)
    ap.add_argument("--strict", action="store_true", help="INFO/WARN'ları da hata say")
    args = ap.parse_args()

    fails = warns = infos = 0
    for dirpath, _dirnames, filenames in os.walk(args.path):
        for fn in sorted(filenames):
            if not fn.endswith(".kt"):
                continue
            p = os.path.join(dirpath, fn)
            for level, line_no, msg in check_file(p, args.strict):
                rel = os.path.relpath(p, ROOT)
                loc = f"{rel}:{line_no}" if line_no else rel
                print(f"[{level}] {loc}\n        {msg}")
                if level == "FAIL":
                    fails += 1
                elif level == "WARN":
                    warns += 1
                else:
                    infos += 1

    print()
    print(f"Sonuç: {fails} hata, {warns} uyarı, {infos} bilgi")
    if fails:
        print("❌ KASMA RİSKİ: yukarıdaki FAIL satırları düzeltilmeli.")
        return 1
    if args.strict and (warns or infos):
        return 1
    print("✅ Sekme/pager performans kuralları temiz.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
