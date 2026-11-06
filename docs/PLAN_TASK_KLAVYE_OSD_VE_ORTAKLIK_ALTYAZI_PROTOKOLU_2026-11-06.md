# PLAN / TASK — Oynatıcı Klavye OSD Davranışı ve Birleşik Stremio Altyazı Ortaklık Protokolü

**Tarih:** 2026-11-06  
**Branch:** `arena/628a4d02-kitsugi-beta`  
**Kapsam:** Tam ekran & TV oynatıcı klavye/odak yönlendirmesi, motor-bağımsız (Media3/MPV) OSD görünürlük sözleşmesi, Birleşik Stremio Altyazı Ortaklık Protokolü (`StremioSubtitleRouting`), AniSub ve altyazı önbellek/arşiv hattı

---

## 1. Kullanıcı Gereksinimleri

1. **Oynatıcı arayüzü (OSD) açıkken klavye kısayollarının pürüzsüz çalışması:** Oynatıcı kontrolleri görünür durumdayken bile klavye kısayolları odak kaybı veya buton çakışması olmadan kesintisiz çalışmalı.
2. **Motor-bağımsız sabit arayüz davranışı:** Hangi oynatıcı motoru kullanılırsa kullanılsın (Media3 / ExoPlayer, MPV vb.) ileri/geri sarma, ses, parlaklık ve diğer kısayollar kullanılırken oynatıcı arayüzü sürekli açılıp kapanmamalı; arayüz yalnızca **Durdur / Başlat (Play / Pause)** yapıldığında açılıp kapanmalı.
3. **Altyazı eklentilerinden sıfır altyazı dönmesi & Ortaklık Protokolü denetimi:** Tüm video ve kaynaklarda altyazı eklentilerinden neden sıfır sonuç döndüğü tespit edilmeli, Stremio altyazı ortaklık protokolü (`StremioSubtitleRouting` + `SubtitleIdentityCandidates`) tam olarak bağlanmalı ve uçtan uca düzeltilmeli.

---

## 2. Kök Neden Analizi

### 2.1 Klavye Kısayolları ve OSD Titremesi (Media3 vs MPV)
- **Media3 seek buffering geçişi:** `KitsugiFullscreenPlayerScreen` içindeki `LaunchedEffect(isPlayingState) { if (!isPlayingState) viewModel.showControls() }` bloğu her `isPlaying == false` olduğunda tam ekran OSD'yi açıyordu. MPV seek sırasında `isPlaying == true` tutarken, Media3/ExoPlayer her seek işleminde `STATE_BUFFERING` durumuna geçtiği için `exoPlayer.isPlaying` geçici olarak `false` oluyordu (`playWhenReady == true` kalmasına rağmen). Bu fark her ileri/geri sarmada OSD'nin kendiliğinden açılıp kapanmasına yol açıyordu.
- **Kısayol eylemlerinde zorunlu `showControls()`:** `SeekBackward`, `SeekForward`, `ToggleControlsLock`, `PlayerKeyboardController.opensFullOsd` ve eşlenmemiş tuş yollarında doğrudan `viewModel.showControls()` çağrılıyordu.
- **OSD açıkken tuşların düşmesi ve odak çalınması:** `controlsShown == true` iken `onPreviewKeyEvent` ok tuşlarını ve `Enter`/`DPAD_CENTER` tuşlarını OSD butonlarına bırakıyordu; ayrıca `Space`/`Enter` `ACTION_UP` olayı odaklanmış bir OSD butonuna (ör. Geri tuşu) gidip oynatıcıdan çıkabiliyordu. `TvPlayerScreen` içindeki dış `tvRootFocus` kutusu ve `ExternalInputDetector`'ın yön tuşlu fiziksel QWERTY klavyeleri (`KEYBOARD_TYPE_ALPHABETIC` + `SOURCE_DPAD`) reddetmesi de kısayol yakalamayı bozuyordu.
- **Erken akış / torrent başlangıcında `seekBy` sıfırlaması:** `KitsugiPlayerViewModel.seekBy(offsetMs)` içinde `_duration.value == 0L` iken `(current + offsetMs).coerceIn(0L, 0L)` hedef konumu `00:00`'a sıfırlıyordu.

