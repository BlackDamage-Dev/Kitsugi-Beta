# Anizm / TrAnimeIzle oynatma incelemesi — 4 Kasım 2026

## Kanıtın kapsamı

* Kullanıcının 17 ekran görüntüsü ve Drive klasörlerinin dosya listeleri; her klasörden bir veya daha fazla HTML dosyasının **yalnızca başlangıç bölümü** okunabildi. Büyük Drive yanıtları kesildi; bunlar tam bir sayfa/host fixture testi olarak kullanılmadı. Bağlantı açıklamalarının tersine, `message (36).txt` Anizm ana sayfası, `message (44).txt` TrAnimeIzle ana sayfası ve `message (46).txt` TrAnimeIzle Bot Kontrol sayfasıdır.
* Ayrı `f26901964-eng/Kitsugi-Plugins` deposunun GitHub `main` kaynakları incelendi; bu uygulama deposundaki `Kitsugi-Plugins` gitlink içeriği burada checkout edilmiş değil. Anizm kaynak sürümü 22, TrAnimeIzle yayın manifesti 45. 4 Kasım'da GitHub `builds/plugins.json` Anizm 22 ve TrAnimeIzle 45 gösteriyordu; kullanıcının cihazında kurulu sürüm bilinmiyor.
* Plugin deposundaki `research/anizm-playback-fix-2026-11-02.md` ve `research/tranimeizle-v45-2026-11-02.md`, önceki CI ölçümleridir; bu çalışmada tekrarlanmış canlı test değildir. Sandbox ağ politikası site/CDN hostlarına doğrudan erişim sağlamıyor. Android cihaz/emülatör ve Android SDK yok. Sonraki incelemede izole Java çalışma ortamı ve Kotlin derleyicisi kuruldu; tam Android/Gradle derlemesi yapılamadı. Bu nedenle canlı hostların tamamı ve Media3/MPV üzerinde uçtan uca oynatma **doğrulanmadı**.

## Ekran görüntüsündeki kaynaklar

