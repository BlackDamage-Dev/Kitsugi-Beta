# Uygulama Geneli HD İkon Geçişi — Durum Raporu (2. tur, 2026-10-31)

> Soru: "Emin misin? En ufak mantık hatası, buton uyumsuzluğu, aynı butonun farklı logo
> kullanması, açılır-kapanır alanların ikonlarının eksik kalması… son kontrolleri yap."
>
> Aşağıdaki her madde **çalıştırılan betiklerin gerçek çıktısıyla** doğrulandı.
> Doğrulanamayan tek şey derleme: bu ortamda JDK/Gradle/Android SDK yok.

---

## 1. Bu turda bulunan GERÇEK hatalar (hepsi düzeltildi)

| # | Hata | Neden kritikti | Düzeltme |
|---|------|----------------|----------|
| 1 | `Icons.Dices` üst çubukta çağrılıyordu ama **ikon sette yoktu** | Derleme hatası | Lucide `dices` eklendi |
| 2 | `Icons.FlaskConical`, `Icons.Satellite` çağrılıyor, **sette yoktu** | Derleme hatası | İkonlar eklendi |
| 3 | `Icons.AlertTriangle`, `Smile/Laugh/Frown/Meh/Angry` eski adlarla çağrılıyordu | Lucide'de bu slug'lar artık yok → **boş yol verisi** | `TriangleAlert` + `Face*` ikonları + isim takma adları |
| 4 | 7 ikon dosyası **0 bayt** indirilmişti (sessiz başarısız fetch) | Görünmez ikon riski | Tümü yeniden indirildi, set yeniden üretildi |
| 5 | `ApiResultDetailComponents` içinde `▶` ve `🔍` metin olarak çiziliyordu | Buton/ikon değil glif | `Icons.Play` / `Icons.Search` |
| 6 | `▾` (KitsugiSeasonalFilterBottomSheet) ve `▼` (SubtitleSettingsPanel ×2) açılır-kapanır okları metindi | Açılır-kapanır kontrol kuralı ihlali | Animasyonlu `KitsugiChevronIcon(expanded=…)` |
| 7 | `★ Favori`, `★ 8.5`, `⭐ 7.9`, `👍 12`, `✓ Kuruldu`, `✗`, `📚`, `📭`, `❤`, `⚠️`, `📦`, `🔊`, `⚡`, `🔗` … 20+ yerde metin glif | Tema/HD kuralı ihlali | `KitsugiEmojiLabel` / `Icons.*` ile ikon |
| 8 | Galeri kategorisi emoji bloğu **3 dosyada kopyalanmıştı** | Aynı şeyin 3 farklı görünümü | Tek `galleryCategoryIcon()` |
| 9 | `KitsugiGenreItem` / `KitsugiGridSortOption` / `StudioSortOption` / `SearchGenre` alanları `emoji: String` idi | Tip güvenliği yok, eşleme kayabilir | `icon: ImageVector` |
| 10 | Sıralama seçeneklerinde A→Z / Z→A aynı `🔤` ikonunu kullanıyordu | Farklı eylem, aynı ikon | `ArrowDownAZ` / `ArrowUpZA` ayrı ikonlar |

---

## 2. Bu turda kurulan ortak mantık (tek kaynak)

