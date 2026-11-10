# PLAN & TASK — Tüm kaynaklar için ortak tür / etiket / kurum katmanı (v2.4.339)

**Tarih:** 2026-10-21 · **Dal:** `arena/a610f459-kitsugi-beta` · **Taban:** `971abf1`
**Kök neden raporu:** [KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md](KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md)

---

## 1) Hedef (kullanıcı isteği)

1. **Bangumi'de yaptığımızın aynısı** tüm detay kaynakları için: AniList, MAL/Jikan, Shikimori,
   Kitsu, Simkl, TMDB.
2. Türler **ve** etiketler **birleştirilecek** (biri diğerini ezmeyecek); Bangumi'nin hazır
   etiketleri/türleri diğer kaynaklarla birlikte görünecek.
3. Eksik kalan tür/etiket varsa **tamamlanacak**, TR/EN dil dosyalarıyla uyumlu çalışacak.
4. Aynı şekilde **yayıncı / yapımcı / stüdyo / kanal / dergi** bilgileri tüm kaynaklardan
   eksiksiz toplanacak.
5. **Rusça ve Çince metin ekranda görünmeyecek.**

---

## 2) Kabul ölçütleri

| # | Ölçüt | Doğrulama |
|---|---|---|
| A1 | İki kaynakta da tür varsa **ikisi de** listede | `TaxonomyLabelsTest` + `KitsugiTaxonomyMerge.mergeGenres` |
| A2 | `Экшен` · `Action` · `アクション` · `动作` · `Aksiyon` ekranda **tek çip** | `taxonomyCanonicalKey` testleri |
| A3 | Shikimori'nin 79 resmî türünün tamamı sözlükte | `check_taxonomy_master.py` K5 |
| A4 | TR/EN değerlerinde tek bir Kiril harf yok | K4 |
| A5 | Bilinmeyen Kiril etiket Latin harfine çevrilir, CJK etiket gizlenir | `transliterateCyrillic`, `isUnresolvedForeignLabel` |
| A6 | Aynı kurum stüdyo+yapımcı olarak iki kez görünmez | `dropProducersThatAreStudios` |
| A7 | `bangumi_tag_*` + `taxonomy_tag_*` kaynakları tr/en dosyalarında eşit | `check_taxonomy_master.py` K6, `check_localization_resource_parity.py` |
| A8 | İki üreteç betiği art arda çalıştırılabilir (idempotent) | `sync_*_tag_strings.py --check` |
| A9 | Kurum alanındaki çöp (kaomoji, `;` notları, tırnak artığı) ekranda görünmez | `sanitizeOrgName` + `KitsugiStudiosCard` filtresi |
| A10 | Değiştirilen Kotlin dosyaları sözdizimsel olarak geçerli | `tree-sitter-kotlin` ayrıştırması |

---

## 3) Mimari

```
detay açılışı (herhangi bir kaynak)
        │
        ├─ fetchPrimaryDetail ──► kaynağın kendi tür/etiket/kurum verisi
        │
        └─ enrichDetail (zaman tavanlı, arka plan)
                 │
                 ├─ TMDB zenginleştirmesi ──► trMeta (tür + keyword + şirket/kanal)
                 ├─ Bangumi enrich (yalnız Bangumi kaynağı) ──► MAL/AniList birleşimi
                 ├─ AniList hafif sorgu ──► genres + tags(+spoiler/rank) + studios
                 └─ KitsugiTaxonomySources.collect()
                          ├─ Jikan  : genres/themes/demographics/studios/producers/licensors
                          └─ AniList: (yukarıdaki yanıtla aynı)
                 │
                 ▼
        KitsugiTaxonomyMerge  ──► mergeGenres / mergeTags / mergeOrgs / dropProducersThatAreStudios
                 │
                 ▼
        gösterim katmanı: toLocalizedTaxonomyLabel / toDisplayOrgName
                 │
                 ▼
        dil dosyası override'ı: taxonomy_tag_* , bangumi_tag_*
```

**Zaman bütçesi:** toplam 6 sn (`TOTAL_TIMEOUT_MS`), kaynak başına 4,5 sn. Geciken kaynak atlanır;
sayfa asıl detayla açılmaya devam eder. Sonuç `DetailCache` + Room'a yazılır, ikinci açılışta ağ yok.

---

