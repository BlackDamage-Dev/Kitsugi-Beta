# Kitsugi açık kaynak okuma yetenekleri denetimi

Tarih: 2026-11-10. Dal: `arena/9417dff1-kitsugi-beta`.

## Yöntem ve sınır

Bu çalışma yalnız uygulama mağazası açıklamalarına dayanmadı. GitHub üzerindeki recursive source tree ve ilgili kaynak dosyaları incelendi. Özellikler Kitsugi'nin mevcut Compose, `MangaSource`, `MangaPageLoaderV2`, DataStore ve okuma ilerlemesi modeline uyarlandı; başka uygulamaların ekranları veya düğmeleri kopyalanmadı.

Manga/manhwa/manhua/webtoon aynı görüntü sayfası motorunu kullanır; farkları otomatik Webtoon seçimi, RTL/LTR, dikey akış, kesintisiz kaydırma, geniş sayfa ve çift sayfadır. Çizgi romanlar ayrıca CBZ/CBR/PDF yolunu kullanır. Novel/light novel/web novel için seçilebilir metin, EPUB spine ve TTS yolu vardır. Bu belge her cihaz/kaynak/EPUB üreticisinde çalışma veya bütün açık kaynak uygulamaların tüketildiği iddiası değildir.

## İncelenen kod tabanları ve çıkarımlar