### 2.2 Altyazı Eklentileri ve Ortaklık Protokolü Sıfır Sonuç Sorunu
1. **`StremioSubtitleRouting` üretim koduna bağlanmamıştı:** `data/remote/StremioSubtitleRouting.kt` ve `SubtitleIdentityCandidates` sınıfları repoda mevcut olmasına rağmen `AddonStreamClient` ve `SubtitleRepositoryImpl` tarafından hiç çağrılmıyordu.
2. **Uydurma dosya adı + zorunlu 4-segment URL (`404` hatası):** `KitsugiPlayerViewModel`, gerçek dosya adı olmadığında `"$mediaTitle - S01E01.mkv"` şeklinde yapay bir ad üretip `StreamSource`'un gerçek `subtitleFilename` / `subtitleVideoHash` / `subtitleVideoSize` ipuçlarını yok sayıyordu. `AddonStreamClient.fetchSubtitles` her zaman `/subtitles/{type}/{id}/{extra}.json` (4 segment) isteği atıyordu; yalnızca kanonik 3-segment `/subtitles/{type}/{id}.json` bekleyen eklentiler (`AltyaziDB`, `TurkceAltyazi`, `YTS Subtitles` vb.) `404` / boş yanıt döndürüyordu.
3. **Eklentiler ekranı açılmadıysa veritabanında sıfır altyazı eklentisi:** `DEFAULT_SUBTITLE_ADDONS` tohumlaması yalnızca `AddonViewModel` açıldığında çalışıyordu; Eklentiler ekranına girilmemiş kurulumlarda ve Android TV oturumlarında veritabanında hiç altyazı eklentisi bulunmuyordu.
4. **8 paralel ham başlık sorgusu + 6 sn timeout + Circuit Breaker + AniSub önbellek zehirlenmesi:** Her videoda 8 paralel sorgu (6 tanesi geçersiz ham başlık) atılıyor, `6000 ms` zaman aşımıyla `circuitBreakers` sayacı tek videoda dolup eklentileri 5 dakika devre dışı bırakıyordu. `AniSubSubtitleClient` ise boş sonucu 10 dakika önbellekte tutuyor ve `CancellationException` yutuyordu.
5. **Yerel torrent P2P ve HLS akışlarda bloklayan OpenSubtitles hash hesabı, `.gz`/`.zip` arşivleri ve `PREFERRED_ONLY` filtresi:** `OpenSubtitlesHasher` `127.0.0.1` P2P vekilinde (son 64 KB parça inmediği için) tüm eklentileri bekletiyor; `SubtitleFileCache` `.gz`/`.zip` arşivli altyazıları açmıyor; `PREFERRED_ONLY` ise Türkçe altyazı dönmediğinde diğer tüm dilleri eleyip sıfır sonuca düşüyordu.

---

## 3. Tamamlanan İşler

### 3.1 Oynatıcı Klavye ve Motor-Bağımsız OSD Düzeltmeleri
- `PlayerEngine.kt`, `Media3PlayerEngine.kt`, `MpvPlayerEngine.kt`: Motor-bağımsız `val isPausedByUser: Boolean` eklendi (`Media3`: `!playWhenReady && state !in [IDLE, ENDED]`; `MPV`: `fileLoaded && !isPlaying`).
- `KitsugiFullscreenPlayerScreen.kt`:
  - Otomatik OSD açma etkisi `isPlayingState` yerine `isUserPausedState` durumuna bağlandı; kare adımlama (`,` / `.`) için geçici bastırma eklendi.
  - Yalnızca **Durdur / Başlat** (`TogglePlayPause`, `Play`, `Pause`) eylemleri duraklatmada `showControls()`, oynatmada `hideControls()` çağırır; diğer tüm klavye kısayolları OSD durumunu değiştirmez.
  - Activity `dispatchKeyEvent` köprüsü + `ACTION_DOWN` / `ACTION_UP` tekilleştirmesi ile OSD açıkken bile tüm fiziksel klavye kısayolları kesintisiz çalışır; açık alt paneller/pencereler aynı tuşla veya `Esc` ile kapanır; gerçek Android TV kumandasında D-pad gezinmesi korunur.
- `ExternalInputDetector.kt`: `KEYBOARD_TYPE_ALPHABETIC` fiziksel klavyelerin `SOURCE_DPAD` bayrağı taşısalar bile klavye olarak sınıflandırılması sağlandı.
- `KitsugiFullscreenPlayerActivity.kt`, `TvPlayerActivity.kt`, `TvPlayerScreen.kt`: Activity tuş köprüsü eklendi, odak çalan dış `tvRootFocus` kaldırıldı ve Binder `TransactionTooLargeException` yedek yolunda tüm harici kimliklerin (`malId`, `aniListId`, `tmdbId`, `bangumiId`, `imdbId`, `knownImdbId`, `mediaType`, başlıklar, `startYear`) korunması sağlandı.
- `KitsugiPlayerViewModel.kt`: `togglePlay()` `isPausedByUser` ile hizalandı; `seekBy(offsetMs)` süre henüz `0L` iken konumu `00:00`'a sıfırlamayacak şekilde düzeltildi.

### 3.2 Birleşik Stremio Altyazı Ortaklık Protokolü ve Eklenti Hattı
- `AddonStreamClient.kt`:
  - `fetchManifest` içinde `StremioSubtitleRouting.parseSubtitleResource` bağlanarak eklentinin `subtitles.types` ve `idPrefixes` yetenekleri doğru ayrıştırıldı ve önbelleğe alındı.
  - Gerçek `videoHash` / `videoSize` / sürüm `filename` ipucu varsa önce 4-parçalı `/subtitles/{type}/{id}/{extra}.json`, hata veya boş sonuçta otomatik olarak kanonik 3-parçalı `/subtitles/{type}/{id}.json` çağrılır (manifest query parametreleri korunur).
  - Hem `{"subtitles": [...]}` hem dizi `[...]` yanıtları, göreli indirme URL'leri ve alternatif dil alanları (`lang`, `language`, `SubLanguageID`, `iso639`, `langCode`) desteklendi.
