# Kitsugi Beta v2.4.454 — Entegrasyonlar Müzik Sekmesi ve Ses Çalma Motoru Düzeltmesi (Audio & Settings Fix)

### 🇹🇷 Türkçe Güncelleme Özeti

Bu sürümle birlikte Harici Entegrasyonlar ayarlarındaki "Müzik" sekmesinin boş/siyah görünmesi sorunu çözülmüş, manga okuyucu ortam sesi (prosedürel sentetik ve akış) motoru Android cihaz mimarileriyle %100 uyumlu ve kararlı hale getirilmiştir.

#### 🔧 Yapılan Düzeltmeler ve İyileştirmeler:

1. **Harici Entegrasyonlar — Müzik Sekmesi Tam Entegrasyonu (`KitsugiIntegrationsSettingsDialog.kt`):**
   - Sayfalayıcı (`HorizontalPager`) içerisinde 4. sayfa (`MusicSettingsTab`) çağrılmadığı için oluşan boş siyah ekran sorunu giderildi.
   - Sekmeye özel bağımsız kaydırma durumu (`musicScrollState`) eklendi ve `activeScrollState` ile bağlandı.
   - Jamendo, Freesound, Openverse, Internet Archive ve Audius API anahtarı alanları, doğrulama butonları, ortam sesi anahtarı, otomatik başlatma gecikme ayarı ve ses mikser seviyeleri eksiksiz görüntülenebilir ve kaydedilebilir hale getirildi.
   - Türkçe/İngilizce string simetrisi %100 sağlandı.

2. **Prosedürel Ses Çalma Motoru Kararlılığı (`ProceduralSoundPlayer.kt`):**
   - Donanım bağımlı ve bazı Android/MIUI cihazlarda sessizliğe veya çökmeye yol açabilen `ENCODING_PCM_FLOAT` yapısı, tüm Android mimarilerinde (%100) yerel olarak desteklenen `ENCODING_PCM_16BIT` stereo standardına dönüştürüldü.
   - Ses seviyesi (volume) kontrolü, donanım mikserinin `setVolume(0f)` kilitlenme ve sessizlik hatalarından kurtarılarak doğrudan DSP örnekleme katmanında yazılımsal (`clampSample(left[i] * curVol)`) olarak uygulandı.
   - `AudioTrack.STATE_INITIALIZED` ve `PLAYSTATE_PLAYING` kontrolleri eklendi; donanım hazır olmadan render döngüsünün başlatılması veya buffer dolu olduğunda döngünün erkenden sonlanması engellendi.

3. **Ortam Sesi Motoru ve Akış Uyumluluğu (`AmbientAudioEngine.kt`):**
   - `ProceduralVoice.start()` artık gerçek oynatma durumunu doğrulamakta ve ses çalmadığı halde çalıyor gibi görünmesini engellemektedir.
   - Çevrimiçi ses akışlarında (`StreamVoice`) Media3 ExoPlayer iş parçacığı güvenliği (`Main Looper`) sağlanarak arka plan iş parçacığı istisnaları giderildi.
   - Atmosfer presetleri çalınırken başlığın (`_activeTitle`) tekil katman isimleriyle ezilmesi önlenerek preset adının korunması sağlandı.

4. **Doğrulama ve Test:**
   - 29 prosedürel ses sentézi, tüm katalog kimlikleri, preset katman çözümleri ve PCM dönüşümleri için birim testleri (`ProceduralSoundSynthTest`) eklendi ve başarıyla geçti.
   - Pager performans kuralları ve yayın derlemesi (`assembleFossRelease`) sıfır hata ile tamamlandı.

---

### 🇬🇧 English Release Notes

Kitsugi Beta v2.4.454 resolves the blank "Music" integration settings tab issue and fixes the ambient sound playback engine to deliver flawless, reliable audio across all Android devices and hardware HALs.

#### 🔧 Bug Fixes & Improvements:

1. **External Integrations — Music Tab Display (`KitsugiIntegrationsSettingsDialog.kt`):**
   - Fixed the missing page 4 (`MusicSettingsTab`) invocation inside `HorizontalPager`, which previously caused a completely blank black screen when navigating to the Music tab.
   - Wired a dedicated `musicScrollState` into the dialog's `activeScrollState`.
   - Exposed all API connection fields (Jamendo, Freesound, Openverse, Internet Archive, Audius), verification actions, ambient sound toggles, autoplay delay slider, and volume sliders.
   - Maintained 100% Turkish/English string symmetry.

2. **Procedural Sound Engine Reliability (`ProceduralSoundPlayer.kt`):**
   - Migrated from hardware-sensitive `ENCODING_PCM_FLOAT` to universal `ENCODING_PCM_16BIT` stereo, guaranteeing 100% compatibility across all Android versions, custom ROMs, and chipsets.
   - Implemented direct Software DSP Volume Gain scaling (`clampSample(sample * curVol)`), preventing hardware mixer mute lockups caused by initial zero-volume fading.
   - Added rigorous `STATE_INITIALIZED` and `PLAYSTATE_PLAYING` verification, preventing premature write loop termination when audio buffers fill.

3. **Ambient Audio Engine & Stream Safety (`AmbientAudioEngine.kt`):**
   - `ProceduralVoice.start()` now verifies true AudioTrack playback state before reporting success.
   - Thread-safe Media3 ExoPlayer handling (`Main Looper`) implemented for `StreamVoice` volume fading and lifecycle operations.
   - Fixed preset title preservation so that active atmosphere names are not overwritten by individual layer titles.

4. **Testing & Verification:**
   - Added `ProceduralSoundSynthTest` verifying all 29 synths, catalog sound IDs, preset layer resolutions, and PCM16 conversions.
   - Pager performance guard and `assembleFossRelease` passed with zero errors.