- **`KitsugiIcons`** — 266 taban ikon / 522 özellik (alias'lar dahil), tamamı şeffaf arka planlı
  vektör, renk gömülü değil (`tint` ile temadan gelir).
- **`KitsugiEmojiIcons`** — 135 emoji/sembol → `ImageVector` eşlemesi. Veri katmanından gelen
  `"🔥 Popülerlik"`, `"🎬 Filmler"`, `"✅ Kuruldu"` gibi etiketler tek yerden çözülür.
- **`KitsugiEmojiLabel`** — emoji önekli etiketi HD ikon + metin olarak çizen tek birim
  (çipler, sıralama seçenekleri, başlıklar, rozetler).
- **`KitsugiTopActionBar`** — üst çubuk düğmelerinin tek kaynağı; sıra/konum/görünüm aynı,
  `NOTIFICATIONS` her zaman en sağda.
- **`KitsugiIconKit`** — `KitsugiIconButton`, `KitsugiChevronIcon` (0°↔180°, spring),
  `KitsugiStateIcon` (150 ms crossfade).
- **`galleryCategoryIcon`** — galeri kategorilerinin tek eşlemesi.

---

## 3. Son doğrulama koşuları (hepsi temiz)

| Kontrol | Sonuç |
|---|---|
| Çözülmemiş `Icons.X` referansı | **0** |
| `KitsugiIcons as Icons` import'u eksik dosya | **0** |
| `KitsugiEmojiLabel` import'u eksik dosya | **0** |
| `KitsugiEmojiLabel`'a geçersiz parametre (`fontSize`, `maxLines`…) | **0** |
| `R.string.*` eksik anahtar (tüm `values/*.xml` tarandı) | **0** |
| Parantez/süslü parantez dengesi HEAD'e göre fark | **0 (tüm değişen dosyalar)** |
| Boş yol verisi içeren ikon (`listOf()`) | **0** |
| `Icons.AutoMirrored` / `androidx.compose.material.icons` kalıntısı | **0** |
| `expanded` parametresiz chevron çağrısı | **0** |
| Emoji tablosunda çözülmemiş ikon adı / yinelenen anahtar | **0** |
| `Text(` içinde doğrudan çizilen emoji/glif (UI paketinde) | **0** |

> Not: Filtre/sıralama **veri** listelerinde emoji hâlâ anahtar olarak duruyor
> (`"🔥 Popülerlik" to "popularity"`). Bunlar ekrana basılmıyor; `KitsugiEmojiLabel`
> çizim anında HD ikona çeviriyor. Ülke bayrakları da veri olarak kalıyor (bayrak ikonu
> HD vektör karşılığı olmayan tek istisna; bilinmeyen dilde `Icons.Globe` gösterilir).

---

## 4. Dürüst kalan risk

- **Derleme yapılmadı**: bu ortamda JDK/Gradle/Android SDK yok. Kotlin derleyicisi
  çalıştırılamadığı için "derleniyor" garantisi veremem; verdiğim garanti
  *statik*: referans çözümü, import bütünlüğü, parametre imzaları, denge kontrolleri.
- Yeniden üretim sırası bozulursa: `python3 /tmp/gen_icons.py` → `python3 /tmp/unify.py`
  (aksi hâlde aile birleştirmeleri kaybolur).
- `data/cloudstream/CsPluginDiagnosticRunner.kt` gibi **rapor/markdown üreten** yerlerde
  `⚠️`, `📦` gibi işaretler bilinçli olarak metin: kullanıcıya indirilen `.md` raporunun
  içeriği bunlar, arayüz ikonu değil.

---

## 5. Değişen başlıca dosyalar

`KitsugiIcons.kt`, `KitsugiEmojiIcons.kt`, `KitsugiEmojiLabel.kt`, `KitsugiGalleryCategoryIcons.kt`,
`KitsugiTopActionBar.kt`, `KitsugiIconKit.kt`, `KitsugiMediaFilterBottomSheet.kt`,
`KitsugiImageGalleryDialog.kt`, `KitsugiSeasonalFilterBottomSheet.kt`, `KitsugiMediaEntryCard.kt`,
`SourceEngineFilterSheet.kt`, `SourceSpecificFilterChipsRow.kt`, `SearchFilterSheet.kt`,
`SearchGenreChips.kt`, `PluginPickerScreen.kt`, `PluginPickerBottomSheet.kt`,
`CsPluginDiagnosticScreen.kt`, `CsPluginRow.kt`, `CsRepoCard.kt`, `CsInstalledPluginRow.kt`,
`StreamCard.kt`, `TvLibraryScreen.kt`, `TvDetailScreen.kt`, `TvSearchScreen.kt`,
`TvModernHero.kt`, `TvReviewsTabContent.kt`, `TvMangaExtensionScreen.kt`,
`TvMangaSourceHealthScreen.kt`, `EntryDetailTabContents.kt`, `MyListComponents.kt`,
`KitsugiMyListFilterComponents.kt`, `AboutScreen.kt`, `MangaSourceHealthScreen.kt`, …

Komutlar: `git log --oneline` → `71337e4`, `1875c0c`, `f52799a`, `b307ffe`
