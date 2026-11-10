# Plan / Task — Bildirimler: Tüm Kaynaklarda Ortak, Temaya Uyumlu, Kapsamlı Görünüm

**Tarih:** 2026-10-23 · **Durum:** Tamamlandı

---

## Kullanıcı bildirimi

> "Ve artık bildirim sayfası görünümü vb tüm kaynaklar arası ortak düzgün sorunsuz ve
> daha kapsamlı kullanıcı dostu olması lazım tüm kaynaklar arası tmmm yani bildirim
> sayfasını ele alman ve benim uygulamam temama uyumlu hale getirmen lazım eksikleri
> tamamlaman lazım anlatabiliyor muyum bir tanem ??"

Ekran görüntüsü: Bildirimler → "Bildirim Kaynağı Seç" paneli (AniList, MyAnimeList,
TMDB & Simkl, Kitsu, Shikimori, Bangumi) ve AniList bildirim listesi.

## Hedef

Bildirimler ekranı 6 kaynağın tamamında **tek ortak görünüm ve davranış** sözleşmesine
uysun: aynı liste, aynı durum ekranları, aynı tarih biçimi/sıralaması, Listem kaynak
seçicisiyle aynı tema dili (platform marka renkleri); eksik kullanıcı dostu öğeler
(tekrar dene, aşağı çekerek yenileme, tarih gruplama, sayaçlar, bağla eylemi) tamamlansın.

## Kapsam taraması (yapıldı)

| Alan | Durum (önce) | Not |
|---|---|---|
| AniList listesi | ❌ Ayrı kod | Kendi `LazyColumn`'u; hata durumunda tekrar dene yok |
| Diğer 5 kaynak listesi | ⚠️ Ortak ama eksik | `SourceNotifPage`; hata = salt metin, boş durum kaynaktan kaynağa farklı |
| Bağlı değilken yerel yedek | ❌ Ulaşılamaz | Ekran `if (isXConnected)` kapısı ViewModel'in yerel yedeğini engelliyordu |
| Tarih biçimi / sıralama | ❌ Kaynak başına farklı | MAL sunucu metni, Shikimori ham `2026-10-21`, AniList/Shikimori `tsMs` yok |
| Tema (marka renkleri) | ❌ Yok | Tek vurgu rengi; sabit `0xFF27AE60` / `0xFFFF6B6B` |
| Seçici sayaçları | ❌ Yalnız AniList | Diğer kaynaklarda toplam/okunmamış bilgisi yok |
| Poster/avatar kırpımı | ❌ Hatalı | `userId` var diye medya posteri yuvarlak kırpılıyordu |

## Yapılan değişiklikler

1. **`KitsugiNotificationsViewModel.kt`**
   - `formatNotifTimestamp()`: tüm kaynaklarda tek biçim ("Bugün 14:32" / "Dün 09:10" /
     "12 Eki 14:32" / "12 Eki 2025 14:32").
   - AniList (`tsMs = createdAt*1000`) ve Shikimori (mesaj + geçmiş) artık `tsMs` taşır;
     MAL'da sunucu metni yalnızca tarih çözülemezse yedek.
   - `archiveAndMerge` her zaman yeni → eski sıralar (tarihsizler sonda); Shikimori
     geçmişi de sıralı yayınlanır.
2. **`KitsugiNotificationsScreen.kt`**
   - Tek ortak liste `NotifListPage` (6 kaynak + AniList alt filtreleri): açıklama notu,
     yükleniyor, hata + Tekrar Dene + hata kodu, boş durum, tarih grupları
     (Yaklaşan/Bugün/Dün/Bu Hafta/Daha Eski/Diğer), sonsuz kaydırma, aşağı çekerek yenileme.
   - Bağlantı kapısı düzeltmesi: AniList dışı kaynaklar bağlı değilken de yüklenir;
     gerçekten boşsa platform logolu "Hesabı Bağla" eylemli durum ekranı.
   - `NotifPlatform.brandColor` (Listem ile birebir): hap buton, seçici sayfası, yenile
     ikonu, filtreler, kart kaynak rozeti, okunmamış noktası. Sabit renkler tema
     tokenlarına taşındı (`AccentGreen`/`AccentRed`).
   - Seçicide her kaynakta toplam sayı + okunmamış rozeti; hap satırında
     "N bildirim • M yeni" özeti; dış pager kaydırılabilir.
   - Kart kırpımı: yuvarlak yalnızca medya bağlantısız kullanıcı bildirimlerinde.
3. **`AppRootDetailPages.kt`** — `onConnectPlatform`: "Hesabı Bağla" ilgili kaynağın
   hesap ayarlarına gider (MAL tek-oturum akışı korunur).
4. **`values/strings.xml` + `values-en/strings.xml`** — `notif_group_*` (6),
   `notif_date_today/yesterday`, `notif_items_count`, `notif_empty_subtitle` (TR+EN).

## Kapsam dışı (bilinçli, değiştirilmedi)

- Bildirim uçlarının kendisi (AniList GraphQL, MAL web akışı, Simkl/Kitsu/Bangumi
  takvim eşleşmesi, Shikimori messages) — veri kaynakları ve iş mantığı aynı kaldı;
  bu görev görünüm/sözleşme katmanıdır.
- "Tümünü okundu işaretle": AniList dışındaki kaynakların çoğunda sunucu tarafında
  okundu durumu yazılamadığı için sahte bir eylem eklenmedi.

## Doğrulama

- Sandbox'ta JDK/Android SDK olmadığından Gradle derlemesi çalıştırılamadı; yerine
  parantez dengesi, XML geçerliliği, `PullToRefreshBox` imzasının repo içi kullanımlarla
  karşılaştırılması, TR/EN string anahtar kontrolü ve tam diff incelemesi yapıldı.
- Cihaz kontrolü önerisi: 6 kaynak × (bağlı/bağlı değil) ilk açılış, aşağı çekerek
  yenileme, tarih grupları, seçici sayaç/rozetleri, "Hesabı Bağla" yönlendirmesi.

## İlgili rapor

- `COZUM_RAPORU_BILDIRIMLER_ORTAK_TEMA_VE_KAPSAM_2026-10-23.md`
