# Manga okuyucu / bölüm listesi regresyon kontrolü

## Değişiklikler
- RTL / LTR: zıt yatay sayfa yönleri, ayrı oklar ve RTL/LTR rozetleri.
- Dikey: HorizontalPager yerine gerçek VerticalPager; üst/alt dokunma bölgeleri.
- Webtoon: doğal görsel oranında kesintisiz LazyColumn. Telephoto 0.8 için Coil 2
  ImageRequest ve yerel dosyalar için file URI kullanılır; pinch/double-tap korunur.
- Okuma modu değişiminde aktif okuyucuya mevcut sayfa aktarılır; pasif okuyucunun
  eski konumu ilerlemeyi ezmez. Bölüm sonu kartı geçerli sayfa aralığına sıkıştırılır.
- Sistem çubukları için ayrı tam ekran düğmesi, varsayılan açık kalıcı otomatik
  başlatma tercihi; odak/uygulamaya dönüşte yeniden uygulama, çıkışta eski durumun geri yüklenmesi.
- Bu dalın başlangıcındaki MangaDetailScreen'de ekran görüntüsündeki arama/sıralama
  bileşenleri yoktu. Tek `chapter_search` öğesi ve KitsugiSheetOrDialog tabanlı
  alttan açılır sıralama paneli eklendi. Tek filtreleme hattı kaynak listeyi değiştirmez.

## Otomatik testler
`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

- MangaChapterListOptionsTest: sayısal/ondalıklı sıralama, eksik numaraların adlardan
  çıkarılması, tarih sıralaması, birleşik arama/okundu filtresi ve kaynak sırasının korunması.
- MangaReaderTapActionTest: RTL/LTR yönleri, dikey üst/alt bölgeler, Webtoon'da
  dokunmayla yanlış sayfaya atlamama ve dört modun merkez menü davranışı.

Arena ortamında Java ve Android SDK bulunmadığından Gradle testi çalıştırılamadı
(`JAVA_HOME is not set and no 'java' command could be found`). Değişen Kotlin
dosyaları Tree-sitter ile sözdizimi bakımından, yeni kaynaklar XML olarak kontrol
edildi. Bu kontroller derleme veya cihaz testi yerine geçmez.

## Cihazda doğrulanacaklar
1. Bir bölüme gir: sistem durum çubuğu ve üç tuş/gesture navigasyon çubuğu gizlensin.
   Kenardan kaydırınca çubuklar geçici görünsün; okuyucunun menüleri ayrıca merkezden açılsın.
2. Tam ekran düğmesiyle aç/kapat. Ayarlardaki otomatik tam ekranı kapat; okuyucudan
   çıkıp dönünce tercih korunsun. Tekrar açınca otomatik gizleme geri gelsin.
3. Ayar panelini aç/kapat, uygulamayı arka plana al/geri dön; tam ekran korunsun.
   Okuyucudan geri çıkınca uygulamanın sistem çubukları ve ekran yönü eski haline dönsün.
4. Dört modun ikon/rozetlerini, yatay ve dikey hareketleri dene. 6. sayfada modlar
   arasında geç; bölüm/sayfa ilerlemesi sıfırlanmasın. Kaydırıcı ve sonraki/önceki bölüm çalışsın.
5. Çok uzun bir Webtoon görselini aşağı/yukarı kaydır; içerik tek ekrana küçülmesin,
   sayfalar doğal yükseklikte dizilsin. Pinch/double-tap sonrası kaydırmayı da dene.
6. Bölüm listesinde yalnızca bir arama alanı olsun. Aramayı temizle, okundu filtresini
   değiştir. Sıralama ikonundan alttan açılır panel gelsin; seçili seçenek işaretli olsun.
   Seçim/scrim/geri ile kapanabilsin. Eski/yeni/tarih sıralaması ve kompakt görünüm çalışsın.
7. Dar telefon, yatay ekran ve tablette ayar rozetleri satır kaydırsın; panel içerikleri
   kaydırılabilsin. Android gesture ve üç tuşlu navigasyonu ayrı ayrı kontrol et.
