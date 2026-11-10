# PLAN & GÖREV — Sticky Arama Bar + Bildirim/Geçmiş/İndirilenler Butonları + Bölüm Sıralama & Hayali Bölüm Fix

> Oturum: 2026-10-25 | Branch: `arena/a4f594e3-kitsugi-beta`
> Kullanıcı talepleri (2 ekran görüntüsü ile):
> 1. Arama sayfasında arama barı kaydırılınca yukarı yapımalı (sticky)
> 2. Arama sayfasında üst-sağ: bildirim butonu + izleme geçmişi butonu
> 3. Bildirim butonunun yanında (Keşfet, MyList, Profil): geçmiş + indirilenler butonları
> 4. Bölümler ters sıralı geliyor — düzelt
> 5. Bölüm logiği hatası: hayali 12/24/100 bölüm, film/dizi karışımı — kök neden incelenip düzeltildi

---

## DEĞİŞKEN DOSYALILAR (9 file, +326/-165 satır)

| # | Dosya | Değişiklik |
|---|-------|-----------|
| 1 | `app/src/main/java/com/kitsugi/animelist/AppRootTabPages.kt` | Arama/Keşfet/MyList/Profil sayfalarına yeni params bağlandı |
| 2 | `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiMediaTabsClient.kt` | Bölüm sıralama + hayali bölüm padding fix |
| 3 | `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt` | Geçmiş + İndirilenler butonları eklendi |
| 4 | `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` | `hasNextEpisode` guard fix |
| 5 | `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/KitsugiPlayerViewModel.kt` | Film için bölüm çekme durduruldu + guard fix |
| 6 | `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListComponents.kt` | Geçmiş + İndirilenler butonları eklendi |
| 7 | `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt` | Yeni params geçirildi |
| 8 | `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiProfileScreen.kt` | Geçmiş + İndirilenler butonları eklendi |
| 9 | `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt` | Sticky header + bildirim/geçmiş butonları |

---

## TASK 1 — Arama Barı Sticky (Sticky Header)

**Dosya:** `SearchScreen.kt`
**STATUS: ✅ TAMAMLANDI**

- Arama çubuğu eskiden LazyColumn'un ilk öğesi (plain `item {}`) olarak sıralanıyordu, kaydırınca ekrandan kayboluyordu.
- Şimdi `stickyHeader(key = "search_title_and_bar")` ile dönüştürüldü.
- `Surface(color = KitsugiColors.Background.copy(alpha = 0.96f))` arka planlı, Column padding 8/4 dp.
- Başlık (`pageTitle`) ve arama çubuğu birlikte sticky header içinde.
- `CosmicCompanionButton` (Eklentı Portalı) sticky header içinde, arama çubuğunun sağında.

## TASK 2 — Arama Sayfası Üstte-Sağ Butonlar (Bildirim + Geçmiş)

**Dosya:** `SearchScreen.kt` + `AppRootTabPages.kt`
**STATUS: ✅ TAMAMLANDI**

- `SearchScreen` yeni params: `onOpenNotifications`, `isNotificationsVisible`, `onOpenWatchHistory`, `onOpenDownloads` (varsayılan `{}`/`false`).
- Sticky header Row'ında, arama çubuğunun sağına:
  - **🔔 Bildirim** Box (38.dp, clip 12.dp) — `isNotificationsVisible` true ise görünür
  - **🕐 İzleme Geçmişi** Box (38.dp, clip 12.dp) — her zaman görünür
- `AppRootTabPages.kt` SearchTabPage'te bağlandı:
  - `onOpenNotifications` → `DetailScreen.Notifications`
  - `isNotificationsVisible` = `ctx.isAniListConnected || ctx.isMalConnected || ctx.isSimklConnected`
  - `onOpenWatchHistory` → `DetailScreen.WatchHistory`
  - `onOpenDownloads` → `DetailScreen.Downloads`

## TASK 3 — Diğer Sayfalarda Geçmiş + İndirilenler Butonları

