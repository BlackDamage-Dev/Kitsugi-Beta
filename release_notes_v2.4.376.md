# Kitsugi Beta v2.4.376

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Sabit (Sticky) Arama Çubuğu (`SearchScreen.kt`)
- **Kaydırmada Sabit Kalan Üst Alan:** Arama çubuğu daha önce listenin en başındaki sıradan bir öğe olduğu için sayfa aşağı kaydırıldığında ekrandan kayboluyordu. Artık `stickyHeader(key = "search_title_and_bar")` ve hafif yarı saydam arka planlı `Surface` yapısına kavuştu; kullanıcı sayfayı ne kadar kaydırırsa kaydırsın arama çubuğu, motor seçici ve filtre butonu her zaman ekranın üstünde sabit kalır.

### 2. Arama ve Tüm Ana Sayfalarda Üst-Sağ Kısayol Butonları
- **Arama Sayfası (`SearchScreen.kt` & `AppRootTabPages.kt`):** Arama çubuğunun hemen sağına, Eklenti Portalı butonunun yanına 🔔 **Bildirim** (hesap bağlıysa) ve 🕐 **İzleme Geçmişi** butonları eklendi.
- **Keşfet, Listem ve Profil Sayfaları (`ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`):** Mevcut bildirim butonunun hemen yanına 🕐 **İzleme Geçmişi** ve ⬇️ **İndirilenler** butonları yerleştirildi; tek dokunuşla geçmişe ve indirilen videolara erişim sağlandı.

### 3. Bölüm Sıralama Tersliği Giderildi (`KitsugiMediaTabsClient.kt`)
- **Jikan Ters Sıralama ve MAL ID Çözümü:** Jikan API'si bölümleri ters sırada (en yüksekten en düşüğe) döndürür ve her bölümün `mal_id` değeri (100001+) bölüm numarası yerine kullanıldığında sıra karışıyor ve hayali bölümler oluşuyordu. Artık bölümler `mal_id` üzerinden artan sırayla dizilip 1, 2, 3... biçiminde kesin sıralı olarak numaralandırılıyor.
- **Tüm Kaynaklarda Artan Sıra Garantisi:** AniList, Bangumi, Kitsu, Simkl ve Shikimori dahil tüm kaynaklardan gelen bölüm listeleri `list.sortedBy { it.episodeNumber ?: 0 }` ile sıralanarak sunulur.

### 4. Hayali Bölümler (12/24/100) ve Film/Dizi Karışımı Giderildi
- **DB Toplamına Göre Padding Kaldırıldı (`KitsugiMediaTabsClient.kt`):** Veritabanındaki tahmini toplam bölüm sayısı API'den fazla olduğunda aradaki boşluklar hayali bölümlerle (12, 24 veya 100) dolduruluyordu. Artık boşluk doldurma yalnızca gerçekten çekilen en yüksek bölüm numarasına (`maxFetchedEp`) kadar yapılır.
- **Filmlerde Bölüm Çekimi Engellendi (`KitsugiPlayerViewModel.kt`):** Filmler için (`_isMovie.value == true`) anime/dizi bölüm çekimi yapılmayarak film içeriklerinin dizi bölümleriyle karışması önlendi.
- **Boş Liste Koruması (`KitsugiFullscreenPlayerScreen.kt` & `KitsugiPlayerViewModel.kt`):** Boş bölüm listelerinde `Int.MAX_VALUE` fallback'i `episodesList.isNotEmpty()` kontrolüyle engellendi; böylece filmlerde "Sonraki Bölüm" butonunun görünmesi veya otomatik sonraki bölüme geçme hatası tamamen giderildi.

---

## 🇬🇧 English Release Notes

### 1. Sticky Search Header (`SearchScreen.kt`)
- **Pinned Top Search Bar:** Previously, the search bar was a standard scrollable item in the list and disappeared when scrolling down. It is now implemented as a `stickyHeader(key = "search_title_and_bar")` wrapped in a sleek, semi-translucent `Surface`, keeping the search input, engine selector pill, and filter action accessible at all times while scrolling.

### 2. Quick Navigation Buttons on Top Bars Across All Screens
- **Search Screen (`SearchScreen.kt` & `AppRootTabPages.kt`):** Added 🔔 **Notifications** (when accounts are connected) and 🕐 **Watch History** buttons directly beside the search bar and Addon Companion button.
- **Explore, My List & Profile Screens (`ExploreScreen.kt`, `MyListComponents.kt`, `MyListScreen.kt`, `KitsugiProfileScreen.kt`):** Added 🕐 **Watch History** and ⬇️ **Downloads** buttons right next to the notification icon, providing instant access to downloaded content and playback history across all main tabs.

### 3. Inverted Episode Ordering Resolved (`KitsugiMediaTabsClient.kt`)
- **Jikan Descending Order & MAL ID Fix:** Jikan API returns episodes in descending order and provides a global `mal_id` (100001+). These raw IDs previously distorted episode indexing. Episodes are now collected, sorted ascending by ID, and re-indexed sequentially (1, 2, 3...).
- **Ascending Order Guarantee:** Guaranteed ascending order (`list.sortedBy { it.episodeNumber ?: 0 }`) across all stream/episode providers (AniList, Bangumi, Kitsu, Simkl, Shikimori).

### 4. Phantom Episodes (12/24/100) & Movie/Series Mix-Up Fixed
- **Eliminated DB-Based Over-Padding (`KitsugiMediaTabsClient.kt`):** Removed fallback padding that created phantom episodes up to estimated DB totals (e.g. 12, 24, 100). Padding is now strictly capped to the maximum fetched episode number (`maxFetchedEp`).
- **Guarded Movie Episode Loading (`KitsugiPlayerViewModel.kt`):** Movie media items (`_isMovie.value == true`) now immediately return an empty episode list, preventing anime episode fetchers from polluting movie screens.
- **Empty Episode List Guard (`KitsugiFullscreenPlayerScreen.kt` & `KitsugiPlayerViewModel.kt`):** Guarded `hasNextEpisode` and `onEpisodeEnded` against empty lists, preventing the `Int.MAX_VALUE` fallback from showing an invalid "Next Episode" button during movie playback.
