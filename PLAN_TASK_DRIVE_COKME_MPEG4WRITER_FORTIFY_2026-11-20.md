# PLAN & TASK — Drive Çökme Analizi ve MPEG4Writer/FORTIFY Düzeltmesi (2026-11-20)

**Dal:** `arena/01b9b6a2-kitsugi-beta` (taban: `97b5aa4`)
**Hedef sürüm:** 2.4.492-beta (repo `version.properties` → 2.4.491)
**Kaynak rapor:** `KOK_NEDEN_RAPORU_DRIVE_COKME_MPEG4WRITER_FORTIFY_2026-11-20.md`
**Commit:** `84fcd8b`

---

## 0. Özet (tek paragraf)

2026-11-20 15:18:10'da uygulama, video indirme/birleştirme (remux) sırasında **native
SIGABRT** ile öldü: `FORTIFY: write: count 18446744073709551615 > SSIZE_MAX`,
`thread=MPEG4Writer`. Kök neden, bozuk MPEG-TS parçasından `-1` dönen örnek boyutunun
doğrulanmadan `MediaMuxer.writeSampleData`'ya verilmesiydi. Bu turda **11 kod dosyası**
düzeltildi, **1 yeni koruma sınıfı** (`MuxSampleGuard`) ve **15 birim testi** eklendi.

---

## 1. Görev listesi (tamamlandı)

| # | Görev | Durum | Dosya |
|---|-------|-------|-------|
| T1 | Örnek boyutu / PTS / bayrak doğrulayan ortak koruma sınıfı | ✅ | `core/download/MuxSampleGuard.kt` (yeni) |
| T2 | Regresyon testleri | ✅ | `core/download/MuxSampleGuardTest.kt` (yeni, 15 test) |
| T3 | `MediaStreamMuxer` — `size=-1` yazımı + muxer ömrü | ✅ | `core/download/video/MediaStreamMuxer.kt` |
| T4 | `MediaRemuxer` — 2 muxer yolu (mux + TS remux) | ✅ | `core/download/MediaRemuxer.kt` |
| T5 | `MediaTrackRemuxer` — 3 muxer yolu | ✅ | `core/download/MediaTrackRemuxer.kt` |
| T6 | `YouTubeDownloadEncoder.copyTrack` yeniden yazımı | ✅ | `data/trailer/YouTubeDownloadEncoder.kt` |
| T7 | `UniversalAudioTranscoder` AAC sink + `abort()` | ✅ | `core/download/UniversalAudioTranscoder.kt` |
| T8 | `AudioDemuxer` ses bayrağı | ✅ | `core/player/audio/AudioDemuxer.kt` |
| T9 | WebView sızıntısı + clearance kararı | ✅ | `core/network/WebViewResolver.kt` |
| T10 | Jikan iptal/devre kesici/miribyou 404 | ✅ | `data/remote/JikanGateway.kt` |
| T11 | Raf tekrarı (publishing/completed/newly-added) | ✅ | `data/remote/JikanSearchClient.kt` |
| T12 | CF sabır bütçesi soğuması | ✅ | `data/cloudstream/CsStreamRunner.kt` |
| T13 | Bellek koruması (committed heap) + çökme geçmişi budama | ✅ | `core/memory/KitsugiMemoryGuard.kt`, `core/diagnostics/KitsugiCrashLogger.kt` |
| T14 | İptal hijyeni (RetryInterceptor + OkHttp call cancel) | ✅ | `core/network/RetryInterceptor.kt`, `core/download/video/VideoDownloadManager.kt` |
| T15 | Sürüm + sürüm notları + rapor | ✅ | `version.properties`, `release_notes_v2.4.492.md` |

---

## 2. Açık görevler (bu turda kapsam dışı)

| # | Görev | Öncelik | Not |
|---|-------|---------|-----|
| A1 | CI'da Kotlin derlemesi + `MuxSampleGuardTest` çalıştırma | **Yüksek** | Sandbox'ta Android SDK/Kotlin derleyicisi yok; derleme doğrulanamadı |
| A2 | `Kitsugi-Plugins` → TrAnimeIzle CF arama kapısı ~35 sn'ye indirilsin | Orta | Şu an 120 sn sabır bütçesi kullanıyor |
| A3 | İndirme sırasında öncelik düşürme (mux anında GC fırtınası) | Orta | `AnimeDownloadService` |
| A4 | `crash_history` / `live_logcat` okunamayan parçalar | Düşük | Araç kısıtı (Drive aracı tek parça döndürüyor) |
| A5 | Cihazda doğrulama: 3 farklı HLS kaynağından indirme + mux | **Yüksek** | Özellikle `s427.rapidrame.com` benzeri TR CDN |

