# PLAN & TASK — Manga Keşfet başlığı + okuyucu panelleri (2026-11-07)

> Kullanıcı bildirimi (ekran görüntüleriyle):
> 1. **Manga Keşfet** ekranında işaretlenen üst blok (üst bar + arama + sıralama) aşağı/yukarı
>    kaydırırken düzensiz "yok olup geri geliyor"; altındaki kaynak çipleri ve kartlar
>    başlıkla **birlikte** akmıyor. Yumuşak animasyon + doğru düzen isteniyor.
> 2. **Okuyucu klavye kısayolları**: alttan açılan bir paneli açan tuşa **tekrar** basınca
>    o panel kapanmalı (yalnızca panel açan tuşlar için).
> 3. **Parlaklık**: ayarlar sayfasındaki sürgü kalsın; ayrıca okuyucunun alt çubuğu
>    bölgesinde **açılır/kapanır** ikinci bir parlaklık şeridi olsun.

## T1 — Manga Keşfet: kaydırmayla birlikte akan (coupled) başlık
- [x] Nested scroll ile kaydırma **tüketimini** kaldır (kartlar artık ilk pikselden itibaren akar).
- [x] Başlığı ızgaranın **0. öğesi** yap (tek kaydırma ekseni, zıplama yok).
- [x] Yapışkan kaynak çubuğunu başlığın **ölçülen konumuna** bağla; tepede sabitlensin.
- [x] Çubuk için ayrılmış boşluk öğesi (kartlarla çakışma yok).
- [x] Başlık zemini (scrim) tam genişlikte, `zIndex(-1)` ile ızgaranın **altında** çizilsin
      (12 dp iç boşluk kenarda iz bırakmasın, kare gecikmesi olmasın).
- [x] Yumuşak solma: başlık yukarı kaydıkça içerik %35, perde %25 soluyor.
- [x] "Aramaya dön" / "arama adını değiştir" akışları `animateScrollToItem(0)` + güvenli odak.

## T2 — Okuyucu: panel açan tuş = aynı tuşla kapat
- [x] `ReaderPanel` numaralandırması + `ReaderKeyContext.openPanel` (tuş politikası).
- [x] `ReaderPanel`'e `toggleKeys` parametresi (S / C / G / H,?,F1) — panel açıkken aynı tuş kapatır.
- [x] `dispatch` içinde `Settings/Chapters/GoToPage/Help` artık **toggle** eder.
- [x] Kısayollar panelinde bilgi satırı + TR/EN metinler.

## T3 — Okuyucu: alt çubukta hızlı parlaklık şeridi
- [x] Alt çubukta parlaklık düğmesi (yeni ikon, kısayol tuşu yok → etiket temiz).
- [x] `expandVertically + fadeIn` / `shrinkVertically + fadeOut` ile açılır/kapanır şerit:
      düşük ikon, sürgü, % etiketi, "sıfırla" (tek dokunuş %100).
- [x] Ayarlar panelindeki `Parlaklık` sürgüsü **aynen** korunuyor.
- [x] Kontroller veya bir panel kapandığında şerit kendiliğinden kapanır.

## Kabul ölçütleri
- Kaydırırken başlık + çipler + kartlar tek parça gibi hareket eder; hiçbir karede sıçrama yok.
- Başlık tamamen yukarı çıktığında kaynak çubuğu tepede yumuşakça sabitlenir.
- `S`, `C`, `G`, `H`/`?`/`F1` tuşlarına ikinci kez basmak açtıkları paneli kapatır.
- Alt çubuktaki parlaklık düğmesi şeridi açar/kapatır; ayarlardaki sürgü çalışmaya devam eder.
- `MangaReaderRegressionCases` yeni "panel aç/kapa" senaryosuyla geçer.
