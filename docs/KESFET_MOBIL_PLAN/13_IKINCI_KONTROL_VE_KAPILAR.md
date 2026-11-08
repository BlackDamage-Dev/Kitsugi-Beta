> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# 13 — İkinci kontrol: somut boşluklar ve kapanış kapıları

**Tarih:** 2026-11-08 · **Temel:** `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350` · **Yöntem:** statik inceleme; cihaz/API/derleme testi değildir. **TV:** kapsam dışı. Bu dosya önceki görevleri silmez; aşağıdaki daha somut koşullar önceki genel maddeleri tamamlar. Üç fonksiyonun v1 Kotlin imzaları ve dosya sahipliği değişmedi. Bu denetim her olası sorunu bulduğunun veya uygulamanın hatasız olacağının garantisi değildir.

## Bulgu 1 — API isteği ile afiş yükleme birbirine karışmıştı
- **Kanıt:** İş 2 talimatı 'istek yapmaz/ağa çıkmaz' derken, zorunlu `KitsugiHorizontalMediaSection` → `KitsugiExploreMediaCard` → Coil `AsyncImage` yolu gerçek `imageUrl` varsa görüntü yükleyebilir.
- **Düzeltme:** İş 2 katalog API'si, kaynak verisi, kimlik doğrulama ve yeni ağ istemcisi yazmayacak. Mevcut kart bileşeninin gerçek afişi uygulamanın mevcut image loader'ı üzerinden indirmesi normaldir. Testlerde ağsız/null image fixture veya test image loader kullanılır. Başka cache/izin eklenmez.
- **Sahip/test:** İş 2 bileşen kodu ve test raporu; İş 1 entegrasyonda ağ taleplerini kontrol eder.

## Bulgu 2 — Onaylı görselin boyutu mevcut kartla otomatik aynı değil
- **Kanıt:** `KitsugiHorizontalMediaSection.kt` telefonda 180 dp, landscape 260 dp kart genişliği ve `forceVertical=false` kullanıyor; `KitsugiExploreMediaCard.kt` portre metin alanı 182 dp, şekil 24 dp. Onaylı konsept daha kompakt posterler gösteriyor. Aynı komponentin dışına başlık koymak bu farkı ortadan kaldırmaz.
- **Düzeltme/kapı:** Donmuş v1 sözleşmesi İş 2'nin mevcut rafı kullanmasını gerektirir; ortak kodu veya API'yi izinsiz değiştirme. İş 2 mevcut ölçülerle ilk UI'ı hazırlayıp gerçek ekran/preview görüntüsünü ve görünür poster sayısını teslim raporuna koyar. Görsel uyum kullanıcıyla karşılaştırılır. Kompakt kart gerekirse sözleşme/izinli dosyalar için **ayrı sürüm ve her iki ajan onayı** alınır; aksi hâlde geçici fark açıkça bildirilir. Ürün onayı olmadan otomatik 'tasarım birebir' deme.
- **Sahip/test:** İş 2 görsel karşılaştırma; İş 1 entegrasyonda küçük ekran ve landscape ölçümü.

