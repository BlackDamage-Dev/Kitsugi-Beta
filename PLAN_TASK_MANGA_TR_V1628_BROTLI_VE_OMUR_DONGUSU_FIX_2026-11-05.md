# Manga Eklenti Sistemi & Manga-TR v1.6.28 — Plan ve Görev Durumu

**Paket zamanı:** 2026-11-05 (Europe/Istanbul)  
**Depo:** `f26901964-eng/Kitsugi-Beta`  
**Çalışma dalı:** `arena/16a166e4-kitsugi-beta`  
**Temel sürüm / birleşim noktası:** `v2.4.422` (`f4c9b4ad` — `main` + `origin/feat/golge-bahcesi-integration`)  
**Kapsam:** Bu oturumda manga eklenti yükleme/silme/güncelleme ömür döngüsü, ağ sıkıştırma (Brotli/Gzip/Deflate), DDoS-Guard/Cloudflare kurtarma, domain zehirlenmesi temizliği, R8 host ABI koruması ve Eklenti Portalı manga araması için değiştirilen/eklenen **28 kaynak ve test dosyası** + bu plan/görev dosyası (**toplam 29 dosya**).  
**Mimari kısıt uyumu:** **+0 yeni XML `<string>` / `<id>` kaynağı** (4 modül kuralı ve TR/EN yerelleştirme paritesi %100 korundu: 18.452 string senkron).

---

## 1. Tespit Edilen Kök Nedenler

1. **`Manga-TR v1.6.28` Kurulum Hatası (`"APK paket/sürüm bilgisi katalogla uyuşmuyor"`):**
   - `Kitsugi-Manga-Plugins` kataloğu `.../builds/apk/tachiyomi-tr.mangatr-v1.6.28.apk` adresini işaret ederken `builds` dalındaki dosya `versionCode=106027` (`1.6.27`), `main` dalındaki `apk/tachiyomi-tr.mangatr-v1.6.28.apk` ise gerçek imzalı `versionCode=106028` (`1.6.28`) sürümüdür.
   - Eski `MangaExtensionLoader.downloadExtension` ilk HTTP 200 dönen adayı indirip döngüden çıkıyor, manifest uyuşmazlığını döngü dışında görüp diğer dal adaylarını denemeden kurulumu düşürüyordu.
   - Ayrıca `Kitsugi-Manga-Plugins` deposunda kullanılan ek imzalama sertifikaları (`76f057ae...`, `7ca8efbd...`, `d2e1e47b...`) `TrustManager` yerleşik güven listesinde eksikti.

2. **Sıkıştırılmış Yanıt Gövdeleri (`Brotli` / `Gzip` / `Deflate`) ve DDoS-Guard / Cloudflare (`KTS-SRC-9003`):**
   - `MangaTR.headersBuilder()` açıkça `Accept-Encoding: gzip, deflate, br` eklediği (ve `ajaxHeaders()` üzerine `identity` eklediği) için hem OkHttp `BridgeInterceptor` hem de `KeiSource` içindeki `CompressionInterceptor` yanıt açmayı atlıyordu.
   - Yerel `okhttp3.brotli.Brotli` ve `okhttp3.zstd.Zstd` sınıfları yalnızca no-op stub olduğundan `br` yanıtları ham ikili veri olarak kalıyor; `DDoSGuardInterceptor.peekBody()` ve `Jsoup` ayrıştırması başarısız oluyordu.
   - `CloudflareInterceptor`, `isMangaTrHost` için devre dışıydı, `Server: ddos-guard` başlığını tanımıyordu, WebView içinde farklı `User-Agent` kullanıyordu ve çözülen çerezleri `NetworkHelper.cookieJar`'a aktarmıyordu.

3. **Eski Sürümlerden Kalan Domain Zehirlenmesi (`mangatr.net`) ve `"0 sonuç · 0 kaynak"` Soğutma Kilidi:**
   - Eski `SourceMirrorResolver.isReachable` `403`/`503` yanıtlarını erişilebilir kabul edip `MangaTrCompat.KNOWN_DOMAINS` içindeki squatter alan adlarını (`mangatr.net`, `manga-tr.net`) kalıcı olarak `SourceConfigStore` ve `source_<id>` tercihlerine yazmıştı.
   - Tek bir `CaptchaRequired` hatası kaynağı ilk denemede 30 dakika soğutmaya sokuyor, `MangaSearchCoordinator` da tüm kaynaklar soğutmadayken boş liste döndürerek `"0 sonuç · 0 kaynak"` ekranına yol açıyordu.

