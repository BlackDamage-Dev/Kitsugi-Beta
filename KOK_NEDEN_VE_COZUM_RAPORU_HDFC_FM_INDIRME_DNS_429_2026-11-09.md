# KÖK NEDEN VE ÇÖZÜM RAPORU — HDFilmCehennemi / FilmMakinesi İndirme Hataları (DNS + 429)
Tarih: 2026-11-09 · Dal: `arena/1aa6d85c-kitsugi-beta` · Etiket: **T-IND-1**

## 0. Kullanıcı bildirimi
- İndirmeler ekranında (ekran görüntüsü): Supernatural Bölüm 14/15 — HDFilmCehennemi kaynağı:
  `Unable to resolve host "srv8.cdnimages936.cyou": No address associated with hostname`
  ve `Unable to resolve host "srv8.cdnimages1762.cyou": No address associated with hostname`.
- Sözlü bildirim: aynı kaynaklardan (HDFilmCehennemi, FilmMakinesi) indirme başlıyor,
  **yarıya kadar gelip** `segment alınamadı: https 429` benzeri hatalarla ölüyor.
- Kullanıcı gözlemi: oynatma çoğunlukla çalışıyor, indirme ölüyor; diğer kaynaklarda indirme sağlam.

## 1. Yöntem
1. Uygulama indirme hattının uçtan uca kod incelemesi:
   `KitsugiStreamScreen → AddonStreamRepository.resolveStreamUrl → AnimeDownloadManager.addDownload →
   AnimeDownloadService → AnimeDownloader → KitsugiHttpClient`.
2. CloudStream çözüm zinciri incelemesi: `CsStreamRunner.resolveEmbedUrl → loadExtractor →
   KitsugiCdnJwExtractor (yerleşik) / eklenti çözücüleri`.
3. Eklenti deposu incelemesi (`f26901964-eng/Kitsugi-Plugins`, HDFilmCehennemi v63 / FilmMakinesi v73):
   `PlayerResolver.kt`, `PlayerDecoder.kt` ve birleşmiş PR #19 ("video kaynakları canlı yapıya göre
   yeniden yazıldı") bulguları.
4. Ağ katmanı incelemesi: `KitsugiHttpClient`, `RetryInterceptor`, `IPv4FirstDns`, `DnsManager/DohProviders`.

## 2. Kök nedenler

