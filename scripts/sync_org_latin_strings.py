#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""TR/EN kurum/etiket adlarını kalıcı Latin ad haritasıyla eşitler.

`diag/bangumi_master/org_latin.tsv` içindeki 9.457 anahtar tamamlanmış
v2.4.340 sözlüğüdür. Bangumi VAULT mimarisinde Kotlin'e veri basmak yerine
`app/src/main/resources/bangumi/orgs.tsv` içindeki *aynı anahtarların* en/tr
alanları ve TR/EN XML kaynakları senkronlanır; diğer VAULT satırları silinmez.
`--check` karşılaştırmayı gerçekten yapar (yalnız CJK yok diye başarı vermez).

    python3 scripts/sync_org_latin_strings.py          # TSV + XML düzelt
    python3 scripts/sync_org_latin_strings.py --check  # CI: yalnız denetle
"""

from __future__ import annotations

import argparse
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ORG_STORE = ROOT / "app/src/main/resources/bangumi/orgs.tsv"
RES_TR = ROOT / "app/src/main/res/values/strings.xml"
RES_EN = ROOT / "app/src/main/res/values-en/strings.xml"
LATIN_TSV = ROOT / "diag/bangumi_master/org_latin.tsv"
TAG_TSV = ROOT / "diag/bangumi_master/tag_latin.tsv"

CJK = re.compile(r"[\u3040-\u30FF\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF\uAC00-\uD7AF]")
# Romanizasyondan sonra temizlenen CJK noktalama (・ = U+30FB, ー = U+30FC, 「」, 、。 vb.)
CJK_PUNCT = re.compile(r"[\u3000-\u303F\u30FB\uFF01-\uFF65\uFFE0-\uFFEF]")
HANGUL = re.compile(r"[\uAC00-\uD7AF]")
KANA = re.compile(r"[\u3040-\u30FF]")
STRING_RE = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.S)

# Dergi/dergi benzeri önekler — yalnız kalan ad sözlükte bulunursa uygulanır.
MAGAZINE_PREFIXES = [
    ("週刊少年", "Weekly Shonen"),
    ("月刊少年", "Monthly Shonen"),
    ("別冊少年", "Bessatsu Shonen"),
    ("週刊", "Weekly"),
    ("月刊", "Monthly"),
    ("別冊", "Bessatsu"),
    ("増刊", "Extra"),
    ("隔週", "Biweekly"),
]

ORG_PREFIXES = ("bangumi_studio_", "bangumi_producer_", "bangumi_publisher_",
                "bangumi_magazine_", "bangumi_network_", "bangumi_org_")
TAG_PREFIXES = ("bangumi_tag_",)


def normalize(value: str) -> str:
    """Sözlüğün Kotlin tarafındaki normalize() ile aynı sadeleştirme."""
    value = value.strip()
    for opener, closer in (("「", "」"), ("『", "』"), ("（", "）"), ("(", ")")):
        if value.startswith(opener):
            value = value[len(opener):]
        if value.endswith(closer):
            value = value[: -len(closer)]
    value = value.replace("　", " ")
    return re.sub(r"\s+", " ", value).strip().lower()


def title_case(text: str) -> str:
    """`manga 4 koma paretto` → `Manga 4 Koma Paretto`; kısaltmalar korunur."""
    out = []
    for token in text.split():
        if not token:
            continue
        if token.isupper() and len(token) <= 4:
            out.append(token)
        elif token.isdigit() or re.fullmatch(r"[0-9]+[A-Za-z]*", token):
            out.append(token)
        else:
            out.append(token[:1].upper() + token[1:])
    return " ".join(out)


# ── Hangul → Revize Romanizasyon (kısaltılmış) ────────────────────────────────
_HANGUL_INITIAL = list("gkkknnndddlllmmmbbbsss​jjjcccktttppphhh")
_HANGUL_MEDIAL = list("aaeoaoaeoyeooyeyeoowaeoeowiyuyuieoeeoeoeooeueueueui")
_HANGUL_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l",
                 "l", "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t",
                 "k", "t", "p", "t"]

_INITIAL = ["g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "",
            "j", "jj", "ch", "k", "t", "p", "h"]
_MEDIAL = ["a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae",
           "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"]
_FINAL = ["", "k", "k", "k", "n", "n", "n", "t", "l", "l", "l", "l", "l",
          "l", "l", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t",
          "p", "t"]


def hangul_to_latin(text: str) -> str:
    """Hangul hecelerini Revize Romanizasyon ile Latin harflerine çevirir."""
    out: list[str] = []
    for ch in text:
        code = ord(ch)
        if 0xAC00 <= code <= 0xD7A3:
            index = code - 0xAC00
            initial = index // 588
            medial = (index % 588) // 28
            final = index % 28
            out.append(_INITIAL[initial] + _MEDIAL[medial] + _FINAL[final])
        else:
            out.append(ch)
    return "".join(out)


def _kakasi(text: str) -> str:
    import pykakasi

    result = " ".join(
        item["hepburn"] for item in pykakasi.kakasi().convert(text) if item["hepburn"]
    )
    return result


def _pinyin(text: str) -> str:
    from pypinyin import lazy_pinyin

    return " ".join(lazy_pinyin(text))


def _clean(value: str) -> str:
    value = CJK_PUNCT.sub(" ", value)
    value = re.sub(r"\s+", " ", value).strip(" -_·.")
    return value


# Romanizasyon kütüphanelerinin tanımadığı uyumluluk ideografları (Japonca adlarda sık).
COMPAT_MAP = {"\ufa11": "\u5d0e", "\ufa19": "\u9ad9", "\ufa0c": "\u795e", "\u9ad9": "\u9ad8"}


def romanize(text: str) -> str:
    """CJK metni Latin harflerine çevirir (Japonca → Hepburn, Çince → pinyin, Korece → RR)."""
    cleaned = text.strip()
    if not cleaned:
        return ""
    cleaned = unicodedata.normalize("NFKC", cleaned)
    for src, dst in COMPAT_MAP.items():
        cleaned = cleaned.replace(src, dst)
    if HANGUL.search(cleaned):
        cleaned = hangul_to_latin(cleaned)

    # Japonca varsayılan (anime endüstrisi); kanji adlar da Hepburn okumasıyla çıkar.
    # Çince kısa adlar kakasi'de tek heceye düşüyor (`软房子` → "Ko"); bu durumda pinyin
    # okuması daha doğru. Kana varsa metin kesin Japoncadır — pinyin hiç denenmez.
    try:
        kakasi_result = _clean(_kakasi(cleaned))
    except Exception:
        kakasi_result = ""
    kana_present = bool(KANA.search(cleaned))
    han_count = len(CJK.findall(cleaned))

    if kakasi_result and not CJK.search(kakasi_result):
        tokens = kakasi_result.split()
        # Çince kısa adlar kakasi'de tek kısa heceye düşer (`软房子` → "Ko",
        # `电击魔王` → "Maou"); bu durumda pinyin okuması daha doğru.
        truncated = len(tokens) == 1 and len(kakasi_result) <= 4 and han_count >= 2
        if kana_present or not truncated:
            return title_case(kakasi_result)
    if not kana_present:
        try:
            pinyin_result = _clean(_pinyin(cleaned))
        except Exception:
            pinyin_result = ""
        if pinyin_result and not CJK.search(pinyin_result):
            return title_case(pinyin_result)
    if kakasi_result and not CJK.search(kakasi_result):
        return title_case(kakasi_result)

    # Son çare: kalan CJK karakterleri at (Latin kısım korunur).
    stripped = _clean(CJK.sub("", _kakasi(cleaned) if False else cleaned))
    stripped = re.sub(r"\s+", " ", stripped).strip()
    return title_case(stripped) if stripped else ""


def load_dict_rows() -> list[tuple[str, str, str]]:
    """VAULT satırları: <kaynak etiketi> TAB <anahtar> TAB <küresel ad> [TAB <tr>]."""
    rows = []
    for number, line in enumerate(ORG_STORE.read_text(encoding="utf-8").splitlines(), 1):
        fields = line.split("\t")
        if len(fields) < 3:
            raise SystemExit(f"orgs.tsv satır {number}: en az üç sütun gerekli")
        rows.append((fields[0], fields[1], fields[2]))
    return rows


def build_alias_index(rows: list[tuple[str, str, str]]) -> dict[str, str]:
    """Latin adı temiz olan kayıtların takma ad → Latin ad haritası."""
    index: dict[str, str] = {}
    for label, _key, latin in rows:
        if CJK.search(latin) or not latin.strip():
            continue
        key = normalize(label)
        if key and key not in index:
            index[key] = latin.strip()
    return index


WRAP_RE = re.compile(r"^[「『\"']+|[」』\"']+$")


def strip_wrapping(name: str) -> str:
    """Kaynak adın başındaki/sonundaki tırnak ve köşeli parantezleri atar.

    Bangumi infobox'ında bazı adlar `「Sunflower Studio」` gibi sarılı gelir;
    Latin adın parçası değildir, ekranda görünmemelidir.
    """
    cleaned = name.strip()
    while True:
        trimmed = WRAP_RE.sub("", cleaned).strip()
        if trimmed == cleaned or not trimmed:
            return cleaned
        cleaned = trimmed


def best_latin(raw: str, latin: str, alias_index: dict[str, str]) -> tuple[str, str]:
    """(yeni Latin ad, yöntem) döndürür. Yöntem: official | prefix | romanized | keep."""
    if not CJK.search(latin):
        return strip_wrapping(latin), "keep"

    alias_hit = alias_index.get(normalize(raw)) or alias_index.get(normalize(latin))
    if alias_hit:
        return strip_wrapping(alias_hit), "official"

    for prefix, replacement in MAGAZINE_PREFIXES:
        if latin.startswith(prefix):
            rest = latin[len(prefix):].strip()
            base = alias_index.get(normalize(rest))
            if base:
                return f"{replacement} {base}", "prefix"

    romanized = romanize(latin)
    if romanized and not CJK.search(romanized):
        return strip_wrapping(romanized), "romanized"
    romanized_raw = romanize(raw)
    if romanized_raw and not CJK.search(romanized_raw):
        return strip_wrapping(romanized_raw), "romanized"
    return strip_wrapping(latin), "unresolved"


ENTITY_RE = re.compile(r"&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9A-Fa-f]+;)")


def xml_text(value: str) -> str:
    """Android string kaynağı için XML kaçışı (`&` → `&amp;`, `<`/`>` kaçırılır, `'`/`"` kaçırılır)."""
    value = ENTITY_RE.sub("&amp;", value)
    value = value.replace("<", "&lt;").replace(">", "&gt;")
    value = re.sub(r"(?<!\\)'", r"\'", value)
    value = re.sub(r'(?<!\\)"', r'\"', value)
    return value


def render_strings(path: Path, values: dict[str, str]) -> tuple[int, int]:
    """Var olan `<string name=...>` değerlerini değiştirir; eksikse ekler."""
    text = path.read_text(encoding="utf-8")
    changed = 0

    def repl(match: re.Match[str]) -> str:
        nonlocal changed
        name, body = match.group(1), match.group(2)
        raw = values.get(name)
        if raw is None:
            return match.group(0)
        new = xml_text(raw)
        if body == new:
            return match.group(0)
        changed += 1
        return f'<string name="{name}">{new}</string>'

    text = STRING_RE.sub(repl, text)
    added = 0
    missing = [(name, value) for name, value in values.items() if f'<string name="{name}">' not in text]
    if missing:
        anchor = '    <string name="bangumi_tag_'
        idx = text.find(anchor)
        if idx < 0:
            anchor = "    <string name=\"bangumi_org_"
            idx = text.find(anchor)
        block = "".join(f'    <string name="{n}">{xml_text(v)}</string>\n' for n, v in sorted(missing))
        if idx < 0:
            idx = text.rfind("</resources>")
            text = text[:idx] + block + text[idx:]
        else:
            text = text[:idx] + block + text[idx:]
        added = len(missing)
    path.write_text(text, encoding="utf-8")
    return changed, added


def write_dictionary(rows: list[tuple[str, str, str]], latin_by_key: dict[str, str]) -> int:
    """Sadece haritadaki anahtarların VAULT adlarını güncelle, ham takma adları koru."""
    changed = 0
    out_lines = []
    for line in ORG_STORE.read_text(encoding="utf-8").splitlines():
        fields = line.split("\t")
        new = latin_by_key.get(fields[1])
        if new is not None and (fields[2] != new or (len(fields) > 3 and fields[3] != new)):
            fields = fields[:2] + [new]  # TR=EN: ayrı dördüncü sütun gerekmez.
            line = "\t".join(fields)
            changed += 1
        out_lines.append(line)
    if changed:
        ORG_STORE.write_text("\n".join(out_lines) + "\n", encoding="utf-8")
    return changed


def _read_latin_map(path: Path) -> dict[str, str]:
    names: dict[str, str] = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line.startswith("#"):
            continue
        key, separator, name = line.partition("\t")
        if not separator or not name.strip() or key in names:
            raise SystemExit(f"{path.name}:{number}: yinelenen/boş Latin ad: {key}")
        if CJK.search(name) or re.search(r"[\u0400-\u052f]", name):
            raise SystemExit(f"{path.name}:{number}: Latin olmayan ad: {key}")
        names[key] = name
    return names


def _resource_values(path: Path) -> dict[str, str]:
    import xml.etree.ElementTree as ET
    return {node.attrib["name"]: "".join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
            for node in ET.parse(path).getroot() if node.tag == "string"}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="yazma yapmadan tam eşitlik denetle")
    args = parser.parse_args()

    orgs = _read_latin_map(LATIN_TSV)
    tags = _read_latin_map(TAG_TSV)
    if len(orgs) < 9457 or len(tags) < 18:
        raise SystemExit(f"Latin haritası eksik: kurum={len(orgs)}, etiket={len(tags)}")
    values = {**orgs, **tags}
    missing = set(orgs)
    mismatches: list[str] = []
    for raw in ORG_STORE.read_text(encoding="utf-8").splitlines():
        label, key, name, *rest = raw.split("\t")
        expected = orgs.get(key)
        if expected is None:
            continue  # VAULT'taki başka anahtarlar kesinlikle silinmez.
        missing.discard(key)
        if name != expected or (rest and rest[0] != expected):
            mismatches.append(key)
    if missing:
        raise SystemExit(f"orgs.tsv: Latin haritasındaki {len(missing)} anahtar yok: {sorted(missing)[:5]}")

    leaks = []
    resource_mismatches: list[str] = []
    for path in (RES_TR, RES_EN):
        actual = _resource_values(path)
        for key, expected in values.items():
            if actual.get(key) != expected:
                resource_mismatches.append(f"{path.parent.name}:{key}")
        for key, name in actual.items():
            if key.startswith(ORG_PREFIXES + TAG_PREFIXES) and CJK.search(name):
                leaks.append(f"{path.parent.name}:{key}")

    print(f"Kurum/etiket anahtarı            : {len(orgs)}")
    print(f"Kurum VAULT ad uyuşmazlığı       : {len(mismatches)}")
    print(f"Dil dosyası uyuşmazlığı          : {len(resource_mismatches)}")
    print(f"Dil dosyalarında CJK kurum/etiket : {len(leaks)}")
    if args.check:
        if mismatches or resource_mismatches or leaks:
            print("HATA: ilk farklar:", (mismatches + resource_mismatches + leaks)[:8], file=sys.stderr)
            return 1
        print("TÜM KURUM VE ETİKET ADLARI LATİN — TR/EN hazır.")
        return 0

    changed = write_dictionary(load_dict_rows(), orgs)
    tr_changed, tr_added = render_strings(RES_TR, values)
    en_changed, en_added = render_strings(RES_EN, values)
    print(f"orgs.tsv                         : {changed} satır güncellendi")
    print(f"values/strings.xml                : {tr_changed} değişti, {tr_added} eklendi")
    print(f"values-en/strings.xml             : {en_changed} değişti, {en_added} eklendi")
    if leaks and not (tr_changed or en_changed):
        print(f"HATA: harita dışında CJK kalan kaynaklar: {leaks[:8]}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
