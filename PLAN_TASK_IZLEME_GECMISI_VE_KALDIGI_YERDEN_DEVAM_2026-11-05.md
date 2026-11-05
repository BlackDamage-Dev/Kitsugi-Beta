# Plan / Task — İzleme geçmişi ve kaldığı yerden devam

**Tarih:** 2026-11-05  
**Dal:** `arena/ff998af1-kitsugi-beta`  
**Temel commit:** `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`

## Hedef

İzleme geçmişi; başlık, sezon ve bölüm, içerik kimlikleri, eklenti ve video kaynağı, gerekli oynatma bilgileri ve konumu kalıcı olarak saklamalı. Geçmişten devam edildiğinde kayıtlı sağlayıcı yeniden sorgulanmalı, uygun güncel akış bulunmalı ve konum koruma ayarı açıksa video kaldığı yerden başlamalı. Sağlayıcı veya kaynak bulunamazsa başka bir video sessizce seçilmemeli; kullanıcı uyarılarak alternatifler gösterilmeli.

## Yapılan işler

- [x] Geçmiş modeline geriye uyumlu eklenti, kaynak izi ve içerik metaverisi alanları eklendi; eski kayıtlar okunabilir bırakıldı.
- [x] Kaynak ve içerik kimlikleri ayrıştırıldı; sezon/bölüm bazlı kayıt, ilerleme, silme ve eski sayısal kimlik çakışması kuralları düzenlendi.
- [x] Başlangıç yüklemesi sırasında yapılan işlemler sırasıyla yeniden uygulanacak şekilde düzenlendi. Gecikmeli yazma, silme ve anında kaydetme arasındaki yarışlar nesil kontrolüyle korundu; çıkışta bekleyen ilerleme yazımı tetikleniyor.
- [x] Stremio manifest kimliği ve CloudStream eklenti/API/sayfa bilgileri kaynaklara bağlandı. Aynı isimli eklentiler kimlikleriyle ayrılıyor; eski bağlantı ve header'lar yerine sağlayıcıdan gelen güncel veriler kullanılıyor.
- [x] Torrent hash/dosya indeksi, HTTP kaynak izi ve uygun olduğunda ad, kalite ve ses bilgileriyle eşleştirme eklendi. Farklı video kimliği içerebilen sorgu parametreleri korunuyor; geçici kimlik doğrulama parametreleri ve yerel P2P adresleri kalıcı URL izi olarak kullanılmıyor. Belirsiz eşleşmelerde otomatik seçim yapılmıyor.
- [x] Geçmiş ekranı, akış seçimi ve tam ekran oynatıcı arasında kayıt, güncel konum ve otomatik devam köprüsü kuruldu. Sıfıra sarma, tamamlanan videolar ve ilk yükleme sırasındaki durumlar ele alındı.
- [x] Bölüm/kaynak değişiminde eski bölümün konumunun yeni bölüme yazılması engellendi; başarısız bölüm aramasında mevcut liste korunuyor ve kullanıcı bilgilendiriliyor.
- [x] TV geçmiş navigasyonu, oyuncu metaverisi ve harici oynatıcı dönüşleri bağlandı. Harici oynatıcı konum bildirmediğinde konum uydurulmuyor; P2P izin akışı korunuyor.
- [x] Geçmiş kuralları, kaynak eşleştirme, depolama yarışları, ilk yükleme ve eklenti kimliği için regresyon testleri eklendi; CI test filtresine bağlandı. Harici oynatıcı sözleşmesi için cihaz testi dosyası eklendi.

## Değişen dosyalar

### Üretim kodu ve kaynaklar

- `.github/workflows/android-build.yml`
- `app/src/main/java/com/kitsugi/animelist/core/player/ExternalPlayerLauncher.kt`
- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt`
- `app/src/main/java/com/kitsugi/animelist/data/local/WatchHistoryManager.kt`
- `app/src/main/java/com/kitsugi/animelist/data/local/WatchHistoryRules.kt` **(yeni)**
- `app/src/main/java/com/kitsugi/animelist/data/local/WatchHistorySource.kt` **(yeni)**
- `app/src/main/java/com/kitsugi/animelist/data/local/WatchHistoryStore.kt`
- `app/src/main/java/com/kitsugi/animelist/data/model/WatchHistoryEntry.kt`
- `app/src/main/java/com/kitsugi/animelist/data/repository/AddonStreamRepository.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamActivity.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamModels.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamResultsList.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/TvRootScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/stream/TvStreamScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

### Yeni testler

- `app/src/test/java/com/kitsugi/animelist/data/local/WatchHistoryRulesTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/local/WatchHistorySourceTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/local/WatchHistoryStoreTest.kt`
- `app/src/test/java/com/kitsugi/animelist/data/local/WatchHistoryManagerTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/stream/StreamModelsTest.kt`
- `app/src/androidTest/java/com/kitsugi/animelist/core/player/ExternalPlayerResultContractTest.kt`

## Doğrulama

- **40/40 JUnit testi geçti:** Kotlin 2.0.21, gerçek JUnit 4.12, Gson, coroutine kitaplığı ve Android API 36 sınıf tanımlarıyla izole koşum. Bazı Android çalışma zamanı, veritabanı ve sağlayıcı bağımlılıkları taklit edildi; bu, Gradle/cihaz testi değildir.
- **27/27 oynatıcı politika kontrolü geçti:** `scripts/run_player_policy_checks.sh`; saf Kotlin koşumu, Android/JNI veya cihaz testi değildir.
- Sağlayıcı kimlikleri, iki ID rotasının tamamlanması, iptal ve fallback için 8; harici oynatıcı sonuçları için 4 ek senaryo taklit edilmiş bağımlılıklarla denendi.
- Kotlin PSI sözdizimi ve seçili çağrı imzası denetimleri, kaynak XML kontrolleri ve `git diff --check` başarılı.
- **Tam Gradle/Android derlemesi ve gerçek cihaz/emülatör testi yapılmadı.** Yeni Android enstrümantasyon test dosyası yazıldı, ancak cihazda çalıştırılmadı.

## Kalan doğrulama

1. Tam SDK ve bağımlılıkların bulunduğu ortamda `./gradlew :app:testFossDebugUnitTest` ve uygun debug/release derlemelerini çalıştır.
2. Telefon ve TV cihazında eski/yeni geçmiş kayıtlarını, farklı sezonları, duraklatma/sıfıra sarma, uygulamayı kapatıp açma ve bölüm/kaynak değişimini doğrula.
3. Etkin/devre dışı/silinmiş eklenti, süresi dolan URL, belirsiz kaynak ve P2P izin durumlarını gerçek sağlayıcılarla dene.
4. Konum döndüren ve döndürmeyen harici oynatıcılarla dönüşü; cihaz üzerinde `ExternalPlayerResultContractTest` testini doğrula.
