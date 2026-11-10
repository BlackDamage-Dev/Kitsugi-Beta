# Çözüm Raporu — MDBList API Kotası Gerçek Değeri Göstermiyordu

**Tarih:** 2026-10-23
**Konu:** Ayarlar → Entegrasyonlar → MDBList sekmesindeki "API Kotası" alanının ve detay sayfasındaki MDBList rozetinin sabit `1.000 / 1.000` göstermesi.

---

## 1. Şikâyet

> "Ve halen daha MDBList API kotasını sayısal bir değer olarak göstermekten başka bir işe yaramıyor; yani gerçekten ne kadar kaldığını görmemiz gerekiyor."

İki ekran görüntüsü:

1. Detay sayfası → **Harici Puanlar** başlığının yanında `MDBlis 1.000/1.000`.
2. Ayarlar → **API Kotası** kartı → `1.000 / 1.000 istek kaldı` ve dolu bir çubuk.

Her iki değer de sunucudan okunmuyordu: kutu, günlük ücretsiz limit olan 1.000'i sabit yazıyordu. Kullanıcı 300 istek harcasa da ekranda hâlâ 1.000 görünüyordu.

## 2. Kök Neden

| Katman | Sorun |
| --- | --- |
| İstemci (`MdbListClient`) | Yalnızca `https://mdblist.com/api/?apikey=…&i=…` puan uç noktası kullanılıyordu; yanıt başlıkları (`X-RateLimit-*`) hiç okunmuyordu. |
| Kota kaynağı | MDBList'in kota uç noktası (`GET https://api.mdblist.com/user`) hiç çağrılmıyordu; hesabın planı/kalan hakkı bilinmiyordu. |
| Görünüm | Kalan istek değeri sabit `1.000` ve "kalan" oranı `1.0` olarak çiziliyordu; yenileme yolu yoktu. |
| Ağ davranışı | `RetryInterceptor` 429 yanıtında isteği 2 kez daha gönderiyordu. Her deneme günlük kotadan bir istek daha düşürdüğü için ekrandaki değer gerçek tüketimden daha da uzaklaşıyordu. |

## 3. Araştırma (MDBList resmî dokümanı)

- **Kota başlıkları:** Her API yanıtı `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` (Unix saniye, 00:00 UTC) döndürür. 429 durumunda `Retry-After` (saniye) gelir.
- **Hesap uç noktası:** `GET https://api.mdblist.com/user?apikey=…` yanıtı `api_requests` (günlük hak), `api_requests_count` (bugün kullanılan), `rate_limit`, `rate_limit_remaining`, `rate_limit_reset` ve `plan` alanlarını içerir.
- **Limitler:** Ücretsiz 1.000 istek/gün; destekçi kademeleri 10.000 / 25.000 / 100.000 / 250.000 / 500.000 / 1.000.000 / 1.500.000. Limitler hesap bazlıdır; tüm API anahtarları ve uygulamalar aynı havuzu paylaşır. Ayrıca 5 dakikada 1.000 okuma / 300 yazma sınırı vardır.

Kaynaklar:
- <https://docs.mdblist.com/docs/api>
- <https://docs.mdblist.com/docs/supporter>
- `openapi.json` (linaspurinis/api.mdblist.com) → `/user` şeması ve `ApiKeyAuth` (`?apikey=`)

## 4. Yapılan Değişiklikler

### 4.1 Yeni model ve ayrıştırıcı — `data/remote/MdbListQuota.kt`

- `MdbListQuota`: `dailyLimit`, `serverRemaining`, `pendingRequests`, `resetAtEpochSeconds`, `observedAtMillis`, `plan`, `authoritative`.
  - `remaining = serverRemaining - pendingRequests`
  - `updatedFor(now)`: UTC gece yarısı geçtiyse kotayı yeni güne taşır.
  - `afterRequest(now)`: başlık gelmeyen yanıtlarda yerel sayacı düşürür.
  - `onRateLimited(now, retryAfter)`: 429'da kalan hakkı sıfırlar, sıfırlanma anını `Retry-After`'dan alır.
- `MdbListQuotaParser`:
  - `fromHeaders()` → `X-RateLimit-*`, `RateLimit-*`, `X-Rate-Limit-*` (büyük/küçük harf bağımsız).
  - `fromUserJson()` → `rate_limit_remaining` öncelikli; yoksa `api_requests - api_requests_count`.
  - Sıfırlama değeri epoch saniye / epoch milis / "kalan saniye" biçimlerinin üçünü de kabul eder.
