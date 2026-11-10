# Kitsugi Beta v2.4.374

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. TMDB / Jikan / Bangumi Yorumlar Şeridi Çökmesi Giderildi (`ReviewsTab.kt`)
- **Boş / Null Yorum Kimliği Koruması:** TMDB, Jikan ve Bangumi servislerinden gelen yorumların kimlikleri (`id`) boş veya `null` dönebildiğinden Jetpack Compose `LazyColumn` içinde `tmdb_null` gibi mükerrer anahtarlar oluşuyor ve "Key was already used" istisnasıyla uygulama çöküyordu.
- **Güvenli Benzersiz Anahtarlar (`KitsugiReview.stableReviewKey`):** Yorumlar için yazar, özet veya zaman bilgisiyle her koşulda benzersiz ve kararlı anahtar üreten yapı kuruldu.
- **Tüm Listelerde Çökme Koruması (`UniqueLazyItems` & `LazyKeyDedup`):** Uygulama genelindeki yaklaşık 45 LazyColumn / LazyRow / Grid listesine mükerrer anahtarları tespit edip otomatik ayıran koruma eklendi.

### 2. Oynatıcı Harici Altyazı Donması ve Çift Başlatma Giderildi (`MpvSubtitlePrefetcher.kt`)
- **Arka Planda İndirme (UI Donması Çözümü):** Harici altyazılar eskiden ana iş parçacığında uzak URL üzerinden senkron `sub-add` ile ekleniyordu; bu da 2 ila 5 saniyelik belirgin arayüz donmalarına yol açıyordu. Artık altyazılar arka planda (IO) önbelleğe indirilip yerel dosya yolu üzerinden MPV'ye ekleniyor.
- **Dinamik Altyazı Ekleme (`addExternalSubtitles`):** Eklentilerden sonradan gelen altyazılar için oynatıcı artık sıfırdan yeniden başlatılmıyor. Bu sayede CDN'e ikinci istek atılmıyor (HTTP 429 riski kalktı), gereksiz yeniden tamponlama yaşanmıyor ve "Başladı" bildirimi iki kez çıkmıyor.

### 3. Tanılama ve Çökme Raporlama İyileştirmeleri
- **Yanlış "Sessiz Çökme" Alarmları Düzeltildi (`PreviousExitClassifier` & `KitsugiSessionSupervisor`):** Güncelleme sonrası normal kapanmalar veya önceden işlenmiş çökmeler artık mükerrer olarak sessiz çökme kaydedilmiyor.
- **ApplicationExitInfo Desteği:** Android'in sağladığı gerçek işlem çıkış nedenleri (bellek yetersizliği, kullanıcı sonlandırması vb.) çökme raporlarına dahil edildi.
- **AniList 404 & SLF4J:** AniList 404 yanıtlarında hangi isteğin başarısız olduğu açıkça günlüğe yazılıyor; gereksiz SLF4J uyarısı susturuldu.

---

## 🇬🇧 English Release Notes

### 1. TMDB / Jikan / Bangumi Reviews Lazy List Crash Fixed (`ReviewsTab.kt`)
- **Null / Empty ID Guard:** Reviews fetched from TMDB, Jikan, or Bangumi often lack unique IDs, producing duplicate keys like `tmdb_null` in Jetpack Compose `LazyColumn` and crashing with `IllegalArgumentException: Key was already used`.
- **Deterministic Keys (`KitsugiReview.stableReviewKey`):** Generates guaranteed unique and stable keys derived from author, snippet hash, and source metadata.
- **App-Wide List Defense (`UniqueLazyItems` & `LazyKeyDedup`):** Protected approximately 45 Lazy lists across the entire app with deduplicating item builders to prevent duplicate key crashes.

### 2. MPV Subtitle Freezes & Double Initialization Resolved (`MpvSubtitlePrefetcher.kt`)
- **Background Subtitle Prefetching:** Previously, external subtitles were added synchronously on the main thread via remote URLs, causing noticeable 2-5 second UI freezes. Remote subtitles are now pre-downloaded asynchronously on IO threads and fed to MPV as local cache files.
- **Dynamic Subtitle Injection (`addExternalSubtitles`):** Late-arriving subtitles no longer re-initialize the player engine from scratch. This eliminates duplicate requests (mitigating HTTP 429 rate limits), prevents unnecessary re-buffering, and fixes the duplicate "Started" notification.

### 3. Diagnostics & Crash Reporting Enhancements
- **Eliminated False "Silent Crash" Flags (`PreviousExitClassifier` & `KitsugiSessionSupervisor`):** Routine post-update terminations and already recorded exceptions are no longer falsely logged as silent crashes.
- **ApplicationExitInfo Logging:** Integrated native Android exit reasons (OOM, user kill, crash) into diagnostic reports.
- **AniList 404 & SLF4J:** Clear endpoint context logged for AniList 404 errors; silenced redundant SLF4J warning.
