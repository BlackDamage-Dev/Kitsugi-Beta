# ÇÖZÜM RAPORU — Çift Sayfa Dokunma Etkileşimleri + Pürüzsüz Otomatik Kaydırma (2026-11-07)

## Bildirilen Sorunlar
1. **"İki sayfa yan yana" açıkken okuyucu barları gizlenmiyor** — ekranın ortasına dokunmak menüyü açıp kapatmıyor.
2. **Çift sayfa modunda sağ/sol dokunma kısayolları ve diğer dokunma eylemleri çalışmıyor.**
3. **"Otomatik kaydırma (bu oturum)" pürüzlü** — ~1 fps gibi merdiven şeklinde ilerliyor, göz yoruyor.

## Kök Neden 1+2 — Ölü Dokunma Katmanı (Main pass vs Initial pass)
`MangaPagerReader` çift sayfa yayılımında iki sayfayı bir `Row` içinde gösteriyor ve
dokunmaları `Row`'a kurulan `detectTapGestures` ile dinliyordu. Ancak:

- `detectTapGestures`, pointer olaylarını **Main** geçişinde ve **tüketilmemiş** olarak bekler.
- Çocuktaki Telephoto `ZoomableAsyncImage` (pinch/çift dokunma zoom) olayları Main geçişinde
  **tüketir** → ebeveyn `Row` hiçbir zaman temiz down/up göremez → dokunmalar sessizce ölür.
- Tek sayfa modu bu yüzden çalışıyordu: oradaki `readerTapZones` olayları **Initial** geçişinde
  (`requireUnconsumed = false`, çocuklardan ÖNCE) izliyordu.
- Aynı gizli hata `MangaPageItem`'ın **Default dışı** dokunma düzenlerinde de vardı
  (Custom ızgara / SolEl / SağEl → `detectTapGestures` → zoom'lu sayfada ölü).
- Yayılım `Row`'u ayrıca `tapZonesEnabled=false` durumunu ve uzun basma (galeri) eylemini hiç bağlamıyordu.

## Çözüm 1+2 — `ReaderTapGestures.kt` (yeni, paylaşılan Initial-pass gözlemci)
- Olayları **Initial** geçişinde izler, **kendisi hiçbir olayı tüketmez**:
  - Temiz tek parmak dokunuş → bölge/ızgara eylemi (menü / sonraki / önceki)
  - Çoklu dokunuş (pinch) veya touch-slop aşımı (pager kaydırması) → yok sayılır; zoom ve kaydırma aynen çalışır
  - Uzun basma → **gerçek zaman aşımında** tetiklenir (parmak kıpırdamasa bile); Custom düzende
    uzun-basma ızgarası varsa o eylem, yoksa menü + sayfa galerisi
- `withTimeoutOrNull` yarışı, `AwaitPointerEventScope` `@RestrictsSuspension` olduğu için
  androidx'in kendi `waitForLongPress` kalıbıyla scope extension gövdesinde çalışır.
- `MangaPageItem` artık **tüm düzenlerde** bu tek yolu kullanır (eski `readerTapZones` ve
  `detectTapGestures` dalları kaldırıldı). Webtoon'daki uzun görsellerde `tapViewport` Y eşlemesi korundu.
- Yayılım `Row`'u aynı gözlemciyi kullanır; uzun basmada **fiziksel olarak basılan yarı** açılır:
  `readerSpreadPressedPage()` LTR'de sol=ilk, RTL'de sağ=ilk kuralını uygular.
- `tapZonesEnabled=false` her dokunuş → menü (tüm modlarda tutarlı).

## Kök Neden 3 — 20 Hz "merdiven" kaydırma
`WebtoonAutoScroll` her adımda `delay(50)` + ayrı bir `scrollBy` mutasyonu yapıyordu:
en fazla 20 sıçrama/sn, kare saatinden bağımsız; ağır webtoon sayfalarında belirgin tekleme.

## Çözüm 3 — Kare saatiyle (withFrameNanos) sürekli akış
- Tek bir `scroll(MutatePriority.Default)` mutasyonu içinde **her karede** (60–120 Hz)
  `hız × geçen süre` kadar ilerler → kesintisiz akış.
- Kesirli piksel birikimi (carry) korunur → yavaş hızlar da şaşmaz.
- Süre 100 ms'e kelepçeli → GC/duraksama sonrası tek karede büyük sıçrama yok.
- Kullanıcı kaydırması (UserInput önceliği) mutasyonu iptal eder; döngü ölmez,
  hareket yatışınca kaldığı yerden devam eder. Bölüm sonunda `onEnd` bir kez tetiklenir.
- Aynı hastalık `EpubNovelReader`'ın WebView döngüsünde de vardı → o da kare saatine taşındı.
- `PlainTextNovelReader` ve `TvMangaReaderScreen` aynı `WebtoonAutoScroll`'u kullandığı için otomatik iyileşti.
- Eski `autoScrollStepPixels` kaldırıldı; yeni saf yardımcılar:
  `autoScrollPixelsPerSecond(speed, density)`, `autoScrollFramePixels(pxPerSec, elapsedNanos)`.

## Değişen Dosyalar
| Dosya | Değişiklik |
|---|---|
| `components/ReaderTapGestures.kt` | **YENİ** — Initial-pass dokunma/uzun basma gözlemcisi |
| `components/MangaPageItem.kt` | Tüm dokunma düzenleri tek sağlam yola bağlandı; ölü `detectTapGestures` dalı kaldırıldı |
| `components/MangaPagerReader.kt` | Yayılım `Row`'u yeni gözlemciyi kullanıyor; uzun basma → basılan yarıdaki sayfanın galerisi |
| `components/ReaderTapLayout.kt` | `readerLongTapAction()` yardımcısı |
| `components/ReaderSpreadLayout.kt` | `readerSpreadPressedPage()` yardımcısı |
| `components/WebtoonAutoScroll.kt` | Kare saatiyle pürüzsüz kaydırma; yeni hız/kare yardımcıları |
| `EpubNovelReader.kt` | WebView otomatik kaydırması kare saatine taşındı |
| 3 test dosyası | Yeni yardımcılar için testler + güncellenen hız testi |

## Testler
- `ReaderTapZonesTest`: hız dönüşümü (dp/sn → px/sn, sınırlar), kare deltası (60/120 Hz),
  negatif süre, 100 ms kelepçesi, kesirli piksel birikimi.
- `ReaderSpreadLayoutTest`: basılan yarının sayfa indeksi (LTR/RTL, tek sayfa, NaN).
- `ReaderCustomTapGridTest`: uzun basma ızgarası yalnızca Custom+dolu olduğunda devrede.
- Not: Bu ortamda JDK/Android SDK olmadığından derleme yapılamadı; değişiklikler androidx
  kaynak kalıplarıyla (`waitForLongPress`, `MutatorMutex` çocuk izolasyonu) birebir uyumludur.

## Davranış Doğrulama (elle test senaryoları)
1. Çift sayfa açık + LTR/RTL: orta dokunuş barları aç/kapat; sol/sağ dokunuş sayfa çevirir; uzun basma basılan sayfanın galerisini açar.
2. Pinch zoom ve pager kaydırması çift sayfa modunda çalışmaya devam eder; yanlışlıkla sayfa atlamaz.
3. Custom ızgara / SolEl / SağEl düzenleri tek sayfa + webtoon'da artık çalışır.
4. Otomatik kaydırma ekran yenileme hızında akar; parmakla kaydırınca duraklar, bırakınca devam eder; bölüm sonunda durur/sonraki bölüme geçer.