4. **Yükleme / Silme / Güncelleme Ömür Döngüsü ve R8 Host Arayüz Koruması:**
   - Dosya seçiciden (`.apk`/`.mex`) mevcut bir eklentinin üzerine yeni sürüm kurulduğunda `unloadExtension` çağrılmadığı için eski sınıf yükleyici önbellekten dönüyordu.
   - `getExtensionIdForSource` yalnızca referans eşitliğine (`===`) baktığı için yenilenmiş sarmalayıcı nesnelerde diskteki `.apk` silinmeden kalabiliyordu; Android 14+ `setReadOnly()` dosyalarında silme/yedek temizliği öncesi `setWritable(true)` güvencesi eksikti.
   - `app/proguard-rules.pro` içinde `eu.kanade.tachiyomi.**`, `tachiyomi.core.**`, `uy.kohesive.injekt.**`, `okhttp3.**`, `okio.**`, `org.jsoup.**`, `androidx.preference.**`, `androidx.webkit.**` ve `rx`/`io.reactivex` için `-keep interface` kuralları eksikti.

5. **Eklenti Portalı (`PluginPickerScreen`) Manga Sekmesi Arama Akışı:**
   - Kullanıcı `"Manga kaynaklarında ara"` kutusuna `"Berserk"` yazdığında klavye `ImeAction.Done` yalnızca klavyeyi kapatıyor ve kaynak adı filtrelemesi `"Berserk ile eşleşen eklenti yok"` gösteriyordu; doğrudan `MangaBrowseScreen` aramasına geçiş bulunmuyordu.

---

## 2. Tamamlanan Görevler

- [x] **Gerçek Brotli + Ağ Katmanı Açma (`WireDecompressionInterceptor`):** `org.brotli:dec:0.1.2` eklendi, `okhttp3.brotli.Brotli` gerçek `BrotliInputStream` ile bağlandı, `okhttp3.zstd.Zstd` güvenli `gzip` delegasyonuna alındı ve `NetworkHelper.client` üzerine `KeiSource` kısıtlarıyla tam uyumlu `WireDecompressionInterceptor` eklendi.
- [x] **DDoS-Guard & Cloudflare Kurtarma:** `CloudflareInterceptor` eklenti içi `DDoSGuardInterceptor`'ın önce çalışmasına izin verecek, çözülmemiş GET `403`/`503` Cloudflare ve DDoS-Guard korumalarında istek `User-Agent`'ı ile WebView açacak ve çözülen çerezleri `WebViewCookieJar`'a senkronlayacak şekilde güncellendi. `DdosGuardKiller` içindeki tek kullanımlık `check.js` yolu önbelleklemesi kaldırıldı.
- [x] **Aynı Repo İçi `main`/`builds` APK Aday Çözümleme & İmza Güveni:** `buildMangaApkDownloadCandidates` ve `MangaExtensionLoader.downloadExtension` her adayı döngü içinde ZIP + `matchesMangaApkMetadata` kontrolünden geçirerek `builds` dalı geride kaldığında `main` dalındaki gerçek imzalı `v1.6.28` (`106028`) APK'yı indirip kurar. `TrustManager`'a `Kitsugi-Manga-Plugins` sertifika SHA-256 özetleri eklendi.
- [x] **Kirli Domain Temizliği & Mirror Güvenliği:** `SourceConfigStore` kullanıcı tarafından açıkça girilmeyen (`KEY_USER_DOMAIN_EXPLICIT == false`) kanonik dışı `Manga-TR` domain kayıtlarını otomatik temizler; `SourceMirrorResolver.applyStoredDomainPreference` `source_<id>` içindeki kirli domain kaydını siler; `isReachable` artık `403`/`503` kabul etmez; `MangaTrCompat.KNOWN_DOMAINS` squatter alan adlarından arındırıldı.
- [x] **Soğutma Kilidi, Zaman Aşımı ve Hata Sınıflandırması:** `MangaSourceStateStore` soğutma için `failureStreak >= 3` şartı arar; `MangaSearchCoordinator` tüm kaynaklar soğutmadaysa etkin kaynaklara geri düşer (`"0 kaynak"` oluşmaz); eklenti yüklendiğinde eski `CaptchaRequired`/`RateLimited` durumu sıfırlanır; `MihonSourceWrapper` `TimeoutCancellationException`'ı `SocketTimeoutException`'a çevirir; `SourceFailureClassifier` `LinkageError` / host sözleşme hatalarını yanlışlıkla CAPTCHA (`KTS-SRC-9003`) olarak sınıflandırmaz.
- [x] **Eklenti Ömür Döngüsü (Yükleme / Silme / Güncelleme) & R8 ProGuard:** `installExtensionFromUri`, `deleteExtension`, `getExtensionIdForSource` ve `withMangaApkRollback` Android 14+ salt-okunur dosya izinleri ve bellek önbelleği temizliğiyle sağlamlaştırıldı; tüm eklenti host paketleri için `-keep interface` kuralları eklendi.
- [x] **Eklenti Portalı Manga Araması:** `PluginPickerScreen` Manga sekmesinde klavye `ImeAction.Search` ve `"Manga Ara: \"...\""` aksiyonu ile doğrudan `MangaBrowseScreen` aramasına yönlendirme eklendi.
- [x] **Birim ve Emülatör Testleri:** `WireDecompressionInterceptorTest`, `MangaTrPolicyTest`, `SourceFailureClassifierTest` birim testleri ile `MangaTrPublishedApkTest.publishedV28RunsThroughHostBridge` (`v1.6.28` gerçek APK + Brotli/Gzip sıkıştırmalı yanıt testi) eklendi; `MyListContentPageTest` derleme uyumsuzluğu giderildi.

