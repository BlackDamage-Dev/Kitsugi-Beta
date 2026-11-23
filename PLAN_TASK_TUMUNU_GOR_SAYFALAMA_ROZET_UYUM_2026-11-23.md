# TÜMÜNÜ GÖR: SAYFALAMA + ROZET/UYUM DÜZELTMESİ (v2.4.490)

**Tarih:** 2026-11-23
**Kapsam:** `FullScreenMediaGridPage`, `KitsugiExploreMediaCard`, `KitsugiRankingMediaCard`, `TmdbDiscoverClient`, `AddonFullScreenGridPage`, mobil keşfet yönlendirmesi, `AppRoot`
**Durum:** Uygulandı (derleme doğrulaması henüz yapılmadı — sandbox'ta JDK/Android SDK yok).

---

## 1. Kullanıcı bildirimi (özet)

> "Tümünü Gör sayfaları bütün kaynaklarda düzgün çalışmıyor. MAL'da veri gelmeye devam ediyor
> ama TMDB'de Tümünü Gör sayfasına girince veri gelmiyor. Sol üstteki listeye ekle butonu
> kalksın. Kare kutucuklardaki sayı/puan rozetleri liste görünümündekiyle birebir aynı olsun."

Üç ayrı kök neden bulundu; üçü de düzeltildi.

---

## 2. Kök neden A — TMDB sayfalaması kalıcı olarak kilitleniyordu

**Belirti:** TMDB rafında "Tümünü Gör" → ilk 20 öğe gelir, kaydırınca **hiç** yeni veri gelmez.

**Zincir:**

1. `TmdbDiscoverClient.executeGet` herhangi bir hata (429 hız sınırı, 5xx, zaman aşımı) için
   `null` döner; hiçbir istisna yükselmez.
2. `parseTmdbDiscoverList`/`parseTmdbDiscoverListAll` `null` gövdeyi boş liste sayar.
   Ayrıca interleave eden uçlar (`getTrendingAll`, `getTrendingMedia`, …) iki 20'lik API
   sayfasını birleştirip `take(20)` yapıyordu → sonuç **her zaman** 20 görünüyor, kısmi/hatalı
   sayfa "dolu" sanılıyordu.
3. `TmdbDiscoverClient` boş sonucu 60 dakikalık TTL ile **başarı önbelleği** olarak yazıyordu.
   Tek bir 429, o sorguyu 1 saat boyunca boş bırakıyordu.
4. `FullScreenMediaGridPage.loadNextPage()` boş sayfayı **liste sonu** kabul edip
   `hasMorePages = false` yazıyordu → sayfa bir daha istek atmaz.

**Düzeltme:**

- `TMDB_PAGE_SIZE` sabiti; birleşik sayfa artık `distinctBy { type:tmdbId }` + `take(20)`
  ile **kayıpsız** dönüyor (kuyruktaki öğeler atılmıyor).
- Boş sonuç önbelleği ayrı TTL: `EMPTY_TTL_MS = 60 sn` (normal önbellek 60 dk). Geçici hata
  1 saat boyunca servis edilmez.
- `loadNextPage()`: **boş sayfa ≠ liste sonu.** Son sayfa `pageSizeFor(platform)` kadar dolu
  geldiyse boş sonuç "kaynak yanıt vermedi" hatası olarak işaretlenir, `hasMorePages` korunur.
  Kısmi sayfadan sonra gelen boşluk ise gerçek sondur.
- Yeni `pageSizeFor()` (SIMKL 20, diğerleri 20) ve `lastPageWasFull` durumu eklendi.
- Kaynak sayfa sınırı: `TMDB_MAX_API_PAGE = 500` sonrası istek atılmıyor (TMDB 400 döner).
- Sessiz kurtarma: `TRANSIENT_PAGE_ERROR` durumunda 600 ms / 1200 ms arayla en fazla
  `MAX_AUTO_RETRIES = 2` otomatik tekrar denemesi; başarıda sayaç sıfırlanır.
- Otomatik kaydırma yüklemesi hata varken durur (`loadError == null` koşulu) — hem sonsuz
  istek fırtınası hem de "kaydırdıkça hata büyüyor" durumu engellendi.
- `fetchFilteredExplorePage(..., orThrow = true)`: "Tümünü Gör"de `runCatching` ile yutulan
  hatalar artık yukarı taşınır; hata ile "sonuç yok" ayrışır. Keşfet ekranı eski davranışı korur.

**Aynı sınıf hata — eklenti (CS3) ızgarası:** `AddonFullScreenGridPage.loadNextPage()`
yanıt `null` olduğunda `hasMorePages = false` yapıyordu; artık `addon_page_load_error`
gösterir, sayfalama durumu korunur ve "Tekrar dene" aynı sayfayı yeniden çeker.

---

## 3. Kök neden B — Telefonda "Tümünü Gör" hiç sayfalamıyordu

**Belirti:** Aynı raf tablette/TV'de sonsuz kaydırırken telefonda ilk 20 öğede duruyor
(kullanıcı için "MAL'da veri geliyor, TMDB'de gelmiyor" algısının ikinci kaynağı).

**Zincir:** `ExploreScreen` telefonda `onOpenMobileShelf` → `openMobileExploreShelf` →
`mobileFiniteGridState(...)` → `FullScreenMediaGridState(mobileFiniteResults = true)` →
`MobileExploreFiniteGrid` (yalnızca hazır öğeler). Kaynak ne olursa olsun sayfalama yoktu.

**Düzeltme:** `MobileExploreNavigation.kt`

- `MobileExploreShelfData.canPaginateAsCatalogShelf` = `!requiresSpecialContinuation && source == originPlatform`.
- Temiz katalog raflarında (MAL, TMDB, AniList, Kitsu, Shikimori, Simkl, Bangumi) artık
  `mobileFiniteResults = false` ile **gerçek sayfalanan** `FullScreenMediaGridPage` açılır.
- Karışık/kişisel/türetilmiş raflar (Simkl listeleri, ALL birleşimleri, novel rafları) sonlu
  ızgarada kalır: sayfalanan sorgu raftan farklı içerik getireceği için bu bilinçli bir sınırdır.

---

## 4. Kök neden C — Rozetler iki görünümde farklıydı

**Belirti (kullanıcı ekran görüntüleri):**
- **Liste görünümü (referans, `image-1`)**: `Anime (25 bölüm)` altında `1/25 bölüm` (durum renginde),
  ayrıca dolu öğelerde `★ 8` — yıldız + sayı, **kutu/dolgu yok**.
- **Kare görünüm (`image-2`)**: aynı öğede bölüm bilgisi tek satırda ve puan **posterin üzerinde
  siyah yarı saydam dikdörtgen içinde** `★ 8/10`.

Aynı öğe görünüm değiştirince puan farklı yerde ve farklı görünümde okunuyordu → kullanıcı
"kare kutucuktaki rozetler liste görünümündekilerle birebir aynı olsun" dedi.

**Karar: liste görünümü referanstır.** Kare görünüm, liste görünümünün rozet dilini birebir alır.

**Düzeltme:** Yeni **tek kaynak** dosya: `ui/components/KitsugiMediaCardStats.kt`

| Bileşen / yardımcı | Görev |
|---|---|
| `KitsugiMediaStatSpec` | Liste görünümünün ölçüleri (16 dp yıldız, `0xFFFFB800`, 4 dp çubuk, 6/4 dp boşluklar) |
| `KitsugiScoreBadge` | **Tek puan rozeti**: sarı `Icons.Star` + kalın sayı, kutusuz — iki görünüm de bunu kullanır |
| `KitsugiStatProgressBar` | 4 dp yuvarlak ilerleme çubuğu |
| `KitsugiMediaCardStatBlock` | Biçim satırı + bölüm metni + puan + üye + ilerleme (liste/kare ortak) |
| `kitsugiStatusColor` / `kitsugiEntryProgressText` / `kitsugiFormatTypeText` / `kitsugiPublicScoreLabel` / `kitsugiFormatStatNumber` | Ortak biçimlendirme |

- Kare görünümde metin alanı sabit yükseklikte (`EXPLORE_CARD_PORTRAIT_TEXT_HEIGHT = 182 dp`)
  olduğu için aynı blok `compact = true` ile tek satıra sığdırılır: `0/12 bölüm` + `★ 8.00` + 4 dp çubuk.
  Bileşenler, renkler ve tipografi **liste görünümüyle aynıdır**.
- Poster üzerindeki siyah puan şeridi (`KitsugiPosterScoreStrip`) **kaldırıldı**; puan artık
  iki görünümde de kartın metin alanında, listedeki gibi.
- Liste kartına eksik olan `scoreFormat` parametresi eklendi; puan biçimi iki görünümde aynı
  (`POINT_10` → `8/10`, kaynak ortalaması → `8.00`).
- TV ve yatay kare varyantları da aynı `compact` bloğu kullanır (üç görünümde tek dil).
- Sıra numarası rozeti (`#n`) mevsimsel gridde gizleniyordu; artık her iki görünümde de
  `index + 1` kaynağından gelir.

## 5. Diğer

- **"Tümünü listeye ekle" kaldırıldı:** `FullScreenMediaGridPage` içindeki üç `PlaylistAdd`
  düğmesi (kare başlık, liste başlığı, yüzen başlık) ve `onAddAllToList` parametresi silindi;
  `AppRoot.kt`'deki `addApiSelectionsToList(...)` bağlantısı da kaldırıldı. Tek tek hızlı
  ekleme (poster köşesi) korunur.
- **Başlık düzeni:** düz "Geri" metin düğmesi yerine yuvarlak `KitsugiCircularBackButton`
  (38 dp, `Surface` zemin, 1 dp kontur, 19 dp ok). Başlığın altına `SourceIdentityRow`:
  16 dp kaynak logosu + kısa ad (ör. "Trend Animeler" altında "MAL").
- **Kod temizliği:** iki karttaki kopya `EpisodeProgressBar`, `buildFormatText`,
  `entryProgressText`, `statusColor`, `formatNumber` yardımcıları ortak dosyaya taşındı;
  kullanılmayan `MediaType`/`AsyncImage`/`ContentScale` importları silindi.

---

## 6. Doğrulama durumu

- Kapsamlı statik inceleme: brace/parantez dengesi, kullanılmayan import taraması,
  çapraz referans (tanımsız sembol) taraması → temiz.
- **Derleme çalıştırılmadı**: sandbox'ta JDK ve Android SDK yok (`java: command not found`,
  `ANDROID_HOME` boş). `./gradlew :app:compileDebugKotlin` yerel makinede çalıştırılmalı.

## 7. Yeniden üretim adımları (test)

1. Keşfet → TMDB → "Trend Her Şey" → **Tümünü Gör** → 400+ öğeye kadar kaydır.
   Beklenen: her sayfa eklenir, hata olursa "Tekrar dene" görünür ve tekrar denemede devam eder.
2. Ağ throttle/airplane mode ile aynı senaryo: liste kilitlenmez, hata sonrası devam eder.
3. Telefonda MAL ve TMDB raflarında **Tümünü Gör**: kaydırınca yeni sayfalar gelir.
4. Liste ↔ kare geçişi: aynı öğenin bölüm/puan rozetleri birebir aynı görünmeli;
   puan her iki görünümde posterin sağ altında.
5. Tümünü Gör başlıklarında "listeye ekle" düğmesi **yok**.