- `SubtitleRepositoryImpl.kt`:
  - `UnifiedSubtitleQuery` + `fetchUnifiedSubtitles` eklendi; `StremioSubtitleRouting.buildTasks` ve `SubtitleIdentityCandidates.build` ile her eklenti yalnızca desteklediği tip ve ID önekleriyle (`tt`, `tmdb:`, `kitsu:` ve beyan edilen özel önekler) sorgulanır.
  - Veritabanında hiç altyazı eklentisi yoksa `AddonPresets.DEFAULT_SUBTITLE_ADDONS` anında otomatik tohumlanır ve manifestleri arka planda tazelenir.
  - Zaman aşımı `15 sn` (`AniSub` için `20 sn`) olarak dengelendi; circuit breaker yalnızca gerçek zaman aşımı/ağ hatalarını video bazında sayacak şekilde düzeltildi.
- `AniSubSubtitleClient.kt`: Çoklu başlık adaylarıyla (Romaji, İngilizce, yerel başlık) tek birleşik sorgu desteği eklendi; boş sonuçların 10 dk önbellekte tutulması engellendi; `CancellationException` yutulması giderildi.
- `OpenSubtitlesHasher.kt`: `IPv4FirstDns`, paylaşımlı `OkHttpClient` ve `canProbeUrl` (`.m3u8`/`.mpd` ve `127.0.0.1`/`localhost` P2P vekillerini anında atlama) eklendi.
- `SubtitleFileCache.kt`: `IPv4FirstDns`, paylaşımlı `OkHttpClient`, otomatik `.gz` ve `.zip` arşivli altyazı açma ve çakışmasız atomik dosya yazımı eklendi.
- `KitsugiPlayerViewModel.kt`, `KitsugiStreamScreen.kt`, `KitsugiAddonDetailDialog.kt`: Uydurma `S01E01.mkv` dosya adı üretimi kaldırıldı, gerçek `StreamSource` ipuçları bağlandı, `PREFERRED_ONLY` boş kaldığında yumuşak geri dönüş eklendi, eklenti çeşitliliğini koruyan seçim ve `Semaphore(4)` sınırlı indirme devreye alındı, `mediaType` ve `startYear` geçişleri tamamlandı.

---

## 4. Değişen ve Eklenen Dosyalar (20 Dosya)

1. `docs/PLAN_TASK_KLAVYE_OSD_VE_ORTAKLIK_ALTYAZI_PROTOKOLU_2026-11-06.md` *(yeni)*
2. `app/src/main/java/com/kitsugi/animelist/core/player/OpenSubtitlesHasher.kt`
3. `app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt`
4. `app/src/main/java/com/kitsugi/animelist/core/player/engine/Media3PlayerEngine.kt`
5. `app/src/main/java/com/kitsugi/animelist/core/player/engine/MpvPlayerEngine.kt`
6. `app/src/main/java/com/kitsugi/animelist/core/player/engine/PlayerEngine.kt`
7. `app/src/main/java/com/kitsugi/animelist/core/player/input/ExternalInputDetector.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/remote/AddonStreamClient.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/remote/anisub/AniSubSubtitleClient.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/repository/SubtitleRepositoryImpl.kt`
11. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerActivity.kt`
12. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt`
13. `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt`
14. `app/src/main/java/com/kitsugi/animelist/ui/screens/search/components/KitsugiAddonDetailDialog.kt`
15. `app/src/main/java/com/kitsugi/animelist/ui/screens/stream/KitsugiStreamScreen.kt`
16. `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerActivity.kt`
17. `app/src/main/java/com/kitsugi/animelist/ui/tv/player/TvPlayerScreen.kt`
18. `app/src/test/java/com/kitsugi/animelist/core/player/OpenSubtitlesHasherRangeTest.kt`
19. `app/src/test/java/com/kitsugi/animelist/data/remote/AddonStreamClientSubtitleProtocolTest.kt` *(yeni)*
20. `app/src/test/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerKeyboardShortcutsTest.kt`

---

## 5. Mimari Kısıt ve Doğrulama Notları

- **+0 XML ID Kuralı:** `app/src/main/res/` altında hiçbir yeni XML string ID eklenmemiştir (`+0 ID`); `app/src/main/res/raw/bangumi_vault_keep.xml` korunmuştur.
- `git diff --check` ve tüm değişen/eklenen Kotlin dosyalarında parantez/blok dengesi ile `R.string.*` referans doğrulamaları temiz geçmiştir.
