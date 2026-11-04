# Küçük Video Oynatma + İndirme Kök Neden ve Çözüm Raporu — 2026-11-05

Konu: Detay sayfası **Videolar** sekmesindeki "küçük videolar" (fragman / PV / OP / ED /
müzik videosu) iki ayrı şikâyet:

1. **İndirme:** "Bu videonun sesi ve görüntüsü ayrı akışlarda sunuluyor; tek dosya olarak
   indirilemiyor." — kaynak ne olursa olsun video **inmeli**.
2. **Oynatma:** AnimeThemes kaynaklı açılış müzikleri küçük (kompakt) oynatıcıda
   "Bu video uygulama içinde oynatılamadı" kartına düşüyor.

Her iki şikâyet de tek bir kök nedene değil, **kaynaktan bağımsız tek bir indirme yolu
olmaması** + **kompakt oynatıcının tek adres / sabit süre mantığına sıkışması**na dayanıyor.
Aşağıda bulgular ve yapılan düzeltmeler.

---

## 1. Bulgular (kök nedenler)

### B1 — İndirme motoru yalnızca "tek parça, tek akış" varsayıyordu
Küçük videolar dört farklı biçimde geliyor:

| Kaynak | Gerçek adres türü |
|---|---|
| Shikimori PV / karakter tanıtımı | `youtube.com/watch?v=…` sayfası (akış çıkarılmalı) |
| AnimeThemes OP/ED | doğrudan `.webm` / `.mp4` dosyası |
| YouTube akışları | **ayrı** video-only + audio-only (adaptif) veya HLS |
| Eklenti (CS3) kaynakları | `.m3u8` / `.mpd` / imzalı doğrudan dosya |

Eski akış "ses ve görüntü ayrıysa indirme yok" diyerek pes ediyordu. Oysa aynı video için
YouTube tarafında **muxed HLS** veya **progressive (ses+görüntü tek dosya)** sürüm neredeyse
her zaman var; yoksa bile iki akış indirilip yeniden kodlanmadan birleştirilebilir.

### B2 — HLS/`.m3u8` desteği yoktu (küçük videolar için)
Bölüm indirmelerinde HLS mantığı vardı (`AnimeDownloader`), ancak küçük videolar için ortak
bir çözümleyici yoktu. `.m3u8` adresi doğrudan indirilmeye çalışılınca oynatılamayan bir
metin dosyası oluşuyordu.

### B3 — AnimeThemes için tek adres seçiliyordu
`KitsugiAnimeThemesClient` aday dosyalar arasından **tek** bir "en iyi" link seçiyordu.
Seçilen dosya cihazın çözemediği bir kodek (VP9 `.webm`) olduğunda başka hiçbir dosya
denenmiyordu. Aynı tema genelde H.264 `.mp4`, farklı çözünürlük ve `nc` sürümleriyle de
mevcut.

### B4 — Kompakt oynatıcıda sabit 18 saniye zaman aşımı
Hazırlık için sabit `18_000 ms` kullanılıyordu. Yavaş bağlantıda (sahada 2–4 KB/s görüldü)
dosya sağlıklı olsa bile süre doluyor, motor değişiyor, ikinci motor da aynı duvara
çarpıyor ve **hata kartı** çıkıyordu. Üstelik iki motor denemesi = 2 × 18 s boşa bekleme.

### B5 — İki motor da tükenince başka adres denenmiyordu
`switchToAlternateEngine` yalnızca Media3 ↔ MPV geçişi yapıyordu; "sıradaki dosya" diye bir
kavram yoktu. Bu yüzden tek bir bozuk/uyumsuz link tüm satırı ölü gösteriyordu.

### B6 — Doğrudan dosyalara yabancı `Origin` başlığı gönderiliyordu
`YouTubeRequestPolicy.headersForInlineUrl` YouTube dışı adreslere de `Origin: <host>`
ekliyordu. Bazı CDN'ler yabancı Origin başlığını CORS ihlali sayıp 403 dönebiliyor; bu da
"oynatılamadı" kartının sessiz bir kaynağı. Ayrıca `Accept` başlığı hiç gönderilmiyordu.

---

## 2. Yapılan düzeltmeler

### 2.1 Kaynaktan bağımsız indirme motoru — `core/download/video/`
| Dosya | Görev |
|---|---|
| `VideoDownloadModels.kt` | `VideoDownloadRequest/State/Phase` — satır bazlı ilerleme modeli |
| `VideoSourceClassifier.kt` | Adres sınıflandırma (YouTube / HLS / DASH / dosya), uzantı tahmini, güvenli dosya adı, göreli adres çözümü (**saf Kotlin**, testli) |
| `HlsPlaylistParser.kt` | Master/medya listesi çözümleme: varyant seçimi, `#EXT-X-MAP`, `#EXT-X-BYTERANGE`, `#EXT-X-KEY` (AES-128), göreli segment adresleri (**saf Kotlin**, testli) |
| `MediaStreamMuxer.kt` | Ayrı ses + görüntü akışlarını `MediaExtractor`/`MediaMuxer` ile **yeniden kodlamadan** tek MP4/WebM dosyasına yazar |
| `VideoDownloadManager.kt` | Uygulama kapsamlı kuyruk, aday zinciri, HLS parça indirme + AES-128 çözme, ayrı akışları indirip birleştirme, MediaScanner kaydı, Toast geri bildirimi |

