# PLAN / TASK — Eklenti Portalı: Arama geçmişi paneli + Video/Manga ayrımı ve Manga keşfeti

Tarih: 2026-11-04  
Dal: `arena/6f5167ab-kitsugi-beta`

## İstek

1. Eklenti Portalı'ndaki "Eklentilerde ara..." çubuğuna dokunulduğu ANDA, ana arama sayfasındaki "Son Aramalar" geçmiş paneli açılmalı.
2. Aynı eklentiler sayfasında Video ve Manga bölümleri ayrı ayrı olmalı; manga eklentilerinin de video eklentilerindeki gibi bir keşfet (gözat) akışı olmalı. Uygun açık kaynak uygulamalardaki desenler araştırılıp uyarlanmalı.

## Araştırma / referans desen

- Aniyomi/Mihon ekolünde "Browse" ekranı Anime ve Manga olarak ikiye ayrılır; her kaynağa dokununca o kaynağın kataloğu (popüler/güncel) açılır, ayrıca tüm kaynaklarda global arama vardır.
- Kitsugi uyarlaması: portal üstüne Video/Manga segment bandı; Manga tarafında kaynak listesi + dil çipleri; kaynak satırı → o kaynağın keşfeti; "Tüm Manga Kaynakları" satırı → global manga keşfet/arama.

## Uygulama planı ve gerçekleştirilen işler

### A) Arama çubuğunda geçmiş paneli (PluginPickerScreen)

- [x] Arama alanına `onFocusChanged` ile odak takibi eklendi; odak + boş sorgu + geçmiş doluysa `showHistoryPanel` açılır.
- [x] Ana arama sayfasıyla AYNI bileşen (`SearchHistorySection`) ve AYNI veri (`SearchViewModel.uiState.searchHistory`) kullanılır → iki sayfa hep senkron (silme/temizleme/bulut dahil).
- [x] Geçmiş öğesine dokununca (Video modu) sorgu ana aramada çalıştırılır (`applyHistoryItem`) ve arama sayfasına dönülür.
- [x] Panel, eklenti listesinin yerine kaydırılabilir + `imePadding`'li kap içinde çizilir; klavye üstünde rahatça kaydırılır.
- [x] Geri tuşu hiyerarşisi: yazıyı temizle → odağı kapat (paneli kapat) → kategori/dil filtresini sıfırla → sayfadan çık.
- [x] Klavye "Done" eylemi ve kategori/dil çipleri odağı kapatır; panel açıkken kaydırma üst barı gizlemez (`!isSearchFocused` koruması).

### B) Video / Manga bölüm ayrımı (Aniyomi tarzı)

- [x] Başlık altına her zaman görünür `KitsugiSettingsSegmentedTabs` bandı: **Video** (PlayCircle) / **Manga** (AutoStories). Seçim `rememberSaveable` ile korunur.
- [x] Video tarafı mevcut davranışıyla aynen korundu (kategoriler, alfabetik ray, geçmiş paneli, eklenti satırları).
- [x] Manga tarafı: `MangaViewModel.mangaSources` (Mihon/Kotatsu kaynakları) bayrak + ad + baseUrl + dil rozetiyle listelenir; portal açılınca `refreshMangaSources()` ile tazeleme.
- [x] Dil çipleri: Tümü + mevcut diller (TR önce, bayraklı); arama çubuğu manga modunda "Manga kaynaklarında ara... (N kaynak)" olur ve ad/adres/dile göre filtreler.
- [x] Manga listesi ayrı `LazyListState` tutar → Video listesinin kaydırma konumu bozulmaz; alfabetik ray + büyük harf göstergesi ortak `AlphabetRailWithOverlay` bileşenine çıkarıldı (iki liste aynı davranış).
- [x] Geçmiş paneli Manga modunda da açılır; geçmiş sorgusuna dokununca tüm manga kaynaklarında arama açılır (`openMangaBrowse(query=...)`).
- [x] Boş durum metinleri: kaynak yoksa Ayarlar → Eklentilerim → Manga Kaynakları yönlendirmesi; dil filtresi boşsa ve sorgu eşleşmezse uygun mesajlar.

### C) Manga keşfeti (kaynak bazlı + global)