### KN-1 — Rotasyonlu CDN host'u + "adres tazeleme" yok (ekrandaki DNS hatası)
- Close/Rapid oynatıcı ailesi videoları **geciçi CDN host'larında** yayınlıyor:
  `srvN.cdnimagesNNNN.cyou/hls/…/master.txt`, `sNNN.rapidrame.com/hls2/…/master.m3u8`
  (PR #19 canlı doğrulaması ile teyitli; ekran görüntüsündeki iki host da bu aileden).
- Bu host'lar saatler içinde ölüp yenileniyor. İndirme URL'si **kuyruğa eklenirken** bir kez çözülüyor
  (`resolveStreamUrl` → `AnimeDownload.url`); indirme SERVİSİ onu sonra işliyor (kuyruk beklemesi,
  uygulama kapalıyken kuyruk, "devam et" denemeleri). Arada host ölünce OkHttp
  `UnknownHostException` fırlatıyor → indirme ERROR'a düşüyor.
- Oynatma neden etkilenmiyor: oynatma çözünürlükten SANİYELER sonra başlıyor; ayrıca oynatıcı
  hata durumunda kullanıcıyı kaynak değiştirmeye yönlendiriyor. İndirme hattında ise ölü URL ile
  yeniden deneme dışında yol YOKTU → "indirme yarıda ölüyor, oynatma çalışıyor" farkının kökü.
- Eklenti tarafı da kuyruk anında ölü host'u fark edemiyordu: `PlayerResolver` çözdüğü master
  adresini doğrulamadan (probe etmeden) yayınlayordu.

### KN-2 — 429 sarmalı: çarpışan retry katmanları + tempo (pacing) yok
- `KitsugiHttpClient.client` üzerindeki `RetryInterceptor(maxRetries=2)` her 429/5xx/ağ hatasında
  isteği anında (Retry-After üstü 5 sn) 2 kez daha gönderiyor.
- `AnimeDownloader.downloadSegmentToStream` ayrıca 3 deneme yapıyordu → **tek segment için 9'a kadar
  istek** kısa pencerede çıkıyordu. CDN (Referer/Origin hassas, hız sınırlı) 429'u büyütüyordu:
  "segment alınamadı: 429" sarmalı.
- Segment istekleri arasında asgari ara (pacing) yoktu; ardışık yüzlerce segment isteği aynı host'a
  delici biçimde gidiyordu.
- `Retry-After` başlığı indirme bağlamında en fazla 5 sn okunuyordu; CDN'in istediği soğuma
  sağlanamıyordu.

### KN-3 — Close CDN `master.txt` adresleri HLS sanılmıyordu
- `AnimeDownloader` HLS kararını yalnızca `.m3u8` uzantısına veriyordu.
  Close CDN'in master adresi `…/master.txt` olduğundan indirme DOĞRUDAN DOSYA yoluna düşüyor,
  playlist metni video diye yazılıyordu (küçük/bozuk dosya ya da Range denemeleriyle hata).
- Oynatıcı tarafında bu host listesiyle HLS sayılıyordu (`PlayerMediaSourceFactory`); indirme
  tarafında sayılmıyordu → "oynatır ama indirme bozuk" ikinci yolu.

### KN-4 — Kısmi segment yazımı resume hizasını bozuyordu
- Segment doğrudan dosya akışına `copyTo` ile yazılıyordu; deneme yarıda koparsa dosyada KISMİ
  dilim kalıyor, `downloadedSegments` sayacı ilerlemediği için sonraki deneme aynı dilimi tekrar
  ekleyip videoyu kaydırıyordu (resume = bozuk dosya).

### KN-5 — Sistem DNS'i tek nokta hatası
- `IPv4FirstDns` zinciri: aktif DNS → sistem DNS → (codeberg sabit IP) → hata.
  Sistem DNS'i NXDOMAIN döndürdüğünde (TR sağlayıcı DNS'lerinde rotasyonlu `.cyou`/CDN host'ları
  için gözleniyor) DoH'a DÜŞÜLMÜYORDU; oysa `DnsManager/DohProviders` altyapısı mevcut.

### KN-6 — Hata görünmezliği
- İndirmeler ekranı ERROR durumunda yalnızca genel "Bir hata oluştu" yazıyordu; gerçek neden
  (DNS/429/404) kayıtta saklanmıyordu → teşhis ancak logcat ile mümkündü.

### KN-7 — "Kasıp donan" bozuk dosya: devam ettirmede sayaç↔dosya uyuşmazlığı (T-IND-2)
- Bu kaynakların segmentleri **MPEG-TS** (`seg-N-v1-a1.ts`, imzalı URL, `e=14400` ≈ 4 sa TTL).
  TS akışında YİNELENEN dilim = zaman damgası/PAT-PMT geri sarması → her oynatıcıda
  (MX Player dahil) kasma/donma; EKSİK dilim = senkron kaybı → donma.
- Devam ettirme, modeldeki `downloadedSegments` sayacına güveniyordu. Sayaç:
  (a) UI kısıklaştırması (250 ms) nedeniyle dosyaya yazılanın GERİSİNDE kalabiliyor,
  (b) yalnızca durum değişimlerinde diske yazılıyor (uygulama öldüyse daha da bayat).
  Hata→"devam et" akışında sayaç < dosyadaki gerçek dilim sayısı olduğunda, zaten yazılmış
  dilimler dosyaya TEKRAR ekleniyordu → bozuk (kasan) dosya. Bu kaynaklar sık yarıda kaldığı
  için bozulma istatistiksel olarak yalnızca HDFC/FM indirmelerinde görünüyordu.
- Eski koddaki kısmi segment yazımı (KN-4) da aynı belirtiyi büyütüyordu.
- İmzalı URL TTL'si (≈4 sa): uzun bekleyen kuyrukta token ölür → segment 403; T-IND-1 adres
  tazelemesi bunu da kapsar (yeni embed → yeni token), playlist yol anahtarı aynı kaldığı
  için journal ile kaldığı yerden sürer.

