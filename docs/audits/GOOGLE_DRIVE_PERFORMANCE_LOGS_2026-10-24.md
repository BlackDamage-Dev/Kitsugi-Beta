# Google Drive performans/çökme günlükleri — inceleme notu

**İnceleme tarihi:** 2026-10-24  
**Kaynak:** Kullanıcının paylaştığı Drive klasöründeki altı dosya. Önceki turda dosyalar ham metin olarak alınıp mekanik olarak tarandı; ancak kullanıcının istediği satır satır manuel inceleme ve her bulguyu checkout koduna eşleme henüz tamamlanmadı. Bu belge bu nedenle ara çalışma notudur, eksiksiz rapor incelemesi iddiası değildir.

## İncelenen dosyalar

| Dosya | Boyut | Satır | Sonuç |
|---|---:|---:|---|
| `crash_history (8).txt` | 2,047,268 B | 12,031 | 42 crash-report kaydı; iki tekrar eden VerifyError imzası |
| `crash_log (11).txt` | 5,529 B | 91 | 2026-10-21 açılışta AppSettings VerifyError, v2.4.324 |
| `device_info (10).txt` | 264 B | 9 | Xiaomi 23043RP34C, Android 13; raporlanan uygulama v2.4.362 |
| `kitsugi_debug (8).log` | 279,653 B | 1,513 | Oynatıcı oturumları, kaynak hataları ve buffering süreleri |
| `live_logcat_report (9).txt` | 24,108 B | 193 | 2026-10-24 ana-thread takılması ve yan uyarılar |
| `logcat_at_crash (7).txt` | 73 B | 1 | Yalnızca başlık; çökme anı logcat içeriği yok |

## Kök neden ve yapılan düzeltme

### Ana arayüzün kilitlenmesi — doğrudan günlük kanıtı

`live_logcat_report` içinde MIUI Scout, `main` iş parçacığında 2,503 ms uyarı ve 5,007 ms hang kaydı üretmiş. Stack trace şu zinciri açıkça gösteriyor:

`AndroidUiDispatcher` → `KitsugiSimklDetailClient.resolveSimklCrossIds` → `fetchSimklRawJson` → `OkHttp RealCall.execute()`.

Aynı mesaj sonunda UI gecikmesi 6,324 ms olarak ölçülmüş. İkinci stack trace, OkHttp’in TCP fallback beklemesini ve retry interceptor’ındaki beklemeyi de içeriyor. Bu, ağ çağrısının suspend fonksiyon içinde olmasına rağmen UI dispatcher’dan ayrılmadığını doğruluyor.

**Düzeltme:** `KitsugiSimklDetailClient.kt` içindeki Simkl ham JSON ve TMDB yerelleştirilmiş başlık istekleri `Dispatchers.IO`’ya taşındı; 20 saniyelik çağrı tavanı olan `metadataClient` kullanılıyor; coroutine iptali yutulmuyor. `resolveSimklCrossIds` çağrılarının uygulamadaki farklı ekranlardan gelmesi nedeniyle düzeltme, ortak ağ sınırında yapıldı.

### Sınırsız Simkl bellek önbelleği

`rawJsonCache` ve `crossIdCache`, ham JSON/çapraz kimliklerini `ConcurrentHashMap` içinde sınırsız tutuyordu. `extended=full` yanıtları ilişkiler/öneriler içerebildiğinden, ekranlar arasında gezinirken birikme riski vardı. Bunlar sırasıyla 250/1.000 öğelik `BoundedCache` ile değiştirildi; mevcut `KitsugiMemoryGuard` bellek baskısı altında önbellekleri küçültebiliyor.

## Dosyalardaki diğer bulgular

### Crash geçmişi ve crash anı

- 42 raporun tümü iki kök imzaya indirgeniyor: 34 `AppSettings.<init>` ve 8 R8 tarafından yatay birleştirilmiş `androidx.room.r` suspend lambda `VerifyError`. Bunlar v2.4.321, v2.4.322 ve v2.4.324 sürümlerinde tekrarlanmış; aynı 255-Dalvik-yazmaç taşması ailesi.
- Bu tarihsel hata için mevcut checkout’ta `AppSettings` sekiz alt modele ayrılmış ve yazmaç sınırına karşı regresyon testi ile APK DEX doğrulama CI adımı var. Bu eski crash imzası için aynı düzeltme yeniden uygulanmadı.
- `logcat_at_crash` 73 baytlık başlıkla sınırlı. Mevcut crash logger kaynak kodu logcat’i 1.5 saniyeye kadar topluyor ve `main,system,crash` tamponlarını istiyor; bu da eski rapor üretilirken kullanılan crash-capture davranışının değiştiğini gösteriyor.

