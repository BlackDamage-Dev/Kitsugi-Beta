# Manga bölüm indirmeleri — kaynak araştırması ve uygulama

## İncelenen açık kaynaklar

Araştırma GitHub API üzerinden yapıldı; kaynak sitelere istek atılmadı. Belirli bir
cihazda yüklü APK sürümleri bu çalışma ortamında mevcut değil. Bu nedenle tüm eklenti
sürümlerinin veya canlı sitelerin çalıştığı iddia edilmez.

- [Mihon Downloader](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt):
  sayfaları liste sırasıyla yeniden indeksliyor; eksik `imageUrl` için source çözümlemesi,
  kaynak istemcisiyle görsel alma, sayısal sıfır dolgulu dosya adları, yarım indirmeleri
  tekrar kullanma ve bölümün tamamlandığını doğrulama yaklaşımı incelendi.
- [Mihon DownloadProvider](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadProvider.kt):
  kaynak → manga → bölüm dizinleri, dosya adı temizleme ve bölüm URL hash'iyle
  aynı isimli bölümleri ayırma yaklaşımı incelendi.
- [Kotatsu DownloadWorker](https://github.com/KotatsuApp/Kotatsu/blob/06a0b5829b8d5f214d60d5011d66ec1ccbd5630e/app/src/main/kotlin/org/koitharu/kotatsu/download/ui/worker/DownloadWorker.kt):
  WorkManager, foreground dataSync bildirimi, manga kilidi, sınırlı paralellik,
  yerel çıktı ve indirilmiş bölümleri atlama yaklaşımı incelendi.
- [Keiyoushi Mangitto](https://github.com/keiyoushi/extensions-source/blob/16cde44564d45c3fa9c14371b4d9152606a9821d/src/tr/mangitto/src/eu/kanade/tachiyomi/extension/tr/mangitto/Mangitto.kt):
  `getPageList`, `chapter.memo["mangaSlug"]` ile API'ye gider; `uploads.fileLength`
  sırasıyla WebP CDN adresleri oluşturur. Sadece bölüm slug'ını saklamak yeterli değildir.
- [Keiyoushi MadaraBase](https://github.com/keiyoushi/extensions-source/blob/16cde44564d45c3fa9c14371b4d9152606a9821d/lib-multisrc/madara/src/eu/kanade/tachiyomi/multisrc/madara/MadaraBase.kt):
  modern bölüm URL'si `chapter.memo["mangaPath"]` kullanır. Sayfalar DOM sırası veya
  eklentinin çözdüğü bölüm verisinin sırasıyla gelir. Bu işi indirme katmanında
  tekrar scraping ile uygulamak yerine eklentiye bırakmak gerekir.
- [Keiyoushi MangaThemesia](https://github.com/keiyoushi/extensions-source/blob/16cde44564d45c3fa9c14371b4d9152606a9821d/lib-multisrc/mangathemesia/src/eu/kanade/tachiyomi/multisrc/mangathemesia/MangaThemesia.kt):
  HTML görselleri, JS JSON listeleri ve base64 script içindeki okuyucu verileri aynı
  sayfa-listesi kontratına dönüştürülür.

Bu projelerin kodu topluca kopyalanmadı. Mevcut Kitsugi `MangaSource` kontratı üzerine
ayrı bir çıktı/journal/worker/yerel-okuyucu katmanı yazıldı.

## Kitsugi'de incelenen ve kullanılan akışlar

- `MihonSourceWrapper.fetchPageList → fetchImageUrl → getImage`: özel imageRequest,
  kaynak istemcisi, Referer, çerezler ve interceptor'lar korunur. Genel URL indiricisi
  ile kaynak istemcisi değiştirilmez.
- Köprüde bulunan somut hata: `SChapter.memo` dönüşümde kayboluyordu. `MangaChapter`
  artık kaynak memo'sunu taşır; `MihonChapterBridge` bunu tarih, scanlator ve numarayla
  birlikte geri aktarır. Bu hem okuyucuya hem indirmelere uygulanır.
- `KotatsuMangaSource`: `parser.getPages`, sonra `getPageUrl`; preview küçük resmi
  kullanılmaz. `getImage` parser başlıklarını ve kaynak etiketini kullanır.
- Yerel Svelte/Inertia motorları sayfa verilerini JSON'dan çıkarır. İndirici bu
  ayrıntıları yeniden uygulamaz; sonuçta dönen `MangaSource` sayfa listesini kullanır.
- Süreç yeniden açıldığında kurulu APK/MEX ve Kotatsu kaynakları çözümlenir;
  bölüm listesi yeniden alınarak kaynağın geçici chapter/page cache'leri ve memo'su
  yeniden oluşturulur. Eksik eklenti açıklanır. **Tamamlanmış** indirmeleri okumak için
  eklenti, ağ veya bu kaynak çözümleme adımı gerekmez.

## Depolama ve adlandırma

```
Pictures/Kitsugi/Manga/
  Mangitto_<kaynakKimliği>/
    Chainsaw Man_<mangaKimliği>/
      000000012.500000_Chainsaw Man_Bölüm_12.5_<bölümKimliği>/
        Chainsaw Man_Bölüm_12.5_Sayfa_0001.png
        Chainsaw Man_Bölüm_12.5_Sayfa_0002.png
```

- Kaynak/dil, manga URL'si ve bölüm URL'si kimlikleri isim çakışmasını önler.
- Dosya adları Unicode'u korur, yasak karakterleri temizler, UTF-8 bayt uzunluğunu
  sınırlar. Ondalıklı bölüm numaraları korunur; bilinmeyen numara uydurulmaz.
- Sayfa sırası kaynak listesidir: CDN dosya adına, indirme bitiş zamanına veya URL'ye
  göre tekrar sıralama yapılmaz. En az dört basamaklı sayfa adları dosya yöneticisinde
  sıralamayı da korur. Bölüm klasörünün başında sabit basamaklı sayısal sıralama
  anahtarı bulunur; Manga sekmesinde de bölümler sayısal olarak sıralanır.
- Android 10+: `MediaStore.Images`, doğru MIME/uzantı ve `RELATIVE_PATH` kullanılır.
  Geniş galeri/depolama okuma izni gerekmez. `IS_PENDING` yarım dosyaları galeriden gizler.
- Android 8–9: çalışma anında WRITE_EXTERNAL_STORAGE izni, `.part` → rename ve
  MediaScanner kullanılır.
- Manga içerikleri **genel Resimler klasörüne yazılmaz**; eski resim tarayıcısı Manga
  alt ağacını taramaz. Galeride görünmeleri için `.nomedia` oluşturulmaz.

## Kalıcılık ve güvenli devam

- Her bölüm için `noBackupFilesDir/manga_downloads/<id>.json` altında AtomicFile
  manifesti bulunur. İş kimliği, sayfa sırası, yerel URI, format, boyut, tamamlanma ve
  hata durumu tutulur. Bu indirme kataloğu LRU cache temizliğinden bağımsızdır.
- Sayfa rezerve edilir, pending URI kaydedilir, dosya yazılır/yayımlanır, ardından
  sayfa tamamlanır. Yeniden deneme aynı isim/klasörü kullanır. Journal öncesi çok kısa
  çökme penceresindeki MediaStore satırı isim/path sorgusuyla bulunur; bölüm silme bu
  klasördeki unjournaled satırları da temizler.
- WorkManager, bölüm başına benzersiz iş, bağlı ağ kısıtı, foreground dataSync bildirimi,
  en fazla iki eşzamanlı bölüm ve bölüm içinde sıralı sayfa indirme kullanılır.
- Duraklatma ve bildirimden iptal devam edilebilir sayfaları silmez. Eski WorkInfo
  olayları yeni denemeyi bozamaz: manifestteki workId ile eşleştirilir.
- Yeniden deneme var olan ve okunabilen sayfaları atlar. Sayfa sayısı değişmişse
  farklı sürümlerin sayfaları karıştırılmaz; silip yeniden indirme istenir.
- Tüm sayfalar ve kesintisiz indeks aralığı doğrulanmadan bölüm "çevrimdışı hazır"
  yapılmaz. Silme önce işi iptal eder ve bölüm kilidini bekler; yazarken dosya silmez.
- Tamamlanan bölümler content/file URI'den doğrudan mevcut manga okuyucusuna açılır;
  tekrar cache'e kopyalanmaz. Online ve offline ViewModel anahtarları ayrıdır.

## Format ve bellek sınırları

- JPEG/PNG/GIF geçerli boyut bilgisiyle tespit edilir, gerçek uzantısıyla korunur.
  PNG şeffaflığı, uzun sayfa boyutu ve GIF animasyonu dışa aktarmada korunur.
- WebP ve cihazın çözebildiği diğer biçimler **gerçekten PNG'ye kodlanır**; sadece
  uzantı değiştirilmez. Statik görselde çizgi/metin/şeffaflık korunur. Hareketli WebP
  veya başka çok kareli bir dönüştürme girdisi tek PNG kareye dönüşür; tüm karelerin
  korunması bu sürümün kapsamı değildir.
- Akış dosyaya yazılır; tüm bölüm veya ham dosya RAM'e alınmaz. Sayfa başına 64 MB
  indirme sınırı, dönüşüm için en fazla 32 megapiksel sınırı (kullanılabilir heap azsa
  daha düşük), 160 MB boş geçici alan kontrolü vardır. Dönüşümler tekilleştirilir;
  iki büyük bitmap aynı anda çözülmez.
  Yaygın JPEG/PNG/GIF doğrudan aktarımlarında 32 MP dönüşüm sınırı uygulanmaz.
- Android'in ilgili cihazda çözemediği AVIF/TIFF vb. veri veya HTML/Captcha cevabı
  sahte JPEG adıyla başarılı gösterilmez; açık indirme hatası oluşur. Harici/native
  bir universal codec eklenmedi; her eski cihazda her formatı dönüştürme garantisi yoktur.

## Sınırlar

- Bu sürüm klasör/sayfa indirme sağlar; CBZ paketleme ve toplu manga indirme seçicisi
  eklenmedi. WorkManager kuyruğuna bölüm düğmelerinden birden çok bölüm eklenebilir.
- Genel indirme Wi-Fi tercihi bu yeni kuyruğa aktarılmadı: şu anda bağlı herhangi bir
  ağda çalışır. İşletim sistemi kota/arka plan/force-stop sınırlamalarına tabidir.
- Manifestler kurulum ömrüne özeldir (yedeklenmez). Uygulama kaldırılıp kurulursa
  galerideki dosyalar kalabilir ancak bu katalog otomatik yeniden içe aktarılmaz.
- Eski genel Resimler indirmeleri taşınmaz/silinmez. Yeni bölüm indirmeleri bağımsızdır.
- Derleme/cihaz testleri bu ortamda çalıştırılamadı; ayrıntı test belgesindedir.
