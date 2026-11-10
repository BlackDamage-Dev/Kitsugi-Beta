# Manga / Webtoon okuyucu: kesintisiz şerit ve klavye denetimi

**Tarih:** 7 Kasım 2026  
**İstek:** Webtoon görsellerinin kendi içlerinde ayrı ayrı kayması yerine bölümün tek bir bütün olarak kayması; yinelenen okuyucu kontrollerinin temizlenmesi ve oynatıcıdaki odak/kısayol yaklaşımının manga okuyucuya uygulanması.

> **Doğrulama sınırı:** Kaynak düzenlemeleri, gerçek üretim Kotlin politikaları, SQLite migration ve kaynak sözleşmeleri doğrulandı. Android/Compose tam derlemesi, APK kurulumu ve cihaz testi **tamamlanmadı**. Gradle 8.13 indirmesi `SSLHandshakeException: Remote host terminated the handshake` ile durdu; Android SDK da bu ortamda hazır değil. Aşağıdaki “uygulandı” ifadeleri kod değişikliğini anlatır, cihazda kusursuz çalışma garantisi değildir.

## 1. Kök nedenler

| Bulgu | Eski davranış | Düzenleme |
|---|---|---|
| Webtoon normal manga bileşenini kullanıyordu | Her görselde `MangaPageItem` → `ZoomableAsyncImage`; bağımsız pan/zoom sahipliği, ekran boyuna göre kutulama | Webtoon için ayrı şerit çizicisi; normal manga bileşeni yalnızca pager modlarında |
| Zorunlu ayırıcı | Her görselden sonra 4 dp koyu boşluk | Varsayılan 0 dp; istenirse tek ayardan değişir |
| Dokunma “sonraki resme” atlıyordu | `animateScrollToItem(current + 1)` uzun görselin kalanını atlayabiliyordu | Ekran yüksekliğinin ayarlanabilir yüzdesi kadar kaydırma |
| Yalnızca sayfa indeksi kaydediliyordu | Uzun görselin ortasına dönülemiyordu | Kaynak görsel içindeki normalize konum da saklanır |
| Son görselin ilk pikseli “tamamlandı” sayılıyordu | Son sayfa indeksi görülünce okundu işareti | Webtoonda son görselin alt ucunun görünmesi gerekir |
| Gizli pager gözlemcisi aktifti | Webtoon ilerlemesini eski pager konumuyla ezme riski | Yalnızca etkin okuma modunun gözlemcisi; restore sırasında yazım kapalı |
| Genel ayar kimliği uyuşmuyordu | Genel ayarlar `WebtoonZoom` yazıyor, okuyucu yalnızca `Webtoon` tanıyordu | Kanonik `Webtoon`; eski değer için geriye uyumlu normalizasyon |
| Filtre kimlikleri uyuşmuyordu | `GrayScale` / `Inverted` ile `Grayscale` / `Invert`; okuyucuda karşılığı olmayan sıcak/soğuk seçenekleri | Aynı kanonik seçenekler; eski sıcak → sepya, desteklenmeyen soğuk → normal |
| “Dikey” gerçekte yataydı | Bütün pager modları `HorizontalPager` kullanıyordu | Dikey moda gerçek `VerticalPager` |
| Telephoto / Coil model uyuşmazlığı | Telephoto 0.8.0 Coil **2** entegrasyonuna Coil **3** `ImageRequest` veriliyordu | Pager kendi Coil 2 isteğiyle kaynak tarafından indirilmiş yerel dosyayı açar |
| Yinelenen arayüz | Üst ve alt ayar düğmesi; ayrıca panelde olan yön/sığdırma çevrimleri | Tek ayar giriş noktası; özel işlevli alt araçlar; mobil/TV ortak okuyucu |

## 2. Açık kaynak araştırması

Araştırma GitHub API üzerinden aşağıdaki sabit commit'lerde yapıldı; karşılaştırma yalnızca ekran görüntülerine veya proje tanıtımlarına dayanmıyor.

