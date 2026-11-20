# ÇÖZÜM RAPORU — İndirilen Videoda Ses Ayrı Dosyaya Düşüyordu

**Tarih:** 2026-11-20
**Dal:** `arena/2f0ed9cd-kitsugi-beta` (temel: `97b5aa4`)
**Kapsam:** hdfilmcehennemi / filmakinesi (Close CDN, Rapidrame) + tüm eklentiler
**Şikâyet (kullanıcı):** *"İnen videolar video ayrı ses ayrı iniyor... video indirirken ses videoya gömülü olmalı; sesi ayrı indirmek istersek başka."*

---

## 1. Kök nedenler

### KN-1 — Birleştirme motoru hiç devreye girmiyordu (asıl neden)
`AnimeDownloader.finalizeDownloadedMedia(...)` ayrık ses (`#EXT-X-MEDIA:TYPE=AUDIO`,
`externalAudioUrl`, DASH ses adaptasyonu) indirildiğinde **yalnızca** `MediaRemuxer.muxVideoAndAudio`
çağırıyordu.

`MediaRemuxer` platform `MediaExtractor` + `MediaMuxer` kullanır ve:
- TS parçalarındaki çoklu AAC karelerinde PTS çakışması/ses örneği kaybı yaşar,
- `E-AC-3` / `AC-3` / `DTS` / `MP3` / `FLAC` / `Opus` / 5.1 sesleri MP4'e yazamaz,
- yazamadığında **hiçbir dönüştürme yapmadan** `CompanionAudioRequired` döner.

Sonuç: `Download/Kitsugi/Video/<Bölüm>.audio.m4a` gibi **ayrı ses dosyası** + sessiz/az sesli video.
Oysa depoda zaten bu işi çözen `MediaTrackRemuxer` (Media3 extractor + AAC-LC transcode +
`UniversalAudioTranscoder`) vardı — **bu indirme yolundan hiçbir yerde çağrılmıyordu**
(`git grep remuxVideoWithAudios` → yalnızca tanımı). Kullanıcının gördüğü "ses ayrı iniyor"
tam olarak bu ölü koddu.

### KN-2 — MP4'ün reddettiği gömülü sesler dönüştürülmüyordu
Tek akışta gömülü ses varsa `.ts → .mp4` adımı yine `MediaRemuxer.remuxSingleTsToMp4IfLossless`
ile yapılıyordu. `E-AC-3`/`AC-3` görünce `.ts` dosyasını "koruyordu" (ses kaybı yok ama MP4 dışı
kapsayıcı) — ya da `MediaExtractor` sesi hiç göstermezse **sessiz MP4** üretilebiliyordu.

### KN-3 — Sessiz dosya "indirildi" sayılıyordu
Video indirme yolunda `DownloadVerifier` **hiç çağrılmıyordu** (yalnızca bağımsız ses indirmesinde
vardı). Ses izi olmayan dosya `COMPLETED` olarak listeleniyordu.

### KN-4 — A/V senkron bilgisi (ID3 damgası) motora aktarılmıyordu
`audioFirstId3PtsUs` (Packed-Audio `com.apple.streaming.transportStreamTimestamp`) yalnızca
`MediaRemuxer`'a veriliyordu; evrensel motor sesi her zaman 0'dan başlatıyordu → birleştirme
çalışsa bile dudak senkronu kayabiliyordu.

### KN-5 — Kliplerde "ayrı ses = indirilemez" hatası
`ClipDownloadSource` ses ayrı akıştaysa indirmeyi **tamamen reddediyordu**
(`Failure.SEPARATE_AUDIO` → "tek dosya olarak indirilemiyor"). Aynı sözleşme ihlali.

---

## 2. Yapılan düzeltmeler

### 2.1 `AnimeDownloader.finalizeDownloadedMedia` — yeni birleştirme sırası
1. **`MediaTrackRemuxer.remuxVideoWithAudios`** (evrensel motor)
   - video + ayrık ses → kayıpsız `MediaMuxer` mux,
   - ses kodeki MP4'e uygun değilse **otomatik stereo AAC-LC'ye çevirip aynı `.mp4`'e gömer**,
   - çıktı `hasBothTracks()` ile doğrulanır (hem görüntü hem ses izi).
2. **`MediaRemuxer.muxVideoAndAudio`** (platform güvenlik ağı).
3. Yalnızca ikisi de başaramazsa yan ses (`<taban>.audio.<uzantı>`) — oynatıcı `MergingMediaSource`
   ile yine tek parça oynatır.

Mux ara dosyaları artık `cacheDir/kitsugi_mux_*` altında; kullanıcının `Video/` klasörüne
`merged_output.mp4` gibi artıklar sızmıyor.

### 2.2 `remuxEmbeddedAudioIfNeeded` (yeni)
Tekil akış (gömülü ses) için:
- kapsayıcı `ts` ise, ya da
- ses kodeki 1–2 kanallı AAC/Opus **değilse** (E-AC-3, AC-3, DTS, MP3, FLAC, 5.1/7.1)