- `MdbListQuotaStore`: API anahtarının SHA-256 parmak iziyle `SharedPreferences` üzerinde kalıcı; depo açılamazsa bellek içi yedek.

### 4.2 İstemci — `data/remote/MdbListClient.kt`

- `quotaFlow: StateFlow<MdbListQuota?>` — ayarlar ve detay sayfası aynı kaynağı dinler.
- `refreshQuota(apiKey, force)` → `GET https://api.mdblist.com/user?apikey=…`, önce başlıkları, başlık yoksa gövdeyi ayrıştırır.
- `recordQuotaResponse()` → her puan/doğrulama yanıtında kota güncellenir (başlık varsa kesin, yoksa yerel sayaç).
- `loadCachedQuota()` → diskteki son değer anında akışa yüklenir (ağ isteği yok).
- `maybeRefreshQuotaInBackground()` → eski uç nokta başlık döndürmezse puan isteğini bekletmeden arka planda tek `/user` çağrısı (15 dk'da en fazla 1).
- Kota akışı anahtar parmak iziyle eşleştirilir; anahtar değişince eski hesabın kotası gösterilmez.

### 4.3 Ağ — `core/network/KitsugiHttpClient.kt`

- `mdbListClient`: `RetryInterceptor` kaldırıldı. 429 alındığında isteği tekrar göndermek kotayı boşa tüketiyordu; kota zaten 429 ile kesinleşiyor.

### 4.4 Arayüz

- `KitsugiIntegrationsSettingsDialog.kt` → `MdbListQuotaBlock`
  - Başlık: "API Kotası", büyük satır: `987 / 1.000 istek kaldı` (yerel ayraçla).
  - Kalan hak oranını gösteren çubuk; %15 altı kırmızı, %35 altı amber, üstü yeşil.
  - "3 sa 12 dk sonra yenilenir" canlı geri sayım + "Son güncelleme: 21:58".
  - Yenile düğmesi; hata durumunda açıklama + tekrar dene.
  - Sunucudan doğrulanmamış değerlerde "tahmini" uyarısı.
  - Plan satırı (`Free planı · günlük 1.000 istek`).
- `ApiResultDetailComponents.kt` → "Harici Puanlar" başlığındaki MDBList rozetinin yanında gerçek `kalan/limit` metni. **Yalnızca `authoritative` değer varsa** gösterilir; uydurma sayı yazılmaz.
- `SettingsSearch.kt` → "API Kotası" arama dizinine eklendi.

### 4.5 Başlatma

- `KitsugiApplication.onCreate()` → `MdbListClient.initialize(this)` (izole `runCatching`, açılışı bloke etmez).

## 5. Davranış Özeti

| Durum | Gösterim |
| --- | --- |
| Sunucudan başlık geldi | `Kesin`: gerçek kalan / günlük limit |
| Yalnızca yerel sayaç var | `Tahmini` etiketiyle kalan / limit + uyarı satırı |
| Hiç veri yok | "Kota bilgisi henüz alınmadı" + Yenile |
| 429 | Kırmızı "Günlük kota doldu · X sonra yenilenir" |
| Gece yarısı (UTC) | Otomatik olarak günlük limite döner |

## 6. Testler

`app/src/test/java/com/kitsugi/animelist/data/remote/MdbListQuotaTest.kt`

- Başlık ayrıştırma (3 farklı başlık adı varyantı, eksik başlıkta `null`)
- Sıfırlama biçimleri (epoch saniye / epoch milis / delta saniye)
- `/user` JSON'u (`rate_limit_remaining` ve `api_requests_count` yedekleri)
- Yerel istek düşümü, UTC gece yarısı devri, 429 davranışı
- Diskte saklama biçiminin gidiş-dönüşü

## 7. Doğrulama Adımları (cihaz)

1. Ayarlar → Entegrasyonlar → MDBList → geçerli bir API anahtarı gir.
2. "API Kotası" kartında `1.000 / 1.000` yerine `997 / 1.000` gibi bir değer görünmeli.
3. Birkaç detay sayfası aç → değer düşmeli; yenile → sunucudan güncel değer gelmeli.
4. Detay sayfası → Harici Puanlar → rozet yanında `997/1.000` görünmeli.
5. Uçak modunda yenile → "Kota okunamadı" satırı ve son bilinen değer korunmalı.
