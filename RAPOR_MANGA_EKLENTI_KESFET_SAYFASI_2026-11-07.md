# RAPOR — Manga Eklentileri İçin "Eklenti Keşfet" Sayfası

Tarih: 2026-11-07
Sürüm: v2.4.463 (kaynak: v2.4.341)
Durum: Kod tamamlandı, FOSS release APK derlendi ve DEX doğrulaması geçti.

---

## 1) İstek

> "Eklenti portalındaki **video** eklentilerinin üstüne tıklayınca açılan *Eklenti Keşfet*
> sayfası, manga eklentileri için de — manga eklentilerine uygun şekilde — olsun.
> Manga eklentilerinin de kendine ait **keşfet**, **arama**, **Tümünü Gör** kısımları
> olsun; içinden **mangaların bölümleri görünen sayfalara** gidilebilsin."

## 2) Mevcut durum (öncesi)

| Katman | Video eklentileri | Manga eklentileri |
|---|---|---|
| Liste | `CloudstreamExtensionsTab` → `CsInstalledPluginRow` | `MangaExtensionTab` (yalnızca tanı/şablon/sil ikonları) |
| Keşfet | Satırdaki "Keşfet" → `DetailScreen.AddonExplore(apiName)` → `AddonExplorePage` | **Yok** |
| Arama | Eklenti içi arama (`searchWithCfRetry`) | Yalnızca tüm kaynaklarda arama (`MangaBrowseScreen`) |
| Tümünü Gör | `AddonFullScreenGridPage` | **Yok** |
| İçerik sayfası | `KitsugiAddonDetailDialog` | `MangaDetailScreen` (bölüm listesi) var ama keşfet akışı yok |

Manga satırında yalnızca **Dünya (doğrula)**, **Tune (yönet)**, **Çöp (sil)** vardı;
satırın kendisi tıklanabilir değildi.

## 3) Yapılan işler

### 3.1 Veri katmanı — gerçek "son güncellemeler" yeteneği

`data/manga/MangaSource.kt` kontratına **varsayılan uygulamalı** iki üye eklendi
(böylece mevcut üçüncü parti eklentiler değişmeden derlenir):

```kotlin
val supportsLatest: Boolean get() = false
suspend fun fetchLatestManga(page: Int): MangaSourceResult = fetchPopularManga(page)
```

- `MihonSourceWrapper`: `Source.supportsLatest` bayrağı + `getLatestUpdates(page)`.
  Latest ucu hata verirse popüler listeye düşer (keşfet boş kalmasın).
- `KotatsuMangaSource`: `SortOrder.UPDATED in parser.availableSortOrders` +
  `parser.getList(order = UPDATED)`.
  **Sayfalama çakışması önlendi:** Kotatsu adaptörü ofsetleri `(sorgu, sayfa)` ile
  önbelleklediği için latest rafı `LATEST_OFFSET_KEY` (`"\u0000latest"`) adlı ayrı
  sanal anahtar kullanır; popüler rafın `""` anahtarını kirletmez.

**Uydurma veri yok:** raf yalnızca `supportsLatest == true` iken çizilir.

### 3.2 Yeni ekran — `ui/screens/manga/MangaSourceExploreScreen.kt`

Video tarafındaki `AddonExplorePage` sözleşmesinin manga karşılığı:

- **Üst bar:** geri, kaynak adı, `Keşfet · <DİL>` alt başlığı, yenile.
- **Kaynak içi arama:** debounce (260 ms), iptal edilebilir iş, sayfalama,
  sonuç yoksa "Sonuç bulunamadı", hata varsa kod çipi + "Yeniden Dene".
- **Raflar:** "Son Güncellemeler" (koşullu) ve "Popüler Mangalar" — yatay poster
  şeritleri, iskelet (shimmer) yükleme durumu, boş raf metni.
