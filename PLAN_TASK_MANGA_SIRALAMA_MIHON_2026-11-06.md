# PLAN & TASK — Manga Bölüm Sıralaması (1 → N) + Mihon/Tachiyomi Uyarlamaları
**Tarih:** 2026-11-06 · **Dal:** `arena/f6a2450e-kitsugi-beta` · **Temel commit:** `220bda4`

---

## 1. Talep → Görev eşlemesi

| # | Kullanıcı talebi | Yapılan görev | Durum |
|---|---|---|---|
| A | "Bölüm listesi 386'dan başlıyor, **1'den** başlamalı" | Varsayılan sıralama `NewestFirst` → `OldestFirst` (1 → N); `rememberSaveable` yerine kalıcı tercih | ✅ |
| B | "Bölüm sıralama mantığı düzeltilsin (asıl sorun bu)" | Sıralama tek kaynaktan (`ChapterSortMode.DEFAULT`), manga bazlı ezme, numara çözümleme, okuyucu geçişleri sıralamaya uydu | ✅ |
| C | "Açık kaynak manga uygulamalarını **eksiksiz** araştır" | Mihon, TachiyomiSY, MihonDesktop, miharchy, Kotatsu taraması (§2) | ✅ |
| D | "İyi olan **her** özelliği Kitsugi'ye uyarla" | Uyarlanan 16 özellik (§3), uyarlanmayanların **teknik gerekçesi** (§4) | ✅ |
| E | "Kestirip atma, adam akıllı yap" | Hiçbir özellik "gereksiz" diye reddedilmedi; her biri için ya uygulama ya somut mühendislik engeli yazıldı | ✅ |

---

## 2. Araştırma kapsamı (kaynak → bulgu)

| Kaynak | İncelenen başlıklar | Kitsugi'ye etkisi |
|---|---|---|
| **Mihon** `reader-settings` | Okuma modu, sayfa geçişleri, kenarlık kırpma, tap zones, **skip read / skip filtered / skip duplicate**, chapter transition, ekran açık tutma, sayfa numarası | Kenarlık kırpma, okunanı atla, otomatik bölüm geçişi eklendi |
| **Mihon** derin wiki (`4.3 Reader configuration`) | Tercih anahtarları, global + **manga bazlı** ezme | Manga bazlı sıralama ezmesi (DataStore hash anahtarı) |
| **TachiyomiSY** | Kitap kategorileri, gelişmiş sıralama, devam et düğmesi, önbellek, kaynak birleştirme, sayfa ön yükleme | Kitaplık + kategoriler, devam et, sıralama modları |
| **Tachiyomi v0.9/0.15 changelog** | **Bölüm yer imi (★)**, tarayıcı filtresi, eksik bölüm göstergesi, yinelenen bölüm, çoklu okundu işaretleme, indirme rozetleri | Yer imi, scanlator çipleri, eksik/yinelenen bölüm uyarısı, toplu işaretleme |
| **miharchy PR #135/#140** | Display mode (numara/başlık), tri-state filtreler, mark-previous-read, sıralama flip | Görünüm modu, "öncekileri okundu işaretle" (uzun basış) |
| **MihonDesktop** | 9 sıralama + 6 filtre, toplu okundu, fit modları, çift sayfa, ön yükleme | 5 kitaplık sıralaması, toplu okundu |
| **Mihon issue #1698** | "Kaldığım yere dön" talebi; önerilen çözüm jest değil **düğme + sıralama** | "Kaldığım yere git" düğmesi |

---

## 3. Uyarlanan özellikler (dosya → özellik)