---

## 3. Değişen ve Eklenen Dosyalar Listesi (28 Kod/Yapılandırma Dosyası)

1. `.github/workflows/mangatr-emulator.yml`
2. `app/build.gradle.kts`
3. `app/proguard-rules.pro`
4. `app/src/androidTest/java/com/kitsugi/animelist/data/manga/MangaTrPublishedApkTest.kt`
5. `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/mylist/MyListContentPageTest.kt`
6. `app/src/main/java/com/kitsugi/animelist/core/network/CloudflareInterceptor.kt`
7. `app/src/main/java/com/kitsugi/animelist/core/security/TrustManager.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaApkTransaction.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaExtensionLoader.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaSearchCoordinator.kt`
11. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaSourceRepository.kt`
12. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaSourceStateStore.kt`
13. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaTrCompat.kt`
14. `app/src/main/java/com/kitsugi/animelist/data/manga/MangaTrPolicy.kt`
15. `app/src/main/java/com/kitsugi/animelist/data/manga/MihonSourceWrapper.kt`
16. `app/src/main/java/com/kitsugi/animelist/data/manga/SourceConfigStore.kt`
17. `app/src/main/java/com/kitsugi/animelist/data/manga/SourceFailureClassifier.kt`
18. `app/src/main/java/com/kitsugi/animelist/data/manga/SourceMirrorResolver.kt`
19. `app/src/main/java/com/kitsugi/animelist/ui/app/MangaViewModel.kt`
20. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt`
21. `app/src/main/java/com/lagradost/cloudstream3/network/DdosGuardKiller.kt`
22. `app/src/main/java/eu/kanade/tachiyomi/network/NetworkHelper.kt`
23. `app/src/main/java/eu/kanade/tachiyomi/network/interceptor/WireDecompressionInterceptor.kt` *(yeni)*
24. `app/src/main/java/okhttp3/brotli/Brotli.kt`
25. `app/src/main/java/okhttp3/zstd/Zstd.kt`
26. `app/src/test/java/com/kitsugi/animelist/data/manga/MangaTrPolicyTest.kt`
27. `app/src/test/java/com/kitsugi/animelist/data/manga/SourceFailureClassifierTest.kt`
28. `app/src/test/java/com/kitsugi/animelist/data/manga/WireDecompressionInterceptorTest.kt` *(yeni)*

---

## 4. Doğrulama Durumu

- `check_localization_resource_parity.py`: **GEÇTİ** (18.452 string, 4 dizi tam senkron, `+0` yeni XML ID).
- `check_taxonomy_master.py`: **GEÇTİ** (114 sözlük girişi, 79 resmî Shikimori türü, 0 uyarı).
- `check_search_catalog.py` & `gen_taxonomy_catalog.py --check`: **GEÇTİ**.
- `check_bangumi_meta_tag_coverage.py`: **GEÇTİ** (158/158, eksik: 0).
- GitHub Actions (`arena/16a166e4-kitsugi-beta` üzerinde Debug + R8 Release APK derlemesi, DEX doğrulayıcı ve Android 14 API 34 emülatöründe `Manga-TR` v1.6.24 / v1.6.26 / v1.6.28 entegrasyon testleri): çalışıyor / doğrulamada.
