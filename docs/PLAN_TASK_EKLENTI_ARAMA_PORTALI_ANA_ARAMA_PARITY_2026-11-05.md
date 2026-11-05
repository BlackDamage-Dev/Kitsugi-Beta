# PLAN_TASK — Eklenti Arama Sayfası ↔ Ana Arama Sayfası Birebir Parite (2026-11-05)

## Amaç
Eklenti Portalı (Video/Manga eklenti araması) içindeki eklentiler arası arama görünümünün,
ana arama sayfasındaki (AniList / MyAnimeList / TMDB bölümleri) görünüm ve mantıkla
**tıpatıp aynı** olması; arama geçmişinin eklentiye özel tutulması; eklenti portalı ile
eklentiler arası aramanın ayrı sayfa/katman olarak kalması.

## Yapılan Değişiklikler

### 1. Ortak sonuç bölümü bileşeni (yeni dosya)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonPortalSearchResults.kt`
  - `SearchResponse.toJikanResult(api)` — CS3 arama sonucunu `JikanSearchResult` (source="cs3") olarak dönüştürür.
  - `AddonSearchResultSection` — ana aramadaki `MultiSearchSection` düzeninin aynısı:
    eklenti rozeti + ad + "N sonuç" + sağda "Tümünü Gör", altında yatay kayan
    `KitsugiExploreMediaCard` poster kartları (180dp portrait / 260dp landscape).

### 2. Eklentiler arası arama — Eklenti Portalı tam ekran sayfası
- `ui/screens/search/PluginPickerScreen.kt`
  - Arama kutusu artık aktif tüm eklentilerde paralel arama yapıyor (`CsStreamRunner.searchWithCfRetry`).
  - Sonuçlar eklenti bazında `AddonSearchResultSection` ile gruplanarak gösteriliyor.
  - "Tümünü Gör" → `DetailScreen.AddonExplore(apiName, initialQuery)` — eklentiye özel ayrı sayfada
    aynı sorgunun tüm sonuçları açılır (ana aramadaki SourceSearchPage karşılığı).
  - Kart tıklaması → `KitsugiAddonDetailDialog`.
  - Arama butonu + `ImeAction.Search` ile tetiklenir; geri tuşu aramayı kapatır.
  - Arama kutusu boşken **eklentiye özel arama geçmişi** (`SearchHistorySection`) görünür.

### 3. Eklentiye özel arama geçmişi (ana geçmişten izole)
- `data/local/SearchHistoryDao.kt`
  - Ana sorgu artık `type NOT LIKE 'EXTENSION%'` filtresi içeriyor.
  - Yeni: `getRecentExtensionSearchHistory`, `deleteExtensionSearchQuery`, `clearExtensionSearchHistory`.
- `data/repository/SearchHistoryRepository.kt`
  - `EXTENSION_HISTORY_TYPE = "EXTENSION:ALL"` + `getRecentExtensionSearchHistory` /
    `insertExtensionSearchQuery` / `deleteExtensionSearchQuery` / `clearExtensionSearchHistory`.

### 4. Tutarlılık — diğer eklenti arama yüzeyleri
- `ui/screens/search/components/PluginPickerBottomSheet.kt` — arama modu aynı `AddonSearchResultSection` düzenine geçti.
- `ui/screens/search/components/AddonSearchDialog.kt` — gruplu sonuçlar aynı bölüme geçti.

### 5. "Tümünü Gör" sorgu aktarımı
- `AppNavigation.kt` — `DetailScreen.AddonExplore(apiName, initialQuery = "")` ve `AppStateKey.AddonExplore` aynı parametreyi aldı.
- `AppRoot.kt` — `initialQuery` `AddonExplorePage`'ye iletiliyor; state key sorguyu da içeriyor.
- `ui/screens/search/components/AddonExploreDialog.kt` — `AddonExplorePage(initialQuery)`; sorgu doluysa sayfa açılır açılmaz otomatik arama yapar.

## Değişen Dosyalar
1. `app/src/main/java/com/kitsugi/animelist/AppNavigation.kt`
2. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/local/SearchHistoryDao.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/repository/SearchHistoryRepository.kt`
5. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt`
6. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonPortalSearchResults.kt` (yeni)
7. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonSearchDialog.kt`
8. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/PluginPickerBottomSheet.kt`
9. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/AddonExploreDialog.kt`
10. `PLAN_TASK_EKLENTI_ARAMA_PORTALI_ANA_ARAMA_PARITY_2026-11-05.md` (bu dosya)

## Not
Sandbox'ta Java kurulu olmadığı için gradle derlemesi çalıştırılamadı; tüm dosyalarda
sözdizimi dengesi (braces/parens) otomatik kontrol edildi.
