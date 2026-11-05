# Kitsugi Beta v2.4.444

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Manga Okuyucu & Geçmiş Deneyimi Sadeleştirme ve İyileştirmeleri
- **Okuyucu Alanı Ferahlatıldı**:
  - Alt bardaki 60dp'lik gereksiz küçük resim şeridi kaldırıldı. Okuyucu alt çubuğu 3 satırdan 2 satıra indirildi (−32dp okuma alanı kazanımı).
  - Tek dokunuşla tam ekran galeriye erişim (📄 düğmesi, üst barda ▦ ve sayfaya uzun basış kısayolu).
  - Bölüm ayarları alt paneli küçük ekranlarda kesilmeyecek şekilde %62 yükseklik ve kaydırılabilir liste yapısına kavuşturuldu.
- **Bölüm & Arama Arayüzü İyileştirmeleri**:
  - Manga detay hero alanı ekran boyutuna göre optimize edildi (200/260/292dp, 1.42× kapak oranı).
  - Açıklama metni 4 satır ve "Devamını göster / Daha az" katlanabilir yapısıyla dengelendi.
  - Manga arama ekranında 76dp'lik dev bilgi kartı kompakt tek satır şeride dönüştürüldü ve arama kutusuna hızlı temizleme (✕) düğmesi eklendi.
- **Manga Okuma Geçmişi**:
  - `MangaReadHistoryEntity` ve `MangaReadHistoryManager` ile manga okuma geçmişi ve sayfa ilerlemesi izleme sistemi.
  - Geçmiş ekranında Video ve Manga sekmeleriyle eksiksiz geçmiş yönetimi.

### 2. Manga Bölüm Sıralaması (1'den N'e) ve Mihon/Tachiyomi Uyarlamaları
- **Varsayılan Sıralama (1 → N)**:
  - Bölüm listeleri artık ters (386'dan geriye) değil, mantıksal okuma sırasına göre artan (1 → N) `ChapterSortMode.OldestFirst` varsayılanıyla listelenir.
  - Sıralama tercihi DataStore ile genel ve manga bazında kalıcı ezme (`manga_sort_override_<hash>`) desteğine sahiptir.
  - Sıralama döngü butonu yerine doğrudan seçim yapılabilen Açılır Sıralama Menüsü (1→N, N→1, Son güncellenen).
  - Okuyucuda önceki/sonraki bölüm gezinmesi ve bölüm sonu kartı doğru okuma sırasını takip eder. Numarasız bölümler başlıktan otomatik çözümlenir (`MangaChapterNumberResolver`).
- **Mihon Uyarlamaları**:
  - **Bölüm Gösterim Modu**: Bölüm Numarası veya Bölüm Başlığı olarak gösterme seçeneği.
  - **Bölüm Yer İmleri (★)**: Bölümleri yer imine ekleme ve yer imlerine göre filtreleme.
  - **Okunmuş Bölümleri Atlama & Otomatik Bölüm Geçişi**: Bölüm bittiğinde otomatik sonraki bölüme geçiş ve okunmuş bölümleri atlama seçenekleri.
  - **Sayfa Kenarlıklarını Kırpma (Crop Borders)**: Mobil ve TV'de webtoon ve dikey sayfa kenar boşluklarını kırpma (`cropBorders`).
  - **CBZ Çevrimdışı İndirme & Okuma**: `MangaChapterDownloader` ile bölümleri CBZ arşivi olarak indirme ve kaynaktan bağımsız çevrimdışı okuma.
  - **Manga Kütüphanesi (Library)**: Room v34 veritabanı göçü (`manga_library` tablosu, `MIGRATION_33_34`) ile favori manga kitaplığı ve Android TV tam paritesi.

---

## 🇬🇧 English Release Notes

### 1. Manga Reader & History Streamlining
- **Uncluttered Reader Viewport**:
  - Removed duplicate 60dp thumbnail strip from reader bottom bar. Bottom bar reduced from 3 to 2 rows (−32dp height reduction).
  - Quick single-tap access to page gallery (📄 button, ▦ in top bar, and long-press page gesture).
  - Chapter settings bottom sheet resized to 62% screen height with scrolling to prevent brightness slider clipping on compact screens.
- **Detail & Search Enhancements**:
  - Dynamic responsive hero height in manga details (200/260/292dp with 1.42× aspect ratio).
  - 4-line truncated synopsis with expandable "Show more / Show less" toggle.
  - Compact single-line search summary badge with quick clear (✕) button in search field.
- **Manga Reading History**:
  - Persistent reading history with `MangaReadHistoryEntity` and `MangaReadHistoryManager`.
  - Unified history screen with dedicated Video and Manga tabs.

### 2. Manga Chapter Sorting (1 to N) and Mihon/Tachiyomi Parity
- **Default Chapter Order (1 → N)**:
  - Chapter lists now default to ascending reading order (`ChapterSortMode.OldestFirst`, 1 → N) instead of descending order.
  - Persistent DataStore settings with per-manga overrides (`manga_sort_override_<hash>`).
  - Dropdown sort menu replacing cycling buttons (1→N, N→1, Recently updated).
  - Reader previous/next navigation and end-of-chapter cards strictly follow reading order with unnumbered chapter resolution (`MangaChapterNumberResolver`).
- **Mihon Adaptations**:
  - **Chapter Display Mode**: Toggle between Chapter Number and Chapter Title.
  - **Chapter Bookmarking (★)**: Bookmark individual chapters and filter bookmarked chapters.
  - **Skip Read Chapters & Auto-Advance**: Automatically advance to next chapter on page finish and optionally skip already read chapters.
  - **Crop Borders**: Trim page borders in webtoon and vertical readers on mobile and TV.
  - **Offline CBZ Chapter Downloader**: Download chapters as offline CBZ archives with automatic offline fallback reading.
  - **Manga Library**: Room v34 database migration (`manga_library` table, `MIGRATION_33_34`) with favorite manga library and full Android TV parity.
