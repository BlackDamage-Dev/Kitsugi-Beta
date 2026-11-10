# Diğer yapay zekâ için TEK GEÇERLİ TALİMAT — v2: onaylı tasarım

Onaylı referans: https://drive.google.com/file/d/17_vONg0AYXWi6jGHhNJ2FwhOO7xD-7_v/view . Kullanıcı görseli ilham değil, ulaşılması gereken tasarım olarak onayladı. Kopyasına erişemiyorsan kullanıcıdan görseli iste. Eski v1 ve diğer eski görev belgelerini kullanma; v1 ile v2 paketlerini karıştırma. Eğer eski görevle başladıysan burada dur ve kullanıcı/entegrasyon sorumlusuna haber ver, v2 ile uyumu netleştir. Diğer ajan otomatik başlatılmadı.

## Sınırlar ve teslim
Yalnız mobil telefon/tablet Keşfet'in izole Compose görsel parçalarını geliştir; gerçek ekran/entegrasyon/pager/kaynak/hesap/servis/detay/grid/cache, TV, Gradle, ortak bileşen, ortak tema ve navigasyon dosyalarına dokunma. Yeni bağımlılık ekleme. Kullanıcının değişikliklerini silme. Temel commit `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350`; ortak çalışma dizininde eşzamanlı çalışma yapma: ayrı kopya kullan, branch oluşturma/değiştirme, commit, push, merge yapma. Ayrı kopya yoksa bildir ve dur.

Yalnız 5 yeni dosya: `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisuals.kt`; `app/src/main/res/values/mobile_explore_strings.xml`; `app/src/main/res/values-en/mobile_explore_strings.xml`; `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisualsTest.kt`; `docs/KESFET_MOBIL_PLAN/IKI_IS_PARCACIGI/IS_2_TESLIM_RAPORU.md`. Bunlar önceden varsa üzerine yazmadan dur; başka dosya gerekiyorsa sor. Testler ve rapor dahil beş dosyayı aynı yollarda ZIP ile teslim et. Ana ekrana bağlama.

## Dondurulan Kotlin sözleşmesi v2
Üç eski fonksiyon imzası değişmedi; dördüncü hero eklendi. Bütün fonksiyonlar `com.kitsugi.animelist.ui.screens.explore.mobile` paketinde, yukarıdaki tek Kotlin dosyasında, `internal`, `@Composable`. Gerekli tipler: `androidx.compose.foundation.layout.RowScope`, `androidx.compose.ui.Modifier`, `com.kitsugi.animelist.ui.screens.explore.ExplorePlatform`, `com.kitsugi.animelist.data.remote.JikanSearchResult`, `com.kitsugi.animelist.model.MediaEntry`. Sözleşmeyi tek taraflı değiştirme.

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

