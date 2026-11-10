# Kitsugi Beta v2.4.340

## 🇹🇷 Türkçe

### 🌐 Kurum ve etiket adlarının tamamı Latin alfabesinde — ekranda artık tek bir Çince/Japonca karakter yok

v2.4.339'a kadar Bangumi kaynaklı **kurum adları** (stüdyo / yapımcı / yayıncı / kanal / dergi) ve
bir grup **etiket** dil dosyalarına kaynaktaki yazımıyla (CJK) yazılıyordu. Bu turda 9.457 kurum
anahtarının ve 18 CJK etiket anahtarının tamamı Latin alfabesine sabitlendi; TR ve EN dosyalarında
kalan CJK **0**.

```
$ python3 scripts/sync_org_latin_strings.py --check
Kurum/etiket anahtarı            : 9457
  çözülemeyen (hâlâ CJK)         : 0
Dil dosyalarında CJK kurum/etiket : 0
TÜM KURUM VE ETİKET ADLARI LATİN — TR/EN hazır.
```

#### 1) 5.223 kurum adı + 18 etiket çevrildi

| Alan | Çevrilen anahtar |
|---|---|
| Yapımcı (`bangumi_producer_*`) | 2.941 |
| Yayınevi (`bangumi_publisher_*`) | 890 |
| Stüdyo (`bangumi_studio_*`) | 809 |
| Dergi (`bangumi_magazine_*`) | 452 |
| Kanal (`bangumi_network_*`) | 131 |
| Etiket (`bangumi_tag_cjk_*`, `bangumi_tag_ga_2`) | 18 |
| **Toplam** | **5.241** |

TR dosyasında 5.315, EN dosyasında 5.300 `<string>` değeri değişti (18 etiket + 18 arayüz
metni dahil). Öncesi/sonrası CJK satır sayısı: **5.245 → 0** (TR), **5.241 → 0** (EN).

#### 2) Yöntem — uydurma çeviri yok

9.457 anahtarın dağılımı: **4.259** anahtarın kaynağında zaten resmî Latin yazım var
(`京都アニメーション` + `Kyoto Animation`), **65** dergi öneki türetildi, **5.133** ad
romanize edildi.

Sıra şu:

1. **Resmî/yerleşik Latin ad** — sözlükte aynı şirketin Latin yazımı zaten varsa (diğer kaynaklardan
   gelen `Kyoto Animation`, `BONES`, `MAPPA` gibi) o kullanılır; iki yazım tek çipte toplanır.
2. **Dergi öneki** — `月刊` → `Monthly`, `週刊` → `Weekly`, `別冊` → `Bessatsu`, `増刊` → `Extra`
   vb. + kalan adın Latin karşılığı (`月刊アフタヌーン` → `Monthly Afternoon`).
3. **Romanizasyon** — Japonca için Hepburn (pykakasi), Çince için pinyin (pypinyin), Korece için
   Gözden Geçirilmiş Romanizasyon; NFKC normalleştirme ve `﨑→崎` gibi uyumluluk eşlemeleri.
   `まんが4コマぱれっと` → `Manga 4 Koma Paretto`, `软房子` → `Ruanfangzi`, `코믹GT` → `Komik GT`.
4. **Olduğu gibi** — ad zaten Latin ise (`BONES`, `A-1 Pictures`) dokunulmaz.

Çözülemeyen anahtar: **0**.

> **İkinci geçiş:** ilk turda bazı Çince adlar kakasi'de tek heceye düşüp kalıcılaşmıştı
> (`电击魔王` → `Maou`, `张圣晏` → `An`). Üretici artık Latin alanına değil **kaynak etikete**
> bakıp her tur yeniden türetiyor; 397 değer düzeltildi (`Dian Ji Mo Wang`, `Zhang Sheng Yan`)
> ve `髙→高` uyumluluk eşlemesi eklendi (`髙橋竜` → `Takahashi Ryuu`).

#### 3) TR ve EN **aynı** Latin yazımı kullanıyor

Kurum adları özel addır, çevrilmez; iki dilde de aynı yazılır. Betik bunu zorunlu kılıyor:

```
kurum anahtarlarında TR ≠ EN : 0
```

Yalnızca **etiketler** çevrilir (`bangumi_tag_movie` → TR `Film`, EN `Movie`) — orada da TR/EN
simetrisi anahtar bazında korunuyor (17.795 string, 4 dizi, biçim yer tutucuları birebir).

#### 4) Ekranda kalan Çince açıklama parçaları da temizlendi

Kaynak etiketin kendisi parantez içinde bırakılmış 18 arayüz metni vardı
(`izleme listesi (在看)`, `kayıt (条目) arama`, `(打格子)`, `"允许" (İzin Ver)`, `收藏`,
`小说/画集`, `(个人令牌)`, `收藏热度`, `三次元`, `开发者平台 → 我的应用 → 创建应用` …). Hepsi
TR/EN karşılığıyla değiştirildi ya da tamamen kaldırıldı; `「Sunflower Studio」` gibi sarılı
gelen kurum adlarındaki tırnaklar da atıldı.

#### 5) Yeni araçlar ve kalıcı kaynaklar

