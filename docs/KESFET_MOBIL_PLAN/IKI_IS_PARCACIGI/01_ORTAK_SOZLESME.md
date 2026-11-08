> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

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
