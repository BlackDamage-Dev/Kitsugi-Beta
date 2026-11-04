#!/usr/bin/env python3
"""
Kitsugi Glyph System — tek kaynak üreteci.

Bu betik iki şey üretir:
  1) app/src/main/java/com/kitsugi/animelist/ui/theme/icons/KitsugiGlyphs.kt
     (Jetpack Compose ImageVector seti — elle çizilmiş, her biri kendine özgü)
  2) gorseller/kitsugi_ikon_seti.png
     (tasarım önizlemesi — kodla birebir aynı path verisinden üretilir)

Tasarım kuralları (tüm sette ortak, ama her glyph'in silueti kendine özgü):
  * 24x24 grid, güvenli alan 2.4–21.6
  * 1.75 birim çizgi kalınlığı, yuvarlak uç/birleşim
  * dolu (fill) alanlar sadece vurgu ögesi olarak kullanılır

Kullanım:
  python3 tools/kitsugi_glyphs/generate_kitsugi_glyphs.py
"""

import math
import os

# ─────────────────────────── geometri yardımcıları ───────────────────────────


def n(v):
    """Sayıyı SVG/Kotlin dostu biçime çevir (gereksiz sıfırları at)."""
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def pt(cx, cy, r, deg):
    a = math.radians(deg)
    return (cx + r * math.cos(a), cy + r * math.sin(a))


def P(p):
    return f"{n(p[0])},{n(p[1])}"


def circ(cx, cy, r):
    """Tam daire (iki yay ile)."""
    return (
        f"M{n(cx + r)},{n(cy)} "
        f"A{n(r)},{n(r)} 0 1 1 {n(cx - r)},{n(cy)} "
        f"A{n(r)},{n(r)} 0 1 1 {n(cx + r)},{n(cy)} Z"
    )


def ell(cx, cy, rx, ry):
    return (
        f"M{n(cx + rx)},{n(cy)} "
        f"A{n(rx)},{n(ry)} 0 1 1 {n(cx - rx)},{n(cy)} "
        f"A{n(rx)},{n(ry)} 0 1 1 {n(cx + rx)},{n(cy)} Z"
    )


def arc(cx, cy, r, deg_from, deg_to):
    """Saat yönünde (ekranda) artan açıyla, from -> to yayı."""
    x0, y0 = pt(cx, cy, r, deg_from)
    x1, y1 = pt(cx, cy, r, deg_to)
    delta = (deg_to - deg_from) % 360
    large = 1 if delta > 180 else 0
    return f"M{P((x0, y0))} A{n(r)},{n(r)} 0 {large} 1 {P((x1, y1))}"


def head(tip, direction_deg, size=2.5, spread=40):
    """Ok başı: tip noktası + yön (derece, ekran koordinatı)."""
    x, y = tip
    a1 = direction_deg + 180 - spread
    a2 = direction_deg + 180 + spread
    p1 = (x + size * math.cos(math.radians(a1)), y + size * math.sin(math.radians(a1)))
    p2 = (x + size * math.cos(math.radians(a2)), y + size * math.sin(math.radians(a2)))
    return f"M{P(p1)} L{P((x, y))} L{P(p2)}"


def arc_arrow(cx, cy, r, deg_from, deg_to, head_at="end", size=2.5):
    """Yay + ucunda ok başı (yay artan açı yönünde ilerler)."""
    d = arc(cx, cy, r, deg_from, deg_to)
    if head_at == "end":
        deg = deg_to
        # artan açı yönündeki teğet
        dir_ = deg + 90
    else:
        deg = deg_from
        dir_ = deg - 90
    tip = pt(cx, cy, r, deg)
    return [d, head(tip, dir_, size=size)]


def rr(x, y, w, h, r):
    """Yuvarlatılmış dikdörtgen."""
    return (
        f"M{n(x + r)},{n(y)} H{n(x + w - r)} "
        f"A{n(r)},{n(r)} 0 0 1 {n(x + w)},{n(y + r)} "
        f"V{n(y + h - r)} A{n(r)},{n(r)} 0 0 1 {n(x + w - r)},{n(y + h)} "
        f"H{n(x + r)} A{n(r)},{n(r)} 0 0 1 {n(x)},{n(y + h - r)} "
        f"V{n(y + r)} A{n(r)},{n(r)} 0 0 1 {n(x + r)},{n(y)} Z"
    )


def star(cx, cy, outer, inner, points=5, rot=-90):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        pts.append(pt(cx, cy, r, rot + i * (360 / (points * 2))))
    return "M" + " L".join(P(p) for p in pts) + " Z"


def poly(*pts, close=True):
    d = "M" + " L".join(P(p) for p in pts)
    return d + (" Z" if close else "")


S, F = "s", "f"  # stroke / fill

# ──────────────────────────────── glyph seti ────────────────────────────────
# Her glyph: (isim, [(tür, pathData), ...])
# Siluetler kasten birbirinden farklı: aynı işi yapan iki buton asla aynı
# görünmez. Sıra korunur (önizleme tablosu bu sırayı kullanır).

