# Çözüm Raporu — Eklentiler & Kaynaklar > Manga Kaynakları > "Toplu Sil" basılmıyor (2026-10-27)

## Şikâyet
Ayarlar → **Eklentiler & Kaynaklar → Manga Kaynakları → Installed (121)** ekranındaki
"Toplu sil" kutusu görünüyor ama dokununca hiçbir şey olmuyor (soluk/devre dışı görünüm).

## Kök neden (iki katmanlı)
1. **Görünür liste ≠ silinebilir eklenti.** `Installed (121)` listesindeki kayların büyük
   bölümü uygulama içine derlenmiş **Kotatsu** kaynakları (`pkgName = kotatsu.*`). Bunların
   `MangaExtensionLoader` içinde bir `.apk`/`.mex` dosyası yok. Eski akış listedeki her
   `MangaSource` için `MangaExtensionLoader.getExtensionIdForSource()` → `null` →
   `deleteExtension()` → `false` dönüyordu. Sonuç: buton basılır, hiçbir şey değişmez.
2. **Tersine bağımlılık / ölü callback.** Video Sağlayıcıları sekmesinde aynı sınıf hata
   daha önce `onUninstallAllCsPlugins` varsayılan no-op olarak bırakıldığı için yaşanmıştı.
   Manga tarafında da ekran katmanıyla ViewModel arasında gerçek bir toplu silme yolu yoktu;
   buton ya `enabled = false` ile donduruluyor ya da boş bir callback'e bağlanıyordu.
3. **Yan etki:** `getAvailableSources()` `Disabled` kaynakları filtreler. Sadece görünür
   listeyi silmek, devre dışı bırakılmış/yüklenememiş eklentileri diskte bırakıyordu.

## Yapılan düzeltme
- **Disk esas alındı:** `MangaExtensionLoader.getInstalledExtensionFiles()` ile
  `manga_extensions/` klasöründeki **tüm** `.apk`/`.mex` dosyaları silinir; görünür liste
  değil disk kaynaktır. Silinen eklentilerin tanılama/ayar kayıtları da temizlenir.
- **Yerleşik kaynaklar için doğru davranış:** silinecek dosyaları olmayan Kotatsu
  kaynakları `SourceHealthStatus.Disabled` yapılarak listeden ve aramadan çıkarılır.
  Bu işlem **geri alınabilir**: gizlenen kaynakların anahtarları
  `manga_bulk_delete` SharedPreferences dosyasında tutulur, ekrandaki
  **"Geri getir (N)"** butonu ile tekrar açılır (uygulama yeniden başlasa da görünür).
- **Buton artık gerçekten basılır:** `Toplu Sil` her zaman `enabled`, 44 dp dokunma alanı,
  kırmızı yıkıcı stil, ikon + metin. Basınca `KitsugiConfirmDialog` onayı açılır
  (telefon + TV uyumlu), onay → `MangaViewModel.deleteAllMangaExtensions()`.
  Callback'ler `SettingsScreenParameters → KitsugiAddonsSettingsDialog →
  MangaExtensionsTab` üzerinden **varsayılan no-op olmadan** bağlanır; state `null` ise
  buton hiç çizilmez (ölü buton gösterilmez).
- **Her durumda geri bildirim:** kaç eklenti silindi, kaç yerleşik kaynak gizlendi,
  kısmi hata, "silinecek bir şey yok" ve genel hata için ayrı mesajlar (TR + EN).
  `mangaIsBulkDeleting` bayrağı `finally` içinde sıfırlanır → hata sonrası buton kilitlenmez.
- **Performans:** 121 kaynak için 121 ayrı SharedPreferences/DB yazımı yerine
  `MangaSourceStateStore.setHealthStatusForAll()` tek `edit().apply()` + tek
  `MangaSourceStateDao.upsertAll()` kullanır.

## Değişen dosyalar
`MangaExtensionLoader.kt`, `MangaSourceRepository.kt`, `MangaSourceStateStore.kt`,
`MangaSourceStateEntity.kt`, `MangaViewModel.kt`,
`ui/components/addons/MangaBulkDeleteState.kt` (yeni), `MangaExtensionTab.kt`,
`KitsugiAddonsSettingsDialog.kt`, `SettingsScreen.kt`,
`SettingsScreenParameters.kt`, `AppRootSettingsExtras.kt`, `MangaBrowseScreen.kt`,
`values/strings.xml`, `values-en/strings.xml`,
`app/src/test/.../MangaBulkDeleteWiringTest.kt` (yeni).

## Doğrulama
- `python3 scripts/check_localization_resource_parity.py` → **17708 string + 4 dizi, TR/EN eşit, placeholder'lar eşleşiyor**.
- Yeni 12 string'in tamamı iki dilde tanımlı; koddaki tüm `R.string.manga_bulk_*` referansları çözülüyor.
- "Buton `enabled = false` değil / gerçek callback / disk tabanlı silme / geri getir"
  sözleşmeleri `MangaBulkDeleteWiringTest` (JUnit) ile koruma altında; aynı kontroller
  depo üzerinde statik olarak da çalıştırılıp geçti.
- Not: Bu ortamda Java/Android SDK bulunmadığı için `./gradlew` derlemesi çalıştırılamadı;
  CI derlemesi önerilir.

## Kullanıcı için davranış
1. Manga Kaynakları → Installed → **Toplu Sil** → onay.
2. İndirilmiş eklentiler diskten silinir; yerleşik kaynaklar listeden çıkar.
3. Snackbar sonucu gösterir, `Installed` sayacı güncellenir.
4. **Geri getir (N)** ile yerleşik kaynaklar istenirse anında geri açılır.