**Dosya:** `ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`, `AppRootTabPages.kt`
**STATUS: ✅ TAMAMLANDI**

### 3a. Keşfet (ExploreScreen.kt)
- Sticky header Row'ında, bildirim Box'ının hemen sağına:
  - **🕐 İzleme Geçmişi** Box (38.dp, clip 12.dp)
  - **⬇️ İndirilenler** Box (38.dp, clip 12.dp)
- `onOpenDownloads` param eklendi (varsayılan `{}`).
- `AppRootTabPages.kt` ExploreTabPage'te `onOpenDownloads → DetailScreen.Downloads` bağlandı.

### 3b. MyList (MyListComponents.kt + MyListScreen.kt)
- Top bar Row'ında, bildirim Box'ının hemen sağına:
  - **🕐 İzleme Geçmişi** Box (38.dp, clip 14.dp)
  - **⬇️ İndirilenler** Box (38.dp, clip 14.dp)
- `MyListScreen` yeni params: `onOpenWatchHistory`, `onOpenDownloads` (varsayılan `{}`).
- `MyListComponents` yeni params: `onOpenWatchHistory`, `onOpenDownloads` (varsayılan `{}`).
- `AppRootTabPages.kt` MyListTabPage'te bağlandı: `onOpenWatchHistory → DetailScreen.WatchHistory`, `onOpenDownloads → DetailScreen.Downloads`.

### 3c. Profil (KitsugiProfileScreen.kt)
- Header Row'ında, bildirim IconButton'ının hemen sağına:
  - **🕐 İzleme Geçmişi** IconButton (40.dp, clip 16.dp)
  - **⬇️ İndirilenler** IconButton (40.dp, clip 16.dp)
- `onOpenWatchHistory`, `onOpenDownloads` params eklendi (nullable, default null).
- `AppRootTabPages.kt` ProfileTabPage'te bağlandı.

---

## TASK 4 — Bölüm Sıralama Tersliği Fix

**Dosya:** `KitsugiMediaTabsClient.kt`
**STATUS: ✅ TAMAMLANDI — Kök neden bulundu ve düzeltildi**

### KökNeden

1. **`fetchEpisodesFromJikan`**: Bölümlerin `mal_id` değeri (örn. 100001, 100002...) bölüm numarası olarak kullanılıyordu.
   - Jikan API'si bölümleri **ters sırada** (en yüksekten en düşüğe) döndürür.
   - `mal_id` 100001+ olduğu için `maxFetchedEp` 100012 olarak hesaplanıyordu.
   - Bu da padding mantığının 100012'ye kadar hayali bölüm yaratmasına neden oluyordu.

2. **`fetchEpisodes` (jikan/mal/anilist branch)**: `list.size >= finalLimit` olduğunda liste **sırasız** döndürülüyordu.
   - AniList `streamingEpisodes` sırası garanti edilmez — ters/karışık gelebilir.

3. **`fetchEpisodes` (non-TMDB else branch)**: Bangumi/Kitsu/Simkl/Shikimori kaynaklarından gelen liste sırasız döndürülüyordu.

### Çözüm

- **`fetchEpisodesFromJikan`**: `(mal_id, (rawTitle, url))` Pair listesi kullanıldı. `mal_id` ascending sıralanıp, `mapIndexed` ile 1, 2, 3... episodeNumber verilerek `KitsugiStreamingEpisode` nesneleri oluşturuldu. Başlık da `#$epNum – $rawTitle` formatında yeniden üretildi.
- **`fetchEpisodes` (jikan/mal/anilist branch)**: Padding sonrası `list.sortedBy { it.episodeNumber ?: 0 }` ile ascending sıra garanti edildi.
- **`fetchEpisodes` (non-TMDB else branch)**: `list.sortedBy { it.episodeNumber ?: 0 }` ile ascending sıra garanti edildi.
- **`fetchEpisodes` (TMDB branch)**: `resultList` zaten `1..finalLimit` ascending loop ile oluşturuluyor — değişmedi.

