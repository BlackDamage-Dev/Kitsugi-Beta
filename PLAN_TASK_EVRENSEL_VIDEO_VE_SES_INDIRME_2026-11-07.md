# PLAN/TASK — Evrensel Video+Ses İndirme Motoru, Bağımsız Ses İndirme ve Otomatik Kodek Dönüştürme (AAC-LC / M4A)

Tarih: 2026-11-07  
Durum: **Tamamlandı ve doğrulandı** (`kotlinc 2.0.21` ile **0 hata**, `UniversalDownloadEngineTest` **10/10 JUnit testi başarılı**, XML `<string>` farkı **+0**).

---

## 📌 Kapsam ve Hedefler

1. **Evrensel Video + Ses İndirme:** Hangi video eklentisinden veya CDN kaynağından gelirse gelsin (CloudStream3 eklentileri, Kitsugi-Plugins, WebView yakalayıcıları, Stremio HTTP / Debrid / P2P Torrent eklentileri, YouTube / GoogleVideo) indirilen bölümlerde ayrık (`#EXT-X-MEDIA:TYPE=AUDIO`, DASH `.mpd`, `ExtractorLink.audioTracks`) veya uyumsuz gömülü seslerin sessiz kalmaması; tekil ve evrensel oynatılabilir bir video dosyası olarak kaydedilmesi.
2. **Bağımsız (Standalone) Ses İndirme (`Download/Kitsugi/Audio`):** Hem akış seçim kartlarından (`StreamCard` -> 🎧) hem de tam ekran video oynatıcının `Ses Parçaları` panelinden (`AudioTracksSheet` -> 🎧) tek veya çoklu dildeki seslerin doğrudan bağımsız ses dosyası olarak indirilebilmesi; `İndirilenler` ekranında 4. sekme olan **`Sesler`** sekmesinden yönetilebilmesi.
3. **%100 Eklenti, Konteyner ve P2P Kapsaması:** HLS (`.m3u8`, `.txt`, sahte `.png`/`.jpg` başlıklı TS segmentleri, `AES-128`, `#EXT-X-MAP` fMP4, `#EXT-X-BYTERANGE`, Packed Audio), MPEG-DASH (`.mpd`), çok dilli doğrudan konteynerler (`MKV`, `MP4`, `WebM`, `TS`, `FLV`, `AVI` — non-faststart kuyruk `moov` atomlu `.mp4` dahil) ve P2P Torrent (`P2pStreamingEngine`) akışlarının tamamından ses indirilebilmesi.
4. **Desteklenmeyen Ses Kodeklerini Otomatik Evrensel Formata Dönüştürme:** Cihazların donanımsal lisans veya konteyner kısıtları yüzünden açamadığı `E-AC-3` (Dolby Digital Plus / Atmos), `AC-3`, `DTS` / `DTS-HD`, `TrueHD` / `MLP`, `Opus`, `Vorbis`, `FLAC`, `ALAC`, `PCM` / `WAV`, `MP2`, `WMA`, `AMR`, `xHE-AAC` ve **5.1 / 7.1 surround** ses akışlarının otomatik olarak her cihazda açılan **Stereo/Mono `AAC-LC (.m4a)`** biçimine dönüştürülmesi.

---

## 🛠️ Yapılan Mimari Geliştirmeler ve Görevler

### 1) Akış Biçimi Koklama, Manifest Çözümleme ve CDN HTTP Politikaları
- **`StreamFormatSniffer.kt` (Yeni):**
  - URL uzantısına güvenmek yerine ilk bayt imzaları (magic bytes) ve `Content-Type` ile `HLS_MANIFEST` (`.txt`/`.png` arkasına gizlenmiş `#EXTM3U` dahil), `DASH_MANIFEST` (`<MPD`), `HTML_PAGE` (Cloudflare/403 koruma sayfaları), `MP4`, `M4A`, `FMP4_SEGMENT`, `MKV`, `WEBM`, `MPEG_TS`, `FLV`, `AVI`, `ADTS_AAC`, `MP3`, `AC3`, `EAC3`, `OPUS_OGG`, `VORBIS_OGG`, `FLAC`, `WAV` ve `DTS` tespiti eklendi.
  - Sahte PNG/JPEG/GIF/BMP başlığı arkasına gizlenen 188-bayt `0x47` MPEG-TS segmentlerini temizleyen `sanitizeDisguisedTsSegment` ve Packed Audio `ID3v2` etiketlerini soyup gerçek ses çerçevesine ulaşan `stripLeadingId3Tags` eklendi.
