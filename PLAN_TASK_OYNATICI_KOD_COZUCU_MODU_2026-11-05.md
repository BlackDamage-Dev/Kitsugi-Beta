# PLAN_TASK: Oynatıcı "Donanım Kod Çözücü Modu" — Tek Auto + SW/HW/HW+ Çift Motor Düzeltmesi (2026-11-05)

## İstek
1. Oynatıcı "Daha Fazla" panelindeki birden fazla "Auto" seçeneğini tek "Auto"ya düşürmek.
2. SW, HW ve HW+ modlarının **iki oynatıcı motorunda da (Media3 + MPV)** uygun ve düzgün çalıştığından emin olmak.

## Değişen Dosyalar
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/PlayerEnums.kt`
   - `Decoder` enum'undan `AutoCopy("Auto", "auto-copy")` kaldırıldı.
   - Kalan seçenekler: `Auto("auto")`, `SW("no")`, `HW("mediacodec-copy")`, `HWPlus("mediacodec")`.
   - Eski kayıtlı değerler ("auto-copy", "auto-safe") `getDecoderFromValue` ile `Auto`'ya düşüyor.

2. `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
   - Yeni `decoderMode` alanı + `normalizeDecoderMode()` (eski değerleri "auto"ya çeker).
   - `buildDecoderSelector()`: Media3 `MediaCodecSelector` üretir.
     - SW (`no`): yalnızca yazılım çözücüler (`OMX.google.*` / `c2.android.*`).
     - HW/HW+: donanım çözücüler öncelikli.
     - Hiçbiri yoksa varsayılana geri düşer → siyah ekran riski yok.
   - `initializePlayer()` içinde `setMediaCodecSelector(...)` uygulandı.
   - `applyDecoderMode(mode)`: mod değişince ExoPlayer'ı **kaldığı konumdan** yeniden kurar (prepare argümanları `lastAudioUrl`/`lastHeaders`/`lastQualityValue` olarak saklanıyor).

3. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
   - `updateDecoder()` artık aktif motora göre dallanıyor:
     - MPV → `hwdec` özelliği anında set edilir.
     - Media3 → `applyDecoderMode()` ile konum korunarak yeniden kurulum.
   - Seçim her iki durumda da `setMpvHwdecMode` ile kalıcı hale getiriliyor.

## Not
MPV tarafı zaten geçerli hwdec değerleri kullanıyordu (`auto`, `no`, `mediacodec-copy`, `mediacodec`); bu değişiklik esas olarak Media3 motoruna SW/HW/HW+ desteği ekliyor.
