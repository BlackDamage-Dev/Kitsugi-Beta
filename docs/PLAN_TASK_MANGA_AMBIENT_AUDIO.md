# Plan/Task — Manga Okuyucu Ortam Sesi ve Çevrimdışı Kuyruk

**Durum:** Uygulandı; Gradle derlemesi ve cihaz doğrulaması bekliyor.  
**Dal:** `arena/a2127c13-kitsugi-beta`  
**Tarih:** 2026-11-07

## Amaç

Manga okuyucuda eser adına öncelik veren ambiyans müziği araması, seçilen ses kataloglarından birleştirilmiş oynatma kuyruğu, daha kullanılabilir indirme kontrolleri ve sağlayıcı destekliyorsa çevrimdışı dinleme sağlamak.

## Gereksinimler ve tamamlanan işler

- [x] Önce `MangaDetails.title` ile seçili kataloglarda arama yap. Beşten az yeni/uygun başlık eşleşmesi varsa tür ve hikâye ipuçlarından yerel bağlam sorgusuna geç.
- [x] Sonuçları sağlayıcılar arasında birleştir; yinelenen kayıtları ayıkla ve son dinlenenleri aşağı sırala.
- [x] Openverse (Freesound ve Wikimedia Audio), Internet Archive, Audius, Wikimedia Commons, ccMixter, Free To Use ve Radio Browser adaptörlerini ekle. Jamendo kapsam dışı.
- [x] Bilinen ses biçimlerini tanı; çevrimiçi oynatma ve sağlayıcının doğrudan HTTPS dosya indirme uygunluğunu ayrı tut.
- [x] İndirme ilerlemesi/durumları, kalıcı URI ile geri yükleme, çevrimdışı oynatma ve destek varsa Media3 Transformer ile en iyi çaba AAC dönüştürmesi ekle. Dönüştürme başarısızsa özgün dosyayı sakla.
- [x] İndirme düğmesini 48 dp dokunma alanı ve belirgin durum simgeleriyle büyüt.
- [x] Bir parçadaki **Çal** işlemi mevcut parçayı/kuyruğu tek parçayla değiştirir. **Sıraya ekle** mevcut çalmayı kesmeden parçayı kuyruğa ekler; kuyruk boşsa ilk eklenen parça başlatılır.
- [x] Manga okuyucu ve TV okuyucuya ortak ses panelini, oynatıcıyı ve Türkçe/İngilizce metinleri bağla.
- [x] Sağlayıcı ayrıştırma, format, başlık/bağlam sıralaması, tekrar/dedup ve yakın zamanda çalınanlar için birim testleri ekle.
- [x] Kaynak/uyumluluk ve sınırlamaları `docs/AMBIENT_AUDIO_RESEARCH.md` içinde belgele.

## Kabul ölçütleri

1. Arama manga başlığını önceliklendirir; yeterli yeni başlık sonucu yoksa bağlam sonuçlarını ekler.
2. Seçili kaynaklardan gelen eşleşmeler tekilleştirilmiş, yakın zamanda dinlenenleri aşağı alan tek kuyrukta toplanır.
3. Doğrudan **Çal** önceki çalmayı/kuyruğu değiştirir; ayrı **Sıraya ekle** eylemi mevcut çalmayı kesmez.
4. İndirme düğmesi rahat dokunulabilir, ilerleme ve tamamlanma durumu anlaşılırdır; Radio Browser indirme sunmaz.
5. AAC dönüştürme yalnızca cihaz ve giriş biçimi izin verdiğinde başarılı sayılır; aksi durumda özgün dosya korunur.
6. Jamendo sorgulanmaz; bilinmeyen lisans bilgisi tek başına oynatmayı engellemez ve tüm içeriklerin sınırsız kullanımda olduğu iddia edilmez.

## Değişiklik alanları

- `app/src/main/java/com/kitsugi/animelist/data/ambient/` — modeller, kaynak adaptörleri, arama planlayıcısı, formatlar ve indirme yöneticisi.
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/` — ortam sesi paneli, Media3 oynatıcı ve okuyucu entegrasyonu.
- `app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaReaderScreen.kt` — TV okuyucu entegrasyonu.
- `app/src/test/java/com/kitsugi/animelist/data/ambient/` — ayrıştırma, biçim ve planlayıcı testleri.
- `app/build.gradle.kts` — Media3 Transformer bağımlılığı.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — iki dilde UI metinleri.
- `docs/AMBIENT_AUDIO_RESEARCH.md` — araştırma ve sınırlamalar.

## Doğrulama ve açık sınırlamalar

- Kotlin sözdizim taraması: 19 dosya, hata yok.
- XML, Türkçe/İngilizce kaynak eşleşmesi ve boşluk kontrolleri: başarılı.
- Gradle derlemesi ve birim testleri: bu çalışma alanında Java/`JAVA_HOME` bulunmadığından çalıştırılamadı.
- Gerçek sağlayıcı API istekleri, cihazda ses çalma/indirme ve AAC kod dönüştürme doğrulaması yapılmadı.
- Manga modelinde doğrulanmış dış katalog kimlikleri veya alternatif ad listesi yok; arama mevcut `MangaDetails.title` değerini kullanıyor.
