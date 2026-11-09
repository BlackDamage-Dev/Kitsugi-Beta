# PLAN TASK — İndirilen Videolarda Ses Sorunu ve Tüm İndirme/Oynatıcı Hatalarının Giderilmesi

**Tarih:** 2026-11-09  
**Durum:** ✅ Tamamlandı  
**Temel Sürüm / Commit:** `v2.4.340` (`561a12cc972d9f8a7efa889e65b206d6d2b317bb`)  
**Çalışma Dalı:** `arena/075b4b15-kitsugi-beta`

---

## 1. Hedefler ve Görev Tablosu

| # | Hedef / Görev | Durum |
|---|---------------|-------|
| H1 | Ayrık HLS ses izlerinin (`#EXT-X-MEDIA:TYPE=AUDIO`) ayrıştırılması, dil/tür önceliğine göre seçilmesi ve görüntüyle birlikte indirilmesi | ✅ |
| H2 | `KitsugiCdnJwExtractor` içinde ayrık sesli (`#EXT-X-MEDIA:TYPE=AUDIO`) master playlist'lerin tek video varyantına daraltılmasının engellenmesi | ✅ |
| H3 | `.m3u8` uzantısı olmayan HLS akışlarının (`master.txt`, `/playlist/*.txt`, uzantısız HLS, UTF-8 BOM `#EXTM3U`), düz URL yönlendirmelerinin ve Google Drive onay sayfasının doğru çözümlenmesi | ✅ |
| H4 | Paket ses segmentlerindeki (`ADTS AAC`, `AC-3`, `E-AC-3`, `MP3`) tekrarlayan `ID3v2` başlıklarının ve gizlenmiş (`.png`/`.jpg` sahte başlıklı) MPEG-TS segmentlerinin temizlenmesi | ✅ |
| H5 | Kayıpsız görüntü + ses birleştirme (`MediaRemuxer`), `#EXT-X-DISCONTINUITY` PTS hizalama, Dolby (`E-AC-3`/`AC-3`)/`MP3` koruması ve yan ses (`<taban>.audio.<uzantı>`) yedek mimarisi | ✅ |
| H6 | HLS `#EXT-X-KEY` (`AES-128`), `#EXT-X-BYTERANGE` ve çoklu `#EXT-X-MAP` (fMP4 başlangıç segmenti) tam desteği | ✅ |
| H7 | Duraklatma (`Pause`) sırasında `CancellationException` yakalanıp indirmelerin `ERROR` durumuna düşmesinin düzeltilmesi ve `.staging_<mediaId>/state.json` ile kaldığı yerden devam etme | ✅ |
| H8 | `StreamSource.externalAudioUrl`, `isCS`, `addonName`, `providerAudioKind`, `providerLanguages` alanlarının indirme kuyruğuna eksiksiz aktarılması | ✅ |
| H9 | CloudStream eklenti HTTP paritesi (`CsVideoInterceptorFactory`, `Origin`/`Referer` türetme) ve eklenti CDN altyazılarında `403 Forbidden` hatasının giderilmesi | ✅ |
| H10 | Tüm 4 çevrimdışı oynatma giriş noktasında (`DownloadsScreen`, `DownloadedPlayerActivity`, `ApiDetailTabContents`, `EntryDetailTabContents`) yan ses + yerel altyazı çözümlemesi | ✅ |
| H11 | Media3 ve MPV oynatıcı motorlarında yerel `file://`/`.ts` HLS/DASH yanlış algılaması, `MergingMediaSource(true, true, ...)`, `ParallelRangeDataSource` EOF ve MPV `file://` yolu düzeltmeleri | ✅ |
| H12 | `kotlinc 2.0.21` (JDK 17, `tr-TR` yerel ayar dahil) derleme doğrulaması ve 28 birim testinin tamamının geçirilmesi + ZIP paketleme & Google Drive yüklemesi | ✅ |

---

## 2. Tespit Edilen Kök Nedenler