- [x] `AppNavigationState.openMangaBrowse(query, mediaId, sourceKey)` — opsiyonel `sourceKey` eklendi; kapanışta temizlenir.
- [x] `MangaBrowseViewModel.applyInitialSource(key)` + `lastInitialSourceKey`: eşleşen kaynağı seçip popüler listesini yükler; `reset()` temizler.
- [x] `MangaBrowseScreen(initialSourceKey)` — kaynak listesi gelince eşleşen kaynağın keşfeti önden seçili açılır.
- [x] Portalda kaynak satırına dokunma → `openMangaBrowse(sourceKey = stableSourceKey())` → o kaynağın keşfeti (popüler ızgara + kaynak çipleri + arama) → manga detay → okuyucu akışı.
- [x] "Tüm Manga Kaynakları · Keşfet" başlık satırı → `openMangaBrowse()` global keşfet/arama.
- [x] `MangaViewModel.refreshMangaSources()` public tazeleme fonksiyonu eklendi.

## Değişen dosyalar (9) + bu plan dosyası

| Dosya | Değişiklik |
| --- | --- |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt` | Geçmiş paneli, Video/Manga segmenti, manga kaynak listesi + dil çipleri, ortak alfabetik ray, keşfet navigasyonu |
| `app/src/main/java/com/kitsugi/animelist/ui/app/AppNavigationState.kt` | `mangaBrowseSourceKey` + `openMangaBrowse(sourceKey)` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseViewModel.kt` | `applyInitialSource`, `lastInitialSourceKey`, reset temizliği |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt` | `initialSourceKey` parametresi + önden kaynak seçimi efekti |
| `app/src/main/java/com/kitsugi/animelist/AppRootMangaPages.kt` | `initialSourceKey` geçişi |
| `app/src/main/java/com/kitsugi/animelist/ui/app/MangaViewModel.kt` | `refreshMangaSources()` |
| `app/src/main/java/com/kitsugi/animelist/AppRoot.kt` | Portal'a `mangaViewModel` parametresi |
| `app/src/main/res/values/strings.xml` | 9 yeni TR metin (portal_tab_video/manga, portal_kesfet, manga_kaynaklarinda_ara_x_kaynak, tum_manga_kaynaklari, tum_manga_kaynaklarinda_ara_kesfet, x_kaynak, aktif_manga_kaynagi_yok, bu_dilde_manga_kaynagi_yok) |
| `app/src/main/res/values-en/strings.xml` | Aynı 9 metnin EN karşılığı |
| `PLAN_TASK_PORTAL_ARAMA_GECMISI_VE_VIDEO_MANGA_AYRIMI_2026-11-04.md` | Bu dosya |

## Kontrol / devretme

- Tüm Kotlin dosyalarında süslü parantez dengesi ve her iki `strings.xml` XML doğrulaması: temiz.
- Gradle derlemesi **çalıştırılamadı**: bu çalışma ortamında Java/Android SDK yok. Derleme ve cihaz doğrulaması gereklidir.
- Cihazda kontrol listesi:
  1. Arama → Eklenti Portalı → arama çubuğuna dokun → Son Aramalar paneli açılıyor mu; öğeye dokununca ana aramada sonuçlar geliyor mu; tekil silme/tümünü temizleme iki sayfada senkron mu.
  2. Video/Manga segmenti geçişi; Video listesi ve kaydırma konumu korunuyor mu.
  3. Manga sekmesi: kaynaklar listeleniyor mu; dil çipleri ve arama filtresi; alfabetik ray.
  4. Kaynak satırı → o kaynağın keşfeti önden seçili açılıyor mu; popüler ızgara, kaynak çipleri, arama; manga detay → okuyucu.
  5. "Tüm Manga Kaynakları" → global keşfet; Manga modunda geçmiş sorgusu → tüm kaynaklarda manga araması.
  6. Geri tuşu hiyerarşisi (yazı → panel → filtre → çıkış); keşfetten geri → portal.
  7. EN dilinde yeni metinler; klavye açıkken panel kaydırması.

## Paket notu

Bu dosya ile birlikte ZIP'teki `app/src/...` yolları depo köküne göredir. Paket, tam proje/APK değil; bu görevde değişen kaynak dosyalarını ve bu plan dosyasını içerir. ZIP'i depo kökünde açıp üzerine yazarak uygulayabilirsiniz.