- **`HlsManifestResolver.kt` & `DashManifestResolver.kt` (Yeni):**
  - RFC 8216 uyumlu Master/Media HLS ayrıştırıcısı (`#EXT-X-MEDIA:TYPE=AUDIO` ayrık `URI` vs gömülü ses tespiti, `AES-128-CBC` şifre çözme, `#EXT-X-MAP` fMP4 init segmenti, `#EXT-X-BYTERANGE`).
  - MPEG-DASH `.mpd` ayrıştırıcısı (`SegmentTemplate`, `SegmentTimeline`, `SegmentList`, `BaseURL` ve çok dilli ses `AdaptationSet` seçimi).
- **`StreamDownloadHttpClient.kt` (Yeni):**
  - CloudStream eklentilerinin `getVideoInterceptor`, paylaşılan `cookieJar`, SSL bypass ve IPv4-öncelikli DNS yapısını (`CsVideoInterceptorFactory`) devralır.
  - CloseLoad / Rapidrame vb. gömülü oynatıcılarda `Referer`'dan otomatik `Origin` türetir; yabancı `Origin` başlığını reddeden `ok.ru`, `mycdn.me`, `sibnet.ru`, `vkuser.net`, `googlevideo.com` sunucularında `Origin` eklemez.
  - `googlevideo.com` akışlarını daraltmaya (throttling) takılmadan 10 MB `range=` parçalarıyla indirir.
  - Uzak konteynerlerde tüm videoyu indirmeden gömülü ses dillerini keşfetmek için `fetchRangeWindowBlocking` (`Range: bytes=...`) eklendi.

### 2) Eklenti Ses Parçası Taşıma (`ExternalAudioTrack`, `CsStreamRunner`, `KitsugiCdnJwExtractor`, `StreamProbe`)
- **`ExternalAudioTrack.kt` (Yeni):** Çoklu harici ses URL'si, başlıkları (`headers`), dil kodu, etiket, kodek ve kanal sayısını Gson-güvenli olarak taşır; 25+ dil için TR/EN görüntüleme adı ve metinden dil çıkarımı sağlar.
- **`CsStreamRunner.kt` & `KitsugiCdnJwExtractor.kt` & `StreamProbe.kt` & `AddonStreamRepository.kt`:**
  - CloudStream3 `ExtractorLink.audioTracks` listesinin tamamı (`allExternalAudioTracks`) korunur.
  - Varyant HLS URL'si döndüren eklentilerde kardeş master playlist adresi `X-Kitsugi-Master-Url` üstbilgisiyle indirme motoruna aktarılır.

### 3) Evrensel Ses Kodek Dönüştürücü (`KitsugiFfmpegAudioBridge` & `UniversalAudioTranscoder`)
- **`KitsugiFfmpegAudioBridge.kt` (Yeni — `package androidx.media3.decoder.ffmpeg`):**
  - Uygulama içinde zaten paketli olan `lib-decoder-ffmpeg-release.aar` (`libffmpegJNI.so` — `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`) içindeki paket-içi `FfmpegAudioDecoder` sınıfına güvenli köprü kurar.
  - Üretici (OEM) Dolby/DTS lisansına bağımlı olmadan `E-AC-3` / Atmos, `AC-3`, `DTS`, `DTS-HD`, `TrueHD`, `MLP`, `FLAC`, `ALAC`, `Opus`, `Vorbis`, `MP2`, `WMA`, `AMR`, `PCM` ve çok kanallı sesleri normalize edilmiş 16-bit PCM akışına çözer (`setDownmixNormalizationEnabled(true)`).
