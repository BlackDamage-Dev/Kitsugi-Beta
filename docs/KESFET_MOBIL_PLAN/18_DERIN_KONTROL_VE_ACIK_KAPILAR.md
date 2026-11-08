# 18 — Mobil Keşfet derin kontrol ve açık kabul kapıları

Tarih: 2026-11-08 · Dal: `arena/7e4f350c-kitsugi-beta` · Baz: `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350`

## Dürüst sonuç

Bu bir kaynak kodu ve izole kontrol incelemesidir. **Yeni Keşfet gerçek ekrana bağlı değildir; Android derlemesi, ağ/cihaz testleri ve görsel kabul tamamlanmamıştır. Eksiksiz olduğu garanti edilemez.** TV kapsam dışıdır. İş 2 raporu 39 test ve ilk teslim SHA'larını açıklayan tarihsel belgedir; aşağıda tarif edilen düzeltmelerden sonra çalışma kopyasındaki iki İş 2 dosyasının hash'i doğal olarak değişmiştir. Orijinal ZIP Drive'da değişmeden durur.

## Bu kontrolde saptanan ve düzeltilenler

1. **Hata ≠ boş katalog:** `MobileExplorePageData.showNoShelves`, hata almış sayfada veya Tümü'nde kısmi kaynak hatasında yanlışlıkla boş durumuna izin veriyordu. Hata varken boş başarı gösterilmez; başarısız/henüz bilinmeyen boş içerik scroll konumunu placeholder'a sıfırlamaz. Idle fakat `payload=null` de henüz sonuç değildir. Hatalar `sourceStatus` slotunda ayrıca gösterilmelidir. İzole testlerle kaplandı.
2. **Simkl yaklaşan animeleri:** gerçek `loadSimklData` anime/best/upcoming sonucunu eski `upcomingMediaTmdb` alanında tutuyor; ortak `sourceSections` Simkl için bu alanı okumuyordu. Mobil mapper artık gerçek Simkl öğelerini Anime sekmesinde `Yaklaşan Animeler` rafı olarak tutuyor, TMDB endpoint'ine göndermiyor, kısıtlı grid kullanıyor. İzole test eklendi; canlı API doğrulaması değildir.
3. **İş 2 testinde derleme engeli:** `MobileExploreVisualsTest.shelf` composable çağırdığı halde `@Composable` değildi. İşaret eklendi; Android derlemesi yine yapılmadı, başka hata olmadığının kanıtı değildir.
4. **Test doğruluğu:** pasif hero testleri engelli düğmeye `performClick` yerine `assertIsNotEnabled` uygular. Sadece `LocalConfiguration` değiştirmek gerçek Compose ölçüsünü değiştirmediğinden test kutusuna istenen boyut eklendi. Gerçek kullanılabilir test penceresi kutuyu sınırlayabilir; çoklu cihaz/ekran testinin yerini tutmaz.
5. **Erişilebilirlik:** aktif hero için genel başlığın yanında seçilen gerçek içerik semantik açıklamaya eklendi; cihazda TalkBack kontrolü açık.
6. **Yatay konum:** raf state'i çevrilen görünen başlıkla sıfırlanmıyor. `MobileExploreHost` kararlı kaynak/tür/kategori/origin anahtar sınırını kullanıyor; başlık değişikliği testi eklendi ama cihazda çalıştırılmadı. Host dışında doğrudan shelf kullanacak çağıran kendi kararlı kimlik sınırını sağlamalıdır.
7. **Dikey düzen:** görsel köprüde hero öncesi/sonrası ve raf aralıkları eklendi; ölçüler gerçek render/screenshot ile henüz doğrulanmadı.

## Kod ve kapsam kontrolü