GLYPHS = [
    # ── alt menü / ana gezinme ──────────────────────────────────────────────
    ("Explore", [
        (S, circ(12, 12, 8.6)),
        (S, "M12,5.9 L14,10 L18.1,12 L14,14 L12,18.1 L10,14 L5.9,12 L10,10 Z"),
    ]),
    ("MyList", [
        (S, rr(5.0, 5.4, 3.0, 14.0, 1.4)),
        (S, rr(9.6, 7.6, 3.0, 11.8, 1.4)),
        (S, rr(14.2, 4.6, 3.0, 14.8, 1.4)),
        (S, "M4.2,20.9 H19.8"),
    ]),
    ("Search", [
        (S, circ(10.4, 10.4, 6.2)),
        (S, "M15.1,15.1 L20,20"),
        (S, "M7.9,9.6 A3.4,3.4 0 0 1 9.6,7.9"),
    ]),
    ("Profile", [
        (S, circ(12, 8.4, 3.6)),
        (S, "M4.9,19.9 C5.6,15.6 8.5,13.6 12,13.6 C15.5,13.6 18.4,15.6 19.1,19.9"),
        (S, "M12,13.7 V16.3"),
    ]),
    ("Settings", [
        (S, "M12,3.4 L19.4,7.7 V16.3 L12,20.6 L4.6,16.3 V7.7 Z"),
        (S, circ(12, 12, 3.5)),
    ]),

    # ── hesap / ayarlar satırları ───────────────────────────────────────────
    ("Account", [
        (S, rr(3.4, 5.6, 17.2, 12.8, 2.8)),
        (S, circ(8.8, 10.2, 1.9)),
        (S, "M6.0,15.6 C6.4,14.0 7.4,13.2 8.8,13.2 C10.2,13.2 11.2,14.0 11.6,15.6"),
        (S, "M13.8,10.2 H17.6"),
        (S, "M13.8,13.6 H17.6"),
    ]),
    ("Person", [
        (S, circ(10.2, 8.2, 3.4)),
        (S, "M3.4,19.9 C4.0,15.6 6.8,13.5 10.2,13.5 C13.6,13.5 16.4,15.6 17.0,19.9"),
        (F, star(19.2, 6.6, 2.9, 1.15, points=4, rot=-90)),
    ]),
    ("Palette", [
        (S, "M12,3.6 C6.7,3.6 3.6,7.4 3.6,11.6 C3.6,16.4 7.3,19.8 11.6,19.8 "
            "C13.6,19.8 14.4,18.5 13.7,17.2 C13.0,15.9 13.9,14.5 15.3,14.5 "
            "H17.4 C19.4,14.5 20.4,13.1 20.4,11.6 C20.4,7.2 16.9,3.6 12,3.6 Z"),
        (F, circ(8.2, 8.6, 1.05)),
        (F, circ(12.0, 7.2, 1.05)),
        (F, circ(15.6, 9.4, 1.05)),
    ]),
    ("Player", [
        (S, circ(12, 12, 8.6)),
        (F, "M10.2,8.2 L16.6,12 L10.2,15.8 Z"),
    ]),
    ("Plugin", [
        (S, "M9.2,3.4 V7.6"),
        (S, "M14.8,3.4 V7.6"),
        (S, rr(6.4, 7.6, 11.2, 6.8, 2.8)),
        (S, "M12,14.4 V18.2"),
        (S, "M9.3,20.6 C9.3,19.4 10.4,18.2 12,18.2 C13.6,18.2 14.7,19.4 14.7,20.6"),
    ]),
    ("Integrations", [
        (S, circ(12, 12, 2.5)),
        (S, "M12,9.5 V5.4"),
        (S, "M14.5,12 H18.6"),
        (S, "M12,14.5 V18.6"),
        (S, "M9.5,12 H5.4"),
        (S, circ(12, 3.9, 1.5)),
        (S, circ(20.1, 12, 1.5)),
        (S, circ(12, 20.1, 1.5)),
        (S, circ(3.9, 12, 1.5)),
    ]),
    ("Storage", [
        (S, ell(12, 7.2, 7.4, 2.7)),
        (S, "M4.6,7.2 V16.8"),
        (S, "M19.4,7.2 V16.8"),
        (S, "M4.6,16.8 A7.4,2.7 0 0 0 19.4,16.8"),
        (S, "M4.6,12 A7.4,2.7 0 0 0 19.4,12"),
    ]),
    ("Cleaning", [
        (F, star(9.2, 9.2, 5.4, 1.7, points=4, rot=-90)),
        (S, "M13.2,13.2 L19.8,19.8"),
        (F, star(17.6, 5.4, 2.4, 0.9, points=4, rot=-90)),
    ]),
    ("Tune", [
        (S, "M4.2,7.6 H12.6"),
        (S, "M17.4,7.6 H19.8"),
        (S, circ(15.0, 7.6, 2.3)),
        (S, "M4.2,16.4 H6.4"),
        (S, "M11.2,16.4 H19.8"),
        (S, circ(8.8, 16.4, 2.3)),
    ]),
    ("Download", [
        (S, "M12,4.2 V14.4"),
        (S, "M8.0,10.8 L12,14.8 L16.0,10.8"),
        (S, "M4.6,16.4 V18.0 A2.0,2.0 0 0 0 6.6,20.0 H17.4 A2.0,2.0 0 0 0 19.4,18.0 V16.4"),
    ]),
    ("History", [
        (S, arc(12, 12, 7.9, -62, 252)),
        (S, head(pt(12, 12, 7.9, -62), -62 - 90)),
        (S, "M12,7.9 V12.3 L15.7,14.2"),
    ]),
    ("Clock", [
        (S, circ(12, 12, 8.4)),
        (S, "M12,6.9 V12.4 L16.2,14.4"),
    ]),
    ("Info", [
        (S, rr(3.6, 3.6, 16.8, 16.8, 6.2)),
        (F, circ(12, 8.4, 1.15)),
        (S, "M12,11.5 V16.5"),
    ]),
    ("Feedback", [
        (S, rr(4.4, 4.6, 15.2, 11.6, 3.4)),
        (S, "M8.6,16.2 V20.2 L12.8,16.2"),
        (F, circ(9.0, 10.4, 0.95)),
        (F, circ(12.0, 10.4, 0.95)),
        (F, circ(15.0, 10.4, 0.95)),
    ]),
    ("Bug", [
        (S, ell(12, 13.4, 4.4, 5.6)),
        (S, "M8.7,9.4 A3.3,3.3 0 0 1 15.3,9.4"),
        (S, "M10.4,6.6 L8.6,4.4"),
        (S, "M13.6,6.6 L15.4,4.4"),
        (S, "M7.6,11.8 H3.6"),
        (S, "M16.4,11.8 H20.4"),
        (S, "M7.9,15.2 H4.4"),
        (S, "M16.1,15.2 H19.6"),
        (S, "M8.9,18.4 L6.4,20.4"),
        (S, "M15.1,18.4 L17.6,20.4"),
    ]),
    ("Bell", [
        (S, "M6.6,17.4 H17.4 L15.9,15.2 V10.6 C15.9,7.2 14.1,5.0 12,5.0 "
            "C9.9,5.0 8.1,7.2 8.1,10.6 V15.2 Z"),
        (S, "M10.2,19.9 A2.0,2.0 0 0 0 13.8,19.9"),
        (S, "M12,3.4 V5.0"),
    ]),

    # ── içerik / puanlama ──────────────────────────────────────────────────
    ("Star", [(S, star(12, 12.4, 8.4, 3.7))]),
    ("StarFilled", [(F, star(12, 12.4, 8.4, 3.7))]),
    ("Heart", [(F, "M12,19.8 C6.2,15.6 3.9,12.4 3.9,9.6 C3.9,7.1 5.8,5.4 8.0,5.4 "
                   "C9.7,5.4 11.2,6.4 12,7.9 C12.8,6.4 14.3,5.4 16.0,5.4 "
                   "C18.2,5.4 20.1,7.1 20.1,9.6 C20.1,12.4 17.8,15.6 12,19.8 Z")]),
    ("HeartOutline", [(S, "M12,19.8 C6.2,15.6 3.9,12.4 3.9,9.6 C3.9,7.1 5.8,5.4 8.0,5.4 "
                         "C9.7,5.4 11.2,6.4 12,7.9 C12.8,6.4 14.3,5.4 16.0,5.4 "
                         "C18.2,5.4 20.1,7.1 20.1,9.6 C20.1,12.4 17.8,15.6 12,19.8 Z")]),
    ("Bookmark", [(S, "M6.9,3.8 H17.1 V20.4 L12,16.8 L6.9,20.4 Z")]),
    ("Check", [(S, "M4.8,12.4 L9.6,17.2 L19.2,6.8")]),
    ("CheckCircle", [
        (S, circ(12, 12, 8.6)),
        (S, "M7.8,12.2 L10.8,15.2 L16.4,8.8"),
    ]),
    ("Film", [
        (S, rr(3.4, 4.8, 17.2, 14.4, 2.8)),
        (S, "M8.0,4.8 V19.2"),
        (S, "M16.0,4.8 V19.2"),
        (F, circ(5.7, 8.0, 0.85)),
        (F, circ(5.7, 12.0, 0.85)),
        (F, circ(5.7, 16.0, 0.85)),
        (F, circ(18.3, 8.0, 0.85)),
        (F, circ(18.3, 12.0, 0.85)),
        (F, circ(18.3, 16.0, 0.85)),
    ]),
    ("Tv", [
        (S, rr(3.2, 6.8, 17.6, 12.2, 2.8)),
        (S, "M8.4,3.4 L12,6.8 L15.6,3.4"),
        (F, circ(12, 21.2, 0.9)),
    ]),
    ("Comment", [
        (S, "M4.4,6.2 A2.8,2.8 0 0 1 7.2,3.4 H16.8 A2.8,2.8 0 0 1 19.6,6.2 "
            "V13.6 A2.8,2.8 0 0 1 16.8,16.4 H11.4 L7.2,20.0 V16.4 "
            "A2.8,2.8 0 0 1 4.4,13.6 Z"),
        (S, "M8.4,9.9 H15.6"),
    ]),
    ("Tag", [
        (S, "M11.0,3.6 H18.4 A1.9,1.9 0 0 1 20.3,5.5 V12.9 L12.2,21.0 "
            "A1.8,1.8 0 0 1 9.6,21.0 L3.1,14.5 A1.8,1.8 0 0 1 3.1,11.9 Z"),
        (F, circ(16.5, 7.4, 1.25)),
    ]),

    # ── paylaş / kopyala / bağlantı ────────────────────────────────────────
    ("Share", [
        (S, circ(17.4, 6.2, 2.5)),
        (S, circ(6.6, 12.0, 2.5)),
        (S, circ(17.4, 17.8, 2.5)),
        (S, "M8.8,10.9 L15.2,7.3"),
        (S, "M8.8,13.1 L15.2,16.7"),
    ]),
    ("Copy", [
        (S, rr(6.0, 3.6, 14.0, 14.0, 2.6)),
        (S, "M4.0,9.2 V18.2 A2.4,2.4 0 0 0 6.4,20.6 H15.4"),
    ]),
    ("Link", [
        (S, rr(4.4, 9.0, 15.2, 6.0, 3.0)),
        (S, "M12,9.0 V15.0"),
    ]),
    ("LinkOff", [
        (S, rr(4.4, 9.0, 15.2, 6.0, 3.0)),
        (S, "M12,9.0 V15.0"),
        (S, "M4.4,19.6 L19.6,4.4"),
    ]),
    ("External", [
        (S, "M18.6,13.4 V18.2 A2.4,2.4 0 0 1 16.2,20.6 H5.8 A2.4,2.4 0 0 1 3.4,18.2 V7.8 "
            "A2.4,2.4 0 0 1 5.8,5.4 H10.8"),
        (S, "M13.8,3.4 H20.6 V10.2"),
        (S, "M20.2,3.8 L11.8,12.2"),
    ]),
    ("Translate", [
        (S, "M4.4,17.8 L8.9,6.4 L13.4,17.8"),
        (S, "M6.2,13.8 H11.6"),
        (S, "M14.8,12.0 H20.4"),
        (S, poly((17.8, 9.0), (20.8, 12.0), (17.8, 15.0), close=False)),
    ]),
    ("Subtitles", [
        (S, rr(3.4, 5.6, 17.2, 12.8, 2.8)),
        (S, "M6.6,10.6 H11.0"),
        (S, "M13.0,10.6 H17.4"),
        (S, "M6.6,14.2 H14.2"),
    ]),
    ("Delete", [
        (S, "M4.6,7.0 H19.4"),
        (S, "M9.4,7.0 V5.0 A1.6,1.6 0 0 1 11.0,3.4 H13.0 A1.6,1.6 0 0 1 14.6,5.0 V7.0"),
        (S, "M6.6,7.0 L7.6,19.0 A2.0,2.0 0 0 0 9.6,20.8 H14.4 A2.0,2.0 0 0 0 16.4,19.0 L17.4,7.0"),
        (S, "M10.4,11.0 V16.8"),
        (S, "M13.6,11.0 V16.8"),
    ]),
    ("Edit", [
        (S, "M4.4,19.6 L5.2,15.6 L15.4,5.4 A2.3,2.3 0 0 1 18.6,8.6 L8.4,18.8 Z"),
        (S, "M13.8,7.0 L17.0,10.2"),
    ]),
    ("Filter", [
        (S, "M4.2,5.6 H19.8 L13.8,12.9 V18.6 L10.2,20.6 V12.9 Z"),
    ]),
    ("Chart", [
        (S, "M4.2,20.0 H20.2"),
        (S, "M7.4,20.0 V13.4"),
        (S, "M12.0,20.0 V8.2"),
        (S, "M16.6,20.0 V11.0"),
        (F, circ(19.4, 5.6, 1.25)),
    ]),
    ("Calendar", [
        (S, rr(3.6, 5.4, 16.8, 15.0, 3.0)),
        (S, "M8.0,3.2 V7.4"),
        (S, "M16.0,3.2 V7.4"),
        (S, "M3.6,10.2 H20.4"),
        (F, circ(8.2, 14.6, 1.0)),
        (F, circ(12.0, 14.6, 1.0)),
        (F, circ(15.8, 14.6, 1.0)),
    ]),
    ("Book", [
        (S, "M12,6.8 C9.8,5.0 6.8,4.6 4.2,5.6 V18.4 C7.0,17.4 9.8,17.9 12,19.7 "
            "C14.2,17.9 17.0,17.4 19.8,18.4 V5.6 C17.2,4.6 14.2,5.0 12,6.8 Z"),
        (S, "M12,6.8 V19.7"),
    ]),
    ("Image", [
        (S, rr(3.4, 4.8, 17.2, 14.4, 3.0)),
        (S, "M3.6,15.8 L8.6,10.8 L12.6,14.8 L15.4,12.0 L20.4,16.8"),
        (F, circ(8.4, 9.0, 1.3)),
    ]),
    ("Folder", [
        (S, "M3.6,7.4 A2.6,2.6 0 0 1 6.2,4.8 H9.6 L11.8,7.4 H17.8 "
            "A2.6,2.6 0 0 1 20.4,10.0 V17.2 A2.6,2.6 0 0 1 17.8,19.8 H6.2 "
            "A2.6,2.6 0 0 1 3.6,17.2 Z"),
    ]),
    ("Shield", [
        (S, "M12,3.4 L19.6,6.2 V11.8 C19.6,16.4 16.4,19.6 12,20.6 "
            "C7.6,19.6 4.4,16.4 4.4,11.8 V6.2 Z"),
        (S, circ(12, 11.2, 2.2)),
        (S, "M12,13.4 V16.2"),
    ]),
    ("Globe", [
        (S, circ(12, 12, 8.6)),
        (S, "M12,3.4 C8.4,6.2 8.4,17.8 12,20.6 C15.6,17.8 15.6,6.2 12,3.4 Z"),
        (S, "M3.8,9.2 H20.2"),
        (S, "M3.8,14.8 H20.2"),
    ]),

    # ── gezinme / yön ──────────────────────────────────────────────────────
    ("Menu", [
        (S, "M4.2,7.0 H19.8"),
        (S, "M4.2,12.0 H15.2"),
        (S, "M4.2,17.0 H18.0"),
    ]),
    ("More", [
        (F, circ(12, 5.2, 1.5)),
        (F, circ(12, 12.0, 1.5)),
        (F, circ(12, 18.8, 1.5)),
    ]),
    ("Close", [
        (S, "M6.0,6.0 L18.0,18.0"),
        (S, "M18.0,6.0 L6.0,18.0"),
    ]),
    ("Back", [
        (S, "M19.6,12 H4.4"),
        (S, "M10.8,5.2 L4.0,12 L10.8,18.8"),
    ]),
    ("Forward", [
        (S, "M4.4,12 H19.6"),
        (S, "M13.2,5.2 L20.0,12 L13.2,18.8"),
    ]),
    ("ChevronDown", [(S, "M6.4,9.4 L12,15.0 L17.6,9.4")]),
    ("ChevronUp", [(S, "M6.4,14.6 L12,9.0 L17.6,14.6")]),
    ("ChevronRight", [(S, "M9.4,6.2 L15.0,12 L9.4,17.8")]),
    ("Plus", [
        (S, "M12,4.6 V19.4"),
        (S, "M4.6,12 H19.4"),
    ]),

    # ── durum / yardımcı ───────────────────────────────────────────────────
    ("Eye", [
        (S, "M2.6,12 C5.0,7.4 8.4,5.2 12,5.2 C15.6,5.2 19.0,7.4 21.4,12 "
            "C19.0,16.6 15.6,18.8 12,18.8 C8.4,18.8 5.0,16.6 2.6,12 Z"),
        (S, circ(12, 12, 3.0)),
    ]),
    ("EyeOff", [
        (S, "M2.6,12 C5.0,7.4 8.4,5.2 12,5.2 C15.6,5.2 19.0,7.4 21.4,12 "
            "C19.0,16.6 15.6,18.8 12,18.8 C8.4,18.8 5.0,16.6 2.6,12 Z"),
        (S, circ(12, 12, 3.0)),
        (S, "M4.4,20.2 L19.6,4.0"),
    ]),
    ("SearchOff", [
        (S, circ(10.4, 10.4, 6.2)),
        (S, "M15.1,15.1 L20,20"),
        (S, "M4.8,19.2 L19.2,4.8"),
    ]),
    ("Sort", [
        (S, "M4.8,7.0 H19.2"),
        (S, "M7.2,12.0 H16.8"),
        (S, "M9.6,17.0 H14.4"),
    ]),
    ("Queue", [
        (S, "M4.2,6.4 H19.8"),
        (S, "M4.2,10.8 H19.8"),
        (S, "M4.2,15.2 H11.4"),
        (F, "M15.8,14.6 L20.6,17.4 L15.8,20.2 Z"),
    ]),
    ("Sparkle", [
        (F, star(10.6, 10.6, 6.6, 2.0, points=4, rot=-90)),
        (F, star(18.4, 6.2, 2.8, 0.9, points=4, rot=-90)),
        (F, star(18.2, 17.4, 2.2, 0.7, points=4, rot=-90)),
    ]),
    ("Refresh", [
        (S, arc(12, 12, 8.0, -48, 196)),
        (S, head(pt(12, 12, 8.0, 196), 196 + 90)),
    ]),
    ("Sync", [
        (S, arc(12, 12, 8.0, -28, 152)),
        (S, head(pt(12, 12, 8.0, 152), 152 + 90)),
        (S, arc(12, 12, 8.0, 152, 332)),
        (S, head(pt(12, 12, 8.0, 332), 332 + 90)),
    ]),
    ("CloudSync", [
        (S, "M7.6,18.6 A3.9,3.9 0 0 1 6.9,10.9 A5.0,5.0 0 0 1 16.5,9.7 "
            "A3.9,3.9 0 0 1 17.0,18.6 Z"),
        (S, "M12,15.6 V9.8"),
        (S, "M9.6,12.2 L12,9.6 L14.4,12.2"),
    ]),

    # ── medya oynatıcı ─────────────────────────────────────────────────────
    ("Play", [(F, "M7.6,4.6 L19.4,12 L7.6,19.4 Z")]),
    ("Pause", [
        (F, rr(6.6, 4.6, 3.2, 14.8, 1.2)),
        (F, rr(14.2, 4.6, 3.2, 14.8, 1.2)),
    ]),
    ("PlayCircle", [
        (S, circ(12, 12, 8.6)),
        (F, "M10.2,8.2 L16.6,12 L10.2,15.8 Z"),
    ]),
    ("SkipNext", [
        (F, "M6.2,4.8 L14.6,12 L6.2,19.2 Z"),
        (F, rr(16.2, 4.8, 2.4, 14.4, 0.9)),
    ]),
    ("Volume", [
        (S, "M4.2,9.4 H7.6 L12.2,5.4 V18.6 L7.6,14.6 H4.2 Z"),
        (S, "M15.4,8.8 A4.8,4.8 0 0 1 15.4,15.2"),
        (S, "M17.8,6.4 A7.8,7.8 0 0 1 17.8,17.6"),
    ]),
    ("Fullscreen", [
        (S, "M9.4,4.2 H4.2 V9.4"),
        (S, "M14.6,4.2 H19.8 V9.4"),
        (S, "M19.8,14.6 V19.8 H14.6"),
        (S, "M9.4,19.8 H4.2 V14.6"),
    ]),
    ("Cast", [
        (S, "M20.4,16.2 V6.6 A2.2,2.2 0 0 0 18.2,4.4 H5.8 A2.2,2.2 0 0 0 3.6,6.6 V7.8"),
        (S, "M3.6,11.2 A8.6,8.6 0 0 1 12.2,19.8"),
        (S, "M3.6,15.2 A4.6,4.6 0 0 1 8.2,19.8"),
        (F, circ(3.9, 19.5, 0.95)),
    ]),
    ("Speed", [
        (S, "M3.8,17.4 A8.6,8.6 0 1 1 20.2,17.4"),
        (S, "M12,17.4 L16.4,10.4"),
        (F, circ(12, 17.4, 1.25)),
    ]),
    ("Pip", [
        (S, rr(2.8, 4.6, 18.4, 14.8, 2.8)),
        (S, rr(11.6, 11.2, 7.4, 6.2, 1.8)),
    ]),

    # ── uyarı / hata / liste düzeni ─────────────────────────────────────────
    ("Warning", [
        (S, "M13.0,3.7 A1.2,1.2 0 0 0 11.0,3.7 L2.9,17.8 A1.3,1.3 0 0 0 4.0,19.8 "
            "H20.0 A1.3,1.3 0 0 0 21.1,17.8 Z"),
        (S, "M12,9.2 V13.6"),
        (F, circ(12, 16.5, 1.05)),
    ]),
    ("Error", [
        (S, circ(12, 12, 8.8)),
        (S, "M12,7.4 V13.0"),
        (F, circ(12, 16.2, 1.05)),
    ]),
    ("Grid", [
        (S, rr(3.8, 3.8, 6.8, 6.8, 1.9)),
        (S, rr(13.4, 3.8, 6.8, 6.8, 1.9)),
        (S, rr(3.8, 13.4, 6.8, 6.8, 1.9)),
        (F, rr(13.4, 13.4, 6.8, 6.8, 1.9)),
    ]),
    ("Rows", [
        (F, circ(4.9, 6.8, 1.15)),
        (F, circ(4.9, 12.0, 1.15)),
        (F, circ(4.9, 17.2, 1.15)),
        (S, "M8.6,6.8 H19.8"),
        (S, "M8.6,12.0 H19.8"),
        (S, "M8.6,17.2 H15.6"),
    ]),
    ("Repeat", [
        (S, rr(3.6, 7.4, 16.8, 9.2, 4.6)),
        (S, head((19.4, 9.2), 0)),
        (S, head((4.6, 14.8), 180)),
    ]),
    ("Key", [
        (S, circ(8.0, 12.0, 3.7)),
        (S, "M11.7,12.0 H20.2"),
        (S, "M16.6,12.0 V15.2"),
        (S, "M19.4,12.0 V14.4"),
    ]),
    ("Code", [
        (S, poly((9.2, 7.2), (3.8, 12.0), (9.2, 16.8), close=False)),
        (S, poly((14.8, 7.2), (20.2, 12.0), (14.8, 16.8), close=False)),
        (S, "M13.2,5.4 L10.8,18.6"),
    ]),
    ("Send", [
        (S, "M20.6,3.8 L3.6,10.9 L10.4,13.6 L13.1,20.4 Z"),
        (S, "M10.4,13.6 L20.6,3.8"),
    ]),
    ("People", [
        (S, circ(9.4, 8.6, 3.0)),
        (S, "M3.4,19.4 C4.0,15.8 6.4,14.0 9.4,14.0 C12.4,14.0 14.8,15.8 15.4,19.4"),
        (S, circ(17.4, 9.8, 2.4)),
        (S, "M16.3,14.2 C18.9,14.7 20.4,16.4 20.8,19.4"),
    ]),
    ("UpCircle", [
        (S, circ(12, 12, 8.8)),
        (S, "M12,16.3 V7.9"),
        (S, "M8.5,11.4 L12,7.9 L15.5,11.4"),
    ]),
    ("ChevronLeft", [(S, "M14.6,6.2 L9.0,12 L14.6,17.8")]),
    ("FastForward", [
        (F, "M3.2,5.6 L10.0,12 L3.2,18.4 Z"),
        (F, "M11.6,5.6 L18.4,12 L11.6,18.4 Z"),
        (F, rr(19.2, 5.6, 1.8, 12.8, 0.7)),
    ]),
    ("FastRewind", [
        (F, "M20.8,5.6 L14.0,12 L20.8,18.4 Z"),
        (F, "M12.4,5.6 L5.6,12 L12.4,18.4 Z"),
        (F, rr(3.0, 5.6, 1.8, 12.8, 0.7)),
    ]),
    ("CloudDownload", [
        (S, "M7.6,18.6 A3.9,3.9 0 0 1 6.9,10.9 A5.0,5.0 0 0 1 16.5,9.7 "
            "A3.9,3.9 0 0 1 17.0,18.6 Z"),
        (S, "M12,9.8 V15.6"),
        (S, "M9.6,13.2 L12,15.8 L14.4,13.2"),
    ]),
    ("Bolt", [
        (S, "M13.8,2.9 L5.6,13.2 H10.8 L9.6,21.1 L18.2,10.6 H12.8 Z"),
    ]),
    ("Hourglass", [
        (S, "M6.4,3.6 H17.6"),
        (S, "M6.4,20.4 H17.6"),
        (S, "M8.2,3.6 V7.2 C8.2,9.6 12,11.2 12,12 C12,12.8 8.2,14.4 8.2,16.8 V20.4"),
        (S, "M15.8,3.6 V7.2 C15.8,9.6 12,11.2 12,12 C12,12.8 15.8,14.4 15.8,16.8 V20.4"),
    ]),
    ("Swap", [
        (S, "M4.4,9.0 H18.2"),
        (S, head((18.2, 9.0), 0)),
        (S, "M19.6,15.0 H5.8"),
        (S, head((5.8, 15.0), 180)),
    ]),
    ("RemoveCircle", [
        (S, circ(12, 12, 8.8)),
        (S, "M8.2,12.0 H15.8"),
    ]),
    ("Reply", [
        (S, poly((9.6, 5.8), (3.8, 11.2), (9.6, 16.6), close=False)),
        (S, "M3.8,11.2 H13.4 C16.6,11.2 19.0,13.6 19.0,16.6 C19.0,18.4 18.2,19.8 17.0,20.6"),
    ]),
    ("ThumbUp", [
        (S, "M7.0,10.4 H4.4 A1.2,1.2 0 0 0 3.2,11.6 V19.2 A1.2,1.2 0 0 0 4.4,20.4 H7.0"),
        (S, "M7.0,10.4 L11.0,3.8 C11.5,3.0 12.8,3.4 12.8,4.4 V9.0 H18.2 "
            "C19.7,9.0 20.7,10.3 20.3,11.7 L18.8,18.6 C18.6,19.6 17.7,20.4 16.7,20.4 H7.0 Z"),
    ]),
]