### KN-8 — Şifreli (AES-128) ve BYTERANGE playlist'leri çöp dosya üretiyordu (T-IND-3)
- Eski ayrıştırıcı `#EXT-X-KEY` ve `#EXT-X-BYTERANGE` satırlarını YOK SAYIYORDU:
  - AES-128: şifreli segmentler şifreli hâlde birleştiriliyordu → hiçbir oynatıcının açamadığı dosya.
  - BYTERANGE: tüm satırlar aynı URL'yi verdiğinden aynı dosya N kez indirilip birleştiriliyordu
    → dev, bozuk dosya.
  - SAMPLE-AES/WIDEVINE gibi DRM: belirsiz davranış.
- Bu iki özellik HDFC/FM'in TS akışlarında yok ama uygulamanın GENEL indirme hattı tüm
  kaynaklara hizmet ettiği için denetimde kapatılması gereken son açıklardı.

## 3. Çözüm — uygulama (Kitsugi-Beta)

### 3.1 Yeni: `core/download/DownloadResilience.kt` (saf JVM, testli)
- Hata sınıflama: `DNS / RATE_LIMIT / SERVER / STALE_URL / FORBIDDEN / NETWORK / OTHER`
  (cause zinciri + `SegmentHttpException(code, retryAfterMs)`).
- **Kaynak politikası** (`SourceDownloadPolicy`): eklenti adı veya CDN host imzasından
  (`hdfilm`, `filmmakinesi`, `close`, `rapid` / `cdnimages`, `rapidrame`, `rplayer`, `closeload`,
  `playmix` …) → `TR_CDN` politikası:
  - `minSegmentIntervalMs = 250` (segment istekleri arası asgari ara → 429 engelimi),
  - segment başına 6 deneme, playlist başına 4 deneme,
  - üstel backoff 1.5 sn → 30 sn tavan, ±%20 jitter,
  - `Retry-After` 45 sn'ye kadar geçerli,
  - adres yeniden çözümü izinli, indirme başına en fazla 2 kez.
- `reconcileResumeIndex(...)`: adres tazelendikten sonra yeni playlist'in segment YOL dizisi
  eskisiyle birebir aynıysa kaldığımız dilimden devam; değilse BAŞTAN (bozuk bayt yok).
- `parseRetryAfterMs`, `backoffMs`, `segmentPathKey` yardımcıları.

### 3.2 Yeni: `core/download/DownloadStreamResolver.kt`
- Ölü URL tespitinde indirme kaydıyla saklanan **embed adresinden** (`recoverUrl`) aynı çözücü
  zincirini yeniden çalıştırır: önce CS `loadExtractor` (eklenti çözücüsü öncelikli),
  önek kaçtıysa yerleşik `KitsugiCdnJwExtractor` doğrudan.
- Üretilen taze linkten HLS tercihli seçim + başlık tamamlama (Referer/UA).

### 3.3 `AnimeDownloader` yeniden yapılandırıldı
- Playlist indirme: politika denemeleri + backoff; master→varyant seçimi korunur.
- **`master.txt` / `playlist.txt` artık HLS sayılır** (KN-3).
- Segment döngüsü: tempo (pacing) + `downloadSegmentBytes` (segment ÖNCE belleğe, sonra dosyaya →
  KN-4 kısmi yazım bitti) + politika backoff/Retry-After.
- Segment/init hatasında `recover(...)`: embed'den taze link → yeni playlist → resume uzlaşısı →
  gerekirse dosyayı baştan aç + init segmenti yeniden yaz → kaldığı yerden devam (KN-1).
- Doğrudan dosya indirme: politika denemeleri + Range resume korunarak backoff/Retry-After.
- Hata mesajı sınıflandırılmış Türkçe metinle UI'a taşınır (KN-6).
- Tüm indirme istekleri artık `KitsugiHttpClient.downloadClient` ile (bkz. 3.5).

### 3.4 Kayıt/UI hattı (recoverUrl + errorMessage)
- `AnimeDownload`: `recoverUrl`, `recoverReferer`, `errorMessage` alanları (Gson geriye uyumlu).
- `StreamSource`: `embedUrl`, `embedReferer`; `CsStreamRunner.resolveEmbedUrl` tüm çözülen
  akışlara embed adresini damgalar (loadExtractor + wrapper + derin tarama yolları dahil).
- `KitsugiStreamScreen.addDownload(...)`: `recoverUrl/recoverReferer` kuyruğa yazılır.
- `AnimeDownloadManager.updateStatus(...)`: `errorMessage` saklar; QUEUE/DOWNLOADING/COMPLETED
  mesajı temizler ("devam et" sonrası eski hata görünmez).
