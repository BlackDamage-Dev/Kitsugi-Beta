# KÖK NEDEN + ÇÖZÜM RAPORU — Videolar Sekmesinde Hızlı Dokununca Oynatıcının Kilitlenmesi

**Tarih:** 2026-11-05
**Sürüm:** v2.4.341
**Alan:** Detay sayfası → **Videolar** sekmesi → Açılış / Kapanış müzikleri (AnimeThemes) satır içi oynatıcı
**Belirti (kullanıcı):** "Videolara hızlı hızlı arka arkaya basınca videolar kilitleniyor gibi oluyor; sonra diğer videolara basınca oynamıyor, hata veriyor."

---

## 1. Kısa cevap: sorun AnimeThemes API'sinde değil

AnimeThemes yalnızca liste + video bağlantısı sağlıyor (`KitsugiAnimeThemesClient`), oynatma
sırasında yeniden istek atılmıyor ve bu ekranda indirme/çözümleme zinciri yok. Sorun
**oynatıcı yaşam döngüsünde**: her satır dokunuşunda tam bir oynatıcı yığını kurulup
yıkılıyor ve **yığınlar üst üste biniyor**.

---

## 2. Kök neden — teknik olarak tam olarak ne oluyordu?

### 2.1 Motor composition sırasında kuruluyordu

`ThemePlayerContainer` içinde eski kod:

```kotlin
val playerEngine = remember(context, activeEngineType, url) {
    when (activeEngineType) {
        PlayerEngineType.MPV -> MpvPlayerEngine(context, safeSettings)
        else -> Media3PlayerEngine(context, safeSettings)
    }
}
```

- `remember { }` bloğu **composition** sırasında çalışır.
- Eski satırın `DisposableEffect(onDispose)` bloğu ise **aynı karenin apply (uygulama) fazında** çalışır.
- Compose sırası: composition → `remember` blokları → applyChanges (burada `onDispose`) → efektler.

Sonuç: satır değiştiğinde **yeni `ExoPlayer` eski motor hâlâ ayaktayken** kuruluyordu.
Bir `Media3PlayerEngine` yalnızca şunları oluşturmaz:

- `ExoPlayer` + `MediaCodecVideoRenderer` (donanım **video çözücü örneği**)
- `DefaultAudioSink` → `AudioTrack` + `GainAudioProcessor` + `AudioDelayProcessor`
- `PlayerView` + **TextureView** + `SurfaceTexture`
- `PlayerMediaSourceFactory` → `CsVideoInterceptorFactory.buildClient(...)` → **OkHttp istemcisi** (dispatcher + connection pool + thread'ler)
- ASS extractor açıksa `AssSubtitleView` + statik handler kaydı

Yani her dokunuşta tam bir medya motoru kurulup yıkılıyordu. Hızlı dokunmada bu yığınlar
**üst üste biniyor**.

### 2.2 Bu neden "kilitlenme + sonrakiler hiç açılmıyor" olarak görünüyor?

| Katman | Üst üste binince olan | Kullanıcıya etkisi |
|---|---|---|
| **MediaCodec** | Platformun eşzamanlı çözücü örneği sınırı (çoğu cihazda 2-4) aşılır; eski çözücü `release()` tamamlanmadan yenisi `configure()` edilir → `MediaCodecRenderer$DecoderInitializationException` ("decoder init failed") | Yeni video **hiç açılmaz**; `onPlaybackError` → yedek motor → MPV |
| **Audio focus** | Her yeni oynatıcı `handleAudioFocus = true` ile odak ister; eski oynatıcı bırakırken odak kaybı (`AUDIOFOCUS_LOSS`) yeni oynatıcıya ulaşır | Oynatıcı **duraklatılmış** kalır, süre 00:00'da donar |
| **libmpv** | mpv-android **tek global mpv çekirdeği** kullanır; iki `KitsugiMpvSurfaceView` aynı anda yaşarsa biri `destroy()` ederken diğeri hâlâ komut gönderir | MPV yedeğine düşen videolarda **kilitlenme**; sonraki videolar da bozuk çekirdeğe düşer |
| **Yüzey sırası** | Eski kod `player.release()` → **sonra** `playerView.player = null` sırasıyla çalışıyordu; serbest bırakılmış oynatıcıya yüzey temizliği yaptırılıyordu | `TextureView`/surface sızıntısı, bir sonraki videonun **siyah** açılması |
| **Ana iş parçacığı** | Bir `ExoPlayer.release()` + `build()` çifti main thread'de ~100-400 ms | Dokunmalar birikir, arayüz "kilitlenmiş" gibi donar |

Zincir şu şekilde kapanıyordu:

```
hızlı dokunma → 3-4 motor üst üste → çözücü havuzu tükenir → decoder init failed
             → yedek motor (MPV) → MPV çekirdeği çakışır → her motor "başarısız"
             → isPlayerError = true → "Bu video uygulamada oynatılamıyor" ekranı
```

Bu yüzden hata **kalıcı** hissi veriyordu: kullanıcı başka bir satıra dokunsa bile aynı
tıkanmış kaynaklara çarpıyordu.

### 2.3 Neden sadece "hızlı" dokununca oluyordu?

Tek dokunuşta motorlar sırayla (biri bittikten sonra diğeri) kurulup yıkandığı için
çakışma penceresi (~1 kare, 16-50 ms) gözden kaçıyordu. Dokunmalar 200-400 ms aralıkla
gelince, eski motorun yıkımı (asenkron çözücü/surface bırakma) hâlâ sürerken yeni motor
kuruluyor ve **çakışma penceresi sürekli açık kalıyordu**.

---

## 3. Uygulanan çözüm (v2.4.341)

### 3.1 Yeni: `InlinePlaybackHandoff.kt` — tek oynatıcı el değiştirme kapısı

- Bir inline motor bırakıldığında zaman damgası yazılır.
- Yeni motor kurulmadan önce **`INLINE_PLAYER_HANDOFF_MS = 220 ms`** beklenir → çözücü,
  ses yolu ve yüzey kaynakları serbest kalır.
- Kilit/semafor değil **zaman damgası** kullanılır: iptal edilen bir efekt kapıyı asla
  kilitleyemez (donma riski yok), en kötü durumda oynatıcı birkaç yüz ms geç açılır.
- `inlineEngineHandoffDelayMs(lastReleaseAtMs, nowMs)` saf fonksiyon olarak ayrıldı ve
  birim testlerle doğrulandı.

### 3.2 `ThemePlayerContainer` — motor artık composition'da kurulmuyor

- `remember { }` içindeki motor oluşturma kaldırıldı.
- Motor `LaunchedEffect(url, retryTrigger, activeEngineType)` içinde, şu sırayla kurulur:
  1. `session.release()` → önceki motoru bırak (varsa),
  2. `InlinePlaybackHandoff.awaitEngineSlot()` → el değiştirme aralığı,
  3. `currentCoroutineContext().ensureActive()` → iptal edilmiş efekt motor kurup sızdırmasın,
  4. motoru kur + dinleyiciyi bağla + `prepare()`.
- Motor sahipliği tek bir yerde: `InlineThemeEngineSession` (`start` / `release`).
  `release()` idempotent; dinleyici → duraklat → serbest bırak sırası garanti.
- Dinleyici, **kendi motoruna ait olmayan** olayları yok sayar (bayat olay koruması).

### 3.3 `KitsugiMediaVideosTab` — dokunma birleştirme (tap coalescing)

- Satır vurgusu (`activeVideoKey`) **anında** güncellenir → arayüz tepkisel kalır.
- Oynatıcıyı taşıyan anahtar (`playerVideoKey`) ise:
  - kapatmada **anında** `null`,
  - ilk açılışta **anında**,
  - **satır değiştirmede** `INLINE_PLAYER_TAP_COALESCE_MS = 320 ms` bekler.
- Kullanıcı 4-5 satıra hızlıca bassa bile **tek** oynatıcı kurulur.

### 3.4 `Media3PlayerEngine.release()` — yüzey sırası + çift bırakma koruması

```kotlin
if (released) return                      // idempotent
runCatching { playerView?.player = null } // ÖNCE yüzeyi bırak
... player.stop() / clearMediaItems() / release()
```

### 3.5 `MpvPlayerEngine` — `playRequested` mandalı + idempotent `release()`

Motor artık yüzeyden (AndroidView fabrikası) **önce** kurulabildiği için `prepare()`/`play()`
çağrıldığında `KitsugiMpvSurfaceView` henüz oluşmamış olabilir. Eski `play()` bu durumda
sessizce hiçbir şey yapmıyordu; video yüzey oluşunca yükleniyor ama **oynamıyordu**.
`playRequested` mandalı oynatma isteğini hatırlar ve yüzey oluştuğunda uygular.
Ayrıca `release()` idempotent yapıldı: serbest bırakılmış mpv örneğine ikinci kez JNI
çağrısı yapılması (SIGSEGV riski) engellendi.

---

## 4. Dosya değişiklikleri

| Dosya | Değişiklik |
|---|---|
| `ui/screens/detail/InlinePlaybackHandoff.kt` | **YENİ** — el değiştirme kapısı + sabitler + saf gecikme fonksiyonu |
| `ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt` | `ThemePlayerContainer` yeniden yazıldı (lazy motor + oturum), `KitsugiMediaVideosTab`/`DetailVideoGroup` dokunma birleştirme |
| `core/player/engine/Media3PlayerEngine.kt` | `release()` sırası + idempotent |
| `core/player/engine/MpvPlayerEngine.kt` | `release()` idempotent |
| `app/build.gradle.kts` | `2.4.340` → `2.4.341` |
| `app/src/test/.../InlinePlaybackHandoffTest.kt` | **YENİ** — 5 birim test |

---

## 5. Doğrulama adımları (gerçek cihaz)

1. Detay → **Videolar** sekmesi → "Daha Fazla" ile kapanış listesini aç.
2. Aynı anda 4-5 farklı satıra **hızlı hızlı** dokun (aralarında ~100-200 ms).
   - Beklenen: arayüz donmaz, yalnızca **son** dokunulan video açılır ve oynar.
3. Oynayan videoyu kapat, başka bir satıra dokun → video normal açılmalı.
4. Aynı testi **MPV yedeğine düşen** bir `.webm` temasıyla ve VP9 çözücüsü olmayan bir
   cihazda tekrarla → "Bu video uygulamada oynatılamıyor" ekranı **hızlı dokunma yüzünden**
   çıkmamalı.
5. Logcat kontrolü (hızlı dokunma dizisi sırasında):
   - `DecoderInitializationException` **olmamalı**
   - `ThemePlayerContainer: engine=... visibleVideo=true` satırları görünmeli
   - `Both inline engines failed` yalnızca gerçekten desteklenmeyen dosyada çıkmalı
6. Uygulamayı arka plana al/geri getir → oynatma kendiliğinden devam etmemeli (mevcut
   davranış korunur).

---

## 6. Birim testler

`app/src/test/java/com/kitsugi/animelist/ui/screens/detail/InlinePlaybackHandoffTest.kt`

- Hiç motor kapatılmadıysa bekleme yok.
- Motor yeni kapatıldıysa tam el değiştirme penceresi kadar beklenir.
- Pencerenin kalan kısmı beklenir.
- Eski kapanışlar yeni oynatıcıyı geciktirmez.
- Birleştirme/handoff süreleri "başlamadı" bekçisinden kısa olmalı.

Çalıştırma: `./gradlew :app:testDebugUnitTest --tests "*InlinePlaybackHandoffTest*"`

---

## 7. Notlar / sınırlar

- Handoff süresi süreç genelindedir; tam ekran oynatıcı (`KitsugiFullscreenPlayerActivity`)
  ayrı bir yaşam döngüsüne sahiptir ve bu kapıdan etkilenmez. Tam ekrana geçerken inline
  oynatıcı duraklatılıp bırakılır; iki oynatıcı bir an birlikte yaşasa da bu geçiş
  kullanıcı kontrollüdür (tek seferlik) ve hızlı dokunma seli oluşturmaz.
- `KitsugiThemesList` / `ThemeVideoSection` (eski, artık çağrılmayan yol) aynı
  `ThemePlayerContainer`'ı kullandığı için el değiştirme korumasından oto­matik olarak
  yararlanır.
- Gelecekte farklı bir sekme de satır içi video açarsa: **motoru `remember` içinde
  kurmayın**, `InlinePlaybackHandoff.awaitEngineSlot()` sonrasında kurun.