GLYPHS += [
    ("VolumeOff", [(S, "M4,9 H8 L13,5 V19 L8,15 H4 Z M17,9 L21,15 M21,9 L17,15")]),
    ("VolumeLow", [(S, "M4,9 H8 L13,5 V19 L8,15 H4 Z M17,9 Q20,12 17,15")]),
    ("RepeatOne", [(S, "M5,7 H19 L16,4 M19,7 L16,10 M19,17 H5 L8,20 M5,17 L8,14 M11,10 L13,9 V15")]),
    ("Unlock", [(S, "M8,10 V7 A4,4 0 0 1 8,7.1 M8,7 A4,4 0 0 1 16,7 M6,10 H18 V20 H6 Z M12,14 V16")]),
    ("BellOff", [(S, "M5,17 H19 L17,14 V10 M7,13 V10 A5,5 0 0 1 15,6 M10,20 H14 M3,3 L21,21")]),
    ("FullscreenExit", [(S, "M3,8 H8 V3 M16,3 V8 H21 M21,16 H16 V21 M8,21 V16 H3")]),
    ("SubtitlesOff", [(S, "M4,7 V19 H20 V10 M8,5 H20 V7 M7,11 H9 M7,15 H13 M17,15 H18 M3,3 L21,21")]),
]

from extra_glyphs import PATHS
GLYPHS += [(name, [(S, data)]) for name, data in PATHS.items()]