### AniList delta senkronizasyonu

Canlı logda `SourceListSynchronizer`, AniList’in desteklemediği `updatedAt_greater` argümanını kullanıyor; API 400 veriyor ve kod tam listeye düşüyor. Bu gereksiz tam senkronizasyon/ek ağ yükü doğurabilir. **Bu sınıf ve argüman mevcut checkout’ta bulunmuyor** (`app/src/main/java` ve testlerde eşleşme yok); bu yüzden yanlış bir sorgu değişikliği tahmin edilerek yapılmadı. Günlükteki uygulama v2.4.362 olarak raporlanırken checkout’taki kaynakta bu senkronizasyon bileşeni yok; düzeltebilmek için çalışır durumdaki v2.4.362 kaynak koduyla eşleştirmek gerekiyor.

### Oynatıcı / ağ kaynakları

- 1,513 satırlık debug günlüğünde 20 Media3 `code=2004 / Source error` olayı var; tekrarlayan HDFilmCehennemi ve FilmMakinesi hataları `hls8.playmix.uno` adresinde. Bunlar logda uygulama çökmesi değil, kaynak/akış açılamaması olarak görünüyor.
- 174 buffering olayı içinde 49’u 3 saniyeyi aşıyor; 43’ü `v.animethemes.moe` üzerinde, en uzunu 33.823 ms. Googlevideo akışında da 6.006 ms’lik bir bekleme var. Bunlar güçlü biçimde sağlayıcı/CDN/bağlantı gecikmesini gösteriyor; uygulama gecikmesiyle karıştırılmamalı. Oynatıcı kaynak değiştirme ve motor fallback mekanizması mevcut olsa da bu dış sunucuların yanıt süresi uygulama koduyla garanti edilemez.
- `Simkl historyBatchDetailed` boş/eksik işlem makbuzunu başarı saymıyor; kayıtta bu yüzden hata uyarısı var. Shikimori `429 Retry-After: 3000ms`, AniList/ARM ve TMDB 400/404 kayıtları da dış servislerin kota/ID/yanıt sorunları; mevcut loglarda bunlar ana-thread stack trace’i değil.

### Diğer canlı-log satırları

- `KitsugiCrashActivity` açılırken görülen `proc/mv` `FileNotFoundException`, Xiaomi’nin MIUI freeform sistem sınıfından geliyor; uygulamanın kendi kaynak kodundan kaynaklanan bir Java fatal crash değil.
- `WebView.destroy() called while WebView is still attached` ve 2.903 saniyelik `SynchronizedLazyImpl` monitor contention uyarıları var. Log bu lazy nesnesinin adını veya kilit sahibi initializer’ın çağrı yığınını vermediğinden kesin kod satırına bağlanamıyor; Perfetto/stacktrace olmadan hedefli düzeltme yapmak tahmin olur.
- `Looper` kayıtlarından 21:02’de 4.754 ms süren ikinci bir uzun mesaj da var, ancak o satırda çağrı yığını bulunmuyor. Simkl ağ çağrısı olarak teşhis edilen 21:04 takılmasından ayrı tutulmalı.

## Günlüklerden bağımsız mevcut-kaynak performans taraması

Kullanıcının uyarısı doğrultusunda inceleme yalnızca günlükte görünen çağrı zinciriyle sınırlı tutulmadı. Bu turda mevcut My List veri akışı ve Compose scroll state'i, uzun ömürlü bellek haritaları, UI tarafındaki senkron ağ çağrısı adayları ve ana thread'de dosya/ağ erişimi desenleri tarandı. Bu, bütün uygulamanın cihazda ölçülmüş/kanıtlanmış olduğu anlamına gelmez; aşağıdaki düzeltmeler doğrudan kaynakta görülen sınırsız haritalar içindir.

### Ek bulunan bellek büyümesi riski ve düzeltme

Mevcut kaynakta Simkl dışı şu URL/ID cache'leri sınırsızdı: hero artwork çözümleri, home hero decode/sonuçları, AniList tags/airing alias'ları, Bangumi kurum adları (negatif sonuçlar dahil) ve Kitsu başarısız ID'leri. İçerikleri farklı kayıtlar arasında tutulduğundan sürekli gezinme sırasında süreç ömrü boyunca büyüyebilirdi. Bunlar thread-safe, LRU `BoundedCache` / `boundedSet`'e taşındı (300 / 256+256 / 1.200 / 1.000 / 3.000 giriş) ve bellek baskısı temizleyicisine bağlandı. Home hero hata-yenileme yolu da snapshot map görünümünde `removeAll` yapmamak üzere gerçek cache key'lerini map üzerinden siliyor.

