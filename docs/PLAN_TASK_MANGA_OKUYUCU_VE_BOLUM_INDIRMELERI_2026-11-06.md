# Plan / Task — Manga okuyucu ve bölüm indirmeleri

Tarih: 6 Kasım 2026
Dal: `arena/e1952a2f-kitsugi-beta`
Başlangıç commit'i: `b78cbc4cdc39808cb0ec475026edef6b464b71ed`

## Amaç

Okuyucunun dört okuma modunu ve tam ekran davranışını düzeltmek; bölüm listesini tek arama alanı/alttan açılan sıralama paneliyle düzenlemek; eklentiye ait bölüm görsellerini kaynak sırasıyla, manga/bölüm klasörlerine indirip bağımsız Manga indirmeleri alanından çevrimdışı okutmak.

## Tamamlanan kod görevleri

### 1. Okuyucu
- [x] RTL / LTR / dikey sayfalama / kesintisiz Webtoon modlarını ayrı simge ve rozetlerle göster.
- [x] Dikey modda gerçek VerticalPager kullan; dokunma bölgelerini üst/alt eksenine taşı.
- [x] Webtoon sayfalarını doğal görsel oranıyla göster; aşağı/yukarı akışı koru.
- [x] Mod değiştirirken mevcut sayfayı aktif okuyucuya aktar; pasif okuyucunun eski ilerlemeyi ezmesini önle.
- [x] Android sistem durum ve gezinme çubuklarını gizleyen ayrı tam ekran düğmesi ekle.
- [x] Kalıcı, varsayılan açık otomatik tam ekran başlatma tercihi ekle.
- [x] Odak/uygulamaya dönüşte tam ekranı yeniden uygula; çıkışta önceki sistem çubuğu/ekran yönünü geri yükle.
- [x] Ayar seçeneklerinin dar ekranlarda satır kaydırmasını ve panelin kaydırılabilmesini sağla.

### 2. Bölüm listesi
- [x] Tek bölüm arama alanı ve tek arama/okundu filtresi hattı oluştur.
- [x] Eski → Yeni / Yeni → Eski / Son güncellenen seçeneklerini alttan açılan panele taşı.
- [x] Sayısal ve ondalıklı bölüm sıralamasını koru; kaynak gezinme listesini değiştirme.
- [x] Kompakt bölüm görünümü seçeneğini ekle.

Not: Bu dalın başlangıcındaki MangaDetailScreen, kullanıcı ekran görüntüsündeki arama/sıralama bileşenlerini içermiyordu. Bu nedenle mevcut dalda tek arama öğesi ve uygun alt panel oluşturuldu.

### 3. Kaynak araştırması ve eklenti köprüsü
- [x] Mihon Downloader / DownloadProvider ve Kotatsu DownloadWorker yaklaşımını incele.
- [x] Keiyoushi Mangitto, MadaraBase ve MangaThemesia sayfa akışlarını incele.
- [x] Yerel Mihon/Kotatsu ve Svelte/Inertia adaptörlerini incele.
- [x] Genel URL indiricisi yerine `fetchPageList → fetchImageUrl → source.getImage` akışını kullan.
- [x] Eklentinin özel istemci, Referer, çerez ve interceptor davranışlarını koru.
- [x] Bölüm dönüşümünde kaybolan `SChapter.memo` bilgisini tarih/scanlator/numarayla birlikte koru.

Araştırma kaynakları ve commit bağlantıları: `docs/research/manga-downloads-2026-11-06.md`.

### 4. Manga bölüm indirmeleri
- [x] Bölüm satırlarına indirme/duraklatma/hata/hazır durumlu düğmeler bağla.
- [x] WorkManager üzerinden kalıcı, bölüm başına benzersiz iş oluştur.
- [x] Foreground dataSync bildirimi ve doğrudan Manga sekmesine açılan bildirim bağlantısı ekle.
- [x] En fazla iki eşzamanlı bölüm; bölüm içinde sıralı sayfa indirme uygula.
- [x] Manga, kaynak/dil ve bölüm kimliklerini ayır; aynı adların çakışmasını önle.
- [x] `Pictures/Kitsugi/Manga/` altında kaynak → manga → bölüm klasörlerini otomatik oluştur.
- [x] Manga adı/bölüm numarası/sıfır dolgulu sayfa numarası içeren dosya adları oluştur.
- [x] Bölüm klasörlerine dosya yöneticisi için sayısal sıralama öneki ekle.
- [x] Unicode adları koru, yasak karakterleri temizle ve UTF-8 dosya adı uzunluklarını sınırla.
- [x] Android 10+ MediaStore/IS_PENDING; Android 8–9 izin/.part/MediaScanner akışı uygula.
- [x] AtomicFile manifesti ile yerel URI, sıra, boyut, iş kimliği ve tamamlanma durumunu sakla.
- [x] Duraklat/devam et, eksik sayfaları tamamlama, ağ hatasında sınırlı yeniden deneme ve silme akışlarını ekle.
- [x] Eksik/tekrarlı/pending sayfalar varken bölümü tamamlanmış işaretleme.
- [x] Silmeden önce işi iptal et ve bölüm kilidini bekle; yarım MediaStore kayıtlarını temizle.

