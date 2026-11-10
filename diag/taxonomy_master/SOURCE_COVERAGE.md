# Kaynak Kapsama Raporu — tür / etiket / kurum

**Sürüm:** v2.4.339 · **Tarih:** 2026-10-21 · **Doğrulayan:** `scripts/audit_taxonomy_sources.py --strict`

Bu rapor, desteklenen **yedi detay kaynağının** her biri için hangi alanların okunduğunu,
çapraz toplayıcının hangi **yedi kaynağı** paralel çağırdığını ve hangi tavanların geçerli
olduğunu gösterir. Denetim kodu tarar: bir alan veya tavan sessizce düşerse CI kırılır.

---

## 1) Kapsama matrisi

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Lisans | Yayıncı | Kanal | Dergi |
|---|---|---|---|---|---|---|---|---|
| **AniList** | `genres` | `tags` (rank + spoiler) | `studios` (isMain dahil, hepsi) | — | — | — | — | — |
| **MAL/Jikan** | `genres`, `explicit_genres` | `themes`, `demographics` | `studios` | `producers` | `licensors` | `publishers` (manga) | — | `serializations` |
| **Bangumi** | 类型 (meta etiketler) | `tags` (400'e kadar) | 动画制作 · アニメーション制作 | 製作 · 製作委員会 · 制作協力 · 音楽制作 · レーベル · 配給 | — | 出版社 (manga) | 播放电视台 · 放送局 | 连载杂志 · 掲載誌 |
| **Shikimori** | `genres` (`kind=genre`) | `kind=theme` / `demographic` | `studios` | — | **`licensors`** | **`publishers`** (manga) | — | — |
| **Kitsu** | `categories` | `categories` | `animeProductions` (`role=studio`) | `role=producer` | `role=licensor` | `role=publisher` | `role=network` | `role=magazine` |
| **TMDB** | `genres` | `keywords` | `production_companies[0]` | `production_companies[1…]` | — | — | `networks` | — |
| **Simkl** | `genres` | `genres` | — *(API vermiyor)* | — *(API vermiyor)* | — | — | **`network`** | — |

**v2.4.339'da kapatılan son boşluklar**

| Kaynak | Eksik olan | Nasıl düzeltildi |
|---|---|---|
| Shikimori | `licensors`, `publishers` hiç okunmuyordu | `parseShikimoriOrgArray()` — hem string hem nesne dizisi |
| Simkl | `network` okunmuyordu, `studios` sabit boştu | `network` + `networks` → Kanal |
| MAL/Jikan | manga `serializations`, `publishers` çapraz toplayıcıda yoktu | `fetchJikan` içine eklendi |
| Kitsu | manga `mangaProductions` istenmiyordu | `include=categories,mangaProductions.producer` + 400 yedeği |

---

## 2) Çapraz toplayıcı — **7 kaynak, tek havuz**

`KitsugiTaxonomySources.collect()` her detay açılışında yedi servisi **paralel** sorgular
(kaynak başına 5,5 sn, toplam 8 sn tavan):

| Kaynak | Getirilen | Kimlik çözümü |
|---|---|---|
| Jikan | genres · explicit_genres · themes · demographics · studios · producers · licensors · serializations · publishers | MAL kimliği |
| AniList | genres · tags (rank, spoiler) · studios (isMain) | AniList/MAL kimliği |
| Shikimori | genres (`kind`) · studios · licensors · publishers | MAL kimliği + `myanimelist_id` doğrulaması |
| Kitsu | categories · productions (rol) | `mappings` → `myanimelist/{anime,manga}` |
| Bangumi | tags + infobox kurumları | **normalize tam başlık** + yıl ±1 |
| TMDB | genres · keywords · production_companies · networks | TMDB kimliği |
| **Simkl** | genres · network | başlık araması + normalize eşleşme (+ yıl ±1) |

Cihaz günlüğünde kaynak bazlı sayaç basılır:

```
KitsugiTaxonomySources: Kaynak bazlı: jikan=5t/12e/4k | anilist=4t/38e/2k |
shikimori=6t/9e/3k | kitsu=6t/4e/3k | bangumi=5t/120e/9k | tmdb=4t/15e/7k | simkl=6t/0e/1k
Taksonomi toplandı: tür=12 etiket=185 stüdyo=4 yapımcı=16
```

`yok` = o kaynakta kimlik/başlık çözülemedi; diğerleri etkilenmez.

**Bangumi sayfaları** da aynı havuzu kullanır (`enrich` → `collect(includeBangumi = false)`),
yani sayfanın hangi kaynaktan açıldığı sonucu değiştirmez.

---

## 3) Tavanlar (v2.4.339)

| Alan | v2.4.337 | v2.4.339 |
|---|---|---|
| Bangumi etiketleri | 40 | **400** |
| Bangumi türleri | 12 | **64** |
| Birleşik tür | 48 | **160** |
| Birleşik etiket | 96 | **600** |
| Birleşik kurum | 64 | **256** |
| Tema / hedef kitle | 24 / 12 | **64 / 32** |
| Etiket kartı (daraltılmış) | 10 | **30** |

Bu değerlerin altına inilmesi `audit_taxonomy_sources.py` tarafından **hata** sayılır.

---

## 4) Kurum bütünlüğü kuralları

1. **Hiçbir kurum rolden dolayı silinmez.** `dropProducersThatAreStudios` kaldırıldı; aynı
   şirket hem stüdyo hem yapımcı ise iki listede de görünür.
2. **Tekilleştirme anahtarı `rol + ad`.** `MAPPA` (STUDIO) ile `MAPPA` (PRODUCER) ayrı kayıttır.
3. **Kimliği olan kayıt tercih edilir** — aynı kurum iki kaynakta geçiyorsa tıklanabilir olan
   (MAL/AniList/Bangumi person-id) korunur.
4. **Kiril/Çince sızıntısı yok**: `toLocalizedOrgName` → sözlük; sözlükte yoksa Latin harflerine
   çevrilir; kaomoji/infobox artığı adlar gösterilmez.
5. **Dergi/yayınevi ayrı bölüm**: "Yayıncı Ağlar" ile karışmaz. Yayınevleri `PUBLISHER`,
   dergiler `MAGAZINE` rolündedir.

---

## 5) Otomatik denetimin kapsadığı üç eksen

1. **Alan kapsaması** — **35 alan** + **7 çapraz kaynak** (Shikimori licensors/publishers,
   Simkl network, MAL manga serializations/publishers dahil).
2. **Tavan alt sınırları** — tür ≥160, etiket ≥600, kurum ≥256, Bangumi etiketi ≥400.
3. **Kurum eksilten çağrı yasağı** — `dropProducersThatAreStudios` geri gelirse CI kırılır.

```
KAYNAK       ALAN                      DURUM
------------------------------------------
AniList     genres                    ✔ okundu
...
TMDB        keywords                  ✔ okundu

Çapraz kaynak toplayıcı (KitsugiTaxonomySources):
  Jikan      ✔
  AniList    ✔
  Shikimori  ✔
  Kitsu      ✔
  Bangumi    ✔
  TMDB       ✔
  Simkl      ✔

Toplam 35 alan denetlendi · 7 kaynak
TÜM KAYNAKLAR BEKLENEN ALANLARI OKUYOR — eksik yok.
```

---

## 6) Cihazda nasıl doğrulanır

1. `adb logcat -s KitsugiTaxonomySources KitsugiDetailClient KitsugiBangumiDetailClient`
2. Aynı yapımı iki farklı kaynaktan açın (MAL ve Bangumi). **Tür/etiket/kurum listeleri aynı
   olmalı** — çünkü ikisi de aynı yedi kaynağı birleştiriyor.
3. `KitsugiDetailClient: Birleşik taksonomi: …` satırı nihai sayıları verir.
4. Etiket sayısı beklediğinizden düşükse ilgili kaynağın `yok` yazıp yazmadığına bakın.

---

## 7) Ad yazımı kapsaması (v2.4.340) — kurum + etiket

Alanların **okunması** kadar, okunan adın **hangi yazımla** ekrana çıktığı da kapsam içinde.
Kaynaklar CJK adları Latin harflere çevirmeden döndürür; dönüşüm uygulama tarafındaki sözlük +
dil dosyası katmanında yapılır.

| Alan | Anahtar | Ekranda CJK | Yöntem |
|---|---|---|---|
| Yapımcı | 2.941 | 0 | resmî ad / romanizasyon |
| Yayınevi | 890 | 0 | resmî ad / romanizasyon |
| Stüdyo | 809 | 0 | resmî ad / romanizasyon |
| Dergi | 452 | 0 | dergi öneki (`月刊`→`Monthly`) + romanizasyon |
| Kanal | 131 | 0 | resmî ad / romanizasyon |
| Etiket (CJK anahtar) | 18 | 0 | elle düzeltilmiş romanizasyon (`tag_latin.tsv`) |
| **Toplam** | **5.241** | **0** | — |

Kurallar:

- **Kurum adı çevrilmez, yazımı sabitlenir.** TR ve EN dosyalarında aynı Latin ad
  (`kurum anahtarlarında TR ≠ EN: 0`).
- **Etiket çevrilir** (ortak ad), anahtar simetrisi korunur.
- Arayüz metinlerindeki kaynak etiket parantezleri (`(在看)`, `(条目)`, `(打格子)`, `"允许"`)
  tamamen kaldırıldı.

Denetim komutları:

```bash
python3 scripts/sync_org_latin_strings.py --check   # 9.457 anahtar, CJK 0 olmalı
python3 scripts/fix_cjk_ui_strings.py --check       # arayüz metni CJK 0 olmalı
python3 scripts/sync_bangumi_tag_strings.py --check
python3 scripts/sync_taxonomy_tag_strings.py --check
```

Kalıcı kaynaklar: `diag/bangumi_master/org_latin.tsv` (9.457 satır),
`diag/bangumi_master/tag_latin.tsv` (18 satır).

---

## 8) Arama kataloğu kapsamı (v2.4.341)

Detay sayfası (§1–7) olduğu gibi kalır. Arama sayfası artık **tek üretilmiş
`res/raw/taxonomy_catalog.json`** dosyasını arka planda bir defa okur:

| Arama alanı | Kaynak | Çip / ekran adedi | Gösterim |
|---|---|---:|---|
| Tür | `RussianTaxonomyDictionary.kt` + TR/EN XML | **114** | TR/EN ortak ad |
| Etiket | VAULT `bangumi/tags.tsv` içindeki kaynaklı gruplar + TR/EN XML | **1.325** | TR/EN ortak ad |
| Kurum | `org_latin.tsv` + VAULT `bangumi/orgs.tsv` + TR/EN XML | **9.457**, 5 rol | **TR=EN Latin özel ad** |

Kaynak yazımlar (Rusça `Экшен`, Çince `京阿尼`) JSON'da yalnızca **gizli arama
alias'larıdır**. Ekran metni daima `tr`/`en` veya kurumun tek Latin `name` alanından
alınır; alias ekrana yazılmaz. `aksiyon`, `action`, `экшен` türde aynı `action`
anahtarını bulur; MAPPA (STUDIO) 3 harfle bulunur. JSON ≈1,2 MB ham, gzip
≈273 KB; Android raw kaynak sıkıştırması ayrıca cihazda doğrulanmalıdır.

**Sağlayıcı durumu:** v2.4.341'de kurum **seçimi ve çipi** hazırdır, fakat sonuç
süzme henüz etkin **değildir** (T55–T60). Arayüz bunu açıkça belirtir; kurum
çipi seçimi tek başına boş sorguyla kaynak isteği başlatmaz. Tür/etiket geçmişteki
AniList/MAL akışına İngilizce etiket olarak aktarılır; tüm 7 kaynakta filtre
paritesi tamamlanmış sayılmaz. T55 yetenek matrisi ve T66 denetiminden sonra
kaynak bazında etkin/pasif/istemci rozeti eklenecek.

`adb logcat -s KitsugiSearch KitsugiTaxonomySources KitsugiDetailClient`:

```
KitsugiSearch: Taksonomi kataloğu: tür=114 etiket=1325 kurum=9457
KitsugiSearch: Arama filtreleri: tür=1 etiket=2 kurum=1 | kurum sağlayıcı eşlemesi henüz etkin değil
```

Derlemesiz denetim: `gen_taxonomy_catalog.py --check`,
`check_search_catalog.py --check`, `check_localization_resource_parity.py`,
`check_taxonomy_master.py`, `audit_taxonomy_sources.py --strict`,
`sync_*_tag_strings.py --check`, `sync_org_latin_strings.py --check`.
VAULT'un harici/ignore edilen kaynak JSON dosyaları olmadan
`diag/bangumi_vault/scripts/verify_vault.py` çalışmaz; bu, katalog
kaynaklarının `--check` doğrulamasını etkilemez.
