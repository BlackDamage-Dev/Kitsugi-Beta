# Üst Çubuk Aksiyon Paritesi — 2026-10-31

## Sorun
Ekranların sağ üstündeki aksiyon butonları (bildirim, geçmiş, indirmeler, eklenti, istatistik,
rastgele, paylaş, favori, galeri) her ekranda **farklı sırada, farklı boyutta ve farklı görünümde**
çiziliyordu:

| Ekran | Önce | Sorun |
|---|---|---|
| Keşfet | 🎲 (emoji metin) + 🔔 Material ikonu, 38dp / 12dp köşe | emoji ikon = HD değil, bildirim sağda ama stil farklı |
| Listem | 🔔 + 🎲 **sırası ters** (bildirim solda), 38dp / 14dp köşe | bildirim en sağda değil |
| Arama | Filtre/eklenti aramanın içinde, 🔔 yok | bildirim hiç yok |
| Profil | 🔔 + 📊 **bildirim solda**, 40dp / 16dp köşe | sıra ve köşe farklı |
| Oynatıcı akış | Geçmiş → İndirmeler → Arama, 36dp, arka plansız | sıra kanonik değil |
| Detay sayfaları | Galeri/Paylaş/Favori, farklı sırada ve farklı boyutlarda | iki detay sayfası birbirinden farklı |

## Çözüm

### 1. Tek kaynak: `ui/components/KitsugiTopActionBar.kt`
- `KitsugiTopAction` enum'u: her aksiyonun ikonu + **kanonik sıra numarası**
  `FILTER(10) → SEARCH(15) → REFRESH(20) → HISTORY(30) → DOWNLOADS(40) → EXTENSIONS(50) → STATS(60) → SHARE(70) → FAVORITE(75) → GALLERY(80) → RANDOM(90) → NOTIFICATIONS(100)`.
  **Bildirim her zaman en sağda.**
- `KitsugiTopActionRow`: verilen aksiyonları kanonik sıraya göre dizer, tekrarları kaldırır.
  Bir ekranda hangi aksiyon varsa yalnızca o çizilir → boşluk kalmaz, göreli konum bozulmaz.
- `KitsugiTopActionButton`: ortak görünüm — 40dp kare, 14dp köşe, vurgu gradyanlı zemin +
  ince vurgu kenarlığı, basınca yay animasyonu, şeffaf arka planlı vektör ikon,
  tema vurgusu (gradyan modu dâhil) otomatik uygulanır, isteğe bağlı bildirim rozeti.
- `KitsugiDetailHeaderActions`: detay sayfaları için Paylaş · Favori · Galeri satırı (favori seçili
  durumu vurgu rengiyle, galeri yüklenirken devre dışı).

### 2. HD ikon seti (12 adet vektör, şeffaf arka plan)
`app/src/main/res/drawable/ic_kitsugi_top_*.xml` — 24dp viewport, `stroke` tabanlı çizgi ikonlar
(Lucide yerleşik HD setinden uyarlanmış path'ler). Emoji (🎲) ve düşük çözünürlüklü görseller
kaldırıldı. Önizleme: `gorseller/kitsugi_top_action_icons_preview.png`

| Aksiyon | Dosya |
|---|---|
| Bildirim | `ic_kitsugi_top_bell` |
| Geçmiş | `ic_kitsugi_top_history` |
| İndirmeler | `ic_kitsugi_top_download` |
| Eklenti | `ic_kitsugi_top_extension` |
| Filtre | `ic_kitsugi_top_filter` |
| Arama | `ic_kitsugi_top_search` |
| Yenile | `ic_kitsugi_top_refresh` |
| İstatistik | `ic_kitsugi_top_stats` |
| Rastgele | `ic_kitsugi_top_random` |
| Galeri | `ic_kitsugi_top_gallery` |
| Paylaş | `ic_kitsugi_top_share` |
| Favori | `ic_kitsugi_top_favorite` |

### 3. Ekranlara uygulandı
- **Keşfet:** Geçmiş · İndirmeler · Rastgele · **Bildirim (en sağda)**
- **Listem:** Geçmiş · İndirmeler · Rastgele · **Bildirim**
- **Arama:** Geçmiş · İndirmeler · **Bildirim** (başlık satırının sağında, 3 durumda da görünür)
- **Profil:** Geçmiş · İndirmeler · İstatistik · **Bildirim**
- **Oynatıcı akış üst çubuğu:** Geçmiş · İndirmeler · Arama
- **Detay sayfaları (2 adet):** Paylaş · Favori · Galeri

Navigasyon bağlantıları (`AppRootTabPages.kt`) tamamlandı; tüm butonlar gerçek ekranlara gider
(İzleme geçmişi, İndirilenler, Bildirimler, İstatistikler).

## Test
`app/src/test/java/com/kitsugi/animelist/ui/components/KitsugiTopActionOrderTest.kt`
- Bildirim her zaman en sağda,
- Girdi sırası sonucu değiştirmez,
- Tekrarlanan aksiyon tek butona iner,
- Her aksiyonun sıra numarası tekil.

## Riskler / notlar
- Butonlar artık tüm ana sekmelerde 40dp; satırlar 4 aksiyonda ~184dp yer kaplar, kalan alan
  kaynak seçici hapına kalır (hap metni ellipsis ile kısalır).
- `KitsugiGalleryIconButton` geriye dönük uyumluluk için silinmedi; detay sayfaları artık ortak
  satırı kullanıyor.
