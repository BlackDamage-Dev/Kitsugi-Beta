# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🎬 Yayın (Kaynak) Ekranı — Üst Bar, Filtreler ve Tek Kaynak Paneli
- **Üst bar artık yumuşak hareket ediyor:** Kaynak ekranındaki başlık çubuğu, kaydırma mesafesine birebir bağlı eski `translationY` yöntemini bıraktı. Artık Listem ve detay sayfalarıyla **aynı ortak hareketi** kullanıyor: aşağı kaydırınca yumuşakça süzülüp çekiliyor, yukarı kaydırınca aynı easing ile geri geliyor. Parmak titremesi bara yansımıyor.
- **Yatay düzende sol ray senkronu:** Yatayda sol kontrol rayı elle taşınmıyor; bar ile aynı akışta hareket ettiği için gizlenme sırasında boş şerit kalmıyor.
- **Arama alanı ve filtre çipleri düzenlendi:** Kesilen uzun placeholder yerine kısa metin ("Kaynak veya video ara…"), tek satır ve küçük tipografi. Çipler eklenti ekranlarındaki **kompakt çip stiliyle** aynı; etiketler netleşti (Altyazı · Dublaj · 4K · 1080p+ · 720p · ⚡ Debrid) ve dar ray/genişliklerde taşmadan sarıyor.
- **Anlamsız "Tüm kaynaklar" kartı kaldırıldı:** Listede sürekli duran ve listenin üstünü kaplayan özet kartı artık yok.
- **Sıralama ve kaynak seçimi tek panelde:** Ayrı "Sıralama: kaynak sırası" satırı kaldırıldı; ikisi birlikte arama alanının yanındaki kompakt **Tune** butonundan açılan **"Kaynaklar ve sıralama"** alt panelinde toplandı. Panelde:
  - **Sıralama:** Kaynak sırası · Kalite (yüksekten düşüğe) · Boyut (büyükten küçüğe) — boyut sıralaması yeni.
  - **Eklenti listesi:** her eklentinin video sayısı, arama, yükleme durumu, hata/doğrulama durumu.
  - **Tek dokunuşla eklenti işlemleri:** hata veren veya boş dönen eklenti için **yenile**, doğrulama isteyen eklenti için **doğrula**, ayrıca "Hatalıları yenile" toplu işlemi ve doğrudan **Eklenti ayarları** kısayolu.
  - **Yalnızca sonuç verenler** anahtarı ile uzun eklenti listeleri kısalır.
- **Aktif filtre görünür:** Panelden tek bir eklenti seçildiğinde, arama alanının altında o eklentinin adı kompakt bir çip olarak görünür; dokununca filtre kalkar.

---

## 🇬🇧 English

### 🎬 Stream (Source) Screen — Top Bar, Filters and a Single Source Panel
- **Softer top bar motion:** The stream screen header no longer follows scroll distance 1:1 with `translationY`. It now uses the **same shared scroll-aware header motion** as My List and detail pages: it slides away softly on scroll-down and returns with the same easing on scroll-up, without finger-jitter artifacts.
- **Landscape rail stays in sync:** The left control rail is no longer translated by hand; it moves with the header, so no empty strip is left behind when the bar collapses.
- **Search field and filter chips cleaned up:** Short placeholder ("Search sources or videos…"), single line, smaller typography, and the **compact chip style** used by the add-on screens (Altyazı · Dublaj · 4K · 1080p+ · 720p · ⚡ Debrid) that wraps instead of overflowing in narrow rails.
- **Removed the redundant "All sources" card:** The always-visible summary card that covered the top of the list is gone.
- **Sorting and source picking unified in one panel:** The separate "Sort: source order" row is gone; everything lives in a **"Sources & sorting"** bottom sheet opened from the compact **Tune** button next to the search field:
  - **Sorting:** Source order · Quality (highest first) · Size (largest first) — size sorting is new.
  - **Add-on list:** per add-on video counts, search, loading state, error / verification state.
  - **One-tap add-on actions:** retry a failing or empty add-on, verify an add-on that requires verification, a bulk "Retry failed" action, and a direct **Add-on settings** shortcut.
  - **"Only with results"** toggle to shorten long add-on lists.
- **Active filter is visible:** When a single add-on is selected, its name appears as a compact chip under the search field; tapping it clears the filter.