### 5. Format / bellek güvenliği
- [x] Geçerli JPEG/PNG/GIF dosyalarını gerçek uzantısıyla koru.
- [x] WebP ve cihazın çözebildiği diğer biçimleri gerçekten PNG'ye kodla.
- [x] HTML, boş yanıt ve çözülemeyen biçimleri başarılı görsel gibi kaydetme.
- [x] Ham veriyi akışla diske yaz; tüm bölümü RAM'e alma.
- [x] 64 MB girdi, en fazla 32 MP/uygun heap dönüşüm sınırı ve boş geçici alan kontrolü uygula.
- [x] Büyük bitmap dönüşümlerini tekilleştir.

### 6. İndirmeler ve çevrimdışı okuma
- [x] Genel Resimler'den ayrı Manga sekmesi ekle.
- [x] Manga/kaynak grupları, sayısal bölüm sırası, sayfa ilerlemesi ve toplam boyutu göster.
- [x] Çevrimdışı oku, devam et, klasörü göster ve onaylı bölüm silme eylemleri ekle.
- [x] Tamamlanan dosyaları eklenti/ağ gerektirmeyen yerel MangaSource ile okut.
- [x] Yerel URI'leri okuyucu cache'ine yeniden kopyalamadan göster.
- [x] Online/offline ViewModel anahtarlarını ayır; okuyucudan dönüşte Manga sekmesini koru.

## Doğrulama görevleri

- [x] Değişen Kotlin dosyalarının sözdizimi Tree-sitter ile kontrol edildi.
- [x] XML ayrıştırma, TR/EN kaynak anahtarı ve format argümanı eşleşmeleri kontrol edildi.
- [x] Yeni manga indirme `R.string` referansları ve `git diff --check` kontrol edildi.
- [x] Okuma yönleri, bölüm filtreleme/sıralama, dosya adlandırma/manifest ve memo köprüsü için JUnit testleri eklendi.
- [x] Görsel dönüşümü ve MediaStore akışı için Android cihaz testleri eklendi.
- [x] CI işine manga veri ve okuyucu test filtreleri eklendi.
- [ ] Android/Kotlin derlemesini ve JUnit testlerini çalıştır.
- [ ] Cihaz/emülatörde dönüşüm, MediaStore, izinler, arka plan indirme ve çevrimdışı okuma testlerini çalıştır.
- [ ] Kurulu eklentilerin belirli sürümleriyle canlı kaynak kabul testlerini yap.

**Derleme engeli:** Bu çalışma ortamında Java/JAVA_HOME, Android SDK ve emülatör yok. Gradle komutu Java bulunamadığı için başlayamadı. Sözdizimi/XML kontrolleri derleme, tür denetimi veya cihaz testi yerine geçmez; testlerin geçtiği iddia edilmez.

Test planları:
- `docs/testing/manga-reader-regression.md`
- `docs/testing/manga-downloads-regression.md`

Önerilen komutlar:

```sh
./gradlew :app:compileFossDebugKotlin :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.data.manga.*' \
  --tests 'com.kitsugi.animelist.ui.screens.manga.*'

./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.data.manga.download.MangaPageExportTest
```

## Bilinen sınırlar / sonraki işler

- [ ] CBZ paketleme ve toplu manga/bölüm seçicisi bu kapsamda eklenmedi.
- [ ] Genel Wi-Fi indirme tercihini yeni manga kuyruğuna bağlama: şu anda bağlı herhangi bir ağ kullanılır.
- [ ] Uygulamayı yeniden kurduktan sonra galeride kalan manga klasörlerini kataloğa yeniden içe aktarma.
- [ ] Cihazın kendi decoder'ının desteklemediği biçimler için isteğe bağlı ek codec değerlendirmesi.
- [ ] Hareketli WebP/diğer çok kareli dönüştürme girdilerinin tüm karelerini koruma: şu anda PNG dönüşümü tek kare verir; GIF doğrudan korunur.

Android'in arka plan, kota ve force-stop sınırları geçerlidir. Eski genel resim indirmeleri taşınmadı veya silinmedi. Tamamlanmış dosyalar okuyucu LRU önbelleğinden bağımsızdır.

## Teslim paketi

ZIP; bu dalda başlangıç commit'inden beri değişen/yeni dosyaların tam içeriklerini, bu plan/task dosyasını, araştırma ve test belgelerini içerir. Göreli repo yolları korunur. APK, derleme çıktısı, `.git`, kimlik bilgileri veya çalışma önbellekleri dahil değildir. Paket manifesti dosya listesi/SHA-256 özetlerini; `TESLIM_README.md` uygulama ve doğrulama notlarını içerir.
