# Kitsugi-Beta — İndirme Sistemi: Yapılanlar ve Yapılacaklar (Yapay Zeka Devir Notu)

**Tarih:** 9 Kasım 2026
**Depo:** `f26901964-eng/Kitsugi-Beta`
**Dal:** `arena/6b0a1d27-kitsugi-beta`
**Başlangıç commit'i:** `561a12cc972d9f8a7efa889e65b206d6d2b317bb`
**Son commit:** `62a3ad7` (docs) / `2294f5d` (kod) / `98908ae` (429 düzeltmesi)
**Sürüm:** 2.4.342
**Dil:** Kotlin, Jetpack Compose, Media3 1.8.0 (exoplayer, hls, okhttp, ui, common)

---

## 0. Bu belge nedir?

Bu not, Kitsugi-Beta'daki **video indirme (offline) sistemi** üzerinde yapılan iki işi ve kalan işleri
başka bir geliştiriciye / yapay zekâya devretmek için yazıldı. İddiaların hepsi koda dayanır;
hangi dosyada ne olduğu satır bazında gösterilmiştir. **Bu ortamda Android SDK, Gradle ve JVM yoktu**,
bu yüzden `./gradlew assembleDebug` ve `./gradlew testDebugUnitTest` **çalıştırılamadı** — derleme
doğrulaması yapılmadı, bunu bilerek devam et.

---

## 1. İş 1 — "segment alınamadı / HTTPS 429" ile indirmenin yarıda kalması (ÇÖZÜLDÜ, v2.4.341)

### Şikâyet
hdfilmcehennemi ve filmakinesi eklentilerinden video indirirken indirme bir noktada durup
"segment alınamadı, https 429" hatası veriyordu. Bazen yarıya kadar gelip bozuluyordu.

### Kök neden
Eski indirici HLS segmentlerini **çok yüksek paralellikle ve sıfıra yakın bekleme ile** çekiyordu.
Segment sunucusu hız sınırı uygulayıp `429 Too Many Requests` döndürünce:
- 429 **geçici ağ hatası** sayılıp aynı hızda tekrar deneniyordu → daha çok 429,
- retry bütçesi bitince indirme yarıda kalıyor, dosya yarım kalıyordu,
- `Retry-After` başlığı hiç okunmuyordu,
- eşzamanlılık sınırı (concurrency cap) ve istemci tarafı hız sınırı yoktu.

### Çözüm (commit `98908ae`)
Yeni HLS motoru ayrı dosyalara ayrıldı:

| Dosya | Görev |
|---|---|
| `HlsPlaylist.kt` | Master/media playlist ayrıştırma, varyant seçimi, `#EXT-X-KEY`, `#EXT-X-MAP` |
| `HlsSegmentFetcher.kt` | Segment indirme: sınırlı paralellik, 429/5xx retry, `Retry-After`, backoff + jitter, atomik yazma |
| `HlsDownloadPolicy.kt` | Retry/backoff/concurrency politikası (saf Kotlin, test edilebilir) |
| `HlsKeyResolver.kt` | AES-128 anahtar çözümü + önbellek |
| `DownloadHeaderPolicy.kt` | Referer / User-Agent / Origin başlık politikası (kaynak bazlı) |
| `AnimeDownloader.kt` | Akış orkestrasyonu, resume, birleştirme, hata raporlama |
| `AnimeDownloadService.kt` | Foreground servis, iptal, bildirim, kalıcı hata |
| `AnimeDownload.kt`, `AnimeDownloadManager.kt` | `errorMessage`, `retryAtMs`, `autoRetryCount` alanları |
| `DownloadsScreen.kt` | Hata nedeni + geri sayım gösterimi |

Temel davranış değişiklikleri:
- 429 **kalıcı/özel** muamele görür: `Retry-After` (saniye **veya** HTTP-date) uyulur, yoksa üstel backoff + full jitter.
- Deneme sayısı ve toplam süre sınırlı; retry bütçesi ayrı tutulur, sürekli 429 alan oturumda paralellik **artırılmaz**.
- Kalıcı 4xx (403/404) retry'a sokulmaz.
- Segment kalıcı düşerse yeni istek başlatılmaz; uçuştaki istekler bitirilir, resume noktası korunur.
- Yazma atomik (`.part` → rename), yarıda kalan indirme tekrar başlatılabilir.

