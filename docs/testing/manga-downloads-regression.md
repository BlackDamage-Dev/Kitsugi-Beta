# Manga indirmeleri — doğrulama planı

## Komutlar

```sh
./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.data.manga.*' \
  --tests 'com.kitsugi.animelist.ui.screens.manga.*'
./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.data.manga.download.MangaPageExportTest
```

## Eklenen testler

- `MangaDownloadTest`: sıralı sayfa adları; 1200 sayfada doğal dosya sırası;
  ondalıklı numaralar; Unicode/UTF-8 ve path temizliği; kaynak/dil/bölüm kimlikleri;
  eksik, pending, tekrarlı veya boşluklu manifesti tamamlanmış saymama; JSON round-trip.
- `MihonChapterBridgeTest`: eklentinin ihtiyaç duyduğu mangaSlug/memo'nun, tarih ve
  scanlator ile bölüm/sayfa köprüsünde korunması.
- `MangaPageExportTest` (cihaz): uzun PNG'nin değiştirilmemesi, WebP'nin gerçekten
  PNG'ye dönüştürülmesi, HTML'in görsel diye kaydedilmemesi, API 29+ MediaStore
  reserve → journal → publish → read → delete akışı (geniş depolama izni gerektirmez).
- Var olan CI derleme işine manga veri ve okuyucu test filtreleri eklendi.

## Bu ortamda yapılanlar / yapılmayanlar

- Gradle komutu denendi, fakat Java bulunmadığı için başlayamadı:
  `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
- Android SDK ve emülatör de bulunmuyor. JUnit/cihaz testlerinin geçtiği iddia edilmez.
- Kotlin sözdizimi Tree-sitter ile, XML dosyaları XML ayrıştırıcıyla kontrol edilir;
  kaynak isimleri/format argümanları ve `git diff --check` ayrıca kontrol edilir.
  Bunlar Kotlin tür denetimi, Android derlemesi veya canlı eklenti testi yerine geçmez.
- GitHub Actions çalıştırmak için commit/push yapılmadı; kullanıcı isterse mevcut
  dal üzerinden CI'da derleme/test yapılabilir.

## Cihaz matrisi ve manuel kabul adımları

1. Android 8/9: depolama iznini önce reddet, sonra ver; ret halinde boş/başarılı kayıt
   oluşmasın. Android 10–16: geniş galeri okuma izni istemeden MediaStore'a kaydetsin.
2. Mangitto, Madara ve Themesia tabanlı birer kurulu eklenti ve Kotatsu kaynağı ile
   gerçek bölüm indir. Preview değil gerçek sayfa olsun. Kaynak Referer/çerezleri ve
   chapter memo'su korunsun. Tekrarlı bölüm adlarında manga/kaynak/dil karışmasın.
3. 12.5 gibi bölüm numarası, Türkçe/Japonca/emoji manga başlığı, slash/colon içeren
   başlık, 1000+ sayfalı sahte test kaynağıyla adları/sıralamayı kontrol et.
4. Galeri/dosya yöneticisinde Pictures/Kitsugi/Manga altında manga ve bölüm
   klasörleri otomatik oluşsun. Sayfalar 0001, 0002… olsun; genel Resimler sekmesinde
   görünmesin. Ayrı Manga sekmesinde bölüm/sayfa/MB/ilerleme görünsün.
5. Bölüm ortasında duraklat, devam ettir; sağlam sayfalar yeniden indirilmesin.
   Bildirimden iptal, ağ kesilmesi, süreç ölümü ve cihaz yeniden başlatma denensin.
   Android force-stop sonrası uygulamayı açmadan işlerin devam etmesi beklenmez.
6. İşi aynı düğmeye hızlı basarak tekrarla; aynı bölüm için paralel/çift dosya oluşmasın.
   Duraklat → devam et → sil yarışlarını dene; silme sırasında worker çıktı yazmasın.
7. Bir sayfa indirilirken süreç öldür: pending satır galeride görünmesin; yeniden
   denemede aynı ad/URI temizlensin veya tekrar kullanılsın. Bölüm silme pending satırı
   da kaldırsın. Galeriden bazı sayfaları sil; eksik sayfaları yeniden indir.
8. Uçak modu + eklentiyi kaldırma sonrası İndirmeler > Manga > Çevrimdışı oku:
   okuyucu açılsın, ağ çağrısı yapılmasın. İleri/geri bölüm yalnız indirilmiş bölümlere
   geçsin. Okuyucu cache'ini temizle; kalıcı indirme etkilenmesin. Geri dönünce Manga
   sekmesi ve önceki tam ekran/sistem çubuğu durumu korunsun.
9. WebP/JPEG/PNG/GIF ve cihazın desteklediği AVIF testleri; HTML challenge/boş stream,
   64 MB sınırı, dönüşümde 32 MP sınırı, dolu depolama ve izin iptali denensin.
   Hatalı veya yarım bölüm hiçbir zaman "çevrimdışı hazır" olmasın.
10. Video, altyazı, genel resim indirme/açma/silme ve önceki okuyucu yön/tam ekran
    testlerini tekrar çalıştır. Manga silme diğer içeriklerin dosyalarını silmesin.