## Bulgu 3 — Eylemler için somut eşleme ve dar ekran
| Mevcut işlev | Kod kanıtı | Koruma/önerilen mobil yer | Sorumlu |
|---|---|---|---|
| Kaynak seçici ve sheet | `ExploreScreen.kt` / `ExplorePlatformToggle.kt` | Başlık alanındaki seçici; 7+Tümü sheet ve seçimi kaydetme aynı akış | İş 2 pill/callback, İş 1 sheet/state |
| Rastgele 🎲 | `ExploreScreen.kt` | `actions` alanında veya erişilebilir taşma menüsünde; mevcut kapsamı değiştirmeden | İş 1 eylem, İş 2 slot |
| Bildirim | `ExploreScreen.kt`, `isNotificationsVisible` | Yalnız mevcut koşulda header action; açılır sayfa/giriş kontrolü korunur | İş 1 eylem, İş 2 slot |
| Kategori kısayolları / katlama | `ExploreCategories.kt`, `AllSourcesExploreContent.kt` | Kategoriye ve kaynak hata/tekrar denemeye erişim kaybolmasın; yeni konum ürün kararı | İş 1 |
| Takvim, geri sayım | `ExploreComponents.kt`, `ExploreScreen.kt` | Takvim butonu ve ortak veri kökeni korunmalı; uygun sekme/yerleşim netleştirilmeli | İş 1 |
| Manga okuyucu | `ExploreCategories.kt` | Manga sayfasında/kısayol alanında erişim; son yerleşim kullanıcı onayına | İş 1 |
| Simkl izlemeye devam | `TmdbExploreContent.kt` | `Tümünü gör` **izleme geçmişine** gider; sıradan katalog grid'ine yönlendirme | İş 1 |
| Simkl planlananlar | aynı dosya | Sıradan devam değil: kaynak, kategori ve kişisel liste başlığı grid'e taşınır | İş 1 |
| Ayarlar/oturum/hata/retry | `ExploreScreen.kt`, `AllSourcesExploreContent.kt` | TMDB anahtarı, giriş, error code, tek-kaynak retry ve eski veri uyarısı korunur | İş 1 |
| FAB/scroll/refresh | `ExploreScreen.kt`, `RetainedLazyListState.kt` | Her tab ve kaynak durumu ile geri dönüş; refresh ve bottom bar koordinasyonu | İş 1 |

İş 2 başlık `actions` slotuna gelen içeriği yutmaz; **320–360 dp, büyük font, uzun kaynak adı ve 2 action** kombinasyonlarında tüm eylemler erişilebilir olmalı. 48 dp tıklama alanı için mevcut source pill'in gerçek dokunma sınırı cihaz/Compose testinde doğrulanır; yalnız görsel boyut yeterli değildir. Yerleşim sığmazsa sessizce gizleme; teslim raporunda İş 1'e aktar.

## Bulgu 4 — Boş raf ve Tümünü gör semantiği
- **Kanıt:** `KitsugiHorizontalMediaSection` `shouldRender = results.isNotEmpty() || isLoading || onSeeAllClick == null` kullanır. `showTitle=false` olsa bile `onSeeAllClick` iç davranışa etki eder: boş + null → içerik yok metni; boş + dolu → hiç çizilmeyebilir. Sadece header butonunun gizlenmesi problemi çözmez.
- **Test kapısı:** İş 2 dört durumu test edip aynen raporlar: boş + yükleniyor; boş + yüklenmiyor/null callback; boş + yüklenmiyor/dolu callback; dolu + her callback. İş 1, hangi boş rafın oluşturulacağını ve durum mesajlarını sahiplenir. Ortak raf API'sini değiştirme.
- `title` aynı olduğunda mevcut `rememberSaveable(title, LazyListState.Saver)` raf konumları ve farklı kaynak/sekme örnekleri birbirine karışmamalı: İş 1 her render için `source + category + tab + origin` ile kararlı Compose key sınırları oluşturur; aynı başlıklı iki rafın dönüşte pozisyonu test edilir. Display title kimlik yerine kullanılmaz.

## Bulgu 5 — Tema ve kaynak rengi ayrı karar
- **Kanıt:** `AllSourcesExploreContent.kt` kaynak raflarını `CompositionLocalProviderForSource` ile kaynak rengine boyuyor. Diğer modlarda kullanıcı `LocalKitsugiAccent` seçiyor; gradient ayrı CompositionLocal değerleriyle çalışıyor.
- **Kapı:** Yeni mobil tasarımda seçili kullanıcı teması/degradesi temel; kaynağın kimliği logo/metin ile açık. Kaynağa özel aksan korunacak mı, yoksa görsel gibi tek kullanıcı vurgusu mu kullanılacak, kullanıcı tarafından kararlaştırılmalı. Küçük kontrast, `OnAccent`, gradient ve kaynak rozetleri dark/light/AMOLED testleri geçmeden stil hazır sayılmaz. İş 2 global tema değiştirmez; İş 1 bu tercih için kullanıcı onayı alır.

