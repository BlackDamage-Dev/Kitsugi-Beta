# Plan / Task — Cihazın Desteklediği Formatta İndirme

**Tarih:** 9 Kasım 2026
**Proje:** Kitsugi-Beta
**Çalışma dalı:** `arena/6b0a1d27-kitsugi-beta`
**Başlangıç commit'i:** `561a12cc972d9f8a7efa889e65b206d6d2b317bb`
**Uygulama commit'i:** `2294f5d`
**Sürüm:** 2.4.342
**Durum:** Kod değişiklikleri tamamlandı ve push edildi. Android derlemesi ve cihaz doğrulaması bekliyor (bu ortamda Android SDK / Gradle / JVM yok).

---

## 1. İstenen davranış (kullanıcı ifadesi)

> "En mantıklısı hangisi ise ekle düzenle hallet işte **torrent videoları haricindekiler** nasıl inerse insin, cihazların desteklemediği formatlarda inerse bir anlamı kalmaz."

Kabul kriterleri:

1. Torrent/P2P akışları **kapsam dışı** — dokunulmayacak.
2. Torrent dışındaki her indirme cihazın gerçekten oynatabildiği formatta olmalı.
3. Oynatılamayacak dosya "indirildi/başarılı" sayılmamalı.
4. Çözüm "en mantıklısı" olabilir; codec dönüştürme (transcode) zorunlu tutulmadı.

## 2. Tespit (eski durum)

| Konu | Eski davranış | Sonuç |
|---|---|---|
| Kapsayıcı/uzantı | HLS segmentleri birleştirilir, dosya adı **her zaman** `.mp4` | TS birleşimi `.mp4` adıyla kaydediliyordu |
| Codec seçimi | Master playlist'teki **en yüksek bitrate** varyantı | AV1-only cihazda AV1 indiriliyordu |
| Doğrulama | Yok | Oynatılamayan dosya "başarılı" görünüyordu |
| Dönüştürme | Yok (ffmpeg/Transformer bağımlılığı yok) | Format dönüşmüyordu |

## 3. Karar: transcode değil, cihaza uygun kaynak seçimi

**Neden transcode seçilmedi**

- ffmpeg-kit/Media3 Transformer eklemek APK'yı **+35–60 MB** büyütür; ffmpeg-kit ayrıca arşivlenmiş durumda.
- 45 dakikalık bir bölümü telefonda H.264+AAC'ye çevirmek **15–40 dakika** sürer; ısınma, batarya ve depolama maliyeti yüksek.
- Kaynak (HDFC / FilmMakinesi) master playlist'te **zaten birden fazla codec** sunuyor → doğru olanı seçmek kalite kaybı ve ek süre olmadan aynı sonucu verir.

**Seçilen yaklaşım — üç katman**

1. **Ön seçim:** Cihazın decoder envanteri okunur, desteklenen en yüksek kaliteli varyant seçilir.
2. **Son doğrulama:** İndirilen dosyanın gerçek track'leri cihaz decoder'larıyla karşılaştırılır; uyumsuzsa dosya silinir.
3. **Doğru uzantı:** Kapsayıcıya uygun uzantı (`.ts` / `.mp4`) verilir.

## 4. Uygulama planı ve tamamlanan işler

### A — Cihaz codec envanteri

- [x] `DeviceCodecSupport` eklendi: `MediaCodecList(REGULAR_CODECS)` decoder'ları okur, `ConcurrentHashMap` ile önbellekler.
- [x] HLS `CODECS` kimlikleri eşlendi: `avc1/avc3`, `hvc1/hev1`, `dvh1/dvhe`, `av01`, `vp09/vp8`, `mp4v`; ses: `mp4a`, `ac-3`, `ec-3`, `ac-4`, `opus`, `vorbis`, `mp3`, `flac`, `dts*`.
- [x] `isMimeSupported`, `isCodecStringSupported` (bilinmiyorsa `null`), `isCodecListSupported`, `extensionFor`, `describe` eklendi.

### B — Varyant seçimi

- [x] `HlsVariant` artık `videoCodec` ve `audioCodec` taşıyor.
- [x] `HlsPlaylist.selectVariant(master, allowUnsupported)` eklendi: **desteklenen > bilinmeyen > desteklenmeyen**, eşitlikte bitrate.
- [x] `splitCodecs`, `isPlayableOnThisDevice`, `CODE_C_Video` regex eklendi.
- [x] Eski `selectBestVariant` geriye dönük uyumluluk için korundu.

### C — İndirme ve doğrulama

