# ÇÖZÜM RAPORU — Profil Üst Çubuğu Sağ Hizalama + İkon Yol Kaybı

**Tarih:** 2026-11-01
**Dal:** `arena/a39ca1a8-kitsugi-beta`
**Taban:** v2.4.393 (`2e401e2`)
**Şikâyet:** "Profil sekmesinde butonlar sağ köşeye yapışık değil" + üst çubuk ve sekme
ikonlarının "tek çubuk / tek çentik" gibi görünmesi.

Ekran görüntüsü ölçümleri (2560×1600, ~2.2x yoğunluk, sağdan sola):

| Öğe | Ölçülen | Beklenen |
|---|---|---|
| Aksiyon butonu boyutu | 88 px = 40 dp | 40 dp ✅ |
| Butonlar arası boşluk | 19 px ≈ 8 dp | 8 dp ✅ |
| Aksiyon satırı bitişi | 1886 px | 2520 px (sağ padding) |
| Sağda kalan boşluk | **634 px ≈ 288 dp** | 0 dp ❌ |

---

## 1. Üst çubuk sağa yapışmıyor (asıl şikâyet)

### Kök neden

`KitsugiProfileScreen.kt` üst çubuğu şu şekilde kurulmuştu:

```kotlin
Row(Modifier.fillMaxWidth().padding(...), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    ProfileSourceSelectorPill(..., modifier = Modifier.weight(1f, fill = false))  // ⬅️
    Spacer(Modifier.weight(1f))
    KitsugiTopActionRow(...)
}
```

Aynı satırda **iki ağırlıklı** çocuk var ve birincisi `fill = false`.
Compose `RowColumnMeasurePolicy` davranışı (kaynak doğrulandı):

* Ağırlıklı çocuk `fill = true` ise `mainAxisMin = mainAxisMax = dilim` → dilim tamamen dolar.
* Ağırlıklı çocuk `fill = false` ise `mainAxisMin = 0`, `mainAxisMax = dilim` → çocuk **içerik
  kadar** ölçülür ve satır yalnızca **ölçülen** boyu hesaba katar
  (`weightedSpace += placeable.mainAxisSize`).
* `mainAxisLayoutSize = max(fixedSpace + weightedSpace, mainAxisMin)` → `fillMaxWidth`
  sayesinde satır tam genişlikte kalır, fakat artan boşluk **yeniden dağıtılmaz**;
  `Arrangement.spacedBy(...)` varsayılanı `Start` olduğu için boşluk **satırın sonuna**
  (sağa) eklenir.

Sayısal doğrulama (1083 dp içerik, 18 dp yatay padding):

```
kullanılabilir = 1047 dp
pill dilimi = (1047 − 184 − 16) / 2 = 423 dp  → pill yalnızca 128 dp ölçülür
spacer      = 423 dp (dolu)
aksiyonlar  = 184 dp (4×40 + 3×8)
yerleşim   = 16 + 128 + 8 + 423 + 8 + 184 = 767 dp  → sağda 298 dp artık
```

Ekran görüntüsündeki **288 dp** artık ile birebir örtüşür. Bu yüzden butonlar sağ köşeye
değil, "havada" duruyordu.

### Çözüm

Pill, ağırlığı **tam dolduran** bir sarmalayıcı `Row` içine alındı; aksiyon satırı sarmalayıcıdan
sonra geldiği için daima en sağda (sağ padding'in hemen yanında) bitiyor:

```kotlin
Row(Modifier.fillMaxWidth().padding(...), Arrangement.spacedBy(8.dp)) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        ProfileSourceSelectorPill(...)      // solda, dar ekranda üç nokta ile kısalır
    }
    KitsugiTopActionRow(...)                // daima sağ köşede
}
```

Aynı desen diğer üst çubuklarda da denetlendi:

| Ekran | Desen | Durum |
|---|---|---|
| Keşfet | pill `weight(1f)` (fill = true) | ✅ sağa yapışık |
| Arama | başlık/`Spacer` `weight(1f)` (fill = true) | ✅ |
| Listem | pill ağırlıksız + `Spacer(weight(1f))` | ✅ |
| **Profil** | pill `weight(1f, fill = false)` + `Spacer(weight(1f))` | ❌ → düzeltildi |
| Kullanıcı profili / platform profili | `Arrangement.SpaceBetween` | ✅ |

---

## 2. İkonların yalnızca son parçası çiziliyor (görsel bozulmanın kaynağı)

### Kök neden

`ui/icons/KitsugiIcons.kt` içindeki `build()` yardımcısı, **tek bir `PathParser`** örneğini
tüm yollar için yeniden kullanıyordu:

```kotlin
val parser = PathParser()
pathData.forEach { raw ->
    builder.addPath(pathData = parser.parsePathString(d).toNodes(), ...)
}
```

`PathParser.parsePathString()` kendi dâhilî listesini **temizleyip yeniden kullanır** ve
`toNodes()` **aynı listenin referansını** döndürür (androidx kaynağı doğrulandı):

```kotlin
public fun parsePathString(pathData: String): PathParser {
    ... dstNodes.clear(); pathStringToNodes(pathData, dstNodes); return this
}
public fun toNodes(): List<PathNode> = nodes ?: emptyList()
```

Dolayısıyla `ImageVector` içine eklenen **tüm yollar aynı listeye** bakıyordu; döngü bitince
liste yalnızca **son yolun** düğümlerini taşıyor → çok yollu her ikon sadece son parçasıyla
çiziliyordu:

| İkon | Son yol | Ekranda görünen |
|---|---|---|
| `ClockArrowUp` (Geçmiş) | `M18 22v-8` | tek dikey çubuk |
| `Download` (İndirmeler) | `m7 10 5 5 5-5` | tek "v" çentiği |
| `ChartColumn` (İstatistik) | `M8 17v-3` | tek dikey çubuk |
| `Bell` (Bildirim) | gövde | çan gövdesi (alt çengel yok) |
| `Info` (Hakkında sekmesi) | `M12 11v5` | "•" gibi nokta |

Bu tek hata **tüm uygulamadaki çizgi ikon setini** etkiliyordu (üst çubuk, sekme çubuğu,
ayar satırları, detay sekmeleri…).

### Çözüm

Her yol için taze `PathParser` + kopyalanmış düğüm listesi:

```kotlin
val pathNodes = PathParser().parsePathString(d).toNodes().toList()
builder.addPath(pathData = pathNodes, ...)
```

İkonlar `by lazy` üretildiği için ek maliyet yalnızca ilk kullanımda, yol başına bir küçük
liste kopyası kadardır (ölçülemez düzeyde).

---

## Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiProfileScreen.kt` | Üst çubuk: pill sarmalayıcı `Row(weight(1f))`, `Spacer(weight)` kaldırıldı → aksiyonlar sağ köşede |
| `app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIcons.kt` | `build()`: yol başına taze `PathParser` + liste kopyası → ikonların tüm yolları çizilir |

## Regresyon riski

* Yerleşim: yalnızca `profiles` üst çubuğunun iç düzeni değişti; dikey/yatay, telefon/tablet
  ve TV genişliklerinde pill kısalır, butonlar sağda kalır.
* İkonlar: `ImageVector` üretimi aynı API ile, yalnızca yol listesi kopyalanıyor; ikon adları,
  boyutları (`24dp` viewport, `STROKE = 2f`), uyumluluk takma adları (`Icons.Rounded.*`)
  birebir korunuyor.