## Bulgu 6 — Liste başlığı ve devam hedefi aynı string olmamalı
- **Kanıt:** `AllSourcesExploreContent.kt` içindeki `localizedExploreSectionTitle` private ve Türkçe string eşlemesine bağlı; `FullScreenMediaGridPage.kt` Simkl kişisel listeyi yerelleştirilmiş başlık `contains`, TMDB kişisel listeyi Türkçe `startsWith` ile tanıyor.
- **Kapı:** İş 1 TR/EN ekran başlığını, raf source + category/origin ve mevcut grid'in semantiğini ayrı test eder. Gelişigüzel kaynak öneki/metin çevirisi kişisel listeleri bozabilir. İzinli ve uyumlu explicit origin çözümü tasarlanmadan eski sözleşme değiştirilmez; tarih/puan sırası sayfa 1/2 test edilir.

## Bulgu 7 — Build variant ve ortam
- **Kanıt:** `app/build.gradle.kts` `foss` ve `gms` flavor, debug/release, compileSdk 36, minSdk 26, Java 17 hedefi ve CMake 3.22.1 içerir. Önceki genel Gradle görevleri flavor'a göre doğrulanmamıştı.
- **Kapı:** Yetkili JDK, SDK, CMake/NDK ve build bağımlılıkları olan ortamda önce `./gradlew :app:tasks --all` ile gerçek variant görevleri tespit edilir. FOSS/GMS debug derleme + ilgili unit/UI test görevleri gerçekten çalıştırılır; release/shrink için dağıtım öncesi uygun build ayrıca doğrulanır. Komut adı veya test sonucu tahmin edilmez. Emülatör/cihaz yoksa UI testleri bloklu kaydedilir. Mevcut ortamda Java/SDK bulunmadığı için sonuçlar hâlâ bilinmiyor.

## Bulgu 8 — Diğer ajanın paketi ve bağımlılık teyidi
- Görsel referans ve tam görev talimatı ayrı, izole bir handoff klasöründedir; önceki araştırmalar iş emri değildir. UI paketindeki beş izinli dosya aynı kalır. Kaynaklarda mevcut bileşen kullanımı için başka dosya değiştirilmesi gerekirse İş 2 durup entegrasyon sorumlusuna sorar.
- İş 2 tesliminde fonksiyon imzaları, `internal` görünürlük, kaynak dosyaları, test kanıtı, fixture içerikleri ve kaynaklarına ilişkin inceleme yapılır. Ajanların ayrı kopyalarda aynı temel commit ve kullanıcının gerekli güncel değişikliklerini kullandığı teyit edilir; ZIP dosya listesi/çatışmalar kontrol edilmeden uygulanmaz. Kullanıcı dosyaları üzerine yazılmaz.
- Handoff'taki doküman düzenlenmişse veya kullanıcı/diğer ajan onun eski bir kopyasını aldıysa **yeniden paylaşılması gerekir**; dış ajana bildirim otomatik yapılmaz. Kotlin imzası değişmedi.

## Durum ve kapanış
Bu maddelerin tamamı şu anda **planlandı, uygulanmadı ve test edilmedi**. Butonların kesin yerleşimi, manga vitrin kararı, ilk sekme/kalıcılık, Tümü kaynak sırası, modern/klasik ayar, rastgele davranışı, renk tercihleri ve kategori kısayolları kullanıcıyla netleştirilmeyi bekler. Aşama kapıları ve 32 kombinasyon testi sonucu olmadan 'eksiksiz/kusursuz' iddiası yapılmaz. Uygulamanın diğer sayfalarına yayılan ortak davranış değişikliği gerekiyorsa ayrıca kapsam kararı alınır.