@Composable
internal fun MobileExploreHero(
    item: JikanSearchResult?,
    isLoading: Boolean,
    blurAdultMedia: Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier,
)
```

## Görsel şartlar
- Referansı esas al. Koyu zemin üzerinde üstte KITSUGI ve sağda gerçek eylemler için `actions` slotu; altta büyük Keşfet başlığı solda ve ince çerçeveli Tümü/kaynak + aşağı ok sağda. `actions` kendi başına sahte ikon/rota üretmez; dar ekranda erişimi kaybolmaz. Var olan global üst bar ile mükerrer çizilmesini entegrasyon sorumlusu denetler.
- Diziler / Filmler / Anime / Manga yatay sırada. Seçili sekme vurgulu yazı ve ince alt çizgi, altta ayırıcı; seçililer ve tıklama semantiği tam, geçersiz index crash etmez. Yatay swipe/pager bu bileşenin değil İş 1'in görevidir.
- Hero görseli geniş yatay yaklaşık 2,3:1; hafif yuvarlatılmış köşe/ince sınır, alttan siyaha gradient, solda yerelleştirilmiş “Yeni hikayeler keşfet”, sağda yerelleştirilmiş “İncele”. `item != null` ise `backdropUrl` sonra `imageUrl` görüntüle, `onItemClick(item)` çağır; gerçek tür/kaynak değiştirme. Veri yoksa placeholder/empty, yükleniyorsa loading; buton tıklanamaz. `blurAdultMedia` + `item.isAdult` için güvenli mevcut `KitsugiNsfwImage` altyapısı; kitaplar dahil parametre olarak geleni göster, seçim İş 1'indir.
- Raf başlığı solda beyaz kalın, yanında daha küçük kaynak adı, sağda Tümünü gör; `onSeeAllClick == null` ise yok. Raf ve posterler yatay scroll; yaklaşık 384dp genişlikte dört dikey kart ve beşincinin bir kısmı görünür. İlk hedef kart yaklaşık 84×135dp, aralık 6–7dp; görsele uygun ölçümü gerçek cihazda düzelt. Telefonda landscape'de de dikey poster. Poster içinde gerçek artwork; alt gradient üzerinde gerçek `genres`/`year` mevcutsa “tür • yıl”; eksikse boş parçayı/ayracı atla, uydurma veri yok. Başlık erişilebilir semantik ve artwork yoksa görünen fallback. `titleLanguage`, `scoreFormat`, `hideScores`, `blurAdultMedia`, `alreadyInList` ve `getMediaEntry` davranışları korunur; gerçek score/durum gerektiğinde tasarıma uygun ve erişilebilir gösterilir. Aynı `result` click/long-click callback'ine gider. `kitsugiLazyKey` ile kaynak+tür+kimlik çakışmasını önle.
- Önceki `KitsugiHorizontalMediaSection(showTitle=false)` ve `ExploreSourceEngineSelectorPill` zorunluluğu **kaldırıldı**; bunlar referans ölçülerine uymuyor. Yalnız yeni mobil dosyada kendi LazyRow, poster ve seçiciyi uygula. Ortak kart/tema dosyalarını değiştirme. Gerçek sheet ve tüm yönlendirmeleri İş 1 bağlar. `source=ALL` durumunda çökme yok ve yerelleştirilmiş Tümü göster. Parent boş/hata politikasını yönetir; boş+yüklüyor durumunda shimmer, doluyken stale veri kaybolmamalı.
- Kitap/vitrin gibi gerçek verilerin seçimi, filtrelenmesi, katalog API çağrıları ve navigation yapma. Coil veya mevcut görüntü yükleyicinin gerçek resim indirmesi normaldir. Görseldeki kurgusal posterleri veya başlıkları sabit kodlama. Yeni TR/EN string adları `mobile_explore_` ile başlar; mevcut kaynaklar kullanılabilir. Renkleri `KitsugiColors`, `LocalKitsugiAccent`, `OnAccent` ve mevcut tema altyapısından al: onaylı koyu/mint kombinasyonunda görüntüye sadık, diğer temalarda uygulama tercihine uyumlu.

## Test ve kapanış
- Sabit ağsız fixture/test image loader ile tüm 4 tab ve index, kaynak/action, hero boş/loading/dolu/İncele callback, shelf kaynak/başlık, Tümünü gör null/dolu, kart click/long, yetişkin blur, eksik image/genre/year, listede olma, skor/başlık dili ve farklı temaları test et. Erişilebilirlik: 48dp dokunma alanı, açıklamalar ve büyük font. 320–360dp, 384dp, tablet ve landscape için overflow kontrol et.
- 384×683 dp civarı aynı koyu/mint tema ve font ölçeğinde gerçek cihaz/Compose screenshot veya açıkça preview olduğunu belirten görüntüyü referansla karşılaştır; ölçü, boşluk, renk, tipografi, poster görünürlüğü ve hero/raf farklarını raporla. Tek screenshot görsel sadakat kanıtı değildir; gerçek entegrasyon/uygun cihaz hâlâ İş 1'in kapısıdır. Görseli görmeden uyum iddia etme.
- `./gradlew :app:tasks --all` ile foss/gms gerçek görevlerini bul, uygun debug ve cihaz testlerini çalıştır. JDK/SDK/cihaz yoksa bloklu yaz; çalışmamış teste geçti deme. Teslim raporu base commit, değişiklikler, API, test komut/sonuçları, preview/gerçek screenshot, kısıt ve riskleri içersin. Sınır veya sözleşme değişikliği gerekiyorsa durup entegrasyon sorumlusuna sor.
