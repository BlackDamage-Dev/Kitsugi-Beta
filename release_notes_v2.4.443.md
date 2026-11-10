# Kitsugi Beta v2.4.443

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Varsayılan Dil Tercihleri Standardizasyonu (Ayarlar → Görünüm & Tercihler → Liste & Puan)
- **Anime & Manga Başlık Dili**: Varsayılan olarak **İngilizce (`ENGLISH`)** olarak ayarlandı.
- **Ekip & Karakter Adı Dili**: Varsayılan olarak **İngilizce (`ENGLISH`)** olarak ayarlandı.
- **Dizi & Film Başlık Dili (TMDB)**: Varsayılan olarak **Türkçe (`tr`)** olarak ayarlandı.
- `SettingsDataStore.kt` içinde `DEFAULT_TITLE_LANGUAGE`, `DEFAULT_STAFF_NAME_LANGUAGE` ve `DEFAULT_TMDB_LANGUAGE` sabitleri tanımlandı; `SettingsScreenParameters.kt` içindeki kalıntı `"en"` varsayılanı düzeltildi.
- Kullanıcının önceden kaydettiği veya seçeceği dil tercihleri eksiksiz korunur; varsayılanlar yalnızca anahtar henüz atanmamışken devreye girer.

### 2. Manga Eklentilerinde Video Eklentileriyle Tam Parite
- **Depo Kartı**:
  - **Her zaman görünen Yenile butonu**: Yükleme anında plazma göstergesiyle dinamik geri bildirim (önceden yalnızca depo boşken görünüyordu).
  - **Alt Eylem Şeridi**: 🇹🇷 Hızlı Türkçe indirme butonu (`yüklü/toplam`), aktif dil filtresine göre toplu indirme butonu ve repo düzeyinde otomatik güncelleme anahtarı (`MangaSwitchChip`).
- **Eklenti Satırları (Repo Listesi)**:
  - Kurulu eklentiler için **Yeniden Yükle/Güncelle butonu** ve eklenti başına **Oto-Güncelleme çipi**.
- **Kurulu Sekmesi (Installed)**:
  - **Genel Otomatik Güncelleme Ana Kartı**: Ana şalter kapatıldığında gereksiz ağ trafiği tamamen atlanır (`MangaExtensionPrefs`).
  - **Toplu Silme / Kaldırma**: Video eklentileriyle tam paritede "Eklentileri Kaldır" butonu ve `KitsugiConfirmDialog` onay mekanizması; tüm APK eklentilerini tek seferde güvenle temizler.
  - **Kurulu Eklenti Eylem Şeridi**: Güncelleme rozeti (`⬆️ vX`), yeniden kur/ayna yenile butonu, oto-güncelleme çipi, web doğrulama ve kaynak yönetim butonları.
- **Sistem İkonları Uyumluluğu**: Tüm bileşenler `com.kitsugi.animelist.ui.icons.KitsugiIcons` standardına uygun şekilde güncellendi.

---

## 🇬🇧 English Release Notes

### 1. Language Preferences Defaults Standardization (Settings → Appearance & Preferences → List & Score)
- **Anime & Manga Title Language**: Defaults to **English (`ENGLISH`)**.
- **Staff & Character Name Language**: Defaults to **English (`ENGLISH`)**.
- **Series & Movies Title Language (TMDB)**: Defaults to **Turkish (`tr`)**.
- Added unified constants `DEFAULT_TITLE_LANGUAGE`, `DEFAULT_STAFF_NAME_LANGUAGE`, and `DEFAULT_TMDB_LANGUAGE` in `SettingsDataStore.kt`; resolved residual `"en"` fallback in `SettingsScreenParameters.kt`.
- User-saved customizations are fully respected; default fallbacks only apply when preferences are unconfigured.

### 2. Manga Extensions Parity with Video Extensions
- **Repository Card**:
  - Always-visible **Refresh Button** with dynamic plasma loader during network fetches.
  - **Bottom Action Strip**: 🇹🇷 Quick Turkish install button (`installed/total`), bulk install button according to selected language filter, and repo-level auto-update toggle chip (`MangaSwitchChip`).
- **Extension Row (Repo Explorer)**:
  - Reinstall/refresh button for installed extensions alongside per-extension **Auto-Update** toggle chip.
- **Installed Tab**:
  - **Global Auto-Update Master Card**: Bypasses network scans upfront when disabled (`MangaExtensionPrefs`).
  - **Bulk Uninstall**: Complete parity with video extensions via "Uninstall Extensions" action with `KitsugiConfirmDialog` confirmation dialog to safely remove all APK extensions in one step.
  - **Installed Source Action Strip**: Update available badge (`⬆️ vX`), reinstall/mirror refresh button, auto-update toggle, source verification, and management controls.
- **Kitsugi Icons Standard**: Fully migrated to `com.kitsugi.animelist.ui.icons.KitsugiIcons` without external Material dependencies.
