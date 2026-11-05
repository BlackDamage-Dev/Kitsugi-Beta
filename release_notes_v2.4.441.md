# Kitsugi Beta v2.4.441

## 🇹🇷 Türkçe

### 🔎 "Oku" Butonu Artık Seçtiğin Dilde Arıyor
Başlık dili **İngilizce** seçili olmasına rağmen "Oku" butonu ekranda/veritabanında bulunan adla arama yapabiliyordu; çünkü ayrı bir arama dili konsepti yoktu.
- **Yeni: Kaynak Arama Dili ayarı** (`Ayarlar → Genel → Manga Arama Dili`): İngilizce ad / Romaji / Orijinal ad / Yerel ad.
- **"Başlık dilini takip et"** anahtarı: Açıkken arama dili, uygulamada seçili başlık diline otomatik uyar (varsayılan). Kapalıyken sabit seçilen dil kullanılır.
- **Tek merkezli sorgu motoru (`MangaSearchQueryBuilder`)**: Öncelik sırası seçilen dile göre kurulur; ilk ad bulunamazsa yedekler denenir (İngilizce → Romaji → Orijinal → Alternatif adlar). Arama **asla boş isimle** gitmez.
- **Tüm yüzeylerde tutarlı:** Detay ekranı "Oku" butonu, eşlemesi olmayan kitaplık kayıtları, API/keşif detay sayfası ve kaynak tarayıcısı aynı altyapıyı kullanır.
- **Kaynak tarayıcısında görünür kontrol:** Arama çubuğunun üstünde dil çipi (`Arama: İngilizce ad`). Dokunulduğunda sıradaki dile geçer ve kalıcı kaydedilir.
- **Kaynak bazlı yedek arama:** Seçili adla sonuç gelmezse kaynak, sıradaki dildeki adla (en fazla 3 yedek) tekrar denenir — boş ekran yerine doğru sonuçlar gelir.
- **TV Deneyimi:** Android TV Manga Tarayıcısı çoklu dil alternatifleriyle otomatik arar ve arama dili rozetini gösterir.

### 🗂️ Bölüm Listesi Sırası — Artan Sırada Gelen Kaynaklar
Bazı kaynaklar bölümleri **1 → N** sırasıyla döndürdüğünde, "En yeni üstte" seçiliyken 1. bölüm en üstte kalabiliyordu.
- `MangaChapterOrdering` bölüm numaralarına bakarak listenin artan mı azalan mı geldiğini tespit eder (`looksAscending`).
- Artan sırada gelen kaynaklarda liste otomatik olarak "en yeni üstte" olacak şekilde düzeltilir.
- Numarasız bölümler ("Duyuru") her durumda en altta kalır.

### 🎛️ Dil Çipi Gerçek Zamanlı Yeniden Arama
- "Oku" akışı, serinin **tüm adlarını** (`MangaTitleSet`) tarayıcıya taşır.
- Çipe dokunulduğunda arama **yeni dilde anında yeniden** tetiklenir (örn. İngilizce → Orijinal ad) ve seçim kaydedilir.

### 🐞 Bölüm Geçişi Yönü (Okuma Sırası) Düzeltildi
Bazı kaynaklar bölüm listesini eskiden yeniye verdiğinde okuyucudaki sonraki/önceki bölüm geçişleri ters çalışabiliyordu.
- `MangaChapterSequence` bölümleri numaraya göre artan okuma sırasına (1 → N) dizer; numara yoksa bölüm adından çözümlenir (`MangaChapterNumberResolver`), yine yoksa kaynak sırasının tersi kullanılır.
- Sonraki/önceki bölüm düğmeleri ve bölüm sonu yönlendirmesi artık kaynaktan bağımsız olarak daima doğru yöne ilerler.

### 🔧 Kalite ve Uyumluluk
- Yeni `R.string` anahtarlarının tamamı Türkçe ve İngilizce için %100 simetrik eklendi.
- `manga_search_title_language` ve `manga_search_follow_title_language` ayarları bulut senkronizasyonuna dahil edildi.
- Pager performans denetimi temiz; `androidx.compose.material.icons` bağımlılığı sıfır.

---

## 🇬🇧 English

### 🔎 The "Read" Button Now Searches in Your Selected Language
Previously, the "Read" button could search using whichever title happened to be in the database, as there was no dedicated search language preference.
- **New Setting:** `Settings → General → Manga Search Language` (English / Romaji / Native / Localized).
- **"Follow title language"** toggle: Enabled by default, keeping search aligned with the app's selected title language.
- **Unified Query Engine (`MangaSearchQueryBuilder`)**: Builds candidate queries prioritized by chosen language with graceful fallbacks (English → Romaji → Native → Synonyms). Never queries with an empty string.
- **Consistent Across Surfaces:** Detail page Read button, library entries without mapping, API/discovery detail pages, and the source browser all share this engine.
- **Interactive Language Chip:** Chip above search bar (`Search: English title`) allows quick cycling through languages on tap.
- **Per-Source Fallback Search:** Tries alternative titles (up to 3) if primary query yields no results on a source.
- **TV Interface:** Android TV Manga Browser auto-searches across query alternatives and displays active search language badge.

### 🗂️ Chapter List Order — Ascending Source Detection
Sources returning chapters in 1 → N order previously caused Chapter 1 to appear on top even when "Newest first" was selected.
- `MangaChapterOrdering` detects natural order via chapter numbers (`looksAscending`).
- Ascending chapter feeds are automatically adapted to match the selected sort mode.
- Non-numbered chapters (e.g. notices/announcements) reliably stay at the end.

### 🎛️ Real-Time Language Switching
- Full set of titles (`MangaTitleSet`) is passed to the browser.
- Tapping the language chip re-triggers active search in the selected language immediately.

### 🐞 Chapter Navigation Order & Next/Prev Fix
Sources listing chapters in ascending order could cause chapter navigation to jump backwards.
- `MangaChapterSequence` establishes true reading progression (1 → N), resolving chapter numbers from titles via `MangaChapterNumberResolver`.
- "Next chapter" and "previous chapter" actions reliably advance according to natural reading sequence.

### 🔧 Reliability & Verification
- 100% key parity across Turkish and English localization strings.
- Cloud sync support for all newly introduced settings.
- Zero `androidx.compose.material.icons` usage and passed pager performance audit.
