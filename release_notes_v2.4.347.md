# Kitsugi Beta v2.4.347

## 🇹🇷 Türkçe

### ⚡ Listem Arama ve Yazarken Donma Optimizasyonu (MyList Search Pipeline & Index)
- **Arka Planda Arama:** Binlerce kaydı içeren Listem ekranında her harf girişinde ana iş parçacığında (UI) çalışan ağır filtreleme, NFKC/regex normalizasyonu ve Levenshtein hesaplaması kaldırıldı. Arama `Dispatchers.Default` üzerine taşındı.
- **Yazma Gecikmesi & İptal:** Tuş vuruşları için 180 ms yazma gecikmesi (`debounce`) eklendi; `transformLatest` ile yeni bir harf yazıldığında eski arama işlemi derhal iptal ediliyor. Temizleme ve filtre değişimleri ise beklemeden anında çalışıyor.
- **Arama İndeksi (`MyListSearchIndex`):** Başlıklar, alternatif adlar ve fuzzy normalizasyonları önbelleğe alınıyor; ızgara çiziminde tüm listeyi tekrar tekrar kopyalayan `chunked()` yerine dinamik satır hesabı (`items(count = ...)`) getirildi.
- **Kısa Sorgu Koruması:** 1–2 harfli kısa aramalarda listeyi gereksiz yere şişiren toleranslı eşleşmeler sınırlandırıldı; 2.603 kayıtlı testler dahil 46 birim testiyle doğrulandı.

### 🛡️ LazyVerticalGrid Sonsuz Yükseklik Çökmesi Düzeltmesi (Infinite Max Height Fix)
- **Kök Neden Giderildi:** `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` çökme hatası çözüldü.
- **Kesin Yükseklik Hesabı (`GridHomeContent`):** `wrapContentHeight()` yerine satır sayısından sonlu yükseklik hesaplayan `rememberGridHomeEmbedHeight` getirildi; `LazyColumn.item` içinde sonlu `height(...)` ile güvenli ölçüm sağlandı.
- **Profil Seçim Izgarası (`ProfileSelection`):** Olası sonsuz yükseklik ölçümünü engellemek için `heightIn(max = 520.dp)` sınırı eklendi.
- **Teşhis Sistemi (`KitsugiApplication`):** `:crash` sürecinin `StartupStage` iz dosyasını ezmesi engellendi; çökme anındaki gerçek aşama raporlara doğru yansıtılıyor.

### 📋 Hakkında Ekranı Sürüm Notları ve Önbellek Düzeltmesi (`ReleaseNotesCache`)
- **Sürüme Bağlı Önbellek:** Sürüm notu önbelleği kurulu uygulama sürümüne (`VERSION_NAME`) bağlandı. Eski uygulama sürümünden kalan bayat kayıtlar ve geleceğe ait zaman damgaları reddediliyor.
- **Kanonik GitHub Kaynağı:** Hakkında ekranındaki sürüm notları doğrudan GitHub Releases API'sinden okunuyor; gecikebilen Codeberg aynasından eski sürüm bilgisi çekilmesi önlendi. Güncelleme denetiminde ise Codeberg yedeği korunuyor.

---

## 🇬🇧 English

### ⚡ MyList Search Pipeline & Keystroke Freeze Optimization
- **Background Search Execution:** Offloaded heavy search filtering, NFKC/regex normalization, and fuzzy string distance calculations from the main UI thread to `Dispatchers.Default`.
- **Typing Debounce & Instant Cancellation:** Added a 180 ms typing debounce with `transformLatest`, ensuring in-flight searches are immediately cancelled upon new keystrokes. Clear actions and filter toggles respond immediately.
- **Search Pre-Indexing (`MyListSearchIndex`):** Precomputed title aliases and metadata keys. Replaced full-list `chunked()` collection copying in grid views with dynamic count-based row rendering (`items(count = ...)`).
- **Short Query Guard:** Constrained broad fuzzy matching for 1–2 character queries to avoid over-matching large lists, fully verified across 46 unit tests.

### 🛡️ LazyVerticalGrid Infinite Height Crash Fix
- **Root Cause Resolved:** Fixed `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints` when embedding vertical grids inside LazyLists.
- **Deterministic Grid Height (`GridHomeContent`):** Replaced unbounded `wrapContentHeight()` with row-count-based finite height calculation (`rememberGridHomeEmbedHeight`) inside `LazyColumn.item`.
- **Profile Selection (`ProfileSelection`):** Bounded grid height via `heightIn(max = 520.dp)`.
- **Crash Diagnostics (`KitsugiApplication`):** Prevented the `:crash` process from overwriting the `StartupStage` trace file, preserving the true crash stage in reports.

### 📋 About Screen Release Notes & Cache Fix (`ReleaseNotesCache`)
- **Version-Scoped Cache:** Tied release notes cache invalidation directly to installed app version (`VERSION_NAME`). Stale records from older versions and future-dated timestamps are discarded.
- **Canonical GitHub Source:** About screen fetches release notes strictly from GitHub Releases API, preventing stale mirror data from Codeberg from appearing as the latest release. Codeberg fallback remains active for APK update checks.
