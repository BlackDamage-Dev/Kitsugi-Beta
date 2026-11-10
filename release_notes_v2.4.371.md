# Kitsugi Beta v2.4.371

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Yüklü Eklenti ve Depo Eşitlemesi (Uçtan Uca Şifreli)
- **Kasa Entegrasyonu (`AddonVaultBackup` & `LinkedAccountVault`):** Stremio eklenti ayarları, CloudStream ve Manga depoları ile kurulu eklenti tanımları mevcut uçtan uca şifreli Kitsugi kasasına eklendi. Supabase şemasında herhangi bir değişiklik yapılmadan mevcut güvenlik mimarisi korundu.
- **Ayrı Kategori Anahtarı (`accountAddonSyncEnabled`):** Hesap ekranına "Yüklü eklentileri eşitleme" seçeneği eklendi. Eklenti eşitlemesi bağımsız olarak açılıp kapatılabilir; kapatıldığında buluttaki mevcut yedek korunur.
- **Granüler Birleştirme (`VaultMerge`):** Her eklenti ve depo bağımsız bir merge grubu (`addons:key`) olarak işlenir. Farklı cihazlarda eklenen farklı eklentiler çakışma yaratmadan otomatik olarak birleştirilir.

### 2. Güvenli Geri Yükleme ve Depo Güven Onayı
- **Çalıştırılabilir Dosyalar Buluta Yüklenmez:** Eklenti kodları (APK, CS3 vb.) veya çalıştırılabilir ikili dosyalar kasada tutulmaz; yalnızca meta veriler ve repo tanımları eşitlenir.
- **Kullanıcı Onaylı İndirme:** Yeni bir cihazda eklentiler otomatik indirilmez; hesap ekranında açık kullanıcı onayı alındıktan sonra güncel depolardan çekilir.
- **Bilinmeyen Kaynak Koruması:** Depo güveni başka cihaza körü körüne taşınmaz. Bu cihazda daha önce güvenilmemiş kaynaklar için ayrı onay istenir; CloudStream kaynak kontrolleri ve Manga APK imza güvenliği tavizsiz korunur.

### 3. Otomatik Eşitleme & Gelişmiş Raporlama
- **Anlık Değişiklik Tetikleyicileri:** Room veritabanı, manga depo tercihleri ve eklenti dosyalarındaki değişiklikler otomatik yedekleme işini tetikler.
- **Bağımsız Eşitleme Raporu (`AccountSyncReport`):** "Şimdi eşitle" raporunda `ADDONS` ayrı bir adım olarak takip edilir; bir kategorideki hata diğerlerinin başarısını engellemez.
- **Döngü Koruması:** Parmak izi (fingerprint) kontrolüyle gereksiz Room observer ve WorkManager döngüleri engellenir.

---

## 🇬🇧 English Release Notes

### 1. Installed Add-on & Repository Sync (End-to-End Encrypted)
- **Vault Integration (`AddonVaultBackup` & `LinkedAccountVault`):** Stremio add-on configurations, CloudStream & Manga repositories, and installed extension records are now synced inside the existing end-to-end encrypted Kitsugi vault without modifying the Supabase backend schema.
- **Independent Category Switch (`accountAddonSyncEnabled`):** Added a dedicated "Installed add-on sync" toggle in the Account screen. Add-on sync can be toggled independently without affecting account or search backups.
- **Fine-Grained Three-Way Merge (`VaultMerge`):** Each add-on and repository is handled as an independent merge group (`addons:key`), preventing conflicts when different devices install different add-ons concurrently.

### 2. Safe On-Demand Restore & Repository Trust
- **No Binaries in Cloud Vault:** Executable binaries (APK, CS3, etc.) are never uploaded to the cloud; only clean metadata and repository references are stored.
- **User-Confirmed Download:** Restoring extensions on a new device requires explicit user confirmation in the Account screen before files are downloaded from active repositories.
- **Untrusted Source Guard:** Trust is never silently propagated across devices. Unrecognized hosts require separate user approval before any code runs, preserving signature and source validation.

### 3. Automatic Synchronization & Robust Reporting
- **Automated Inventory Observers:** File changes, Room database updates, and repository preference edits immediately schedule a background backup.
- **Independent Sync Reporting (`AccountSyncReport`):** The "Sync now" status dialog reports `ADDONS` as an isolated category; failures in one section never hide successes in others.
- **Fingerprint-Based Idempotency:** Prevents redundant WorkManager and observer loops.