# ───────────────────────── Kotlin dosyası üretimi ─────────────────────────

KOTLIN_HEADER = '''package com.kitsugi.animelist.ui.theme.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Kitsugi Glyph System — uygulamanın kendi, elle çizilmiş ikon seti.
 *
 * Neden var: Material ikonları her uygulamada aynı görünür; bu yüzden Keşfet,
 * Listem, Arama, ayarlar satırları, açılır-kapanır başlıklar, oynatıcı
 * kontrolleri ve tüm butonlar birbirine benziyordu. Burada her butonun/işlevin
 * **kendi silueti** var: aynı işi yapan iki alan aynı glyph'i kullanır, farklı
 * işlevler için ayrı simgeler geliştirilir.
 *
 * Tasarım kuralları:
 *  - 24x24 grid, 2.4–21.6 güvenli alan
 *  - 1.75 birim çizgi, yuvarlak uç ve birleşim
 *  - dolu alanlar yalnızca vurgu ögesi (oynatma üçgeni, nokta, yıldız)
 *  - hiçbir glyph Material'daki muadilinin geometrisini kopyalamaz
 *
 * Üretici: tools/kitsugi_glyphs/generate_kitsugi_glyphs.py
 * Önizleme: gorseller/kitsugi_ikon_seti.png
 *
 * Kullanım:
 *   Icon(imageVector = KitsugiGlyphs.Search, contentDescription = null)
 */
object KitsugiGlyphs {

    private const val STROKE = 1.75f

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "kitsugi_$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    /** Monoline çizgi katmanı. */
    private fun ImageVector.Builder.stroke(d: String) {
        addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }

    /** Dolu vurgu katmanı. */
    private fun ImageVector.Builder.solid(d: String) {
        addPath(
            pathData = addPathNodes(d),
            fill = SolidColor(Color.Black)
        )
    }
'''