1. **Ayrık HLS Ses Akışlarının (`#EXT-X-MEDIA:TYPE=AUDIO`) Yok Sayılması (Ana Sessiz Video Nedeni)**
   - *HDFilmCehennemi* / *FilmMakinesi* (**Close CDN** `.../hls/<slug>.mp4/txt/master.txt`, **Rapidrame** `master.m3u8`), *trdual* yayınlar ve modern fMP4/CMAF HLS sunucuları görüntüyü `#EXT-X-STREAM-INF` içinde **sessiz (video-only)**, sesi ise ayrı bir `#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="...",URI="..."` oynatma listesinde sunar.
   - Eski `AnimeDownloader` yalnızca `#EXT-X-STREAM-INF` varyantını indirip `#EXT-X-MEDIA:TYPE=AUDIO` satırlarını tamamen atladığı için indirilen dosyada **0 ses izi** oluşuyordu.
   - Ek olarak `KitsugiCdnJwExtractor.resolveMasterPlaylist(...)` master playlist'i doğrudan tek video varyant URL'sine indirgediği için ayrık ses bilgisi daha indirme başlamadan kayboluyordu.

2. **`.m3u8` Dışı HLS Akışları, Yönlendirmeler ve HTML Yanıtları**
   - Eski kod yalnızca `url.contains(".m3u8")` kontrolü yapıyordu; `master.txt`, `/playlist/<id>.txt`, `/embed/sheila/...`, UTF-8 BOM (`\ufeff#EXTM3U`) ile başlayan manifestler veya HTML hata sayfaları birkaç KB'lık bozuk `.mp4` dosyası olarak kaydediliyordu.
   - HTTP yönlendirmelerinden sonra son URL (`response.request.url`) alınmadığı için bağıl segment adresleri yanlış kök URL'ye göre çözülüyordu.

3. **Paket Ses (`ID3v2`) ve Sahte Görsel Başlıklı MPEG-TS Segment Bozulmaları**
   - Paket ses segmentleri (`.aac`, `.ac3`, `.ec3`, `.mp3`) her segmentin başında `ID3v2` (`com.apple.streaming.transportStreamTimestamp`) etiketi taşır. Ham bayt birleştirmesi yapıldığında `MediaExtractor` ilk segmentten (~4 sn) sonra `ID3` başlığına çarpıp `EOF` döndürüyordu.
   - `.png` / `.jpg` / `.gif` sahte başlığıyla gizlenmiş TS segmentleri temizlenmediği için hem konteyner algılama hem PES ses paketleri bozuluyordu.

4. **Dolby (`E-AC-3` / `AC-3`) ve `MP3` Ses Kaybı**
   - Android `MediaMuxer` (`MUXER_OUTPUT_MPEG_4`), birçok cihazda `AC-3`/`E-AC-3`/`MP3` ses kodeklerini MP4 içine mux etmeyi reddeder. Birleştirilmiş TS akışı zorla `.mp4` olarak yeniden paketlendiğinde veya isimlendirildiğinde ses izi düşüyordu.

5. **Şifreli HLS (`AES-128`), `#EXT-X-BYTERANGE`, `#EXT-X-MAP` ve Duraklatma Hatası**
   - `#EXT-X-KEY:METHOD=AES-128`, `#EXT-X-BYTERANGE` ve çoklu `#EXT-X-MAP` desteklenmiyordu.
   - Kullanıcı indirmeyi duraklattığında fırlatılan `CancellationException`, genel `catch (e: Exception)` bloğuna düşüp indirmeyi `Status.ERROR` durumuna sokuyordu.

6. **Harici Ses (`externalAudioUrl`), CloudStream İstek Başlıkları ve Altyazı `403` Hatası**
   - `StreamSource.externalAudioUrl` indirme yöneticisine aktarılmıyordu.
   - İndirici `CsVideoInterceptorFactory` kullanmadığı ve `Referer`'dan `Origin` türetmediği için CS CDN'lerinde bağlantı/altyazı istekleri `403` alabiliyordu.

