# Kitsugi Beta v2.4.370

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Oynatıcı Seekbar & Sürükleme Deneyimi
- **Kesintisiz Sürükleme Kontrolü (`PlayerControls`):** Zaman çubuğunda sürükleme yapılırken arayüz kontrollerinin (OSD) ekrandan kaybolması engellendi. Kullanıcı parmağını veya imlecini çekene kadar zaman çubuğu ve önizleme ekranda kalır.
- **Dinamik Sayaç Yenileme:** Sürükleme bırakıldığında video hedef konuma atlar ve OSD gizlenme sayacı baştan başlatılır.
- **Still-Watching Entegrasyonu:** Sürükleme eylemleri aktif kullanıcı etkileşimi olarak işlenir; izleme limiti uyarısının zamansız açılması önlenir.

### 2. Video & Akış Kimlikli "Kaldığın Yerden Devam" Mimarisi
- **Akışa Özel Devam Konumu (`PlayerResumeStore` & `PlayerResumeIdentity`):**
  - Oynatma ilerlemesi artık yalnızca medya ve bölüm numarasına göre değil; medya, sezon, bölüm, eklenti adı, kaynak başlığı ve kararlı akış URL'si/hash'i ile SHA-256 depolama anahtarına bağlanır.
  - İmzalı bağlantılardaki (`token`, `expires`, vb.) dönen sorgu parametreleri filtrelenir; link süresi dolsa dahi aynı videonun devam konumu korunur.
  - Aynı bölümde farklı bir eklenti veya kaynak seçildiğinde başka videonun konumu yanlışlıkla devralınmaz.
- **Kaynak Değişimi ve Çıkışta Kayıt:**
  - Farklı bir akış kaynağına geçiş yapıldığında ve oynatıcı kapatılırken son konum anlık olarak saklanır.
  - Tamamlanmış (sonuna kadar izlenmiş) bölümlerin devam kayıtları temizlenir.

### 3. Sonraki Bölüm Önceden Arama & Hızlı Geçiş
- **Akıllı Eklenti Ön Yüklemesi (`prefetchNextEpisode`):**
  - İzlenmekte olan eklentinin sonraki bölüm verisi arka planda önceden aranır ve oturum içi önbelleğe alınır.
  - Bölüm geçişinde önbellekteki bu sonuçlar kullanılarak bekleme süresi ortadan kaldırılır. Tercih edilen eklenti uygun sonuç vermezse diğer kaynaklarla arama yedeği devreye girer.
- **Yarış Durumu Koruması (`episodeRequestGeneration`):**
  - Yeni bölümün kaynak listesi ancak akış bağlantısı başarıyla çözümlendikten sonra geçerli kılınır; eşzamanlı bölüm isteklerinde eski yanıtların oynatıcı durumunu ezmesi engellendi.

### 4. Fiziksel Klavye & Medya Kısayolları
- **Genişletilmiş Klavye Kısayolları (`KitsugiFullscreenPlayerScreen`):**
  - `Boşluk` / `K`: Oynat / Duraklat
  - `Sol Ok` / `Sağ Ok`: 5 saniye geri / ileri (Shift ile 30 saniye, Ctrl ile 60 saniye)
  - `J` / `L`: 10 saniye geri / ileri
  - `Yukarı Ok` / `Aşağı Ok`, `+` / `-`: Ses seviyesini artır / azalt
  - `M`: Tek dokunuşla sessize al / sesi geri aç (Mute toggle)
  - `[` / `]`: Ekran parlaklığını azalt / artır
  - `<` / `>` (Virgül / Nokta): Oynatma hızını azalt / artır (0.1x adımlarla)
  - `N` / `P`: Sonraki / Önceki bölüme geç
  - `Home` / `End`: Videonun başına (%0) / sonuna (%95) atla
  - `0` - `9`: Video süresinin ilgili yüzdesine (%0 - %90) doğrudan atla
  - `A`: Ses parçaları (Audio tracks) arasında geçiş yap
  - `C`: Altyazı parçaları arasında döngü yap
  - `T`: Altyazıyı hızlıca aç / kapat
  - Standart medya tuşları (Play, Pause, Fast Forward, Rewind vb.) tam uyumlu çalışır.

---

## 🇬🇧 English Release Notes

### 1. Player Seekbar & Scrubbing Enhancements
- **Continuous Scrubbing OSD Hold (`PlayerControls`):** The on-screen display (OSD) controls no longer disappear while the user is actively dragging or scrubbing the seekbar.
- **Dynamic Timer Restart:** Releasing the seekbar applies the new playback position immediately and restarts the auto-hide timer.
- **Still-Watching Awareness:** Scrubbing actions are reported as active user interaction, preventing premature "Still watching?" timeout overlays.

### 2. Stream-Specific Resume Identity Architecture
- **Exact Stream Identity (`PlayerResumeStore` & `PlayerResumeIdentity`):**
  - Playback progress is no longer keyed solely to series and episode numbers. It is now bound to a SHA-256 storage key composed of media ID, season, episode, addon name, source title, and stable stream URL/infohash.
  - Transient signed URL parameters (`token`, `expires`, etc.) are stripped during key derivation, ensuring rotating links retain the correct position.
  - Switching between different sources on the same episode will never erroneously inherit progress from an incompatible video file.
- **Safe Persistence on Source Switch and Exit:**
  - Saves playback position on source changes and player exit. Completed videos have their resume points safely cleared.

### 3. Background Next-Episode Prefetch & Instant Transition
- **Addon-Specific Prefetching (`prefetchNextEpisode`):**
  - While watching, streams for the next episode are proactively fetched in the background from the active provider and cached in-memory.
  - Next episode playback starts immediately from the pre-warmed stream. If the preferred provider has no matches, it automatically falls back to full provider search.
- **Race Condition Prevention (`episodeRequestGeneration`):**
  - Active stream lists are only updated once resolution succeeds. Stale asynchronous responses cannot overwrite current player state.

### 4. Physical Keyboard & Media Key Navigation
- **Rich Keyboard Navigation (`KitsugiFullscreenPlayerScreen`):**
  - `Space` / `K`: Play / Pause
  - `Left` / `Right Arrow`: Seek 5s (with Shift: 30s, with Ctrl: 60s)
  - `J` / `L`: Seek 10s backward / forward
  - `Up` / `Down Arrow`, `+` / `-`: Volume up / down
  - `M`: Toggle mute
  - `[` / `]`: Adjust brightness down / up
  - `<` / `>` (Comma / Period): Decrease / increase playback speed (by 0.1x)
  - `N` / `P`: Next / Previous episode
  - `Home` / `End`: Jump to start / end of video
  - `0` - `9`: Jump directly to percentage of duration (0% - 90%)
  - `A`: Cycle audio tracks
  - `C`: Cycle subtitle tracks
  - `T`: Toggle subtitles on / off
  - Full support for hardware media keys (Play, Pause, Stop, Fast Forward, Rewind).
