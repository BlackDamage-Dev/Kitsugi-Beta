> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# Diğer yapay zekâya verilecek görev — İŞ 2: mobil görsel bileşenler

Bu metni ve yanındaki `01_ORTAK_SOZLESME.md` içeriğini diğer yapay zekâya birlikte ver. Bu oturumda diğer yapay zekâ otomatik çalıştırılmamaktadır.

---

Senin görevin Kitsugi Android uygulamasının **sadece mobil Keşfet tasarım bileşenlerini** bağımsız hazırlamak. Başka bir ajan aynı anda 7 kaynak veri modeli, gerçek ekran, pager, navigasyon ve entegrasyon üzerinde çalışıyor. Birbirinizin dosyalarını değiştirmeyin. Ana görsel referansı: https://drive.google.com/file/d/12-pwLJS1rRMvVUPvCkuwB3c0I6fa52Q0/view . Görsele erişemiyorsan kullanıcıdan görselin kendisini iste, varsayım yapma. TV kapsam dışı.

**Temel commit:** `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350`. Kullanıcının mevcut değişikliklerini silme/üzerine yazma. Aynı çalışma dizininde iki ajan eşzamanlı yazmasın: ayrı çalışma kopyası kullan; Git branch değiştirme/oluşturma, commit, push veya merge yapma. Ayrı kopya yoksa bunu bildir ve bekle. Bu iş paketini yalnız izinli dosyalarla teslim edeceksin.