---

## 3. Değişen dosyalar (18)

### Yeni (3)
```
app/src/main/java/com/kitsugi/animelist/core/download/MuxSampleGuard.kt
app/src/test/java/com/kitsugi/animelist/core/download/MuxSampleGuardTest.kt
KOK_NEDEN_RAPORU_DRIVE_COKME_MPEG4WRITER_FORTIFY_2026-11-20.md
```

### Değişen — indirme / mux (7)
```
app/src/main/java/com/kitsugi/animelist/core/download/video/MediaStreamMuxer.kt
app/src/main/java/com/kitsugi/animelist/core/download/video/VideoDownloadManager.kt
app/src/main/java/com/kitsugi/animelist/core/download/MediaRemuxer.kt
app/src/main/java/com/kitsugi/animelist/core/download/MediaTrackRemuxer.kt
app/src/main/java/com/kitsugi/animelist/core/download/UniversalAudioTranscoder.kt
app/src/main/java/com/kitsugi/animelist/core/player/audio/AudioDemuxer.kt
app/src/main/java/com/kitsugi/animelist/data/trailer/YouTubeDownloadEncoder.kt
```

### Değişen — ağ / kaynak / bellek / tanı (5)
```
app/src/main/java/com/kitsugi/animelist/core/network/WebViewResolver.kt
app/src/main/java/com/kitsugi/animelist/core/network/RetryInterceptor.kt
app/src/main/java/com/kitsugi/animelist/data/remote/JikanGateway.kt
app/src/main/java/com/kitsugi/animelist/data/remote/JikanSearchClient.kt
app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt
app/src/main/java/com/kitsugi/animelist/core/memory/KitsugiMemoryGuard.kt
app/src/main/java/com/kitsugi/animelist/core/diagnostics/KitsugiCrashLogger.kt
```

### Sürüm / notlar (3)
```
version.properties                    (2.4.489 → 2.4.491)
release_notes_v2.4.492.md             (yeni)
```

---

## 4. Kabul kriterleri

1. `MuxSampleGuardTest` 15/15 geçer.
2. Bozuk/kesik bir HLS parçası içeren indirmede uygulama **çökmez**; mux başarısız olur ve
   iki akış ayrı dosya olarak kaydedilir (kullanıcı veri kaybetmez).
3. `MediaMuxer.stop()` başarısız olduğunda yarım moov'lu `.mp4` **hiçbir** yolda teslim edilmez.
4. `cf_clearance` alınamayan Cloudflare meydan okuması 15 sn içinde WebView'i **yok eder**
   (bellek sızıntısı yok; GC fırtınası yok).
5. Jikan istekleri iptal edildiğinde host devre kesicisi **açılmaz**; raflar boş dönmez.
6. "Devam Edenler" ve "Tamamlananlar" rafları **farklı** içerik gösterir.

---

## 5. Risk ve geri alma

| Risk | Etki | Önlem |
|------|------|-------|
| `MuxSampleGuard` eşiği çok sıkı seçilirse geçerli örnekler atlanır | Seyrek moov'lu mp4 | `MAX_SAMPLE_BYTES = 32 MB` (4K IDR için fazlasıyla geniş); atlanan örnek sayısı `Log.w` ile raporlanıyor |
| `nextMonotonicPtsUs` gerçek VFR zamanlamasını bozabilir | A/V senkron kayması | Yalnızca PTS ≤ son yazılan olduğunda devreye girer (zaten bozuk zaman damgası) |
| WebView kararı sıkılaştığı için CF geçişi başarısız sayılabilir | Eklenti araması boş döner | Zaman aşımında eski davranış korunuyor: mevcut çerezler dönülür |

**Geri alma:** `git revert 84fcd8b` — tek commit, bağımsız.

---

## 6. Doğrulama komutları (CI / yerel)

```bash
./gradlew :app:testDebugUnitTest --tests "com.kitsugi.animelist.core.download.MuxSampleGuardTest"
./gradlew :app:assembleDebug
```

Cihaz testi: HDFilmCehennemi / FilmMakinesi / bir YouTube fragmanı indirmesi → dosyanın
oynatılabilirliği + `logcat | grep MPEG4Writer` satırında FORTIFY olmaması.
