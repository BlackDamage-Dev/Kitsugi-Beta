# Kitsugi Beta v2.4.497 — NuvioMobile UI, Kesintisiz Sayfalama, Rozet Uyumu ve Eklenti Portalı Entegrasyonu

[TR]
### 🌟 NuvioMobile UI & Modern Keşfet Deneyimi (v2.4.497)
- **Buzlu Cam (Frosted Glass) Yüzeyler:** Harici kütüphane bağımlılığı olmaksızın saf Jetpack Compose ile yazılan `NuvioGlassPillSurface` ve `NuvioCircularActionButton` bileşenleri; Açık, Koyu, AMOLED ve Gradyan temalarına tam uyumlu hale getirildi.
- **Yüzen Navigasyon Kapsülü (`NuvioFloatingNavBar`):** Dikey kullanımda ekranın alt ortasında yay animasyonlu yüzen hap; yatay ve tablet kullanımında üst ortada kompakt simge çubuğu (`<840dp` cihazlarda `256dp`, tabletlerde `304dp`). Android TV için D-pad navigasyon çubuğu eksiksiz korundu.
- **Sinematik Hero Carousel:** 8 saniyelik yumuşak otomatik kaydırma, genişleyen sayfa göstergeleri, tema dostu sinematik degrade ve bozuk görsel yedeği ile tam genişlikli keşfet vitrini.
- **Dinamik Kategori Sekmeleri:** `Diziler · Filmler · Anime · Manga` sekmeleri dar ekranlarda yatay kaydırılabilir yapıldı.
- **Yeni 2:3 Katalog Poster Kutucuğu (`NuvioCatalogPosterTile`):** Ekran genişliğine göre 3–7 sütun dinamik yerleşim, sıra rozeti (`#1`), hızlı liste ekleme/düzenleme butonu (`+`/`✓`) ve minimal puan rozeti.

### 🛠️ Medya Detay Sayfaları ve Sekme İyileştirmeleri
- **Resimler Sekmesi Kaldırıldı:** Üst bardaki tam ekran galeri ikonu ve `KitsugiImageGalleryDialog` tek başına yeterli olduğu için sekme karmaşası giderildi.
- **Sekme İndeksleri Birebir Hizalandı:** `DETAIL_VIDEO_PAGE_INDEX = 1` olarak güncellendi; Videolar sekmesi ViewModel'den bağımsız null dönerken, diğer tüm sekmeler (`1..8`: Videolar, Karakterler, Ekip, Öneriler, İlişkiler, Grafikler, Yorumlar, Bölümler) ViewModel indeksleriyle 1:1 eşleştirildi.

### 🔄 "Tümünü Gör" Sayfalama ve Rozet Uyumu
- **Kesintisiz Sürekli Sayfalama:** "Boş sayfa = liste sonu" varsayımı kaldırıldı. Önceki sayfa dolu geldiyse dönen boş yanıt geçici hata kabul edilip `hasMorePages` korunuyor; 2 sessiz otomatik deneme ve gerektiğinde kullanıcıya "Tekrar dene" butonu sunuluyor.
- **TMDB 60s Önbellek & Sınırlar:** Başarısız/boş TMDB yanıtlarının 1 saat kilitlenmesi önlendi (TTL 60 saniyeye düşürüldü). Birleşik sayfalarda kuyruk kaybı giderildi ve `page=500` sınırı eklendi.
- **Kutusuz Rozet Uyumu:** Yeni tek kaynak `KitsugiMediaCardStats.kt` ile liste ve kare görünümdeki puan rozetleri tek tip hale getirildi (16 dp sarı `0xFFFFB800` yıldız + kalın sayı + 4 dp ilerleme çubuğu). Poster üstü siyah şerit kaldırıldı.
- **Toplu Ekleme Kaldırıldı:** Üç adet `PlaylistAdd` butonu ve `onAddAllToList` bağlantıları kaldırılarak arayüz sadeleştirildi.

### 🔌 Eklenti Portalı & Manga Keşfet
- **Doğrudan Keşfet Açılışı:** Eklenti Portalı → Manga sekmesindeki bir kaynak satırına dokunulduğunda artık doğrudan o kaynağın kendi Keşfet sayfası (`DetailScreen.MangaSourceExplore`) açılıyor.
- **Ayrı Arama Kapsamı İkonu:** Satırın sağına eklenen büyüteç ikonu ile arama kapsamı seçimi korunurken, satır gövdesi Keşfet ekranına ayrıldı.
- **Güncel Kaynak Listesi:** Kaynak çözümlemesi ViewModel snapshot'ı yerine doğrudan repository'nin güncel kurulu eklenti listesinden yapılıyor.

