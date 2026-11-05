# PLAN & TASK — Manga Arama Dili + "Oku" Akışı + Bölüm Sırası (v2.4.343)

**Tarih:** 2026-11-05
**Dal:** `arena/c81eefa5-kitsugi-beta`
**Sürüm:** 2.4.343
**Kapsam:** Manga / Manhwa / Manhua / Novel — arama dili, "Oku" akışı, bölüm listesi ve bölüm geçişi

---

## 1. Kullanıcı Şikâyeti (birebir)

> "Oku butonuna basınca uygulama kafasına göre bir başlıkla arıyor. Uygulama ayarlarında seçili dil (mesela İngilizce) neyse ona uygun aramalı. Ayarlarda manga/manhwa/manhua/novel için ayrı bir arama dili seçeneği yoksa, tüm kaynakların uyabileceği şekilde eklensin."

> "Manga ile ilgili tüm kısımları en ufak detayı bile atlamadan tek tek kontrol et."

---

## 2. Kök Neden Analizi

| # | Sorun | Kök neden |
|---|-------|-----------|
| 1 | "Oku" yanlış adla arıyor | Arama sorgusu **ekranda/DB'de görünen tek başlıktan** üretiliyordu. "Arama dili" diye bir kavram yoktu. `titleLanguage` ayarı yalnızca **görüntülemeyi** etkiliyordu, aramayı değil. |
| 2 | Ayar eksikliği | `AppSettings` içinde `mangaSearchTitleLanguage` / `mangaSearchFollowTitleLanguage` alanı **yoktu**. |
| 3 | Dil çipi işlevsiz | Tarayıcıda dil çipi yalnızca etiket değiştiriyordu; serinin diğer adları ekranda olmadığı için yeni dilde arama yapamıyordu. |
| 4 | Bölüm geçişi ters | `goToNextChapter()` listeyi **koşulsuz "yeniden eskiye"** kabul edip `idx - 1` kullanıyordu. Artan sırada veren kaynaklarda ileri/geri ters çalışıyordu. |
| 5 | Bölüm listesi ters | `ChapterSort.NewestFirst` kaynak sırasını **hiç kontrol etmeden** koruyordu; artan sırada gelen kaynakta 1. bölüm en üstte görünüyordu. |

---

## 3. Çözüm Mimarisi

```
Detay ekranı (kitap kaydı / API-keşif)
        │  MangaTitleSet(local, english, romaji, native, synonyms)
        ▼
MangaSearchQueryBuilder.buildQueries(titleLanguage, searchLanguage, followTitleLanguage, …)
        │  → öncelik sıralı aday listesi (asla boş değil)
        ▼
navState.openMangaBrowse(query = first, titleVariants = all, titleSet = …)
        ▼
MangaBrowseScreen → vm.search(query, variants)
        ▼
Kaynak başına: ilk ad → boşsa CanonicalMangaResolver → boşsa sıradaki dil (≤3 yedek, 20 sn timeout)
```

**Dil sırası kuralları**

| Seçim | 1. aday | 2. | 3. | 4. |
|-------|---------|----|----|----|
| `ENGLISH` | İngilizce ad | Romaji | Yerel | Orijinal |
| `ROMAJI` | Romaji | Yerel | İngilizce | Orijinal |
| `NATIVE` | Orijinal (CJK) | Romaji | Yerel | İngilizce |
| `LOCAL` | Yerel/çevrilmiş | İngilizce | Romaji | Orijinal |

- `followTitleLanguage = true` → ekranın başlık dili esas alınır (varsayılan).
- `followTitleLanguage = false` → sabit `mangaSearchTitleLanguage` kullanılır.
- CJK içeren `localTitle` "orijinal ad" sayılır (çift sayılmaz).
- `clean()`: kırpma, boşluk sadeleştirme, <2 karakter eleme; `distinctBy(lowercase)` tekilleştirme.
- `ascii()`: Türkçe karakter sadeleştirme (kaynaklar ASCII bekler).
- `containsCjk()`: 3040-30FF, 3400-4DBF, 4E00-9FFF, F900-FAFF, AC00-D7AF, FF65-FF9F.

---

## 4. Yapılan İşler (task listesi — hepsi tamam)

### 4.1 Veri / ayar katmanı
- [x] `data/manga/MangaSearchLanguage.kt` **(YENİ)** — enum + `MangaSearchQueryBuilder` (saf, ağ/UI bağımsız)
- [x] `data/manga/MangaTitleSet.kt` **(YENİ)** — serinin tüm adları + `queries(...)`
- [x] `data/settings/AppSettings.kt` — `mangaSearchTitleLanguage = "ENGLISH"`, `mangaSearchFollowTitleLanguage = true` + `AppSettings` getter'ları
- [x] `data/settings/SettingsDataStore.kt` — `manga_search_title_language`, `manga_search_follow_title_language` anahtarları, okuyucu, setter'lar, **bulut yedek allowlist'i**

