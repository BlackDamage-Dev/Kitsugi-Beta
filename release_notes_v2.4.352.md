# Kitsugi Beta v2.4.352

## 🇹🇷 Türkçe

### 🔍 Listem Kaynak Bazlı Arama & Kesin Sağlayıcı İzolasyonu
- **Sekme Arama İzolasyonu:** AniList, MyAnimeList, Simkl, Kitsu, Shikimori ve Bangumi sekmelerinde arama yapıldığında arama kutusunun seçili sağlayıcıyı yok sayarak tüm kütüphaneyi taraması (`useGlobalSearchResults`) tamamen kaldırıldı. Her sekme yalnızca kendi platformuna ait kayıtları arar, listeler, filtreler ve sayar.
- **Bağlantı ve Boş Durum Koruması:** Bağlı olmayan veya henüz senkronize edilmemiş kaynak sekmelerinde arama kutusu dolu olsa dahi "Hesabı Bağla" / "Senkronize Et" durumu korunur; yanlışlıkla boş sonuç listesine düşülmez.
- **Tek Geçişli Kütüphane Anlık Görüntüsü (`MyListLibrarySnapshot`):** Arka planda `Dispatchers.Default` üzerinde tek geçişte hem "Tümü" sekmesinin birleşik/tekilleştirilmiş kartları ve kaynak rozetleri hem de platform sekmelerinin (`entriesByTab`) kendi listeleri üretilir; arayüz kasmaları ve titremeler engellenir.

### 🌐 Kardeş Başlık Varyantları & Otomatik Zenginleştirme
- **Simkl, Shikimori & Bangumi Başlık Tamamlama:** Yalnızca Romaji veya CJK başlık döndüren servislerin eksik İngilizce/Latin başlıkları, aynı yapıma ait doğrulanmış kardeş grup üyeleri ve `MalAxisLibraryTitleHydrator` (AniList `idMal_in` toplu sorgusu) ile tamamlandı ("Kaiju No. 8" araması Simkl, Shikimori ve Bangumi sekmelerinde de ilgili kartı doğru bulur).
- **Kitsu Ters Kimlik Köprüsü:** `MyListIdentityCrosswalk` içerisine `reverseKitsuMapping` eklenerek Kitsu kayıtlarının MAL ekseniyle eksiksiz birleşmesi sağlandı.
- **Kaynak Onarım Koruması:** `MyListViewModel.repairMisclassifiedSources`, gerçek MAL kayıtlarını yanlışlıkla AniList kaynağına taşımayacak şekilde sınırlandırıldı.

### 🎯 Gürültüsüz & Hassas Başlık Araması
- **Meta Alan Gürültüsü Temizlendi:** Arama sorgusu `source`, `type.name`, `status` ve sentetik MAL kimlikleri (100M/300M/400M/500M) gibi alanlardan arındırıldı; yalnızca başlık varyantları ve 4 haneli yapım yılı üzerinde çalışır.
- **Gelişmiş Eşleme Algoritmaları:** Türkçe `İ/I/ı/i` katlama (`foldMyListSearchText`), noktalama duyarsız başlık eşleşmesi, tam sayı belirteç koruması (sezon/numara karışmasını önleme) ve güvenli kelime bazlı yazım hatası / kısaltma toleransı eklendi.

### 🏷️ Listem Kart Köşe Rozetleri (Görsel Netlik)
- **Köşelere Tam Oturan Rozetler:** Durum ve puan rozetleri poster kartının üst ve alt dış köşelerine sıfırlandı (`TopEndCornerShape`, `BottomEndCornerShape`).
- **Minimal Kaplama Alanı:** Dış kenarlardaki yuvarlaklıklar kaldırılıp yalnızca görsele bakan iç köşeler 6.dp yumuşatıldı; rozet içi boşluklar ve yazı boyutları optimize edilerek afişlerin kapanma oranı en aza indirildi.

---

## 🇬🇧 English

### 🔍 Source-Scoped Search & Strict Provider Isolation in MyList
- **Tab Search Isolation:** Eliminated global search leakage (`useGlobalSearchResults`) across individual provider tabs (AniList, MyAnimeList, Simkl, Kitsu, Shikimori, Bangumi). Each tab strictly filters, searches, sorts, and counts only its own provider entries.
- **State Preservation on Disconnected Tabs:** Unconnected or empty provider tabs consistently maintain their "Connect Account" / "Sync" landing state even when a search query is active.
- **Single-Pass Library Snapshot (`MyListLibrarySnapshot`):** Offloaded combined deduplication and per-tab slice generation (`entriesByTab`) to a unified background pass on `Dispatchers.Default`, eliminating UI stutters.

### 🌐 Cross-Provider Title Hydration & Alias Matching
- **Simkl, Shikimori & Bangumi Variant Enrichment:** Missing English/Latin title variants from provider APIs that only return Romaji or CJK titles are now automatically hydrated in-memory from verified sibling records and AniList batch GraphQL queries via `MalAxisLibraryTitleHydrator`.
- **Kitsu Reverse Crosswalk:** Added `reverseKitsuMapping` to `MyListIdentityCrosswalk` to ensure full parity when merging standalone Kitsu records.
- **Source Repair Guard:** Hardened `MyListViewModel.repairMisclassifiedSources` against falsely reclassifying genuine MAL entries to AniList.

### 🎯 Accurate & Noise-Free Title Search
- **Metadata False Positives Eliminated:** Restricted search strictly to validated title variants, verified aliases, and exact 4-digit release years, preventing erroneous matches on metadata (type names, sources, or internal synthetic IDs).
- **Advanced Text Normalization:** Added Turkish `İ/I/ı/i` folding, punctuation-free title matching, exact numeric token preservation (preventing season/number collisions), and typo/initialism tolerance.

### 🏷️ Compact Poster Corner Badges
- **Flush Corner Alignment:** Status and score badges now sit flush against the outer boundaries of poster cards (`TopEndCornerShape`, `BottomEndCornerShape`).
- **Reduced Visual Occlusion:** Removed outer border radius while keeping a soft 6.dp inner corner with tightened padding and typography, maximizing poster artwork visibility.