## Yalnız şu yeni dosyaları oluşturabilirsin
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisuals.kt`
2. `app/src/main/res/values/mobile_explore_strings.xml`
3. `app/src/main/res/values-en/mobile_explore_strings.xml`
4. `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisualsTest.kt`
5. `docs/KESFET_MOBIL_PLAN/IKI_IS_PARCACIGI/IS_2_TESLIM_RAPORU.md`.

Dosyalardan biri zaten varsa üzerine yazmadan dur; başka dosyaya ihtiyaç duyarsan önce sor. `ExploreScreen.kt`, ViewModel, `AppRoot*`, veri/client/cache, grid sayfası, Gradle, ortak tema/kart/hero kodu ve TV hiçbir koşulda değiştirme. Yeni bağımlılık ekleme. Uygulamaya bağlama/feature flag açma. UI yalnızca parametreleri gösterir ve callback çalıştırır; katalog verisini yönetmez, katalog API isteği yapmaz, sıralamaz, kaynak veya medya türü seçmez; mevcut afiş bileşeninin Coil üzerinden görsel yüklemesi mümkündür.

## Sözleşme
Yanındaki `01_ORTAK_SOZLESME.md` içindeki **üç Kotlin fonksiyonunun paketini, tiplerini, parametrelerini, sırasını ve isimlerini birebir uygula**: `MobileExploreHeader`, `MobileExploreTabs`, `MobileExploreShelf`. Sekme sırası `0 Diziler`, `1 Filmler`, `2 Anime`, `3 Manga`. Kaynak seçici mevcut `ExploreSourceEngineSelectorPill`; yatay kartlar mevcut `KitsugiHorizontalMediaSection(showTitle = false)` üzerinden kullanılır. Üç işlev yeni mobil dosyada bulunur ve `internal`dır. Kaynak seçimini, sayfalar arası swipe/pager'ı, gerçek hero içeriğini, yükleme hatasını ve navigation'ı kendi başına ekleme. `actions` slotu mevcut eylemlerin barınması içindir ve boş dahi olabilir.

Onaylı görselin diline yakın temiz başlık, seçili sekmede tema vurgusu, kaynak etiketi ve yatay raf sun; gerçek renkleri `KitsugiColors`, `LocalKitsugiAccent`, `OnAccent` gibi tema değerlerinden al. Hardcoded mint veya poster/puan/katalog verisi yok. Hem açık hem koyu hem AMOLED temayı ve kullanıcı vurgularını koru. Yeni metinleri TR/EN `mobile_explore_` önekli kaynaklarla yaz. `onSeeAllClick == null` ise Tümünü gör butonu gösterilmez. Kart, long click, source ve tab callbackleri olduğu gibi iletilir. `source=ALL` beklenmese bile crash olmamalı. Ekran okuyucu, 48dp hedef, büyük yazı ve yatay kaydırılabilir sekmeler önemlidir. Gerekiyorsa mevcut header/raf bileşenlerine bak ama onları değiştirme.

## Test ve teslim
- Compose UI testlerinde dört sekmenin seçimi/callback indeksleri, source tıklaması ve action slotu, kaynak etiketli raf, null/dolu Tümünü gör, kart/uzun basma, yükleme durumunu test et. Gerçek ağ ve oturum gerektirmeyen küçük fixture'lar kullan. UI test paketinin oluşturulması yetmez: olanak varsa çalıştır; hangi komutun ne sonuç verdiğini raporla. Büyük yazı, TR/EN, koyu/açık/AMOLED ve tema vurgularını manuel veya uygun UI testiyle doğrula.
- Mümkünse `./gradlew :app:assembleDebug` ve uygun emülatörde `./gradlew :app:connectedDebugAndroidTest` çalıştır. Kurulu JDK/Android SDK/cihaz yoksa kesinlikle başarı iddia etme; raporda 'çalıştırılmadı/bloklu' yaz.
- Teslim raporunda tam temel commit, eklenen dosyalar, sözleşme imzaları, test komutları/sonuçları, bilinen risk ve bağımlılıkları, ekran görüntüsünün gerçek test mi yoksa preview mi olduğunu yaz. Yalnız bu beş dosyayı klasör yolları korunarak ZIP halinde ver. Kullanıcı daha sonra ZIP'i bu oturuma aktarır. Başka ajan dosyalarını içeren toplu diff verme.
- İmza değişikliği gerekirse veya başka dosyada değişiklik zorunluysa dur; kullanıcı/entegrasyon sorumlusundan sözleşme onayı iste. Sözleşme dışında kendin başka model veya iş bölümü üretme.

---

**Not:** Diğer ajanın ürün özelliklerini koruma ve 32 kaynak/tür kombinasyonunu entegrasyonda doğrulama sorumluluğu vardır; bu UI paketi tek başına tamamlanmış bir Keşfet ekranı değildir.


---

## EK — Dondurulmuş ortak sözleşme (v1)

# Ortak sözleşme v1 — mobil görsel bileşenler

Bu arayüz, iki parçanın birbirinden bağımsız çalışabilmesi için sabittir. Değişiklik gerekiyorsa iki işi de durdur, sözleşmenin yeni sürümü üzerinde anlaş, sonra devam et. İş 2 bu dosyayı değiştirmez. Bütün fonksiyonlar `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisuals.kt` içinde `package com.kitsugi.animelist.ui.screens.explore.mobile` kullanır; `internal`, `@Composable`.

## Sabit Kotlin arayüzü

```kotlin
@Composable
internal fun MobileExploreHeader(
    selectedPlatform: ExplorePlatform,
    onSourceClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal fun MobileExploreTabs(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)

@Composable
internal fun MobileExploreShelf(
    title: String,
    source: ExplorePlatform,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    modifier: Modifier = Modifier,
)
```

Gerekli tipler mevcut repo sınıflarıdır: `androidx.compose.foundation.layout.RowScope`, `androidx.compose.ui.Modifier`, `com.kitsugi.animelist.ui.screens.explore.ExplorePlatform`, `com.kitsugi.animelist.data.remote.JikanSearchResult`, `com.kitsugi.animelist.model.MediaEntry`. Yeni bağımlılık eklenmez. Ajan 1 ekran, kaynak state'i, pager, kayıt, hata/boş ve hero entegrasyonunu kontrol eder.

## Davranış şartları
- `MobileExploreHeader`: yerelleştirilmiş Keşfet başlığı + `ExploreSourceEngineSelectorPill` ile seçili kaynak/tümü + `actions` slotu. Pill `onSourceClick` çağırır, kaynak seçimi sheet'ini kendisi açmaz. Action slotunda bildirim/rastgele/arama gibi mevcut eylemler ev sahibi tarafından sağlanır; bileşen bunları elemez. Düzen küçük ekranda taşmayacak şekilde düşünülür; uygulanamayan durum teslim notuna yazılır.
- `MobileExploreTabs`: indeksler tam olarak `0=Diziler, 1=Filmler, 2=Anime, 3=Manga`; tıklama sadece `onTabSelected` çağırır; seçili semantiği ve tıklanabilirlik sağlanır. Kendisi pager, ağ işi, state persistence veya source seçiminde bulunmaz. Ekran klavyesi/ekran okuyucu ile tek başına erişilebilir olmalı. Geçersiz index'te crash yok: hiçbir sekme seçili gösterilmez; otomatik callback yok.
- `MobileExploreShelf`: `source` gerçek raf kökenidir (ALL değil). Kaynak adı/logo gösterilir; başlık ve `onSeeAllClick` (null ise buton yok) içeren bölüm başlığı + `KitsugiHorizontalMediaSection(showTitle = false)` kullanılarak yatay kart listesi. Kart/result olduğu gibi callback'e geçer; tür, sıralama, skor veya kaynak değiştirilmez. Tüm ayarlar ve `getMediaEntry` iletilir. `isLoading` boşken shimmer; parent, boş ve hata politikalarını belirler. `modifier` en dış kapsayıcıya uygulanır. Modifikasyon global kart/hero görünümünü değiştirmez.
- Kaynak `ALL` verilirse crash yerine kaynak etiketi için mevcut yerelleştirilmiş `Tümü` göster; ancak canlı entegrasyonun kaynak-özel raf göndermesi gerekir. Liste boş/başlık uzun/koyu-açık-AMOLED/erişilebilir boyutlar ele alınır. Hiçbir posterin URL'si, kullanıcı verisi veya sabit puan kodlanmaz.
- Renk ve tema için `KitsugiColors`, `LocalKitsugiAccent`/`OnAccent` ve mevcut gradient yardımcıları; seçili renk örnek resimde mint ama kodda sert kodlanmaz. Metinler sadece yeni `mobile_explore_strings.xml` TR/EN dosyalarından; `R.string.kesfet`/mevcut stringler de kullanılabilir. Yeni kaynak adlarının `mobile_explore_` ön ekiyle benzersiz olması zorunlu.
- İlk sürümde hero içerik seçimi ve `isHomeHeroMedia` değişikliği kapsam dışı. Görsel bileşenler yeni katalog API isteği yapmaz; mevcut kartın Coil ile afiş yüklemesi normaldir. Tekil kaynak başarısızlığını, refresh, retry, bottom bar, navigasyon ve source seçimi Ajan 1 yönetir.

## İki tarafın doğrulama noktaları
İş 2: Android Compose testleri en az dört tab/selected state, her tab callback index, kaynak pill callback ve action slot, shelf kaynağı/başlığı, Tümünü gör null/dolu, kart callback ve loading ile empty; TR/EN ve farklı tema ile manuel kontrol; test edilemeyen durum açıkça raporlanır.  
İş 1: API imzaları yukarıdakiyle birebir eşleşiyor mu; tüm ayarlar aktarılıyor mu; raf source kimliği doğru mu; composable'lar mevcut UI durumunu iki kez açmıyor mu; pager ile LazyRow gesture cihaz testi; hiçbir başka dosyanın/TV'nin beklenmedik değişikliği yok mu.