| Proje / lisans | İncelenen yaklaşım | Kitsugi kararı |
|---|---|---|
| **Mihon / Apache-2.0** — `70c73c728b6350dd9f1447420e9cef54e8403b41` | Tek RecyclerView, genişliğe sığdırma, ekranın 3/4'ü kadar ilerleme, ortak şerit çerçevesinde zoom; yön ve ses tuşlarının bağlama göre ele alınması | Tek kaydırma sahibi, genişlik tabanlı geometri, ekran yüzdesiyle ilerleme, isteğe bağlı ses tuşları |
| **Aniyomi / Apache-2.0** — `97414446b8a95994c72dd33c41c971a89d4d25b8` | Webtoon için ortak çerçeve/adaptör; ekran mesafesiyle ileri/geri | Mevcut Kitsugi oynatıcısına da uygun, aynı eyleme hem düğme hem kısayol üzerinden ulaşma |
| **Kotatsu / GPL-3.0** — `34f6e5232bf7f0bcdf8f3fbddb986697518e82a4` | Şerit kapsayıcısında ölçekleme; boşlukların ayrı tercih olması; uzun görüntüler için özel görünüm/scroll hesapları | Şerit zoom'u ve isteğe bağlı boşluklar benimsendi. Görsel başına iç kaydırma sistemi kopyalanmadı |
| **Suwayomi WebUI / MPL-2.0** — `bd90885b821d894a7cdedf993f987bd9d19caa7f` | Okuyucuya özel hotkey scope; sayfa/kaydırma/bölüm ayrımı, otomatik kaydırma ve hız ayarı; özelleştirilebilir ekran yüzdesi | Okuyucu odak kapsamı, bölüm/sayfa/ekran ayrımı, otomatik kaydırma ve rehber. Web/React motoru Android'e taşınmadı |
| **Telephoto 0.8.0** | Yerel büyük görsellerin subsampling ile çizimi; Coil 2 model API'si | Pager'da mevcut kütüphane korunur. Webtoon için bağımsız görsel zoom'u devreden çıkarılarak yerel, sınırlı bölge çözümü kullanılır |

### İncelenen kaynak dosyaları