- [x] `DownloadVerifier` eklendi: `MediaExtractor` ile track'ler okunur; görüntü ya da ses hiç desteklenmiyorsa `playable=false`.
- [x] Türkçe/İngilizce uyarı metinleri (`friendlyName`: H.264 / H.265 / AV1 / E-AC-3 …).
- [x] `AnimeDownloader`: HLS ve düz indirme sonrası doğrulama; başarısızsa **dosya silinir** ve `UnsupportedMediaException` fırlatılır.
- [x] `buildFailure` bu hatayı **kalıcı** sayar; kullanıcıya nedeni gösterilir, boşuna yeniden denenmez.

### D — Dosya adı ve uzantı

- [x] `guessContainerExtension(url)`: HLS → `ts`, `.mpd` → `mp4`, bilinen uzantılar aynen.
- [x] Playlist'ten gerçek segment uzantısı öğrenilirse `renameTo` ile düzeltme (`logcat: "Kapsayıcı düzeltildi"`).
- [x] `DownloadExportNaming`: `exportFileName(..., extension)`, `VIDEO_EXPORT_EXTENSIONS`, `isVideoOfBase()` — eski `.mp4` kayıtları da silinebiliyor.

### E — Metinler ve testler

- [x] `strings.xml` (TR) + `values-en/strings.xml`: `indirme_hata_desteklenmeyen_format`, `indirme_format_uyarisi` (parite **17709 string, 4 array**).
- [x] `DeviceCompatibilitySelectionTest`: 11 test (AV1'siz cihazda H.264, HEVC tercihi, `CODECS` yoksa engellememe, audio-only atlama, `splitCodecs`, uzantı, `friendlyName` …).

## 5. Yeni davranış (özet)

| Kaynak durumu | Sonuç |
|---|---|
| H.264 varyantı var | H.264 indirilir (kesin oynar) |
| Cihaz HEVC destekliyor | HEVC tercih edilir (küçük dosya, aynı kalite) |
| Yalnızca AV1 var | En iyisi indirilir; doğrulama başarısızsa **dosya silinir** + "cihazınızın çözemediği görüntü formatı (AV1)" |
| Ses AC-3/E-AC-3 ve desteklenmiyor | "Görüntü oynatılabilir ama ses formatı (E-AC-3) desteklenmiyor" (sessiz video yerine açık bilgi) |
| `CODECS` bilgisi yok | Engelleme yok; karar son doğrulamaya bırakılır |

## 6. Sınırlar / kapsam dışı

- AV1-**only** kaynak (H.264/HEVC varyantı yoksa) indirilemez — dönüştürme yok. İstenirse ayrı iş: Media3 Transformer / ffmpeg-kit.
- Torrent/P2P akışları kapsam dışı.
- Bu ortamda **derleme ve `./gradlew test` çalıştırılamadı**. Yapılabilen doğrulamalar: brace dengesi denetimi, grep ile kapsam kontrolü, TR/EN parite betiği (`check_localization_resource_parity.py`).

## 7. Cihazda doğrulama adımları

```bash
adb logcat -s AnimeDownloader
```

Beklenen satırlar:

- `Master playlist: N varyant, seçilen … (cihaz uyumlu=true)`
- `Doğrulama: playable=true …` veya uyarı satırı
- `Kapsayıcı düzeltildi` (segment uzantısı farklıysa)

Test senaryoları: (1) AV1 desteklemeyen cihazda AV1 master'lı bölüm → H.264 inmeli; (2) HEVC destekleyen cihazda HEVC tercih edilmeli; (3) AV1-only bölüm → hata + dosya kalmamalı; (4) indirilen dosya İndirilenler'den oynatılabilmeli.

## 8. Değişen dosyalar

| Dosya | Tür |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/core/download/DeviceCodecSupport.kt` | Yeni |
| `app/src/main/java/com/kitsugi/animelist/core/download/DownloadVerifier.kt` | Yeni |
| `app/src/main/java/com/kitsugi/animelist/core/download/HlsPlaylist.kt` | Değişti |
| `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt` | Değişti |
| `app/src/main/java/com/kitsugi/animelist/core/download/DownloadExportNaming.kt` | Değişti |
| `app/src/main/res/values/strings.xml` | Değişti |
| `app/src/main/res/values-en/strings.xml` | Değişti |
| `app/src/test/java/com/kitsugi/animelist/core/download/DeviceCompatibilitySelectionTest.kt` | Yeni (11 test) |
| `app/build.gradle.kts` | Sürüm 2.4.342 |
| `SURUM_NOTLARI_v2.4.341.md` | Sürüm notu eki |
| `KOK_NEDEN_RAPORU_INDIRME_429_SEGMENT_2026-11-09.md` | §5 eklendi |

Toplam: 11 dosya, +825 / −21 satır.
