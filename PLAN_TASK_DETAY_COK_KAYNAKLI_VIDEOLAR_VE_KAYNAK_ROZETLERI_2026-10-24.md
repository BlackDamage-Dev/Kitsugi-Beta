# Plan Task — Detay Sayfaları Çok Kaynaklı Ortak Videolar Sekmesi ve Kaynak Rozetleri

**Tarih:** 2026-10-24  
**Durum:** Tamamlandı

## İstenenler

1. **Ortak Çok Kaynaklı Video Verisi (`Resimler` sekmesi mantığıyla):**
   - Detay sayfası hangi kaynaktan açılırsa açılsın (AniList, MyAnimeList, TMDB, Shikimori, Kitsu, Simkl, Bangumi, Fandom veya Listem kayıtları), **Videolar** sekmesindeki veriler yalnızca açılan kaynağın kendi verisiyle sınırlı kalmamalı; çapraz kimlik/başlık eşleştirmesiyle video verisi sunan **tüm kaynakların** videoları (fragman/ön izleme, açılış müzikleri, kapanış müzikleri, PV/klip/ek videolar) bir araya getirilip gösterilmelidir.
2. **Kaynak Adı ve Logo Rozeti (`[Logo + Kaynak Adı]`):**
   - Video verileri tüm kaynaklar arasında ortaklaştığı için, `Ön izleme`, `Açılış Müzikleri`, `Kapanış Müzikleri` ve `Klipler ve Ek Videolar` kartlarının sağ üst başlık alanında (ve birden çok kaynak bulunduğunda ilgili video satırlarında) videonun geldiği kaynağın **logosu ve ismi** birlikte gösterilmelidir.

## Mimari ve Uygulama

### 1. Ortak Video Veri Modeli ve Tekilleştirme (`MediaVideoItem.kt`)
- `MediaVideoCategory` (`TRAILER`, `OPENING`, `ENDING`, `EXTRA`) ve `MediaVideoItem` (`key`, `title`, `videoUrl`, `category`, `source`, `sources`) tanımlandı.
- `extractCanonicalYouTubeId`: `watch?v=`, `youtu.be/`, `/embed/`, `youtube-nocookie.com/embed/`, `/shorts/` ve ham 11 karakterlik YouTube kimliklerini Android bağımlılığı olmadan saf Kotlin ile ayrıştırır.
- `mergeAndOrderMediaVideos`:
  - Farklı kaynaklardan aynı YouTube ID veya doğrudan akış URL'si ile gelen videoları tek kayıtta birleştirir; katkıda bulunan tüm kaynakları `sources` listesinde korur.
  - Genel yer tutucu başlıklar (*"Fragmanı İzle"*, *"Trailer"*) yerine açıklayıcı başlığı (*"PV 1 - Official Trailer"* vb.) seçer.
  - Doğrudan oynatılabilir `AnimeThemes` OP/ED akışı mevcutken aynı şarkının salt-metin (`videoUrl == null`) kopyalarını eler.

### 2. Kaynak Bazlı Video İstemcileri ve Önbellek Güncellemeleri
- **AnimeThemes (`KitsugiAnimeThemesClient.kt`):** Sonuçlara `source = "animethemes"` etiketi ve `BoundedCache("animethemes.bySiteId", 200)` eklendi.
- **MyAnimeList / Jikan (`KitsugiMalDetailClient.kt`):** `fetchMalVideos(malId)` eklendi; `/v4/anime/{malId}/videos` uç noktasından `promo` (fragman/PV) ve `music_videos` (OP/ED/ek müzik videoları) hız sınırlayıcı (`KitsugiApiBase.runWithRateLimit`) üzerinden çekilip önbelleğe alınır.
- **TMDB (`TmdbMediaDetailClient.kt`, `TmdbApiClient.kt`):** `fetchStructuredVideos` ve `fetchMediaVideoItems` eklendi; aktif uygulama dili (`tr-TR`) + `en-US` + sezon videoları birleştirilip YouTube anahtarına göre tekilleştirilir. Çapraz çözülen TMDB kimliklerinde `TmdbArtworkIdentity.matches` başlık doğrulaması uygulanır ve `tmdbUseTrailers` ayarına uyulur.
- **Shikimori (`KitsugiShikimoriClient.kt`):** `fetchShikimoriVideos` yerelleştirilmiş etiketlerle güncellendi (`url` + `player_url` desteği, `source = "shikimori"`, `BoundedCache`); birincil detayda PV/CM kayıtlarının `openings` listesine karışması önlendi.
- **Simkl (`KitsugiSimklDetailClient.kt`):** `extended=full` yanıtındaki `trailers` dizisi ayrıştırıldı (`extractSimklTrailerVideos`, `fetchSimklVideos`), `fetchSimklDetailDirect` içinde `trailerUrl` dolduruldu ve harici kimliklerden (`mal`, `anilist`, `imdb`, `tmdb`) Simkl ID çözümleyen `resolveSimklIdByExternalIds` eklendi.
- **Önbellek (`DetailCache.kt`) ve Modeller (`KitsugiModels.kt`):** `DetailCache` içine sezon duyarlı `mediaVideos` önbelleği, `KitsugiTheme` içine `source` ve `KitsugiMediaDetail` içine `trailerSource` alanları eklendi.