| Özellik | Dosya(lar) |
|---|---|
| Varsayılan sıralama 1 → N, `ChapterSortMode.DEFAULT` | `data/manga/ChapterSorting.kt` |
| Sıralama **menüsü** (1→N / N→1 / son güncellenen) — döngü değil | `components/MangaChapterTools.kt`, `MangaDetailScreen.kt` |
| Kalıcı tercih + manga bazlı ezme | `SettingsDataStore.kt` (`manga_chapter_sort`, `manga_sort_override_<hash>`) |
| Bölüm görünümü: numara / başlık | `ChapterSorting.kt` (`ChapterDisplayMode`), detay satırı |
| Bölüm **yer imi ★** + yalnızca yer imlileri filtrele | `MangaDetailViewModel.toggleBookmark`, `MangaChaptersHeader` |
| **Eksik bölüm** göstergesi | `missingChapterNumbers()`, `MangaChapterProgressSummary` |
| **Yinelenen bölüm** gizleme | `duplicatedChapterNumbers()`, çip |
| **Öncekileri okundu işaretle** (uzun basış) | `MangaDetailViewModel.markPreviousRead` |
| **Kaldığım yere git** düğmesi | `MangaChaptersHeader.onJumpToCurrent` |
| Okunmamış sayacı + ilerleme özeti | `MangaChapterProgressSummary` |
| Okuyucu: sonraki/önceki bölüm **sıralamaya uyar** | `MangaReaderViewModel.orderedChapters()` |
| Okuyucu: **okunanı atla** | `manga_skip_read_chapters` + `nextChapter()` |
| Okuyucu: **bölüm bitince otomatik geçiş** | `manga_auto_advance_chapter` + `LaunchedEffect` |
| **Kenarlık kırpma** (crop borders) | `MangaPageItem.cropBorders` (%4 + clipToBounds) |
| **Bölüm indirme (CBZ)** + çevrimdışı okuma | `data/manga/download/MangaChapterDownloader.kt`, `MangaPageLoaderV2.archiveResolver` |
| **Manga kitaplığı**: kategoriler, 5 sıralama, ilerleme, indirme rozeti | `MangaLibraryEntity.kt`, `MangaLibraryViewModel/Screen.kt`, Room **v31** |
| Bölüm numarası eksikse **başlıktan çözümle** | `withResolvedNumbers()` (`ChapterRecognition`) |
| TV paritesi (sıralama, kitaplık, crop borders) | `TvMangaDetailScreen`, `TvMangaBrowseScreen`, `TvNavigationState` |

---

## 4. Uyarlanmayanlar ve **teknik** gerekçeleri

Bunlar "gereksiz" olduğu için değil, mevcut mimaride karşılığı olmadığı için bu turda yapılmadı;
hepsi için izlenecek yol yazıldı.

| Özellik | Gerekçe / izlenecek yol |
|---|---|
| **İzleyici (tracker) senkronu** (MAL/AniList/Kitsu) | Kitsugi'de manga tarafı için tracker kimliği **yok**: `MangaDetails` içinde anilistId/malId alanı bulunmuyor; eşleştirme `MangaMappingEntity` üzerinden medya kimliğiyle yapılıyor. Doğru uygulama: eşleşmiş medya varsa `MediaEntry` ilerlemesini güncellemek → ayrı tur (§5). |
| **Kaynak birleştirme (merge sources)** | Liste modeli `MangaChapter.mangaUrl` tek kaynağa bağlı; birleştirme bölüm URL'lerini kaynak bazında ayrıştırmayı ve okuyucuda kaynak değiştirmeyi gerektirir (indirme + ilerleme anahtarları da değişir). Riskli refactor; ayrı tur. |
| **Çift sayfa / wide-page split** | `MangaPagerReader` sayfa başına `ZoomableAsyncImage` çiziyor; çift sayfa iki sayfayı tek `Page`'e birleştirmeyi (bitmap birleştirme) gerektirir → bellek maliyeti. Ön koşul: `MangaPage` çoklu kaynak desteği. |
| **Kaynak geçişi (migration)** | Kaynak başına URL şeması farklı; otomatik geçiş için başlık eşleştirme + yeni URL üretimi gerekir (`MangaTitleMatcher` var, URL üretimi yok). |
| **E-Hentai/ExHentai özel ayarları** | Kitsugi'de bu kaynak yok; eklenti (Mihon extension) olarak gelirse jenerik ayar altyapısı zaten mevcut. |
| **Autoscroll** | Webtoon okuyucusunda `LazyListState` ile mümkün; ancak sayfa yükleme hızıyla senkron otomatik kaydırma düşük uçlu cihazlarda takılma üretir. Ölçüm yapılmadan eklenmemeli (yanlış yapılmış bir autoscroll okumayı bozar). |
| **Kitaplık toplu güncelleme (background refresh)** | WorkManager işi + kaynak başına sayfalama gerekir; ayrı mimari tur. |

