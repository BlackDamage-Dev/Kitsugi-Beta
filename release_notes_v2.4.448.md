# Kitsugi Beta v2.4.448

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Çok Adlı Yerel / Orijinal Başlık Araması (`MangaTitleVariantResolver`)
- **Gerçek Yerel ve Orijinal Arama Modları**:
  - Manga arama çubuğundaki "Yerel / çevrilmiş ad" düğmesi artık sadece etiket değiştirmek yerine arka planda eserin yerel ve orijinal varyantlarını akıllıca çözümler.
  - **Yerel Ad Modu (`LOCALIZED`)**: Eklentinin baskın diline göre (örn. Türkçe eklentiler için `tr`) çevrilmiş ad → arama sorgusu → arındırılmış çekirdek/ASCII ad → orijinal Japonca/Çince/Korece ad sırasıyla aranır. Eklentiler genellikle Türkçe başlık tuttuğu için ilk denemede doğrudan sonuç alınır.
  - **Orijinal Ad Modu (`ORIGINAL`)**: Orijinal başlık (Kanji/Hangul/Hanzi veya Romaji) → arama sorgusu → arındırılmış ad → yerel başlık sırasıyla denenir.
  - AniList GraphQL entegrasyonu ve önbellekli `MangaTitleVariantResolver` ile başlık varyantları anında çözümlenir; yerel eşanlamlılar varsa harici ağ isteğine çıkılmadan çözülür.
  - Çok sorgulu eleme (`postProcessSearchResults` / `filterResultsTwoPass`): Eser hangi adla bulunursa bulunsun sonuçlar listede güvenle tutulur.

### 2. Kompakt Keşif Üst Bölümü (Video / Stream Eklenti Paritesi)
- **Tek Satır Kompakt Üst Bar (`MangaDiscoverTopBar`)**:
  - Geri butonu, ekran başlığı, canlı özet ("X sonuç · Y kaynak · Z başlık denendi"), arama ayrıntıları ve eklenti yönetimi tek bir satırda toplanarak dikey alandan tasarruf sağlandı.
- **Tek Kart Arama ve Filtre Paneli (`MangaDiscoverSearchPanel`)**:
  - Arama alanı, mod değiştirme çipi ve kaynak filtreleme çipleri tek bir kart içine alındı; dikey modda `210.dp`, yatay (landscape) modda `168.dp` kompakt düzeniyle ekranı gereksiz kaplamaz.
  - Yatay modda arama kutusu ve mod çipi yan yana yerleşerek ızgaranın yüksekliğinden çalmaz.
- **Arama Ayrıntıları Bilgi Diyaloğu (`MangaSearchInfoDialog`)**:
  - Eser için denenen tüm varyantları ve hangi eklentinin hangi başlıkla sonuç getirdiğini gösteren şeffaf bilgilendirme penceresi eklendi.

### 3. "Tümü" Filtresi Kalıcılığı & Popüler Liste Birleştirmesi
- **Gerçek "Tümü" Popüler Görünümü**:
  - Filtre seçilmediğinde (`null`), en popüler 3 kaynağın listesi birleştirilerek sunulur; kartların hangi kaynağa ait olduğu `manga.source` üzerinden birebir çözümlenir.
  - Arama sırasında kaynak filtrelerine dokunmak ek ağ isteği tetiklemez (yalnızca görünüm filtrelenir); detay ekranına gidip geri dönüldüğünde "Tümü" seçimi kaybolmaz veya kendiliğinden bir eklentiye tıklanmış duruma geçmez.
  - TV arayüzü (`TvMangaBrowseScreen`) de "Tümü" çipi ve birleşik popüler liste mantığıyla mobil arayüzle tam eşitlendi.

### 4. Mimari ve Dil Bütünlüğü
- **Zero Material Icons**:
  - `app/src/main` genelinde `androidx.compose.material.icons.*` sıfırlandı; %100 `KitsugiIcons as Icons` standardı uygulandı.
- **%100 Dil Simetrisi**:
  - Türkçe (`values/strings.xml`) ve İngilizce (`values-en/strings.xml`) dosyalarında 18.716 anahtar birebir simetrik olarak eşitlendi (0 fark).
- **Test ve Performans**:
  - Birim testleri (`testFossDebugUnitTest`) ve sekme performans koruyucusu (`kitsugi_pager_perf_guard.py`) başarıyla tamamlandı.

---

## 🇬🇧 English Release Notes

### 1. Multi-Title Localized & Original Manga Search (`MangaTitleVariantResolver`)
- **True Localized & Original Search Modes**:
  - The "Localized / translated title" toggle now actively resolves and searches multi-language variants instead of merely flipping an indicator tag.
  - **Localized Mode (`LOCALIZED`)**: Evaluates translated title (tailored to dominant extension language, e.g. `tr`) -> raw query -> normalized core/ASCII title -> original CJK title. Allows Turkish and localized extension catalogs to hit immediate matches on the first attempt.
  - **Original Mode (`ORIGINAL`)**: Prioritizes original native title (Kanji/Hangul/Hanzi/Romaji) -> query -> normalized title -> localized title.
  - AniList GraphQL resolution and in-memory caching in `MangaTitleVariantResolver` provide instantaneous title variant resolution.
  - Multi-query candidate filtering (`postProcessSearchResults` / `filterResultsTwoPass`): Matches across any valid title variant are preserved in search results.

### 2. Compact Discover Header (Stream / Video Extension Parity)
- **Single-Row Compact Top Bar (`MangaDiscoverTopBar`)**:
  - Back navigation, screen title, live result summary ("X results · Y sources · Z titles tried"), search details, and extension settings consolidated into a single row.
- **Unified Discover Search Panel (`MangaDiscoverSearchPanel`)**:
  - Search text field, mode toggle chip, and source filter chips merged into a single surface card with responsive sizing (`210.dp` portrait, `168.dp` landscape) maximizing grid viewport space.
- **Search Info Dialog (`MangaSearchInfoDialog`)**:
  - Displays all tried title variants, mode descriptions, and per-source matched titles.

### 3. Persistent "All" Source Filter & Merged Popular Manga
- **Unified Popular View**:
  - Default browsing merges popular catalogs across top 3 sources when no filter is selected. Each card reliably maps to its origin via `manga.source`.
  - Filter selection during search is strictly view-only without issuing redundant network requests; navigating back from manga details reliably preserves the active "All" filter selection.
  - `TvMangaBrowseScreen` brought to full parity with the "All" filter chip and unified popular listing.

### 4. Architectural & Localization Integrity
- **Zero Material Icons**:
  - Zero imports of `androidx.compose.material.icons.*` across `app/src/main`, fully standardized on `KitsugiIcons as Icons`.
- **100% Localization Parity**:
  - Exactly 18,716 string keys synchronized across Turkish and English language resources (0 discrepancy).
- **Verified Stability**:
  - Passed all unit tests (`testFossDebugUnitTest`) and pager performance verification (`kitsugi_pager_perf_guard.py`).
