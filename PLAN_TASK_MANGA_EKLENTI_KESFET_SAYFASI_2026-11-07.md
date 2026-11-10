# PLAN & TASK — Manga Eklentileri İçin "Eklenti Keşfet" Sayfası

- **Sürüm:** 2.4.463 (kaynak: 2.4.341)
- **Tarih:** 2026-11-07
- **Dal:** `feat/golge-bahcesi-integration` (kaynak: `arena/a04658b8-kitsugi-beta`)
- **Durum:** ✅ Kod tarafı tamam · ✅ FOSS APK derleme ve DEX doğrulaması tamam

---

## 1. Amaç (kullanıcı isteği)

Video eklentilerinde bir eklentiye tıklanınca açılan **"Eklenti Keşfet"** sayfasının **birebir manga karşılığı**:
kaynağın kendi kataloğunu (popüler / son güncellenen / türler) listeler, kaynak içinde arama yapar,
"Tümünü Gör" ile tam ekran ızgaraya geçer ve doğrudan manga detayına → bölüm listesine götürür.

---

## 2. Video tarafındaki referans desen (parite kaynağı)

| Katman | Video | Manga (uygulanan) |
|---|---|---|
| Giriş noktası | `CloudstreamExtensionTab` → `CsInstalledPluginRow.onExplorePlugin` | `MangaExtensionTab.onExploreSource` (satır tıklaması + ikon + ipucu) |
| Diyalog zinciri | `KitsugiAddonsSettingsDialog` | `onExploreMangaSource` aynı diyalogda |
| Navigasyon | `DetailScreen.AddonExplore(name)` | `DetailScreen.MangaSourceExplore(sourceKey)` |
| Sayfa | `AddonExplorePage` | `MangaSourceExploreScreen` |
| Önbellek | `AddonExploreCache` (12) | `MangaExploreCache` (8) |
| Liste | yatay raflar + `AddonFullScreenGridState` | `MangaExploreRailSection` + `openGrid/closeGrid` |
| Detay | `KitsugiAddonDetailDialog` | `MangaDetailScreen` → bölüm listesi |
| Kimlik | plugin adı | `stableSourceKey()` |

---

## 3. Görev listesi

### 3.1 Veri katmanı (kaynak yetenekleri)
- [x] `MangaSource.supportsLatest: Boolean` (varsayılan `false`)
- [x] `MangaSource.fetchLatestManga(page): MangaSourceResult` (varsayılan → popüler)
- [x] `MihonSourceWrapper`: `Source.supportsLatest` + `getLatestUpdates(page)`, hata → popüler yedeği
- [x] `KotatsuExtensionAdapter`: `SortOrder.UPDATED in parser.availableSortOrders` + `getList(order = UPDATED)`, `LATEST_OFFSET_KEY`
- [x] Sonuç kontratı sabit: `MangaSourceResult(mangas, hasNextPage)`

### 3.2 Keşfet ekranı
- [x] `MangaSourceExploreScreen(source, repository, onMangaClick, onBack)` — yeni dosya
- [x] `MangaSourceExploreState`: `start`, `refresh`, `loadPopular`, `loadLatest`, `search`, `loadMoreSearch`, `openGrid`, `closeGrid`, `loadGridFirstPage`, `loadMoreGrid`
- [x] Popüler + Son Güncellemeler rafları (kaynak `supportsLatest` değilse ikinci raf gizlenir)
- [x] Kaynak içi arama (260 ms debounce, `searchToken` ile yarış koruması) + sonsuz kaydırma
- [x] "Tümünü Gör" → tam ekran ızgara (sayfalama + hata bloğu)
- [x] Tür çipleri (raf verisinden türetilir, seçili çip aramayı tetikler)
- [x] `MangaExplorePosterCard` → `onMangaClick` → manga detayı → bölüm listesi
- [x] Shimmer iskeletler, boş/hata durumları, `KTS-SRC-*` hata kodu bloğu, yeniden dene
- [x] Yetişkin içerik filtresi (`LocalShowAdultContent`)
- [x] Parametre adı gölgeleşmeyi önlemek için `mangas` (LazyGrid/LazyList `items` ile çakışmaz)

### 3.3 Navigasyon
- [x] `DetailScreen.MangaSourceExplore(sourceKey)` + `AppStateKey.MangaSourceExplore`
- [x] `AppRootMangaPages`: kaynağı çöz, yoksa otomatik geri dön
- [x] `SaveableStateProvider` anahtarı `manga_source_explore_${depth}_${sourceKey}`
- [x] `ui/app/AppNavigationState.kt` kaydı

### 3.4 Giriş noktaları
- [x] `MangaExtensionTab` satırı tıklanabilir + `TravelExplore` ikonu
- [x] `MangaExtensionTab` ipucu satırı (`manga_source_explore_hint`)
- [x] `KitsugiAddonsSettingsDialog.onExploreMangaSource`
- [x] `SettingsScreenParameters` / `SettingsScreen` / `AppRootSettingsExtras` zinciri
- [x] `MangaBrowseScreen` eklenti diyaloğundan keşfet (`showSettingsDialog = false` önce — diyalog katmanı keşfet sayfasını örtmesin)

### 3.5 Sağlamlaştırma (rötuş turu)
- [x] `MangaExploreSnapshot` genişletildi: `gridRailId`, `gridItems`, `gridPage`, `gridHasNext`, `searchQuery`, `searchResults`, `searchPage`, `searchHasNext`
- [x] `persistSnapshot()` 12 çağrı noktasına yayıldı (raflar, grid, arama, hata, temizleme, openGrid/closeGrid)
- [x] Sıralı raf yükleme: `loadPopular`/`loadLatest` → `Job?`, ikinci raf `job?.join()` sonrası (HTTP 429 koruması)
- [x] `popularLoading`/`latestLoading` bayrakları `launch` öncesinde set (çift istek yok)
- [x] `search()` başında grid açıksa `closeGrid()`
- [x] `start()`: grid kapalıyken yarıda kalmış aramayı yeniden çalıştır
- [x] `supportsLatest` `get()` değil, init'te bir kez okunan `val`
- [x] Geri navigasyonu tek yerde: `goBack` (grid → arama → `onBack`), `BackHandler` + üst bar oku
- [x] Yenile ikonunun erişilebilirlik metni `yenile`

### 3.6 Metin / sürüm / doküman
- [x] TR + EN 12 yeni string anahtarı (`manga_source_explore*`, `manga_explore_*`)
- [x] EN apostrof kaçışı (`extension\'s`) — aapt kuralı
- [x] `release_notes_v2.4.341.md`
- [x] `RAPOR_MANGA_EKLENTI_KESFET_SAYFASI_2026-11-07.md`
