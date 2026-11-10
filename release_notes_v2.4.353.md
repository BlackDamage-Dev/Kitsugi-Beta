# Kitsugi Beta v2.4.353

## 🇹🇷 Türkçe

### 📅 Listem Sıralaması Baştan Yazıldı — "Son Eklenen" Artık Gerçekten Son Eklenen
- **Gerçek Eklenme Tarihi & DB v30:** Kayıtlara `addedAt` alanı eklendi (Room Database v30). AniList, Kitsu ve Shikimori'de kütüphanenize eklediğiniz gerçek tarih kullanılır; önceden sıralama, içe aktarım sırasından ibaret olan yerel satır numarasına bakıyordu.
- **Sıralama Artık Kazanıyor:** Bir sıralama seçtiğinizde (Son eklenen, Puan, Ada göre…) liste tek akışta ve **global** sıralanır. İzleniyor/Tamamlandı/Planlandı bölümleri artık seçtiğiniz sırayı ezmez; durum bölümlü gezinme yalnızca "Varsayılan" sıralamada gösterilir.
- **Son Eklenen'e Özel Dönem Başlıkları:** Liste zaman çizelgesiyle açılır — Bugün, Dün, Bu Hafta, Geçen Hafta, Bu Ay, Geçen Ay, Son 3 Ay, Bu Yıl, Geçen Yıl, Daha Eski…
- **Her Seçeneğe Özgü Sıralama:** İlerleme artık tamamlanma oranına (%100, %50…) göre; puansız ve tarihsiz kayıtlar her iki yönde de sonda; "Ada göre" ekranda gördüğünüz başlığı (başlık dili tercihine göre) sıralar; Favoriler içinde en yeni favori üstte.
- **Zaman Birimi & Sağlayıcı Normalizasyonu:** Bangumi'nin milisaniye, diğerlerinin saniye yazdığı güncelleme zamanları normalize edildi; Simkl kayıtları artık son izleme zamanını taşır (`last_watched_at`).
- **Kullanıcı Listesi & TV Uyumu:** Başka kullanıcıların profil listesi ekranına ve komşu sayfa geçişlerine de aynı sıralama sözleşmesi uygulandı.
- **Not:** Güncellemeden sonra bir kez senkronizasyon yapmak eklenme tarihlerini kaynaklardaki gerçek değerlerle netleştirir. Mevcut görünümünüzün aniden bozulmaması için kayıtlı eski "Son eklenen" seçimi bir kerelik "Varsayılan"a alınır; dilediğiniz an Sıralama menüsünden tekrar seçebilirsiniz.

### 🎬 Fragman & OP/ED Video Oynatıcı Aşağı Kayarak Açılma & Yukarı Kapanma Animasyonu
- **Akıcı slide & expand:** `KitsugiDetailReveal` geçişine dikey kayma animasyonu (`slideInVertically(-h/8)` / `slideOutVertically(-h/8)`) eklendi; fragman ve tema kartları artık aniden açılmak yerine aşağı doğru akıcı şekilde genişleyip yukarı doğru büzülerek kapanır.
- **Videolar sekmesi entegrasyonu:** `DetailVideoGroup` içerisindeki oynatıcı alanı `KitsugiDetailReveal(visible = isSelected)` ile sarılarak kapatma butonu ile birlikte animasyona dahil edildi.
- **İçerik boyutu senkronizasyonu:** `KitsugiTrailerCard`, `DetailVideoGroup`, `KitsugiMediaVideosTab`, `KitsugiThemesList` ve `ThemeVideoSection` alanlarına `animateDetailContentSize()` eklenerek oynatıcı açılıp kapanırken alt içeriklerin zıplamadan yumuşakça kayması sağlandı.

### ⚡ Detay Sayfası & Bilgi Sekmesi Akıcılık ve Kaydırma Performansı
- **Recompose fırtınası engellendi:** `MediaEntryDetailPage` içindeki sekme akışları bağımsız slot composable'larına (`OverviewTabSlot`, `CharactersTabSlot`, vb.) ayrıştırıldı; arka planda bir sekmenin verisi geldiğinde tüm sayfanın yeniden kompoze olması önlendi.
- **Jest & sayfa geçiş optimizasyonu:** `currentPage` okumaları izole yüklere alındı ve callback'ler `remember` ile stabilize edilerek yatay ve dikey kaydırmalardaki takılmalar giderildi.
- **Layout fazı enterpolasyonu:** Portre moddaki ofset okumaları layout fazına taşınarak gereksiz kare başı çizimler sonlandırıldı.
- **Galeri butonu nabız animasyonu:** `KitsugiGalleryIconButton` üzerindeki sonsuz nabız döngüsü yalnızca aktif yükleme anıyla sınırlandırılarak CPU ve pil yükü sıfırlandı.

