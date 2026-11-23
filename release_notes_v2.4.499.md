# Kitsugi Beta v2.4.499 — NuvioMobile Keşfet UI & Tüm Ekranlar Dairesel Koyu-Cam Birleşimi

[TR]
### 🌟 NuvioMobile Keşfet UI & Yüzen Navigasyon (Tam Koruma)
- **Sinematik Hero Carousel & Kenardan Kenara Tasarım:** Keşfet ana ekranında tam kenardan kenara (edge-to-edge) uzanan 8 saniyelik sinematik hero carousel, yumuşak degrade ve saf Compose akrilik buzlu cam katmanı (`NuvioGlassSurface`) eksiksiz olarak korundu ve kilitlendi.
- **Yüzen Navigasyon Kapsülü (`NuvioFloatingNavBar`):** Dikey modda kaydırmaya duyarlı dinamik daralan/genişleyen yüzen kapsül; yatay ve tablet modunda ekranın üst ortasında kompakt simge çubuğu ve D-pad uyumlu Android TV desteği.
- **Dinamik Kategori Sekmeleri & Katalog Posterleri:** `Diziler · Filmler · Anime · Manga` sekmeleri ve 2:3 en-boy oranlı `NuvioCatalogPosterTile` poster kutucukları (hızlı liste ekleme/düzenleme, sıra rozeti, puan göstergesi) tamamen muhafaza edildi.

### 🔮 Tüm Ekranlar Dairesel Koyu-Cam (Circular Dark Glass) Entegrasyonu
- **Ortak Üst Bar ve Dairesel Butonlar (`KitsugiTopActionBar.kt`):** 70'in üzerindeki ikincil ekranda (MyList, Profiller, Ayarlar, İndirilenler, Detaylar, Manga ekranları, İstatistikler) dairesel koyu-cam aksiyon butonları (`KitsugiIconGlassButton`), durum hapları (`KitsugiSourceSelectorPill`) ve kanonik aksiyon sıralaması uygulandı.
- **Birleşik Geometri ve Boşluk Jetonları (`KitsugiBottomBarTokens`):** Alt yüzen çubuk, yatay yan ray (`AppNavigationRail`), yukarı kaydır butonu (`KitsugiScrollToTopFab`) ve filtre hapları arasındaki mesafe ve animasyonlar tek bir tasarım jetonu havuzundan yönetiliyor.
- **Pencere Boşlukları ve Çakışma Koruması:** Durum çubuğu (`statusBarsPadding`) ve navigasyon çubuğu boşlukları ikincil ekranlarda güvenli alan sağlayacak şekilde düzenlendi; Keşfet ekranında ise kenardan kenara görsel akış bozulmaksızın korundu.

### 🛠️ Sayfalama, Rozet ve Eklenti Portalı Entegrasyonları
- **"Tümünü Gör" Sayfalama:** Sonsuz ve kesintisiz liste akışı; 60 saniyelik TMDB önbelleği, geçici hatalarda sayfalama durumunu koruma ve 2 sessiz otomatik deneme.
- **Birleşik Yıldız Puanı Rozetleri:** `KitsugiMediaCardStats.kt` ile tüm kartlarda kutusuz 16 dp sarı yıldız rozeti uyumu.
- **Manga Eklenti Portalı:** Kaynak satırından doğrudan o kaynağın Keşfet sayfasına yönlendirme ve bağımsız arama ikonu.

### 🌐 GitHub Senkronizasyonu & Sürüm
- **Arena AI Senkronizasyonu:** GitHub üzerindeki `origin/main` ve `origin/release` dalları en son commit'lerle tamamen güncellendi.
- **APK Sürüm:** v2.4.499 (Kod: 499) derlendi.

---

[EN]
### 🌟 NuvioMobile Discover UI & Floating Navigation (Fully Preserved)
- **Cinematic Hero Carousel & Edge-to-Edge:** Edge-to-edge cinematic hero carousel with 8s auto-scroll, smooth scrim gradients, and pure Jetpack Compose frosted glass (`NuvioGlassSurface`) fully preserved and locked.
- **Floating Navigation Capsule (`NuvioFloatingNavBar`):** Scroll-responsive floating bottom capsule in portrait; compact top-center icon capsule in landscape/tablet mode, with full Android TV D-pad rail compatibility.
- **Dynamic Category Chips & Catalog Tiles:** `Shows · Movies · Anime · Manga` tabs and 2:3 responsive `NuvioCatalogPosterTile` cards with quick list toggles, rank badges, and minimal score pills.

### 🔮 Complete Circular Dark Glass UI Unification Across 70+ Screens
- **Unified Top Bar & Action Glass Buttons (`KitsugiTopActionBar.kt`):** Circular dark glass buttons (`KitsugiIconGlassButton`), source selector pills (`KitsugiSourceSelectorPill`), and canonical action ordering deployed across MyList, Profiles, Settings, Downloads, Details, and Manga screens.
- **Shared Geometry Tokens (`KitsugiBottomBarTokens`):** Centralized spacing and clearance metrics for floating capsule, landscape rail, scroll-to-top FABs, and bottom filters.
- **WindowInsets & Collision Protection:** Double status bar padding eliminated in secondary screens while preserving true edge-to-edge behind top bar on Discover.

### 🛠️ Continuous Pagination, Badges & Manga Portal
- **"See All" Continuous Pagination:** Resilient infinite pagination with 60s TMDB cache and silent retries.
- **Unified Star Score Badges:** Clean 16dp yellow star badge standard across grid and list views (`KitsugiMediaCardStats.kt`).
- **Manga Plugin Portal:** Direct one-click navigation to provider-specific Discover pages with dedicated search icons.

### 🌐 GitHub Synchronization & Build
- **Arena AI Sync:** GitHub `origin/main` and `origin/release` are fully synced with the latest codebase.
- **APK Version:** v2.4.499 (Version code: 499) built.
