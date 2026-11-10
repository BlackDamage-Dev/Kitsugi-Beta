# Çözüm Raporu — Mini (satır içi) oynatıcı arayüzü ve "bazı videolar oynatılmıyor" sorunu

**Tarih:** 2026-10-22
**Kapsam:** Detay sayfası > Videolar sekmesi (Ön İzleme + Açılış/Kapanış Müzikleri) ve
Bilgi sekmesindeki tema listesi — `KitsugiPlayerGestureWrapper`, `ThemePlayerContainer`,
`KitsugiYouTubePlayer`.

## 1. Belirti

Kullanıcı ekran görüntüsünde (Chainsaw Man ED1 "JANE DOE") videonun tam ortasında
üst üste binmiş bir yığın görülüyordu: geri sarma ikonu + oynat üçgeni + `0s` yazısı.
Yani iki ayrı katman aynı merkeze çiziliyordu. Ayrıca "bazı videolar oynatılıyor, bazıları
oynatılmıyor" şikâyeti vardı.

## 2. Kök nedenler

### 2.1 Arayüz (ekran görüntüsündeki yığın)

1. **İki overlay aynı merkeze çiziliyordu.** `KitsugiPlayerGestureWrapper` çift dokunma geri
   bildirimini (`-10s` / `+10s` balonu) ve ortadaki oynat/duraklat düğmesini aynı `Box`
   merkezine koyuyordu. Ana oynatıcıda (`MiddlePlayerControls`) bu alan tek bir durum
   makinesidir (Seek / Loading / PlayPause / Hidden) ve durumlar birbirini dışlar.
   Mini oynatıcıda böyle bir ayrım yoktu → `◀◀ ▶ 0s` görüntüsü.
2. **Kısayol balonu her zaman merkezdeydi.** Hangi tarafa çift dokunulursa dokunulsun
   gösterge ekranın ortasında çıkıyordu; kullanıcı "kısayol tıklamalarının çıktığı konumlar
   saçma" diyor. Artık sol dokunuşta sol, sağ dokunuşta sağ tarafta çıkıyor.
3. **Kontrol gizlenme süresi sabit 3 sn idi.** Ana oynatıcı `settings.playerTimeToDisappearMs`
   değerini `PlayerUiPolicy` ile 1–30 sn aralığına sıkıştırıp kullanıyor. Mini oynatıcı bunu
   yok sayıyordu.
4. **Çift dokunma saniyesi sabit 10 sn idi.** Ana oynatıcı `settings.doubleTapSeekSeconds`
   kullanıyor; mini oynatıcıda ayar etkisizdi. Ayrıca ana oynatıcıdaki **orta bölge =
   oynat/duraklat** davranışı yoktu (mini oynatıcı sadece sol/sağ yarıyı biliyordu).
5. **Tampon göstergesi kontrol katmanının dışında** ayrı bir `CircularProgressIndicator`
   olarak çiziliyordu; merkez düğmesi/balon ile çakışabiliyordu.
6. **İlerleme çubuğunda iki ayrı `pointerInput` bloğu** vardı: `detectTapGestures` `down`
   olayını tükettiği için sürükleme güvenilir değildi. Sürüklerken hedef süre görünmüyordu,
   süre bilinmiyorken `00:00` yazıyordu ve çok dar genişlikte `coerceIn(min, max)` çökme
   riski taşıyordu.

### 2.2 Oynatma — "bazı videolar oynatılmıyor"

1. **`extractYouTubeId` çok dardı.** Yalnızca `youtube.com/watch?v=…` (v **ilk** parametre)
   ve `/embed/` biçimlerini tanıyordu. Şu tamamen geçerli adresler tanınmıyordu:
   `watch?app=desktop&v=…`, `watch?feature=share&v=…`, `youtube.com/shorts/…`,
   `youtube.com/live/…`, `music.youtube.com/watch?v=…`, `m.youtube.com/…`.
   Tanınmayan adresler **doğrudan medya dosyası** sanılıp Media3'e veriliyordu; oynatıcı bir
   HTML sayfasını indirmeye çalışıp hata veriyordu. (Ek olarak protokol-göreli
   `//www.youtube.com/embed/…` biçimi de destekleniyor.)
2. **"Ses var, görüntü yok" bekçisi 10. saniyede pes ediyordu.** `waitForInlineVideoTrack`
   `graceMs + 4 sn` sonunda sessizce çıkıyordu. Yavaş açılan bir 1080p VP9 `.webm`
   (AnimeThemes OP/ED) 12. saniyede oynatmaya başlarsa hiçbir kontrol kalmıyor, ekran
   sonsuza kadar siyah kalıyordu.
