# Kitsugi Beta v2.4.349

## 🇹🇷 Türkçe

### 🎬 Oynatıcı Siyah Ekran & Sesli Takılma Kurtarma Motoru (Audio-Only Playback Detector)
- **Sessiz Video Çözücü Çökmesi Kurtarma:** Ses çalarken görüntünün gelmediği ("audio-only") siyah ekran durumlarını tespit eden `AudioOnlyPlaybackDetector` eklendi. Zaman çizelgesi ilerlemesi ve 6 saniyelik tolerans penceresi ile video karesi üretilmediği anlaşıldığında otomatik olarak alternatif motora veya kaynağa geçilir.
- **Kesintisiz Tam Ekran Bekçisi:** Bekçi yalnızca açılışta değil, oynatma boyunca uyanık kalarak ileri/geri atlama (seek) sonrası veya akış esnasında donan video karelerini de yakalar.
- **Compose `key(playerEngine)` Görünüm Fabrikası Onarımı:** Tam ekran oynatıcıda motor değiştirildiğinde (Media3 ↔ MPV) Compose'un eski motorun ölü görünümünü tutması engellendi. Her motor kendi bağımsız `SurfaceView`/`TextureView` örneğini baştan kurar.
- **MPV Yüzey Yaşam Döngüsü & Mükerrer Reconfig Temizliği:** `attachedSurface` kontrolüyle MPV'nin aynı yüzeye iki kez bağlanması ve yeniden oluşturulan yüzeyi bozan fazladan `video-reconfig` komutları kaldırıldı. `video-out-params` boyutları (w/h, dw/dh) doğrulanarak sahte "hazır" durumları elendi.
- **Fallback Zincir Döngüsü Koruması:** `PlayerFallbackCoordinator` başarısız olan motoru doğrudan kayıt altına alarak Media3 → MPV → Media3 döngülerini engelledi. Her kaynak için her motor en fazla bir kez denenir.
- **Başlangıç Ayar Yarışı Çözümü:** DataStore diskten okunurken `AppSettings` varsayılan değerleri beklenir; kullanıcının MPV/harici motor tercihi yerine Media3'ün erken başlatılması engellendi.
- **Satır İçi (Inline) Yükleme Katmanı:** Fragman ve tema oynatıcılarında yükleme animasyonu, motor `READY` olsa bile gerçek bir video karesi ekrana gelene kadar (`inlineVideoVisible`) ekranda tutulur.

### 📦 AniSub Tam Donanımlı Altyazı Motoru (Full AniSub Engine)
- **Thread-Safe Bellek İçi Çerez Yöneticisi:** `InMemoryCookieJar` ile AniSub ve Cloudflare oturum çerezleri güvenle yönetilir.
- **ZIP & RAR Arşiv Desteği:** Altyazı paketleri (`.zip` ve `.rar`) `junrar` entegrasyonuyla otomatik olarak ayıklanıp önbelleğe alınır.
- **Akıllı Başlık ve Bölüm Eşleştirme:** Sezon ve bölüm numaraları toleranslı desenlerle filtrelenerek doğru Türkçe altyazı dosyası oynatıcıya sunulur.

### 🚀 Otomatik Sürüm Yükseltme Sistemi (Auto-Versioning)
- `version.properties` altyapısı kuruldu. Her APK derlemesinde sürüm numarası otomatik olarak yükseltilir ve standart `Kitsugi-Beta-v<version>-foss.apk` biçiminde paketlenir.

---

## 🇬🇧 English

### 🎬 Audio-Only Black Screen Recovery & Surface Lifecycle
- **Audio-Only Playback Detector:** Added pause/buffering/seek-aware `AudioOnlyPlaybackDetector` requiring real position deltas and a 6-second grace window to automatically recover when audio plays but the screen stays black.
- **Continuous Fullscreen Watchdog:** Watchdog stays active throughout playback to catch mid-stream frame loss and post-seek decoder stalls.
- **Compose `key(playerEngine)` Re-instantiation:** Replaced stale view reuse with `key(playerEngine)` so new video surfaces are created immediately on engine switches.
- **MPV Surface Lifecycle & Dimension Checks:** Tracked `attachedSurface` identity, dropped redundant `video-reconfig`, and verified `video-out-params` dimensions (`w/h` or `dw/dh`).
- **Fallback Loop Prevention:** Bounded engine fallbacks to at most one attempt per engine per source (`triedEngines += currentEngine`), preventing ping-pong loops.
- **Startup Settings Race Resolution:** Gated initial engine preparation on persisted settings from DataStore, preventing uninitialized placeholder engine defaults.
- **Inline Loading Overlay Predicate:** Kept plasma loader visible until `inlineVideoVisible(playerEngine)` confirms rendered video frames.

### 📦 Complete AniSub Subtitle Engine
- **In-Memory Cookie Jar:** Added thread-safe cookie persistence for AniSub requests.
- **ZIP & RAR Unpack Support:** Added native unpacking for both `.zip` and `.rar` packages.
- **Robust Fuzzy Matching:** Improved season/episode normalizer and subtitle caching.

### 🚀 Automated Build Versioning
- Added `version.properties` integration that auto-increments the build version whenever an APK assembly task runs.
