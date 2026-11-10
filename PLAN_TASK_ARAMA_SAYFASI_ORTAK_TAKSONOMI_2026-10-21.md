# PLAN / TASK — Arama Sayfasının Ortak Taksonomiye Uyarlanması

**Sürüm hedefi:** v2.4.341 (ilk teslim) → v2.4.342 (kaynak filtreleri) → v2.4.343 (kart + arama terimi)
**Tarih:** 2026-10-21
**Dal:** `arena/a610f459-kitsugi-beta`
**Başlangıç commit'i:** `ad91c69` (v2.4.340 — kurum/etiket adlarının tamamı Latin)
**Durum (2026-10-21, v2.4.341):** T48–T54 ve T65/T67 uygulandı; T55–T64 ve T66 bekliyor. Bu oturumun sabit dalı `arena/dac8a4b1-kitsugi-beta` (eski `arena/a610f459-kitsugi-beta` geçmişi buraya birleştirildi).

---

## 1) Neden bu plan var?

v2.4.328 → v2.4.340 arasında **detay sayfası** tarafı bitti: 7 kaynak (MAL/Jikan, AniList,
Bangumi, Shikimori, Kitsu, TMDB, Simkl) aynı havuza tür/etiket/kurum katkısı yapıyor, adlar
TR/EN dil dosyalarıyla uyumlu ve ekranda CJK yok.

**Arama sayfası ise hâlâ eski, bağımsız listelerle çalışıyor.** Yani detayda 114 tür + 600 etiket
görünürken, arama filtresinde 24 tür + ~170 etiket var ve bunlar ortak taksonomiyle
ilişkilendirilmemiş. Bu plan o kopukluğu kapatıyor.

## 2) Mevcut durum (koda dayalı tespit)

| Konu | Bugün | Kanıt |
|---|---|---|
| Tür listesi | **Kodda sabit 24 tür** (`Action`…`Kids`) | `GenresTagsSheet.kt:48-56` |
| Etiket listesi | **Kodda sabit ~170 etiket** | `GenresTagsSheet.kt:58-100` |
| Taksonomi sözlükleri | 114 kanonik tür (TR/EN + Rusça alias), 1.325 Bangumi etiketi, 9.457 kurum | `RussianTaxonomyDictionary.kt`, `BangumiTagDictionary.kt`, `BangumiOrgDictionary.kt` |
| `SearchFilters` alanları | `format, status, genres, excludedGenres, tags, minYear, maxYear, season, minScore, maxScore, sort` | `SearchUiState.kt:255-266` |
| Kurum filtresi | **Yok** (stüdyo/yapımcı/yayıncı/kanal ile arama yapılamıyor) | `SearchFilters` |
| Filtrelerin gittiği kaynaklar | Esas olarak AniList (`genre_in` / `tag_in`) + Jikan (tür ID) | `AniListSearchClient.kt:639-720`, `JikanSearchClient.kt` |
| Arama terimi çevirisi | `SearchTranslation` var; tür/etiket için TR↔EN eşleme mevcut, **kurum adı eşlemesi yok** | `SearchTranslation.kt` |
| Sonuç kartı | Kaynak rozeti var; **stüdyo/tür satırı yok** | `SearchResultRow.kt` |

## 3) Değişmez kurallar (detay sayfasıyla aynı)

1. **Özel ad çevrilmez.** Stüdyo/yapımcı/yayıncı/kanal/dergi ve kişi adları TR'de de EN'de de
   **aynı global Latin ad** ile gösterilir (`Kyoto Animation`, `MAPPA`).
2. **Ortak ad çevrilir.** Tür/etiket/rol adları arayüz diline göre (`Aksiyon` / `Action`).
3. **Ekranda CJK yok.** Ad çözülemezse romanize edilir; ham kaynak metni gösterilmez.
4. **Tek doğruluk kaynağı.** Arama filtresi listeleri kodda elle yazılmaz; taksonomi
   sözlüklerinden üretilir (üretici betik + `--check`).
5. **Kaynak yeteneği şeffaf.** Bir kaynak bir filtreyi desteklemiyorsa çip "bu kaynakta yok"
   rozetiyle pasifleşir; sessizce yok sayılmaz.

## 4) İş kalemleri

### A. Katalog katmanı (v2.4.341)

