# PLAN / TASK — MDBList Detay Sayfası Puanlarının Yüklenmesi

**Tarih:** 2026-11-05  
**Branch:** `arena/c9fc2826-kitsugi-beta`  
**Durum:** Kod düzeltmeleri uygulandı; derleme ve testler bu ortamda Java/JDK bulunmadığından doğrulanamadı.

## İstek

Kullanıcının MDBList API anahtarı kaydedilmiş ve entegrasyon açık olmasına rağmen harici puanların hem API arama sonucundan açılan hem de yerel kütüphanedeki medya detay sayfalarında yüklenmemesi/görünmemesi sorununu araştırıp düzeltmek.

## İnceleme bulguları

- İki detay ViewModel'i de MDBList ayarlarını okuyordu; ancak detay ekranı açıkken entegrasyon durumu veya API anahtarı değiştiğinde istek otomatik yenilenmiyordu.
- Farklı katalogların kimlikleri bazı akışlarda aynı `malId` alanına yazılabildiğinden MAL, AniList, Bangumi, Kitsu, TMDB, Simkl ve Shikimori kimliklerini kaynak adına göre ayırmak gerekiyordu.
- Detay metadata isteği ile MDBList isteği paralel başlıyordu. IMDb dış bağlantısı/TMDB kimliği detay cevabıyla sonradan gelirse ilk puan isteği bu kimlikleri kaçırabiliyordu.
- İstemci IMDb kimliğini tercih ediyor, fakat boş/başarısız IMDb yanıtından sonra TMDB kimliğiyle yeniden denemiyordu.
- Puan ayrıştırıcısı yanıt biçimlerinin yalnızca bir bölümünü kapsıyordu; nesne biçimi ve eski alan adları da desteklenmeli.
- UI kartı geçerli puan yokken veya yükleme sürmüyorken bilerek gizleniyor. Kartın StateFlow ve ayar görünürlük bağlantıları incelendi; asıl eksik, karta ulaşan puan verisi/yenileme akışıydı.

## Uygulanan değişiklikler

1. Her iki detay ViewModel'inde MDBList açık/kapalı ve API anahtarı değişikliklerini izleyip aktif içerik için puan sorgusunu yenile.
2. İptal ve nesil/istek anahtarı kontrolleriyle eski isteğin yeni detay ekranının state'ini ezmesini engelle.
3. Kaynak kimliklerini doğru namespace üzerinden çöz; IMDb/TMDB kimliği ilk çözümlemede bulunamazsa detay metadata tamamlanınca sınırlı süreyle yeniden dene.
4. MDBList sorgusunda IMDb'yi tercih et; yanıt boş veya erişilemezse TMDB sorgusuna düş.
5. Array/object biçimli `ratings`/`scores`, eski üst seviye alanlar ve servis alias'larını ayrıştır; sıfır puanları eksik veri kabul et.
6. Fallback seçimi, farklı yanıt biçimleri ve boş listeyle birlikte gelen eski puan alanları için regresyon testleri ekle.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListClientTest.kt`

## Doğrulama ve kalan iş

- [x] `git diff --check` başarılı.
- [x] Parser ve IMDb→TMDB fallback regresyon testleri eklendi.
- [ ] `./gradlew :app:testFossDebugUnitTest --tests com.kitsugi.animelist.data.remote.MdbListClientTest` çalıştırılsın.
- [ ] Uygulama derlemesi ve cihazda MDBList anahtarıyla API detay + yerel kütüphane detay akışları doğrulansın.

**Test engeli:** Gradle komutu denendi ancak ortamda `java`/JDK bulunmadığı için çalışmadı: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` Bu nedenle testlerin başarılı olduğu iddia edilmemektedir.