- **`UniversalAudioTranscoder.kt` (Yeni):**
  - **Kayıpsız Doğrudan Koruma:** Yalnızca 1–2 kanallı standart `AAC-LC` (`.m4a`) ve `MP3` (`.mp3`) akışları yeniden kodlamadan korur.
  - **Otomatik Evrensel Dönüştürme:** Cihazların desteklemediği tüm kodekleri ve 5.1/7.1 surround akışları (ITU-R BS.775 konuşma `Center` kanalını koruyan stereo downmix + Float/24-bit/32-bit -> 16-bit PCM + 96/192 kHz -> 48 kHz örnekleme hızı dönüşümü) Android donanımsal `MediaCodec` (`audio/mp4a-latm`, `AACObjectLC`, Stereo 192 kbps / Mono 128 kbps) + `MediaMuxer` ile standart ISO **`.m4a`** dosyasına dönüştürür.
- **`MediaTrackRemuxer.kt` (Yeni):**
  - Bağımsız ses indirmelerinde (`extractAudioToStandaloneFile`) evrensel olmayan kodekleri otomatik olarak `UniversalAudioTranscoder` üzerinden `.m4a` olarak üretir.
  - Video indirmelerinde (`remuxVideoWithAudios` & `remuxSingleStreamVideo`) birincil ses `E-AC-3` / `AC-3` / `DTS` / `Opus` / `FLAC` vb. olduğunda sesi otomatik olarak `AAC-LC`'ye dönüştürüp doğrudan `.mp4` içine gömer; sessiz `.mp4` oluşmasını engeller.
  - `probeTracks` içinde `Extractor.RESULT_SEEK` takibiyle `moov` atomu sonda olan non-faststart `.mp4` dosyalarını ve `probeRemoteContainerTracks` ile uzak çok dilli `MKV`/`MP4`/`WebM`/`TS`/`FLV`/`AVI` konteynerlerini eksiksiz okur.

### 4) Kuyruk Yönetimi, P2P Torrent Desteği, Çevrimdışı Oynatma ve Arayüz
- **`AnimeDownload.kt`, `AnimeDownloadManager.kt`, `AnimeDownloader.kt`, `AnimeDownloadService.kt`:**
  - `DownloadKind.VIDEO` ve `DownloadKind.AUDIO` ayrımı, `uniqueKey` tabanlı eşzamanlı video + çoklu dil ses kuyruğu, eski Gson kayıtlarıyla %100 geriye dönük uyumluluk (`normalized()`).
  - P2P torrent kaynaklarında (`p2pInfoHash`, `p2pFileIdx`, `p2pTrackers`) `P2pStreamingEngine` üzerinden isteğe bağlı yerel akış başlatma ve ses indirme tamamlandığında güvenli kapatma.
- **`AudioTrackInspector.kt` (Yeni):**
  - HLS, DASH, `ExtractorLink.audioTracks` ve uzak `MKV`/`MP4`/`WebM`/`TS`/`FLV`/`AVI` konteynerlerindeki tüm ses parçalarını keşfeder; dönüştürülecek kodekleri `E-AC3 → M4A`, `DTS → M4A`, `FLAC → M4A`, `OPUS → M4A` rozetleriyle gösterir.
- **Arayüz ve Depolama Entegrasyonu:**
  - `StreamCard.kt`, `StreamResultsList.kt`, `StreamScreenContent.kt`, `KitsugiStreamScreen.kt`: Akış kartlarına bağımsız ses indirme (🎧) butonu, P2P onay akışı ve çok dilli ses seçim penceresi eklendi.
  - `AudioTracksSheet.kt`, `PlayerSheetsHost.kt`, `KitsugiFullscreenPlayerScreen.kt`, `KitsugiPlayerViewModel.kt`: Tam ekran oynatıcı içindeki Ses Parçaları paneline tekil ve toplu ses indirme butonları eklendi.
  - `DownloadsScreen.kt`, `DownloadsActivity.kt`: `İndirilenler` ekranına **`Sesler`** sekmesi (oynat, paylaş, duraklat/devam et, sil) eklendi.
  - `DownloadExportNaming.kt`, `DownloadStorage.kt`, `MediaStoreFileDeleter.kt`, `StorageSettingsContent.kt`, `OfflinePlaybackHelper.kt`, `PlayerMediaSourceFactory.kt`, `MpvPlayerEngine.kt`: `Download/Kitsugi/Audio` depolama hesaplama/temizleme ve yan ses dosyalarıyla çevrimdışı oynatma desteği eklendi.

---

