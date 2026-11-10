#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Arayüzde kalan Çince/Japonca açıklama parçalarını temizler.

Bangumi kaynaklı bazı metinlerde kaynak etiketin kendisi parantez içinde
bırakılmıştı (`izleme listesi (在看)`, `kayıt (条目)`, `(打格子)` ...). Ekranda
CJK metin görünmemesi kuralı gereği bu parçalar TR/EN karşılıklarıyla
değiştirilir ya da tamamen kaldırılır.

Kullanım:
    python3 scripts/fix_cjk_ui_strings.py           # yazar
    python3 scripts/fix_cjk_ui_strings.py --check   # fark varsa 1 döner
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TR_STRINGS = ROOT / "app/src/main/res/values/strings.xml"
EN_STRINGS = ROOT / "app/src/main/res/values-en/strings.xml"

# (eski, yeni) — dosyadaki kaçışlar korunmuş hâliyle yazılır.
TR_PAIRS: list[tuple[str, str]] = [
    ("Canlı çekim (三次元) yapımlarda", "Canlı çekim yapımlarda"),
    ("izleme listenizde (在看)", "izleme listenizde"),
    ("izleme listesinde (在看)", "izleme listesinde"),
    ("izleme listeniz (在看)", "izleme listeniz"),
    ("İzleme listesi (在看) +", "İzleme listesi +"),
    ("开发者平台 → 我的应用 → 创建应用", "Developer Platform → My Apps → Create App"),
    ('"回调地址" (Redirect URI)', '"Redirect URI"'),
    ("kayıt (条目) arama", "kayıt arama"),
    ("koleksiyon durumları (想看/在看/看过/搁置/抛弃)", "koleksiyon durumları (izlenecek/izleniyor/izlendi/ertelendi/bırakıldı)"),
    ("koleksiyon durumu (想看/在看/看过/搁置/抛弃)", "koleksiyon durumu (izlenecek/izleniyor/izlendi/ertelendi/bırakıldı)"),
    ("ilerlemeyi (打格子)", "ilerlemeyi (bölüm işaretleme)"),
    ("Bölüm bazlı ilerleme (打格子)", "Bölüm bazlı ilerleme"),
    ("kayıt \\'抛弃 (Bırakıldı)\\'", "kayıt \\'Bırakıldı\\'"),
    ("Bangumi 收藏 (koleksiyon) listenizi", "Bangumi koleksiyon listenizi"),
    ("(NSFW)条目\\'ları", "(NSFW) kayıtları"),
    ("roman (小说) ve çizim kitabı (画集) kayıtları", "roman ve çizim kitabı kayıtları"),
    ("(个人令牌) —", "—"),
    ("🔥 İlgi (收藏热度)", "🔥 İlgi"),
    ("koleksiyonunuzu (想看 / 在看 / 看过) Kitsugi", "koleksiyonunuzu Kitsugi"),
    ("「Sunflower Studio", "Sunflower Studio"),
]

EN_PAIRS: list[tuple[str, str]] = [
    ("live-action (三次元) works", "live-action works"),
    ("watch list (在看)", "watch list"),
    ("开发者平台 → 我的应用 → 创建应用", "Developer Platform → My Apps → Create App"),
    ('"回调地址" (Redirect URI)', '"Redirect URI"'),
    ("subject search (条目)", "subject search"),
    ("collection states (想看/在看/看过/搁置/抛弃)", "collection states (wish/watching/watched/on hold/dropped)"),
    ("progress (打格子)", "progress (per-episode marking)"),
    ("token (个人令牌) —", "token —"),
    ("Interest (收藏热度)", "Interest"),
    ("collection (想看 / 在看 / 看过)", "collection"),
    ("「Sunflower Studio", "Sunflower Studio"),
]


def apply(path: Path, pairs: list[tuple[str, str]]) -> tuple[int, list[str]]:
    text = path.read_text(encoding="utf-8")
    hits = 0
    leftovers: list[str] = []
    for old, new in pairs:
        count = text.count(old)
        if count:
            hits += count
            text = text.replace(old, new)
        else:
            leftovers.append(old)
    path.write_text(text, encoding="utf-8")
    return hits, leftovers


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="yalnızca doğrula, yazma")
    args = parser.parse_args()

    total = 0
    problems = 0
    for path, pairs in ((TR_STRINGS, TR_PAIRS), (EN_STRINGS, EN_PAIRS)):
        before = path.read_text(encoding="utf-8")
        probe = before
        for old, _new in pairs:
            probe = probe.replace(old, "")
        if args.check:
            changed = probe != before
            if changed:
                problems += 1
                print(f"{path.name}: hâlâ CJK açıklama var", file=sys.stderr)
            continue
        hits, leftovers = apply(path, pairs)
        total += hits
        for old in leftovers:
            print(f"  ! eşleşme yok ({path.parent.name}): {old}")
        print(f"{path}: {hits} değişiklik")

    if args.check:
        if problems:
            print("Çince açıklama kalıntısı var: scripts/fix_cjk_ui_strings.py çalıştırın.", file=sys.stderr)
            return 1
        print("Arayüz metinlerinde CJK açıklama yok.")
        return 0

    print(f"Toplam {total} değişiklik.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
