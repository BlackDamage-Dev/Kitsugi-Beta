# Plan / Görev: LazyGrid Sonsuz Yükseklik Çökmesi (Grid-in-LazyItem)

**Tarih:** 2026-10-22
**Branch:** arena/e0ffe39c-kitsugi-beta
**Commit:** çalışma ağacı (taban: 26bb044, commitsiz değişiklikler)

## Hata
- `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`
- Yığın: `LazyGrid.kt:194` → `checkScrollableContainerConstraints` → LazyGrid → LazyLayout → WrapContentNode → FillNode → LazyColumn item.
- Cihaz geçmişi (arkadaş telefonu): v2.4.314 açılış ("baslangic") sırasında 08:25, 10:14 ve 12:28'de gerçek Java çökmeleri; Java crash raporu LazyGrid stack'ini doğruluyor.

## Kök neden
- `LazyColumn.item { LazyVerticalGrid(..., userScrollEnabled = false, Modifier.fillMaxWidth().wrapContentHeight()) }` kalıbı.
- LazyList item'ları `maxHeight = Constraints.Infinity` ile ölçülür; `wrapContentHeight()` yalnızca MIN yüksekliği serbest bırakır, sonsuz maxHeight'i kısar.
- `userScrollEnabled = false` iken androidx `lazyLayoutBeyondBoundsModifier` EKLENMEZ (LazyList.kt) → ölçüm kuralı düşer ve istisna fırlatılır.
- Aynı çökme sınıfı daha önce: `ProfileActivityComponents.kt` favori sheet gridi (2026-10-12), wiki okuyucu ScrollNode (2026-10-13).

## Yapılan değişiklik
- `GridHomeContent.kt`: grid hücre yüksekliği artık satır sayısından hesaplanıyor
  (`rememberGridHomeEmbedHeight` / `gridHomeCardHeightDp` → `height(...)`; 28dp padding + 2:3 poster + 182dp sabit metin bloğu; shimmer satırları hücre genişliği/0.67). `kotlin.math.ceil` importu eklendi.
- `ProfileSelection.kt`: profil seçim gridine `heightIn(max = 520.dp)` kısıtı.
- `KitsugiApplication.kt`: `detectCrashProcess()` yardımcı fonksiyonu; `:crash` süreci artık `StartupStage` işaretçisini ezmiyor (yanlış "Son aşama: attachBaseContext" raporları düzeldi).
- `KOK_NEDEN_RAPORU_LAZY_GRID_SONSUZ_YUKSEKLIK_2026-10-22.md` eklendi (tam teşhis raporu, §6'da v2.4.344 port talimatı).

## Doğrulama durumu
- [ ] Android APK derlemesi (SDK sandbox'ta yok — yerelde doğrulanmalı)
- [ ] Ana ekran grid görünümü açılış + dikey kaydırma
- [ ] Profil seçim gridi (ekran boyutlarına göre)
- [ ] Hata günlüğü ekranı açıldığında "Son aşama" bilgisinin doğru görünmesi

## Açık kalan işler (bu düzeltme kapsamında DEĞİL)
- [ ] v2.4.344 derlemesine §6 port talimatının uygulanması: cihazdaki Listem/ana ekran "ızgara görünümü"ndeki
      `LazyColumn.item { LazyVerticalGrid(..., userScrollEnabled=false, Modifier.fillMaxWidth().wrapContentHeight()) }`
      kalıbında `wrapContentHeight()` yerine hesaplanmış `height(...)` (GridHomeContent deseni) veya
      `height(screenHeightDp * 0.7f)` + iç kaydırma (ProfileActivityComponents deseni)
- [ ] Unclean-exit analizcisinin Java çökmelerinde "SEBEP BULUNAMADI" demesi (logcat döndüğü için)
- [ ] Raporlardaki "Zaman:" alanının yanlış tarih göstermesi

## Değişen dosyalar
- app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/home/components/GridHomeContent.kt
- app/src/main/java/com/kitsugi/animelist/ui/screens/home/components/ProfileSelection.kt
- KOK_NEDEN_RAPORU_LAZY_GRID_SONSUZ_YUKSEKLIK_2026-10-22.md
- PLAN_TASK_LAZY_GRID_SONSUZ_YUKSEKLIK_COKME_2026-10-22.md
- diff: fix.diff (bu zip içinde)