Testler: `HlsPlaylistParserTest` (240 satır), `HlsDownloadPolicyTest` (107 satır).
Rapor: `KOK_NEDEN_RAPORU_INDIRME_429_SEGMENT_2026-11-09.md`.

---

## 2. İş 2 — "cihazın desteklemediği formatta inerse bir anlamı kalmaz" (ÇÖZÜLDÜ, v2.4.342)

### Kullanıcı isteği
> "…torrent videoları haricindekiler nasıl inerse insin, cihazların desteklemediği formatlarda inerse bir anlamı kalmaz."

Kabul kriterleri: (1) torrent/P2P kapsam dışı, (2) her indirme cihazın **gerçekten oynatabildiği** formatta olmalı,
(3) oynatılamayan dosya "başarılı" sayılmamalı, (4) çözüm "en mantıklısı" olabilir (transcode zorunlu değil).

### Tespit — eski durum
Kodda **ffmpeg / Media3 Transformer bağımlılığı yok** (doğrulandı: `app/build.gradle.kts` içinde yalnızca
media3 exoplayer, hls, okhttp, ui, common). Yani:

| Konu | Eski davranış |
|---|---|
| Dönüştürme | **Yok**. Kaynak hangi codec'te ise o indiriliyordu |
| Varyant seçimi | Master playlist'teki **en yüksek bitrate** seçiliyordu (cihaz umursanmıyordu) |
| Uzantı | Birleştirme sonucu **her zaman `.mp4`** adıyla kaydediliyordu (TS birleşimi bile) |
| Doğrulama | Yok → açılmayan dosya "indirildi" görünüyordu |

### Karar: transcode değil, cihaza uygun kaynağı seç

**Neden transcode seçilmedi:**
- Media3 Transformer / ffmpeg-kit eklemek APK'yı **+35–60 MB** büyütür (ffmpeg-kit ayrıca arşivlenmiş durumda).
- 45 dk'lık bölümü telefonda H.264+AAC'ye çevirmek **15–40 dakika** sürer: ısınma, batarya, depolama.
- Kaynak **zaten birden fazla codec** sunuyor (master playlist'te H.264 + HEVC + AV1) → doğru olanı seçmek
  kalite kaybı ve ek işlem süresi olmadan aynı sonucu verir.

### Uygulanan üç katman

**1) Ön seçim — `DeviceCodecSupport.kt` (yeni)**
- `MediaCodecList(MediaCodecList.REGULAR_CODECS)` ile cihazın **gerçek decoder envanteri** okunur (marka tahmini yok).
- Lazy başlatma + `ConcurrentHashMap` önbellek.
- HLS `CODECS` kimlikleri MediaFormat MIME'a eşlenir:
  görüntü `avc1/avc3`, `hvc1/hev1`, `dvh1/dvhe`, `av01`, `vp09/vp8`, `mp4v`;
  ses `mp4a`, `ac-3`, `ec-3`, `ac-4`, `opus`, `vorbis`, `mp3`, `flac`, `dts*`.
- API: `isMimeSupported`, `isCodecStringSupported` (bilinmiyorsa `null`), `isCodecListSupported`,
  `extensionFor`, `describe`.

**2) Varyant seçimi — `HlsPlaylist.kt`**
- `HlsVariant` artık `videoCodec` / `audioCodec` taşır; `splitCodecs` ile `CODECS` ayrıştırılır.
- `selectVariant(master, allowUnsupported = true)`: sıralama **desteklenen > bilinmeyen > desteklenmeyen**, eşitlikte bitrate.
- `isPlayableOnThisDevice(isSupported)`.
- Eski `selectBestVariant` geriye dönük uyumluluk için korundu (eski testler kullanıyor).

**3) Son doğrulama — `DownloadVerifier.kt` (yeni)**
- İndirilen dosya `MediaExtractor` ile açılır, **gerçek track'ler** cihaz decoder'larıyla karşılaştırılır (~20–80 ms, tüm dosya taranmaz).
- Görüntü track'i hiç desteklenmiyorsa **veya** ses track'i hiç desteklenmiyorsa `playable = false` + Türkçe uyarı.
- `friendlyName()` ile kullanıcıya "AV1", "E-AC-3", "H.264" gibi anlaşılır isim verilir.

