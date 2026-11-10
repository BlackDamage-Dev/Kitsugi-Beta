# PLAN_TASK — DETAY SAYFASI "BİLGİ" SEKMESİ KAYDIRMA PERFORMANS FIX (2026-10-23)

**Dal:** `arena/74d9ecc5-kitsugi-beta`
**İlgili rapor:** `KOK_NEDEN_RAPORU_DETAY_BILGI_KAYDIRMA_KASMASI_2026-10-23.md`
**Belirti:** "detay sayfası bilgi kısmı feci kasıyor donuyor … sağa sola yukarı aşağı kaydırınca"

## Görev listesi

| # | Görev | Dosya | Durum |
|---|---|---|---|
| 1 | Sekme-özel StateFlow'ları tek sayfa scope'undan çıkarıp izole slot composable'larına taşı (`OverviewTabSlot`, `CharactersTabSlot`, `StaffTabSlot`, `RecommendationsTabSlot`, `RelationsTabSlot`, `StatsTabSlot`, `ReviewsTabSlot`, `EpisodesTabSlot`) | `MediaEntryDetailPage.kt` | ✅ |
| 2 | `pagerState.currentPage` okumasını sayfa gövdesinden kaldır; sekme yükleme efektini izole `DetailTabLoadEffect`'e, video sekmesi kapısını pager ITEM scope'una al | `MediaEntryDetailPage.kt` | ✅ |
| 3 | Tüm satır-içi callback'leri `remember` ile stabilize et (sekme içerikleri skippable olsun): `openGallerySelection`, `onOverviewImageGalleryRequest`, `onOverviewGalleryItemRequest`, `onOverviewTranslateClick`, `onToggleSynopsisOriginal`, `onIntegrationsClick`, `onSeasonSelected`, `onEpisodeOptionsRequested`, `onUnlinkManga`, `onImagesItemClick`, `onImagesOpenGallery`, `onImagesOpenSourceGallery`, `onWikiOpenGallery`, `onRelationItemClick`, `onMediaClick` | `MediaEntryDetailPage.kt` | ✅ |
| 4 | `studioChipClick` lambda'sını `remember` ile stabilize et | `EntryDetailTabContents.kt` | ✅ |
| 5 | Portre pager yükseklik interpolasyonunu composition fazından `Modifier.layout { }` (layout fazı) içine taşı → kare başına recompose yerine yalnızca relayout | `DetailPageScaffold.kt` | ✅ |
| 6 | Sonsuz nabız animasyonunu yalnızca `isLoading` iken kurulan izole `PulsingGalleryIconButton` alt ağacına al (boşta recompose fırtınası yok) | `KitsugiGalleryIconButton.kt` | ✅ |
| 7 | Kök neden raporu + bu plan dosyası | repo kökü | ✅ |

## Kapsam dışı (bilinçli dokunulmadı)
- Yatay pager `beyondViewportPageCount = 0` değeri korundu (bellek/ilk açılış maliyeti trade-off'u).
- Sol vitrin paneli ve hero görsel politikası (`highQuality` decode) değiştirilmedi.
- `ApiResultDetailPage` kendi `selectedTab` yapısını korur; scaffold değişikliği ona da yarar.

## Davranış değişikliği yok
Sekme yükleme tetikleyicileri (`loadTab`), özet/çeviri yayılımı (`onSynopsisLoaded`), galeri
açma/kapama semantiği ve video sekmesi kapısı birebir aynı; yalnızca state okuma fazları
ve recompose kapsamları değişti.

## Cihazda doğrulama adımları
- [ ] Detay sayfasını aç → Bilgi sekmesinde yukarı/aşağı hızlı kaydır: jest akıcı.
- [ ] Sekmeler arası yatay kaydırma (Bilgi ⇄ Resimler ⇄ …): jest ortasında takılma yok.
- [ ] Sayfa açılır açılmaz (galeri/çeviri yüklenirken) kaydır: kasma yok.
- [ ] Layout Inspector: kaydırma sırasında sayfa gövdesi recompose olmuyor.
- [ ] Portre modda sekme kaydırması: yükseklik interpolasyonu aynı, kasma yok.