| Proje | Kod düzeyinde incelenen başlıca yollar | Kitsugi denetimine katkısı |
|---|---|---|
| [Mihon](https://github.com/mihonapp/mihon) | `ReaderPreferences.kt`, `SettingsReaderScreen.kt`, `ReaderActivity.kt`, `ReaderViewModel.kt`, `core/archive/.../EpubReader.kt` | Mod ve yön, ekran yönü, kesme, spread bölme/çevirme, Webtoon padding/zoom, sayfa geçişi, Wi-Fi preload, parlaklık/renk/grayscale/invert, tam ekran/cutout/keep-screen-on, ses tuşu/uzun dokunma, okunmuş bölüm atlama, EPUB container/OPF/spine. |
| [Kotatsu](https://github.com/KotatsuApp/Kotatsu) | `reader/ui/config/ReaderSettings.kt`, `ReaderActivity.kt`, `ReaderViewModel.kt`, `doublepage/`, `doublereversed/`, `bookmarks/domain/Bookmark.kt`, `CbzFetcher` | Seri bazlı filtre/ayar üretimi, bitmap renk kalitesi, standart ve Webtoon ayrı kırpma, çift ve ters çift mod, yer iminde bölüm+sayfa+scroll+yüzde+zaman. |
| [Aniyomi](https://github.com/aniyomiorg/aniyomi) | Mihon tabanlı reader/settings, source ve tracking tree | Anime/manga arasında aynı kütüphane ve ilerleme disiplini; kaynak tercihi ve senkronizasyon ayrımı. |
| [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY) | Genişletilmiş reader preferences, source, tracking ve backup tree | Okuyucu özelleştirmesi ve kaynak/seri meta verisinin birlikte yedeklenmesi gereksinimi. |
| [Komikku](https://github.com/komikku-app/komikku) | Mihon tabanlı reader ve gelişmiş ayar tree | Kaynak başına davranış, gelişmiş okuyucu ve kütüphane özelleştirmesi karşılaştırması. |
| [Komga](https://github.com/gotson/komga) | `EPUB` TOC/Extractor/metadata ve `OPDS v1/v2` controller/DTO tree | EPUB yalnız resim kabı değildir; metadata, sıralı spine ve içindekiler gerekir. OPDS hem Atom/XML hem JSON catalog standardıdır. |
| [Kavita](https://github.com/Kareadita/Kavita) | OPDS servisleri, EPUB fontları, `BookReaderPreferences` migration tree | Metin okuyucu tema/font/ayar kalıcılığı; OPDS sunucu etkileşimi ve farklı kitap biçimleri. |
| [LNReader](https://github.com/lnreader/lnreader) | `SettingsReaderScreen/tabs/DisplayTab.tsx`, `NavigationTab.tsx`, `AccessibilityTab.tsx`, `ThemeTab.tsx`, EPUB parser/normalizer/exporter/testler | Satır aralığı, padding, hizalama, font, tema; ses tuşu offset, swipe/tap, sayfalı metin, animasyon, otomatik kaydırma hız/offset, tam ekran, yüzde, pil/saat, keep-screen-on, bionic reading. |
| [QuickNovel](https://github.com/LagradOst/QuickNovel) | `TTSHelper.kt`, TTS notification/service, `BookReader.kt`, EPUB provider | HTML temizleme, okunabilir span/paragraflar, cümle sınırında TTS bölümlendirme, konuşma kuyruğu, EPUB indirme/üretme, yazar notu. |
| [Legado](https://github.com/gedoor/legado) | Reader, book source, EPUB, TTS ve replace-rule tree | Kaynak kuralı, içerik temizleme/değiştirme kuralları, metin/TTS ve kitap kaynağı yönetimi. |
| [KOReader](https://github.com/koreader/koreader) | `frontend/apps/reader/modules/readerconfig.lua`, document/crengine tree | Belge türüne özgü görünüm, tipografi, reflow ve gelişmiş belge okuma sınıfı. |
| [LibreraReader](https://github.com/foobnix/LibreraReader) | Reader/settings ve belge formatı tree | PDF/DJVU/EPUB dahil çok biçimli yerel belge beklentisi; Kitsugi'de desteklenmeyen format kapsamı açık tutulmalı. |
| [Readest](https://github.com/readest/readest) | Reader, settings ve book/document tree | Modern metin okuyucu tipografisi, tema, not/işaret ve platformlar arası kitap deneyimi. |
| [OpenComic](https://github.com/ollm/OpenComic) | Reader ve compressed-file tree | Yerel çizgi roman arşivi, doğal sayfa sıralaması ve klasör/arşiv gezinme ihtiyacı. |
| [YACReader](https://github.com/YACReader/yacreader) | Reader, compressed archive ve library tree | CBZ/CBR/PDF okuma, arşivden görüntü çıkarma ve çizgi roman kütüphanesi. |
| [Suwayomi Server](https://github.com/Suwayomi/Suwayomi-Server) | Reader, chapter/page, download ve backup tree | Sayfa isteklerinin sınırlanması, bölüm/sayfa kimlikleri, indirme ve sunucu tarafı ilerleme ayrımı. |

## Ayrıntılı özellik matrisi

Durumlar: **Uygulandı** = bu çalışmada kod yolu bağlandı; **Var** = önceki okuyucu düzeltmelerinde mevcut; **Sınırlı** = bütün kaynakları/biçimleri kapsamaz; **Sonraki** = bu pakette yoktur ve varmış gibi gösterilmez.

### Görüntü okuyucu: manga, manhwa, manhua, webtoon, çizgi roman

| Yetenek | Açık kaynak dayanağı | Kitsugi karşılığı |
|---|---|---|
| RTL, LTR, dikey, Webtoon; kalıcı mod | Mihon/Kotatsu | **Var.** Kullanıcı seçimi kalıcı; restore sırasında otomatik seçim yarışmaz. |
| Webtoon/Manhwa otomatik mod | Mihon/LNReader | **Var.** İçerik türüne göre yalnız ilk açılışta uygulanır. |
| Çift sayfa, yatay otomatik çift sayfa | Mihon/Kotatsu/YACReader | **Var.** Bağımsız display-page grupları; LTR/RTL sıralama; ilerleme son gerçek sayfadan. |
| Geniş sayfayı döndürme | Mihon | **Var.** Sayfa kimliği ve sayfa sayısı değişmez. |
| Fit screen/width/height, zoom başlangıcı | Mihon | **Var.** Telephoto zoom ve pan; çift sayfada tap bölgesi görsel yarım sayfaya eşlenir. |
| Kenar kırpma; standart/Webtoon ayrımı | Mihon/Kotatsu | **Sınırlı.** Kitsugi'de ortak kırpma vardır; mod başına iki kalıcı değer sonraki iş. |
| Yan boşluk/sayfa aralığı/opaklık/kitap efekti | Mihon/Kotatsu | **Var.** Reader options ve Webtoon/Pager bileşenlerine bağlı. |
| Yüksek renk/bellek dengesi | Kotatsu | **Var.** ARGB_8888 seçeneği. |
| Renk filtresi, grayscale, invert, sepya, özel parlaklık | Mihon/Kotatsu | **Var.** Hızlı renk kısayoku ve tam seçim paneli. |
| Okuyucu teması | Mihon/LNReader | **Uygulandı.** Siyah, koyu, beyaz, sepya; metin rengi temayla uyumlu. |
| Tam ekran, geçici sistem çubukları | Mihon/LNReader | **Uygulandı.** `WindowInsetsControllerCompat`, swipe ile geçici gösterme. |
| Ekranı açık tutma | Mihon/LNReader | **Uygulandı.** Reader yaşam döngüsünde flag eklenir/temizlenir. |
| Sayfa geçiş animasyonunu kapatma | Mihon/LNReader | **Uygulandı.** Navigasyonda animate veya immediate scroll. |
| Uzun dokunarak ilerleme | Mihon | **Uygulandı.** Yalnız zoom yokken sonraki sayfa; zoom pan'i engellenmez. |
| Ses tuşu ve ters ses tuşu | Mihon/LNReader | **Var.** Tekrarlı key event tüketimi kontrollü. |
| Tap alanları, ters/kapalı navigasyon | Mihon | **Var.** Pager, spread ve Webtoon ayrımı. |
| Otomatik kaydırma ve hız | LNReader | **Uygulandı.** Pager ve LazyColumn üzerinde px/sn; ayar 8–240 aralığında sanitize. |
| Ayarlanabilir preload | Mihon/Suwayomi | **Uygulandı.** 0–8 sayfa, loader'a anında uygulanır. Wi-Fi-only ve ağ ölçülü preload sonraki iş. |
| Sayfa ve yüzde sayacı | Mihon/LNReader | **Uygulandı.** Gerçek sayfa indeksine dayalı yüzde; çift sayfada grup indeksi kullanılmaz. |
| Yer imleri | Kotatsu/Readest | **Uygulandı.** Seri+bölüm+gerçek sayfa+zaman; ekle/kaldır/listele/sil/aç; farklı bölümde hedef sayfa korunur. |
| Okunmuş bölüm atlama | Mihon | **Var.** Sonraki/önceki araması mevcut tamamlanma kaydını kullanır. |
| Gizli okuma | Mihon/çoğu kütüphane uygulaması | **Uygulandı.** Bekleyen yazma iptal edilir; sayfa ve tamamlanma kaydı yapılmaz. |
| Seri başına ayar override | Kotatsu | **Sonraki.** Mevcut ayarlar globaldir. |
| Sayfa kaydet/paylaş ve duvar kâğıdı | Mihon/Kotatsu | **Sonraki.** Mevcut görsel paylaşımı bu reader paketine eklenmedi. |
| Sözlük/çeviri/OCR | KOReader/Librera/Readest | **Sonraki.** Görsel OCR ve offline sözlük yok. |
| Kesintisiz bölüm sonu kartı | Mihon | **Sınırlı.** Bölüm navigasyonu var; her mod için özelleştirilebilir geçiş kartı yok. |
| Donanım sayfa tuşu/gamepad/TV | KOReader/YACReader | **Sınırlı.** Android TV ayrı reader mevcut; bu paket mobil reader içindir. |

### Yerel dosya ve arşiv

| Yetenek | Kitsugi karşılığı |
|---|---|
| ZIP/CBZ | **Uygulandı/düzeltildi.** Görsel entry'ler doğal sıralanır; her sayfa deterministik private cache dosyasına stream edilir; Coil tek dosya modeli kullanır. |
| CBR/RAR | **Uygulandı.** Junrar `Archive` ve `FileHeader`; şifreli/bozuk RAR varyantları her ortamda garanti edilmez. |
| Doğal sayı sıralaması | **Uygulandı.** `page2 < page10`; leading zero, klasör ve taşmayan büyük sayı testleri. |
| PDF | **Uygulandı.** Android `PdfRenderer`, sayfa bazlı render ve private WebP cache; tüm belge RAM'e alınmaz. |
| EPUB metin | **Uygulandı.** `META-INF/container.xml`, OPF manifest/idref ve spine; göreli yol/`..` normalizasyonu; XHTML paragraf ayrıştırma. |
| EPUB görsel/math/SVG/font/CSS/inline image | **Sınırlı.** Metin yolu vardır; tam CSS/reflow, MathML, SVG ve zengin inline-image layout yoktur. Image-only EPUB ayrıca geliştirilmelidir. |
| DJVU, MOBI, AZW3, FB2, DOCX, TXT, Markdown | **Sonraki.** Destekleniyor gibi dosya seçiciye eklenmedi. |
| SAF kalıcı URI | **Sınırlı.** Seçilen dosya private storage'a kopyalanır; kaynak dosya sonradan değişse açılan kopya değişmez. Görünen dosya adı `OpenableColumns.DISPLAY_NAME` ile okunur. |
| OPDS 1 Atom / OPDS 2 JSON | **Uygulandı.** Yayın/yazar ve desteklenen acquisition linkleri; EPUB/PDF/CBZ/CBR indirip yerel okuyucuda açma. Kimlik doğrulama, arama şablonu ve navigation-feed gezgini sonraki iş. |
| Arşiv klasörü/seri kütüphanesi | **Sonraki.** Şu an tek dosya açma vardır. |
| Şifreli arşiv ve parola yönetimi | **Sonraki.** Güvenli parola saklama akışı yok. |

### Novel, light novel, web novel ve metin erişilebilirliği

| Yetenek | Açık kaynak dayanağı | Kitsugi karşılığı |
|---|---|---|
| HTML/XHTML içerik temizleme | QuickNovel/LNReader/Legado | **Uygulandı.** script/style/noscript/nav/header/footer/form/iframe/reklam kaldırma; yaygın chapter selector'ları ve body fallback. |
| Seçilebilir metin | LNReader/Readest | **Uygulandı.** Compose `SelectionContainer`. |
| Font boyutu, satır aralığı, margin | LNReader/Kavita | **Uygulandı.** Sırasıyla 12–36 sp, 1–2.5×, 0–48 dp; bozuk JSON değerleri sanitize. |
| Font ailesi ve hizalama | LNReader/Kavita | **Uygulandı.** Sans/serif/monospace; başa/iki yana/orta. Custom font importu yok. |
| Okuma teması | LNReader/Kavita/QuickNovel | **Uygulandı.** Görsel reader ile ortak dört tema. |
| Otomatik kaydırma | LNReader | **Uygulandı.** Görsel ve metin için ortak kontrol. |
| TTS | QuickNovel/Legado | **Uygulandı.** On-device Android TTS; uzun metin segmentleri, queue, play/pause/resume/stop, ViewModel release. |
| TTS bildirimi/MediaSession, hız/ses seçimi, cümle vurgusu | QuickNovel/Legado | **Sonraki.** Bu paket ekran içi kontrol sağlar; arka plan medya oturumu ve görsel vurgu yok. |
| Bionic reading | LNReader/QuickNovel | **Sonraki.** Metin vurgulama modeli eklenmedi. |
| Pil/saat, dikey seekbar, kaydırma offset | LNReader | **Sonraki.** Yüzde ve otomatik hız var; pil/saat ve ince offset yok. |
| Not, highlight, sözlük, arama | Readest/KOReader | **Sonraki.** Sayfa yer imi metin notu/highlight değildir. |
| EPUB export ve kaynak replace kuralları | LNReader/QuickNovel/Legado | **Sonraki.** Mevcut parser kaynak metnini dışa EPUB üretmez. |

## Teknik doğruluk notları

- `MangaPage.index` tek gerçek sayfa kimliğidir. Çift sayfa listesi ayrı üretilir; bookmark/progress/slider grup indeksine çevrilmeden önce gerçek sayfaya bağlanır.
- Arşiv cache anahtarı dosya yolu+boyut+mtime+entry içerir. Aynı isimli farklı arşiv entry'si yanlış cache'i paylaşmaz.
- EPUB path normalization arşiv dışına `..` ile çıkmayı reddeder; OPF `spine` sırası korunur.
- Yeni DataStore alanları eski JSON ile varsayılanlara döner; mevcut 17 seçenek kaybolmaz.
- Gizli mod ilerleme yazımını erken keser; TTS bölüm değişiminde ve ViewModel kapanışında durdurulur.
- Yerel testler arşiv sıralaması ve HTML parser için eklendi; gerçek Android API/Compose/EPUB/PDF derlemesi GitHub Actions üzerinden yapılır.

## Bilinçli kapsam dışı bırakılanlar

Tam PDF reflow/edit, DJVU/MOBI/AZW3/FB2, OPDS kimlik doğrulama/arama/navigation-feed gezgini, Bulut/Calibre/Komga/Kavita eşitleme, AniList/MAL bölüm tracking, seri bazlı ayar profilleri, sayfa OCR/çeviri, görsel sayfa paylaşımı, TTS medya bildirimi ve gelişmiş EPUB CSS bu paketin tamamlandığı anlamına gelmez. Bunlar ayrı veri modeli, UI ve gerçek servis/cihaz testleri gerektirir.