### 🛡️ Simkl ve Ağ Kararlılığı
- **Simkl Dropped Eşlemesi:** Simkl API'nin `dropped` durumunu reddetmesi (HTTP 400 `wrong_parameter: to`) engellendi; bırakılanlar `notinteresting` kovasına yönlendirildi.
- **Video İndirme ve Akış RetryInterceptor:** HLS manifest çekilirken yaşanan HTTP 429 (rapidrame → ExoPlayer 2004) ve `vidcache` segment kopmalarına karşı üstel geri çekilmeli yeniden deneme mekanizması eklendi.
- **MPEG4Writer FORTIFY SIGABRT Koruması:** `MuxSampleGuard` ile negatif/geçersiz boyutlu örneklerin yazılması önlendi.

---

[EN]
### 🌟 NuvioMobile UI & Modern Discover Experience (v2.4.497)
- **Frosted Glass Surfaces:** Built with pure Jetpack Compose (`NuvioGlassPillSurface` & `NuvioCircularActionButton`) with zero external library overhead; fully adaptive to Light, Dark, AMOLED, and Gradient themes.
- **Floating Navigation Capsule (`NuvioFloatingNavBar`):** Spring-animated floating pill centered at the bottom in portrait; compact top-center icon bar in landscape and tablet (`256dp` on `<840dp` screens, `304dp` on tablets). Android TV D-pad navigation rail is fully preserved.
- **Cinematic Hero Carousel:** Full-width showcase featuring 8-second smooth auto-scroll, expanding indicator dots, theme-adaptive cinematic scrim, and broken-image fallbacks.
- **Dynamic Category Chips:** `Shows · Movies · Anime · Manga` frosted glass chips with horizontal scrolling for narrow displays.
- **New 2:3 Catalog Poster Tile (`NuvioCatalogPosterTile`):** Responsive 3–7 column grid layout, rank badge (`#1`), quick add/edit button (`+`/`✓`), and minimal score badge.

### 🛠️ Media Detail Pages & Tab Simplification
- **Images Tab Removed:** Redundant tab removed in favor of the top AppBar gallery icon and full-screen `KitsugiImageGalleryDialog`.
- **1:1 Tab Index Mapping:** Updated `DETAIL_VIDEO_PAGE_INDEX = 1`; Videos tab evaluates to null for remote data, while all other pages (`1..8`: Videos, Characters, Staff, Recommendations, Relations, Charts, Reviews, Episodes) map 1:1 to ViewModel indices.

### 🔄 "See All" Continuous Pagination & Badge Alignment
- **Continuous Pagination:** Eliminated the "empty page = end of list" false assumption. If the previous page was full, an empty response is treated as transient (`hasMorePages` preserved) with 2 silent retries and a visible "Retry" button.
- **TMDB 60s Cache & Limits:** Reduced empty/failed response cache from 60 minutes to 60 seconds (preventing 1-hour query lockouts after a single 429). Lossless unified pagination and strict `page=500` ceiling enforced.
- **Unified Rating Badges:** New single-source `KitsugiMediaCardStats.kt` brings consistent star badges across grid and list cards (16dp yellow `0xFFFFB800` star + bold text + 4dp progress bar). Black poster score strip removed.
- **Batch Add Removed:** Removed 3 `PlaylistAdd` buttons and `onAddAllToList` handlers for a cleaner UI.

### 🔌 Plugin Portal & Manga Discover
- **Direct Source Discover:** Tapping any manga source row in Plugin Portal now directly opens that source's own Discover screen (`DetailScreen.MangaSourceExplore`).
- **Dedicated Search Scope Button:** Added an explicit search icon on the right side of the row to toggle search scope without overriding the main row click.
- **Live Repository Resolution:** Resolves sources against the manga repository's live installed list rather than stale ViewModel snapshots.

### 🛡️ Simkl & Network Stability
- **Simkl Dropped Mapping:** Fixed HTTP 400 (`wrong_parameter: to`) by mapping `WatchStatus.Dropped` to `notinteresting`.
- **Video OkHttpClient RetryInterceptor:** Added exponential-backoff retries for HLS manifest HTTP 429 errors (rapidrame → ExoPlayer 2004) and vidcache segment drops.
- **MPEG4Writer FORTIFY SIGABRT:** `MuxSampleGuard` eliminates native crash during remuxing/downloading.
