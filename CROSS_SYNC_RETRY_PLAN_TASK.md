# Kitsugi Çapraz Eşitleme — Kaynaklara Özgü Hatalar İçin Yeniden Deneme (Retry) Politikası

**Tarih:** 2026-10-24
**Dal:** `arena/3bccbfc7-kitsugi-beta`
**Kapsam:** Çapraz eşitleme (cross-sync) yazma aşamasında, her sağlayıcıya özgü **geçici (tekrarlanabilir)** hatalara ikinci/üçüncü bir şans tanıma; kalıcı hataların ise yeniden denmeyip "hata/atlama" olarak rapor edilmesi.

---

## 1. Gerekçe

Çapraz eşitleme (`AuthViewModel.syncPlatforms`) sırasında her kayıt için birden fazla sağlayıcıya yazma (add/update) gerçekleşir. Her sağlayıcı farklı şekillerde hata bildirir ve mevcut yeniden deneme (retry) mantığı dağınıktı:

| Kaynak | Mevcut retry (kotda) | Kapsadığı hatalar | Kapsanmayanlar |
|---|---|---|---|
| Kitsu | `createLibraryEntryDetailed`/`updateLibraryEntryDetailed`/`findLibraryEntryId` içinde 429 → 3 deneme; `RetryInterceptor` | 429, ağ | HTTP 5xx'i `KitsuWriteResult.errors` içinde döner, provider-level retry yok |
| AniList | `postAniList` içinde 429 → 3 deneme (`Retry-After`) | 429 | HTTP 5xx, ağ hatası, service-down anında directement hata |
| MAL | `KitsugiHttpClient.client` üzerinden `RetryInterceptor` | 5xx/429/ağ (HTTP katmanı) | provider-level `Retry-After` saygısı yok; 408/425 ayrıcahandle edilmiyor |
| Shikimori | `RetryInterceptor` + domain rotasyonu | 5xx/429/ağ (HTTP katmanı) | `createUserRate` HTTP hatası null döner, provider-level retry yok |
| Bangumi | `writeWithAuthRetry` (sadece 401) | 401 | HTTP 5xx, 429, ağ hatası |
| Simkl | `postSyncEnvelope` (429 + transport) | 429, ağ | — (zaten kapsamlı; bu değişikliğe alınmadı) |

Kullanıcı bildirimi: **"çapraz eşitleme için kaynaklara özgü hatalara özel tekrar deneme şansı vermesi lazım."** Yani her kaynağın kendi hata tipine göre bir kez daha denmesi gerekiyor.

---

## 2. Yeni bileşen: `CrossSyncRetry`

`app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncRetry.kt`

### 2.1 Sınıflandırma (`isTransientSyncError` / `isTransientErrorText`)

Bir hatanın **geçici** (tekrarlanabilir) mi **kalıcı** mı olduğunu karar verir:

- `CancellationException` → geçici değil (kontrol akışı).
- `java.io.IOException` (DNS/TLS/bağlantı/timeout) → geçici.
- `AniListServiceDownException`, `RateLimitException`, `SimklRateLimitException` → geçici.
- `BangumiApiException` → HTTP kodu `408/425/429` veya `5xx` ise geçici; `400/401/403/404/422` ise kalıcı.
- Diğer exception'lar → mesaj içersin anahtar kelime/HTTP-kodu analizi:
  - `HTTP 429/408/425/5xx`, `API hatası: 5xx/429` → geçici.
  - `hız sınırı`, `bağlantı hatası`, `connection refused/reset/timed out`, `temporarily disabled`, `service down`, `geçici hata`, `timeout` … → geçici.
  - `HTTP 400/401/403/404/422` ve kimlik/eşleşme açıklamaları → **kalıcı** (yeniden denmez).

### 2.2 `retrySyncWrite(platform, description, attempts=3, initialDelayMs=600, errorTexts, block)`

- Her deneme öncesi `PlatformRateLimiter.acquire(platform)` uygulanır (kaynak hız sınırına saygı).
- Denemeler arası üstel geri çekilme (600ms → 1200ms) ve `ensureActive()` cancellation kontrolü.
- İki türlü geçici başarısızlık yeniden dener:
  1. Bloktan fırlayan exception geçici ise,
  2. Blok başarılı döndü ama `errorTexts` ile çıkarılan hata metinleri arasında geçici olan varsa (ör. Kitsu HTTP 5xx'i `SyncResult.errors` içinde döner, exception değil).
- **Kalıcı** hatalar (4xx kimlik/eksiklik, kimlik reddi, "Kitsu ID bulunamadı", "AniList medya kimliği çözülemedi") hemen döndürülür, yeniden denmez. Böylece yüzlerce kayıtlık kütüphanede gereksiz yeniden deneme kuyruğu oluşmaz.

---

## 3. Uygulama

`AuthViewModel.syncPlatforms` Faz 3'teki 10 yazma çağrısı (5 kaynak × ekleme/güncelleme) `runSyncCatching { … }` yerine `CrossSyncRetry.retrySyncWrite(…)` ile değiştirildi:

| Kaynak | Çağrılar | `errorTexts` |
|---|---|---|
| AniList | `updateAniListEntry` (ekle + güncelle) | `{ emptyList() }` (hata exception olarak fırlar) |
| MAL | `updateMalEntry` (ekle + güncelle) | `{ emptyList() }` |
| Kitsu | `syncEntryToKitsu` (ekle + güncelle) | `{ it.errors }` (HTTP 5xx metin olarak döner) |
| Shikimori | `syncEntryToShikimori` (ekle + güncelle) | `{ it.errors }` |
| Bangumi | `syncEntryToBangumi` (ekle + güncelle) | `{ it.errors }` |
| Simkl | — | batch zaten `postSyncEnvelope` içinde yeniden dener; değiştirilmedi |

---

## 4. Güvenlik ve davranış notları

- **Asla silme yok:** yeniden deneme yalnızca ekleme/güncelleme yazmalarını etkiler; silme işlemi yok.
- **Kimlik güvencesi etkilenmez:** `CrossSyncIdentityGuard` reddi/doğrulanamayan kimlikatlar hala yeniden denenmez (hata metni geçici değil → hemen döner).
- **Cancellation korunur:** `CancellationException` yeniden denmeyi durdurur; `ensureActive()` denemeler arası kontrol edilir.
- **Hız sınırı:** her deneme `PlatformRateLimiter` ile thứlenir; `Retry-After` provider katmanında zaten uygulanıyor, burada ek bir pacing eder.
- **Yoğunluk:** bir kaynağın bütünüyle çöktüğü durumlarda her kayıt en fazla 2 extra deneme yapabilir; kullanıcı iptal edebilir.

---

## 5. Doğrulama

- `git diff --check`: temiz.
- Birim test `CrossSyncRetryTest` eklendi (HTTP kodu sınıflandırması, ağ/sunucu anahtar kelimeleri, `IOException`, cancellation, `BangumiApiException` kodu ayrımı, `IllegalStateException` mesaj analizi).
- Android/Gradle derlemesi ve JUnit bu ortamda çalıştırılamadı (JDK yok); cihazda/CI'da çalıştırılması gerekiyor.

## 6. Değişen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncRetry.kt` (**yeni**)
2. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncRetryTest.kt` (**yeni**)
3. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt` (10 yazma çağrısı + import)
4. `CROSS_SYNC_RETRY_PLAN_TASK.md` (bu dosya)
