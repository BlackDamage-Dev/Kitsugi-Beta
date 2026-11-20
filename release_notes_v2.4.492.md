# Kitsugi Beta v2.4.492 — MPEG4Writer Native Çökmesi (FORTIFY SIGABRT) Düzeltmesi

[TR]
### 🛠️ Kritik Çökme Düzeltmesi (v2.4.492)
- **Native SIGABRT çözüldü (`FORTIFY: write: count 18446744073709551615`):** Video indirme/birleştirme sırasında `MPEG4Writer` iş parçacığında oluşan ve Java tarafında yakalanamayan çökme giderildi. Nedeni: bozuk MPEG-TS/akış parçalarında `readSampleData()` `-1` döndürdüğünde bu değerin doğrulanmadan `writeSampleData`'ya verilmesiydi (native yazıcı `size_t(-1)` hesaplayıp FORTIFY ile süreç öldürüyordu).
  - Yeni `MuxSampleGuard`: örnek boyutu `1..32 MB` aralığında değilse **yazılmaz**, PTS negatif/absürt ise kırpılır ve iz başına kesin artan olacak şekilde zorlanır.
  - Ses izlerine artık `BUFFER_FLAG_KEY_FRAME` verilmiyor (moov/stss tutarsızlığı önlendi).
  - `MediaStreamMuxer`, `MediaRemuxer`, `MediaTrackRemuxer`, `YouTubeDownloadEncoder`, `UniversalAudioTranscoder`, `AudioDemuxer` bu korumaya bağlandı.
  - `muxer.stop()` sonucu artık kontrol ediliyor: başarısızsa yarım moov'lu dosya **kullanıcıya teslim edilmiyor**, çağıran taraf güvenli yan ses yoluna düşüyor.
- **WebView sızıntısı giderildi:** Cloudflare çözümü zaman aşımına uğradığında WebView yok edilmiyordu; her deneme onlarca MB native bellek sızdırıp GC fırtınasına ve ardından muxer çökmesine yol açıyordu. Artık her durumda tek sefer yok ediliyor ve "çözüldü" kararı gerçek `cf_clearance`/DDoS-Guard çerezine dayanıyor.
- **Jikan iptal/boş raf düzeltmesi:** `Canceled` hataları artık devre kesiciyi açmıyor (5 dakikalık gereksiz kapanış bitti), sahipsiz istek yeniden denemesi sınırlandı. `miribyou` host'unun 404 döndürdüğü `top/*` uçları doğrudan Tenrai'ye gidiyor.
- **Yinelenen raflar düzeltildi:** "Devam Edenler" ve "Tamamlananlar" aynı listeyi gösteriyordu; artık Jikan `status=publishing` / `status=complete` filtreleri kullanılıyor. "Yeni Eklenen" rafı da `topAnime` ile aynı değil.
- **CF zaman aşımı fırtınası:** Aynı eklenti arka arkaya üç kez 120 sn bekliyordu; sabır bütçesini tüketen eklenti 5 dk boyunca hızlı bütçeyle denenir.
- **Bellek koruması:** `KitsugiMemoryGuard` artık yalnızca `maxMemory` oranına değil **ayrılmış (committed)** alana da bakıyor — LOS çöp fırtınası erken fark edilip önbellekler boşaltılıyor. Çökme geçmişi dosyası artık belleğe tamamı okunmuyor.

[EN]
### 🛠️ Critical crash fix (v2.4.492)
- **Native SIGABRT fixed (`FORTIFY: write: count 18446744073709551615`):** The uncatchable crash in `MPEG4Writer` during download/remux was caused by writing an unvalidated `-1` sample size into `writeSampleData` (native writer computed `size_t(-1)` and FORTIFY aborted the process).
  - New `MuxSampleGuard`: samples outside `1..32 MB` are never written, PTS is clamped and forced strictly increasing per track.
  - Audio tracks no longer receive `BUFFER_FLAG_KEY_FRAME`.
  - `MediaStreamMuxer`, `MediaRemuxer`, `MediaTrackRemuxer`, `YouTubeDownloadEncoder`, `UniversalAudioTranscoder`, `AudioDemuxer` all route through the guard.
  - `muxer.stop()` failure is now detected: half-written moov files are never delivered; callers fall back to companion audio.
- **WebView leak fixed:** timed-out Cloudflare challenges leaked a WebView (and tens of MB of native memory) per attempt, causing the GC storm that preceded the crash.
- **Jikan:** cancellations no longer trip the host breaker; retry on cancelled owner is bounded; `miribyou` `top/*` 404 no longer costs a wasted round-trip.
- **Duplicate shelves fixed:** publishing/completed manga and newly-added anime shelves now use distinct queries.
- **CF timeout storm:** plugins that exhaust the 120 s patience budget fall back to the fast budget for 5 minutes.
- **Memory:** `KitsugiMemoryGuard` also watches committed heap; crash-history pruning no longer reads the whole file into memory.
