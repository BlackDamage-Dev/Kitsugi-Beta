# PLAN / TASK — İndirmeler `Sesler` Sekmesi, `Download/Kitsugi/Audio` Depolama Katmanı ve Format Bağımsız Çift Motorlu Ses Oynatıcısı

**Tarih:** 5 Kasım 2026  
**Depo / Dal:** `f26901964-eng/Kitsugi-Beta` / `arena/a9c5f652-kitsugi-beta`  
**Karşılaştırma Tabanı:** `abfdb1525` (`feat/golge-bahcesi-integration` v2.4.427 eşitlemesi)  
**Doğrulanan CI Çalıştırması:** [GitHub Actions #41945773219](https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41945773219) (`dcf388ace`) — FOSS Debug + R8 Release APK & DEX doğrulaması başarılı (`VerifyError = 0`)

---

## 1. İstek ve Kabul Ölçütleri

1. **İndirmeler (`DownloadsScreen`) Ses Alanı:** `Ses Parçaları` üzerinden bağımsız ses indirme özelliği bulunduğundan, İndirmeler sayfasında `Videolar`, `Altyazılar` ve `Resimler` sekmelerinin yanında indirilen sesler için ayrı bir **`Sesler`** sekmesi ve üst özet kartında ses sayacı/boyut göstergesi yer almalıdır.
2. **Özel Ses Klasörü ve Depolama Yönetimi:** İndirilen ses dosyaları için dosya sisteminde **`Download/Kitsugi/Audio`** klasörü standartlaştırılmalı; Scoped Storage / `MediaStore` taraması, tekli/toplu silme, klasör açma ve *Ayarlar → Depolama* temizleme akışları eksiksiz çalışmalıdır.
3. **Format Bağımsız Yerleşik Ses Oynatıcısı:** İndirilen veya cihazdan açılan yerel ses dosyaları **ses formatı ve kodeği fark etmeksizin** uygulama içinde tam özellikli bir ses oynatıcısıyla (arka plan çalma, bildirim/kilit ekranı kontrolleri, kuyruk, hız/perde, ses güçlendirme, uyku zamanlayıcısı, karıştırma/tekrarlama) oynatılabilmelidir.
4. **Açık Kaynak Araştırması ve Uyarlama:** Açık kaynak ses oynatıcı uygulamaları derinlemesine incelenip gerekli mimari ve arayüz yetenekleri Kitsugi-Beta'nın kendi kütüphane yığınına lisans uyumlu biçimde uyarlanmalıdır.

---

## 2. Açık Kaynak Araştırma Özeti ve Mimari Kararlar

| İncelenen Proje / Katman | Öne Çıkan Teknik Bulgu | Kitsugi-Beta'ya Uyarlama Biçimi |
| --- | --- | --- |
| **Auxio (`OxygenCobalt/Auxio`)** | Ham ADTS `.aac`, `.amr` ve CBR `.mp3` dosyalarında doğru süre hesabı ve takılmasız sarma (seek) için `DefaultExtractorsFactory` üzerinde sabit bit hızı sarma bayrakları şarttır. | GPL-3.0 kodu kopyalanmadan `KitsugiAudioPlaybackService` içinde `AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING`, `Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING \| FLAG_ENABLE_INDEX_SEEKING`, `AmrExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING` ve `DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS` etkinleştirildi. |
| **Media3 + Yerel FFmpeg / IAMF / MPEG-H AAR Yığını** | Donanım çözücüsü bulunmayan cihazlarda FLAC, ALAC, Opus, Vorbis, AC-3, E-AC-3, DTS, TrueHD, PCM/WAV, IAMF ve MPEG-H akışları yazılım çözücü önceliğiyle açılmalıdır. | Projede yer alan `lib-decoder-ffmpeg-release.aar`, `lib-decoder-iamf-release.aar` ve `lib-decoder-mpegh-release.aar` ile `DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER` yapılandırıldı. |
| **mpv-android / `libmpv` (`abdallahmehiz/mpv-android-lib`)** | `.wma/.asf`, `.ape` (Monkey's Audio), `.wv` (WavPack), `.mpc` (Musepack), `.tta`, `.tak`, `.dts/.dtshd`, `.thd/.mlp`, `.aiff/.caf`, `.dsf/.dff` (DSD/SACD) gibi egzotik konteynerleri Media3 doğrudan ayrıştıramaz; `libavformat` + `libavcodec` tabanlı `libmpv` tümünü açar. | Başsız (`vo=null`, `vid=no`) `KitsugiMpvAudioHost` yazıldı. Egzotik uzantılar doğrudan `libmpv`'ye yönlendirilir; ayrıca Media3 bir dosyada hata verirse aynı milisaniye konumundan otomatik olarak `libmpv` motoruna düşer (**çift motorlu otomatik fallback**). |
| **Symphony (`zyrouge/symphony`) & Modern Compose Oynatıcı UX** | Uygulama genelinde yüzen mini çubuk, tam ekran oynatıcı sayfası, kuyruk yönetimi, uyku zamanlayıcısı, hız/perde ayarı ve teknik akış rozetleri kullanıcı deneyimini tamamlar. | AGPL-3.0 kodu kopyalanmadan Kitsugi tasarım dilinde (`KitsugiColors`, `LocalKitsugiAccent`, Lucide `KitsugiIcons`) `KitsugiAudioPlayerUi` ve `KitsugiAudioPlayerActivity` sıfırdan geliştirildi; `NextLib MediaInfo` ile canlı kodek/örnekleme hızı/kanal/bit hızı rozetleri eklendi. |

---

## 3. Görev ve İlerleme Tablosu (PLAN / TASK)

| Görev Kodu | Durum | Kapsam ve Sonuç |
| --- | --- | --- |
| **SES-OYN-01** | Tamamlandı | Açık kaynak Android ses oynatıcılarının (`Auxio`, `Symphony`, `mpv-android`) ve depodaki yerel AAR'ların (`lib-decoder-ffmpeg`, `lib-extractor`, `nextlib-mediainfo`, `mpv-android-lib`) bayt kodu / API incelemesi tamamlandı. |
| **SES-OYN-02** | Tamamlandı | `DownloadExportNaming.publicAudioDir()` (`Download/Kitsugi/Audio`), `ensureStandardDownloadDirs()` ve `parseAudioFileName()` eklendi; `MainActivity` başlangıcında dört standart indirme klasörünün (`Video`, `Audio`, `Subtitles`, `Images`) otomatik oluşturulması sağlandı. |
| **SES-OYN-03** | Tamamlandı | `DownloadStorage.Kind.AUDIOS` ve `MediaStoreFileDeleter.Mode.AUDIO` eklendi. `MediaStore.Audio` + `MediaStore.Downloads` + doğrudan `Download/Kitsugi/Audio` taraması birleştirildi; `.webm` uzantısı yalnızca `Kitsugi/Audio` altında ses kabul edilerek video klasörüyle çakışma önlendi. |
| **SES-OYN-04** | Tamamlandı | *Ayarlar → Depolama* (`StorageSettingsContent.kt`) ekranına indirilen ses boyutu ve temizleme satırı eklendi; `AudioExportWorker` bildirim tıklaması doğrudan `DownloadsActivity.TAB_AUDIOS` sekmesine bağlandı ve `WORK_TAG = "kitsugi-audio-export"` ile canlı ilerleme takibi açıldı. |
| **SES-OYN-05** | Tamamlandı | Çift motorlu ses oynatma altyapısı (`AudioPlayerModels.kt`, `KitsugiMpvAudioHost.kt`, `KitsugiAudioPlaybackService.kt`, `KitsugiAudioPlayerManager.kt`) kuruldu: `MediaSessionCompat`, ön plan medya bildirimi, `AudioFocus`, kulaklık çıkarıldığında duraklatma, `LoudnessEnhancer` ses güçlendirme (`0..+15 dB`), hız (`0.5x..2.5x`), perde koruma, uyku zamanlayıcısı, karıştırma ve tekrarlama modları eklendi. |
| **SES-OYN-06** | Tamamlandı | Mini oynatıcı + tam ekran oynatıcı (`KitsugiAudioPlayerUi.kt`), bildirim/harici dosya aktivitesi (`KitsugiAudioPlayerActivity.kt`), `DownloadedPlayerActivity` `audio/*` yönlendirmesi ve `AppRoot.kt` uygulama geneli mini ses oynatıcı yerleşimi tamamlandı. |
| **SES-OYN-07** | Tamamlandı | `DownloadsScreen.kt` 4 sekmeye (`Videolar \| Sesler \| Altyazılar \| Resimler`) genişletildi; üst özet kartına ses sayacı/boyutu, üst menüye ses klasörü/yenileme/tümünü silme aksiyonları, `DownloadsAudiosTab.kt` ile gruplu/düz liste görünümü, arama, format/dil filtreleri, çoklu seçim ve indirilen video satırlarının altında eşlikçi ses parçası satırları eklendi. |
| **SES-OYN-08** | Tamamlandı | `KitsugiIcons.kt` içine Lucide `Headphones`, `Shuffle`, `AudioLines` ikonları eklendi; `values/strings.xml` ve `values-en/strings.xml` içine 62 yeni anahtar eklenip `check_localization_resource_parity.py` (`18,531` metin, `4` dizi) ve `tools/kitsugi_glyphs` kontrollerinden sıfır ihlalle geçirildi. |
| **SES-OYN-09** | Doğrulandı (CI) | `AudioDownloadsAndPlayerTest.kt` birim testleri yazıldı ve GitHub Actions `android-build.yml` (`#41945773219`) üzerinde birim testleri + FOSS Debug APK + R8 Release APK + DEX bayt kodu doğrulaması (`VerifyError = 0`) başarıyla tamamlandı. |

---

## 4. Değişen ve Yeni Eklenen Dosya Envanteri

| Durum | Dosya Yolu | Açıklama |
| --- | --- | --- |
| `M` | `app/src/main/AndroidManifest.xml` | `KitsugiAudioPlaybackService`, `KitsugiAudioPlayerActivity` ve `DownloadedPlayerActivity` ses MIME filtreleri |
| `M` | `app/src/main/java/com/kitsugi/animelist/MainActivity.kt` | Açılışta `Download/Kitsugi/{Video,Audio,Subtitles,Images}` klasörlerini hazırlama |
| `M` | `app/src/main/java/com/kitsugi/animelist/AppRoot.kt` | İndirmeler ekranı dışındaki sayfalarda yüzen `KitsugiAudioPlayerHost` |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIcons.kt` | Lucide `Headphones`, `Shuffle`, `AudioLines` vektör ikonları |
| `M` | `app/src/main/java/com/kitsugi/animelist/core/download/DownloadExportNaming.kt` | `publicAudioDir()`, `ensureStandardDownloadDirs()`, `parseAudioFileName()` |
| `M` | `app/src/main/java/com/kitsugi/animelist/core/storage/MediaStoreFileDeleter.kt` | `Mode.AUDIO` Scoped Storage / `MediaStore` silme desteği |
| `M` | `app/src/main/java/com/kitsugi/animelist/core/storage/DownloadStorage.kt` | `Kind.AUDIOS`, `scanMediaStoreAudios()`, `deleteAudio()`, `cleanAudios()` |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/settings/StorageSettingsContent.kt` | Depolama ayarlarında indirilen ses boyutu ve temizleme satırı |
| `M` | `app/src/main/java/com/kitsugi/animelist/core/player/audio/AudioExportWorker.kt` | `WORK_TAG` ilerleme etiketi ve `DownloadsActivity.TAB_AUDIOS` bildirim yönlendirmesi |
| `A` | `app/src/main/java/com/kitsugi/animelist/core/player/audio/player/AudioPlayerModels.kt` | Oynatıcı durum modelleri, 35+ uzantı/MIME sınıflandırması, kuyruk ve teknik rozet yardımcıları |
| `A` | `app/src/main/java/com/kitsugi/animelist/core/player/audio/player/KitsugiMpvAudioHost.kt` | Başsız (`vo=null`, `vid=no`) `libmpv` ses motoru |
| `A` | `app/src/main/java/com/kitsugi/animelist/core/player/audio/player/KitsugiAudioPlaybackService.kt` | Media3+FFmpeg + `KitsugiMpvAudioHost` hibrit ön plan servisi ve `MediaSessionCompat` |
| `A` | `app/src/main/java/com/kitsugi/animelist/core/player/audio/player/KitsugiAudioPlayerManager.kt` | Uygulama geneli ses oynatıcı yöneticisi ve `NextLib MediaInfo` akış çözümleyicisi |
| `A` | `app/src/main/java/com/kitsugi/animelist/ui/screens/audio/KitsugiAudioPlayerUi.kt` | Yüzen mini ses oynatıcı ve tam ekran ses oynatıcı Compose arayüzü |
| `A` | `app/src/main/java/com/kitsugi/animelist/ui/screens/audio/KitsugiAudioPlayerActivity.kt` | Bildirim ve harici `audio/*` dosyaları için tam ekran ses oynatıcı aktivitesi |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadedPlayerActivity.kt` | Harici ses URI/dosyalarını doğrudan Kitsugi Ses Oynatıcısına yönlendirme |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsActivity.kt` | `EXTRA_INITIAL_TAB` / `TAB_AUDIOS` sekme derin bağlantısı |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsUiPrefs.kt` | `Sesler` sekmesi sıralama ve anime gruplama tercihlerinin kalıcılığı |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsOverviewScroll.kt` | 4 sekmeli kaydırma politikası desteği (`audioListState`) |
| `A` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsAudiosTab.kt` | `Sesler` sekmesi kartları, filtre/arama/sıralama, çoklu seçim ve detay penceresi |
| `M` | `app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt` | 4 sekmeli `HorizontalPager`, özet kartı ses rozeti, üst menü ve video altı eşlikçi ses satırları |
| `M` | `app/src/main/res/values/strings.xml` | Türkçe yeni ses oynatıcı / İndirmeler `Sesler` metin kaynakları (62 anahtar) |
| `M` | `app/src/main/res/values-en/strings.xml` | İngilizce yeni ses oynatıcı / Downloads `Audio` metin kaynakları (62 anahtar) |
| `A` | `app/src/test/java/com/kitsugi/animelist/core/player/audio/player/AudioDownloadsAndPlayerTest.kt` | Dosya adı ayrıştırma, format/mpv yönlendirme, kuyruk tekrar/karıştırma ve rozet birim testleri |
| `A` | `docs/PLAN_TASK_SESLER_SEKMESI_VE_SES_OYNATICI_2026-11-05.md` | Bu Plan / Task ve doğrulama raporu |

---

## 5. Doğrulama Kanıtları ve Paketleme Notu

- **Dil Kaynakları Eşitliği:** `python3 scripts/check_localization_resource_parity.py` → `OK: 18531 strings and 4 arrays in sync between values and values-en.`
- **İkon & Erişilebilirlik Denetimi:** `tools/kitsugi_glyphs` kontrollerinde yeni/düzenlenen tüm dosyalarda sıfır Material ikon sızıntısı ve sıfır etiketsiz kontrol doğrulandı.
- **CI Derleme & DEX Doğrulaması (`#41945773219`):**
  - `Kitsugi-Beta-v2.4.428-foss.apk` (Debug): 22 DEX, 428.611 metot, `11.863.077` komut doğrulandı, `VerifyError = 0`
  - `Kitsugi-Beta-v2.4.428-foss.apk` (R8 Release): 5 DEX, 267.314 metot, `7.686.332` komut doğrulandı, `VerifyError = 0`
- **ZIP Paketleme:** Bu belge (`docs/PLAN_TASK_SESLER_SEKMESI_VE_SES_OYNATICI_2026-11-05.md`), `PLAN_TASK_SESLER_SEKMESI_VE_SES_OYNATICI_2026-11-05.md` kök kopyası, `ZIP_MANIFEST.txt` (taban/son commit ve tüm dosyaların SHA-256 özetleri) ve değişen/eklenen 24 proje dosyası **orijinal repo yollarıyla** tek bir ZIP arşivinde paketlenmiştir.
