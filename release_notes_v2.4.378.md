# Kitsugi Beta v2.4.378

[TR] **AniList MDBList IMDb Çözümü, Dolby Vision SurfaceView & Yaşam Döngüsü, Torrent Tampon Normalizasyonu ve Altyazı Boyutu Varsayılanı**
[EN] **AniList MDBList IMDb Resolution, Dolby Vision SurfaceView & Lifecycle Retention, Torrent Buffer Normalization, and Subtitle Font Size Defaults**

---

### [TR] Yenilikler ve İyileştirmeler

#### 1. AniList İçin MDBList ve IMDb Puan Çözümü
- **Gerçek MAL Kimliği Koruması:** AniList arama ve detay sonuçlarında MAL kimliği (`idMal`) bilinen serilerde (ör. Attack on Titan / Shingeki no Kyojin, MAL ID: 16498) MAL kimliğinin koşulsuz boşaltılması engellendi.
- **Doğru Kimlik Ayrımı (`resolveMdbListAniListIds`):** AniList'ten gelen gerçek MAL kimliği (`< 100_000_000`) ile MAL eşleşmesi bulunmayan 100M+ offset'li AniList kimlikleri birbirinden net biçimde ayrıştırılarak MDBList istek nesnesine iletilir.
- **Harici Puanlar / IMDb Görünürlüğü:** AniList başlıklarında **Bilgi → İstatistikler → Harici Puanlar → IMDb** puanı ve derecelendirmesi artık eksiksiz yüklenip gösterilir.
- **ARM & TMDB Kimlik Yedeği:** ARM/TMDB çözümlemesinden elde edilen TMDB kimliği hem arama sonuçları detay ekranına (`ApiResultDetailViewModel`) hem de kullanıcı liste kayıt detay ekranına (`MediaEntryDetailViewModel`) aktarılır.

#### 2. Dolby Vision ve Oynatıcı Yaşam Döngüsü İyileştirmeleri
- **SurfaceView Tabanlı Tam Ekran Oynatıcı (`fullscreen_player_view.xml`):** Tam ekran Media3 oynatıcı `TextureView` yerine doğrudan Android'in HDR ve Dolby Vision renk çıkışını koruyan `SurfaceView` kullanacak şekilde yapılandırıldı.
- **Kesintisiz Arka Plan Yaşam Döngüsü:** Uygulama arka plana geçtiğinde veya ekran kapandığında video oynatımı duraklatılır; ancak dekoder oturumu ve Activity kapatılmaz. Kullanıcı geri döndüğünde, yalnızca arka plana geçmeden önce oynuyorsa oynatmaya kaldığı yerden devam eder. Böylece Dolby Vision donanım çözücüsünün sıfırlanıp görüntünün kararması veya bozulması önlendi.
- **PixelCopy ile Ekran Görüntüsü Desteği:** `SurfaceView` yüzeyinden senkron UI thread'i kilitlemeden asenkron `PixelCopy` ile ekran görüntüsü alma ve altyazı katmanlarını birleştirme desteği eklendi (`TextureView` desteği de gömülü fragmanlar için korundu).

#### 3. Torrent Tampon Göstergesi Normalizasyonu
- **0..1 ve 0..100 Format Birleştirme:** Nuvio motorundan kesir (`0..1`) veya yüzde (`0..100`) olarak gelebilen tampon ilerleme verileri `normalizeP2pBufferProgress` ile normalize edildi.
- **Geçersiz Örnek Koruması:** NaN, sonsuz, negatif ve aralık dışı tampon örnekleri ayıklanarak son geçerli tampon değeri korundu. İlerleme çubuğunun ve göstergenin rastgele değerlere sıçraması engellendi (`bufferPercent` daima 0–100 aralığında sınırlandırıldı).

#### 4. Altyazı Tipografi Boyutu Varsayılanı (45) ve Kalıcılığı
- **Yeni Varsayılan:** Tipografi yazı boyutu varsayılanı ve paneldeki sıfırlama değeri 55 yerine 45 olarak ayarlandı.
- **DataStore Kalıcılığı:** Tam ekran oynatıcı altyazı panelinde yapılan tipografi yazı boyutu değişiklikleri `subtitle_font_size` anahtarıyla DataStore'a kaydedilir ve sonraki açılışlarda hatırlanır.

#### 5. Regresyon Test Güvencesi
- `MdbListAniListIdentityTest`: 5/5 test başarılı.
- `P2pBufferProgressTest`: 3/3 test başarılı (normalizasyon, NaN/Inf reddi, 0..100 sınırlandırma).
- `SettingsDataStoreTest`: Altyazı varsayılan boyutu ve DataStore gidiş-dönüş testleri doğrulandı.

---

### [EN] Release Notes

#### 1. AniList MDBList & IMDb Score Resolution
- **Preserved Real MAL Identity:** Resolved an issue where AniList search/detail entries with known MAL IDs (such as Attack on Titan / Shingeki no Kyojin, MAL ID: 16498) had their `realMalId` unconditionally cleared, which previously caused MDBList rating lookups to be skipped.
- **Accurate Dual Identity Decoding (`resolveMdbListAniListIds`):** Distinctly separates real MAL-backed IDs (`< 100_000_000`) from 100M+ offset AniList-only IDs, passing the appropriate identifiers to MDBList.
- **External Ratings / IMDb Visibility:** **Info → Statistics → External Ratings → IMDb** scores and ratings now load reliably for AniList titles.
- **ARM & TMDB Identity Fallback:** Propagates ARM/TMDB resolved TMDB IDs into both `ApiResultDetailViewModel` and `MediaEntryDetailViewModel`.

#### 2. Dolby Vision SurfaceView & Lifecycle Retention
- **SurfaceView Fullscreen Player (`fullscreen_player_view.xml`):** Switched the fullscreen Media3 PlayerView to `SurfaceView`, allowing Android to natively manage HDR and Dolby Vision color presentation without intermediate compositing overhead.
- **Decoder Preservation Across Backgrounding:** Backgrounding the player activity pauses playback without destroying decoder sessions or finishing the activity. Playback resumes automatically upon return only if it was playing beforehand, preventing corrupted or blank frames on Dolby Vision hardware.
- **PixelCopy Asynchronous Screenshots:** Implemented asynchronous `PixelCopy` frame capture for `SurfaceView` with subtitle overlay rendering, while preserving `TextureView` capture for embedded trailer playback.

#### 3. Torrent Buffer Progress Normalization
- **Fraction & Percentage Unification:** Engine buffer metrics delivered as either a fraction (`0..1`) or percentage (`0..100`) are normalized via `normalizeP2pBufferProgress`.
- **Invalid Sample Rejection:** Rejects NaN, infinite, negative, or out-of-range samples, retaining the previous valid buffer progress and bounding the display percentage strictly between 0% and 100%.

#### 4. Subtitle Typography Size Default (45) & Persistence
- **New Typography Default:** Updated default and reset typography font size to 45.
- **DataStore Persistence:** Subtitle font size adjustments made in the player panel are persisted across app sessions via DataStore (`subtitle_font_size`).

#### 5. Test Suite Verification
- `MdbListAniListIdentityTest`: 5/5 passed.
- `P2pBufferProgressTest`: 3/3 passed.
- `SettingsDataStoreTest`: Default font size 45 and roundtrip persistence verified.
