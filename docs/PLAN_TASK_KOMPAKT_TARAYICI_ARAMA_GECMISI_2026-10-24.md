# PLAN / TASK — Arama Sayfası Kompakt Tarayıcı Arama Geçmişi (Omnibox) & Akıllı Özellikler

**Tarih:** 2026-10-24  
**Baz commit:** `3d3242ffb9a99359a7afc4db9bc126a0e511cc48` (`main`)  
**Çalışma dalı:** `arena/0aec819d-kitsugi-beta`  
**Durum:** Kodlandı ve statik doğrulamalardan geçirildi (sandbox ortamında JDK/Android SDK bulunmadığından APK derlemesi ve canlı cihaz testi kullanıcı ortamında yapılacaktır).

---

## 1. Kullanıcı İsteği & Kapsam

| # | Gereksinim | Durum |
|---|---|---|
| 1 | Arama sayfasındaki dağınık çok satırlı çip yığınını **daha kompakt** hale getirme | ✅ |
| 2 | Tasarımı **tarayıcı arama geçmişine (omnibox)** yaklaştırma ve daha kullanışlı kılma | ✅ |
| 3 | Uygulamanın koyu / AMOLED / açık yüzeyleri ve **düz + gradyan vurgu temasıyla** tam uyum sağlama | ✅ |
| 4 | Kullanıcı deneyimini güçlendiren ek akıllı tarayıcı özellikleri ekleme (`↖` çubuğa aktar, canlı öneri/vurgulama, sabitleme, geri al, gizli mod, geçmişte arama paneli, güvenli temizleme) | ✅ |

---

## 2. Mimari ve Tasarım Kararları

### 2.1 Tarayıcı Omnibox Stili Kompakt Kart (`SearchHistorySection.kt`)
- **Görsel Bütünlük:** Üstteki `KitsugiCosmicSearchBar` ile birebir hizalanan `RoundedCornerShape(16.dp)`, `KitsugiColors.Surface` zemin ve `1.dp` `KitsugiColors.Border` kenarlıklı tek bir kompakt kart yapısı kuruldu. Gradyan vurgu temaları için `com.kitsugi.animelist.ui.theme.gradient.*` (`background`, `border`, `Text`, `Icon`) bileşenleri kullanıldı.
- **Kompakt Satır Yüksekliği & Sınır:** Dağınık çip bulutu yerine varsayılan olarak yalnızca **5 adet ince (`38.dp`) tek satırlık tarayıcı geçmişi satırı** gösterilir (`COLLAPSED_BROWSER_ROW_COUNT = 5`). Kart altındaki ince çubuktan **`Daha fazla göster (+N)` / `Daha az göster`** ile satır içi 10 kayda kadar yumuşak `animateContentSize` geçişiyle açılır; daha fazlası için **`Tüm Geçmiş ›`** bağlantısıyla tam panel açılır.
- **Çift Görünüm Desteği:** Başlıktaki görünüm değiştirme düğmesiyle kullanıcı dilediğinde **Tarayıcı Listesi (varsayılan)** ile **Ultra-Kompakt Etiketler (Çip)** görünümü arasında tek dokunuşla geçiş yapabilir; tercih `SharedPreferences` (`kitsugi_search_ui`) üzerinde kalıcı tutulur.

### 2.2 Yazarken Canlı Geçmiş Önerileri & Eşleşme Vurgulama
- `SearchViewModel` canlı ağ araması yapmadığı (yalnızca onayda arama başlattığı) için kullanıcı arama çubuğuna yazarken `showIdleContent` aktif kalır.
- `uiState.query` artık `SearchHistorySection`'a iletilir:
  - Kullanıcı yazmaya başladığında geçmiş kartı otomatik olarak **Geçmiş Önerileri (Omnibox Autocomplete)** moduna geçer.
  - **1'e 1 Karakter Katlama (`foldForMatch`):** Türkçe karakter duyarsız (`İ/I/ı/i`, `Ş/ş`, `Ğ/ğ`, `Ü/ü`, `Ö/ö`, `Ç/ç`) eşleştirme yapılırken karakter dizisi uzunluğu **asla değişmez** (`"İ".lowercase()` kaynaklı indeks kayması riski sıfırdır).
  - Eşleşen harf aralıkları düz temada `accentColor`, gradyan temada doğrudan `SpanStyle(brush = KitsugiColors.AccentBrush, fontWeight = FontWeight.Bold)` ile vurgulanır.
  - Eşleşen geçmiş kaydı kalmadığında kart kalabalık yaratmamak için otomatik gizlenir.