- `DownloadsScreen`: ERROR satırında gerçek mesaj (örn. "Sunucu adresi çözülemedi …").
- `KitsugiCdnJwExtractor.REGISTRATIONS`: `hdfilmcehennemi.mobi`, `rapid.filmmakinesi.to`,
  `hdfilmcehennemi.nl/rplayer/` önekleri eklendi → eklenti bayat olsa bile tazeleme zinciri çalışır.

### 3.5 Ağ katmanı
- `KitsugiHttpClient.downloadClient`: `RetryInterceptor` YOK (retry çarpımı bitti, KN-2),
  readTimeout 120 sn (yavaş CDN segmentleri).
- `IPv4FirstDns`: sistem DNS'i de çözemezse **DoH zinciri** (Cloudflare → Google, bootstrap IP'li,
  tembel önbellekli) → sağlayıcı DNS NXDOMAIN'lerine karşı ikinci yol (KN-5).

### 3.7 Yeni: `core/download/SegmentJournal.kt` — çökmeye dayanıklı devam ettirme (KN-7)
- Her segment dosyaya yazılıp flush edildikten SONRA `<indis> <boyut>` satırı deftere
  (`<video>.jl`) flush ile eklenir; başlık satırı playlist yol anahtarı + toplam + init baytı taşır.
- Devam ettirmede: başlık mevcut playlist ile eşleşmezse BAŞTAN; dosya boyu ≠
  `initBayt + Σ(defter)` ise dosya defterin kefil ettiği boya KIRPILIR (yarım segment kalıntısı
  atılır); kesintisiz giriş dizisi kadar dilimden devam. Sayaç artık yalnızca UI göstergesidir.
- Defter yoksa kısmi dosya doğrulanamaz → temiz baştan (bozuk dosya üretimi imkânsız).
- İndirme bitince defter silinir; hata durumunda korunur (sonraki "devam et" için).
- Testler: `SegmentJournalTest` (başlık/giriş ayrıştırma, çöp satır eleme, kesinti taraması).

### 3.8 Testler
- `app/src/test/.../core/download/DownloadResilienceTest.kt`: sınıflama, politika seçimi,
  backoff büyütme/tavan, Retry-After ayrıştırma, resume uzlaşısı (host farkı/yol farkı/boyut farkı),
  pathKey host-sorgu bağımsızlığı.

### 3.9 Yeni: `core/download/HlsPlaylistParser.kt` — standartlara tam uyumlu HLS (KN-8)
- Saf JVM ayrıştırıcı: master varyant seçimi (bant genişliği + çözünürlük), media playlist'te
  `#EXT-X-MAP`, `#EXT-X-MEDIA-SEQUENCE`, `#EXT-X-KEY` (AES-128), `#EXT-X-BYTERANGE`, göreli
  adres mutlaklaştırma.
- AES-128: segment indirme sonrası `AES/CBC/PKCS5` ile çözüm; IV = playlist IV'si yoksa
  media sequence (RFC 8216 §4.3.4.4); anahtar aynı URI için önbellekli, 16 bayt doğrulamalı.
- BYTERANGE: `Range: bytes=start-end` ile gerçek aralık indirimi; sunucu 206 döndürmezse
  segment kabul edilmez (tam dosyayı "dilim" diye yazma hatası imkânsız).
- SAMPLE-AES/WIDEVINE: dosya yazılmadan TEMİZ hata ("DRM ile şifreli … indirilemez").
- `DownloadExportNaming.journalFileName`: bölüm silinirken `.jl` defteri de temizlenir
  (diskte çöp kalmaz).
- Testler: `HlsPlaylistParserTest` (varyant seçimi, anahtar/IV, DRM reddi, byterange zinciri,
  göreli adresler) + `SegmentJournalTest`.

