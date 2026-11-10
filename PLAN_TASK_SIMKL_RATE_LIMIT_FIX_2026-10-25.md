# Plan / Task — Simkl Hız Sınırı Kök Neden Düzeltmesi

**Tarih:** 2026-10-25  
**Dal:** `arena/78286bf4-kitsugi-beta`  
**Durum:** Kod değişiklikleri ve statik kontroller tamamlandı. Gradle derleme/JUnit testleri Java/JDK bulunmadığı için çalıştırılamadı; canlı Simkl hesabı üzerinden kota doğrulaması yapılmadı.

## 1. Kullanıcı bildirimi

Simkl isteklerinde tekrar tekrar `KTS-SMK-8004 — Simkl hız sınırı` uyarısı görülüyor. Yalnızca uyarı metnini gizlemek değil, ağ akışındaki kök nedeni gidermek gerekiyor.

## 2. Kök neden

- Eski `awaitSimklRateLimit` beklemesi her `SimklApiClient` örneğine özeldi; `SimklCalendarClient`, profil ve import gibi başka yollar aynı sayacı paylaşmıyordu.
- “Tümü” araması anime, TV ve film uçlarına eşzamanlı istek başlatıyordu. Farklı ekran/istemciler birleşince aynı `client_id` ve kullanıcı kotası uygulama genelinde denetlenmiyordu.
- `PlatformFailureInterceptor`, retry katmanının içinde çalıştığı için otomatik retry ile sonradan başarılı olan ara 429 yanıtları da hata kaydına düşebiliyordu.

## 3. Yapılan düzeltme

- `KitsugiHttpClient` ortak taşıma katmanına `SimklRateLimitInterceptor` eklendi. Limiter, istemci örnekleri arasında paylaşılan durum kullanıyor; Simkl GET isteklerini 350 ms aralıkla, yazma isteklerini seri ve 1.25 saniye aralıkla gönderiyor.
- HTTP 429 yanıtlarında `Retry-After` (saniye veya HTTP-date) ortak cooldown'a uygulanıyor. Simkl yazma kilidinin HTTP 400 `rate_limit` yanıtı da güvenli biçimde tanınıp bir kez yeniden deneniyor.
- `PlatformFailureInterceptor`, retry katmanının dışına alındı; yalnızca son HTTP yanıtı hata kaydı oluşturuyor. Yanıt gövdesinden keyfi içerik saklanmıyor, allow-list `rate_limit` işareti kullanılıyor.
- Import akışında kalıcı hız sınırı yanıtı için gereksiz tam liste retry'ları durduruldu; `SimklApiClient` içindeki örnek bazlı hız beklemesi ve çakışan POST retry döngüsü kaldırıldı.
- `SMK_8004` sınıflandırması ve Türkçe/İngilizce kullanıcı açıklamaları güncellendi; limiter ve registry testleri eklendi.

## 4. Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/core/network/KitsugiHttpClient.kt`
- `app/src/main/java/com/kitsugi/animelist/core/network/PlatformFailureInterceptor.kt`
- `app/src/main/java/com/kitsugi/animelist/core/network/RetryInterceptor.kt`
- `app/src/main/java/com/kitsugi/animelist/core/network/SimklRateLimitInterceptor.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/data/auth/SimklImportManager.kt`
- `app/src/main/java/com/kitsugi/animelist/data/platform/PlatformErrorCatalog.kt`
- `app/src/main/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistry.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/core/network/SimklRateLimitGateTest.kt` (yeni)
- `app/src/test/java/com/kitsugi/animelist/data/platform/PlatformFailureRegistryTest.kt`
- `scripts/platform_error_codes/error_table.py`
- `docs/audits/SIMKL_SYNC_AUDIT_2026-10-07.md`
- `KOK_NEDEN_RAPORU_LISTE_SENKRON_SILME_YOLU_VE_KIMLIK_ESLEME_2026-10-21.md`

## 5. Doğrulama ve kalan işler

Başarılı statik kontroller:

- `git diff --check`
- Değişen 10 Kotlin kaynak/test dosyasında Tree-sitter Kotlin sözdizimi kontrolü
- `python3 -m py_compile scripts/platform_error_codes/error_table.py`
- Türkçe ve İngilizce `strings.xml` XML parse kontrolü
- Eski `awaitSimklRateLimit` çağrı/tanımlarının kalmadığına dair kaynak taraması

Kalan doğrulama:

- Java/JDK bulunan ortamda `./gradlew :app:testDebugUnitTest` ve `./gradlew :app:assembleDebug` çalıştırılmalı. Bu çalışma alanında `java`/`JAVA_HOME` bulunmadığından Gradle testleri/derleme henüz doğrulanmış değildir.
- Yeni build ile kullanıcı cihazında Simkl araması ve senkron akışı denenmeli. Hesaba özgü quota/throttle durumu bu oturumda canlı Simkl isteğiyle doğrulanmadı.