### 2.3 `↖` Arama Çubuğuna Aktar / Sorguyu Düzenle
- Her geçmiş satırının sağ tarafına mobil tarayıcılardaki gibi **`↖` (Arama çubuğuna aktar)** düğmesi eklendi.
- `KitsugiCosmicSearchBar` bileşenine geriye uyumlu opsiyonel `focusRequester: FocusRequester? = null` parametresi eklendi.
- `↖` düğmesine dokunulduğunda ağ araması tetiklenmeden sorgu arama çubuğuna yazılır (`setTextAndPlaceCursorAtEnd`), liste gerekiyorsa en üste kaydırılır, metin alanına odak verilir ve klavye açılır. Yazım hatası düzeltme veya sorgu sonuna ekleme tek dokunuşla yapılır.

### 2.4 Şema Göçü Gerektirmeyen Zenginleştirilmiş Veri & Bulut Eşitleme Uyumu
- **Sıfır Room Migration Riski:** `SearchHistoryEntity` tablosunun (`query`, `timestamp`, `type`) sütun yapısı değiştirilmedi; veritabanı sürümü ve şeması korunarak beta kullanıcılarında göç hatası riski sıfırda tutuldu.
- **Geriye Uyumlu `type` Kodlaması (`SearchHistoryMapper`):**
  - Eski biçim: `"${platform}:${mediaType}"`
  - Yeni genişletilmiş biçim: `"${platform}:${mediaType}:${engine}:${scope}:${PIN|UNPIN}:${searchCount}"`
  - Eski 2 parçalı kayıtlar sorunsuz okunur; `:PIN:` ayracı sayısal `searchCount` ile asla çakışmaz (`type LIKE '%:PIN:%'`).
- **Mükerrer Birleştirme & Kapasite (`SearchHistoryDao` & `SearchHistoryRepository`):**
  - Büyük/küçük harf farkı olan tekrar aramalar (`Coraline` / `coraline`) `findByQueryNormalized` ile birleştirilir; varsa sabitleme durumu korunur ve `searchCount` artırılır.
  - Geçmiş kapasitesi 20'den **50 kayda** çıkarıldı; `pruneToLimit` ve `KitsugiAccountRepository` bulut eşitlemesi sabitlenen (`PIN`) aramaları her zaman koruyacak şekilde güncellendi.

### 2.5 Sabitleme, Uzun Basma Hızlı Eylemler, Geri Al (Undo) & Gizli Mod
- **Uzun Basma Çekmecesi:** Satıra uzun basıldığında titreşim geri bildirimiyle (`HapticFeedbackType.LongPress`) satır altında hızlı eylem hapları açılır: **Sabitle / Sabitlemeyi kaldır**, **Arama çubuğuna aktar**, **`[Kaynak]` ile ara** (farklı bir motor/kapsamda kaydedildiyse o kaynağı geri yükleyerek arar), **Kopyala** ve **Kaldır**.
- **Geri Al (`Undo`):** Tekil silmelerde kayıt 5 saniyeliğine `lastDeletedHistoryItem` olarak tutulur; kart içindeki **`Geri Al`** şeridiyle orijinal zaman damgası, kaynağı ve sabitleme durumuyla geri yüklenir.
- **Güvenli Temizleme Onayı:** Çöp kutusu düğmesi doğrudan silmek yerine onay penceresi açar; sabitlenmiş aramalar varsa **`Sabitlenen aramaları koru (N)`** seçeneği varsayılan olarak işaretli gelir.
- **Gizli Arama Modu Hızlı Anahtarı:** `AppSettings.searchHistoryEnabled` ayarı doğrudan geçmiş kartı başlığından tek dokunuşla açılıp kapatılabilir; kayıt kapalıyken arama çubuğu altında ince durum şeridi gösterilir.
- **Tam Tarayıcı Geçmişi Paneli (`SearchHistoryBrowserSheet`):** `KitsugiSheetOrDialog` tabanlı (mobil + tablet + Android TV uyumlu) alt sayfada `Geçmişte ara...` çubuğu, `Tümü / Sabitlenenler / Sık Arananlar / Kaynak` filtre çipleri ve `Sabitlenenler / Bugün (HH:mm) / Dün / Bu Hafta / Daha Eski` tarih grupları sunulur.

---

## 3. Değişen Dosyalar