CloudStream DDoS-Guard interceptor'ındaki host başına cookie/positive/negative/probe map ve set'leri de sınırsızdı. Bunlar 256/512 elemanla sınırlandı; host mutex map'i ise host sayısıyla büyümemesi için 128 sabit kilitli stripe'a çevrildi. Aynı host yine seri kalır; hash çakışması farklı host probe'larını nadiren aynı kilitte sıraya koyabilir. Bu sınırlama haritaların ömrünü kontrol altına alır, ancak gerçek cihaz heap etkisi henüz ölçülmedi.

Bunlar kaynakta kesin gözlenen sınırsız-büyüme kusurlarıdır; sağlanan crash raporlarındaki VerifyError'ların veya her genel donmanın sebebi olduğu ileri sürülmüyor.

### My List, senkron ağ ve player buffer pool taraması

Güncel `MyListScreen` içinde scroll konumları `rememberSaveable` dizilerinde tutuluyor ve `produceState` gruplaması arka plan dispatcher'ına taşınmış; repository akışı `conflate + flowOn(Default)` kullanıyor. Bu nedenle tarihsel 2026-10-20 My List kare-başına state yazımını mevcut kaynakta yeniden bulmadım. Görsel taramadaki geniş `rg` eşleşmeleri tek başına kusur sayılmadı. ViewModel kaynaklarındaki `.execute()` çağrılarının incelenen örneklerinde `withContext(IO)` sınırı mevcut; logda açıkça görünen Simkl istisnası ayrı düzeltildi. `RetryInterceptor` içinde hâlâ kasıtlı `Thread.sleep` backoff'u vardır; bu çağrı artık UI dispatcher'ından yapılmamalı ve başka sync çağrı yolları için audit sürmelidir. Bu interceptor davranışı bu turda değiştirilmedi; retry semantiğini topluca kaldırmak güvenli/kanıtlı bir düzeltme olmaz.

`ParallelRangeDataSource` player yolunda ek somut kusurlar bulundu: global buffer pool'un dış `HashMap`'i paralel worker'larca kullanılıyordu; harici chunk görevinin Future'ı yerine boş no-op Future saklanıyordu; loader thread chunk tamamlanana kadar 5 ms polling yapıyordu; per-instance cached thread pool `close()`'ta durdurulmuyor; ve `upstreamFactory == null` iken aynı DataSource eşzamanlı aralık isteklerine verilebiliyordu. Düzeltmede pool `ConcurrentHashMap.computeIfAbsent` oldu ve kullanılmayan buffer sayısı chunk boyutu başına 8 ile sınırlandı; pool bellek baskısı temizleyicisine de kaydedildi. Gerçek `FutureTask` saklanıp 30 sn timeout/cancel ile bekleniyor, 5 ms polling kaldırıldı, tamamlanan her range sonrası sınırlı sliding prefetch window yenileniyor, worker sayısı 1–8 aralığında sabitleniyor ve `close()` task'ları iptal edip executor'ı durduruyor (tamamlanmamış buffer'lar tekrar havuza verilmez). Factory yoksa paralel mod yerine tek bağlantı kullanılıyor; `StreamSpeedTester` da kendi OkHttp DataSource.Factory'sini iletiyor. Bu, source-level player lifecycle/race/polling sorunlarını düzeltir; logdaki dış CDN buffering süresini tek başına açıklamaz ve cihazda henüz doğrulanmadı.

## Doğrulama ve açık sınırlar

- Kaynakta statik kontroller ve `git diff --check` geçti.
- Android birim testleri çalıştırılamadı: bu sandbox’ta `java` ve `JAVA_HOME` yok.
- Cihaz üzerinde yeni APK ile ANR/akış karşılaştırması yapılamadı; bu nedenle “cihazda tamamen çözüldü” iddiası yok. Kod düzeltmesi derlenip cihazda doğrulanmalı.
- AniList `updatedAt_greater` için v2.4.362’deki eksik bileşen, 2.903 saniyelik lazy contention’ın sahibi ve dış video sunucularının gecikmesi açık/ek kaynak gerektiren başlıklardır.
