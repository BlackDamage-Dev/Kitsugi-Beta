# KÖK NEDEN RAPORU — Tüm kaynaklarda eksik tür / etiket / kurum ve Rusça–Çince sızıntısı

**Tarih:** 2026-10-21 · **Dal:** `arena/a610f459-kitsugi-beta` · **Taban:** `971abf1` (v2.4.336-beta)
**Kapsam:** AniList · MAL/Jikan · Bangumi · Shikimori · Kitsu · Simkl · TMDB detay sayfalarındaki
**tür (genres)**, **etiket (tags)**, **stüdyo / yapımcı / yayıncı / kanal / dergi** alanları.

---

## 1) Kullanıcı belirtisi

> “Birçok kaynakta bu türler ve etiketler kısmında eksiğimiz gözden kaçmış. Bangumi için yaptığımız
> gibi diğer tüm kaynakların türleri, etiketleri, yayıncı/yapımcı/stüdyo kısımları eksiksiz
> toplanacak; **Rusça veya Çince görünmesini istemiyorum**.”

Somut gözlemler:

| # | Gözlem | Nerede görülüyor |
|---|---|---|
| 1 | Bir kaynakta tür listesi doluysa diğer kaynakların türleri **hiç gelmiyor** | MAL/Jikan, Kitsu, Simkl, TMDB, Bangumi detayları |
| 2 | Shikimori türleri **Rusça** görünüyor (`Экшен`, `Повседневность`) | Shikimori detay sayfası |
| 3 | Bangumi'nin kendi stüdyo/yapımcı/kanal bilgisi diğer kaynakta varsa **siliniyor** | Bangumi detay sayfası |
| 4 | Aynı kurum hem “Stüdyolar” hem “Yapımcılar” altında iki kez | MAL/TMDB kaynaklı sayfalar |
| 5 | Sözlükte olmayan Çince etiket çip olarak ekranda kalıyor | Bangumi detay sayfası |

---

## 2) Kök nedenler (kod kanıtlı)

### KN-1 — Birleştirme değil, **yerine koyma** mantığı

`KitsugiDetailClient.enrichDetail` (TMDB zenginleştirmesi):

```kotlin
val updatedGenres = if (currentDetail.genres.isNotEmpty()) currentDetail.genres else trMeta.genres
val mergedStudios = if (currentDetail.studios.isNotEmpty()) currentDetail.studios else trMeta.studios
val mergedProducers = if (currentDetail.producers.isNotEmpty()) currentDetail.producers else trMeta.producers
val mergedNetworks  = if (currentDetail.networks.isNotEmpty())  currentDetail.networks  else trMeta.networks
```

`KitsugiBangumiDetailClient.mergeDetail`:

```kotlin
genres = other.genres.ifEmpty { base.genres },
studios = other.studios.ifEmpty { base.studios },
producers = other.producers.ifEmpty { base.producers },
serializations = other.serializations.ifEmpty { base.serializations },
```

Bu üçlü operatör (`if dolu ise onu, değilse diğerini`) **iki kaynağın verisini asla birleştirmez.**
Birincil kaynak doluysa ikincil kaynağın türleri/kurumları kaybolur; birincil boşsa ikincilin
tamamı gelir. Kullanıcının “eksik kalmış” dediği tablo tam olarak budur.

### KN-2 — Çapraz kaynak toplama yalnızca **AniList etiketleri** için vardı

`enrichDetail` içinde tek çapraz çağrı `KitsugiAniListDetailClient.fetchTagsAndAiringInfo` idi ve
sorgu **yalnızca `tags` + `nextAiringEpisode`** alanlarını istiyordu:

```graphql
tags { id name description category rank isMediaSpoiler isGeneralSpoiler }
nextAiringEpisode { episode timeUntilAiring airingAt }
```

`genres`, `studios` **hiç istenmiyordu**. MAL/Jikan'ın `themes` / `demographics` / `licensors`
alanları, AniList'in stüdyo düğümleri ve TMDB'nin yapım şirketleri detay sayfasına hiç ulaşmıyordu.

### KN-3 — Shikimori verisi **Rusça** taşınıyor, çözümleyici yok

Shikimori REST yanıtındaki `genres[].name` alanı Rusçadır:

```kotlin
val gName = gObj.optString("name")   // "Экшен", "Повседневность", "Махо-сёдзё"
genresList.add(gName)
...
genres = genresList.toTurkishGenres(),
```

`toTurkishGenre()` yalnızca **Bangumi (Çince/Japonca)** sözlüğüne ve **İngilizce** tabloya bakıyordu:

```kotlin
cleaned.toLocalizedBangumiTagOrNull()?.let { return it }
if (!isTurkish()) return this
val mapped = genreMap[cleaned] ?: ...                 // İngilizce anahtarlar
return SearchTranslation.displayLabel(cleaned)        // bilinmeyen → olduğu gibi
```

Rusça bir tür `genreMap`'te yok → `displayLabel` değiştirmeden döndürüyor → **Kiril metin ekrana
düşüyor**. Depoda `[А-Яа-яЁё]` araması yapıldığında Rusça çeviri tablosunun **hiç olmadığı**
doğrulandı (`grep` yalnızca Türkçe yorum satırlarında Kiril buluyor).

### KN-4 — Çözülemeyen Çince etiket “listenin sonuna atılıyor”, **gösteriliyor**

```kotlin
if (isUntranslatedCjkTag(value)) untranslated += value else translated += value
...
return (translated + untranslated).take(limit)
```

Kullanıcı Çince metin görmek istemiyor; ancak kod onu bilinçli olarak listenin sonuna ekliyordu.
(Kullanıcı isteği: “eksiksiz **ve** Rusça/Çince görünmesin”.)

### KN-5 — Kurum adları gösterim katmanında çözülüyor, **modelde ham** kalıyor

`toLocalizedOrgName()` sözlükte olmayan adı olduğu gibi döndürüyordu. Rusça bir kurum adı
(`Студия Гибли`) hiçbir katmanda Latin harfine çevrilmiyordu. Ayrıca aynı kurum `studios` ve
`producers` listelerinde birlikte gelebildiği için **iki kez** görünüyordu (MAL/TMDB).

### KN-6 — Yan bulgu: `sync_bangumi_tag_strings.py` **veri kaybettiren** bir betikti

```python
head = text.split(marker, 1)[0]
updated = f"{head}{block}\n\n</resources>\n"   # işaretçiden sonrası SİLİNİR
```

Betik, `bangumi_tag_*` bloğunun dosyanın **son** bloğu olduğunu varsayıyor. Oysa blok 3.099.
satırda başlıyor; arkasında kurum kaynakları (`bangumi_studio_*`, `bangumi_magazine_*`), wiki ve
stream kaynakları var (~8.800 satır). Betik yazma modunda çalıştırılsaydı **bu satırların tamamı
silinecekti**. `--check` bu yüzden sürekli kırmızıydı; kimse yazma modunu çalıştırmaya cesaret
edemediği için sözlükteki **494 etiket** dil dosyalarına hiç yazılmamıştı (ayrıca sözlükte olmayan
`bangumi_tag_chinese_speaking` artığı dosyada duruyordu).

---

### KN-7 — Kurum alanında **veri olmayan değerler** çip olarak çiziliyordu

Bangumi infobox'ı ve kullanıcı katkıları zaman zaman kurum alanına ad değil çöp yazıyor:

| Değer | Kaynak | Sonuç |
|---|---|---|
| `っ´Д`)っゼロ式の家` | infobox | “Yayıncı” olarak kaomoji çipi (dil dosyasında `bangumi_publisher_cjk_8007fa`) |
| `"""アフタヌーン` | sözlük üretimi | Baştaki üç tırnak ekranda kalıyordu |
| `芳文社; B6版` | infobox `出版社` alanı | Baskı notu kurum adının parçası sanılıyordu |

Gösterim katmanı (`toLocalizedOrgName`) yalnızca çeviri yapıyor, **temizlik yapmıyordu**.

### KN-8 — Sözlük değeri ile dil dosyası arasında sessiz kayma

`BangumiTagDictionary` içinde `area_chinese_speaking` girdisinin Türkçesi `Çinçe Konuşulan Bölge`
(yazım hatası) iken `diag/bangumi_master/tags.tsv` doğru değeri (`Çince`) taşıyordu.
`verify_bangumi_master.py` bunu **1 UYUŞMAZLIK** olarak yakaladı; MASTER hattı daha önce
yeşil olduğu için hata sonradan girmişti.

### KN-9 — Üreteç, kurum sözlüğünü **binlerce satır** kırpabiliyordu

`diag/bangumi_master/gen_bangumi_master.py` yazma modunda `BangumiOrgDictionary.kt` dosyasını
`diag/bangumi_master/orgs.tsv`'den yeniden kuruyor. Ölçüm:

| Kaynak | Satır |
|---|---|
| `orgs.tsv` | 365 satır |
| `BangumiOrgDictionary.kt` (commit'li) | **11.693 eşleme satırı** |

Yani TSV, sözlüğün yalnızca küçük bir bölümünü taşıyor. Bu turda doğrulama amacıyla yazma modu
çalıştırıldı ve dosya **1.125 satıra düştü** (≈10.800 satır kayıp). Dosya `git checkout` ile
geri alındı; kaynak kod kaybı **oluşmadı**, ancak tuzak gerçekti.

Kalıcı çözüm: üretece **güvenlik freni** eklendi. Yeni satır sayısı mevcut satır sayısının
yarısından azsa sözlük **yazılmaz** (uyarı basılır, diğer üretimler devam eder); bilinçli
küçültme için `--force` gerekir. `orgs.tsv` mevcut sözlükten yeniden türetilene kadar bu fren
açık kalmalı.

### KN-10 — Kaynak başına **kurum alanları** eksik okunuyordu (ikinci tur)

Kullanıcı geri bildirimi: “tüm kaynaklara ait tüm kurum/tür/etiket — bu kadar az olamaz.”
Yeniden denetimde dört somut eksik bulundu:

| Kaynak | Bulgu | Etki |
|---|---|---|
| **Kitsu** | `animeProductions.producer` ilişkisi **hiç istenmiyordu** (`include=categories` yalnız) | Kitsu kaynaklı her sayfada stüdyo/yapımcı **tamamen boş** |
| **Kitsu (manga)** | `mangaProductions` ilişkisi yok | Manga sayfalarında yayıncı/dergi yok |
| **Shikimori** | `genres[].kind` okunuyor ama **ayrıştırılmıyordu** | theme/demographic bilgisi kayboluyor, filtreler yanlış çalışıyor |
| **Bangumi** | Infobox'ta yalnız 6 anahtar okunuyordu | `制作協力`, `音楽制作`, `レーベル`, `配給`, `掲載誌` gibi kurumlar hiç gelmiyordu |
| **Birleştirici** | `mergeOrgs` **24**, `mergeGenres` **24**, `mergeTags` **48** ile sınırlıydı | 24'ten fazla kurumu olan yapımlarda (uzun seriler, komiteler) veri **kesiliyordu** |
| **Çapraz toplayıcı** | Yalnızca **Jikan + AniList** sorgulanıyordu | Shikimori/Kitsu'de olup MAL'da olmayan stüdyo ve etiketler hiçbir sayfaya girmiyordu |

### Çözüm — kaynak kapsama matrisi (v2.4.337 sonrası)

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Kanal | Yayıncı | Dergi |
|---|---|---|---|---|---|---|---|
| AniList | `genres` | `tags` (rank + spoiler) | `studios` (isMain dahil) | — | — | — | — |
| MAL/Jikan | `genres`, `explicit_genres` | `themes`, `demographics` | `studios` | `producers`, `licensors` | — | `publishers` (manga) | `serializations` |
| Bangumi | meta etiketler | `tags` | 动画制作 | 製作 · 製作委員会 · 制作協力 · 音楽制作 · レーベル · 配給 | 播放电视台 · 放送局 | 出版社 | 连载杂志 · 掲載誌 |
| Shikimori | `genres` (`kind=genre`) | `genres` (`kind=theme/demographic`) | `studios` | — | — | — | — |
| Kitsu | `categories` | `categories` | `animeProductions` (`role=studio`) | `role=producer` | `role=network` | `role=publisher` | `role=magazine` |
| Simkl | `genres` | `genres` (resmî olmayan) | — *(API vermiyor)* | — | — | — | — |
| TMDB | `genres` | `keywords` | `production_companies[0]` | `production_companies[1…]` | `networks` | — | — |

**Çapraz toplayıcı** artık **dört** kaynağı paralel sorgular: MAL/Jikan · AniList · Shikimori ·
Kitsu. Cihaz günlüğünde kaynak bazlı sayaç basılır (`jikan=… | anilist=… | shikimori=… | kitsu=…`),
böylece hangi kaynağın ne kadar veri verdiği görülebilir.

Yeni denetim: `scripts/audit_taxonomy_sources.py --strict` — 28 alanı ve 4 çapraz kaynağı
denetler; bir alan refactor sırasında düşerse CI kırılır.

## 3) Çözüm özeti

| Kök neden | Çözüm | Dosya |
|---|---|---|
| KN-1 | `KitsugiTaxonomyMerge`: tür/etiket/kurum için **birleşim** (union) + diller arası kanonik tekilleştirme | `data/remote/KitsugiTaxonomyMerge.kt` |
| KN-2 | `KitsugiTaxonomySources`: AniList (genres + studios) + MAL/Jikan (genres/themes/demographics/studios/producers/licensors) paralel ve zaman tavanlı toplama | `data/remote/KitsugiTaxonomySources.kt` |
| KN-2 | AniList hafif sorgusuna `genres` + `studios { edges { isMain node { id name } } }` eklendi | `KitsugiAniListDetailClient.kt` |
| KN-3 | `RussianTaxonomyDictionary`: 114 grup, Shikimori'nin **79 resmî türünün tamamı** + yaygın Rusça etiketler; MAL kimlik eşlemesi | `utils/RussianTaxonomyDictionary.kt` |
| KN-3 | Tek çözümleyici `toLocalizedTaxonomyLabel()`: Bangumi → Rusça → İngilizce → **Kiril transliterasyonu** | `utils/KitsugiTaxonomyLabels.kt` |
| KN-4 | Sözlükte olmayan Çince/Japonca etiket **gösterimden çıkarılır** (modelde korunur) | `KitsugiTranslations.localizedDistinctTags` |
| KN-5 | `toLocalizedOrgName()` Kiril adı Latin harfine çevirir; `mergeOrgs` + `dropProducersThatAreStudios` tekrarı kaldırır | `KitsugiTranslations.kt`, `KitsugiTaxonomyMerge.kt` |
| KN-6 | Betik artık bloğu **yerinde** yeniden yazar; blok tek parça hâline getirildi, artık kaynak silindi, 494 eksik etiket dil dosyalarına yazıldı | `scripts/sync_bangumi_tag_strings.py` |
| KN-7 | `sanitizeOrgName()`: tırnak artefaktı, `;` sonrası baskı notu ve kaomoji ayıklanır; boşalan kayıtlar hem modelde hem `KitsugiStudiosCard` içinde çizilmez | `KitsugiTranslations.kt`, `KitsugiDetailComponents.kt` |
| KN-8 | Sözlük değeri `Çince` olarak düzeltildi, dil dosyası eşitlendi | `BangumiTagDictionary.kt`, `values/strings.xml` |
| KN-9 | Üreteç güvenlik freni: sözlük %50'den fazla küçülecekse yazılmaz (`--force` ile aşılır) | `diag/bangumi_master/gen_bangumi_master.py` |
| KN-10 | Kitsu yapım ilişkisi + Shikimori `kind` ayrımı + Bangumi ek infobox anahtarları + birleştirme tavanları (48/96/64) + Shikimori & Kitsu çapraz toplama | `KitsuClient`, `KitsuExploreClient`, `KitsugiShikimoriClient`, `KitsugiBangumiDetailClient`, `KitsugiTaxonomyMerge`, `KitsugiTaxonomySources` |

---

## 4) Doğrulama (bu sandbox'ta koşulanlar)

```
$ python3 scripts/check_taxonomy_master.py
TÜM KONTROLLER GEÇTİ — 114 sözlük girişi, 79 resmî Shikimori türü, 114 dil dosyası kaynağı, 0 uyarı.

$ python3 scripts/sync_taxonomy_tag_strings.py --check      → exit 0 ("Zaten güncel")
$ python3 scripts/sync_bangumi_tag_strings.py --check       → exit 0 ("Zaten güncel")   [önceden exit 1]
$ python3 scripts/check_localization_resource_parity.py     → exit 0 (17.795 string, 4 dizi eşit)
$ python3 scripts/check_taxonomy_master.py --worklist       → diag/taxonomy_master/worklist.tsv
```

**Ek doğrulama — gerçek Kotlin ayrıştırıcısı:** sandbox'ta JDK olmadığı için derleme yapılamıyor;
bunun yerine `tree-sitter-kotlin` ile **sözdizimi ağacı** kuruldu. Değiştirilen 12 dosya
`ERROR`/`MISSING` düğümü içermiyor. Depoda ayrıştırıcının hata verdiği 6 dosya var; bunların
tamamı **HEAD'de de aynı** (dil bilgisi kısıtı, bizim değişikliğimiz değil).

**Koşulamayanlar (dürüst boşluk):** `./gradlew test`, `kotlinc` derlemesi ve APK üretimi bu
sandbox'ta çalıştırılamadı (JDK yok). Cihaz/CI koşumu ve `TaxonomyLabelsTest` (24 test) CI'ya
bırakıldı; testlerin **beklediği değerler** sözlükten Python ile yeniden üretilerek tek tek
doğrulandı (79 resmî tür, `Экшен`→`Aksiyon`, `«СЁДЗЁ»`→`Shōjo`, MAL kimlikleri).

---

## 7) Üçüncü tur (v2.4.338) — “hâlâ az geliyor” geri bildiriminin kök nedenleri

İkinci turda kaynaklar eklendi, ama **sayılar hâlâ düşük çıkıyordu**. Bu turda ölçülebilir
nedenler tek tek kapatıldı:

| # | Kök neden | Kanıt (kod) | Düzeltme |
|---|---|---|---|
| **KN-11** | Bangumi'nin **tüm etiketleri 40'ta kesiliyordu** — en zengin kaynak fiilen devre dışıydı | `subject.tags.localizedDistinctTags(40)` (BangumiDetailClient:512) | **400** |
| **KN-12** | Bangumi türleri 12'de, birleşik tür 48'de, etiket 96'da, kurum 64'te kesiliyordu | `metaGenres` → 12; `mergeGenres(48)`, `mergeTags(96)`, `mergeOrgs(64)` | 64 / **160** / **600** / **256** |
| **KN-13** | **Kurumlar rolden dolayı siliniyordu**: aynı şirket stüdyo ise yapımcı listeden çıkarılıyordu (MAPPA, BONES …) | `dropProducersThatAreStudios` — 3 çağrı (mergeDetail, enrichDetail ×2, Bangumi mergeDetail) | Çağrılar kaldırıldı, `@Deprecated` + CI yasağı |
| **KN-14** | Tekilleştirme anahtarı **rol içermiyordu**: aynı ad farklı rolde (yapımcı + lisans veren) tek kayda yutuluyordu | `orgDedupKey(org) = orgDedupKey(org.name)` | `rol + ad` |
| **KN-15** | Çapraz toplayıcı **4 kaynaktı**; **Bangumi ve TMDB** yalnızca kendi sayfalarında çalışıyordu | `collect()` → jikan/anilist/shikimori/kitsu | **6 kaynak**: + Bangumi (başlık eşleşmesi) + TMDB |
| **KN-16** | Bangumi sayfaları Shikimori/Kitsu/TMDB verisi **almıyordu** (asimetri) | `KitsugiBangumiDetailClient.enrich` yalnız MAL/AniList/TMDB | `collect(includeBangumi=false)` ile aynı havuz |
| **KN-17** | Etiket kartı daraltılmış görünümde **yalnızca 10 etiket** gösteriyordu | `nonSpoilerTags.take(10)` | **30** (genişletince tümü) |
| **KN-18** | Dergi/yayınevi kayıtları “Yayıncı Ağlar” bölümüne karışıyordu | `networks = detail.networks + detail.serializations` | Ayrı **“Dergi / Yayın”** bölümü |
| **KN-19** | MAL lisans verenleri `PRODUCER` rolündeydi | `StudioRole.PRODUCER` (licensors) | `StudioRole.LICENSOR` |

### Somut beklenti (aynı yapım, 6 kaynak birleşik)

| Alan | v2.4.337 (tipik) | v2.4.338 (tipik) |
|---|---|---|
| Tür | 8–12 | 12–18 |
| Etiket | 35–60 | **90–200** |
| Stüdyo | 1–2 | 2–4 |
| Yapımcı | 3–8 | 8–20 |
| Kanal | 0–2 | 2–6 |
| Dergi | 0–1 | 0–2 |

Sayılar yapıma göre değişir; kritik olan **hiçbir kaynağın verisinin tavan veya rol elemesiyle
düşmemesi**. Cihazda `KitsugiDetailClient: Birleşik taksonomi: …` satırıyla doğrulanır.

### Doğrulama (üçüncü tur)

```
audit_taxonomy_sources.py --strict  → 28 alan · 7 kaynak · 6 çapraz kaynak · eksik yok
check_taxonomy_master.py            → 114 sözlük girişi, 79 resmî tür, 0 uyarı
sync_taxonomy_tag_strings.py --check / sync_bangumi_tag_strings.py --check → güncel
check_localization_resource_parity.py → 17.795 string + 4 dizi (TR/EN eşit)
gen_bangumi_master.py --check       → 365 org satırı, sözlük yazılmadı (güvenli mod)
check_bangumi_meta_tag_coverage.py  → 158 resmî meta etiket, 0 eksik
ktcheck (tree-sitter-kotlin)        → değişen tüm .kt dosyaları temiz
```

---

## 8) Dördüncü tur (v2.4.339) — API sözleşmesi ↔ kod denetimi

Bu turda yedi kaynağın **API yanıt alanları** koda karşı tek tek okundu. Dört gerçek eksik
bulundu; hepsi "alan var, kod okumuyor" tipindeydi.

| # | Kaynak | Bulgu | Kanıt | Düzeltme |
|---|---|---|---|---|
| **KN-20** | Shikimori | `licensors` (anime) ve `publishers` (manga) **hiç okunmuyordu** → lisans veren/yayınevi listesi her zaman boş | `fetchDetail` yalnız `genres` + `studios` okuyordu | `parseShikimoriOrgArray()` (string **ve** nesne dizisi) + detay + çapraz toplayıcı |
| **KN-21** | Simkl | `studios = emptyList()` **sabitti**; `network` alanı yok sayılıyordu | `KitsugiSimklDetailClient` 406–408 | `network` / `networks` → Kanal; Simkl **7. çapraz kaynak** oldu |
| **KN-22** | MAL/Jikan | Manga `serializations` (dergi) ve `publishers` (yayınevi) çapraz toplayıcıda okunmuyordu | `fetchJikan` yalnız studios/producers/licensors | İkisi `fetchJikan`'e eklendi |
| **KN-23** | Kitsu | Manga detayı `mangaProductions` ilişkisini **istemiyordu** | `include=categories` | `categories,mangaProductions.producer` + 400 yedeği |

### Kaynak başına kurum katkısı (v2.4.339)

| Kaynak | Stüdyo | Yapımcı | Lisans | Yayıncı | Kanal | Dergi |
|---|---|---|---|---|---|---|
| AniList | ✔ | — | — | — | — | — |
| MAL/Jikan | ✔ | ✔ | ✔ | ✔ (manga) | — | ✔ (manga) |
| Bangumi | ✔ | ✔ | — | ✔ (manga) | ✔ | ✔ |
| Shikimori | ✔ | — | ✔ | ✔ (manga) | — | — |
| Kitsu | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| TMDB | ✔ | ✔ | — | — | ✔ | — |
| Simkl | — | — | — | — | ✔ | — |

Hiçbir kaynakta "API alanı var ama kod okumuyor" durumu kalmadı; denetim bunu **35 alan +
7 çapraz kaynak** ile zorunlu kılıyor.

### Doğrulama (dördüncü tur)

```
audit_taxonomy_sources.py --strict  → 35 alan · 7 kaynak · 7 çapraz kaynak · eksik yok
check_taxonomy_master.py            → 114 sözlük girişi, 79 resmî tür, 0 uyarı
sync_taxonomy_tag_strings.py --check / sync_bangumi_tag_strings.py --check → güncel
check_localization_resource_parity.py → 17.795 string + 4 dizi
ktcheck (tree-sitter-kotlin)        → değişen tüm .kt dosyaları temiz
```

---

## 9) Beşinci tur (v2.4.340) — "alan var, adı hâlâ kaynak dilinde" sorunu

Önceki turlar **alanların okunmasını** kapattı; bu tur **okunan adın ekrana hangi yazımla
çıktığını** kapattı. Kök neden: kurum adları Bangumi infobox'ından geldiği yazımla (CJK) dil
dosyasına yazılıyordu; alan doluydu ama ad Türkçe/İngilizce arayüzde okunamıyordu.

| # | Kök neden | Etki | Çözüm |
|---|---|---|---|
| KN-24 | Kurum adları CJK olarak dil dosyasına yazılıyordu | 5.223 anahtar (TR 2.941 yapımcı, 890 yayınevi, 809 stüdyo, 452 dergi, 131 kanal) | `sync_org_latin_strings.py` → resmî ad / dergi öneki / romanizasyon |
| KN-25 | 18 `bangumi_tag_cjk_*` etiketi sözlükte CJK TR/EN ile duruyordu | Etiket çipinde Çince ad | `BangumiTagDictionary.kt` TR/EN alanları Latin adla değiştirildi |
| KN-26 | Arayüz metinlerinde kaynak etiket parantez içinde bırakılmıştı | `(在看)`, `(条目)`, `(打格子)`, `"允许"`, `收藏`, `小说/画集` … 18 metin | `fix_cjk_ui_strings.py` |
| KN-27 | Bazı adlar `「Sunflower Studio」` gibi sarılı geliyordu | Adın parçası olmayan tırnak ekranda | `strip_wrapping()` (kurum adı normalizasyonu) |
| KN-28 | İlk turda Çince adlar kakasi'de tek heceye düşüp **kalıcılaştı** (`电击魔王` → `Maou`) | 397 hatalı ad | Üretici Latin alanına değil kaynak etikete bakar; `髙→高` eşlemesi |

### Kural

- **Kurum adı özel addır, çevrilmez** → TR ve EN aynı Latin yazımı kullanır.
- **Etiket ortak addır, çevrilir** → TR/EN farklı olabilir, anahtar simetrisi korunur.
- Üretilen adlar `diag/bangumi_master/org_latin.tsv` (9.457) ve `tag_latin.tsv` (18) üzerinden
  kalıcıdır; sonraki turda aynı anahtar yeniden romanize edilmez.

### Doğrulama (beşinci tur)

```
sync_org_latin_strings.py --check     → 9.457 anahtar · 0 çözülemeyen · dil dosyalarında CJK 0
fix_cjk_ui_strings.py --check         → arayüz metinlerinde CJK yok
sync_bangumi_tag_strings.py --check   → güncel (1.325 etiket)
sync_taxonomy_tag_strings.py --check  → güncel (114 etiket)
check_localization_resource_parity.py → 17.795 string + 4 dizi
check_taxonomy_master.py              → 114 sözlük / 79 resmî tür / 0 uyarı
check_bangumi_meta_tag_coverage.py    → 158 meta etiket / 0 eksik
audit_taxonomy_sources.py --strict    → 35 alan · 7 kaynak · eksik yok
```

---

## 10) Altıncı tur (v2.4.341) — arama filtresi detay taksonomisinden ayrışmıştı

Detay sayfasında yedi kaynak birleşmişti (§9) ama `GenresTagsSheet.kt` hâlâ
24 tür ve ~170 etiketlik sabit İngilizce liste kullanıyordu; `SearchFilters` kurum
rolü/anahtarı taşımıyordu. Aramada alias eşleşmesi olmadığından `Экшен`
yazıldığında `Action` çipi bulunmuyordu. Çözüm, VAULT + Rusça sözlük + iki
XML'den deterministik üretilen katalog (114/1.325/9.457), tembel çip listesi,
rol bazlı kurum seçici, varsayılan boş `organizations` alanı ve Latin çip oldu.

**Önemli sınır:** Bu tur yalnızca seçim/katalog katmanını çözer; kurumun yedi
kaynakta sunucu/istemci filtrelemesi T55–T60 ve sonraki sürümün işidir.
Uygulama bunu açıkça söyler ve yalnız kurum seçimiyle boş sonuç istemez.
Katalog kaynak eşitliği `gen_taxonomy_catalog.py --check` ve
`check_search_catalog.py --check` ile CI'da denetlenir.