| Kaynak | Görülen / kodda tespit edilen | Sonuç ve eksik doğrulama |
|---|---|---|
| Anizm Aincrad | AnizmPlayer HLS listelenmiş. Anizm v22 master adresini korumaya yönelik kod içeriyor. | Bu bölümün gerçek manifest/segment istekleri ve Media3/MPV denenmedi. Önceki rapordaki canlı prob bu bölümde Aincrad bulamamış. |
| Anizm SendVid | Bir 37 KB, 5 saniyelik MP4 görüntüsünde açıkça “This video is temporarily unavailable” yazıyor. | Geçerli MP4 ve HTTP 200/206, gerçek bölüm olduğunu kanıtlamaz. Sağlayıcının hata klibi oynatılıyor. Kısa süre veya boyut tek başına diğer videoların hata olduğunu kanıtlamaz; bunları körlemesine elemedik. Eklenti v22 embed HTML'inde açık hata metni varsa kaynak yayınlamamayı deniyor; doğrudan hata MP4'lerinin otomatik teşhisi hâlâ garanti değil. |
| Anizm OK.ru/Odnoklassniki | Görüntüde kaynak listelenmiş; farklı örnekte sitenin kendisi “video not found” diyor. | Önceki eklenti CI raporunda HLS çıkarılmış ancak manifest 400 dönmüş; bu ortamda tekrarlanmadı. Tek bir dosyanın silinmesi veya engellenmesi bütün host için kesin sonuç değildir. |
| Anizm YourUpload | vidcache MP4'ler listelenmiş. | Önceki CI raporunda CDN zaman aşımı; bu ortamda oynatma doğrulanmadı. |
| Anizm Google Drive | Ayrı örnekte Google oturum/kota ekranı görünüyor. | Dosya erişim/kota kısıtı oynatıcı motoru değiştirilerek ortadan kaldırılamaz. Anizm v22 HTML/uyarı yanıtlarını ayırt etmeye çalışıyor; belirtilen dosyayı bu çalışmada test edemedik. |
| Anizm Voe | 720p ve 1080p bağlantılar listelenmiş. | Listeleme oynatma kanıtı değil; bu bölümde oynatma, başlıklar ve CDN erişimi test edilmedi. |
| Diğer Anizm örnekleri | Vidmoly “video not found”; CyberFile site arayüzünde video gösteriyor. | Vidmoly örneği site tarafında yok; CyberFile site içi görüntü uygulama motorunda oynatma kanıtı değil. LuluStream ve EmbedV için görselde sonuç gösterilmiyor. |
| TrAnimeIzle AitrVip | Site oynatıcısında video görünüyor. | Uygulamada extract ve iki motorla oynatma doğrulanmadı. |
| TrAnimeIzle BYandex, SendVid, Upstream | Görselde example domain sayfası görünüyor. | Bu görüntüler ilgili örnekte gerçek medya yerine örnek alan adı sunulduğunu gösterir; uygulamada düzeltilebilir video bulunduğunu göstermez. |
| TrAnimeIzle GDrive, Mega | Google dosyası yok; Mega “File no longer accessible”. | Görülen dosyalar sağlayıcı tarafında mevcut değil; uygulama bu dosyaları geri getiremez. |
| TrAnimeIzle OK.ru | Site “blocked due to author's rights infringement” gösteriyor. | Site tarafındaki telif engeli uygulama hatası olarak sınıflandırılamaz; engel aşılmadı. |
| TrAnimeIzle Sibnet | Site 403 gösteriyor. | Sadece ekran görüntüsü 403'ün nedenini (erişim politikası, IP, başlık veya başka neden) kanıtlamaz. |
| TrAnimeIzle MailRu | Site oynatıcısında görüntü ve kalite seçimi var. | Uygulamadaki çözümleme ve Media3/MPV oynatması henüz denenmedi. |
| TrAnimeIzle Vudeo ve görüntüdeki diğer listelenmiş fakat açılmamış kaynaklar | Kaynak adı görünüyor, sonuç yok. | Durumları bilinmiyor. |

## Bu uygulama deposundaki değişiklikler

1. Embed kuyruğunda eklentinin `ExtractorLink.referer` değeri artık kaybolmuyor. Extractor çıktısındaki başlıklar, sağlayıcının başlıklarıyla büyük/küçük harf duyarsız birleştiriliyor; extractor açık değerleri öncelikli. Böylece yalnızca extractor'ın bir başlık döndürmesi, önceki cookie/diğer başlıkların tamamını silmiyor.
2. Doğrulama HEAD isteğinde eklentinin UA'sı korunuyor; GET yedeğinde de Range hariç tüm istek başlıkları taşınıyor. HLS manifestinde Range kullanılmıyor. 400/403/429/5xx gibi belirsiz GET sonuçları dosyanın yokluğu diye elenmiyor; bu **oynatma başarılı** anlamına gelmez.
3. Sanitizer'da aynı URL fakat farklı istek başlıkları, kalite veya harici ses URL'si olan seçenekler tek bir kaynağa indirgenmiyor.
4. Eklentinin `M3U8`/`DASH` türü ve `extractorData` değeri `StreamSource` üzerinden Media3'e taşınıyor. Uzantısız ama açıkça adaptif tipli adresler HTML embed olarak tekrar çözülmüyor; Media3 doğru MIME seçiyor ve bu manifestler paralel byte-range kaynağına sokulmuyor. Otomatik/elle yeniden deneme artık CS meta verisini koruyor. MPV başlıkları alıyor; CS interceptor'ı MPV'de çalıştırılmıyor.
5. Kaynak değişiminde ve yeniden çözümlemede ilk harici ses URL'sinin sıfırlanması düzeltildi. Aynı URL farklı ses/tür/interceptor koşullarıyla seçildiğinde oynatıcı hazırlığı yeniden tetikleniyor.
6. Bu kurallar için regresyon testleri eklendi. Bunlar kaynakların gerçek cihazda çalıştığına ilişkin testler değildir.

