# KİTSUGİ — Drive Çökme Raporları Analizi ve Kök Neden Çözümleri (2026-11-20)

**Kaynak:** Google Drive klasörü `10Tf80BfxNpNMHn8IYIjvuGSv6TtX5hpF` (6 dosya, tek oturum)
**İnceleme dalı:** `arena/01b9b6a2-kitsugi-beta`
**Rapor tarihi:** 2026-11-20
**Hedef sürüm:** 2.4.492-beta

---

## 1. İncelenen dosyalar

| # | Dosya | Boyut | Durum |
|---|-------|-------|-------|
| 1 | `device_info.txt` | 277 B | ✅ Tamamı okundu |
| 2 | `Kitsugi_Crash_Report (1).txt` | 587 B | ✅ Tamamı okundu |
| 3 | `kitsugi_debug (1).log` | 27 KB | ✅ Tamamı okundu |
| 4 | `live_logcat_report (1).txt` | 57 KB | ✅ 7/8 parça (1 parça araç hatası) |
| 5 | `crash_history (1).txt` | 608 KB | ✅ Çökme bloğu + tüm oturum başlıkları |
| 6 | `kitsugi_runtime (1).log` | 2,3 MB | ✅ Başlangıç + çökme penceresi (15:17:41–15:18:20) |

**Cihaz:** Xiaomi 23043RP34C (`pipa`), Android 13 (API 33), arm64-v8a, heap 8 MB/512 MB
**APK:** v2.4.491-beta.29919588

> **Kapsam notu:** Drive aracı büyük dosyalarda yalnızca baş + son bölümü veriyor.
> `crash_history` dosyasının `/proc/maps` bölgesi (parça 38–66) yalnızca bellek haritası
> içeriyor; çökme imzası envanteri parça 34–37'den çıkarıldı. `live_logcat_report`
> parça 4 araç tarafında kalıcı olarak hata veriyor (dosya kusuru değil).

---

## 2. ASIL BULGU — 💥 Native SIGABRT: `FORTIFY: write: count 18446744073709551615`

```
2026-11-20 15:18:10  [UNCLEAN EXIT] pid=27597, çalışma 151 sn, ÖN PLAN
                     SIGNALED (2) | önem=125 | durum=6 | PSS=342 MB
                     ekran: ApiResultDetail(Merlin, tmdbId=7225)
F libc  : FORTIFY: write: count 18446744073709551615 > SSIZE_MAX
KITSUGI NATIVE CRASH   signal=SIGABRT (6)   thread=MPEG4Writer   tid=30102
```

`18446744073709551615` = `size_t(-1)` = **-1 bayt**. Yani kapsayıcı yazıcısına
`writeSampleData(..., BufferInfo.size = -1, ...)` çağrısı yapıldı. `MPEG4Writer` bu değeri
`::write()`'a `size_t` olarak geçirdiğinde glibc/bionic FORTIFY `__write_chk` **anında**
`__fortify_fail()` çağırır → `SIGABRT`. Bu, Java tarafında **yakalanamayan** bir çökmedir:
hata `MPEG4Writer` native iş parçacığında oluşur, `catch (t: Throwable)` onu asla görmez.

Aynı pencerede çökme zeminini hazırlayan üç faktör daha var:

```
15:17:41 – 15:17:57  Background concurrent copying GC freed … LOS objects 33-57MB
                     (her ~300 ms'de bir, 175-212 ms duraklama)  → GC fırtınası
15:17:57             [TrAnimeIzle] search('Merlin')  120000ms içinde tamamlanamadı
                     [TrAnimeIzle] search('梅林')    120000ms içinde tamamlanamadı
                     [TrAnimeIzle] search('魔術師MERLIN(マーリン)') 120000ms …
15:17:58             W/PesReader: Unexpected start code prefix: 5438311   (bozuk TS)
15:17:56             D/MPEG4Writer: PreAllocation enabled / csd0 too small
                     E/ExtendedUtils: csd0 too small
```

`csd0 too small` = muxer'a **eksik codec başlığı** verilmiş demektir; bu da yukarıdaki
zincirin bir sonucu.

### 2.1 Zincir (neden -1 bayt yazıldı?)

`MediaMuxer.writeSampleData` örnek boyutunu `ByteBuffer.limit()`'ten değil
`BufferInfo.size` alanından okur. Depoda **10** `MediaMuxer(...)` kurulumu var; **4'ünde**
boyut doğrulanmadan yazılıyordu:

| Dosya | Satır (önce) | Hatalı davranış |
|-------|--------------|-----------------|
| `core/download/video/MediaStreamMuxer.kt` | 114, 126 | `readSampleData()` sonucu doğrudan `info.set(0, size, …)`'a; `size` negatifse native yazıcıya `-1` gider |
| `core/download/MediaTrackRemuxer.kt` | 664 (`tryExtractViaMediaMuxer`) | Media3 `sampleData()` çıktısı doğrulanmadan `sampleBytes.size` yazılıyor |
| `core/download/MediaTrackRemuxer.kt` | 972 (`tryRemuxMuxedFileToMp4`) | aynı + ses izine **her zaman** `BUFFER_FLAG_KEY_FRAME` |
| `core/download/MediaTrackRemuxer.kt` | 1048 (`tryMuxSeparateVideoAndAudio`) | aynı + ses izine key-frame bayrağı |
| `data/trailer/YouTubeDownloadEncoder.kt` | 136 (`copyTrack`) | `size < 0` kontrolü var ama `size == 0` ve PTS doğrulanmıyor; `muxer` `finally`'de hâlâ yazarken `release()` edilebiliyor |

Ek olarak **muxer yaşam döngüsü** hatalıydı: `MediaRemuxer.tryRemux*` ve
`muxVideoAndAudio`, `muxer.stop()` çağrısını `runCatching` içine alıp sonucu yok sayıyor,
sonra `finally`'de `release()` ediyordu. `stop()` başarısız olduğunda moov atomu yarım kalır
ama dosya "başarılı" sayılıp kullanıcıya sunuluyordu; üstelik yazıcı iş parçacığı hâlâ
canlıyken `release()` çağrısı native tarafta tanımsız davranışa açıktır.

**Neden bu oturumda patladı:** indirme, `s427.rapidrame.com` / `vidcache.net` gibi bozuk
MPEG-TS parçaları üreten CDN'lerden geliyordu (`PesReader: Unexpected start code prefix`).
Bozuk TS → eksik `csd-0` (`csd0 too small`) → `readSampleData` `-1`/`0` →
`BufferInfo.size = -1` → FORTIFY → SIGABRT.

---

## 3. Bulgu #2 — 🔁 Jikan: iptal → devre kesici → boş raflar; miribyou `top/*` 404

`live_logcat_report` ve `kitsugi_runtime` boyunca **yüzlerce** `IOException: Canceled`:

```
net|api.jikan.moe|… → bağlantı hatası | … ms | IOException: Canceled
JikanGateway.executeOnce → fetchOnHost → fetchChain → fetch → jikanListOrEmpty
                        → mergedMangaTypeShelf
```

**Kök neden:** `JikanGateway.recordHostOutcome()` iptali **sunucu hatası** sayıyordu
(`code == 0` sayesinde). Kullanıcı ekranı hızlı değiştirdiğinde 3 iptal üst üste gelince
host'un devre kesicisi **5 dakika** açılıyor, tüm Jikan rafları boş dönüyordu.
Ayrıca `fetch()` içindeki "sahip iptal edildiyse yeniden dene" mantığı **sınırsız
özyinelemeli** idi — günlükteki ardı ardına aynı yığınların kaynağı bu.

**İkinci alt bulgu:** `miribyou` host'u `top/*` uçlarını reddediyor
(`GET /v4/top/manga → HTTP 404`, 1080 ms). Her ana sayfa açılışında önce miribyou denenip
404 alınıyor, sonra Tenrai'ye gidiliyordu — boşa tur + kota.

**Üçüncü alt bulgu (raf tekrarı):** `JikanSearchClient` içinde
`publishingManga` ve `completedManga` **aynı** `ranking_type=manga` isteğini yapıyordu;
`newlyAddedAnime` ise `topAnime` ile aynı `ranking_type=all` isteğini. Yani üç raf
kullanıcıya aynı içeriği gösteriyor, her biri için fazladan MAL isteği harcanıyordu.

---

## 4. Bulgu #3 — 🧠 WebView sızıntısı → GC fırtınası (çökmenin zemini)

```
WebViewResolver: WebView timeout after 120000ms — https://www.tranimeizle.live/arama/Merlin
```