**Mihon**
- [WebtoonViewer.kt — ekran mesafesi, tek liste, prefetch bağlamı](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt)
- [WebtoonPageHolder.kt — MATCH_PARENT / WRAP_CONTENT, genişliğe sığdırma](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonPageHolder.kt)
- [WebtoonRecyclerView.kt — ortak şerit ölçeği ve pan sınırları](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonRecyclerView.kt)
- [ReaderActivity.kt — tuş, ses tuşu, yön ve N/P bölüm eylemleri](https://github.com/mihonapp/mihon/blob/70c73c728b6350dd9f1447420e9cef54e8403b41/app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt)

**Aniyomi**
- [WebtoonViewer.kt](https://github.com/aniyomiorg/aniyomi/blob/97414446b8a95994c72dd33c41c971a89d4d25b8/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonViewer.kt)

**Kotatsu**
- [WebtoonScalingFrame.kt — kapsayıcı düzeyinde zoom, klavye/mouse yaklaşımı](https://github.com/KotatsuApp/Kotatsu/blob/34f6e5232bf7f0bcdf8f3fbddb986697518e82a4/app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/pager/webtoon/WebtoonScalingFrame.kt)
- [WebtoonImageView.kt — uzun resim ölçümü ve iç scroll hesabı](https://github.com/KotatsuApp/Kotatsu/blob/34f6e5232bf7f0bcdf8f3fbddb986697518e82a4/app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/pager/webtoon/WebtoonImageView.kt)
- [WebtoonReaderFragment.kt — tercih, boşluk ve yaşam döngüsü](https://github.com/KotatsuApp/Kotatsu/blob/34f6e5232bf7f0bcdf8f3fbddb986697518e82a4/app/src/main/kotlin/org/koitharu/kotatsu/reader/ui/pager/webtoon/WebtoonReaderFragment.kt)

**Suwayomi**
- [ReaderHotkeys.tsx — okuyucu kapsamı ve eylem ayrımı](https://github.com/Suwayomi/Suwayomi-WebUI/blob/bd90885b821d894a7cdedf993f987bd9d19caa7f/src/features/reader/hotkeys/ReaderHotkeys.tsx)
- [ReaderSettings.constants.tsx — varsayılan tuşlar ve davranış tercihleri](https://github.com/Suwayomi/Suwayomi-WebUI/blob/bd90885b821d894a7cdedf993f987bd9d19caa7f/src/features/reader/settings/ReaderSettings.constants.tsx)
- [ReaderSettingScrollAmount.tsx — ekran yüzdesiyle kaydırma](https://github.com/Suwayomi/Suwayomi-WebUI/blob/bd90885b821d894a7cdedf993f987bd9d19caa7f/src/features/reader/settings/behaviour/components/ReaderSettingScrollAmount.tsx)

**Telephoto**
- [0.8.0 ZoomableAsyncImage.kt — `coil.ImageLoader`, `gesturesEnabled`, `onClick`](https://github.com/saket/telephoto/blob/0.8.0/zoomable-image/coil/src/main/kotlin/me/saket/telephoto/zoomable/coil/ZoomableAsyncImage.kt)
- [0.8.0 SubSamplingImage.kt](https://github.com/saket/telephoto/blob/0.8.0/zoomable-image/sub-sampling-image/src/main/kotlin/me/saket/telephoto/subsamplingimage/SubSamplingImage.kt)

**Lisans sınırı:** Bu projelerin ekran/feature paketleri veya GPL/MPL dosyaları depoya kopyalanmadı. Davranışlar karşılaştırıldı ve Kitsugi'nin Compose, DataStore, Room ve kaynak yükleyici altyapısıyla özgün bir uyarlama yazıldı. Yeni harici okuyucu motoru veya yeni runtime bağımlılığı eklenmedi.

## 3. Uygulanan Webtoon mimarisi

```text
MangaReaderScreen / TV giriş noktası
  └─ MangaReaderSession: ortak gezinme ve ilerleme
      └─ MangaWebtoonState: tek zoom + tek yatay pan + tek liste konumu
          └─ LazyColumn
              ├─ görsel 1'in tembel çizim dilimleri
              ├─ görsel 2'nin tembel çizim dilimleri
              ├─ ...
              └─ bölüm sonu kartı
```

### Kesintisiz görüntü

- Görseller aynı kullanılabilir genişliğe ölçeklenir; kaynak oranları korunur. Farklı kaynak genişlikleri olan görseller de şeritte aynı genişliktedir.
- Çizim dilimleri yalnızca bellek/çizim birimidir. Kendi scroll state'i, zoom'u, görünür sayfa numarası, alt/üst barı, çerçevesi veya aralığı yoktur.
- Paylaşılan kaynak sınırları yuvarlanır: tek tek yüksekliklerin yuvarlanmasından doğan birikimli piksel kayması önlenir.
- Varsayılan yan boşluk **0 dp**, görseller arası boşluk **0 dp**. İsteğe bağlı 16/32/64 dp yan boşluk; 4/16/32 dp görseller arası boşluk.
- Webtoonda “ekrana / yüksekliğe sığdır” seçenekleri gösterilmez: bunlar tek genişlikli şeridin mantığıyla çelişir. Pager tercihleri korunur ve pager'a dönünce tekrar görünür.
- Her resmin içine eklenen sayfa numarası kaldırıldı. Kontroller gizliyken istenirse tek küçük bölüm sayacı gösterilir.

### Uzun görseller ve bellek

- Önce dosya başlığı okunur; tam bitmap açılmadan ölçü/EXIF bilgisi yayımlanır.
- Kaynak görüntü kareye yakın bölgelere ayrılır. Liste yalnızca gerekli dilimleri oluşturur; 1:1000 oranlı görsel için de tek dev Compose ölçümü yapılmaz.
- `BitmapRegionDecoder`, iki eşzamanlı decode, bitmap kenarı en fazla **2048 px**, yeniden kullanılabilir tile cache'i **16 MiB**.
- Ekranda hâlâ çizilebilen bitmap'ler cache eviction sırasında `recycle()` edilmez; RenderThread kullanım-sonrası-serbest-bırakma riski önlenir.
- Pinch'in her pikselinde decode yeniden başlatılmaz; gereken örnekleme seviyesi değişince daha uygun tile istenir. Önceki tile yeni çözüm gelene kadar tutulabilir.
- Bölge çözümünü desteklemeyen codec'lerde, tam çözünürlük yerine kenarları sınırlı **durağan** görüntü fallback'i vardır. Çok uzun desteklenmeyen formatlarda ayrıntı kaybı mümkündür; GIF/AVIF animasyonu garantisi yoktur.
- Bozuk 1 px genişlikli dev bir başlığın milyonlarca liste öğesi üretmesini önleyen güvenlik sınırı vardır: görsel başına en fazla 4096 kare dilim oranı.
- Bu sınırlar toplam uygulama belleğinin veya her cihazın OOM'dan tamamen muaf olduğu anlamına gelmez; cihaz profili kabul testi gerekir.

### Jestler ve gezinme

- Dikey tek parmak sürüklemesi tek LazyColumn'a gider; resmin içinde ikinci bir dikey kaydırma başlamaz.
- İki parmak / çift dokunma bütün şeridi yakınlaştırır; 1×–3× sınırı ve yatay pan sınırları ortaktır.
- Yakınlaştırırken mantıksal liste yüksekliği görünür yüksekliğe göre küçültülür. Aksi hâlde ölçeklenmiş son görselin alt ucu erişilemez olabilirdi.
- Üst/alt yüzde 25'e dokunma geri/ileri; orta alana dokunma kontrol aç/kapat. Dokunmayla kaydırma kapatılabilir.
- Varsayılan ekran adımı **%90**, seçenekler %50/%75/%90/%100. Uzun görseli atlayıp bir sonraki URL'ye zıplamaz.
- Webtoon kaydırma sınırı kendiliğinden başka bölüme atlatmaz. Bölüm sonu kartı, bölüm düğmeleri veya N/P açık geçiş yapar.
- Otomatik kaydırma 8–160 dp/sn; dokunma, manuel gezinme, panel açılması, bölüm/mod değişimi veya uygulamanın duraklamasında durur. Yüklenmemiş görünür sayfayı atlamak yerine bekler. Arka plandan dönüşte kendiliğinden yeniden başlamaz.
- Görünür kısa sayfalar da prefetch penceresine dahil edilir; ekranda olan sayfanın indirmesi dar bir “ileri 3 sayfa” penceresi nedeniyle iptal edilmez.
- Eklentinin kendi görsel isteği / Referer / çerez / kaynak istemcisi korunur. Webtoon çizici yeniden genel HTTP isteği oluşturmaz.

## 4. İlerleme ve veritabanı

- Room sürümü **29 → 30**; `manga_chapter_progress.lastPageOffset REAL NOT NULL DEFAULT 0`.
- Eski geçmiş korunur; eski satırlar görselin başından devam eder.
- Konum, düzeltilmiş kaynak görsel yüksekliğinin 0–1 kesridir. Yoğunluk, ekran dönüşü ve yan boşluk değişimlerinden bağımsızdır; sadece ham ekran pikseli saklanmaz.
- Kaydetme 500 ms debounce ile yapılır. Bölüm değişimi, duraklama ve çıkışta son snapshot FIFO yazım kuyruğuna alınır; eski bölüm adı yeni bölüm satırına yazılmaz.
- Geri dönüşte aktif hedef görselin başlığı beklenir; gerçek tile konumu hesaplandıktan sonra ilerleme yazımı açılır.
- Sadece pager modunda pager, sadece Webtoon modunda liste ilerleme yazar.
- Önceden okundu olarak işaretlenmiş bölümün tamamlanma bilgisi geri kaydırınca silinmez. Mevcut “tamamlanmış bölümü yeniden açınca baştan başla” davranışı korunur.
- `30.json` eklendi. Hash rastgele yazılmadı: [Room SchemaIdentityKey](https://github.com/androidx/androidx/blob/067ed409b9bc2dcc59b93c8e9ab8d092beeff3a5/room/room-compiler/src/main/kotlin/androidx/room/vo/SchemaIdentityKey.kt), Entity/Field/PrimaryKey/Index kimlik sözleşmesi kullanıldı; **25–29 arasındaki beş mevcut KSP export'u birebir yeniden üretildikten sonra** 30 türetildi. Yeni hash: `1d1b67f7e361de4b39f68743b48edfa8`. Gerçek KSP/Room derlemesi ve cihazda migration açılışı hâlâ kabul testi kapsamındadır.

## 5. Tekrarlanan kontrollerin temizlenmesi

- Tek okuyucu ayarı düğmesi alt çubuktadır. Üstte aynı paneli açan ikinci düğme kaldırıldı.
- Okuma yönü ve sığdırma tercihlerinin alt çubuktaki ikinci çevrim düğmeleri kaldırıldı; ayarlar panelinde tek düzenleme alanı vardır. Klavye eylemleri aynı tercihleri değiştirir.
- Alt araçlar: bölümler, tam ekran, Webtoon için otomatik kaydırma, klavye rehberi, ayarlar. Sayfa sayacına basmak “sayfaya git” alanını açar.
- İlerleme sürgüsü sürüklerken her küçük harekette birden çok coroutine başlatmaz; bırakınca hedefe gider.
- Hata/yükleme sırasında da ayar/rehber araçları erişilebilir kalır.
- Bölümler, ayarlar, sayfaya git ve rehber tek `ReaderOverlay` durumunu paylaşır; üst üste iki panel açılamaz.
- TV giriş noktası ortak okuyucuyu kullanır. Eski bağımsız `TvMangaReaderControls` kaldırıldı; ikinci bir yön/fit/klavye mantığı tutulmuyor. TV sheet/dialog sarmalayıcısı ve görünür düğme odağı korunur.
- Yeni okuyucu metinleri TR/EN birlikte eklenmiştir. Panellerin kontrastı uygulamanın açık/koyu temasından bağımsız koyu okuyucu yüzeyiyle korunur.

## 6. Klavye kısayolları

Kitsugi oynatıcısındaki panel → kontroller → çıkış önceliği ve odaklı denetime tuş bırakma mantığı referans alındı. Manga için ayrı, saf Kotlin politika ve ince Android key-code adaptörü eklendi; oynatıcının MPV/Media3 tuş işleme kodu değiştirilmedi.

| Tuş | Eylem |
|---|---|
| ← / → | Yöne uygun ileri/geri; RTL mangada yön terslenir |
| ↓ / ↑, J / K | İleri / geri |
| Space / Shift+Space, PgDn / PgUp | İleri / geri; webtoonda ekran yüzdesi kadar |
| Home / End | Etkin bölümün başı / sonu |
| N / P, ] / [, Ctrl+→ / Ctrl+← | Sonraki / önceki bölüm |
| M, Enter, kumanda merkez, Menu | Kontrolleri aç/kapat; odaklı düğmede Enter düğmeye bırakılır |
| S | Ayarlar |
| C | Bölüm seçici |
| G | Sayfaya git; geçersiz aralıkta onay kapalı |
| R | Görünür çevredeki hatalı sayfaları / sayfa listesi hatasını yeniden dene |
| F | Tam ekran; sistem çubukları kontrollere göre gösterilir/gizlenir |
| O | Ekranı döndür; okuyucudan çıkınca önceki orientation geri yüklenir |
| D | Okuma modu çevrimi |
| I | Pager'da görsel sığdırma çevrimi; Webtoon'da etkisiz |
| B | Siyah / koyu gri / beyaz arka plan |
| A | Webtoon otomatik kaydırma başlat/durdur |
| + / − | **Webtoon** şeridini yakınlaştır/uzaklaştır |
| 0 | Yakınlaştırmayı sıfırla; pager'da Telephoto reset'i |
| H, ?, F1 | Kısayol rehberi |
| Esc / Back | Önce açık panel; sonra Webtoon zoom'u; sonra HUD; sonra çıkış |
| Tab / Shift+Tab, Enter | Denetim odağında dolaş / odaktaki denetimi çalıştır |
| Ses − / + | Yalnızca tercih açılmışsa ve HUD gizliyse ileri / geri |

### Çakışma önlemleri

- KeyDown eylemi çalıştırır; ilgili KeyUp tüketilir, ikinci kez çalıştırmaz.
- Basılı tutma yalnızca ilerleme ve Webtoon zoom gibi tekrar edilebilir eylemlerde çalışır. N/P, ayar açma, retry, mod değiştirme vb. her repeat'te yeniden tetiklenmez.
- Alt/Meta kombinasyonları ve tanınmayan Ctrl kombinasyonları sisteme bırakılır.
- Okuma alanı odakta değilken, odaklı slider/çip/düğmenin ok/Space/Enter tuşları çalınmaz.
- Açık panelde kısayollar sayfaya sızmaz. Sayfa numarası alanı metin/Enter girişini kendi içinde işler.
- Sheet/Dialog ayrı Android penceresi olduğundan Escape yalnızca ana okuyucuda değil, panelin kendi odak kapsamında da ele alınır.

## 7. Doğrulama sonuçları

| Kontrol | Sonuç | Ne kanıtlamaz? |
|---|---:|---|
| Gerçek üretim Kotlin geometri, tercih, EXIF, ilerleme ve shortcut politikaları | **37 test grubu geçti** | Android çizim/jest/codec sonucu değildir |
| SQLite geçmiş koruma, schema 30 şekli/hash'i ve statik entegrasyon sözleşmeleri | **15 test geçti** | Gerçek Room/KSP/Compose tip derlemesi değildir |
| Mevcut oynatıcı politika regresyonu | **27 test grubu geçti** | MPV/JNI/cihaz testi değildir |
| Değişen/yeni Kotlin kaynaklarının PSI sözdizimi | **30 dosya geçti** | Android API/tip ve Compose compiler doğrulaması değildir |
| TR/EN kaynak anahtar/format eşleşmesi | **Geçti: 17.733 string, 4 array** | Ekran üzerindeki yerleşim ve çeviri kalitesi testi değildir |
| `git diff --check` | **Geçti** | Çalışma zamanı testi değildir |
| `:app:compileFossDebugKotlin` | **Engellendi** | Gradle 8.13 indirmesinde SSL/EOF; APK üretilmedi |

Çevrimdışı mantık testlerini yeniden çalıştırma:

```bash
JAVA_HOME=/path/to/java17 \
KOTLINC=/path/to/kotlin-2.0.21/bin/kotlinc \
bash scripts/run_manga_reader_checks.sh

python3 scripts/check_localization_resource_parity.py
git diff --check
```

Android ortamında ayrıca:

```bash
./gradlew :app:testFossDebugUnitTest --tests '*MangaReaderPolicyTest'
./gradlew :app:assembleFossDebug
./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.screens.manga.MangaWebtoonReaderTest
```

Dört cihaz testi eklendi: eşit genişlik/sıfır aralık, uzun görselde tek scroll sahibi ve sürükleme, zoom sonrası sona erişim, gerçek Android region decode. **Bu dört test bu ortamda çalıştırılmadı.**

## 8. Açık kabul listesi / kapsam sınırı

- [ ] Android/Compose/KSP tam derlemesi; Room 29'dan gerçek uygulama verisiyle 30'a açılış.
- [ ] Kullanıcının Solo Leveling kaynağında kısa/uzun/karışık en az üç bölüm; sıfır boşluk ve resim sınırını geçen kesintisiz sürükleme.
- [ ] 1:10, 1:100 ve çok uzun JPEG/PNG/WebP; codec fallback'leri, EXIF'in sekiz yönü; çözünürlük ve seams piksel incelemesi.
- [ ] Pinch, çift dokunma, zoom altında yatay/dikey pan, fling ve son piksele erişim; 320 dp, tablet, yatay ekran.
- [ ] Bölümün ortasındaki uzun görselden çık/dön; mod değiştir, ekran döndür, yan boşluk değiştir; son görselin başını görünce okundu olmaması.
- [ ] Bluetooth/USB klavye: Türkçe Q/F ve İngilizce düzen, Shift/Ctrl, tuş tekrarı, numpad; metin alanında G/R/N/P sızmaması.
- [ ] Android TV kumandası: odak halkası, Enter ile gerçek düğme çalıştırma, sheet/dialog Escape/Back önceliği.
- [ ] Otomatik kaydırma: yavaş ağ, hata, arka plan, panel, dokunma, ekran sonu; istemsiz bölüm geçişi olmaması.
- [ ] TalkBack etiketleri, büyük yazı ve açık/koyu/AMOLED uygulama temaları.
- [ ] Düşük RAM cihazda uzun oturum, cache temizlenmesi sırasında retry, bölüm değiştirme stres testi.

**Bilinçli olarak eklenmeyenler:** Otomatik beyaz kenar kırpma/panel ayırma (çizimi veya balonları kesebilir), bölüm sonunu onaysız atlama, OCR/çeviri motoru, yeni indirme motoru, seri bazlı tam tercih profili ve kullanıcı tarafından tuşları yeniden eşleme. Bunlar bu şerit düzeltmesi için zorunlu değil; koddan kopyalanmış ama Kitsugi'de gerçek karşılığı olmayan yeni düğmeler eklenmedi. Pager'da +/- ile programatik Telephoto zoom'u bu sürümün API'sinde desteklenmediğinden vaat edilmedi; dokunma/pinch ve `0` reset korunur, +/- rehberde açıkça Webtoon olarak işaretlidir.
