# Kitsugi Beta v2.4.375

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Simkl Paylaşımlı Hız Sınırlayıcı (`SimklRateLimitInterceptor` & `SimklRequestRateGate`)
- **Merkezi İstek Zamanlaması:** Simkl hız sınırlayıcı daha önce yalnızca `SimklApiClient` örneği içinde yerel olarak çalışıyordu. "Tümü" aramasında anime, dizi ve film için eşzamanlı istek açıldığında ya da profil, takvim, senkronizasyon ve içe aktarma ekranları çalıştığında farklı örnekler birbirinin sınırını görmüyor ve aynı kullanıcı/istemci kotasını aşarak HTTP 429 hatasına düşebiliyordu.
- **Tüm Uygulama İçin Ortak Ağ Katmanı:** `api.simkl.com` adresine giden tüm istekler artık tek bir OkHttp yakalayıcısından (`SimklRateLimitInterceptor`) geçmektedir. Okuma (GET) istekleri 350 ms aralıkla sıralanırken, yazma istekleri 1.250 ms aralıkla ve adil bir semafor (`Semaphore(1, true)`) ile serileştirilir.

### 2. Gelişmiş 429 ve HTTP 400 "Rate Limit" Yönetimi
- **Retry-After Başlığı Desteği:** Sunucudan gelen `Retry-After` yanıtları (hem saniye hem HTTP tarih formatında) merkezi olarak tanınır ve bekleme süresi boyunca tüm Simkl istekleri güvenle bekletilir.
- **HTTP 400 Senkronizasyon Kilidi Tespiti:** Simkl'in yazma isteklerinde döndürdüğü HTTP 400 `rate_limit` yanıtı artık bir kısıtlama sinyali olarak algılanır ve otomatik yeniden deneme döngüsüne alınır.
- **Gereksiz Çoklu Denemelerin Önlenmesi (`SimklImportManager`):** Ağ katmanı zaten yeniden denediği için, kütüphane içe aktarma sırasında ısrarcı 429 yanıtlarının 4 kat daha fazla büyük istek üretmesi engellendi.

### 3. Platform Hata Tanılama ve Rozet İyileştirmeleri
- **Geçici 429'lar Hata Rozetini Tetiklemez (`PlatformFailureInterceptor`):** Ağ katmanında otomatik tekrar denenip başarıyla sonuçlanan geçici 429 yanıtları artık arama ve detay ekranlarına yanıltıcı "servis hatası" rozeti basmaz.
- **SMK_8004 Hata Kodu Eşlemesi (`PlatformErrorCatalog`):** Simkl'in 400 `rate_limit` yanıtları doğru şekilde `SMK_8004` ("Simkl hız sınırı") olarak etiketlenir; hata açıklamaları Türkçe ve İngilizce olarak güncellendi.
- **Statik Medya ve Önceki Düzeltmeler Korundu:** Coil poster/altyazı CDN 404 filtreleri, Dolby Vision RPU düzeltmeleri (v2.4.373) ve yorum şeridi / altyazı önbellekleyici (v2.4.374) eksiksiz muhafaza edildi.

---

## 🇬🇧 English Release Notes

### 1. Centralized Simkl Rate Limiter (`SimklRateLimitInterceptor` & `SimklRequestRateGate`)
- **Global Request Pacing:** Previously, rate limiting was local to each `SimklApiClient` instance. Parallel queries from universal search (fetching anime, series, and movies at once) or overlapping requests from profile, calendar, sync, and import screens bypassed one another's throttles, triggering HTTP 429 rate limit errors.
- **Shared OkHttp Network Layer:** All requests destined for `api.simkl.com` now route through a shared OkHttp interceptor (`SimklRateLimitInterceptor`). Read requests (GET) are paced at 350ms intervals, while write requests are spaced at 1,250ms and strictly serialized via a fair semaphore.

### 2. Robust 429 & HTTP 400 "Rate Limit" Handling
- **Full Retry-After Compliance:** Accurately parses and enforces server `Retry-After` headers (both delta-seconds and RFC-1123 HTTP-date formats) across the entire application.
- **Sync Write-Lock Recognition:** Simkl's HTTP 400 response with `rate_limit` payload during sync writes is now recognized as a throttling condition and queued for retry.
- **Preventing Request Multiplication (`SimklImportManager`):** Redundant inner retry loops on persistent 429s were removed in favor of centralized retry pacing, avoiding multiplying full library fetches.

### 3. Error Registry & Platform Failure Diagnostics
- **Transient 429s Suppressed (`PlatformFailureInterceptor`):** Intermediate 429 responses that are successfully recovered by OkHttp retries no longer leave false failure badges on search and discovery screens.
- **Classification as SMK_8004 (`PlatformErrorCatalog`):** Both HTTP 429 and HTTP 400 write-lock messages are properly mapped to `SMK_8004` ("Simkl rate limit") with updated Turkish and English explanations.
- **Full Architecture Preservation:** Maintained all CDN/media 404 filters, Dolby Vision RPU pipeline (v2.4.373), and review crash / subtitle prefetching fixes (v2.4.374).