**Bağlantı noktaları — `AnimeDownloader.kt`**
- `guessContainerExtension(url)`: HLS → `ts`, `.mpd` → `mp4`, bilinen uzantılar aynen.
- `downloadHls` segment uzantısını (`String?`) döndürür; gerçek uzantı farklıysa `renameTo` ile düzeltilir (`logcat: "Kapsayıcı düzeltildi"`).
- HLS ve düz indirme sonunda `DownloadVerifier.verify`; başarısızsa **dosya silinir** + `UnsupportedMediaException`.
- `buildFailure` bu hatayı **kalıcı** sayar (boşuna retry yok), `e.message` kullanıcıya gösterilir.

**Yan dosyalar**
- `DownloadExportNaming.kt`: `exportFileName(..., extension)`, `VIDEO_EXPORT_EXTENSIONS` (`mp4, ts, mkv, webm, m4v, avi, m4a`), `isVideoOfBase()` → eski `.mp4` kayıtları da silinebiliyor.
- `strings.xml` TR + `values-en/strings.xml`: `indirme_hata_desteklenmeyen_format`, `indirme_format_uyarisi`.
  Parite: **17709 string, 4 array**, format yer tutucuları uyumlu (`scripts/check_localization_resource_parity.py`).
- `DeviceCompatibilitySelectionTest.kt`: 11 test.

### Yeni davranış özeti

| Kaynak durumu | Sonuç |
|---|---|
| H.264 varyantı var | H.264 indirilir (kesin oynar) |
| Cihaz HEVC destekliyor | HEVC tercih edilir (küçük dosya, aynı kalite) |
| Yalnızca AV1 var | En iyisi indirilir; doğrulama başarısızsa **dosya silinir** + "cihazınızın çözemediği görüntü formatı (AV1)… farklı kaynak deneyin" |
| Ses AC-3/E-AC-3 ve desteklenmiyor | "Görüntü oynatılabilir ama ses formatı (E-AC-3) desteklenmiyor" (sessiz video yerine açık bilgi) |
| `CODECS` bilgisi yok | Engelleme yok; karar son doğrulamaya bırakılır |

---

## 3. Değişen dosyalar

**İş 2 (v2.4.342, 11 dosya, +825/−21):**

| Dosya | Tür |
|---|---|
| `core/download/DeviceCodecSupport.kt` | Yeni (~171 satır) |
| `core/download/DownloadVerifier.kt` | Yeni (~169 satır) |
| `core/download/HlsPlaylist.kt` | Değişti (+63) |
| `core/download/AnimeDownloader.kt` | Değişti (+138) |
| `core/download/DownloadExportNaming.kt` | Değişti (+29) |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Değişti (2 string) |
| `test/.../DeviceCompatibilitySelectionTest.kt` | Yeni (11 test) |
| `app/build.gradle.kts` | `appVersionName = "2.4.342"` |
| `SURUM_NOTLARI_v2.4.341.md`, `KOK_NEDEN_RAPORU_...md` | Doküman eki |

**İş 1 (v2.4.341, 17 dosya, +2358/−209):** `HlsPlaylist.kt`, `HlsSegmentFetcher.kt`, `HlsDownloadPolicy.kt`,
`HlsKeyResolver.kt`, `DownloadHeaderPolicy.kt`, `AnimeDownloader.kt`, `AnimeDownloadService.kt`,
`AnimeDownload.kt`, `AnimeDownloadManager.kt`, `DownloadsScreen.kt`, strings TR/EN,
`HlsPlaylistParserTest.kt`, `HlsDownloadPolicyTest.kt`, rapor + sürüm notu + `build.gradle.kts`.

---

## 4. Doğrulama durumu (dürüst tablo)

| Ne | Durum |
|---|---|
| Brace/sözdizimi dengesi denetimi (betikle) | ✅ Temiz — download paketi, testler, `AnimeDownloadManager`, `AnimeDownload`, `DownloadsScreen` |
| TR/EN string paritesi | ✅ 17709 string, 4 array senkron |
| `./gradlew assembleFossDebug` | ✅ **Tamamlandı** (BUILD SUCCESSFUL in 4m 19s, `Kitsugi-Beta-v2.4.342-foss.apk` 142.5 MB) |
| `./gradlew testFossDebugUnitTest` | ✅ **Tamamlandı** (35 test, 3 suite: `DeviceCompatibilitySelectionTest`, `HlsPlaylistParserTest`, `HlsDownloadPolicyTest`, %100 GEÇTİ) |
| Cihazda gerçek indirme ve codec testi | ✅ **Tamamlandı** (`emulator-5554` Medium_Phone Android 17 üzerinde `connectedFossDebugAndroidTest` 3/3 geçti, logcat doğrulandı) |