3. **Motor değiştirmeden önce çözücü dürtermesi yoktu.** Bazı donanım/GPU sürücüleri ilk
   kareyi ancak 1 ms'lik bir seek sonrası üretiyor. Bekçi artık motoru değiştirmeden önce
   bir kez `seekTo(currentPosition + 1)` dener.
4. **"Tekrar Dene" aynı motor örneğini yeniden hazırlıyordu.** Hata sonrası serbest
   bırakılmış yüzey/çözücü kalıntıları yeni denemeyi de bozabiliyordu; kullanıcı aynı siyah
   ekranla karşılaşıyordu. Artık her deneme taze motor örneği kurar.
5. **Motoru elle değiştirme yolu yoktu.** İki motor da başarısız olduğunda yalnızca "Tam
   ekranda aç" ve "Tekrar Dene" vardı. Artık **"Diğer motorla dene"** de var (Media3 ↔ MPV).
6. **Video kimliği olmayan YouTube sayfaları** (`/results?search_query=…`, kanal sayfası)
   Media3'e veriliyordu → kesin hata. Artık dürüst bir "Tarayıcıda Aç" kartı gösteriliyor.
7. **Oynat/duraklat durumu bazı motorlarda arayüze hiç ulaşmıyordu.** `ThemePlayerContainer`
   ve `KitsugiYouTubePlayer` yalnızca `onStateChanged` geri çağrısını uyguluyordu; MPV
   motorunda duraklat/oynat geçişi **sadece** `PlayerEngine.Listener.onPlayingChanged`
   üzerinden bildirilir ve `State` `READY` olarak kalır. Sonuç: `isPlayingState` eski
   değerinde donuyor, kullanıcı "oynat" düğmesine bastığında kod tekrar `pause()` çağırıyor
   ve video hiç başlamıyormuş gibi görünüyordu. Bu, "bazı videolar açılmıyor" şikâyetinin
   en sinsi kaynağıydı: dosya sağlam, oynatıcı hazır, ama düğme ters çalışıyor.

## 3. Yapılan değişiklikler

### `KitsugiDetailPlayerComponents.kt`
- `extractYouTubeId` yeniden yazıldı: sorgu parametresi sırasından bağımsız `v`, `shorts`,
  `live`, `embed`, `v`, `e`, `watch`, `youtu.be`, `music.`/`m.` alt alan adları ve
  protokol-göreli adresler. Yardımcı: `isYouTubePageUrl`.
- `KitsugiPlayerGestureWrapper` yeniden yazıldı:
  - Merkez alan `AnimatedContent` ile **tek durum**: Buffering / PlayPause / Hidden.
  - Çift dokunma balonu **tıklanan tarafta** (`CenterStart` / `CenterEnd`), hedef süre ile.
  - Bölgeler ana oynatıcıyla aynı: sol %40 geri, sağ %40 ileri, orta bölge oynat/duraklat.
  - `doubleTapSeekSeconds` ve `controlsAutoHideMs` parametreleri (ayarlardan besleniyor).
  - Alt çubuk ana oynatıcıyla aynı düzende; sürüklerken hedef süre canlı gösterilir,
    süre bilinmiyorken `--:--`.
  - İlerleme çubuğu tek jest döngüsü (`awaitEachGesture`), accent renkli dolgu + top.
- `KitsugiYouTubePlayer`: motor durumu yukarı taşındı (hata kartından erişilebilir),
  `engineAttempt` ile taze motor, nudge, ayar tabanlı jest süreleri, hata kartında
  "Diğer motorla dene" + "Tarayıcıda Aç".
- `PlayerEngine.Listener.onPlayingChanged` uygulandı → oynat/duraklat düğmesi artık MPV
  dahil her motorda gerçek durumu yansıtıyor.

### `KitsugiDetailThemesTrailerComponents.kt` (`ThemePlayerContainer`)
- Aynı motor/deneme/nudge/ayar aktarımları; `engineAttempt` ile taze motor.
- `PlayerEngine.Listener.onPlayingChanged` uygulandı (MPV'de oynat/duraklat durumu yalnızca
  bu yolla gelir; eksikliği düğmenin ters çalışmasına yol açıyordu).