### 4.2 Arama akışı (tüm yüzeyler)
- [x] `AppRoot.kt` — `onReadMangaClick` (kitap kaydı) → `MangaTitleSet` + `queries(...)`; `titleSet` navigasyona geçirildi
- [x] `AppRootDetailPages.kt` — API/keşif detay "Oku" (5 parametreli callback) → `MangaTitleSet`; ölü `readQueriesFor` / `buildMangaSearchQueries` kaldırıldı; kitap kaydı branch'i dil kararını merkezde veriyor
- [x] `ui/app/AppNavigationState.kt` — `mangaBrowseTitleVariants`, `mangaBrowseTitleSet`, `openMangaBrowse(…, titleSet)` + `close` temizliği
- [x] `AppRootMangaPages.kt` — `appSettings` parametresi, `initialSearchLanguage`, `initialQueryVariants`, `initialTitleSet`
- [x] `ui/screens/manga/MangaBrowseScreen.kt` — `initialTitleSet`, `activeSearchLanguage`, `languageOverride`, arama dili çipi (**dokununca yeni dilde gerçekten yeniden arar**), `lastSearchFingerprint` kontrolü
- [x] `ui/screens/manga/MangaBrowseViewModel.kt` — `lastSearchFingerprint`, `search(query, variants)`, `searchWithVariantFallback` (≤3 yedek)
- [x] `ui/screens/detail/MediaEntryDetailPage.kt` — `onSearchMangaByTitles` callback (iki call site), dil kararı tek yerde
- [x] `ui/tv/manga/TvMangaBrowseScreen.kt` — `initialQuery`, `initialQueryVariants`, `initialSearchLanguage` + otomatik arama + dil bilgisi

### 4.3 Ayarlar UI
- [x] `ui/components/KitsugiPreferencesSettingsDialog.kt` — `MangaReaderTab` içinde "Manga Arama Dili" bölümü (4 seçenek + "Başlık dilini takip et" anahtarı)
- [x] `ui/screens/settings/SettingsScreen.kt` / `SettingsScreenParameters.kt` / `AppRootSettingsExtras.kt` — parametre + callback + snackbar bağlantıları
- [x] `ui/screens/settings/SettingsSearch.kt` — ayarlar aramasına "manga arama dili" (TR/EN anahtar kelimeler)
- [x] `res/values/strings.xml` + `res/values-en/strings.xml` — 18 `manga_search_*` anahtarı (TR + EN)

### 4.4 Bölüm listesi / geçiş
- [x] `data/manga/MangaChapterOrder.kt` — `MangaChapterSequence` (okuma sırası) + `NewestFirst` için artan-sıra tespiti (`looksAscending`)
- [x] `ui/screens/manga/MangaReaderViewModel.kt` — `chaptersInReadingOrder()`, `goToNextChapter/goToPreviousChapter`, `hasNext/hasPrevious`, `nextChapterName` artık numaraya göre; ad yoksa `MangaChapterNumberResolver` devrede
- [x] Sayfa sayacı doğrulandı: `${visiblePage + 1}/${pages.size}` → **1'den başlıyor**

### 4.5 Testler (toplam 42)
- [x] `MangaSearchQueryBuilderTest.kt` (16)
- [x] `MangaChapterSequenceTest.kt` (12)
- [x] `MangaTitleSetTest.kt` (10)
- [x] `MangaChapterOrderingTest.kt` (+4 yeni)
- [x] `app/build.gradle.kts` — sürüm `2.4.343`

---

## 5. Doğrulama Durumu

| Kontrol | Durum |
|---------|-------|
| JDK / Android SDK / Gradle | ❌ **Ortamda yok → derleme ve test çalıştırma YAPILAMADI** |
| Brace/paren dengesi (tüm değişen `.kt`) | ✅ HEAD ile aynı (yeni bozulma yok) |
| `strings.xml` XML parse (TR + EN) | ✅ Temiz |
| Kullanılan tüm `R.string.*` iki dilde mevcut | ✅ |
| Test mantığının Python ile simülasyonu | ✅ Sıralama/çözümleme beklentileri doğrulandı |
| Yinelenen import / yinelenen tanım taraması | ✅ Temiz (HEAD'de var olanlar hariç) |

> ⚠️ **Risk:** İlk gerçek derlemede çıkabilecek tip/import uyuşmazlıkları için `git diff` gözden geçirilmeli.

---

## 6. Commit Geçmişi

| Commit | Açıklama |
|--------|----------|
| `478b824` | Manga arama dili: Oku seçili dilde arar, dil çipi yeniden arar, bölüm geçişi numaraya göre |
| `c27271b` | TV tarayıcıya Oku parametreleri; gereksiz `jumpToPage` kaldırıldı |
| `14a218e` | Aynı sorgu farklı yedek listesiyle gelirse aramayı yenile |
| `68d695d` | TV'de arama dili bilgisi; bölüm sırası testlerine resolver senaryoları |
| `7699161` | Artan sırada gelen kaynaklarda "en yeni üstte" otomatik düzeltme |

Önceki tur (çift sayfa): `cf45c61` — v2.4.342.

---

## 7. Sıradaki Adımlar (öneri)

1. Gerçek derleme + `./gradlew testDebugUnitTest` (42 test).
2. Cihazda senaryo testi:
   - İngilizce başlık dili + eşlemesi olmayan manga → "Oku" → **İngilizce adla** arama.
   - Tarayıcıda çip → Orijinal ad → sonuçların değişmesi.
   - Artan sırada bölüm veren kaynak → listede en yeni üstte; ileri/geri doğru.
   - Webtoon (dikey şerit) + çift sayfa modu çakışması yok.
3. `release_notes_v2.4.343.md` içeriğini mağaza notlarına taşı.