---

## 5. Doğrulama

Ortamda **JDK/Android SDK yok**; bu yüzden derleme yerine statik doğrulama yapıldı:

1. **String parity:** `values` ve `values-en` → **17.650** anahtar, iki dosyada da **birebir aynı** (`set(a) ^ set(b) == ∅`).
2. **Eksik string yok:** kodda geçen tüm `R.string.*` (5.010 kullanım) tanımlı (17.807 tanım) — `MISSING: []`.
3. **Parantez/süslü parantez dengesi:** değişen 36 dosyada `{`/`}` ve `(`/`)` farkı **0**.
4. **Room şeması:** `31.json` üretildi, `MIGRATION_30_31` tablo + 2 indeks oluşturuyor; `exportSchema = true` korunuyor.
5. **Liste indeksi regresyonu:** detay ekranına eklenen öğeler yüzünden sticky başlık eşikleri kaymıştı — kitaplık düğmesi ve ilerleme özeti **ayrı öğe olmaktan çıkarıldı** (Oku satırı + araç çubuğu), liste yapısı orijinal 5 öğeye döndü.

---

## 6. Commitler (bu tur)

```
6094646 fix(manga): bölüm listesi varsayılanı 1 → N (artan), kalıcı sıralama tercihi
20f5fcf feat(manga): bölüm indirme (CBZ), yinelenen bölüm filtresi, uzun basışla öncekileri okundu işaretle
739aedd feat(manga): indirilen bölümler okuyucuda çevrimdışı açılır (ağ yok)
a8bf873 feat(manga): manga kitaplığı — kategoriler, sıralama, ilerleme, indirme rozeti
db7e171 feat(manga): kenarlık kırpma (crop borders) — okuyucu panelinde anahtar
c0f9982 feat(tv-manga): kenarlık kırpma TV okuyucusuna da geçti
0866fb8 feat(tv-manga): kitaplık TV'ye bağlandı (browse başlığı + ayarlar satırı + geri tuşu)
59a8b22 fix(manga): kitaplık düğmesi Oku satırına taşındı — sticky başlık/üst bar indeks kayması giderildi
468321e fix(manga): ilerleme özeti araç çubuğuna taşındı — liste yapısı orijinal indekslere döndü
```

**Kapsam:** 36 dosya, +4.209 / −128 satır (bu tur). Ayrıca önceki turun düzeltmesi:
`SettingsDataStore.setMangaKeepScreenOn` içindeki `context.dataStore` (tanımsız) → `context.settingsDataStore`.

---

## 7. Kullanıcıya görünen davranış değişiklikleri

1. **Bölüm listesi artık 1'den başlar** (Berserk → 1, 2, 3 … 386). Tercih kalıcıdır, uygulama yeniden açılsa da bozulmaz.
2. Sıralama düğmesi **menü** açar; "Yeni → Eski" seçilirse başlıkta ve okuyucuda tutarlı kalır.
3. Detay ekranında: ★ yer imi, indirme rozeti, okunmamış sayacı, eksik/yinelenen bölüm uyarısı, "kaldığım yere git".
4. Okuyucu: sonraki/önceki bölüm doğru yönde, okunanı atlama, otomatik geçiş, kenarlık kırpma.
5. Manga sekmesinde **Kitaplığım** girişi; kategoriler, sıralama, ilerleme ve indirme rozeti.
6. Ayarlar → Okuyucu: Bölüm sıralaması, Bölüm görünümü, Okunanı atla, Otomatik geçiş.