def kotlin_prop(name: str, layers) -> str:
    key = name[0].lower() + name[1:]
    body = "\n".join(
        f"            {'stroke' if kind == S else 'solid'}(\"{d}\")" for kind, d in layers
    )
    return f"    val {name}: ImageVector = build(\"{key}\") {{\n{body}\n    }}\n"


def generate_kotlin() -> str:
    parts = [KOTLIN_HEADER]
    for name, layers in GLYPHS:
        parts.append("\n" + kotlin_prop(name, layers))
    parts.append("\n    /** Ada göre glyph (dinamik kullanım: ayar araması, rozetler). */\n")
    parts.append("    private val byName: Map<String, ImageVector> by lazy {\n")
    parts.append("        mapOf(\n")
    seen = {}
    for name, _ in GLYPHS:
        key = name[0].lower() + name[1:]
        seen[key] = name
        seen[name.lower()] = name
    entries = [f'            "{k}" to {v}' for k, v in seen.items()]
    parts.append(",\n".join(entries))
    parts.append("\n        )\n    }\n\n")
    parts.append("    fun byNameOrNull(name: String?): ImageVector? =\n")
    parts.append("        if (name.isNullOrBlank()) null else byName[name.trim().lowercase()]\n")
    parts.append("}\n")
    return "".join(parts)


