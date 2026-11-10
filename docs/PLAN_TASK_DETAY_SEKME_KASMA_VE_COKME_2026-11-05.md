# PLAN & TASK — Detay/Profil Sekme Kasma + Çökme Düzeltmesi
**Tarih:** 2026-11-05 · **Kapsam:** Tüm kaynak detay sayfaları + profiller + galeri
**Durum:** ✅ Tamamlandı (kod tarafı) · ⏳ Bekleyen: `MediaVideoItem.alternateUrls` NPE yaması (güncel kaynak ağacı gerekli)

---

## A. Görev listesi

| # | Görev | Durum | Dosya |
|---|---|---|---|
| A1 | Sekme pager yüksekliğini layout fazına taşı (per-frame kompozisyonu bitir) | ✅ | `ui/utils/DetailPagerHeightPolicy.kt` *(yeni)* |
| A2 | Detay iskeletinde `selectedTab` kompozisyon okumasını kaldır (`snapshotFlow`) | ✅ | `DetailPageScaffold.kt` |
| A3 | API detay ekranında aynı düzeltme + gereksiz `selectedTab` guard'ı | ✅ | `ApiResultDetailPage.kt` |
| A4 | Liste tabanlı detay ekranında aynı düzeltme | ✅ | `MediaEntryDetailPage.kt` |
| A5 | Karakter detayı pager'ı | ✅ | `CharacterDetailPage.kt` |
| A6 | Ekip (staff) detayı pager'ı | ✅ | `StaffDetailPage.kt` |
| A7 | Galeri kartındaki O(n²) `items.indexOf` | ✅ | `EntryDetailTabContents.kt` |
| A8 | Tam ekran galeri sayfa efekti → çizim fazı (`graphicsLayer`) | ✅ | `KitsugiImageGalleryDialog.kt` |
| A9 | Galeri "diğer görseller" listesindeki O(n·m) `indexOf` | ✅ | `KitsugiImageGalleryDialog.kt` |
| A10 | Profil pager'ları: AniList / MAL / Kitsu / Shikimori / Simkl / Kitsugi | ✅ | `ui/screens/profile/*.kt` (6 dosya) |
| A11 | Regresyon denetleyicisi (per-frame okuma yakalayıcı) | ✅ | `scripts/kitsugi_pager_perf_guard.py` *(yeni)* |
| A12 | 26 pager alanının tam denetimi + rapor | ✅ | `KOK_NEDEN_RAPORU_DETAY_SEKME_KASMA_VE_COKME_2026-11-05.md` |
| **A13** | **`MediaVideoItem.alternateUrls` NPE'si (çökmenin tek gerçek sebebi)** | ⏳ **BEKLİYOR** | `data/remote/KitsugiMediaVideoAggregator.kt` — **bu dosya repoda yok** (repo 2.4.340 / cihaz 2.4.426) |

---

## B. A13 için uygulanacak yama (güncel ağaçta)

```kotlin
// 1) Modeli null'a dayanıklı yap
data class MediaVideoItem(
    val url: String,
    val alternateUrls: List<String>? = emptyList(),   // eskiden: List<String>
    ...
) {
    val safeAlternateUrls: List<String> get() = alternateUrls.orEmpty().filter { it.isNotBlank() }
}

// 2) Kurulum yerlerinde normalize et
alternateUrls = (item.alternateUrls ?: listOfNotNull(item.url.takeIf { it.isNotBlank() }))

// 3) Veri katmanında emniyet kemeri (Compose dışında yakala)
fun seedFromDetail(detail: KitsugiMediaDetail?, mediaTitle: String = ""): List<MediaVideoItem> {
    if (detail == null) return emptyList()
    return runCatching { buildItems(detail, mediaTitle) }
        .getOrElse { t ->
            KitsugiLog.w("MediaVideoAggregator", "seedFromDetail düştü: ${t.message}")
            emptyList()
        }
}
```

---

## C. Doğrulama (cihazda)

1. `python3 scripts/kitsugi_pager_perf_guard.py` → `0 hata, 0 uyarı` ✅
2. Date A Live (231 görsel): Resimler ↔ Videolar 10 kez kaydır
   `adb shell dumpsys gfxinfo com.kitsugi.animelist reset` → `Janky frames < %5`, `90th percentile < 16 ms`
3. Videolar sekmesi + galeri aç/kapa 20 tekrar → `E/ComposeInternal` **hiç görünmemeli** (A13 sonrası)
4. Profil sekmeleri (6 sağlayıcı) hızlı geçiş → kare düşmesi olmamalı
5. 10 dakika gezinme sonrası bellek artış trendi olmamalı (~118 MB civarı)

---

## D. Kural (bir daha bozulmasın)

- `pagerState.currentPageOffsetFraction` yalnızca **çizim** (`graphicsLayer`/`drawBehind`) veya **layout** (`derivedStateOf` + `Modifier.layout`) fazında okunur.
- Pager yüksekliği için tek kaynak: `ui/utils/DetailPagerHeightPolicy.kt`.
- `pagerState.currentPage` kompozisyonda okunacaksa, mutlaka **küçük bir alt bileşenin** kapsamında okunmalı (tüm ekranı yeniden kompoze etmemeli).
- Sonsuz yükseklikle ölçülen bir pager sayfası **asla** `LazyColumn` / `LazyVerticalGrid` / `verticalScroll` içermez.
