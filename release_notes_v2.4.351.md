# Kitsugi Beta v2.4.351

## 🇹🇷 Türkçe

### 🔄 Çapraz Liste Senkronizasyonu & Oturum Dayanıklılığı (E85–E95)
- **E85 (Kitsu Token Yenileme):** Kitsu 30 günlük access token'ı artık proaktif olarak ve 401 sonrasında `refresh_token` ucu ile yenilenir. `getOrRefreshKitsuToken` (proaktif mutex korumalı) ve `forceRefreshKitsuToken` eklendi; `KitsuSyncManager` 401 durumunda otomatik olarak bir kez tekrar dener.
- **E86 (Bangumi Koleksiyon Önceliği):** Yeni eklenen yapımlarda Bangumi API'sinin 400 `ErrSubjectNotCollected` hatası vermesi engellendi; önce koleksiyon oluşturulur, ardından bölüm ilerlemesi kaydedilir.
- **E87 (Bangumi Legacy PHP Yedek Yolu):** Bangumi `/v0` uç noktalarında `insufficient token scope` (403) hatası alındığında, alan-bazlı veri kaybını engelleyen resmi Legacy PHP yedek yolu (`POST /collection/{id}/update` ve `/subject/{id}/update/watched_eps`) devreye girer.
- **E88 (Bangumi Bayat Token Fallback):** `getValidToken` geçici ağ hatalarında yanlış "bağlı değil" durumuna düşmek yerine bayat token ile çalışmaya devam eder; kalıcı ve geçici yenileme ayrımı yapılarak cooldown mekanizması sıfırlanır.
- **E89 (MAL Refresh & Fallback):** MAL token yenileme isteği `FormBody` standardına geçirildi; geçici ağ kopmalarında oturum kapatılmayıp bayat-token ile istek sürdürülür.
- **E90 (Sağlayıcı Başına Refresh Mutex):** Tek kullanımlık refresh token'ların yarış durumunda (race condition) birbirini geçersiz kılmasını önleyen sağlayıcı bazlı senkronizasyon mutex'leri kuruldu.
- **E91 & E95 (Oturum Süresi Doldu Görünürlüğü):** Yetkilendirmesi düşen hesaplar için `markSessionExpired` ve `${provider}_session_expired` bayrakları tanımlandı. Profil ve Ayarlar > Hesaplar sayfalarında "Oturum süresi doldu" rozeti ve yeniden giriş yönlendirmesi sağlandı.
- **E92 (Shikimori 401 İstisnası & Yenileme):** Shikimori'nin 5 yazma ucunda HTTP 401 durumu `ShikimoriTokenException` fırlatarak token'ın yenilenmesini ve işlemin bir kez tekrarlanmasını tetikler.
- **E93 (Shikimori Çift DELETE):** Shikimori silme işleminde `rateId` bayat kalmışsa liste yeniden taranıp taze kimlikle ikinci bir silme denemesi yapılır.
- **E94 (MAL İçe Aktarma 401 Koruması):** MAL listesi içe aktarılırken 401 alındığında token yenilenip işlem sürdürülür.
- **Sözleşme Testleri:** 7 yeni sözleşme testi içeren `BangumiLegacyWriteContractTest` dahil edildi ve tüm `data.auth.*` testleri başarıyla doğrulandı.

---

## 🇬🇧 English

### 🔄 Cross-List Synchronization & Session Resilience (E85–E95)
- **E85 (Kitsu Token Refresh):** Proactive and 401-triggered token refresh via `refresh_token` endpoint. Added mutex-guarded `getOrRefreshKitsuToken` and `forceRefreshKitsuToken` with single auth retry in `KitsuSyncManager`.
- **E86 (Bangumi Pre-Collection):** Resolved 400 `ErrSubjectNotCollected` by ensuring subject collection is created before updating episode progress.
- **E87 (Bangumi Legacy PHP Fallback):** Added official Legacy PHP fallback endpoints (`POST /collection/{id}/update` and `/subject/{id}/update/watched_eps`) when `/v0` returns 403 `insufficient token scope`.
- **E88 (Bangumi Stale Token Fallback):** Retained stale tokens during transient network glitches instead of reporting false disconnection; decoupled temporary vs persistent refresh failures.
- **E89 (MAL FormBody Refresh & Glitch Handling):** Standardized MAL token refresh with `FormBody` and stale-token resilience.
- **E90 (Per-Provider Refresh Mutex):** Guarded one-time refresh tokens against concurrent refresh race conditions.
- **E91 & E95 (Session Expiry Visibility):** Added `markSessionExpired` states and "Session Expired" warning chips on Profile and Account settings screens.
- **E92 (Shikimori 401 Handling):** Threw `ShikimoriTokenException(401)` across 5 write endpoints to trigger auto-refresh and single retry.
- **E93 (Shikimori Delete Recovery):** Refreshes stale `rateId` on deletion failure and attempts a secondary delete request.
- **E94 (MAL Import 401 Recovery):** Seamlessly refreshes expired MAL sessions during list imports.
- **Contract Tests:** Added 7 comprehensive test cases in `BangumiLegacyWriteContractTest`.