## Bilinen açıklar / sonraki test

* Eklenti deposunda bu oturumda değişiklik ya da yayın yapılmadı. TrAnimeIzle kaynak kodu HLS varyant genişletme ve URL bazlı tekilleştirme kullanıyor; imzalı HLS ve kaynak başlığı/ses meta verisi üzerinde ayrı inceleme ve eklenti deposunda değişiklik gerekebilir.
* Uygulama `StreamSource` hâlâ yalnızca ilk harici ses URL'sini taşıyor; ses parçasının kendi başlıkları ve diğer parçalar korunmuyor. Media3'te yeniden oluşturulan `ExtractorLink`'in `audioTracks` alanı hâlâ boş. `extractorData` artık korunuyor; hangi görselin kesin nedeni olduğu kanıtlanmadı. MPV, Media3'ün OkHttp interceptor zincirini kullanamaz.
* Media3 ve MPV için cihazda yüklenmiş eklenti sürümleri, bölüm URL'si, seçili kaynak, gizli token/cookie içermeyen hata logu ve her hostun gerçekten video görüntüsü/sesi gerekir. HLS master, varyant, segment ve key istekleri ayrı gözlemlenmelidir.
* İlk denemede `./gradlew :app:testDebugUnitTest ...` Java bulunmadığı için başlamadı. Ardından PyPI `jdk4py` Java 17 çalışma ortamı ve npm `kotlin-compiler` 2.3.0 ile `scripts/test_cs_playback_offline.sh` çalıştırıldı: **14 test geçti**. Bu test gerçek sanitizer/başlık/format Kotlin dosyalarını kullanır, ancak `StreamSource`, Android Log/CsTrace ve JUnit yerine stub kullanır; Gradle derlemesi, HTTP, Media3, MPV veya cihaz testi değildir. Değiştirilen Kotlin dosyalarında tree-sitter hata düğümü bulunmadı (anlamsal derleme değildir). `git diff --check` geçti.

## Açık kaynak karşılaştırması (4 Kasım)

* CloudStream [`CS3IPlayer.kt`](https://github.com/recloudstream/cloudstream/blob/6e0c1faaa5268a7bf38779f2a52bf936e86a21c8/app/src/main/java/com/lagradost/cloudstream3/ui/player/CS3IPlayer.kt) sağlayıcının interceptor'ına `ExtractorLink` nesnesini geçiriyor; bu nesne tür ve `extractorData` taşıyabiliyor. Uygulamanın önceki yeniden oluşturması bunları kaybediyordu.
* Aniyomi [`SendvidExtractor.kt`](https://github.com/yuzono/anime-extensions/blob/a5971ef82fa1e3fd868c78b0b9f2b10701106221/lib/sendvidextractor/src/aniyomi/lib/sendvidextractor/SendvidExtractor.kt) sayfadaki `source#video_source` üzerinden medya çıkarıyor; görüntüdeki gibi MP4 içine gömülmüş hata kartını analiz etmiyor. Bu nedenle bu extractor'ı eklemek o hata klibini gerçek bölüme dönüştürmez. Anizm v22 açık hata **HTML** metinlerini tanıyor; hata metni yalnız MP4 görüntüsünün içindeyse bu yol yeterli değil. Güvenilir bir hata klibi örneği/fingerprint ya da görsel analizi olmadan yalnız 5 saniye/37 KB ile kaynak silinmedi.
* TrAnimeIzle v45 `tryExtractors` içinde `audioTracks`/`extractorData` yeniden yayınlanmıyor; `emitLink` yalnız URL ile tekilleştiriyor ve `emitMedia` HLS master'ı varyantlara ayırıyor. Bunlar ayrı eklenti deposunda düzeltme gerektiren somut kod bulguları; bu uygulama deposunda plugin paketi üretilmedi veya değiştirilmedi.
