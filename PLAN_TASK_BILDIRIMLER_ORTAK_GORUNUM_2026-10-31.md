# Bildirimler — Ortak Görünüm ve Ortak Mantık (2026-10-31)

## Sorun
Bildirimler ekranındaki altı kaynak (AniList, MAL, TMDB & Simkl, Kitsu, Shikimori,
Bangumi) kendi üst barını, kendi filtre şeridini ve kendi liste iskeletini
çiziyordu. Sonuç:

- Aynı ekranda kaynaklar arasında farklı boşluk/hizalama.
- Yalnızca AniList ve MAL'da filtre şeridi; diğer dört kaynakta filtre yok.
- AniList filtresi `HorizontalPager`, MAL filtresi ayrı bir `horizontalScroll`
  satırı — iki ayrı görünüm ve iki ayrı davranış.
- Uzun kaynak notları ("bu API kişisel bildirim sunmaz…") listeyi kaplıyordu.
- Kaydırma sırasında üst bölüm için ortak bir davranış yoktu.
- MAL'da ekran dışına taşan kategoriler yalnızca elle kaydırılarak bulunuyordu,
  seçili sekme görünür alana getirilmiyordu.

## Çözüm
Yeni dosya: `app/src/main/java/com/kitsugi/animelist/ui/screens/notifications/KitsugiNotificationChrome.kt`

| Bileşen | Sorumluluk |
| --- | --- |
| `NotifChromeHeader` | Üst bar + kaynak hapı + ortak filtre şeridi. Aşağı kaydırınca **başlık barı** yumuşakça yukarı kayar; kaynak hapı ve şerit sabit kalır. |
| `NotifFilterBand` / `NotifFilterTab` | Soldan sağa kaydırılabilen, seçili sekmeyi görünür alana getiren, yumuşak renk animasyonlu ortak şerit. |
| `NotifFeedList` | Ortak liste: aşağı çekip yenileme, sonsuz kaydırma, hata kodu bloğu, kaynak notu, zaman grupları, boş/hata + "Tekrar dene". |
| `NotifChromeCollapseState` | Kaydırma fiziği: aşağıda topla, listede başa gelince aç, fling sonunda en yakın uca yumuşakça otur. |
| `notifDateGroup` / `groupNotifRows` | Bugün / Dün / Bu Hafta / Daha Eski / Tarihsiz grupları ve grup sayıları. |

## Kaynak başına sekmeler
- **AniList:** Tümü / Yayın / Aktivite / Forum / Takip / Medya (sunucu filtreleri;
  sekmeler arasında yatay kaydırma korunur).
- **MAL:** Tümü / **Okunmamış** / MAL kategorileri (sunucudan gelen gerçek sayılar).
- **TMDB & Simkl:** Tümü / Yayınlanan / Yaklaşan (`typeIdentifier` = aired/upcoming).
- **Kitsu:** Tümü / Yayın / Anime / Manga (`typeIdentifier` = release/list).
- **Shikimori:** Tümü / Okunmamış / Bildirimler / Haberler / Medya.
- **Bangumi:** Tümü / Yayın / İlerleme (`typeIdentifier` = airing/watching).

## Veri modeli eklemeleri
`KitsugiNotificationsViewModel` artık Simkl/Kitsu/Bangumi/Shikimori kayıtlarında
`NotifItem.typeIdentifier` alanını doldurur (arşivden geri okunurken de korunur);
filtre sekmeleri bu alandan türetilir. Ek ağ isteği yoktur.

- `getAniListState(filter)` artık her çağrıda yeni akış üretmiyor — sekme geçişinde
  listenin bir an boş görünmesine yol açan abonelik yenilemesi kalktı.
- `getMalUnreadCount()`: filtre seçiminden bağımsız okunmamış sayısı (başlık özeti).

## Test
`app/src/test/java/.../notifications/NotifDateGroupingTest.kt`
- Gün sınırları (Bugün/Dün/Bu Hafta/Daha Eski/Tarihsiz).
- Grup başlığının yalnızca grup değişiminde eklenmesi ve sıralamanın bozulmaması.
- Boş liste davranışı.

## Sürüm
`appVersionName` 2.4.340 → **2.4.341** (`release_notes_v2.4.341.md`).
