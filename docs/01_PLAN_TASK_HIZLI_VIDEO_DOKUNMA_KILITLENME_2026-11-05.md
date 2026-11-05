# Plan / Task — Videolar Sekmesinde Hızlı Dokununca Oynatıcının Kilitlenmesi

**Tarih:** 2026-11-05
**Dal:** `arena/e2b919d7-kitsugi-beta`
**Sürüm:** v2.4.341
**Kapsam:** Detay sayfası → **Videolar** sekmesi → Açılış/Kapanış müzikleri (AnimeThemes) satır içi oynatıcısı.

## Kullanıcı belirtisi

"Videolara hızlı hızlı arka arkaya basınca videolar kilitleniyor gibi oluyor ve diğer videolara
basınca oynamıyor, hata veriyor." Kullanıcı sorunun AnimeThemes API'sinden kaynaklandığını
düşünüyordu (ekran görüntüsü: "Kapanış Müzikleri" listesinde 4 satıra art arda dokunulmuş).

## Teşhis özeti

Sorun API'de değil, **oynatıcı yaşam döngüsünde**:

- Motor `remember { }` içinde, yani **composition sırasında** kuruluyordu.
- Eski satırın `onDispose` bloğu ise aynı karenin **apply** fazında çalışır.
- Sonuç: yeni `ExoPlayer` (MediaCodec video çözücüsü + AudioTrack + TextureView + OkHttp
  istemcisi) **eski motor hâlâ ayaktayken** kuruluyordu.
- Hızlı dokunmalarda yığınlar üst üste bindi → MediaCodec örneği havuzu tükendi
  (`decoder init failed`), ses odağı çakıştı (yeni oynatıcı duraklatılmış kaldı), iki MPV
  örneği tek global mpv çekirdeğini bozdu → "kilitlendi, sonra hiçbiri açılmıyor".

Detaylı kök neden analizi: `COZUM_RAPORU_HIZLI_VIDEO_DOKUNMA_KILITLENME_2026-11-05.md`

## Yapılan değişiklikler

- [x] **Yeni** `InlinePlaybackHandoff.kt`: süreç geneli "tek inline oynatıcı" el değiştirme
      kapısı (`INLINE_PLAYER_HANDOFF_MS = 220 ms`) + dokunma birleştirme sabiti
      (`INLINE_PLAYER_TAP_COALESCE_MS = 320 ms`) + saf `inlineEngineHandoffDelayMs()` fonksiyonu.
- [x] `ThemePlayerContainer`: motor `remember{ }` yerine `LaunchedEffect` içinde, şu sırayla
      kuruluyor → önceki motoru bırak → `awaitEngineSlot()` → `ensureActive()` → motoru kur →
      dinleyiciyi bağla → `prepare()`. Sahiplik `InlineThemeEngineSession`'da (idempotent
      `release`), dinleyici kendi motoruna ait olmayan olayları yok sayıyor.
- [x] `KitsugiMediaVideosTab` + `DetailVideoGroup`: satır vurgusu (`activeVideoKey`) anında,
      oynatıcı anahtarı (`playerVideoKey`) ise dokunmalar 320 ms durulduktan sonra güncelleniyor;
      ilk açılış ve kapatma anında.
- [x] `Media3PlayerEngine.release()`: `PlayerView` oynatıcıdan **önce** ayrılıyor; `release()`
      idempotent; tüm adımlar `runCatching` ile korunuyor.
- [x] `MpvPlayerEngine`: `release()` idempotent; `playRequested` mandalı eklendi (yüzey
      oluşmadan gelen `play()` isteği yüzey hazır olunca uygulanır).
- [x] `app/build.gradle.kts`: sürüm `2.4.340` → `2.4.341`.
- [x] Sürüm notları: `release_notes_v2.4.341.md` + `RELEASE_NOTES.md` başlığı.
- [x] **Yeni** test: `InlinePlaybackHandoffTest` (5 birim test).

## Değişen / eklenen dosyalar

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/InlinePlaybackHandoff.kt` *(yeni)*
2. `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
3. `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
4. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
5. `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/InlinePlaybackHandoffTest.kt` *(yeni)*
6. `app/build.gradle.kts`
7. `release_notes_v2.4.341.md` *(yeni)*
8. `RELEASE_NOTES.md`
9. `COZUM_RAPORU_HIZLI_VIDEO_DOKUNMA_KILITLENME_2026-11-05.md` *(yeni)*
10. `PLAN_TASK_HIZLI_VIDEO_DOKUNMA_KILITLENME_2026-11-05.md` *(bu dosya)*

## Doğrulama durumu

- `git diff --check`: bu paket hazırlanırken kontrol edilecek.
- `./gradlew :app:testDebugUnitTest --tests "*InlinePlaybackHandoffTest*"`: bu ortamda
  Java/JDK ve Android SDK bulunmadığı için çalıştırılamadı.
- Gerçek cihaz testi: yapılmadı (cihaz bu oturumda mevcut değil). Raporda 5. bölümdeki
  adımlar uygulanmalı.

## Kabul kriterleri (cihazda)

1. 4-5 satıra 100-200 ms aralıkla hızlı dokunulduğunda **yalnızca son** video açılır ve oynar.
2. Arayüz donmaz; oynatıcı kapatıldıktan sonra başka satır normal açılır.
3. Logcat'te hızlı dokunma dizisinde `DecoderInitializationException` görünmez.
4. VP9 çözücüsü olmayan cihazda `.webm` teması MPV ile oynar (00:00'da kalmaz).
5. Uygulama arka plana alınıp geri gelindiğinde oynatma kendiliğinden devam etmez.