| Alan | Yapılan doğrulama | Açık olan |
|---|---|---|
| 7 kaynak + Tümü / 4 tür | Kaynak yeteneği, kaynak adı, tür, TMDB karışık raf, MAL/Jikan alias, Bangumi namespace, AniList→Kitsu ve Simkl kişisel raf kodu ile sentetik 32 kombinasyon kontrolü | Gerçek ağ sonuçları ve tüm sayfaların cihazda uçtan uca doğrulanması |
| Simkl sayfalama | Trend/best/upcoming ve payload alanları gerçek ViewModel kodundan karşılaştırıldı | API endpoint ve sayfa 2; finite grid tüm katalog değildir |
| Kaynak durumları | Bilinmeyen/loading/hata/cache/stale/boş ayrımı saf modellerde incelendi | Seçili kaynak ViewModel'i her kategori hatasını ayrı tutmuyor; birçok çağrı `runCatching` ile boş dönüyor. Gerçek entegrasyonda kısmi hata ayrı başarı diye iddia edilemez |
| Yetişkin ve kimlik | Filtre öncesi hero, kaynak+tür+kimlik ve blur parametresi incelendi | Gerçek cihazda blur, API kimliği ve tüm navigasyon yolları |
| Görsel ve tema | 4 bileşen sözleşmesi, TR/EN yeni string çakışması, token, dar/büyük font için kod incelendi | Gerçek Compose render, inset, font, 48dp, kontrast, gesture, 384dp ve diğer gerçek ekran ölçüleri; referansa birebir olduğuna dair iddia yok |
| State ve gezinme | Kaynak/sekme/raf kimlikleri ve opt-in finite grid yolu incelendi | Detay → geri, sistem geri, scroll ve ekran rotasyonu; sayfa 2; toplu ekleme gibi eski grid davranışları |
| Eski ekran eylemleri | ExploreScreen'deki kaynak sheet, refresh, rastgele, bildirim, takvim, okuyucu, FAB/alt bar ve hata/yeniden dene tespit edildi | Yeni host bunları gerçek uygulamada henüz bağlamıyor; birebir korunmaları cihaz/test şartı |
| Doküman ve yedek | İş 2 orijinal ZIP ve 5 dosya SHA doğrulaması, yerel değişiklikler ve rapor | Çalışma kopyası henüz commit/push edilmedi; Drive ZIP tam depo veya otomatik senkronizasyon değildir |

## Kontroller

- **27/27 izole Kotlin kontrolü geçti:** gerçek mapper/content/navigation dosyaları + sahte model/JUnit/navigasyon stand-in'leri + mevcut sourceSections çıkarımı. Gerçek Android model/build, ağ, UI veya cihaz testi değildir.
- **49 cihaz testi yazılı, 0 çalıştırıldı:** orijinal İş 2'nin 39 testine 1 yatay scroll testi ve İş 1'in 9 testi dahil. Bazı testlerde cihaz boyutu/semantik/ağlı görsel yükleme kaynaklı davranışların cihazda düzeltilmesi gerekebilir.
- Kotlin tree-sitter sözdizimi 19/19 parse; TR/EN XML ayrıştırma ve `mobile_grid_` / `mobile_explore_` adlarında kaynak çakışması yok; `git diff --check` temiz. Parser tür denetimi/Compose plugin/Gradle değildir.
- Java 17 ve Kotlin 2.0.21 için yalnız izole geçici araçlar var. Gradle 8.13 dağıtım indirmesi SSL nedeniyle başarısız; Android SDK ve cihaz yok.

## Etkinleştirmeden önce zorunlu kalanlar

1. `ExploreViewModel` ile kaynak-snapshot sahipliğini atomik kur; Tümü ve seçili kaynakta güncel gerçek veri, cache, kısmi hata ve Simkl kişisel içeriği güvenle bağla.
2. Mobil gerçek ExploreScreen, mevcut source sheet/refresh/actions/alerts/retry/scroll/FAB/bottom bar/erişilebilirlik ve detay/uzun basma/takvim/okuyucu dönüşlerini koruyacak şekilde bağla. Global üst barı çift çizme. TV yoluna dokunma.
3. Raf başlıklarını TR/EN kategorilere ve kişisel listeye göre yerelleştir; kaynak-bazlı tema ve tüm mevcut ayarları doğrula.
4. Her kaynağın tüm raflarında birinci sayfa ve sayfa 2 sorgu kimliğini, sıralama/filtre/erişkin kısıtlarını, Simkl kişisel ve Kitsu fallback özel yollarını doğrula. Finite grid yalnız geçici, açıkça etiketli sınırdır; toplu ekleme dahil özellik farkları vardır.
5. SDK/Gradle bulunan ortamda FOSS/GMS debug build, gerçek JVM regresyonları ve tüm 49+ Compose testini çalıştır; hataları düzelt. Ağlı gerçek senaryolar, offline/cache, hesaplı/hesapsız, hızlı kaynak geçişi ve düşük bellek stres testi yap.
6. Gerçek telefon/tablet, açık/koyu/AMOLED, büyük font, ekran yönü, TalkBack/kontrast, insets ve 32 mod/tür kombinasyonu; onaylı referansla gerçek screenshot piksel/ölçü karşılaştırması ve kullanıcı kabulü.
