# Simkl toplu eşitleme incelemesi

Tarih: 2026-10-07  
İncelenen başlangıç: `73e4db1af7878a9bee54fd319ff6610e406841df`

## Sonuç

Sorun sadece gecikme, grup büyüklüğü veya hesabı yeniden bağlama sorunu değil. **Okuma ve yazma API sözleşmeleri karıştırılmış; buna başarısız işlemleri gizleyen hata yönetimi eklenmiş.** Anime aktarımının iki yönünü de bozan somut kod hataları var.

Bu bulgu, hesabın Simkl tarafından engellendiğini veya token'ın geçersiz olduğunu kanıtlamaz. İnceleme kaynak kodu ve resmî API dokümanı üzerinden yapıldı; kullanıcı hesabına erişilmedi, canlı Simkl isteği gönderilmedi. Bu ortamın ağ izinleri Simkl API alan adını kapsamıyor. Hesaba özgü HTTP yanıtları/logcat olmadan 401, 403, 429 veya uygulama anahtarı kısıtlaması teşhis edilemez.

## Kaynak ve yöntem

Resmî kaynak: [SIMKL/API – apiary.apib](https://github.com/SIMKL/API/blob/fdb64611ac109f23cd58c01533ca5220ac51354e/apiary.apib).

İncelenen bölümler:
- `Get All Items`: satır 4316 ve devamı; anime örneği 4508–4560.
- `Add Items to the history`: 4736 ve devamı; anime POST örneği yaklaşık 4965–5004.
- `Add Ratings` / `Remove Ratings`.
- `Add Item to the List`: 5452–5590.
- `How to use Sync` / Activities ve `date_from` açıklamaları.

Kod akışı:
`AuthViewModel` → kaynak listeler → birleştirme → `SimklSyncManager.syncBatchToSimkl` → `SimklApiClient` → `SimklImportManager.fetchAllLists` → `repository.smartImport`.

Ayrıca tekil güncelleme, OAuth ve `SimklSyncRepository` yolları gözden geçirildi. Hesaba özel bağlantı ayarları değiştirilmedi.

## Kanıtlanmış hatalar

### 1. P0 — Anime yanlış JSON alanından gönderiliyor

Başlangıç kodu `/sync/add-to-list`, `/sync/history`, `/sync/ratings` için ayrı bir `anime` dizisi oluşturuyordu. Resmî yazma sözleşmesi `movies` ve `shows` kullanıyor; anime örnekleri de `shows` içinde. Okuma tarafındaki `anime` kategorisi yazma gövdesine taşınamaz.

Yanlış:
```json
{"anime":[{"to":"watching","ids":{"mal":16498}}]}
```
Doğru:
```json
{"shows":[{"to":"watching","ids":{"mal":16498}}]}
```

Etki: Geçerli MAL/Simkl kimliği, başlık ve yıl olsa bile belgelenen sözleşmeye uygun istek gönderilmiyor. Sunucunun bilinmeyen alanı hangi HTTP/gövdeyle yanıtladığı bu incelemede canlı olarak görülmedi; “kesin 400 dönüyor” denemez.

Düzeltme: Toplu liste/geçmiş/puan yazımları doğru zarfa alındı. Tekil liste, durum, puan ve kaldırma isteklerindeki anime zarfı da `shows` yapıldı. Mantıksal anime türü korundu; GET kategorileri topluca `shows` yapılmadı.

### 2. P0 — Simkl'den gelen animeler okunmadan atlanıyor

Resmî yanıt şekli:
```json
{"anime":[{"status":"watching","show":{"title":"Fate/Stay Night","ids":{"simkl":46116,"mal":"22297"}}}]}
```

Başlangıç kodu dıştaki `anime` dizisini buluyor ama her öğede `item["anime"]` arıyordu. Bulamayınca `continue` ile kaydı atlıyordu. Aynı hata `SimklImportManager` ve `getUserWatchlist` içinde vardı.

Etki: Dolu bir Simkl anime kütüphanesi uygulamada boş görünür; çapraz eşitleme Simkl'de zaten var olanları da eksik sanır.

Düzeltme: İç nesne anime/dizi için `show`, film için `movie`. Toplu importer, beklenmeyen öğe yapısını artık sessizce atlamıyor. API'nin belgelenmiş `null` boş liste yanıtı ayrıca ele alınıyor.

### 3. P0 — Sunucu hatası yerelde sahte başarıya dönüşüyor

`AuthViewModel` yenileme sonunda Simkl listesi boş/null ise birleşik yerel kütüphaneyi `source = "simkl"` yaparak içe aktarıyordu. Bu, sunucu onayı değildir.

Etki: Kullanıcı “aktarıldı” sanır; gerçek Simkl hesabında kayıt yoktur. Sonraki okuma, farklı listeler veya yeniden bağlantı tutarsızlığı ortaya çıkarır.

Düzeltme: Bu fallback kaldırıldı. Yalnızca sunucudan okunan kayıtlar Simkl kaydı olarak içe aktarılıyor. Mevcut yerel kayıtları topluca silme yapılmıyor (`allowDelete = false`). Önceki sürümlerin oluşturduğu hayalet kayıtlar otomatik olarak temizlenmiş sayılmaz; güvenli temizlik ayrı bir uzlaştırma işi.

### 4. P1 — HTTP 200, içerik gerçekten işlenmese de başarı sayılıyor

Eski toplu parser boş gövdeyi, `null`, JSON parse hatasını ve sıfır ekleme yanıtını başarı kabul edebiliyordu. `runCatching` sonucu kontrol edilmiyordu. `not_found` kullanıcı açısından başarısız bir aktarım olmasına rağmen genel başarı yolu çalışıyordu.

Düzeltme: Ortak sözleşme parser'ı eklendi. Liste işleminde onay sayısı istek sayısıyla karşılaştırılıyor; `not_found`, eksik onay ve anlamsız/bozuk yanıt hata oluyor. Kısmi onay sayısı kaybolmuyor. Bu temkinli yaklaşımda sıfır işlem yanıtı başarı değil, doğrulanamayan işlem sayılır; mevcut kaydı yeniden yazma davranışı canlı kabul testinde kontrol edilmeli.

### 5. P1 — Bölüm sayısı, tek bölüm numarası olarak yazılıyor

Eski kod `progress = 12` için yalnızca `S01E12` gönderiyordu. Bu, ilk on iki bölümü işaretlemek değildir. Filmler de `shows` altında sezon/bölüm olarak gönderiliyordu.

Düzeltme:
- Anime için resmî anime POST örneğindeki doğrudan `episodes` dizisi, `1..progress` ile oluşturuluyor.
- Filmler `movies` altında, sezon/bölüm olmadan gönderiliyor.
- TV dizileri için toplam sayıdan sezon bilgisi uydurulmuyor; toplu işlem açık hata veriyor. Liste durumu ve puanı yine aktarılabilir.

Sınır: Anime için 1..N, kaynak ilerlemesinin ardışık bölüm sayısı olduğu varsayımıdır. Aralıklı izleme, özel bölüm, birleşik/sezonlanmış katalog eşlemeleri ayrı model ister. Çok sezonlu TV aktarımı bu yamayla tamamlanmış değildir.

### 6. P1 — Geçmiş/puan sonuçları yok sayılıyor; işlem sırası durumu değiştirebiliyor

Eski yönetici `historyBatchDetailed` ve `ratingsBatchDetailed` dönüş değerlerini kullanmıyordu. Bu metotlar da yalnızca HTTP başarısını kontrol ediyordu. History API'sinin izleme durumunu değiştirebildiği belgeleniyor; `hold`/`dropped` gibi durum önce yazılıp sonra geçmişle değiştirilebilir.

Düzeltme: Geçmiş ve puan yanıtları denetleniyor, başarısızlıklar sonuç listesine ekleniyor. Toplu akışta hedef liste durumu en son uygulanıyor. 401 ve coroutine iptali bu toplu katmanlarda yutulmuyor.

Sınır: Üç ayrı HTTP işlemi atomik değildir. Son adım başarısız olursa önceki adımlar sunucuda kalabilir; sonuç kısmi başarısızlık olarak ele alınmalı.

### 7. P1 — Okuma hataları boş kütüphane gibi ele alınıyor

Importer HTTP hatalarında veya denemeler bitince boş liste döndürüyordu. `AuthViewModel` ilk Simkl okumasının istisnasını da boş listeye çeviriyordu.

Düzeltme: Denemeler sonunda hata yükseltiliyor. İlk Simkl görüntüsü alınamazsa çapraz eşitleme, “Simkl boş” varsayımıyla yazmaya devam etmiyor. Son yenileme başarısızsa uzaktan doğrulama yapılmış gibi davranılmıyor.

### 8. P1 — Sonuç ekranı koşulsuz “eksiksiz” diyordu

Platform hata sayıları olsa bile final mesajı tüm hesapların başarıyla/eksiksiz eşitlendiğini söylüyordu.

Düzeltme: Hata varsa kısmi tamamlanma özeti; atlanan öğeler için platform özetini inceleme mesajı. Toplu Simkl hatalarının yalnızca ilki değil tamamı günlüğe ekleniyor. Sayaçlar liste onayını ifade eder; geçmiş/puan dahil uçtan uca öğe başarısını kanıtlamaz. Hatalı grup için hata sayısı grup büyüklüğüdür; onay sayısıyla çakışabilir.

### 9. P1 — Kitsu kimliği gerçek MAL kimliğiyle karışabiliyor

Birleştirme `malId` alanını gerçek MAL kimliğiyle doldururken `source = "kitsu"` kalabiliyor. Eski batch eşlemesi yalnızca kaynağa bakarak bu değeri `ids.kitsu` olarak da gönderiyordu.

Düzeltme: Toplu aktarımda Kitsu kimliği yalnızca importer'ın kullandığı `300_000_000` ofsetli değerden çıkarılıyor. MAL kimliği film/diziler için gönderilmiyor. Kimlik modeli tamamen yeniden tasarlanmış değildir; tekil yoldaki benzer miras eşleme ayrıca ele alınmalı.

### 10. P2 — Simkl'deki mevcut puan farkları atlanıyordu

Güncelleme koşulu yalnızca mevcut puan `null` ise puan farkını dikkate alıyordu. Diğer alanlar eşitse örneğin 6 → 8 gönderilmiyordu.

Düzeltme: Birleşik geçerli puan mevcut Simkl puanından farklıysa güncelleme kuyruğuna alınıyor. Hangi kaynağın puanının kazanacağı ayrı bir çatışma politikasıdır.

## Önceki düzeltmeler neden yeterli olmadı?

`RELEASE_NOTES.md` başlık/yıl, 35'lik grup, 1.1 saniye gecikme ve sunucu doğrulamasından söz ediyor. Bunlar doğru JSON zarfının veya doğru parser'ın yerine geçmez. Yanlış alanı daha yavaş göndermek yine yanlış istektir. Yerel fallback de gerçek sunucu doğrulaması değildir.

Resmî örneklerde yalnızca `ids` ile gönderim bulunuyor; “başlık ve yıl her istekte zorunlu” çıkarımı doğru değil. İncelenen dokümanda önceki kod yorumlarının iddia ettiği evrensel “50 öğe / istek” ve “1 istek / saniye” kurallarını doğrulayamadım. 35'lik grup ve gecikme burada muhafazakâr uygulama tercihidir; garanti değildir.

## Henüz çözülmeyen mimari riskler

1. ~~**Ortak hız sınırlayıcı yok.**~~ **→ GİDERİLDİ (2026-10-25):** Per-instance `awaitSimklRateLimit` tüm çağıranları kapsamıyordu; özellikle her ekranda yeniden oluşturulan istemciler ve doğrudan import/profil çağrıları bu limiti atlayabiliyordu. `SimklRateLimitInterceptor` artık ortak OkHttp taşıma katmanında tüm `api.simkl.com` çağrılarını yönetiyor: GET'lerde 350 ms aralık, yazımlarda tek sıra + 1.25 sn aralık, 429 ve HTTP 400 `rate_limit` yanıtlarında Retry-After/cooldown. Otomatik yeniden denemeden başarıyla çıkan ara 429'lar kullanıcıya hata olarak kaydedilmiyor. Hesaba özgü kota/ban durumu canlı Simkl isteğiyle doğrulanmadı.
2. **Activities/delta eksik.** `fetchAllLists` tam liste okuyor. `SimklSyncRepository.performDeltaSync` de yorumunda belirtildiği gibi tam yeniden okuma yapıyor. Resmî doküman düzenli eşitlemede Activities + `date_from` ister ve uyumsuz istemcilerin client ID'sinin askıya alınabileceğini söyler. Bu kod bir risk taşır; bu hesabın/anahtarın gerçekten askıya alındığına dair kanıt yok. Kalıcı cursor + cache + silme uzlaştırması tasarlanmadan rastgele `date_from` eklenmemeli.
3. **Birleştirme politikası tam çift yönlü eşitlik değil.** En yüksek ilerleme korunuyor, silmeler aktarılmıyor, tekrar izleme ve farklı puanlar için gerçek kaynak zamanları gerekir. Import edilen `updatedAt` alanlarının güvenilirliği ayrıca doğrulanmalı. Bu davranış “her alanı birebir aynalama” olarak sunulmamalı.
4. **Tekil güncelleme yolu hâlâ ayrı.** `syncEntryToSimkl` içinde kümülatif ilerlemenin tek bölüm olarak yazılması, kimlik/ofset mantığı ve sonuç yönetimi toplu yolla tamamen birleştirilmiş değil. Toplu onarımlar tekil scrobble davranışını bütünüyle düzeltti anlamına gelmez.
5. **Token varlığı sağlıklı bağlantı kanıtı değil.** Bağlı hesap tespiti saklanan token'a dayanıyor. Doğru Authorization/API-key başlıkları tek başına token'ın geçerli olduğunu göstermez. OAuth veya API-key engeli ancak gerçek yanıtla ayrıştırılabilir.
6. **Güvenlik notu:** Build yapılandırmasında OAuth secret fallback değerleri var. Değerler bu rapora alınmadı. Mobil uygulamada gömülü secret gizli kabul edilemez; sağlayıcı izin veriyorsa public-client/PKCE yapılandırması ve yayımlanmış sırların rotasyonu ayrı değerlendirilmelidir. Bu, eşitleme arızasının kanıtlanmış nedeni değildir.

## Değişiklikler ve doğrulama durumu

- `SimklSyncContract.kt`: ortak yazma/okuma anahtarları, yanıt doğrulaması, anime bölüm dizisi.
- `SimklApiClient.kt`: batch sözleşmeleri, tekil yazma zarfları, liste yanıtı doğrulama, liste retry beklemesi.
- `SimklImportManager.kt`: anime okuma ve hata/boş-liste ayrımı.
- `SimklSyncManager.kt`: toplu hata yayılımı, sıra, güvenli TV kısıtlaması, kimlik düzeltmesi.
- `AuthViewModel.kt`: sahte yerel fallback kaldırma, sonuç özeti, puan farkı ve günlükler.
- `SimklSyncContractTest.kt`: 8 JVM regresyon testi; gerçek JSON implementasyonu test bağımlılığı eklendi.

Kontroller:
- `git diff --check`: geçti.
- Değişen Kotlin/Gradle dosyaları sözdizimi parser'ından geçirildi. Parser'ın mevcut kodda da bulunan satır-başı `catch` biçimi kısıtı, yalnızca analiz metninde normalleştirildi. Bu derleme veya tip kontrolü değildir.
- Gradle unit test çalıştırma denemesi **Java bulunamadığı için başlamadı** (`JAVA_HOME` yok, `java` PATH'te yok). Android derleme, JUnit çalıştırma ve cihaz testi yapılmadı. “8 test geçti” iddiası yok.
- Kullanıcı hesabında ekleme/silme yapılmadı.

## Cihazda kabul testi — büyük aktarımı tekrar etmeden önce

Önce Android derlemesini ve `*SimklSyncContractTest` testlerini uygun varyantta çalıştırın (projede `gms`/`foss` flavor'ları var). Sonra küçük, kontrollü bir listeyle:

1. Simkl web'de var olduğu bilinen bir animeyi içe aktarın. Aynı Simkl/MAL kimliğiyle görünmeli; boş kütüphane olmamalı.
2. Planlanan bir animeyi MAL ID ile gönderin. `shows`, `to=plantowatch`, `added.shows` ve tekrar okuma uyumlu olmalı.
3. 12 bölümlük animede ilerleme 3 gönderin. Yalnızca üçüncü değil 1, 2, 3 işaretlenmeli; nihai durum doğru kalmalı.
4. İlerleme 3 + `hold` durumunu gönderin. History sonrası son durum tekrar `hold` olmalı.
5. Tamamlanmış film gönderin. History'de `movies`; sezon/bölüm olmamalı.
6. Mevcut puanı 6'dan 8'e değiştirin. Aktarım tetiklenmeli ve yeniden okumada 8 görünmeli.
7. Aynı küçük grubu tekrar gönderin. Yeni kopyalar oluşmamalı; sıfır işlem/önceden mevcut yanıt semantiği kaydedilmeli.
8. Mock yanıtlarla 401, 429, 500, `not_found`, bozuk/boş JSON ve son yenileme hatasını test edin. “Eksiksiz eşitlendi” veya sahte Simkl kayıtları oluşmamalı.
9. Çok sezonlu TV ilerlemesinde açık eşleme uyarısı bekleyin; sezon 1'e toplam bölüm numarası yazılmamalı.

Tanılama için gerekli kayıtlar: uç nokta yolu, HTTP kodu, grup boyutu, `added`/`not_found` sayıları, tekrar deneme bilgisi, anonimleştirilmiş örnek medya türü ve kamuya açık medya ID'si. **Authorization, access token, client secret veya kullanıcıya ait tam kütüphaneyi paylaşmayın.**

Bu kontroller tamamlanmadan “Simkl entegrasyonu tamamen düzeldi” sürüm notu yazılmamalı.
