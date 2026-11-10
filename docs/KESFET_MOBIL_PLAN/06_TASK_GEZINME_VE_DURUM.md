# 06 — Görev C: Üst sekmeler, kaydırma ve durum
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 05  **Bitiş kapısı:** mobil gesture/navigasyon testleri.

## Yapılacaklar
- [ ] `ExploreScreen` içinde yalnız mobil yeni kabuk: kaynak pill ve tür sekmeleri birbirinden bağımsız; seçili/erişilebilir sekme durumları; `HorizontalPager` mümkünse Compose sürümüyle doğrula, yoksa eşdeğer bileşen.
- [ ] `LazyRow` ile pager gesture çatışması: raf üzerinde yatay hareket önce rafa, boş başlık/hero gibi bölgede sayfaya; sekmeye dokunmak her durumda çalışmalı. Gerçekte Compose pointer/nested scroll davranışını cihazda doğrula; kenar devri kendiliğinden varsayılmasın.
- [ ] Her sekme için ayrı `LazyListState` ve kararlı key; seçili tab + konumları `SaveableStateHolder`/mevcut app state ile test edilebilir şekilde koru. `AppViewModel` bugün tek `exploreScrollIndex/Offset` saklar: mevcut sözleşmeyi bozmadan mobilde tabloya uyarlama veya ayrı saveable state tasarla; uygulamadan önce kaynak değişiminde pozisyon politikasını kararlaştır.
- [ ] Hızlı tab/kaynak değişiminde eski içerik, hero veya async yanıtı yeni sekmeye taşınmasın; tab değişimi yeni kaynak fetch başlatmasın. App arka plan, navigasyon geri dönüşü, döndürme ve process restore sınırları açık belgelenir.
- [ ] Pull-to-refresh, yukarı çık FAB, bottom bar scroll ve inset koordinasyonu ile var olan seçili kaynak restore korunur. Sekme sırası ve ilk açılış seçimi kullanıcı kararına bağlı (`12`).

## Testler
En az bir portre telefon + küçük ekran + tablet/landscape üzerinde manuel/UI test: 4 sekme, kaydırma, dokunma, her rafın içinde sürükleme, yeni tab'a geçiş ve geri dönünce konum, kaynak değişimi, hızlı tekrar, detay/geri, ekran döndürme, yenileme/FAB/alt bar. TalkBack sekme rolü ve seçim durumu test edilir. Android UI otomasyonu mümkün değilse bloke olarak raporla.
