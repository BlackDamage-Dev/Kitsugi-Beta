# PLAN/TASK — Manga Bölüm Detay · Okuyucu · Geçmiş Sayfası

**Tarih:** 2026-11-06
**Dal:** `arena/f6a2450e-kitsugi-beta` (taban: `ab81c9e`)
**Kapsam:** Manga detay sayfası, manga okuyucu, manga arama (gözat), Geçmiş sayfası, TV paralelliği

---

## 1) Kullanıcı istekleri (5 ekran görüntüsü ile)

| # | İstek | Durum |
|---|---|---|
| 1 | Manga okuyucuda aşağı kaydırınca "yukarı çık" düğmesi — **Keşfet sayfasındakiyle birebir aynı** | ✅ Tamam |
| 2 | Okuyucuda bölüm görsellerine tıklayınca **görsel galerisi** açılması | ✅ Tamam (uzun basış + alt/üst bar düğmesi) |
| 3 | Bölüm detay sayfasında **kaydırmaya duyarlı üst bar** (2–3. görsellerdeki mantık) | ✅ Tamam |
| 4 | Okuyucuya **video eklentisi izleme sayfası tasarımı** (4–5. görseller) | ✅ Tamam |
| 5 | **Geçmiş** sayfasında manga/novel okumalarının da kaydı; izlenen ↔ okunan ayrımı | ✅ Tamam |

Ek olarak (kullanıcı geri bildirimi): "boşa yer kaplayan blok yok, her şey derli toplu, eksik olan eklenmiş olsun".

---

## 2) Task listesi ve durumu

### A. Veri katmanı
- [x] `manga_read_history` tablosu (`MangaReadHistoryEntity.kt`) — anahtar `mangaUrl|chapterUrl`
- [x] Room **v30** + `MIGRATION_29_30` (`KitsugiDatabase.kt`)
- [x] `app/schemas/.../30.json` şema dosyası
- [x] `MangaReadHistoryManager.kt` — IO + `Mutex`, 300 kayıt sınırı, hata yutulur, açılışta bloklamaz
- [x] `ChapterSorting.kt` — Yeni→Eski / Eski→Yeni / Son güncellenen (kararlı sıralama)
- [x] `MangaChapterFilter.kt` — `ChapterStatusFilter` (Tümü / Okunmamış / Okunan / Devam eden)

### B. Bölüm detay sayfası
- [x] `KitsugiCollapsibleTopBar.kt` — ortak süzülen üst bar bileşeni (eklenti izleme sayfasıyla **aynı kod**)
- [x] `MangaDetailTopBar.kt` — başlık + yazar · kaynak · bölüm sayısı alt satırı + geri + yenile
- [x] Sticky "Bölümler (N)" başlığı: `⇅` sıralama · `✓` okunanları filtrele · `📄/✕` hızlı arama · `✓✓` toplu işaretle · `↻` yenile
- [x] `MangaChapterTools.kt` — bölüm arama alanı, "Okunmamış" düğmesi, kaynak/tarayıcı çipleri
- [x] Filtre boş sonuç → "Bu filtrelere uyan bölüm yok" + **Filtreleri temizle**
- [x] Bölüm satırı: okundu soluklaşması, yarım kalan bölümde ilerleme çubuğu + `Sayfa 12/40`, tik ile okundu işaretleme
- [x] Keşfet ile aynı **yukarı çık FAB'ı**
- [x] Okuyucudan dönüşte yalnızca ilerleme tazeleme (`refreshTick`) — ağ isteği yok
- [x] Hero yüksekliği ekrana göre (200 / 260 / 292dp), kapak oranı 1.42×
- [x] Açıklama 4 satır + "Devamını göster / Daha az" (yalnızca taşıyorsa — `hasVisualOverflow`)

