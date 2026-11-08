> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# 07 — Görev D: Onaylı görsel dil / raflar / vitrin
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 05 + 06  **Bitiş kapısı:** kullanıcı görsel incelemesi ve mobil ekran testleri.

## Yapılacaklar
- [ ] [Onaylı görseli](https://drive.google.com/file/d/12-pwLJS1rRMvVUPvCkuwB3c0I6fa52Q0/view) referans al: başlık/kaynak → tür sekmeleri → kompakt vitrin → kaynak etiketli yatay afiş rafları. İçerik gerçek API'den, uydurma görsel/puan yok; kaynak logoları mevcut bileşenlerle.
- [ ] Koyu için tokenlar (`#080A10`, `#10131D`, `#171B28`, `#F4F7FB`), ancak kodda `KitsugiColors`/`LocalKitsugiAccent`/`OnAccent` ve gradient API kullan. Açık ve AMOLED temaları + seçili kullanıcı vurgu ve kontrastı koru.
- [ ] `KitsugiHorizontalMediaSection`/`KitsugiExploreMediaCard` global görünümünü değiştirmek yerine mobil keşfet sarmalayıcısı veya isteğe bağlı ve geriye uyumlu parametre kullan. Kaynak başlığı ve 'Tümünü gör' için erişilebilir tıklama hedefleri; eksik görüntü fallback, liste rozeti, skor gizleme, başlık dili, adult blur ve uzun basma korunmalı.
- [ ] Hero adaylarını aktif sekmeyle sınırla; eski `HomeHeroPolicy.isHomeHeroMedia` ve paylaşılan video hero kitapları bilinçli reddediyor. Manga için ilk sürümde hero yoksa kompakt alanı atla veya nötr başlık göster; manga hero istenirse ayrı mobil kitap bileşeni ve ayrı test, mevcut global politika değiştirilmez. Vitrin seçiminde source/ID ve arkaplan güvenliği korunmalı.
- [ ] Var olan hero autoplay ve poster/artwork seçimiyle tab pager/raf çakışmasını test et; davranışı değiştirmek gerekiyorsa mobil-only kontrollü tasarla. Hızlı kaynak geçişinde eski vitrin gösterme, yükleme sırasında sabit yer tutucu.

## Testler
Boş/tek/çok kart, uzun TR/EN başlık, eksik/yanlış oranlı artwork, yatay kaydırma ipucu, büyük font, dark/light/AMOLED + custom accent/gradient, hideScores ve blur, içerik doğru tab'da. 360dp telefon ve tabletlerde ekran görüntüsü incele; afişlerin renkleri tasarım şartı değildir. Mevcut hero testleri korunur.