- **Tümünü Gör:** raf başlığının sağında; sayfa içinde tam ekran grid açar
  (ayrı `LazyVerticalGrid`, geri tuşu rafa döner, sona yaklaşınca sonraki sayfa).
- **Türler:** raflardaki `MangaDetails.genre` alanından en sık geçen 12 tür çipi;
  dokununca kaynak içi arama o tür adıyla çalışır.
- **Manga → bölümler:** karta dokunmak `MangaDetailScreen`'i açar (kaynak detayı +
  bölüm listesi + okuyucuya geçiş); geri tuşu keşfet sayfasına döner.
- **+18 sözleşmesi:** `LocalShowAdultContent` kapalıyken yetişkin başlıklar elenir,
  görseller `KitsugiNsfwImage` üzerinden bulanıklaştırılır.
- **Bellek:** `BoundedCache` tabanlı LRU (8 kaynak) anlık görüntü önbelleği —
  manga detayına girip dönünce ağa tekrar çıkılmaz; `MemoryGuard` ile budanabilir.
  Durum tutucu `remember(sourceKey)` ile yaşar (ViewModel biriktirmez).
- **Durum koruma:** anlık görüntü yalnızca rafları değil, açık **"Tümünü Gör" grid'ini**
  (sayfa/sonraki-sayfa dahil) ve **arama sonuçlarını** da saklar. Mangaya girip geri
  dönüldüğünde kullanıcı tam olarak bıraktığı yerden devam eder.
- **Hız sınırı (429) koruması:** "Popüler" ve "Son Güncellemeler" rafları **sırayla**
  yüklenir (ikinci istek, ilki bitince başlar); bazı kaynaklar eşzamanlı iki liste
  isteğini rate-limit ile reddediyordu.
- **Senkron yükleme bayrakları:** bayrak `launch` öncesinde işaretlenir; çift dokunuş
  veya hızlı yenileme aynı rafı iki kez çekmez.
- **Arama önceliği:** grid açıkken arama alanına yazılmaya başlanırsa grid kapanır ve
  arama sonuçları gösterilir (gizli kalan bir arama olmaz).

### 3.3 Navigasyon

- `DetailScreen.MangaSourceExplore(sourceKey)` + `AppStateKey.MangaSourceExplore(sourceKey, depth)`
  (`stableSourceKey()` çözümlemesi; kaynak nesnesi AppStateKey'de taşınamıyor).
- `AppRoot.currentAppStateKey`: `activeScreen is DetailScreen.MangaSourceExplore` eşlemesi.
- `AppRootMangaPages`: ekran render + kaynak artık kurulu değilse güvenli geri dönüş.
- `AppNavigationState.clearPreviousScreens`: yeni anahtarın `SaveableStateProvider`
  kaydı (`manga_source_explore_<depth>_<sourceKey>`) temizleniyor — sızıntı yok.

### 3.4 Giriş noktaları

1. **Eklenti Portalı → Manga sekmesi → kurulu kaynak satırı**
   - Satırın tamamı tıklanabilir (`tvClickable`), ayrıca **Keşfet** (`TravelExplore`) ikonu.
   - `MangaExtensionTab(onExploreSource = …)` → `KitsugiAddonsSettingsDialog(onExploreMangaSource = …)`
     → `SettingsScreen` / `MangaBrowseScreen` → `navState.navigateToDetail(...)`.
2. **Manga tarama ekranındaki eklentiler diyaloğu** aynı hedefe bağlandı
   (tarama sayfası önce kapatılır, çünkü açıkken detay ekranını gölgeler).

### 3.5 Metinler

`values/strings.xml` ve `values-en/strings.xml`:
`manga_source_explore`, `manga_explore_header_subtitle`, `manga_explore_search_hint`,
`manga_explore_clear_search`, `manga_explore_search_results`, `manga_explore_popular`,
`manga_explore_latest`, `manga_explore_genres_title`, `manga_explore_count`,
`manga_explore_rail_empty`, `manga_explore_footer_title`, `manga_source_explore_hint`.
