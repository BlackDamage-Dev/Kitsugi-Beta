# Arama Sayfası Üst Aksiyonları + Bildirim Düzeni — 2026-10-26

Sürüm: 2.4.340-beta

## 1) Arama sayfasındaki butonlar artık sağ üstte (başlık hizasında)

**Önce:** Filtre (Tune), Bildirimler, İzleme geçmişi ve Eklenti Portalı
butonları arama çubuğunun **içinde/yanında** duruyordu. Bu yüzden:

- Ekranın ortasında, başlığın altında kalıyorlardı (Keşfet/Listem/Profil'de
  aynı butonlar en üstte).
- Arama çubuğunun yazma alanını daraltıyorlardı (sağda 4 buton + çubuğun
  kendi filtre butonu).

**Şimdi:**

- Butonlar, "Arama" başlığıyla **aynı satırda**, sağ üstte tek bir küme
  hâlinde (`SearchTopActionBar`).
- Görünüm Keşfet/Listem/Profil ile birebir aynı: 38 dp kare, 12 dp köşe,
  `SurfaceElevated` zemin + ince kenarlık, aktif filtrede vurgu rengi.
- Arama çubuğu **tam genişliğe** kavuştu.
- Sıra sabit: **Filtre → Bildirimler → İzleme Geçmişi → Eklenti Portalı**
  (ekran görüntülerindeki düzenle uyumlu).

Yeni dosya: `ui/screens/search/SearchTopActionBar.kt`

Bağlantılar (`AppRootTabPages.kt` → `SearchTabPage`):

- Bildirimler → `DetailScreen.Notifications`
  (yalnızca AniList/MAL/Simkl hesabı bağlıysa görünür — diğer sayfalarla aynı kural)
- İzleme geçmişi → `DetailScreen.WatchHistory`
- Eklenti Portalı → `DetailScreen.PluginPicker`

Kaynağa özel arama sayfası (`SourceSearchPage`, `pageTitle = null`) da aynı
kümeyi kullanır; burada yalnızca Filtre + Eklenti Portalı görünür.

## 2) Bildirimler: tek düzen, sağ-sol savrulması bitti

### a) Kaynak rozeti artık her satırda aynı yerde (sağda)

Önceden başlık kısayken platform rozeti (AniList/MAL/…) başlığın hemen
yanına düşüyor, başlık uzunken en sağa kayıyordu → liste satır satır
"biri sağda biri solda" görünüyordu.

Artık başlık kalan genişliği doldurur (`weight(1f)`), rozet **her satırda
satırın sağ ucunda** durur.

### b) Tarih her satırda aynı yerde

Tarih gövdenin altında, solda; tarihi olmayan bildirimlerde de aynı
yükseklik korunur (boş satır çizilir). Satırlar `heightIn(min = 72.dp)`
ile aynı taban yüksekliğe sahip → sayfa kaydırırken zıplama yok.

### c) Filtre sekmeleri (AniList / MAL) tek bileşenden çiziliyor

Önceden AniList filtresi geniş ekranda **tüm genişliğe yayılıyor**
(sekmeler sağa-sola dağılıyor), MAL filtresi ise **soldan** akıyordu.
Platform değiştirince çubuk yer değiştirmiş gibi görünüyordu.

Artık iki kaynak da aynı `NotificationFilterTabs` bileşenini kullanıyor:

- Tek satır, hep **soldan** başlar
- Aynı hap ölçüleri (22 dp köşe, 16/8 dp iç boşluk, 4 dp aralık)
- Taştığında yatay kaydırılır (sekmeler sıkıştırılmaz)
- Sayaç rozetleri (MAL kategorileri) aynı yerde

## Dokunulan dosyalar

| Dosya | Değişiklik |
| --- | --- |
| `ui/screens/search/SearchTopActionBar.kt` | **Yeni** — sağ üst aksiyon kümesi |
| `ui/screens/search/SearchScreen.kt` | Başlık + aksiyonlar tek satır; arama çubuğu tam genişlik; yeni parametreler (`isNotificationsVisible`, `onOpenNotifications`, `onOpenWatchHistory`) |
| `AppRootTabPages.kt` | Bildirim / izleme geçmişi yönlendirmeleri |
| `ui/screens/notifications/KitsugiNotificationsScreen.kt` | Ortak filtre sekmeleri, sabit rozet/tarih hizası, sabit satır yüksekliği |

## Kontrol listesi

- [x] Arama sayfası butonları sağ üstte, başlık hizasında
- [x] Arama çubuğu tam genişlik
- [x] Bildirim rozeti her satırda sağda
- [x] Tarih her satırda aynı konumda
- [x] AniList / MAL filtre çubukları aynı hizada
- [ ] Cihazda derleme testi (bu ortamda JDK/Android SDK yok — Gradle derlemesi yapılamadı)