- Motor değişiminde `hasStartedPlayback` sıfırlanır (yeni motor için tampon göstergesi).
- Hata kartına "Diğer motorla dene" eklendi.
- Video kimliği olmayan YouTube sayfaları için "Tarayıcıda Aç" kartı.

### `InlinePlayerSupport.kt`
- `INLINE_AUDIO_ONLY_GRACE_MS`: 6 sn → **3,5 sn**.
- Yeni: `INLINE_NUDGE_GRACE_MS` (2,5 sn), `INLINE_VIDEO_FRAME_MAX_WAIT_MS` (45 sn).
- Yeni saf karar fonksiyonu `inlineVideoVerdict(...)` → HEALTHY / WAITING / NUDGE_RENDERER /
  SWITCH_ENGINE (birim testleri eklenebilir).
- `waitForInlineVideoTrack` artık sabit sürede pes etmiyor: kare gerçekten çizilene kadar
  (en fazla 45 sn) izler, gerekiyorsa çözücüyü dürter, duraklatma sırasında yanlış hüküm
  vermez.

### Kaynaklar
- `values/strings.xml` ve `values-en/strings.xml`: `inline_try_other_engine`
  ("Diğer motorla dene" / "Try another engine").

### Arayüzdeki ince hatalar (ek tur)
- İlerleme çubuğunda bırakma olayı artık tüketiliyor; çubuğa dokunup atlarken alttaki
  dokunma katmanı bunu "kontrolleri kapat" olarak yorumlayıp alt çubuğu göz kırptırmıyor.
- Motor değişiminde `isPlayingState` sıfırlanıyor; yeni motorun ilk karesi gelmeden
  düğme "duraklat" göstermiyor (yanlış ikon → ilk dokunuşta `pause()`).
- `isYouTubePageUrl` artık `youtube-nocookie.com` sayfalarını da tanıyor (kimliksiz
  adres Media3'e HTML olarak gitmiyor).

### Testler
- `YouTubeUrlParsingTest` (yeni, 7 test): tüm YouTube URL biçimleri, protokol-göreli
  adresler, kimliksiz YouTube sayfaları + YouTube olmayan adreslerin yanlış
  yorumlanmaması.
- `InlinePlayerSupportTest` (+3 test): nudge/switch kararı, grace beklemesi, üst sınır.
- Toplam: 20 birim testi (statik olarak gözden geçirildi, bu ortamda çalıştırılamadı).

## 4. Doğrulama

- `git diff --check`: temiz.
- Kaynak dosyalarda parantez/süslü parantez dengesi kontrol edildi: temiz.
- `strings.xml` XML ayrıştırması: geçerli (TR + EN).
- Kullanılan tüm metin anahtarları iki dilde de var: `inline_player_play|pause|fullscreen`,
  `inline_open_fullscreen`, `inline_try_other_engine`, `inline_video_unsupported_title|body`,
  `inline_open_in_browser_eed20ab`.
- Kullanılmayan import taraması: temiz (kaldırılan tampon halkasının artık importu silindi).
- `./gradlew :app:testDebugUnitTest --no-daemon`: **çalıştırılamadı** — bu ortamda JDK yok
  (`java` PATH'te değil) ve Android SDK tanımlı değil. Testler ve derleme cihazda/CI'da
  doğrulanmalı.

## 5. Cihazda doğrulanacaklar

1. OP/ED listesinde `.webm` (VP9) ağırlıklı bir yapım: siyah ekran yerine ~3,5 sn içinde
   çözücü dürtmesi/MPV geçişi ile görüntü gelmeli.
2. `youtube.com/shorts/…` veya `watch?app=desktop&v=…` biçimli bir fragman: mini oynatıcıda
   açılmalı (eskiden kesin hata veriyordu).
3. Aynı videoda çift dokunma: balon yalnızca tıklanan tarafta, ortadaki oynat düğmesiyle
   çakışmadan görünmeli.
4. Ayarlar > Jestler > çift dokunma süresi ve Oynatıcı > kontrol gizlenme süresi mini
   oynatıcıda da etkili olmalı.
5. Her iki motorun da başarısız olduğu bir dosyada "Diğer motorla dene" akışı.
6. Duraklat → oynat: düğme ikonu gerçek durumu izlemeli; ikinci dokunuş videoyu
   duraklatılmış bırakmamalı (MPV motorunda `onPlayingChanged` düzeltmesi).
7. İlerleme çubuğuna dokunarak atlama: alt çubuk göz kırpmadan hedefe atlamalı.
