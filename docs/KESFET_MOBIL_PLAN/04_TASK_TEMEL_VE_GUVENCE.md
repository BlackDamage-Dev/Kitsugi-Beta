# 04 — Görev A: Mevcut davranışı sabitle ve güvence kur
**Durum:** [ ] Başlanmadı  **Bağımlılık:** yok  **Bitiş kapısı:** temel sonuçlar kaydedildi.

## Yapılacaklar
- [ ] Güncel branch/diff/commit ve kullanıcı değişikliklerini kontrol et; bu plana göre yeniden kapsam kontrolü yap. TV'yi değiştirme; ortak sözleşmelerin mobil/diğer tüketicilerini izle.
- [ ] TR/EN, koyu/açık/AMOLED, kaynak seçicisi, 7 kaynak + Tümü, kategori, hero, kaynak hatası, ekran dönüşü, kişisel raflar, okuyucu, bildirim ve takvim için şu anki telefon davranışını ekran kayıtları + test raporuyla belgeleyerek baseline al; giriş/erişim şartlarını ve denenen veri tarihini belirt.
- [ ] `ExploreScreen` callbacklerini ve `AppRootTabPages` bağlantılarını bir kontrol listesine çıkar; `tmdbModernHomeEnabled`, `separateNovelsManga`, `showAnimeLogos`, adult/score/title ayarlarının mevcut davranışını karşılaştır.
- [ ] Yeni ekranı yalnız mobilde denemek için mevcut ekranı değiştirmeden geri dönüşlü iç geliştirme bayrağının yerini ve uygulamasını netleştir. Sürekli yeni kullanıcı ayarı veya ayar migrasyonu ekleme zorunlu değil; sürümleme/çalıştırma stratejisini kaydet.
- [ ] 3 temel test sınıfı ve Hero policy testlerini çalıştır; gerekiyorsa test altyapısını hazırlayıp neden başarısız/atlandı kaydet.

## Test ve çıkış
`./gradlew :app:testDebugUnitTest --tests '*AllSourcesExploreTest' --tests '*ExploreMappingSourceScopeTest' --tests '*HeroSelectionTest' --tests '*HomeHeroPolicyTest'` ve `./gradlew :app:assembleDebug` komutları uygun JDK/SDK/bağımlılıklı CI veya makinede. Komutları CI yapılandırmasına uyarlarken build variantlarını kontrol et. En az bir telefon baseline ve cihaz/OS bilgisi eklenir. Test ortamı yoksa **bloklu**; asla başarılı diye işaretlenmez. Bu planı hazırlayan ortamda java/Android SDK bulunmadığından çalıştırılmadı.