| # | İş | Dosya | Kabul |
|---|---|---|---|
| T48 | `TaxonomyCatalog` (yeni): tür + etiket + kurum listelerini sözlüklerden tek yerde toplar; TR/EN etiket + Latin ad + alias üretir | `utils/TaxonomyCatalog.kt` (yeni) | `catalog.genres.size == 114`, `catalog.tags.size >= 600`, kurumlar rol bazlı |
| T49 | Katalog üreticisi: Kotlin sözlüklerinden `res/raw/taxonomy_catalog.json` üretir (uygulama açılışında tek sefer okunur, sözlük kodu şişmez) | `scripts/gen_taxonomy_catalog.py` (yeni) | `--check` modu CI'da fark bırakmaz |
| T50 | `GenresTagsSheet`: sabit listeler kaldırılır, katalogdan beslenir; arama kutusu TR + EN + alias eşleşmesi yapar | `GenresTagsSheet.kt` | `"aksiyon"`, `"action"` ve `"экшен"` aynı çipi bulur |
| T51 | Çip sayacı: seçili tür/etiket sayısı başlıkta; "tümünü temizle" | `GenresTagsSheet.kt` | — |

### B. Kurum filtresi (v2.4.341)

| # | İş | Dosya | Kabul |
|---|---|---|---|
| T52 | `SearchFilters`'a `organizations: List<OrgFilter>` (`role` + `canonicalKey`) eklenir; eski filtreler bozulmaz (varsayılan boş) | `SearchUiState.kt` | `isDefault()` yeni alanları da kapsar |
| T53 | Kurum seçici sayfası: 9.457 ad içinde TR/EN/alias arama, rol çipleri (Stüdyo / Yapımcı / Yayıncı / Kanal / Dergi), son seçimler | `OrgFilterSheet.kt` (yeni) | 3 tuşla MAPPA bulunur; seçim çip olarak filtre barına düşer |
| T54 | Seçilen kurum filtre barında Latin adla görünür; TR/EN'de aynı yazım | `SearchFilterBar.kt` | TR ≠ EN yok |

### C. Kaynak eşlemesi (v2.4.342)

| # | İş | Dosya | Kabul |
|---|---|---|---|
| T55 | Kaynak × filtre yetenek matrisi kodda tek yerde (`SearchFilterCapabilities`) | `data/remote/SearchFilterCapabilities.kt` (yeni) | Denetim betiği matrisi kontrol eder |
| T56 | AniList: `genre_in`, `tag_in`, `tag_not_in` (mevcut) + **kurum filtresi istemcide** (sonuç `studios` alanından) | `AniListSearchClient.kt` | Stüdyo filtresi AniList sonuçlarını süzer |
| T57 | Jikan: `genres`, `producers`, `magazines` parametreleri | `JikanSearchClient.kt` | Stüdyo/yayınevi filtresi sunucu tarafında |
| T58 | Shikimori: `genre`, `studio` parametreleri; Kitsu: `filter[categories]` | `KitsugiShikimoriClient.kt`, `KitsuExploreClient.kt` | Desteklenmeyen kombinasyonda 400 → filtresiz isteğe düşer |
| T59 | Bangumi: `filter[tag]` / `filter[meta_tags]`; TMDB: `with_genres`, `with_companies`, `with_networks`, `with_keywords` | `BangumiApiClient.kt`, TMDB istemcisi | Tür + kurum filtresi sunucu tarafında |
| T60 | Simkl: yalnız başlık araması → tür/kurum filtresi **istemcide** uygulanır; çipte "istemcide" rozeti | `KitsugiSimklDetailClient`/arama istemcisi | Sonuç kümesi filtreye uyar, rozet görünür |

### D. Arama terimi ve kart (v2.4.343)

