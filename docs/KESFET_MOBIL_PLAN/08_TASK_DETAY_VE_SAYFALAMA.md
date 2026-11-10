# 08 — Görev E: Kart, detay ve Tümünü gör sözleşmesi
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 05  **Bitiş kapısı:** kaynak/sıralama/tür eşitliği doğrulanmış her görünür raf.

## Yapılacaklar
- [ ] `AppRoot.openApiDetail` ve mevcut kart tıklama, uzun basma, liste ekle/düzenle, Bangumi özel akışı, doğru source+type+native ID ile korunur. `ExploreMapping.getMediaEntryFromMap` / `matchesInSource` davranışı fixture ile doğrulanır.
- [ ] Her yeni mobil rafın `platform + category + origin/query kind` ve ilk liste/pagination uyumunu `FullScreenMediaGridState` → `FullScreenMediaGridPage` → `ExploreSourceFilterQuery` çağrı zinciri için tespit et. En azından simkl trend/yayında finite chart, ilk sayfanın tekrar çekilmesi; Bangumi REAL offset; TMDB karma raf; AniList manhwa ve roman; MAL yetişkin filtresi kontrol edilsin.
- [ ] Yeni query kimliği gerekiyorsa mevcut `onSeeAllSection` çağıranlarını kırmayan **eklemeli ve mobil kapsamlı** yaklaşım tasarla. Başlık metninden kişisel Simkl listesi tanıyan mevcut `title.contains` davranışı nedeniyle metin çevirisi/yeniden isimlendirme sessizce yapılmamalı; explicit personal/query spec eklemenin paylaşılan etki riski değerlendirilir.
- [ ] İlk ve sonraki sayfalarda aynı tür ve sıralama uygulanamıyorsa yanıltıcı sonsuz kaydırma yerine mevcut tür-özel endpoint veya açıkça sonlu sonuç seç; filtrelerde sunucu/yerel destek işaretleri korunur. Aynı öğe, her sayfada tür+kaynak kimliğiyle tekilleştirilir. Yanlış sayfa 2, boş sayfa döngüsü ve sınırsız yüklemeyi engelle.
- [ ] Geri dönerken `savedStateKey`, grid/sekme/raf dikey-yatay pozisyonu ve kullanıcının kaynak tercihi korunur.

## Testler
Her temsilî platform/tür için ilk sonuç + sayfa2 + filtre + sıralama + geri + tekrar açma; kaynaklar arası aynı sayıdaki ID; aynı isim film ve dizi; adult on/off; başarısız/boş sayfa; Simkl girişli kişisel raf ve girişsiz; Bangumi REAL; TMDB anime-film; AniList Kitsu fallback. Destek eksikliği not edilip etkilenen buton güvenli davranmalı. Diğer ekranların grid kullanıcılarına regresyon yapılmamalı.