## 4. Çözüm — eklentiler (Kitsugi-Plugins)
Dosya: `KITSUGI_PLUGINS_HDFC64_FM74_CDN_PROBE_FIX.patch` (repo kökünde).
- `PlayerResolver.kt` (HDFilmCehennemi + FilmMakinesi, dosyalar özdeş):
  - `probeStreamFile(...)`: çözülen master adresini 6 sn zaman aşımıyla doğrular (2xx + boş olmayan gövde).
  - `alternateStreamUrls(...)`: sayfadaki diğer `master.txt/master.m3u8/playlist.txt/index.m3u8`
    adaylarını toplar; **playmix.\* tuzak host'ları eleme** (PR #19 bulgusu) + `isPlausibleStreamUrl`.
  - `pickLiveStreamFile(...)`: birincil ölüyse ilk CANLI yedeği yayınlar; hepsi ölüyse orijinalle
    devam (kaynağı tamamen kaybetmemek için).
- Sürümler: HDFilmCehennemi 63→**64**, FilmMakinesi 73→**74** (cihaz güncelleme denetimi atsın).
- Uygulama: `git -C Kitsugi-Plugins apply KITSUGI_PLUGINS_HDFC64_FM74_CDN_PROBE_FIX.patch` ile
  uygulanıp depo CI'ında .cs3 üretilebilir. Eklenti güncellenene kadar 3.2/3.4'teki uygulama yerleşik
  zinciri cihazdaki açığı kapatır.

## 5. Davranış karşılaştırma
| Senaryo | Önce | Sonra |
|---|---|---|
| Kuyrukta beklerken CDN host'u ölür | ERROR: Unable to resolve host | Embed'den taze host → kaldığı dilimden devam |
| Segmentte 429 | 9 isteklik sarmal → ERROR | Tempo + backoff + Retry-After → segment alınır |
| `master.txt` kaynağı | Playlist metni "video" diye iniyor | HLS olarak segmentlerine ayrılır |
| Resume sonrası playlist değişmiş | Bozuk/hizalı olmayan dosya | Yol eşleşmesi yoksa temiz baştan |
| Hata→"devam et" sonrası oynatma | Kasma/donma (yinelenen TS dilimleri) | Journal doğrulamalı resume: duplikasyon imkânsız, yarım dilim kırpılır |
| Uygulama indirme ortasında ölür | Sayaç bayat → bozuk devam | Journal flush'li: dosya=defter her zaman |
| AES-128 şifreli akış | Çöp (şifreli) dosya | Segmentler çözülerek indirilir |
| BYTERANGE playlist | Aynı dosya N kez | Gerçek aralık indirimi (206 doğrulamalı) |
| DRM (SAMPLE-AES/WIDEVINE) | Belirsiz/çöp | Dosya yazmadan temiz hata mesajı |
| Sağlayıcı DNS NXDOMAIN | Anında hata | DoH zinciri (Cloudflare/Google) |
| Hata teşhisi | "Bir hata oluştu" | Gerçek mesaj ekranda + sınıflandırılmış metin |

## 6. Doğrulama planı (cihaz)
1. HDFilmCehennemi'nden 1 bölüm kuyruğa al → indirme başlayınca uçak moduna geç 10 sn → aç:
   segment retry + backoff ile devam etmeli (politika TR_CDN, log tag `AnimeDownloader`).
2. Bölümü kuyruğa al, 6-12 saat beklet (ya da host'u manuel bayatlat) → "devam et":
   `DownloadStreamResolver` taze host üretip resume uzlaşısıyla sürdürmeli.
3. Logcat filtreleri: `AnimeDownloader`, `DownloadStreamResolver`, `KitsugiCdnJwExtractor`,
   `Kekik_CloseLoad`, `Rapidrame`.
4. Birim testler: `./gradlew :app:testDebugUnitTest --tests "*DownloadResilienceTest*"`.

## 7. Riskler / sınırlar
- Adres tazeleme embed sayfası da ölürse çalışmaz (site domain rotasyonu ayrı mekanizma:
  `CsStreamRunner` mirror tablosu zaten güncel tutuluyor).
- `recoverUrl` yalnızca CS embed zincirinden gelen akışlarda dolar; doğrudan HTTP addon'larında
  (Stremio vb.) tazeleme yok — o kaynaklarda CDN rotasyonu gözlenmedi.
- Segment bellekte tutulur (tek dilim, tipik 0.5–4 MB); düşük bellekli cihazda ek risk yok çünkü
  dilim yazıldıktan sonra referans bırakılır.
- Eklenti patch'i birleşene kadar cihazlarda eski .cs3 kalabilir; uygulama yerleşikleri kapatır.
