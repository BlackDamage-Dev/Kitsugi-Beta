# 3B İkon Setleri Kaldırıldı — 2. Tasarım (Kitsugi HD Vektör) Geri Geldi

**Tarih:** 2026-11-03
**Dal:** `arena/fe6a26ad-kitsugi-beta` (taban: `feat/golge-bahcesi-integration`, v2.4.406)
**Durum:** Uygulandı (derleme doğrulaması bu ortamda yapılamadı — JDK / Android SDK yok)

---

## 1. İstek

> "Tıklanır alanlar, açılır-kapanır butonlar … **3 boyutlu logoları tamamı kaldırılacak**.
> En eski hali değil, **2. yaptığımız**, uygulamanın temasına özgün tasarladığımız logolar.
> Uygulamanın her yerindeki logolarım eski haline dönecek, eksiksiz."

## 2. Tasarım kronolojisi (git geçmişinden doğrulandı)

| # | Sürüm | Temsil eden commit(ler) | Durum |
|---|-------|-------------------------|-------|
| 1 | Eski: Material dolgulu glifler + emoji | v2.4.387 ve öncesi | ⛔ istenmiyor |
| 2 | **KITSUGI HD İKON SETİ** — Lucide tabanlı, ince çizgili, `tint` ile temadan boyanan vektörler (`KitsugiIcons`, `KitsugiEmojiIcons`, `KitsugiIconKit`, `KitsugiTopActionBar`) | `b307ffe0`, `f52799ae`, `1875c0cd`, `71337e4e` (2026-10-31) | ✅ **geri getirildi** |
| 3 | 3B cam/gradyan set (3dicons CC0) + otomatik ton kaydırma | `b3457c6d` | ⛔ geri alındı |
| 4 | 3B Blender/Cycles "v13" set (145 PNG varlık, tam kapsam) | `34e726d4` | ⛔ geri alındı |

## 3. Yapılan işlem

1. `git revert 34e726d4` → Blender/Cycles 3B seti, `ui/icons/KitsugiIconAssets.kt`, 145 `ic_kitsugi_*.png`,
   `tools/iconpack/*` üretim betikleri ve `PLAN_TASK_IKON_SETI_3D_TEMA_UYUMU_2026-11-01.md` kaldırıldı.
2. `git revert b3457c6d` → 3dicons cam/gradyan seti, vurgu ton kaydırma (`iconHueShiftFor`,
   `LocalKitsugiIconHueShift`, `iconsFollowAccent`) ve kalan 3B görsel plakalar kaldırıldı.
   - 2 dosyada çakışma çıktı (`PluginPickerScreen`, `PluginPickerBottomSheet`):
     v2.4.400 / v2.4.405'te gelen **yeni işlevsel kod korundu** (ortak `KitsugiCosmicSearchBar`),
     yalnızca 3B ikon katmanı geri alındı.
3. **Ek temizlik — 8 dosya:** geri alma sonrası hâlâ Material glif kullanan (v2.4.395–406 arasında
   eklenmiş) ekranlar HD vektör setine çevrildi:
   `QualitySheet`, `SourceSheet`, `WatchHistoryScreen`, `KitsugiNotificationDeviceStatus`,
   `DownloadsImagesTab`, `OrgFilterSheet`, `StreamSourceSheet`, `KitsugiDetailThemesTrailerComponents`.
   - `Icons.Default.SwapVert` → `Icons.ArrowUpDown`
   - `Icons.Rounded.VerifiedUser` → `Icons.ShieldCheck`
   - Artık `androidx.compose.material.icons.*` importu **0**.

## 4. Doğrulama (statik — gerçek çıktılar)

| Kontrol | Sonuç |
|---|---|
| `res/drawable-nodpi/ic_kitsugi_*.png` (3B varlık) | **0** (yalnızca 7 marka logosu kaldı) |
| `app/src/main/java/.../ui/icons/` | `KitsugiIcons.kt` (HD vektör) + `KitsugiEmojiIcons.kt` |
| `KitsugiArt` / `KitsugiArtIcon` / `KitsugiRowIcon` / `KitsugiIconAssets` referansı | **0** |
| `iconHueShiftFor` / `LocalKitsugiIconHueShift` / `iconsFollowAccent` referansı | **0** |
| `3dicons` / `Blender` / `ic_kitsugi_` (kod, xml, gradle, betik) | **0** |
| `androidx.compose.material.icons.*` importu | **0** |
| `KitsugiIcons` üyesi olmayan `Icons.X` kullanımı | **0** |
| `KitsugiEmojiLabel` importu eksik dosya | **0** |
| Silinen drawable'a referans | **0** (`abc_ic_clear_material` appcompat'tan gelir, mevcut durum) |
| `tools/iconpack` (3B üretim hattı) | kaldırıldı |

## 5. Değişmeyenler (bilinçli)

- **Marka logosu:** launcher ikonu, splash animasyonu ve `kitsugi_current_logo.png` — kırmızı neon
  torii. 3B ikon çalışmaları bu varlıklara dokunmadı; bu yüzden geri alma da kapsamıyor.
- Kaynak platform marka logoları (`ic_logo_anilist`, `ic_logo_mal`, `ic_logo_tmdb`, `ic_logo_simkl`,
  `ic_logo_kitsu`, `ic_logo_shikimori`, `ic_logo_bangumi`, `ic_logo_fanart`, `ic_logo_fandom`).
- Veri katmanındaki emoji etiketleri (`"🔥 Popülerlik"` vb.) — 2. tasarımda da veri olarak duruyor,
  çizim tarafında `KitsugiEmojiLabel` + `KitsugiEmojiIcons` ile HD ikona çözülüyor.

## 6. Doğrulama sınırı

Bu ortamda JDK, Gradle ve Android SDK olmadığı için **derleme/APK doğrulaması yapılamadı**.
Yukarıdaki tüm kontroller statik tarama çıktılarıdır. Cihazda önerilen akış:
Keşfet → alt gezinme çubuğu, Listem, Arama → kaynak seç, Ayarlar (anahtarlar), Detay → bölümler,
Oynatıcı → kontroller, Hakkında. Hepsinin ince çizgili HD vektör setiyle çizilmesi beklenir.