### 🔍 Shikimori Arama Sonuçlarında Başlık Dili Düzeltmesi
- **Doğru Romaji Eşlemesi:** Shikimori REST arama sonuçlarından gelen `name` alanı artık yanlışlıkla `titleEnglish` yerine `titleRomaji` alanında saklanıyor.
- **GraphQL Başlık Zenginleştirmesi:** Anime ve manga arama/keşfet sonuçları `ShikimoriTitleResolver` ile arka planda gerçek İngilizce ve Japonca adlarla zenginleştirilerek Ayarlar'da seçilen başlık diline (İngilizce, Romaji, Japonca) göre listelenir.
- **Zaman Aşımı ve Yedek Koruması:** Paralel çalışan istekler için 6 saniyelik zaman aşımı tanımlandı; bağlantı hatasında veya yanıtsızlıkta Romaji başlık kesintisiz korunur.

### 📖 Wiki Okuyucu Tema Uyumu ve Şerit Sadeleştirmesi
- **Varsayılan Uygulama Teması:** `wikiPageTheme` varsayılanı `APP` olarak güncellendi. Yeni kurulumlarda wiki açılır sayfası uygulamanın açık/koyu temasını doğrudan izler; koyu temada ekran parlaması ve renk kırılması yaşanmaz. Özgün site temasını isteyenler için `SITE` tercihi korunur.
- **Sadeleştirilmiş Sekme Şeridi:** Aktif sekme %16 vurgu dolgusu ve %40 vurgu çerçevesiyle öne çıkarılırken, pasif sekmeler tamamen saydamlaştırılarak kutu kalabalığı giderildi. Kapat (`×`) butonu görsel kirliliği önlemek için yalnızca aktif sekmede gösterilir.
- **Kompakt ⋮ Menüsü ve Yeni Eylemler:** Üstteki kalabalık bilgi satırları menü başlığına (kilit ikonu + alan adı + mod) taşındı. Menüye **Bağlantıyı paylaş** (`ACTION_SEND`), **Bağlantıyı kopyala** (pano) ve çoklu sekmeler için **Sekmeler (n)** listesi eklendi.
- **Gruplandırılmış Sayfa Araçları:** Ayarlar kartları dilinde 3 ana başlığa ayrıldı: Çeviri ve gizlilik, Görünüm (hızlı 10 puanlık yazı boyutu büyüt/küçült, oturumluk ekranda tutma), Sayfa (sayfada bul, hakkında).
- **Zıplamasız Yükleme Çizgisi & Temalı Arama:** Yükleme çizgisi sabit 2 dp yüksekliğe sabitlenerek sayfa yüklenince yaşanan zıplama önlendi; sayfada bul çubuğu yuvarlatılarak uygulama vurgu rengine bağlandı.
- **Ayarlardan Doğrudan Erişim:** **Ayarlar → Tercihler → Fandom Wiki** altına `Okuma görünümü` eklendi; okuyucu penceresi açılmadan da genel tema ve yazı boyutu tercihi yönetilebilir.

---

## 🇬🇧 English