### 3. Çok Kaynaklı Paralel Video Toplayıcı (`KitsugiMediaVideoAggregator.kt`)
- `seedFromDetail`: Birincil detay yanıtından sıfır gecikmeli başlangıç listesi üretir.
- `aggregateVideos`: Kaynağa özgü ad alanlarını (`AniList 100M`, `Kitsu 300M`, `Bangumi 500M`, `Shikimori`, `Simkl`, `TMDB`, `MAL`, `Fandom` başlık eşleştirmesi) güvenli biçimde çözer; `ARM`, `KitsugiIdResolver`, `Simkl`, `Bangumi` ve `Wikidata` üzerinden çapraz kimlikleri tamamlar ve **7 video kaynağını** (`anilist`, `mal`, `tmdb`, `shikimori`, `kitsu`, `simkl`, `animethemes`) `supervisorScope` altında paralel sorgular. Her kaynak tamamlandıkça ara sonuçlar anında UI akışına yansıtılır.

### 4. Kaynak Rozetleri, AnimeThemes Logosu ve Ekran Bağlantıları
- **AnimeThemes Marka Rozeti:** `ic_logo_animethemes.xml` vektör logosu oluşturuldu; `KitsugiPlatformLogos.resFor`, `String.toFriendlySourceLabel()` ve `DetailPlatformBadge` içine `"animethemes"` / `"animethemes.moe"` desteği eklendi.
- **Videolar Sekmesi UI (`KitsugiDetailThemesTrailerComponents.kt`):**
  - `DetailVideoGroup` kart başlıklarının sağ üst köşesine `[Logo + Kaynak Adı]` rozetleri (`DetailVideoSourceBadge`) yerleştirildi.
  - Grupta birden fazla kaynaktan video varsa sağ üstteki kaynak rozetlerine dokunarak kaynağa göre filtreleme yapılabilir; ayrıca satır altlarında her videonun kaynak rozetleri gösterilir.
  - `+X daha / Daha az` genişletme düğmesi kart altına taşındı; arka plan yükleme göstergeleri (`ui_videos_loading`) eklendi.
- **ViewModel, Sayfa ve TV Bağlantıları:**
  - `ApiResultDetailViewModel` ve `MediaEntryDetailViewModel` içine `videoItems` ve `videosLoading` (`GalleryLoadTracker`) eklendi; birincil detayda eksik olan fragman/OP/ED alanları çapraz kaynak verisiyle zenginleştirildi.
  - `ApiResultDetailPage`, `MediaEntryDetailPage` ve Android TV `TvDetailScreen` (`TvThemeCard`) bileşenleri bağlandı.
- **Yerelleştirme:** Yeni metin anahtarları `values/strings.xml` ve `values-en/strings.xml` dosyalarına simetrik olarak eklendi.

## Eklenen ve Değişen Dosyalar

### Yeni Eklenen Dosyalar (5)
- `PLAN_TASK_DETAY_COK_KAYNAKLI_VIDEOLAR_VE_KAYNAK_ROZETLERI_2026-10-24.md`
- `app/src/main/java/com/kitsugi/animelist/data/remote/MediaVideoItem.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaVideoAggregator.kt`
- `app/src/main/res/drawable/ic_logo_animethemes.xml`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MediaVideoItemTest.kt`

### Güncellenen Dosyalar (19)
- `app/src/main/java/com/kitsugi/animelist/data/remote/DetailCache.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiAnimeThemesClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMalDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiModels.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiSimklDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/TmdbMediaDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlatformLogo.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailSharedComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailThemesTrailerComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/detail/TvDetailScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/KitsugiTranslations.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Doğrulama

- `python3 scripts/check_localization_resource_parity.py`: Başarılı (`17,703` string, TR/EN tam simetri).
- `python3 diag/bangumi_master/verify_bangumi_master.py`: Başarılı (tüm sözlük ve dil kontrolleri geçti).
- `git diff --check`: Başarılı (boşluk/biçim hatası yok).
- Statik kaynak ve sözdizimi denetimi (`R.string.*`, `R.drawable.*`, XML geçerliliği ve ayraç dengesi): 20 Kotlin + 3 XML dosyasında doğrulandı.
- `./gradlew :app:compileDebugKotlin`: Sandbox ortamında `java` / JDK kurulu olmadığı için çalıştırılamadı.
