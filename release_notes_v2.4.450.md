# Kitsugi Beta v2.4.450

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Manga Bölüm Listesi: Çevirmen Gruplama (`MangaDetailViewModel.ChapterGroup`)
- **Aynı Bölüm Numaralı Çevirileri Tek Satırda Birleştirme**:
  - Farklı eklenti ve çevirmenlerden (AnimeWho, WebtoonTR, Tempest Fansub vb.) gelen aynı bölüm numaralı kopyalar artık listede mükerrer görünmez; tek bir satırda gruplanır.
  - Kaynağın numara vermediği (-1) bölümler başlıktan akıllıca çözümlenir (ör. "Bölüm 1" → 1). Numarası bulunamayan özel bölümler (ör. "Oneshot") diğerleriyle birleşmeden bağımsız kalır.
  - Grup satırında "X çeviri" rozeti ve çevirmen adları yer alır. Tek çevirmenli bölümler eskisi gibi doğrudan okuyucuyu açar.

### 2. Çevirmen Seçim Penceresi (`ChapterTranslatorPickerSheet`)
- **Seçilebilir Çevirmen Alt Sayfası**:
  - Çok çevirmenli grup satırına dokunulduğunda açılan alt pencerede tüm çeviriler ayrı ayrı listelenir; kullanıcı istediği çevirmeni seçtiğinde okuyucu o bölümle başlar.
  - Her çeviri kartında okuma durumu, sayfa ilerlemesi, indirme rozeti ve yer imi yönetimi mevcuttur.

### 3. Okuma İlerlemesi ve Devam Etme Tutarlılığı
- **Çevirmen Bazlı İlerleme Takibi**:
  - Sayfa ilerlemesi ve okundu işaretleri her çevirmen için bağımsız olarak saklanır.
  - Grup satırında tüm çeviriler okunmuşsa okundu tiki, devam edilen bir çeviri varsa kaldığı sayfa bilgisi (örn. "Sayfa 5/20") gösterilir.
  - "Devam Et" ve "Baştan Oku" butonları kaydedilen çeviri URL'sine göre doğru bölümü açmaya devam eder.

### 4. Kod Kalitesi ve Güvenilirlik
- **Zero Material Icons**: `app/src/main` genelinde `androidx.compose.material.icons.*` sıfırlanmış olup `KitsugiIcons as Icons` standardı korunmuştur.
- **%100 Dil Simetrisi**: Türkçe ve İngilizce dil kaynaklarında tüm çevirmen ve grup anahtarları eksiksiz eşitlenmiştir.
- **Doğrulanmış Kararlılık**: Yeni `MangaDetailChapterGroupingTest` birim testleri ve sekme performans kuralları başarıyla doğrulanmıştır.

---

## 🇬🇧 English Release Notes

### 1. Manga Chapter List: Translator Grouping (`MangaDetailViewModel.ChapterGroup`)
- **Unified Chapter Variants**:
  - Chapters sharing the same chapter number across multiple scanlators/extensions (e.g. "Chapter 1" x3) are now consolidated into a single row.
  - Chapters missing explicit numbers (-1) are resolved from their title via `ChapterRecognition`. Unnumbered specials (e.g. "Oneshot") remain distinct.
  - Group rows display an "X translations" badge and scanlator names. Single-translator chapters open directly without extra steps.

### 2. Translator Picker Sheet (`ChapterTranslatorPickerSheet`)
- **Interactive Translator Selection**:
  - Tapping a multi-translator chapter row opens a bottom sheet listing all available translations for that chapter. Selecting any translation launches the reader with that specific source.
  - Each variant card features individual reading status, page progress, download indicators, and bookmark actions.

### 3. Reading Progress & Resume Fidelity
- **Per-Translator Reading Progress**:
  - Reading progress and completion checkmarks are tracked independently per translator.
  - Group rows show a checkmark when all translations are read, or active page progress (e.g. "Page 5/20") when a translation is in progress.
  - "Resume" and "Read from Start" continue to respect the exact recorded chapter URL and translation.

### 4. Code Quality & Localization
- **Zero Material Icons**: Zero imports of `androidx.compose.material.icons.*` across `app/src/main`, fully standardized on `KitsugiIcons as Icons`.
- **100% Localization Parity**: Synchronized Turkish and English language resources.
- **Verified Stability**: Validated through new `MangaDetailChapterGroupingTest` unit tests and pager performance guards.
