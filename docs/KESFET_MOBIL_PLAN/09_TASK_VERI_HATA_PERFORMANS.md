# 09 — Görev F: Veri yükleme, hata, cache ve performans
**Durum:** [ ] Başlanmadı  **Bağımlılık:** 04–08  **Bitiş kapısı:** yarış/hata/cache testleri + ölçüm.

## Yapılacaklar
- [ ] Mevcut `ExploreViewModel.cancelLoads`, `requestGeneration`, `sourceJobs`, `loadAllSources`, `retrySource`, `loadExploreSource` ve `prefetch` akışlarının baseline ağ sayısını ölç. Tümü'nde yedi kaynağa bağımsız iş; tekrar denenirken diğer veriler kaybolmaz. Tab değişiminde yedi kaynak yeniden çağrılmaz.
- [ ] Hızlı kaynak/tab/refresh/dil geçişi + gecikmiş yanıt senaryosu tasarla: eski generation sonucu yeni ekrana/cache'e yanlış yazılmamalı; `CancellationException` yutulmasın; kişisel listeler hesap değişimi/girişten sonra yanlış hesapta görünmesin.
- [ ] `ExploreCacheEntity`/Gson alanlarına ve `explore_platform_{platform}_{language}_v{titleResolverVersion}` anahtarlarına dokunmadan yeni sunum adaptörünü kullan. Boş/eksik alanlı eski JSON, app upgrade, offline, cache yaşı ve yenileme başarısızlığı test edilir. DB/ayar migration zorunluluğu ortaya çıkarsa ayrı plan/geri dönüş gerekir.
- [ ] `runCatching().getOrDefault(emptyList())` bugün bazı raf ağ hatalarını boş listeye dönüştürüyor; hata/boş/destek dışı ayrımı için öncelikle veri yolu testini yaz ve elde edilemeyen durumda dürüst 'içerik yüklenemedi veya yok' mesajı kullan. Per-raf typed failure eklemek ortak ViewModel davranışını etkiliyorsa TV kapsam dışı kuralı gereği kullanıcı kararı iste.
- [ ] AniList toplu alias ve kısıtlı sorgulama, Jikan hız sınırı, Bangumi başlık zenginleştirmesinde kısıtlı eşzamanlılık, ortak takvim tekilleştirme, artwork arka plan işi ve `remember` hesaplama cache'i korunur. Asılsız hız vaadi yok.
- [ ] Ölçülecekler: soğuk/ılık ilk anlamlı içerik, kaynak başına request sayısı, UI frame/jank, bellek, hata/429; aynı cihaz, sürüm, ağ ve cache koşullarında eski/yeni karşılaştırma. Loglar kimlik/token/kişisel bilgi içermemeli.

## Testler
Fake delayed success/failure/429/timeout, iptal ve force refresh; 7 kaynak içinde 1 failure; stale cache ve bozuk eski JSON; dil/yetişkin ayarı değişimi; rapid source/tab switch; offline sonrası recovery; resim hatası; istek sayısı ve scroll jank ölçüm raporu. Offline/cache içeriği yeni veriymiş gibi gösterilmez. Üretim dağıtımını aşama 11 kapısı olmadan açma.