## ✅ Doğrulama Sonuçları

- **Derleme:** `kotlinc 2.0.21` (JVM 17 hedefi) ile tüm yeni ve değiştirilen çekirdek modüller **0 hata** ile derlendi.
- **Birim Testleri (`UniversalDownloadEngineTest.kt`):** **10 / 10 JUnit 4 testi başarılı**:
  1. `sniffer_detectsManifestsContainersAndRejectsHtmlErrorPages`
  2. `sniffer_stripsFakePngHeaderFromDisguisedMpegTsSegment`
  3. `sniffer_stripsLeadingId3TagFromPackedAudioSegment`
  4. `hlsMaster_parsesDemuxedAndMuxedAudioAccordingToRfc8216`
  5. `hlsMedia_parsesInitMapByteRangesAndDecryptsAes128`
  6. `dashManifest_parsesSegmentTimelineAndMultiLanguageAudio`
  7. `headersNamingAndModel_preserveBackwardCompatibilityAndCdnPolicies`
  8. `universalTranscoder_detectsUnsupportedCodecsAndMultichannelSurround`
  9. `universalTranscoder_downmixesSurroundConvertsPcmAndResamplesHiResAudio`
  10. `snifferAndInspector_detectFlvAviAndConversionBadges`
- **64K `R$string` DEX Sınırı Koruması (`00_GEMINI_ANTIGRAVITY_MASTER_GUIDE.md`):** XML kaynak dosyalarında **+0 yeni `<string>`** eklendi (`git diff -- app/src/main/res/ bangumi-vault-resources-*` = `0` satır).

---

## 📂 Değişen ve Yeni Eklenen Tüm Dosyalar (Toplam 38 Kod Dosyası + Bu Plan Dosyası)

### Yeni Eklenen Dosyalar (10)
1. `app/src/main/java/androidx/media3/decoder/ffmpeg/KitsugiFfmpegAudioBridge.kt`
2. `app/src/main/java/com/kitsugi/animelist/core/download/AudioTrackInspector.kt`
3. `app/src/main/java/com/kitsugi/animelist/core/download/DashManifestResolver.kt`
4. `app/src/main/java/com/kitsugi/animelist/core/download/HlsManifestResolver.kt`
5. `app/src/main/java/com/kitsugi/animelist/core/download/MediaTrackRemuxer.kt`
6. `app/src/main/java/com/kitsugi/animelist/core/download/StreamDownloadHttpClient.kt`
7. `app/src/main/java/com/kitsugi/animelist/core/download/StreamFormatSniffer.kt`
8. `app/src/main/java/com/kitsugi/animelist/core/download/UniversalAudioTranscoder.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/model/ExternalAudioTrack.kt`
10. `app/src/test/java/com/kitsugi/animelist/core/download/UniversalDownloadEngineTest.kt`

### Güncellenen Dosyalar (28)
11. `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloadService.kt`
12. `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt`
13. `app/src/main/java/com/kitsugi/animelist/core/download/DownloadExportNaming.kt`
14. `app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt`
15. `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt`
16. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
17. `app/src/main/java/com/kitsugi/animelist/core/storage/DownloadStorage.kt`
18. `app/src/main/java/com/kitsugi/animelist/core/storage/MediaStoreFileDeleter.kt`
19. `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsStreamRunner.kt`
20. `app/src/main/java/com/kitsugi/animelist/data/cloudstream/extractors/KitsugiCdnJwExtractor.kt`
21. `app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt`
22. `app/src/main/java/com/kitsugi/animelist/data/model/AnimeDownload.kt`
23. `app/src/main/java/com/kitsugi/animelist/data/repository/AddonStreamRepository.kt`
24. `app/src/main/java/com/kitsugi/animelist/data/repository/StreamProbe.kt`
25. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
26. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
27. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailEpisodesTab.kt`
28. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
29. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
30. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerSheetsHost.kt`
31. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/components/sheets/AudioTracksSheet.kt`
32. `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsActivity.kt`
33. `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`
34. `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt`
35. `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt`
36. `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamCard.kt`
37. `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamResultsList.kt`
38. `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt`
39. `PLAN_TASK_EVRENSEL_VIDEO_VE_SES_INDIRME_2026-11-07.md`