Akış:
```
istek → YouTube ise akış çözümle (muxed HLS > progressive > adaptif çift)
      → HLS ise: master'dan en iyi varyant → init + segmentler → tek .mp4/.ts
      → ayrı ses/görüntü ise: iki akış indir → MediaMuxer ile tek dosya
      → doğrudan dosya ise: tek akış indir (Content-Type'tan uzantı)
      → herhangi bir adım hata verirse: sıradaki aday adres (en fazla 3 yedek)
```
Birleştirme cihazda mümkün değilse (ör. desteklenmeyen kodek kombinasyonu) **iki dosya da
eksiksiz kaydedilir** ve kullanıcıya "ses ve görüntü bu cihazda birleştirilemedi; iki dosya
ayrı kaydedildi" denir. Sessiz kayıp yok.

### 2.2 AnimeThemes yedek adres zinciri
- `KitsugiTheme.alternateVideoUrls` eklendi (`data class`, varsayılan boş → geriye uyumlu).
- `KitsugiAnimeThemesClient.rankThemeVideoLinks()`: tüm adaylar oynatma sırasına dizilir
  (temiz > MP4/H.264 > `nc` > WEB > ≤1080p > yüksek çözünürlük), tekilleştirilir.
  `pickBestThemeVideoLink()` artık bu zincirin ilk elemanı (mevcut testler bozulmadı).

### 2.3 Kompakt oynatıcı: adres zinciri + ilerleme tabanlı bekçi
`KitsugiDetailThemesTrailerComponents.kt`
- `ThemePlayerContainer(urls: List<String>)` — sıra: **dosya + motor → aynı dosya + yedek
  motor → sıradaki dosya + uygun motor → hata kartı**.
- Sabit süre kaldırıldı; `bufferedPosition`/`currentPosition` ilerlemesi izlenir
  (25 s tıkanma eşiği, 120 s üst sınır). Yavaş ama sağlıklı indirme artık "bozuk" sayılmaz.
- Hata kartındaki "Yeniden Dene" tüm zinciri baştan dener.

`InlinePlayerSupport.kt`
- `INLINE_PLAYER_STALL_TIMEOUT_MS`, `INLINE_PLAYER_START_HARD_CAP_MS`,
  `inlineStartShouldAbandon(...)` eklendi (saf fonksiyon → testli).

`YouTubeRequestPolicy.kt`
- Doğrudan dosyalarda `Origin` başlığı kaldırıldı (403 kaynağı), `Accept: video/*` eklendi.

### 2.4 UI: satır içi indirme
`DetailVideoRow` sağ tarafına indirme düğmesi eklendi; durum ikonları:
indir → `%` ilerleme → ✓ (tamam) / ⚠ (hata, dokununca yeniden dene).
Videolar sekmesi durumu `VideoDownloadManager.states` akışından okur; satır yeniden
oluşturulsa da ilerleme kaybolmaz.

Yeni metinler (TR + EN): `video_download_action`, `video_download_done_short`,
`video_download_retry`, `video_download_completed`, `video_download_failed`,
`video_download_split_saved`.

### 2.5 Testler
- `VideoSourceClassifierTest` — YouTube biçimleri, HLS/DASH/dosya ayrımı, uzantı tahmini,
  göreli adres çözümü, dosya adı güvenliği.
- `HlsPlaylistParserTest` — master algılama, 1080p tavanlı varyant seçimi, fMP4 `EXT-X-MAP`,
  `BYTERANGE` birikimi, AES-128 anahtarının yalnızca sonraki parçalara taşınması, tırnak
  içinde virgül içeren öznitelikler.
- `InlinePlayerSupportTest` — yavaş-ama-ilerleyen hazırlık bırakılmaz, gerçek tıkanma yedek
  motora geçirir, üst sınır sonsuz skeleton'ı engeller.
- `KitsugiAnimeThemesClientTest` — yedek adres zinciri sırası ve tekilleştirme.

---

## 3. Elle doğrulama listesi (cihazda)

1. **Shikimori PV (YouTube)** → satırdaki indirme ikonu → yüzdeler ilerler, tek `.mp4`
   iner, Bildirim/Galeri'de görünür, uygulama içinde oynar.
2. **AnimeThemes OP (webm)** → indirme → tek dosya; oynatıcıda aç → görüntü gelir.
3. VP9 çözücüsü olmayan cihazda AnimeThemes OP → oynatıcı otomatik MPV'ye, olmazsa sıradaki
   adaya (mp4) geçer; hata kartı yalnızca hepsi tükenirse çıkar.
4. Yavaş ağ (2–5 KB/s simülasyonu) → kompakt oynatıcı hata kartı vermez, yüklenmeye devam eder.
5. Aynı satıra iki kez dokunma → tek indirme (çift dosya yok).
6. İndirme yarıda iptal (ekranı kapat) → `.part` dosyası `cacheDir/kitsugi_video_downloads`
   altında kalır, `Downloads/Kitsugi/Video` içinde yarım dosya görünmez.

## 4. Bilinen sınırlar
- DASH (`.mpd`) için yalnızca tek gösterimli (muxed) akışlar desteklenir; ayrı gösterimler
  ayrı akış yoluyla (indir + birleştir) ele alınır. Karmaşık SegmentBase/şifreli DASH
  akışları hata mesajıyla dürüstçe raporlanır.
- AES-128 dışındaki HLS şifrelemeleri (`SAMPLE-AES`) desteklenmez; mesaj gösterilir.
- İndirme, uygulama süreci yaşadığı sürece sürer (WorkManager'a taşınması ayrı bir iş).
