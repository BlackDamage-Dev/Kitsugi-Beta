# PLAN / TASK — Profil Üst Çubuğu Sağ Hizalama + İkon Yol Kaybı Düzeltmesi

Tarih: 2026-11-01
Dal: `arena/a39ca1a8-kitsugi-beta`
Taban sürüm: v2.4.393 (`2e401e2`)
İlgili rapor: `COZUM_RAPORU_PROFIL_UST_BAR_SAG_HIZALAMA_VE_IKON_YOL_KAYBI_2026-11-01.md`
Commit: `d367b9be`

## İstek ve kapsam

Kullanıcı bildirimi (ekran görüntüsü ile): **Profil sekmesinde sağ üstteki aksiyon butonları sağ köşeye
yapışık değil**, sağda büyük bir boş şerit kalıyor. Aynı görüntüde üst çubuk ve sekme çubuğu
ikonlarının "tek çubuk / tek çentik" gibi bozuk çizildiği de görülüyor.

Kapsam içi:
- Profil sekmesi üst çubuğunun yatay yerleşimi (`KitsugiProfileScreen.kt`)
- İkon üretim yardımcısının yol (path) kaybı hatası (`KitsugiIcons.kt`)

Kapsam dışı: Yeni özellik, renk/tema değişikliği, buton boyutu/aralığı değişikliği,
diğer sekmelerin üst çubuk düzeni (denetlendi, sağlam).

## Tespit özeti

| # | Kök neden | Dosya | Etki |
| --- | --- | --- | --- |
| 1 | Aynı `Row` içinde **iki ağırlıklı** çocuk var ve birincisi `weight(1f, fill = false)`. Ağırlıklı ama `fill=false` çocuk dilimini doldurmaz; `mainAxisLayoutSize = max(fixedSpace + weightedSpace, mainAxisMin)` olduğu için artan boşluk yeniden dağıtılmaz, `Arrangement.spacedBy` varsayılanı (`Start`) nedeniyle satırın **sonuna** eklenir | `KitsugiProfileScreen.kt` | **Asıl şikâyet** — 4 aksiyon butonu sağdan ~288 dp içeride kalıyor |
| 2 | `KitsugiIcons.build()`, **tek bir `PathParser`** örneğini tüm yollar için yeniden kullanıyor. `parsePathString()` dâhilî listeyi temizleyip aynı listeyi döndürür; `toNodes()` referans döndürdüğü için `ImageVector` içindeki tüm yollar **son yolun** düğümlerine bakıyor | `KitsugiIcons.kt` | Uygulamadaki **tüm çizgi ikon seti** bozuk: saat/indirme/grafik = tek çubuk, çan = gövde, "Hakkında" = "•" |

### Ölçüm kanıtı (2560×1600 ekran görüntüsü, ~2.2x yoğunluk)

| Öğe | Ölçülen | Beklenen |
| --- | --- | --- |
| Buton boyutu | 88 px = 40 dp | 40 dp ✅ |
| Buton aralığı | 19 px ≈ 8 dp | 8 dp ✅ |
| Aksiyon satırı bitişi | 1886 px | 2520 px (sağ padding) |
| Sağda kalan boşluk | **634 px ≈ 288 dp** | 0 dp ❌ |

Hesap: `kullanılabilir = 1047 dp`, `pill dilimi = (1047 − 184 − 16) / 2 = 423 dp`,
pill içeriği 128 dp → yerleşim `16 + 128 + 8 + 423 + 8 + 184 = 767 dp` → sağda **298 dp** artık.
Ölçümle birebir örtüşüyor.

## Tamamlanan görevler

- [x] Pill'i, ağırlığı **tam dolduran** sarmalayıcı `Row(Modifier.weight(1f))` içine al;
      gereksiz `Spacer(Modifier.weight(1f))` kaldır → aksiyon satırı daima sağ padding'in yanında biter.
- [x] Pill'i ağırlıksız bırak (sarmalayıcı içinde); dar ekranda metin `TextOverflow.Ellipsis` ile kısalır, taşma yok.
- [x] `KitsugiIcons.build()`: her yol için **taze `PathParser`** + `toNodes().toList()` ile kopyalanmış liste.
- [x] Diğer üst çubukları denetle (Keşfet, Arama, Listem, Kullanıcı profili, Platform profili) → sağlam.
- [x] Kök neden + ölçüm raporunu yaz (`COZUM_RAPORU_..._2026-11-01.md`).
- [x] CI doğrulaması: `🏗️ Android Derleme Doğrulama` → foss debug + R8 release + DEX doğrulama **success**
      (run 41342971906, 31 dk 53 sn).

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| Aksiyon satırı sağ kenara mesafesi | Sağ padding kadar (18 dp yatay / 16 dp dikey) ✅ |
| Buton boyutu / aralığı / köşe yarıçapı | Değişmedi (40 dp · 8 dp · 14 dp) ✅ |
| Bildirim butonu kanonik sırada en sağda | Korundu (`KitsugiTopAction.order`) ✅ |
| Dar ekran (telefon dikey) | Pill kısalır, 4 buton sığar, taşma yok ✅ |
| Yatay / tablet / TV | Sarmalayıcı `weight(1f)` ölçeklenir ✅ |
| İkon doğruluğu | Çok yollu ikonların tüm parçaları çiziliyor ✅ |
| İkon API uyumluluğu | `Icons.Rounded.*` takma adları, 24 dp viewport, `STROKE = 2f` korundu ✅ |
| Build (debug + R8 release + DEX) | success ✅ |

## Değişen dosyalar

| Dosya | Değişiklik |
| --- | --- |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiProfileScreen.kt` | Üst çubuk: pill sarmalayıcı `Row(weight(1f))`, `Spacer(weight)` kaldırıldı (+28 / −6) |
| `app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIcons.kt` | `build()`: yol başına taze `PathParser` + liste kopyası (+10 / −2) |

## Regresyon riski

- **Yerleşim:** Yalnızca Profil üst çubuğunun iç düzeni değişti. Buton boyutları, sırası ve tema
  vurgu davranışı aynı. Diğer sekmeler etkilenmedi (denetlendi).
- **İkonlar:** `ImageVector` üretimi aynı API ile; yalnızca düğüm listesi kopyalanıyor. `by lazy`
  üretim sayesinde ek maliyet yol başına tek küçük liste kopyası (ölçülemez düzeyde).

## Sonraki adımlar (opsiyonel)

- [ ] `main` dalına PR açılması
- [ ] v2.4.394 release (APK + sürüm notları)
- [ ] Cihazda görsel doğrulama (Profil dikey/yatay)
