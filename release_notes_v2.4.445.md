# Kitsugi Beta v2.4.445

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Manga Kaynak Bazlı Giriş Sistemi & Oturum Yönetimi
- **Kotatsu Eklentileri Entegrasyonu**:
  - Kaynak arayüzüne `authUrl`, oturum durumu (`isAuthorized`), kullanıcı adı (`getUsername`) ve Injekt tabanlı ortak `NetworkHelper` / cookie katmanı desteği eklendi.
  - `AuthRequiredException`, 401 ve 403 HTTP yanıtları doğrudan `SourceErrorCategory.Auth` ve `PlatformErrorCode.MangaAuth` olarak sınıflandırılır.
- **Mihon Eklentileri Entegrasyonu**:
  - `ConfigurableSource` yapılandırma ekranı taranarak eklentiye özgü kullanıcı adı, parola, token, oturum ve çerez alanları dinamik olarak çözümlenir (`MihonSourceAuthentication`) ve `source_<id>` tercihlerine işlenir.
  - Web tabanlı oturum gerektiren kaynaklar için WebView ile site oturumu açma ve iki yönlü çerez eşitleme (`cookieSyncUrls`) sağlandı.
- **Kaynak Sağlığı ve Yönetimi**:
  - Eklenti sağlığı ekranında (`MangaSourceHealthScreen`) kurulu kaynaklar sekmesine her kaynak için oturum durumu rozeti ve "Oturum aç" eylem kartları eklendi.

### 2. Akıllı Auth Hata Akışları ve Yerinde Yeniden Deneme
- **Tüm Katmanlarda Auth Yakalama**:
  - Keşfet (popüler/filtreli listeler), serbest metin arama, bölüm listesi, sayfa listesi ve tekil sayfa görsel yüklemelerinde oturum hatası oluştuğunda genel hata yerine `MangaSourceAuthBanner` ("Oturum aç" ve "Yeniden dene") gösterilir.
  - Okuyucu içerisinde tek bir görsel yüklenemediğinde, sayfa üzerinde doğrudan oturum açma butonu belirir.
  - Giriş yapıldıktan sonra tüm ekran sıfırlanmaz; yalnızca ilgili kaynak, bölüm veya sayfa yerinde yeniden denenir.

### 3. Mimari ve Dil Bütünlüğü
- **Zero Material Icons**:
  - `app/src/main` genelinde `androidx.compose.material.icons.*` bağımlılıkları tamamen kaldırılarak `%100 KitsugiIcons as Icons` standardı uygulandı.
- **%100 Dil Simetrisi**:
  - Türkçe (`values/strings.xml`) ve İngilizce (`values-en/strings.xml`) dize kaynakları 18.695 anahtar ile sıfır eksik/fazla olacak şekilde tam simetrik hale getirildi.
- **Test ve Performans Doğrulaması**:
  - Tüm birim testleri (`testFossDebugUnitTest`) ve sekme performans koruyucusu (`kitsugi_pager_perf_guard.py`) yeşil geçti.

---

## 🇬🇧 English Release Notes

### 1. Source-Based Authentication & Session Management
- **Kotatsu Extension Integration**:
  - Added support for `authUrl`, session authorization status (`isAuthorized`), username resolution (`getUsername`), and unified Injekt-backed `NetworkHelper` / cookie layer.
  - Kotatsu `AuthRequiredException`, HTTP 401, and HTTP 403 responses are systematically classified as `SourceErrorCategory.Auth` and `PlatformErrorCode.MangaAuth`.
- **Mihon Extension Integration**:
  - Dynamic credential extraction via `ConfigurableSource` preference inspection (`MihonSourceAuthentication`), mapping username, password, token, session, and cookie fields to `source_<id>` preferences.
  - In-app WebView site login with bidirectional cookie synchronization (`cookieSyncUrls`) for web-authenticated sources.
- **Source Health & Management**:
  - Installed sources tab in `MangaSourceHealthScreen` now displays authentication state badges and quick "Log in" action chips for each supported source.

### 2. Granular Auth Failure Handling & In-Place Retries
- **Unified Auth Error Interception**:
  - Explore (popular/filtered manga), search, chapter lists, page lists, and individual image downloads now render `MangaSourceAuthBanner` ("Log in" / "Retry") upon encountering authentication requirements.
  - In the manga reader, single image failures trigger an inline login prompt directly on the affected page item.
  - Completing authentication triggers an in-place retry solely for the affected source, chapter, or page without restarting the reader or losing navigation context.

### 3. Architecture & Localization Integrity
- **Zero Material Icons**:
  - Zero imports of `androidx.compose.material.icons.*` across `app/src/main`, adhering strictly to `KitsugiIcons as Icons`.
- **100% Localization Symmetry**:
  - Exactly 18,695 string keys synchronized across Turkish (`values/strings.xml`) and English (`values-en/strings.xml`) resource bundles with zero parity discrepancies.
- **Verified Stability**:
  - Passed all unit tests (`testFossDebugUnitTest`) and pager performance verification (`kitsugi_pager_perf_guard.py`).