# ─────────────────────────── SVG önizleme üretimi ───────────────────────────

def svg_layer(kind, d, color):
    if kind == S:
        return (
            f'<path d="{d}" fill="none" stroke="{color}" stroke-width="1.75" '
            f'stroke-linecap="round" stroke-linejoin="round"/>'
        )
    return f'<path d="{d}" fill="{color}"/>'


def generate_preview(path: str, cols: int = 8, cell: int = 92, pad: int = 14):
    rows = math.ceil(len(GLYPHS) / cols)
    label_h = 22
    width = cols * cell
    height = rows * (cell + label_h) + pad * 2
    accent = "#7C5CFF"
    ink = "#EDEDF5"
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        f'<rect width="{width}" height="{height}" fill="#12131B"/>',
    ]
    for idx, (name, layers) in enumerate(GLYPHS):
        r, c = divmod(idx, cols)
        cx = c * cell + cell / 2
        cy = pad + r * (cell + label_h) + cell / 2
        scale = (cell * 0.52) / 24
        out.append(f'<g transform="translate({cx - 12 * scale},{cy - 12 * scale}) scale({scale})">')
        out.append(
            f'<rect x="0" y="0" width="24" height="24" rx="6" fill="#1B1D29" '
            f'stroke="#2A2D3D" stroke-width="0.6"/>'
        )
        for kind, d in layers:
            out.append(svg_layer(kind, d, ink))
        out.append("</g>")
        out.append(
            f'<text x="{cx}" y="{cy + cell / 2 + 6}" fill="#9AA0B4" font-family="Vera" '
            f'font-size="9" text-anchor="middle">{name}</text>'
        )
    out.append("</svg>")
    svg = "\n".join(out)
    with open(path + ".svg", "w", encoding="utf-8") as f:
        f.write(svg)
    return path + ".svg"


def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    kt_path = os.path.join(
        root, "app/src/main/java/com/kitsugi/animelist/ui/theme/icons/KitsugiGlyphs.kt"
    )
    os.makedirs(os.path.dirname(kt_path), exist_ok=True)
    with open(kt_path, "w", encoding="utf-8") as f:
        f.write(generate_kotlin())
    print(f"yazıldı: {kt_path} ({len(GLYPHS)} glyph)")

    preview_dir = os.path.join(root, "gorseller")
    os.makedirs(preview_dir, exist_ok=True)
    svg_path = generate_preview(os.path.join(preview_dir, "kitsugi_ikon_seti.png"))
    print(f"önizleme svg: {svg_path}")


if __name__ == "__main__":
    main()