| Dosya | Yapılan Değişiklik |
|---|---|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchHistorySection.kt` | Kompakt tarayıcı geçmişi kartı, canlı omnibox vurgulama, `↖` aktarma, uzun basma hızlı eylemler, Geri Al şeridi, gizli mod şeridi, onay diyaloğu ve Tam Geçmiş alt sayfası yazıldı. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchScreen.kt` | `searchBarFocusRequester` bağlandı; `SearchHistorySection` yeni durum ve aksiyonlarla (`currentQuery`, `onFillQuery`, `onTogglePin`, `onUndoDelete`, `onApplyWithSource` vb.) entegre edildi. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchUiState.kt` | `SearchHistoryItem` geriye uyumlu varsayılan alanlarla (`timestamp`, `engine`, `scope`, `isPinned`, `searchCount`) genişletildi; `SearchUiState` içine `searchHistoryEnabled` ve `lastDeletedHistoryItem` eklendi. |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/SearchViewModel.kt` | Sabitleme, kaynakla birlikte arama, çubuğa sorgu aktarma, 5 sn süreli Geri Al (Undo), sabitlenenleri koruyarak temizleme ve gizli mod anahtarı işlevleri eklendi. |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiCosmicSearchBar.kt` | Geriye uyumlu opsiyonel `focusRequester: FocusRequester? = null` parametresi eklendi. |
| `app/src/main/java/com/kitsugi/animelist/data/local/SearchHistoryMapper.kt` | Şema göçü gerektirmeyen `:PIN:` / `:UNPIN:`, `engine`, `scope`, `searchCount` ve `timestamp` eşlemesi eklendi. |
| `app/src/main/java/com/kitsugi/animelist/data/local/SearchHistoryDao.kt` | Sabitlenenleri üstte tutan 50 kayıtlık sorgu, normalize tekilleştirme sorguları ve sabitlenenleri koruyan temizleme/budama sorguları eklendi. |
| `app/src/main/java/com/kitsugi/animelist/data/repository/SearchHistoryRepository.kt` | Büyük/küçük harf duyarsız birleştirme, `searchCount` artışı, `restoreSearchQuery`, `togglePinSearchQuery` ve `clearSearchHistory(keepPinned)` eklendi. |
| `app/src/main/java/com/kitsugi/animelist/data/account/KitsugiAccountRepository.kt` | Bulut arama geçmişi sınırı 50'ye çıkarıldı; bulut birleştirmede sabitlenen kayıtların korunması sağlandı. |
| `app/src/main/res/values/strings.xml` | 32 adet yeni Türkçe `search_history_*` arayüz dizgesi eklendi. |
| `app/src/main/res/values-en/strings.xml` | 32 adet simetrik İngilizce `search_history_*` arayüz dizgesi eklendi. |

---

## 4. Doğrulama Özeti

- [x] `python3 diag/bangumi_master/verify_bangumi_master.py` → **`strings.xml: TR 58498 · EN 58498 kayıt (simetri OK)` — `✅ Tüm kontroller geçti`**.
- [x] Değişen tüm `.kt` dosyalarında parantez/süslü parantez/köşeli parantez, yorum ve metin sabiti dengesi statik olarak doğrulandı.
- [x] Kodda referans verilen tüm `R.string.*` anahtarlarının hem `values/strings.xml` hem `values-en/strings.xml` içinde tanımlı olduğu doğrulandı.
- [x] `remember { ... }` blokları içinde yanlışlıkla `@Composable` çağrısı bulunmadığı denetlendi.

---

## 5. Projeye Uygulama & Cihazda Test Listesi

### ZIP Paketi Kurulumu
Proje kök dizininde (`Kitsugi-Beta/`):
```bash
unzip -o Kitsugi_Kompakt_Tarayici_Arama_Gecmisi_2026-10-24.zip -d .
```

### Saha Kontrol Listesi
1. Arama ekranı boşken **Son Aramalar** kartının üstteki arama çubuğuyla hizalı ve varsayılan olarak 5 kompakt satır halinde göründüğünü doğrulayın.
2. Arama çubuğuna birkaç harf yazdığınızda (`de`, `cor` vb.) kartın canlı olarak eşleşen geçmiş satırlarını süzdüğünü ve eşleşen harfleri tema/gradyan rengiyle vurguladığını kontrol edin.
3. Satırın sağındaki **`↖`** butonuna bastığınızda sorgunun arama başlatılmadan çubuğa aktarıldığını ve klavyenin açıldığını doğrulayın.
4. Bir satıra **uzun basıp** `Sabitle` dediğinizde listenin en üstüne yerleştiğini ve çöp kutusu onay penceresinde `Sabitlenen aramaları koru` seçeneğinin çıktığını kontrol edin.
5. **`×`** ile bir kaydı sildiğinizde `Geri Al` şeridinin çıktığını ve dokununca kaydın geri geldiğini doğrulayın.
6. **`Tüm Geçmiş ›`** veya başlıktaki büyüteçli geçmiş butonuna dokunarak tarih gruplu (`Bugün`, `Dün` vb.) ve arama destekli alt sayfanın açıldığını test edin.