| # | İş | Dosya | Kabul |
|---|---|---|---|
| T61 | `SearchTranslation`: sorgu TR ise EN, EN ise TR karşılığıyla da aranır (tür/etiket/kurum alias'ları dahil) | `SearchTranslation.kt` | `"korsan"` ve `"pirates"` aynı sonuçları getirir |
| T62 | Kurum adı araması: `BangumiOrgDictionary` alias'ları (CJK dâhil) → kanonik Latin ad → kaynak sorgusu | `SearchTranslation.kt` | `"京阿尼"` yazınca `Kyoto Animation` sonuçları gelir, ekranda CJK yok |
| T63 | Sonuç kartında stüdyo + ilk 2 tür + kaynak rozeti (tek satır, taşma yok) | `SearchResultRow.kt` | Kart yüksekliği değişmez, CJK yok |
| T64 | Sonuç sıralaması: filtre eşleşme sayısına göre hafif ağırlık (isteğe bağlı, varsayılan kapalı) | `SearchRelevance.kt` | A/B bayrağıyla açılır |

### E. Doğrulama ve doküman (her tur sonunda)

| # | İş | Dosya |
|---|---|---|
| T65 | `scripts/check_search_catalog.py --check`: katalog ile sözlükler arasında fark yok, sabit liste kalmadı | yeni betik |
| T66 | `SearchFilterCapabilities` denetimi: her kaynak için matriste satır var, kodda karşılığı var | `scripts/audit_taxonomy_sources.py` genişletme |
| T67 | Kapsam dokümanına "Arama" bölümü + logcat tarifi | `diag/taxonomy_master/SOURCE_COVERAGE.md` §8 |

## 5) Kaynak × filtre yetenek matrisi (hedef)

| Kaynak | Tür | Etiket | Stüdyo | Yapımcı | Yayıncı | Kanal | Dergi | Not |
|---|---|---|---|---|---|---|---|---|
| AniList | sunucu | sunucu | istemci | — | — | — | — | `tag_in` spoiler etiketlerini dışlar |
| MAL/Jikan | sunucu | — | sunucu (`producers`) | sunucu | sunucu | — | sunucu (`magazines`, doğrulanacak) | v4 parametre adları sınanacak |
| Shikimori | sunucu | — | sunucu (`studio`) | — | — | — | — | yayıncı parametresi doğrulanacak |
| Kitsu | sunucu (`filter[categories]`) | — | istemci | istemci | istemci | istemci | istemci | ilişki alanı `include` ile gelmeli |
| Bangumi | sunucu (`meta_tags`) | sunucu (`tag`) | istemci | istemci | istemci | istemci | istemci | `filter[tag]` çoklu etiket destekler |
| TMDB | sunucu (`with_genres`) | sunucu (`with_keywords`) | sunucu (`with_companies`) | sunucu | — | sunucu (`with_networks`) | — | anime keşfinde tarih aralığı şart |
| Simkl | istemci | istemci | — | — | — | istemci | — | API yalnız başlık araması verir |

> "doğrulanacak" işaretli hücreler kodlanmadan önce API yanıtıyla sınanır; desteklenmiyorsa
> istemci tarafına düşer ve matriste güncellenir.

## 6) Kabul ölçütleri (uçtan uca)

| # | Senaryo | Beklenen |
|---|---|---|
| A1 | Tür/etiket sayfayı aç | 114 tür + ≥600 etiket, TR ve EN etiketlerle; kodda sabit liste yok |
| A2 | `"aksiyon"` / `"action"` / `"экшен"` yaz | Aynı çip filtrelenir |
| A3 | Stüdyo = MAPPA filtresi | 7 kaynağın desteklediği yerde sunucu, kalanında istemci filtresi; çipte rozet |
| A4 | TR arayüzde kurum çipi | Latin ad (`MAPPA`), TR≠EN farkı yok |
| A5 | Sonuç kartı | Stüdyo + 2 tür + kaynak rozeti; tek satır; CJK yok |
| A6 | `"京阿尼"` araması | `Kyoto Animation` sonuçları; ekranda CJK yok |
| A7 | Filtre desteklemeyen kaynak | Sonuçlar yine gelir, çip pasif + "bu kaynakta yok" |
| A8 | `check_search_catalog.py --check` + `audit_taxonomy_sources.py --strict` | İkisi de temiz |

## 7) Riskler ve önlemler

| Risk | Önlem |
|---|---|
| Katalog JSON'u APK'yı şişirir | Yalnız anahtar + TR/EN + Latin ad; tahmini < 400 KB, `res/raw` sıkıştırılır |
| Kitsu/Shikimori 400 döner | Her istekte filtresiz yedek (mevcut `mangaProductions` deseniyle aynı) |
| İstemci filtresi sonuç sayısını düşürür | Sayfalama `limit` artırılır, boş sonuçta "filtreyi gevşet" önerisi |
| Eski filtre tercihleri bozulur | `SearchFilters` yeni alanlarda varsayılan boş; kayıtlı arama geçmişi migrasyonsuz çalışır |
| TMDB anime keşfi tarih aralığı olmadan boş döner | `first_air_date` aralığı filtreyle birlikte gönderilir |

## 8) Doğrulama komutları (her tur sonunda)

```bash
python3 scripts/gen_taxonomy_catalog.py --check      # katalog güncel
python3 scripts/check_search_catalog.py --check      # kodda sabit liste yok
python3 scripts/audit_taxonomy_sources.py --strict   # 35 alan · 7 kaynak
python3 scripts/check_localization_resource_parity.py
python3 scripts/check_taxonomy_master.py
python3 scripts/sync_taxonomy_tag_strings.py --check
/tmp/venv/bin/python scripts/sync_org_latin_strings.py --check
```

Cihazda:

```bash
adb logcat -s KitsugiSearch KitsugiTaxonomySources KitsugiDetailClient
# Beklenen: "Arama filtreleri: tür=3 etiket=2 kurum=1 | kaynak yetenekleri: anilist=server,kitsu=client"
```

## 9) Sıra ve teslim

1. **T48–T54** → v2.4.341 (katalog + tür/etiket + kurum filtresi, UI hazır)
2. **T55–T60** → v2.4.342 (kaynak eşlemesi, yetenek matrisi, rozetler)
3. **T61–T64** → v2.4.343 (arama terimi, kart, sıralama)
4. **T65–T67** her turda; sürüm notları + plan güncellemesi + zip + Drive yüklemesi

## 10) Yeni branch'e geçerken devir notu

- Dal: `arena/a610f459-kitsugi-beta`, son commit `ad91c69` (v2.4.340). Çalışma ağacı temiz.
- Detay sayfası tarafı **bitti**; arama tarafına dokunulmadı — bu plan sıfırdan başlıyor.
- İlk komut: `git log --oneline -3` + `python3 scripts/audit_taxonomy_sources.py --strict`
  (temiz çıkmalı), sonra **T48**.
- Kural hatırlatması: kurum adı çevrilmez (TR=EN), tür/etiket çevrilir; ekranda CJK yok.

---

## 11) v2.4.341 uygulama notu — bu oturum

- **T48–T49 ✅:** `TaxonomyCatalog.kt` tek seferlik arka plan okuması +
  `gen_taxonomy_catalog.py --check`. Üretilen katalog: **114 tür, 1.325 etiket,
  9.457 kurum** (5 rol); ham JSON 1.200.090 B, sıkıştırılmış ≈273 KB.
  Adlar kaynaklardan gelir; yalnız arama takma adlarında Kiril/CJK vardır.
- **T50–T51 ✅:** `GenresTagsSheet` sabit 24/~170 listeyi kaldırdı; görünür çipleri
  tembel satırlara böldü; TR/EN/Rusça alias arama, seçili sayaç ve tümünü temizle.
  Eski `SearchFilters` tür/etiket API değerleri İngilizce kalır.
- **T52–T54 ✅ (UI, sağlayıcı bağlantısı sonraki tur):** `SearchFilters` ve
  `SearchUiState.organizations` varsayılan boş; `OrgFilterSheet` rol filtreleri,
  9.457 adda arama, son seçimler ve Latin çipler. Filtrenin henüz sonuçlara
  uygulanmadığı seçicide ve çipte **açıkça yazılıdır**; yalnız kurum seçmek boş
  sorguyla ağ isteği başlatmaz. `SearchFilterBar` seçimi gerçek Arama ekranında
  `SearchOrganizationChips` aracılığıyla gösterir.
- **T65/T67 ✅:** `check_search_catalog.py --check`, katalog/alias/eksik sabit
  liste denetimi, kaynak kapsamı §8, logcat tarifi, CI. **T66 ve T55–T64 ⏳:**
  7 kaynağa filtre uygulanması, kaynak yetenek matrisi, destek rozetleri,
  `京阿尼` başlık sorgusu ve kart stüdyo satırı henüz teslim edilmedi.
- **Birleşim notu:** bu oturumun v2.4.338 tabanında sözlükler Kotlin satırları
  yerine `app/src/main/resources/bangumi/{orgs,tags}.tsv` (VAULT) olarak duruyordu.
  Eski dalın v2.4.340 tamamlanmış 9.457 Latin anahtarını TSV'ye eşitledik;
  Kotlin içinde yeniden dev liste oluşturmadık. `sync_org_latin_strings.py --check`
  artık 9.457 anahtarı **TSV + iki XML** üzerinde gerçekten karşılaştırır.
  Kalan VAULT-only anahtarlar yeni Android string kaynaklarına eklenmedi.
  Eski sözlükteki `bangumi_studio_kyoto_animation` küresel adı **KyoAni**;
  `Kyoto Animation` arama takma adıdır. T62'de tam ad tercihine ayrıca karar verilmeli.
- **Sınır:** bu sandbox'ta JDK/Gradle bulunmadığından APK ve cihaz E2E testi
  yapılamadı; Kotlin sözdizimi tree-sitter, Python denetimleri ve JSON eşitliğiyle
  doğrulandı. Cihazda kaynak API davranışları T55–T60'a kadar doğrulanmış sayılmaz.

Komutlar:

```bash
python3 scripts/gen_taxonomy_catalog.py --check
python3 scripts/check_search_catalog.py --check
python3 scripts/test_gen_taxonomy_catalog.py
python3 scripts/check_localization_resource_parity.py
python3 scripts/check_taxonomy_master.py
python3 scripts/audit_taxonomy_sources.py --strict
python3 scripts/sync_taxonomy_tag_strings.py --check
python3 scripts/sync_bangumi_tag_strings.py --check
python3 scripts/sync_org_latin_strings.py --check
python3 scripts/fix_cjk_ui_strings.py --check
```