## 4) Değişen / eklenen dosyalar

### Yeni

| Dosya | Görev |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/utils/RussianTaxonomyDictionary.kt` | 114 grup; Shikimori'nin tamamı + Rusça etiketler; MAL tür kimliği eşlemesi |
| `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTaxonomyLabels.kt` | Tek çözümleyici, kanonik anahtar, Kiril transliterasyonu, kurum gösterimi |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiTaxonomyMerge.kt` | Tür/etiket/kurum birleştirme motoru |
| `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiTaxonomySources.kt` | Paralel + zaman tavanlı çapraz kaynak toplayıcı (Jikan + AniList) |
| `app/src/test/java/com/kitsugi/animelist/utils/TaxonomyLabelsTest.kt` | 23 birim test (TR/EN, kanonik anahtar, transliterasyon, birleştirme) |
| `scripts/sync_taxonomy_tag_strings.py` | `taxonomy_tag_*` kaynaklarını üretir (idempotent, `--check`) |
| `scripts/check_taxonomy_master.py` | K1–K9 denetimleri + `diag/taxonomy_master/worklist.tsv` |
| `.github/workflows/taxonomy-master.yml` | CI: sözlük denetimi, üreteç `--check`, TR/EN paritesi, `TaxonomyLabelsTest` |
| `diag/taxonomy_master/README.md` | Sözlük çalışma akışı (Drive'a yükleme dahil) |

### Kaynak kapsama matrisi

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Kanal | Yayıncı | Dergi |
|---|---|---|---|---|---|---|---|
| AniList | ✔ `genres` | ✔ `tags` | ✔ `studios` | — | — | — | — |
| MAL/Jikan | ✔ `genres` | ✔ `themes`/`demographics` | ✔ | ✔ `producers`/`licensors` | — | ✔ (manga) | ✔ |
| Bangumi | ✔ | ✔ `tags` | ✔ 动画制作 | ✔ 製作/委員会/協力/レーベル | ✔ 播放电视台 | ✔ 出版社 | ✔ 连载杂志 |
| Shikimori | ✔ `genres` | ✔ theme/demographic | ✔ `studios` | — | — | — | — |
| Kitsu | ✔ `categories` | ✔ `categories` | ✔ `animeProductions` | ✔ rol=producer | ✔ rol=network | ✔ rol=publisher | ✔ rol=magazine |
| Simkl | ✔ `genres` | ✔ | — | — | — | — | — |
| TMDB | ✔ `genres` | ✔ `keywords` | ✔ şirket[0] | ✔ şirket[1…] | ✔ `networks` | — | — |

Çapraz toplayıcı (`KitsugiTaxonomySources`): **Jikan + AniList + Shikimori + Kitsu** paralel
(Simkl kurum API'si yayınlamadığı, TMDB zenginleştirmesi ayrı yürüdüğü için hariç).

### Değişen

| Dosya | Değişiklik |
|---|---|
| `KitsugiTranslations.kt` | `toTurkishGenre` / `toLocalizedTagLabel` tek çözümleyiciye bağlandı; `taxonomyStringOverride`, `englishTaxonomyName`, `englishTaxonomyToTurkish`; `localizedDistinctTags` CJK'yı gizler; kurum adı Kiril transliterasyonu + `sanitizeOrgName()` |
| `KitsugiDetailComponents.kt` | Stüdyo/yapımcı/kanal kartı: adı temizlenince boşalan kayıtlar çizilmez; başlıklar görünür listeye göre seçilir |
| `KitsuClient.kt` · `KitsuExploreClient.kt` | `animeProductions`/`mangaProductions` ilişkisi + rol eşlemesi (studio/producer/licensor/publisher/magazine/network) |
| `KitsugiShikimoriClient.kt` | `kind` ayrımı → `genres` / `themes` / `demographics` |
| `scripts/audit_taxonomy_sources.py` | 28 alan + 4 çapraz kaynak kapsama denetimi (`--strict`) |
| `KitsugiTaxonomyLabels` ile birlikte `KitsugiSpoilerRules.canonicalTagKey` | Rusça anahtar önceliği (`Экшен` ≡ `Action`) |
| `KitsugiAniListDetailClient.kt` | `AniListTagsAndAiring` + `genres` + `studios`; GraphQL sorgusu genişletildi |
| `KitsugiDetailClient.kt` | Tür/kurum zenginleştirmesi birleştirmeye çevrildi; çapraz kimlik çözümü AniList kapısından çıkarıldı; taksonomi toplama + normalleştirme eklendi |
| `KitsugiBangumiDetailClient.kt` | `ifEmpty` yerine `mergeGenres` / `mergeOrgs` / `dropProducersThatAreStudios` |
| `SearchTranslation.kt` | `translateToTurkishForDisplay` / `translateToEnglishForSearch` / `displayLabel` Rusça desteği |
| `scripts/sync_bangumi_tag_strings.py` | **Yıkıcı** blok yazımı düzeltildi (veri kaybı önlendi) |
| `values/strings.xml`, `values-en/strings.xml` | +114 `taxonomy_tag_*`; Bangumi bloğu tek parça, eksik 494 kayıt yazıldı, artık kaynak silindi |
| `app/build.gradle.kts` | 2.4.336 → 2.4.337 |

---

## 5) Görev listesi

- [x] T1 — Kök neden analizi (KN-1…KN-6) ve kod kanıtları
- [x] T2 — Rusça sözlük (114 grup / 79 resmî tür + MAL kimlikleri)
- [x] T3 — Tek çözümleyici + kanonik anahtar + Kiril transliterasyonu
- [x] T4 — Birleştirme motoru (tür / etiket / kurum, rol-farkında tekilleştirme)
- [x] T5 — Çapraz kaynak toplayıcı (AniList + Jikan), zaman tavanlı
- [x] T6 — AniList sorgusuna `genres` + `studios`
- [x] T7 — `enrichDetail` ve Bangumi `mergeDetail` entegrasyonu
- [x] T8 — TMDB tür ve şirketlerinin birleştirmeye katılması
- [x] T9 — `taxonomy_tag_*` üreteci + K1–K9 denetleyici + iş listesi
- [x] T10 — `sync_bangumi_tag_strings.py` veri kaybı düzeltmesi + dil dosyası onarımı
- [x] T11 — Birim testleri
- [x] T12 — Rapor + plan + sürüm notu + sürüm artırımı
- [x] T13 — Değişen dosyaların zip'i ve iş listesinin Drive'a yüklenmesi
- [x] T14 — Kurum adı temizliği (`sanitizeOrgName`) + `KitsugiStudiosCard` filtresi
- [x] T15 — `area_chinese_speaking` yazım hatası ve dil dosyası eşitlemesi
- [x] T16 — `taxonomy-master` CI iş akışı + tree-sitter sözdizimi doğrulaması
- [x] T18 — `gen_bangumi_master.py` güvenlik freni (orgs.tsv 365 satır ↔ sözlük 11.693 satır)
- [x] T19 — Kitsu `animeProductions`/`mangaProductions` (stüdyo/yapımcı/yayıncı/dergi)
- [x] T20 — Shikimori `kind` ayrımı (genre/theme/demographic)
- [x] T21 — Bangumi ek infobox kurum anahtarları (制作協力, 音楽制作, レーベル, 配給, 掲載誌 …)
- [x] T22 — Birleştirme tavanları: tür 48 · etiket 96 · kurum 64
- [x] T23 — Çapraz toplayıcıya Shikimori + Kitsu eklendi (4 kaynak paralel)
- [x] T24 — `scripts/audit_taxonomy_sources.py` kapsama denetimi + CI adımı
- [ ] T17 — CI'da `./gradlew test` + cihazda TR/EN/RU arayüzlü duman testi (bu sandbox'ta JDK yok)

---

## 6) Cihazda denenecek senaryolar

| # | Senaryo | Beklenen |
|---|---|---|
| D1 | Shikimori kaynağından bir anime aç | Türler **Türkçe**; hiçbir çipte Kiril yok |
| D2 | Aynı animeyi Bangumi'den aç | Bangumi etiketleri + AniList etiketleri birlikte; tekrar yok |
| D3 | MAL kaynaklı bir anime | Stüdyo listesinde MAL + AniList stüdyoları birleşik, kimlikli kayıt tercih edilmiş |
| D4 | TMDB filmi | Yapım şirketleri + kanallar birleşik; yapımcıda stüdyo tekrarı yok |
| D5 | Arayüz dili EN | Türler İngilizce kanonik adlarla (`Action`, `Slice of Life`) |
| D6 | İlk açılış süresi | Enrich 6 sn tavanını aşmıyor; sayfa asıl veriyle açılıyor |
| D7 | Aynı sayfayı ikinci kez aç | Ağ isteği yok (Room/DetailCache) |

---

## 7) Drive paketi

`Kitsugi_Ortak_Taksonomi_v2.4.339.zip` içeriği:

- `PLAN_TASK_TUM_KAYNAKLAR_ORTAK_TAKSONOMI_2026-10-21.md`
- `KOK_NEDEN_RAPORU_TUR_ETIKET_KURUM_EKSIKLERI_2026-10-21.md`
- `release_notes_v2.4.339.md`
- `diag/taxonomy_master/worklist.tsv`
- `diag/taxonomy_master/README.md`
- `Kitsugi_Ortak_Taksonomi_v2.4.339.patch` (tam diff)

---

## 8) Üçüncü tur işleri (v2.4.339) — tavanlar, rol bütünlüğü, 6 kaynak

| # | İş | Durum | Dosya |
|---|---|---|---|
| T25 | Bangumi etiket tavanı 40 → 400, tür 12 → 64 | ✅ | `KitsugiBangumiDetailClient` |
| T26 | Birleşik tavanlar: tür 160 · etiket 600 · kurum 256 · tema 64 · hedef kitle 32 | ✅ | `KitsugiTaxonomyMerge`, `KitsugiSpoilerRules` |
| T27 | `dropProducersThatAreStudios` kaldırıldı (kurum rolden dolayı silinmiyor) | ✅ | `KitsugiTaxonomyMerge`, `KitsugiDetailClient`, `KitsugiBangumiDetailClient` |
| T28 | Tekilleştirme anahtarı rol duyarlı (`rol + ad`) | ✅ | `KitsugiTaxonomyMerge.orgDedupKey` |
| T29 | Çapraz toplayıcıya **Bangumi** (normalize başlık + yıl ±1) ve **TMDB** eklendi | ✅ | `KitsugiTaxonomySources.fetchBangumi/fetchTmdb` |
| T30 | Bangumi sayfaları da aynı havuzu kullanıyor (`includeBangumi=false`) | ✅ | `KitsugiBangumiDetailClient.enrich` |
| T31 | Etiket kartı daraltılmış görünümü 10 → 30 | ✅ | `KitsugiDetailComponents` |
| T32 | Dergi/yayınevi için ayrı bölüm + `detail_section_serials` (TR/EN) | ✅ | `KitsugiDetailComponents`, `ApiDetailTabContents`, `EntryDetailTabContents`, `strings.xml` |
| T33 | MAL lisans verenleri `LICENSOR` rolünde | ✅ | `KitsugiMalDetailClient` |
| T34 | Denetim: 6 çapraz kaynak + tavan alt sınırları + kurum eleme yasağı | ✅ | `scripts/audit_taxonomy_sources.py` |
| T35 | Cihaz teşhisi: kaynak bazlı + birleşik sayaç logları | ✅ | `KitsugiTaxonomySources`, `KitsugiDetailClient`, `KitsugiBangumiDetailClient` |

### Yeni kabul ölçütleri

| # | Senaryo | Beklenen |
|---|---|---|
| D8 | 100+ etiketli bir Bangumi yapımı | Etiket kartında genişletince **en az 100** çip (eskiden 40) |
| D9 | MAPPA yapımı bir anime (MAL) | MAPPA **hem** Stüdyolar **hem** Yapımcılar altında görünür |
| D10 | TV dizisi (TMDB) | Kanallar ayrı bölümde; dergi/yayınevi varsa **“Dergi / Yayın”** başlığı altında |
| D11 | Aynı yapım MAL ve Bangumi'den açılır | Tür/etiket/kurum kümeleri **aynı** (6 kaynak ortak havuz) |
| D12 | Logcat | `Kaynak bazlı:` satırında 6 kaynak; `Birleşik taksonomi:` nihai sayılar |

---

## 9) Dördüncü tur işleri (v2.4.339) — kaynak kaynak son boşluklar

| # | İş | Durum | Dosya |
|---|---|---|---|
| T36 | Shikimori `licensors` + `publishers` ayrıştırma (string/nesne) | ✅ | `KitsugiShikimoriClient` |
| T37 | Shikimori kurumları detay + çapraz toplayıcıya bağlandı | ✅ | `KitsugiShikimoriClient`, `KitsugiTaxonomySources` |
| T38 | Simkl `network` → Kanal; `studios` sabit boş kaldırıldı | ✅ | `KitsugiSimklDetailClient` |
| T39 | Simkl çapraz toplayıcıya eklendi (başlık + normalize eşleşme) | ✅ | `KitsugiTaxonomySources.fetchSimkl` |
| T40 | Jikan manga `serializations` + `publishers` çapraz toplayıcıda | ✅ | `KitsugiTaxonomySources.fetchJikan` |
| T41 | Kitsu manga `mangaProductions` isteği + 400 yedeği | ✅ | `KitsuExploreClient` |
| T42 | Denetim 28 → **35 alan**, 4 → **7 çapraz kaynak** | ✅ | `scripts/audit_taxonomy_sources.py` |

### Yeni kabul ölçütleri

| # | Senaryo | Beklenen |
|---|---|---|
| D13 | Shikimori kaynaklı anime | Lisans verenler bölümü dolu (Rusça değil, Latin) |
| D14 | Simkl kaynaklı dizi/anime | Kanal bölümü dolu |
| D15 | MAL kaynaklı manga | Dergiler bölümü dolu |
| D16 | Kitsu kaynaklı manga | Yayınevi/dergi görünüyor |
| D17 | `audit --strict` | 35 alan + 7 çapraz kaynak, eksik yok |

---

## 10) Beşinci tur işleri (v2.4.340) — kurum ve etiket adlarının dil tutarlılığı

Kapsam daraltıldı: yeni kaynak/alan eklenmedi; **var olan 9.457 kurum anahtarı** ile 18 CJK
etiket anahtarının TR ve EN dil dosyalarındaki yazımı tek bir kurala bağlandı.

| # | İş | Durum | Dosya |
|---|---|---|---|
| T43 | 9.457 kurum anahtarı için Latin ad üreteci (resmî ad → dergi öneki → romanizasyon) | ✅ | `scripts/sync_org_latin_strings.py` |
| T44 | TR + EN dil dosyalarına ve `BangumiOrgDictionary.kt` 3. alanına senkron | ✅ | `values/strings.xml`, `values-en/strings.xml`, `BangumiOrgDictionary.kt` |
| T45 | 18 CJK etiket anahtarının `BangumiTagDictionary.kt` TR/EN alanlarına Latin ad | ✅ | `BangumiTagDictionary.kt`, `tag_latin.tsv` |
| T46 | Arayüz metinlerinde kalan Çince açıklama parantezlerinin temizliği | ✅ | `scripts/fix_cjk_ui_strings.py` |
| T47 | Üretici kaynak etiketten yeniden türetir (kalıcı hatalı romanizasyonu düzeltir) | ✅ | `sync_org_latin_strings.py` |

### Kurallar

1. **Kurum adı çevrilmez.** Aynı şirket iki dilde de aynı Latin yazımıyla görünür
   (`kurum anahtarlarında TR ≠ EN: 0`). Yalnızca etiketler (ortak adlar) çevrilir.
2. **Resmî Latin ad varsa o kazanır**; yoksa dergi öneki (`月刊`→`Monthly`, `別冊`→`Bessatsu`),
   yoksa romanizasyon (Hepburn / pinyin / RR).
3. **Üretilen ad kalıcıdır**: `org_latin.tsv` ve `tag_latin.tsv` elden geçirilebilir; betik
   `--check` modunda fark bırakmaz (CI).
4. **Ekranda CJK yok**: dil dosyalarında kurum/etiket/arayüz metni olarak CJK kalırsa
   `sync_org_latin_strings.py --check` ve `fix_cjk_ui_strings.py --check` kırılır.

### Yeni kabul ölçütleri

| # | Senaryo | Beklenen |
|---|---|---|
| D18 | Bangumi kaynaklı herhangi bir yapım | Stüdyo/yapımcı/yayıncı/dergi/kanal çiplerinde tek bir CJK karakter yok |
| D19 | Aynı yapım TR ve EN arayüzde | Kurum adları **birebir aynı** Latin yazımda; etiketler dile göre çevrili |
| D20 | `grep -P '[\x{4e00}-\x{9fff}]' values*/strings.xml` | 0 satır |