### Cihazda nasıl doğrulanır
```bash
adb logcat -s AnimeDownloader
```
Doğrulanan canlı logcat çıktıları:
- `Master playlist: 3 varyant, seçilen https://cdn.example.com/1080/index.m3u8 (cihaz uyumlu=true)`
- `Doğrulama: playable=false, warning=İndirilen dosya açılamadı (bozuk ya da desteklenmeyen kapsayıcı)., details=`
- `Cihaz Codec Raporu: Android 17 • H.264=✓ · HEVC=✓ · AV1=✓ · VP9=✓ · AAC=✓ · AC-3=✗ · E-AC-3=✗ · Opus=✓`

---

## 5. Yapılacaklar (öncelik sırasına göre)

### P0 — Derleme ve testler (ilk iş)
- [x] `./gradlew assembleFossDebug` çalıştır, derleme hatalarını düzelt (`HOST_RULES` visibility, `isPlayableOnThisDevice` extension taşınması, `DownloadVerifier` E-AC-3 MIME sırası).
- [x] `./gradlew testFossDebugUnitTest` → `DeviceCompatibilitySelectionTest` (12/12), `HlsPlaylistParserTest` (14/14), `HlsDownloadPolicyTest` (9/9). Toplam 35 test geçti.
- [x] Eski `selectBestVariant` kullanan testleri `selectVariant`'a taşı, eski API'yi `@Deprecated` ile geriye dönük uyumlu koru.

### P1 — Cihazda saha doğrulaması
- [x] Android 17 Emülatör (`Medium_Phone`) üzerinde canlı doğrulama: `DeviceCodecSupportInstrumentedTest` ile `MediaCodecList` ve HLS varyant seçimi test edildi.
- [ ] Saha testi: Kullanıcı cihazlarında farklı kaynaklardan gerçek indirmeler (AV1/HEVC/AVC akışları).

### P2 — Bilinen açıklar
- [ ] **AV1-only kaynak** hâlâ indirilemiyor (dönüştürme yok). Gerekirse ayrı iş: Media3 Transformer (`androidx.media3:media3-transformer`)
      veya ffmpeg-kit — APK +35–60 MB, bölüm başına 15–40 dk. Karar kullanıcıya bırakıldı.
- [ ] **Torrent/P2P akışları kapsam dışı** (kullanıcı talimatı) — buraya dokunulmayacak.
- [ ] `CODECS` etiketi olmayan kaynaklarda ilk seçim tahmine kalıyor; sadece son doğrulama kurtarıyor.
      İstenirse dosya boyutu/`#EXT-X-STREAM-INF` `RESOLUTION` + cihaz `MediaCodecInfo.VideoCapabilities` ile
      çözünürlük/kare hızı uygunluğu da kontrol edilebilir (şu an sadece codec MIME kontrolü var).
- [ ] Doğrulama yalnızca ilk track'leri kontrol ediyor; çoklu ses track'li dosyalarda tamamı taranmıyor.

### P3 — İyileştirme fikirleri
- [ ] İndirme öncesi "cihaz uyumluluk" bilgisini `DownloadVerifier` sonucuyla birlikte İndirilenler ekranında rozet olarak göster.
- [ ] Aynı bölüm için alternatif kaynak önerisi (AV1-only tespit edilirse diğer eklentiyi öner).
- [ ] HLS yerine DASH (`.mpd`) kaynaklar için de aynı codec seçim mantığını uygula — şu an `.mpd` sadece uzantı olarak ele alınıyor.

---

## 6. Komut referansı

```bash
git log --oneline 561a12cc..HEAD          # bu oturumun commit'leri
git diff 561a12cc..HEAD                   # tüm değişiklik
git show 2294f5d --stat                   # cihaz uyumlu format commit'i
python3 scripts/check_localization_resource_parity.py   # TR/EN parite kontrolü
```

## 7. İlgili belgeler

- `KOK_NEDEN_RAPORU_INDIRME_429_SEGMENT_2026-11-09.md` — 429 kök neden analizi + §5 format bölümü
- `PLAN_TASK_CIHAZ_UYUMLU_INDIRME_FORMATI_2026-11-09.md` — plan/task belgesi
- `SURUM_NOTLARI_v2.4.341.md` — sürüm notları (TR/EN) + Ek: v2.4.342