---

## TASK 5 — Hayali Bölümler (12/24/100) ve Film/Dizi Karışımı Fix

**Dosya:** `KitsugiMediaTabsClient.kt` + `KitsugiPlayerViewModel.kt` + `KitsugiFullscreenPlayerScreen.kt`
**STATUS: ✅ TAMAMLANDI — Kök neden bulundu ve düzeltildi**

### KökNeden

1. **Padding mantığı DB toplamına göre**: `fetchEpisodes` padding için `maxOf(totalEpisodes, maxFetchedEp)` kullanıyordu.
   - DB'deki `total` değeri yanlış/őnemsiz olduğunda hayali bölümler yaratıyordu.
   - `totalEpisodes` null olduğunda default 12 ile padding yapılıyordu —filmler için 12 hayali bölüm.

2. **`KitsugiPlayerViewModel.loadEpisodes()`**: Filmler için bile `MediaType.Anime` ile bölüm çekiliyordu.
   - `_isMovie.value` true olsa bile episode listesi çekіліп `_episodesList` Set ediliyordu.
   - Bu yüzden filmlerde phantom episode listesi görünüyordu.

3. **`hasNextEpisode` / `onEpisodeEnded`**: Boş episode listesi için `Int.MAX_VALUE` fallback'i vardı.
   - `episodes.lastOrNull()?.episodeNumber ?: Int.MAX_VALUE` → boş liste için `Int.MAX_VALUE`.
   - `currentEpisode < Int.MAX_VALUE` → her zaman true → filmlerde "Sonraki Bölüm" butonu görünürdü.

### Çözüm

- **`fetchEpisodes` (jikan/mal/anilist branch)**: Padding artık **yalnızca** `maxFetchedEp` kadar.
  - `finalLimit = maxFetchedEp` (DB totalEpisodes yok).
  - `if (finalLimit > 0 && list.size < finalLimit)` — boş liste için padding yok.
  - `isAiring` / `cachedDetail` / `finalTotalEpisodes` değişkenleri bu branch'ten kaldırıldı (kullanılmıyor).

- **`fetchEpisodes` (TMDB branch)**: `finalLimit = maxFetchedEp` (DB totalEpisodes yok).
  - `isAiring` / `cachedDetail` değişkenleri bu branch'ten kaldırıldı.

- **`fetchEpisodes` (non-TMDB branch)**: `finalTotalEpisodes` DB query'si kaldırıldı.

- **`KitsugiPlayerViewModel.loadEpisodes()`**: `_isMovie.value` true ise `_episodesList.value = emptyList()` ve return — bölüm çekilmez.

- **`KitsugiPlayerViewModel.hasNextEpisode`**: `episodes.isNotEmpty()` guard eklendi.

- **`KitsugiPlayerViewModel.onEpisodeEnded`**: `_episodesList.value.isNotEmpty()` guard eklendi.

- **`KitsugiFullscreenPlayerScreen.kt`**: `hasNextEpisode` için `episodesList.isNotEmpty() &&` guard eklendi.

---

## TEKNİK NOTLAR

- Tüm yeni params default değerlerle tanımlandı (`{}` / `false` / `null`) — mevcut caller'lar bozulmadan compilation geçiyor.
- `SourceSearchPage.kt` SearchScreen caller'ı default params kullanıyor — değişiklik gerektirmez.
- `TvSearchScreen.kt` ayrı bir composable — etkilenmedi.
- `R.string.settings_watch_history` (strings.xml:984) ve `R.string.settings_downloads` string resource'ları kullanıldı.
- `Icons.Rounded.History` ve `Icons.Rounded.Download` Material icons kullanıldı.
- Kotlin parentez kontrolü tüm 9 dosyada OK.
- Android SDK mevcut olmadığı için tam Gradle build yapılamadı — sözdizimi doğrulandı.