`core/network/WebViewResolver.kt` içinde:

- zaman aşımında WebView **hiç yok edilmiyordu** (`withTimeoutOrNull` yalnızca coroutine'i
  iptal eder; `invokeOnCancellation` iptal edilmiş continuation'da garanti değildir) →
  her CF denemesi bir WebView + renderer süreci + onlarca MB native bellek sızdırdı,
- "challenge çözüldü" kararı `!url.contains("challenge")` idi; Cloudflare normal bir
  adrese (`/arama/...`) yönlendirdiğinde meydan okuma ekranda olsa bile **boş çerezle**
  "çözüldü" deniyordu → sonsuz yeniden deneme,
- bu sızıntı, §2'deki LOS GC fırtınasının doğrudan sebebi.

---

## 5. Bulgu #4 — ⏱️ Aynı eklentide üst üste 120 sn'lik zaman aşımı

```
[TrAnimeIzle] ⏱️ search('Merlin') 120000ms içinde tamamlanamadı
[TrAnimeIzle] ⏱️ search('梅林') 120000ms içinde tamamlanamadı
[TrAnimeIzle] ⏱️ search('魔術師MERLIN(マーリン)') 120000ms içinde tamamlanamadı
```

`stageTimeoutMs()` her çağrıda CF sabır bütçesini (120 sn) yeniden veriyordu. Aynı eklenti
üç varyantla üç kez 120 sn bekledi → ~6 dakika kilitli arama hattı, kuyrukta bekleyen
eklentiler, tam bu sırada çökme.

---

## 6. Yapılan düzeltmeler

### 6.1 Yeni dosya — `core/download/MuxSampleGuard.kt` (Saf JVM + birim testli)

Tüm muxer yazımlarının geçtiği tek savunma hattı:

- `isWritableSampleSize(size)` → `size in 1..32 MB` ( `-1` ve `0` **asla** yazılmaz ),
- `clampPtsUs(pts)` → negatif PTS `0`, 24 saat üzeri PTS üst sınıra sabitlenir
  (`MPEG4Writer` chunk süresi taşması engellenir),
- `nextMonotonicPtsUs(…)` → iz başına **kesin artan** PTS (eşit/azalan PTS moov'u bozar),
- `audioSampleFlags()` → **her zaman 0** (ses izlerinde anahtar kare kavramı yoktur),
- `videoSampleFlags(…)` → key-frame bayrağı yalnızca gerçekten senkron örnekte.

`app/src/test/.../MuxSampleGuardTest.kt` — 15 regresyon testi.

### 6.2 Muxer yaşam döngüsü (4 dosya)

- `MediaStreamMuxer`: örnek boyutu + PTS doğrulaması, bozuk örnek sayacı, `stop()` **tek
  kez** ve yalnızca tüm yazmalar bittikten sonra; `stop()` başarısızsa çıktı **geçersiz**
  sayılır (çağıran iki dosyayı ayrı saklar). `MAX_SAMPLES_PER_TRACK` ile sonsuz döngü kilidi.
- `MediaRemuxer`: her iki muxer yolunda da boyut/PTS doğrulaması, `stop()` sonucu kontrolü
  (`MuxOutcome.CompanionAudioRequired` / `KeepOriginalTs`), ses bayrakları sıfırlandı,
  `toCodecBufferFlags` artık `MuxSampleGuard` üzerinden.
- `MediaTrackRemuxer`: üç muxer yolu da `MuxSampleGuard`'a bağlandı; `released` bayrağı ile
  çift `release()` engellendi; `stop()` başarısızlığında dosya silinip `false` dönülüyor
  (eskiden yarım moov'lu dosya geçerli sayılabiliyordu).
- `YouTubeDownloadEncoder.copyTrack`: boyut + PTS doğrulaması, ses/video bayrağı ayrımı,
  yazılan örnek sayısı döndürülüyor; `stop()` başarısızsa dosya silinip `false`.
- `UniversalAudioTranscoder.AacM4aEncoderSink`: yazma öncesi boyut kontrolü, monoton PTS,
  `abort()` artık `stop()` çağırmıyor (`aborted` bayrağı).
- `AudioDemuxer`: ses örneklerindeki `BUFFER_FLAG_KEY_FRAME` kaldırıldı.

### 6.3 Jikan / metadata

- `recordHostOutcome`: `Canceled` / `Job was cancelled` artık **hata sayılmıyor** →
  devre kesici iptal yüzünden açılmıyor.
- `executeOnce`: `CancellationException` yeniden fırlatılıyor (yutulmuyor).
- `fetch()`: sınırsız özyineleme → `MAX_OWNER_ATTEMPTS = 2`.
- `hostsFor`: miribyou `top/*` için 404 aldıysa o uçlar doğrudan Tenrai'ye gider.
- `JikanSearchClient`: `publishingManga` → Jikan `status=publishing`, `completedManga` →
  Jikan `status=complete`, `newlyAddedAnime` → `seasons/now`, `newlyAddedManga` →
  `status=publishing&order_by=start_date`. Raflar artık **farklı** içerik gösteriyor ve
  gereksiz resmî MAL çağrısı yapılmıyor.

### 6.4 WebView / CF

- `WebViewResolver`: WebView başarı/iptal/zaman aşımı **her** durumda tek sefer yok edilir
  (`settled` bayrağı + `finally` benzeri `finish()`), `clearance` kararı gerçek
  `cf_clearance`/`__ddg*` çerezine dayanır (boş çerez "çözüldü" sayılmaz).
- `CsStreamRunner.stageTimeoutMs`: CF sabır bütçesini zaman aşımıyla tüketen eklenti
  5 dakika boyunca **hızlı** bütçeyle çağrılır; başarılı aramada soğuma temizlenir.

### 6.5 İptal / ağ hijyeni

- `RetryInterceptor`: `InterruptedIOException` (callTimeout / iş parçacığı kesintisi) artık
  yeniden denenmiyor. 2026-11-20 günlüğünde `NiceHttp InterruptedIOException timeout`
  satırları her denemede 3 katına çıkıyordu. `SocketTimeoutException` (yavaş CDN) yine
  yeniden denenir — davranış kasıtlı olarak korundu.
- `VideoDownloadManager.streamToFile`: coroutine iptal edildiğinde bloklayan OkHttp çağrısı
  artık `call.cancel()` ile kesiliyor (`invokeOnCompletion`). Eskiden iptal edilen indirme
  `readTimeout` (30 sn) boyunca çalışmaya devam edip günlüğe `IOException: Canceled`
  yazıyordu.

### 6.6 Bellek

- `KitsugiMemoryGuard.watchdogTick`: yalnızca `used/maxMemory()` değil, **committed** alan
  da ölçülür (`used/committed ≥ %85` veya `used/max ≥ %70` → temizlik). 512 MB heap'li
  cihazda "83 MB kullanım → %16, koruma tetiklenmiyor" körlüğü giderildi; LOS fırtınası
  artık fark ediliyor.
- `KitsugiCrashLogger.appendToHistory`: geçmiş dosyası artık belleğe **tamamı** okunmuyor;
  son N bayt okunup geçici dosyaya yazılıp atomik olarak yer değiştiriliyor (çökme anında
  bellek baskısı azalır, yarım yazma geçmişi bozamaz).

---

## 7. Doğrulama

- `app/src/test/java/com/kitsugi/animelist/core/download/MuxSampleGuardTest.kt` — `-1`, `0`,
  `MAX+1` boyutları reddedilir; negatif/aşırı PTS kırpılır; eşit/azalan PTS kesin artana
  çevrilir; ses bayrağı 0; ses izinde key-frame üretilmez. **15 test.**
- Tüm değiştirilen dosyalar brace-balance denetiminden geçti (sandbox'ta Android SDK /
  Kotlin derleyicisi yok — derleme doğrulaması CI'da yapılmalı).
- Davranışsal güvence: mux başarısız olduğunda çağıran taraf **zaten** iki dosyayı ayrı
  saklıyor (`AnimeDownloader.finalizeDownloadedMedia` → `CompanionAudioRequired`);
  artık "yarım moov'lu bozuk mp4" teslim edilmiyor.

---

## 8. Sonraki adımlar (bu turda kapsam dışı)

1. `crash_history` / `live_logcat_report` dosyalarının okunamayan bölgeleri (araç kısıtı).
2. `Kitsugi-Plugins` tarafında TrAnimeIzle CF arama kapısı: 120 sn yerine ~35 sn hedefi.
3. `AnimeDownloadService` için indirme sırasında öncelik düşürme (mux sırasında GC fırtınası
   engellensin).