| Dosya | Ne yapar |
|---|---|
| `scripts/sync_org_latin_strings.py` | Kurum/etiket Latin ad üreteci; `--check` modu CI için |
| `scripts/fix_cjk_ui_strings.py` | Arayüz metinlerindeki CJK kalıntısını temizler; `--check` |
| `diag/bangumi_master/org_latin.tsv` | 9.457 anahtar → Latin ad (kalıcı, elden geçirilebilir) |
| `diag/bangumi_master/tag_latin.tsv` | 18 etiket → Latin ad (elle düzeltilmiş romanizasyon) |

`BangumiOrgDictionary.kt` artık üçüncü alanında Latin adı da taşıyor
(`CJK ad|anahtar|Latin ad`), böylece sözlükten doğrudan arayüze giden yol da Latin.

#### 6) Doğrulama

```
sync_org_latin_strings.py --check        → 9.457 anahtar · 0 çözülemeyen · dil dosyalarında CJK 0
fix_cjk_ui_strings.py --check            → Arayüz metinlerinde CJK açıklama yok
sync_bangumi_tag_strings.py --check      → Zaten güncel (1.325 etiket)
sync_taxonomy_tag_strings.py --check     → Zaten güncel (114 etiket)
check_localization_resource_parity.py    → 17.795 string + 4 dizi, biçim yer tutucuları eşit
check_taxonomy_master.py                 → 114 sözlük girişi, 79 resmî tür, 0 uyarı
check_bangumi_meta_tag_coverage.py       → 158 resmî meta etiket, eksik 0
audit_taxonomy_sources.py --strict       → 35 alan · 7 kaynak · eksik yok
XML (values + values-en)                 → ET.parse temiz
```

---

## 🇬🇧 English

### 🌐 Every organization and tag name is now Latin — not a single CJK character left on screen

Until v2.4.339, Bangumi-sourced **organization names** (studio / producer / publisher / network /
magazine) and part of the **tag** set were written into the resource files exactly as the source
spells them (CJK). This round pins all 9,457 organization keys and 18 CJK tag keys to Latin script;
CJK left in the TR and EN files: **0**.

#### 1) 5,223 organization names + 18 tags converted

Producers 2,941 · Publishers 890 · Studios 809 · Magazines 452 · Networks 131 · Tags 18 —
**5,241 keys** total (TR: 5,315 string values changed, EN: 5,300). CJK lines before/after:
**5,245 → 0** (TR) and **5,241 → 0** (EN).

#### 2) Method — no invented translations

1. **Established Latin name** — if the dictionary already knows the company in Latin (`Kyoto
   Animation`, `BONES`, `MAPPA`), that spelling wins and the two variants collapse into one chip.
2. **Magazine prefix** — `月刊` → `Monthly`, `週刊` → `Weekly`, `別冊` → `Bessatsu`,
   `増刊` → `Extra`, plus the Latin remainder.
3. **Romanization** — Hepburn (pykakasi) for Japanese, pinyin (pypinyin) for Chinese, Revised
   Romanization for Korean, with NFKC normalization and compatibility maps (`﨑→崎`).
4. **As-is** — names already in Latin are untouched.

Unresolved keys: **0**.

#### 3) TR and EN share one Latin spelling

Company names are proper nouns and are not translated; both locales show the same string. The
script enforces it: **organization keys where TR ≠ EN: 0**. Only tags are translated
(`bangumi_tag_movie` → TR `Film`, EN `Movie`), and placeholder parity holds across 17,795 strings.

#### 4) Chinese fragments in UI copy removed as well

18 interface strings kept the source label in parentheses (`(在看)`, `(条目)`, `(打格子)`,
`"允许" (Allow)`, `收藏`, `小说/画集`, `(个人令牌)`, `收藏热度`, `三次元`,
`开发者平台 → 我的应用 → 创建应用`). All were replaced with TR/EN wording or dropped, and
wrapping brackets in names such as `「Sunflower Studio」` are stripped.

#### 5) New tooling and durable sources

`scripts/sync_org_latin_strings.py` (with `--check`), `scripts/fix_cjk_ui_strings.py` (with
`--check`), `diag/bangumi_master/org_latin.tsv` (9,457 keys → Latin name) and
`diag/bangumi_master/tag_latin.tsv` (18 hand-fixed tag romanizations). `BangumiOrgDictionary.kt`
now carries the Latin name as its third field, so the dictionary path is Latin too.

#### 6) Verification

```
sync_org_latin_strings.py --check     → 9,457 keys · 0 unresolved · 0 CJK in resources
fix_cjk_ui_strings.py --check         → no CJK fragments in UI copy
sync_bangumi_tag_strings.py --check   → up to date (1,325 tags)
sync_taxonomy_tag_strings.py --check  → up to date (114 tags)
check_localization_resource_parity.py → 17,795 strings + 4 arrays, placeholders match
check_taxonomy_master.py              → 114 dictionary entries, 79 official genres, 0 warnings
check_bangumi_meta_tag_coverage.py    → 158 official meta tags, 0 missing
audit_taxonomy_sources.py --strict    → 35 fields · 7 sources · nothing missing
XML (values + values-en)              → ET.parse clean
```