ses AAC-LC'ye çevrilip **aynı dosyaya** yazılır (`.mp4`). Ses izi hiç yoksa dönüştürme denenmez;
o durum sessiz-dosya kontrolünde yakalanır.

### 2.3 Sessiz dosya artık "indirildi" sayılmıyor
`finalizeDownloadedMedia` sonrasında:
- `DownloadVerifier.verify(...)` (cihaz decoder kontrolü),
- `MediaTrackRemuxer.probeTracks(...)` ile **ses izi** kontrolü.

Ses izi yok ve yan ses dosyası da yoksa dosya **silinir** ve kalıcı hata verilir:
`indirme_hata_ses_izi_yok` ("İndirilen videoda ses izi bulunamadı; dosya sessiz kalacağı için
kaydedilmedi…"). `DownloadResilience.classify` artık `UnsupportedMediaException`'ı ağ hatası
saymıyor (boşuna 6 kez retry yok).

### 2.4 A/V senkronu — `MediaTrackRemuxer.alignAudioPtsUs`
- `remuxVideoWithAudios(audioStartPtsUs = ...)` → `tryMuxSeparateVideoAndAudio`'ya aktarılır,
- ID3 mutlak damgası varsa ses o damgaya göre hizalanır (video 2 sn'de başlıyorsa ses de 2 sn'de),
- iki zaman ekseni arasındaki fark > 5 sn ise (farklı eksen/ölçüm hatası) ses videoyla birlikte
  0'dan başlar — dosya başında dakikalarca sessizlik oluşmaz.

### 2.5 Klipler (fragman / kamera arkası / tema)
`ClipDownloadSource.Resolved` artık `audioUrl` taşır; `Failure.SEPARATE_AUDIO` **reddi kaldırıldı**.
`downloadClip` sesi indirir, `remuxVideoWithAudios` ile gömer; mux başarılıysa dosya `.mp4`
uzantısıyla kaydedilir ve eski `.ts` artığı silinir.

### 2.6 Metinler (XML string sayısı: +2, DEX 64K sınırı için minimal)
- `indirme_hata_ses_izi_yok` (tr + en)

---

## 3. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `core/download/AnimeDownloader.kt` | Yeni birleştirme sırası, `remuxEmbeddedAudioIfNeeded`, `isMp4MuxFriendlyAudio`, sessiz-dosya doğrulaması, klip mux akışı, `remuxVideoWithAudios(audioStartPtsUs)` bağlantısı |
| `core/download/MediaTrackRemuxer.kt` | `audioStartPtsUs` parametresi, `alignAudioPtsUs` (saf fonksiyon), `hasBothTracks`, mux çıktısı ses izi doğrulaması |
| `core/download/ClipDownloadSource.kt` | `Resolved.audioUrl` + `SEPARATE_AUDIO` reddinin kaldırılması |
| `core/download/DownloadResilience.kt` | `UnsupportedMediaException` → `ErrorKind.OTHER` (kalıcı) |
| `app/src/test/.../ClipDownloadTest.kt` | `alignAudioPtsUs` için 3 yeni birim testi |
| `res/values/strings.xml`, `values-en/strings.xml` | `indirme_hata_ses_izi_yok` |

---

## 4. Doğrulama

- Bu ortamda **Android SDK / JDK / Gradle yok** (`java`, `javac`, `gradle` bulunamadı) →
  `./gradlew testDebugUnitTest` çalıştırılamadı. Derleme doğrulaması yapılmadı.
- Yapılabilen statik kontroller:
  - Değişen tüm Kotlin dosyalarında parantez/süslü parantez dengesi HEAD ile aynı (`(0, 0)`),
  - `values/strings.xml` ve `values-en/strings.xml` XML parse: **OK**,
  - Çağrı imzaları elle doğrulandı (`remuxVideoWithAudios`, `tryMuxSeparateVideoAndAudio`,
    `finalizeDownloadedMedia`, `MediaTrackRemuxer.hasBothTracks` internal erişim).
- Riski düşüren tasarım: her yeni adım `runCatching` + doğrulama ile sarıldı; evrensel motor
  başarısız olursa eski `MediaRemuxer` yolu aynen çalışmaya devam ediyor (davranış gerilemez).

## 5. Beklenen davranış (sonrası)

| Senaryo | Önce | Sonra |
|---|---|---|
| HDFC/FM ayrık sesli HLS (Türkçe dublaj + orijinal) | video + `.audio.m4a` ayrı | **tek `.mp4`**, seçilen dil gömülü |
| Ses E-AC-3 / AC-3 / DTS / 5.1 | ayrı ses dosyası / sessiz video | AAC-LC'ye çevrilip **videoya gömülü** |
| Gömülü sesli TS akışı | `.ts` olarak kalıyor / sessiz mp4 | ses korunarak `.mp4` |
| Ses izi hiç yok | "indirildi" (sessiz) | net hata + dosya silinir |
| Klip (YouTube adaptive) | "tek dosya indirilemiyor" hatası | video + ses **tek `.mp4`** |
| Kullanıcı 🎧 "sesi ayrı indir" derse | `Download/Kitsugi/Audio/...` | değişmedi (aynen çalışır) |
