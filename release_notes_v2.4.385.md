# Kitsugi Beta v2.4.385

[TR] **Kitsugi-Manga-Plugins Eklenti Deposu Entegrasyonu, Toplu Kurulum ve Repo-Merkezli Manga Altyapısı**
[EN] **Kitsugi-Manga-Plugins Extension Repository Integration, Bulk Install & Repo-Centric Manga Architecture**

---

### [TR] Yenilikler ve İyileştirmeler

#### 1. Repo-Merkezli Manga Eklenti Sistemi (`Kitsugi-Manga-Plugins`)
- **Varsayılan Manga Deposu:** Manga bölümü, CloudStream eklenti mimarisinde olduğu gibi tamamen repo-merkezli bir yapıya kavuşturuldu. Resmi `Kitsugi-Manga-Plugins` deposu (`https://github.com/f26901964-eng/Kitsugi-Manga-Plugins`) ilk açılışta varsayılan repo olarak entegre edildi (`KitsugiMangaDefaultProfile`).
- **Katalog ve URL Normalizasyonu:** Depo kök URL'si ve kısa repo adları, `builds/index.json` uç noktasına otomatik olarak normalize edilir (`normalizeMangaRepoUrl`). Keiyoushi ve Mihon uyumlu JSON ile APK listesi formatları eksiksiz desteklenir.
- **Güvenli Fallback Politikası:** Depoda henüz derlenmemiş veya yayınlanmamış eklentiler için yapay gömülü kaynaklar yerine arayüzde açıklayıcı dizin durumu ve rehberlik kartları gösterilir.

#### 2. Tekli ve Toplu Kurulum (Bulk Install) Desteği
- **Mobil ve Tablet Arayüzü (`MangaExtensionTab`):** Depodaki tüm eklentileri tek dokunuşla indirme ve güncelleme imkanı sağlayan toplu kurulum mekanizması etkinleştirildi.
- **Android TV Desteği (`TvMangaExtensionScreen`):** TV arayüzündeki toplu kurulum butonu doğrudan manga bulk-install motoruna bağlandı; uzaktan kumanda ile tek seferde tüm manga eklentileri kurulabilir ve güncellenebilir.
- **Otomatik Güncelleme Takibi (`MangaExtensionAutoUpdater`):** Yüklü manga eklentilerinin sürüm kontrolü ve repo ile senkronizasyonu geliştirildi.

#### 3. Kotatsu Bağdaştırıcısı ve Altyapı İyileştirmeleri
- **Kotatsu Eklenti Adaptörü (`KotatsuExtensionAdapter`):** Eski/işlevsiz parser'lar devre dışı bırakılarak modern eklenti yükleyicisiyle uyumlu hale getirildi.
- **Hakkında Ekranı (`AboutScreen`):** Manga Eklenti Deposu bağlantısı ve lisans bilgileri Hakkında sayfasına eklendi.
- **Webkit & Ağ Güvenliği:** Eklenti yükleme ve kaynak doğrulama adımları için modern AndroidX Webkit 1.12.1 bağımlılığı tanımlandı.

#### 4. Regresyon ve Birim Test Güvencesi
- `MangaRepoIdentityTest`: Depo URL normalizasyonu, GitHub kök adresi çözümleme ve repo kimlik testleri başarıyla doğrulandı.
- Tüm `FossRelease` birim testleri (70 görev) sıfır hatayla geçti.

---

### [EN] Release Notes

#### 1. Repo-Centric Manga Extension Architecture (`Kitsugi-Manga-Plugins`)
- **Default Manga Repository:** The manga subsystem has been restructured to follow a modular, repo-centric model akin to CloudStream plugins. The official `Kitsugi-Manga-Plugins` repository (`https://github.com/f26901964-eng/Kitsugi-Manga-Plugins`) is now integrated as the default repository out-of-the-box (`KitsugiMangaDefaultProfile`).
- **Catalog & URL Normalization:** Repository root URLs and short names are normalized automatically to the `builds/index.json` catalog endpoint (`normalizeMangaRepoUrl`), with complete support for Keiyoushi/Mihon compatible formats and APK listings.
- **Graceful Diagnostics:** In the absence of an index catalog, clear informative states and setup guidance are shown rather than relying on brittle embedded fallbacks.

#### 2. Single & Bulk Install Experience
- **Mobile & Tablet Interface (`MangaExtensionTab`):** Enabled bulk installation to allow installing or updating all repository extensions in a single action.
- **Android TV Navigation (`TvMangaExtensionScreen`):** Connected the TV interface bulk install button directly to the manga extension manager, enabling full 1-click extension installs via D-pad navigation.
- **Auto-Update Engine (`MangaExtensionAutoUpdater`):** Enhanced version comparison and background update checking for installed extensions against the active catalog.

#### 3. Kotatsu Adapter Modernization & App Enhancements
- **Kotatsu Extension Adapter (`KotatsuExtensionAdapter`):** Retired legacy parsers in favor of the modular plugin loader.
- **About Screen (`AboutScreen`):** Added reference and source links for the Kitsugi Manga Plugins repository.
- **Dependencies & Network Layer:** Added AndroidX Webkit 1.12.1 for webview and extension verification pipelines.

#### 4. Test Suite Verification
- `MangaRepoIdentityTest`: Successfully validated URL normalization, GitHub shorthand resolution, and repo identification.
- All `FossRelease` unit test suites passed with zero failures.

---

### İndirilebilir Dosyalar / Downloadable Assets
- `Kitsugi-Beta-v2.4.385-foss.apk`
