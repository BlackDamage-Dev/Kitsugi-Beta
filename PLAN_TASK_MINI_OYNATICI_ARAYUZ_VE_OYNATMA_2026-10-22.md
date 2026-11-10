# Plan / Task — Mini oynatıcı arayüzü ve "bazı videolar oynatılmıyor"

**Tarih:** 2026-10-22
**Kapsam:** Detay sayfası > Videolar sekmesindeki mini (satır içi) oynatıcı —
`KitsugiPlayerGestureWrapper`, `ThemePlayerContainer`, `KitsugiYouTubePlayer`.
**Durum:** Kod tamam, **derleme/cihaz doğrulaması bekliyor** (bu ortamda JDK/Android SDK yok).

## Belirti

1. Mini oynatıcıda kısayol/dokunma kontrolleri "saçma" konumlarda: videonun tam ortasında
   üst üste binmiş geri sarma ikonu + oynat üçgeni + `0s` yazısı (Chainsaw Man ED1
   "JANE DOE" ekran görüntüsü).
2. Bazı videolar mini oynatıcıda açılıyor, bazıları açılmıyor; sebep görünmüyor.

## Kök nedenler

### Arayüz
- Çift dokunma geri bildirimi ile ortadaki oynat/duraklat düğmesi **aynı `Box` merkezine**
  çiziliyordu; ana oynatıcıdaki gibi tek durum makinesi yoktu.
- Kısayol balonu her zaman merkezde çıkıyordu (hangi tarafa dokunulursa dokunulsun).
- Gizlenme süresi (3 sn) ve çift dokunma süresi (10 sn) **sabit**ti; ayarlar yok sayılıyordu.
- Ana oynatıcıdaki "orta bölge = oynat/duraklat" davranışı yoktu.
- Tampon göstergesi kontrol katmanının dışında ayrı bir halkaydı.
- İlerleme çubuğunda iki ayrı `pointerInput` birbirini yiyordu (dokunma `down`'ı tüketiyordu).

### Oynatma
- `extractYouTubeId` yalnızca `watch?v=` (v ilk parametre) ve `/embed/` tanıyordu; `shorts`,
  `live`, `watch?app=desktop&v=`, `music.`/`m.`, `youtu.be`, protokol-göreli adresler
  tanınmayınca adres "medya dosyası" sanılıp Media3'e veriliyordu (HTML indirme → hata).
- "Ses var, görüntü yok" bekçisi sabit sürede (≈10 sn) sessizce çıkıyordu; yavaş açılan
  1080p VP9 `.webm` sonradan başlarsa ekran sonsuza kadar siyah kalıyordu.
- Motor değiştirmeden önce çözücüyü dürtme (1 ms seek) yoktu.
- "Tekrar Dene" serbest bırakılmış aynı motor örneğini yeniden hazırlıyordu.
- Motoru elle değiştirme yolu yoktu.
- **`PlayerEngine.Listener.onPlayingChanged` uygulanmamıştı.** MPV'de duraklat/oynat geçişi
  yalnızca bu yolla bildirilir; `isPlayingState` donuyor, "oynat"a basınca kod tekrar
  `pause()` çağırıyordu → video hiç başlamıyormuş gibi görünüyordu.

## Yapılan değişiklikler

1. `KitsugiPlayerGestureWrapper` ana oynatıcı referans alınarak yeniden yazıldı: tek merkez
   durumu (`InlineCenterOverlay.Buffering/PlayPause/Hidden`, `AnimatedContent` 160 ms),
   kısayol balonu tıklanan tarafta (`CenterStart`/`CenterEnd`) 800 ms, bölgeler sol %40 geri /
   sağ %40 ileri / orta oynat-duraklat, ayar tabanlı `doubleTapSeekSeconds` ve
   `controlsAutoHideMs`, alt bar ana oynatıcı düzeninde, tek jest döngülü ilerleme çubuğu
   (sürüklerken canlı hedef süre, `--:--` desteği, dar genişlikte çökme yok).
2. `ThemePlayerContainer` ve `KitsugiYouTubePlayer`: `onPlayingChanged` uygulandı;
   `engineAttempt` ile her denemede taze motor; nudge; hata kartında "Diğer motorla dene";
   motor değişiminde `hasStartedPlayback`/`isPlayingState` sıfırlama; kimliksiz YouTube
   sayfaları için "Tarayıcıda Aç" kartı.
3. `InlinePlayerSupport.kt`: `INLINE_AUDIO_ONLY_GRACE_MS` 6 sn → 3,5 sn,
   `INLINE_NUDGE_GRACE_MS` (2,5 sn), `INLINE_VIDEO_FRAME_MAX_WAIT_MS` (45 sn), saf karar
   fonksiyonu `inlineVideoVerdict()` (HEALTHY/WAITING/NUDGE_RENDERER/SWITCH_ENGINE),
   `waitForInlineVideoTrack` yeniden yazıldı (duraklatmada yanlış hüküm vermez).
4. `extractYouTubeId` yeniden yazıldı (sorgu sırasından bağımsız `v`, `shorts`, `live`,
   `embed`, `v`, `e`, `watch`, `youtu.be`, `m.`/`music.`/`youtube-nocookie`, protokol-göreli);
   `isYouTubePageUrl` eklendi/genişletildi.
5. `values/strings.xml` + `values-en/strings.xml`: `inline_try_other_engine`.

## Dokunulan dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailPlayerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupport.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/InlinePlayerSupportTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/YouTubeUrlParsingTest.kt` (yeni)

## Testler

- `YouTubeUrlParsingTest` (yeni, 7 test): URL biçimleri, protokol-göreli adresler,
  kimliksiz YouTube sayfaları, YouTube olmayan adresler.
- `InlinePlayerSupportTest` (+3 test): nudge/switch kararı, grace beklemesi, üst sınır.
- Toplam 20 test.

## Doğrulama / kalan iş

- `git diff --check`: temiz. Parantez dengesi: temiz. `strings.xml` (TR+EN): geçerli XML.
- Kullanılan metin anahtarları iki dilde de mevcut; kullanılmayan import yok.
- `./gradlew :app:testDebugUnitTest --no-daemon`: **çalıştırılamadı** — ortamda `java` yok,
  `local.properties` içinde `sdk.dir` yok. CI/cihazda koşulmalı.
- Cihaz kontrol listesi: `COZUM_RAPORU_MINI_OYNATICI_ARAYUZ_VE_OYNATMA_2026-10-22.md` §5.

## Bilinen, ertelenen konular

- `pickBestThemeVideoLink()` hâlâ bir YouTube **arama** adresini seçebiliyor; artık
  "Tarayıcıda Aç" kartına düştüğü için sessiz hata değil, ama kaynak seçimi iyileştirilmeli.
- `isHlsUrl()` zayıf token eşleşmesi (`.m3u8` dışındaki adreslerde yanlış pozitif riski).