### 📅 My List Sorting Rewritten — "Recently Added" Is Now Truly Accurate
- **Real Add Dates & DB v30:** Added the `addedAt` timestamp to local records (Room Database v30). AniList (`createdAt`), Kitsu, and Shikimori provide the exact date a title was added to your library, replacing the legacy local row ID that reflected raw import order.
- **Sort Priority:** Selecting any sort option (Recently added, Score, Title, etc.) now produces a unified, **globally** sorted list. Status sections (Watching/Completed/Planning) no longer override user sorting; sectioned browsing is retained under the new "Default" sort option.
- **Dedicated Period Headings:** Recently added sort displays intuitive chronological headers: Today, Yesterday, This Week, Last Week, This Month, Last Month, Last 3 Months, This Year, Last Year, and Earlier.
- **Option-Specific Logic:** Progress sorts by completion ratio; unrated and dateless entries sink to the end in both directions; Title sorts by the currently displayed title language; Favorites sorts newest favorites first.
- **Timestamp & Provider Normalization:** Normalized millisecond (Bangumi) and second-based timestamps; Simkl imports now carry `last_watched_at`.
- **User Profile & TV Parity:** Applied the identical global sorting contract to other users' list view screens and horizontal page transitions.
- **Note:** Running a sync after updating backfills exact add dates from supporting providers. Previously saved "Recently added" selections are safely reset to "Default" once to prevent unexpected layout jumps.

### 🎬 Smooth Slide & Expand Animation for Trailer and OP/ED Players
- **Fluid slide & expand:** Added vertical slide transitions (`slideInVertically(-h/8)` / `slideOutVertically(-h/8)`) to `KitsugiDetailReveal`, ensuring inline video players and music themes glide smoothly downward on open and retract upward on close.
- **Videos tab parity:** Wrapped inline players in `DetailVideoGroup` with `KitsugiDetailReveal(visible = isSelected)`, integrating the inline dismiss button into the shared animation.
- **Content size interpolation:** Applied `animateDetailContentSize()` across `KitsugiTrailerCard`, `DetailVideoGroup`, `KitsugiMediaVideosTab`, `KitsugiThemesList`, and `ThemeVideoSection`, preventing layout jumps when players reveal or collapse.

### ⚡ Detail Page & Overview Tab Scroll Smoothness & Performance Optimization
- **Recomposition storm eliminated:** Refactored tab flows into isolated slot composables (`OverviewTabSlot`, `CharactersTabSlot`, etc.), ensuring background data arrivals only recompose their targeted tab instead of the entire page scaffold.
- **Gesture & page transition decoupling:** Moved `currentPage` consumption into scoped effects and stabilized lambdas with `remember` to ensure fluid horizontal and vertical scrolling.
- **Layout-phase offset interpolation:** Deferred portrait offset measurements to the layout phase, eliminating per-frame composition overhead.
- **Gallery icon pulse bounded:** Restricted `KitsugiGalleryIconButton` pulse animations strictly to active image loading states, freeing idle CPU and GPU cycles.

### 🔍 Shikimori Search Result Title Language & Romaji Differentiation
- **Accurate Romaji Mapping:** Shikimori REST search responses now properly route `name` to `titleRomaji` instead of erroneously assigning it to `titleEnglish`.
- **GraphQL Title Enrichment:** Search and discover results are asynchronously enriched with authentic English and Japanese names via `ShikimoriTitleResolver`, strictly respecting user-selected title language preferences (English, Romaji, or Japanese).
- **Timeout & Graceful Fallback:** Added a strict 6-second timeout to GraphQL enrichment queries, seamlessly falling back to Romaji titles without blocking UI or image resolution.

### 📖 Wiki Reader Chrome Theme & Tab Strip Streamlining
- **App Theme by Default:** `wikiPageTheme` now defaults to `APP`, ensuring new installations seamlessly match the application's dark or light theme palette without abrupt color clashing. Existing choices and the `SITE` option remain fully supported.
- **Minimalist Tab Strip:** Active tabs are highlighted with a 16% accent fill and 40% border outline, while inactive tabs remain transparent to prevent visual clutter. Close buttons (`×`) are streamlined to the active tab.
- **Clean Context Menu:** Domain and layout context badges have migrated inside the `⋮` menu header. Added **Share link** (`ACTION_SEND`), **Copy link** (clipboard), and a scrollable **Tabs (n)** overview list.
- **Grouped Page Tools:** Redesigned sheet into structured groups: Translation & Privacy, Appearance (quick ±10pt text resizing, keep screen on toggle), and Page (find in page, about).
- **Stable 2 dp Progress Bar & Themed Find Bar:** Fixed loading indicator jitter by dedicating an unshifting 2 dp progress track; styled find bar with rounded geometry and theme accents.
- **Settings Integration:** Added a direct `Reading appearance` preference entry under **Settings → Preferences → Fandom Wiki**, allowing full font size and theme configuration outside the reader.