7. **Çevrimdışı ve Çevrimiçi Oynatıcı Katmanı Hataları**
   - `PlayerMediaSourceFactory.isHlsUrl` yerel `file:///.../Film.ts` dosyalarını HLS manifest sanarak `HlsMediaSource` ile açmaya çalışıp oynatmayı bozuyordu.
   - `Media3PlayerEngine` URL/dosya adında `"mpd"` geçen içerikleri yanlışlıkla DASH sanabiliyordu; çift akışta `MergingMediaSource(true, true, ...)` hizalaması kapalıydı.
   - `ParallelRangeDataSource` bilinmeyen uzunluklu (`C.LENGTH_UNSET`) akışlarda anında `EOF` döndürüyordu.
   - `KitsugiMpvSurfaceView` / `MpvPlayerEngine` yerel `file://` yollarını normalize etmiyordu.

---

## 3. Değişen ve Yeni Eklenen Dosyalar

### 3.1 Yeni Eklenen Çekirdek Modüller ve Testler (5 Dosya)
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/HlsManifestParser.kt`
  - Master ve Media HLS ayrıştırıcısı, `#EXT-X-MEDIA:TYPE=AUDIO` grup eşleşmesi, dil/tür (`dub`/`sub`/`dual`) puanlaması, `#EXT-X-KEY`, `#EXT-X-MAP`, `#EXT-X-BYTERANGE`, `#EXT-X-DISCONTINUITY`, BOM ve düz URL yönlendirme algılaması.
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/HlsSegmentProcessor.kt`
  - `AES-128/CBC` şifre çözme, paket ses `ID3v2` + Apple 33-bit PTS çıkarma/temizleme, sahte `.png`/`.jpg` başlıklı TS senkronizasyonu ve `PAT`/`PMT` tablo analizi (`AAC`, `AC-3`, `E-AC-3`, `MP3`, `DTS`, `Opus`).
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/DownloadHttpClientFactory.kt`
  - `KitsugiHttpClient` + `CsVideoInterceptorFactory` entegrasyonu, otomatik `Origin`/`Referer`/`User-Agent` tamamlama ve güvenli altyazı başlık çözümlemesi.
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/MediaRemuxer.kt`
  - Kayıpsız `MediaExtractor` + `MediaMuxer` birleştirici, B-frame PTS koruması, süreksizlik (discontinuity) PTS düzeltmesi, ses izi doğrulaması ve yan ses (`<taban>.audio.<uzantı>`) / `.ts` koruma stratejisi.
- [x] `app/src/test/java/com/kitsugi/animelist/core/download/HlsDownloadAudioAndParserTest.kt`
  - HLS ayrıştırma, ses seçimi, şifre çözme, segment temizleme, `PAT`/`PMT` analizi, HTTP başlık türetme ve çevrimdışı yan ses/altyazı çözümleme birim testleri.

### 3.2 Güncellenen Üretim ve Test Dosyaları (19 Dosya)
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt`
  - Uçtan uca yenilenmiş, duraklatılabilir/devam ettirilebilir (`.staging_<mediaId>/state.json`), ayrık HLS ses + harici ses destekli indirme ve birleştirme motoru.
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloadService.kt`
  - Ön plan servis bildirimleri ve `localAudioPath` durum geri çağrısı entegrasyonu.
- [x] `app/src/main/java/com/kitsugi/animelist/core/download/DownloadExportNaming.kt`
  - Çoklu konteyner (`.mp4`, `.mkv`, `.webm`, `.ts`), yan ses (`<taban>.audio.<uzantı>`) ve gizli `.staging_<mediaId>` adlandırma yardımcıları.
- [x] `app/src/main/java/com/kitsugi/animelist/data/model/AnimeDownload.kt`
  - `externalAudioUrl`, `localAudioPath`, `isCS`, `addonName`, `providerAudioKind`, `providerLanguages` alanları eklendi.
- [x] `app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt`
  - Yeni akış meta verilerinin kuyruk, durum güncelleme ve silme akışına bağlanması.
- [x] `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt`
  - İndirme başlatılırken `externalAudioUrl` ve eklenti ses/dil meta verilerinin `addDownload` çağrısına iletilmesi.
- [x] `app/src/main/java/com/kitsugi/animelist/core/storage/DownloadStorage.kt`
  - Çoklu video uzantıları, yan ses dosyaları ve `.staging_` dizinleri için depolama boyutu ve temizlik desteği.
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/OfflinePlaybackHelper.kt`
  - Düz ve eski klasör yapılarında video + yan ses (`audioUri`) + yerel altyazı çözümlemesi (`resolveFilePlaybackParams` dahil).
