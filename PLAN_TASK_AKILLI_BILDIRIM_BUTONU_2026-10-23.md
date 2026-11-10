# PLAN & TASK — Akıllı Bildirim Butonu (Kaynak Bazlı Bildirim Açılışı)

**Tarih:** 2026-10-23
**İstenen davranış:** Kullanıcı **Listem / Keşfet / Arama / Profil** sekmelerinden birinde
hangi kaynağı seçmişse, 🔔 bildirim butonuna bastığı anda bildirimler sayfası **doğrudan o
kaynağın sekmesiyle** açılmalı. Buton "akıllı" olmalı: her sekmede hep aynı (bağlı ilk hesabın)
bildirimlerini açmak yerine, o sekmedeki aktif kaynağı takip etmeli.

---

## 1. Mevcut Durum (Öncesi)

| Sekme | Kaynak seçici | Bildirim butonu | Açılan sayfa |
|---|---|---|---|
| Listem | `MyListTabBar` kaynak hapı (Tümü + 6 kaynak) | ✅ vardı | Sabit: bağlı ilk hesap |
| Keşfet | `ExplorePlatformToggle` | ✅ vardı | Sabit: bağlı ilk hesap |
| Arama | `SourceEngineSelectorPill` (arama çubuğu içi) | ❌ **yoktu** | — |
| Profil | `ProfileSourceSelectorPill` | ✅ vardı | Sabit: bağlı ilk hesap |

Ortak nokta: `DetailScreen.Notifications` bir `data object`'ti ve **hiçbir bağlam taşımıyordu**;
`KitsugiNotificationsScreen` açılış sayfasını yalnızca "hangi hesap bağlı?" sırasına göre
(AniList → MAL → Simkl → Kitsu → Shikimori → Bangumi) seçiyordu. Yani AniList bağlıysa kullanıcı
Keşfet'te Bangumi'yi seçse bile bildirimler AniList sekmesinde açılıyordu.

## 2. Çözüm Mimarisi

1. **Yönlendirme yardımcısı (yeni dosya)**
   `ui/screens/notifications/NotificationSourceRouting.kt`
   - `NOTIF_PAGE_*` sabitleri: bildirim sayfasındaki `HorizontalPager` sekme indeksleri
     (0 AniList, 1 MAL, 2 TMDB & Simkl, 3 Kitsu, 4 Shikimori, 5 Bangumi).
   - `notificationPageForSource(source: String?): Int?` — kaynak kimliğini sekme indeksine çevirir.
     MAL için hem `mal` hem `jikan`, TMDB/Simkl için `tmdb`/`simkl` kabul edilir; `all`/`null`
     → `null` (varsayılan davranış).
   - `ExplorePlatform.notificationSourceId()`, `notificationSourceIdForProfileTab(index)`,
     `notificationSourceIdForMyListTab(index)` — sekmeye özgü seçicileri ortak kimliğe çevirir.

2. **Navigasyon artık bağlam taşıyor**
   - `DetailScreen.Notifications` : `data object` → `data class Notifications(val preferredSource: String? = null)`
   - `AppStateKey.Notifications(depth, preferredSource)`
   - `AppNavigationState.clearPreviousScreens()` içindeki kayıt anahtarına kaynak eklendi
     (`notifications_${depth}_${preferredSource}`) → **farklı kaynak = taze ekran state'i**;
     aynı kaynakla tekrar açılışta kaydırma konumu korunur.

3. **Bildirim ekranı seçili kaynakla açılıyor**
   `KitsugiNotificationsScreen(preferredSource = ...)`:
   - `requestedPage = notificationPageForSource(preferredSource)`
   - `initialPage = requestedPage ?: fallbackPage` (eski "bağlı ilk hesap" mantığı)
   - `LaunchedEffect(requestedPage)` emniyet kemeri: kaynak kimliği değişirse sayfa kaydırılır.
     Kullanıcı bildirim sayfası **içinde** kaynağı elle değiştirdiğinde efekt tetiklenmez
     (anahtar değişmediği için) — kullanıcı seçimi korunur.

4. **Sekme bağlantıları** (`AppRootTabPages.kt`)
   - Keşfet → `ctx.exploreViewModel.selectedPlatform.notificationSourceId()`
   - Arama → `ctx.searchViewModel.uiState.value.selectedEngine.id` (+ **yeni** 🔔 butonu)
   - Profil → `notificationSourceIdForProfileTab(ctx.kitsugiProfileViewModel.activeSubTab)`
   - Listem → `notificationSourceIdForMyListTab(appViewModel.myListTabIndex)`
     ("Tümü" birleşik listede tek kaynak yok → `null` → varsayılan)

5. **Arama sekmesine bildirim butonu eklendi**
   `SearchScreen` yeni parametreler: `isNotificationsVisible`, `onOpenNotifications`.
   Başlık satırına ("Arama") Keşfet/Listem ile birebir aynı 38 dp kutucuk eklendi.

## 3. Kararlar / Notlar

- **"Tümü" seçiliyse** eski davranış korunur (bağlı ilk hesap) — birleşik görünümün tek bir
  bildirim kaynağı yoktur.
- **Seçili kaynak bağlı değilse** yine o kaynağın sekmesi açılır; sayfa ilgili
  "giriş gerekli" durumunu gösterir. Kullanıcı bildirim sayfasındaki kaynak hapından
  dilediği gibi değiştirebilir. (İstenen davranış: "hangi kaynağı seçmişsem o açılsın".)
- Geri tuşu, derinlik (`depth`) hesabı ve `AnimatedContent` geçişleri değişmedi.

## 4. Değişen Dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/screens/notifications/NotificationSourceRouting.kt` | **YENİ** — kaynak → sekme eşlemesi |
| `AppNavigation.kt` | `DetailScreen.Notifications` / `AppStateKey.Notifications` artık `preferredSource` taşıyor |
| `AppRoot.kt` | `AppStateKey` üretiminde `preferredSource` aktarımı |
| `ui/app/AppNavigationState.kt` | state anahtarına kaynak eklendi (`is` desenine çevrildi) |
| `AppRootDetailPages.kt` | `KitsugiNotificationsScreen(... preferredSource = key.preferredSource)` |
| `AppRootTabPages.kt` | 4 sekmede akıllı `onOpenNotifications` + Arama'ya görünürlük/buton |
| `ui/screens/search/SearchScreen.kt` | 🔔 bildirim butonu (başlık satırı) |
| `ui/screens/notifications/KitsugiNotificationsScreen.kt` | Seçili kaynakla açılış |
| `app/build.gradle.kts` | Sürüm `2.4.340` → `2.4.341` |
| `release_notes_v2.4.341.md` | **YENİ** — sürüm notları (TR/EN) |

## 5. Test Senaryoları (elle)

1. Keşfet → Bangumi seç → 🔔 → bildirimler **Bangumi** sekmesinde açılır.
2. Keşfet → Tümü seç → 🔔 → bağlı ilk hesabın bildirimleri açılır (eski davranış).
3. Profil → Shikimori seç → 🔔 → **Shikimori** sekmesi.
4. Listem → MAL seç → 🔔 → **MyAnimeList** sekmesi; Listem → Tümü → 🔔 → varsayılan.
5. Arama → Kitsu seç → 🔔 → **Kitsu** sekmesi.
6. Bildirim sayfasında kaynağı elle değiştir → sayfa içi seçim korunur (geri dönmez).
7. Bildirim sayfasından geri → sekmeye dönüş; aynı kaynakla tekrar girişte kaydırma konumu korunur.