### C. Okuyucu
- [x] Tam ekran **sayfa galerisi** (adaptif ızgara, sayfa numaraları, seçili sayfa vurgusu, açılışta okunan sayfaya kaydırma)
- [x] Alt barda **galeri** düğmesi + üst barda ▦ + sayfaya **uzun basış**
- [x] Galeri küçük resimleri disk önbelleğinden (ek indirme yok), `remember` ile sabitlenmiş model
- [x] Hatalı sayfaya dokunma → yeniden dene
- [x] Keşfet ile **birebir aynı** yukarı çık FAB'ı (`KitsugiScrollToTopFab`, `bottomBarHeight` parametresi)
- [x] **Bölüm seçici** (üst barda bölüm adı + ⌄) — okuyucudan çıkmadan bölüm değiştirme
- [x] **Bölüm sonu geçiş kartı** — menü kapalıyken son sayfada önceki/sonraki bölüm
- [x] **Ekranı açık tut** (`keepScreenOn`, Ayarlar → Okuyucu'da anahtar)
- [x] `ON_PAUSE` / `onDispose` → bekleyen ilerlemeyi **anında yaz** (`flushProgress`)
- [x] Ayarlar sheet'i %62 yükseklik + kaydırılabilir içerik
- [x] Alt bardaki yinelenen sayfa şeritleri kaldırıldı (3 satır → 2 satır, −32dp)

### D. Manga arama (gözat)
- [x] Üstteki 76dp bilgi kartı → tek satır kompakt şerit (kapak + başlık + "N sonuç · M kaynak")
- [x] Arama alanına **temizle (✕)** düğmesi
- [x] Kaynak çipleri, "Tümü" çipi, yükleniyor iskeletleri (mevcut davranış korundu)

### E. Geçmiş sayfası
- [x] `HistoryViewModel.kt` — izleme geçmişi reaktif, okuma geçmişi Room'dan
- [x] "Tümü / İzlenen / Okunan" sekmeleri + sayaçlar
- [x] Grup başlıkları (İzlenen · Okunan), kaynak rozeti, ilerleme çubuğu, tarih
- [x] Tek kayıt silme + sekme bazlı onaylı temizleme
- [x] Keşfet ile aynı yukarı çık FAB'ı

### F. TV paralelliği
- [x] `TvMangaReaderScreen` — `keepScreenOn` + `flushProgress` + alt HUD'da ince ilerleme çubuğu
- [x] `TvMangaDetailScreen` — bölüm sıralama + okunmamış filtresi, filtrelenmiş liste, ilerleme çubuğu, boş durum

### G. i18n
- [x] TR/EN anahtar paritesi tam (17.739 anahtar, mükerrer yok, eksik `R.string.*` yok)

---

## 3) Değişen / eklenen dosyalar

### Yeni (10)
```
COZUM_RAPORU_MANGA_OKUYUCU_GECMIS_2026-11-06.md
PLAN_TASK_MANGA_OKUYUCU_GECMIS_2026-11-06.md
app/schemas/com.kitsugi.animelist.data.local.KitsugiDatabase/30.json
app/src/main/java/com/kitsugi/animelist/data/local/MangaReadHistoryEntity.kt
app/src/main/java/com/kitsugi/animelist/data/local/MangaReadHistoryManager.kt
app/src/main/java/com/kitsugi/animelist/data/manga/ChapterSorting.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCollapsibleTopBar.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/history/HistoryViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaChapterFilter.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaChapterTools.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaDetailTopBar.kt
```

### Güncellenen (27)
```
app/src/main/java/com/kitsugi/animelist/AppRoot.kt
app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt
app/src/main/java/com/kitsugi/animelist/AppRootMangaPages.kt
app/src/main/java/com/kitsugi/animelist/AppRootSettingsExtras.kt
app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt
app/src/main/java/com/kitsugi/animelist/data/local/KitsugiDatabase.kt
app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt
app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt
app/src/main/java/com/kitsugi/animelist/ui/app/AppNavigationState.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPreferencesSettingsDialog.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiScrollToTopFab.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaPageItem.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaPagerReader.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaWebtoonReader.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/settings/SettingsScreenParameters.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/stream/StreamScreenContent.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaDetailScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaReaderScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
```

**Toplam:** 37 dosya · +4.706 / −521 satır

---

## 4) Commit geçmişi (14 commit)

```
d4c65c4  Manga okuyucu + geçmiş: sayfa galerisi, kaydırmaya duyarlı barlar, okuma geçmişi
acf73b5  docs: manga okuyucu + geçmiş düzenlemesi çözüm raporu
07e3ef8  Manga okuyucu incelemesi: kompakt sayfa şeridi, tam ekran galeri, uzun basış, ekran açık tutma ayarı
26256ce  TV manga detay: sıralama/okunmamış filtreleri, ilerleme çubuğu; açıklama kısaltma; şema 30
47a3994  Detay sayfası: sticky başlıkta hızlı bölüm arama, güvenli sıralama kaydırma
25f5f07  Okuyucu alt barı sadeleştirildi: yinelenen sayfa şeridi kaldırıldı, galeri kısayolu, bölüm sonu ipucu
9431e8a  Galeri: hatalı sayfa için yeniden dene, tam siyah zemin
248141a  Okuyucu ayar sayfası: daha kısa sheet + kaydırılabilir içerik
0185b8b  Geçmiş: okunan kayda dokunma yorumu netleştirildi
18ad49a  Okuyucuya bölüm seçici eklendi
8e9e693  Detay: görünen bölümleri toplu okundu/okunmadı işaretleme
5ce06df  Manga arama: arama alanına temizle düğmesi
56dcecf  Rapor: inceleme turu
adaa37e  Rapor düzeltmesi
```

---

## 5) Risk / dikkat noktaları

1. **Room göçü:** v29 → v30 geriye dönük uyumlu; `manga_chapter_progress` verisi korunur. Yeni tablo yalnızca eklenir.
2. **`stickyHeader` deneysel API:** mevcut detay sayfalarındaki `@OptIn(ExperimentalFoundationApi::class)` deseni korundu.
3. **`keepScreenOn`** yalnızca okuyucu açıkken açılır, `onDispose`'ta kapatılır (pil etkisi yok).
4. **`flushProgress`** `viewModelScope` kullanır; okuyucudan çıkışta ViewModel henüz temizlenmediği için yazma tamamlanır.
5. **TV** tarafında metin araması yok (kumanda ile klavye yok) → yerine sıralama + okunmamış düğmeleri konuldu.

---

## 6) Doğrulama

- [x] Tüm değişen Kotlin dosyalarında blok/parentez dengesi (`{`/`}` ve `(`/`)`) temiz
- [x] `strings.xml` XML parse edilebilir, mükerrer anahtar yok, TR/EN setleri eşit
- [x] Kodda kullanılan `R.string.*` anahtarlarının tamamı kaynakta mevcut
- [x] Room `30.json` şeması: CREATE SQL + indeksler `MIGRATION_29_30` ile birebir
- [ ] **`./gradlew :app:assembleDebug`** — bu ortamda Android SDK/Gradle olmadığı için çalıştırılamadı; yerelde doğrulanmalı