- [x] `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt`
  - Çevrimdışı oynatmada `OfflinePlaybackHelper` üzerinden `audioUrl` ve altyazıların eksiksiz iletilmesi + eksik dosya uyarısı.
- [x] `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt`
  - Çevrimdışı tam ekran oynatıcı başlatıcısına `audioUrl` aktarımı.
- [x] `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
  - API detay ekranından çevrimdışı bölüm oynatmada yan ses ve altyazı aktarımı.
- [x] `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt`
  - Liste/girdi detay ekranından çevrimdışı bölüm oynatmada yan ses ve altyazı aktarımı.
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/PlayerMediaSourceFactory.kt`
  - Yerel URI (`file://`, `content://`, `/`) ve `.ts`/`.m2ts`/`.mov` koruması + `MergingMediaSource(true, true, ...)`.
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/ParallelRangeDataSource.kt`
  - `C.LENGTH_UNSET` durumunda erken `EOF` hatasının giderilmesi.
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
  - Yerel dosya koruması, kesin `.mpd` uzantı kontrolü ve `MergingMediaSource(true, true, ...)` hizalaması.
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/engine/KitsugiMpvSurfaceView.kt`
  - Yerel `file://` URI'lerinin POSIX dosya yoluna dönüştürülmesi (`normalizeMpvPath`).
- [x] `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
  - Harici/yan `audio-files` yolunda `file://` normalizasyonu.
- [x] `app/src/main/java/com/kitsugi/animelist/data/cloudstream/extractors/KitsugiCdnJwExtractor.kt`
  - `#EXT-X-MEDIA:TYPE=AUDIO` içeren master playlist'lerin tekil video varyantına daraltılmadan korunması.
- [x] `app/src/test/java/com/kitsugi/animelist/core/player/PlayerHlsDetectionTest.kt`
  - Yerel `file://`, `content://` ve `.ts` regresyon testleri eklendi.

---

## 4. Doğrulama ve Test Sonuçları

- [x] `kotlinc 2.0.21` + JDK 17 + `-Duser.language=tr -Duser.country=TR` (Türkçe yerel ayar) ile tüm çekirdek modüller ve testler **0 derleme hatasıyla** derlendi.
- [x] `HlsDownloadAudioAndParserTest` (12 test), `PlayerHlsDetectionTest` (7 test) ve `KitsugiCdnJwExtractorTest` (9 test) olmak üzere toplam **28/28 birim testi** başarıyla geçti.

---

## 5. Google Drive Zip Paketi İçeriği

**Dosya Adı:** `Kitsugi_Indirme_Ses_ve_Oynatici_Fix_2026-11-09.zip`  
*(Doğrudan repo kök dizinine `unzip -o Kitsugi_Indirme_Ses_ve_Oynatici_Fix_2026-11-09.zip -d .` komutuyla açılabilecek birebir dizin yapısındadır)*

- `PLAN_TASK_INDIRME_SES_VE_OYNATICI_FIX_2026-11-09.md` (bu Plan & Task dosyası)
- Yukarıdaki listede yer alan tüm yeni ve güncellenen **24 kaynak ve birim testi dosyası** (`app/src/main/...`, `app/src/test/...`)
- `fix.patch` (yeni eklenen dosyalar dahil tam `git diff` yaması — `git apply fix.patch` ile tek komutla uygulanabilir)
- `DEGISIKLIK_OZETI.txt` (tüm değişen/eklenen dosyaların listesi, `git diff --stat` ve test doğrulama özeti)
