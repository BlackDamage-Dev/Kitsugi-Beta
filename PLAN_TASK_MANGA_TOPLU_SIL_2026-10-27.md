# PLAN & TASK — Manga Kaynakları "Toplu Sil" Butonu (2026-10-27)

**Dal:** `arena/ed762147-kitsugi-beta`
**Hedef sürüm:** mevcut beta hattı (yeni sürüm numarası verilmedi)
**İlgili rapor:** `COZUM_RAPORU_MANGA_TOPLU_SIL_2026-10-27.md`
**Commit:** `adbd547` (+ bu plan dosyası)

---

## 1. Problem tanımı

Ayarlar → **Eklentiler & Kaynaklar → Manga Kaynakları → Installed (121)** ekranında
"Toplu sil" kutusu görünmesine rağmen dokununca hiçbir şey olmuyor; kutu soluk/devre dışı
görünüyor. Kullanıcı açısından buton "basılmıyor".

### Kabul edilen kök nedenler
1. Görünür `Installed` listesi ağırlıklı olarak uygulama içine derlenmiş Kotatsu
   kaynaklarından oluşuyor. Bunların `manga_extensions/` altında bir `.apk`/`.mex`
   dosyası yok → `MangaExtensionLoader.getExtensionIdForSource()` `null`,
   `deleteExtension()` `false`. Tüm satırlar sessizce başarısız oluyor.
2. Ekran → ViewModel arasında gerçek bir toplu silme callback'i yok (no-op / disabled).
3. `getAvailableSources()` `Disabled` kaynakları filtrelediği için yalnızca görünür
   listeyi silmek, devre dışı/yüklenememiş eklentileri diskte bırakıyor.

---

## 2. Plan

| # | Adım | Dosya(lar) | Durum |
|---|------|------------|-------|
| P1 | Diskteki tüm `.apk`/`.mex` dosyalarını listeleyen yardımcı | `MangaExtensionLoader.kt` | ✅ |
| P2 | Yerleşik kaynakları ayıran erişimci | `MangaExtensionLoader.kt` | ✅ |
| P3 | Toplu silme + geri getirme iş mantığı, kalıcı anahtar seti | `MangaSourceRepository.kt` | ✅ |
| P4 | N kaynak için tek seferde prefs + Room yazımı | `MangaSourceStateStore.kt`, `MangaSourceStateEntity.kt` | ✅ |
| P5 | ViewModel state + iki yeni fonksiyon, hata toleransı | `MangaViewModel.kt` | ✅ |
| P6 | Tek taşıyıcı state nesnesi (DEX yazmaç sınırı için 1 parametre) | `MangaBulkDeleteState.kt` (yeni) | ✅ |
| P7 | Her zaman aktif buton + onay diyaloğu + "Geri getir" | `MangaExtensionTab.kt` | ✅ |
| P8 | Callback zincirinin 3 katmanda bağlanması | `KitsugiAddonsSettingsDialog.kt`, `SettingsScreen.kt`, `SettingsScreenParameters.kt`, `AppRootSettingsExtras.kt`, `MangaBrowseScreen.kt` | ✅ |
| P9 | TR + EN string'ler (12 yeni anahtar) | `values/strings.xml`, `values-en/strings.xml` | ✅ |
| P10 | Regresyon testi + çözüm raporu | `MangaBulkDeleteWiringTest.kt`, `COZUM_RAPORU_*.md` | ✅ |
| P11 | CI'da Gradle derlemesi + cihazda elle doğrulama | — | ⏳ CI/cihaz |

---

## 3. Görev listesi (T1–T8)

- [x] **T1 — Disk kaynağı:** `getInstalledExtensionFiles()` tüm `.apk`/`.mex`
      dosyalarını döner; görünür liste yerine disk esas alınır.
- [x] **T2 — Yerleşik kaynak ayrımı:** `getBuiltInSources()` Kotatsu kaynaklarını
      verir; `getLoadedExtensions()` tanılama temizliği için kimlik eşlemesi sağlar.
- [x] **T3 — Silme motoru:** `deleteAllExtensions()` dosyaları siler, tanılama
      kayıtlarını temizler, yerleşik kaynakları `Disabled` yapar ve sonuç sayılarını
      (`deletedExtensions`, `failedExtensions`, `hiddenBuiltIns`) döner.
- [x] **T4 — Geri alınabilirlik:** gizlenen kaynakların `stableSourceKey()` değerleri
      `manga_bulk_delete` prefs dosyasında tutulur; `restoreBulkDeletedBuiltIns()` ile
      `Unknown` durumuna çekilip tekrar listeye dahil edilir. Uygulama yeniden başlasa
      da "Geri getir (N)" görünür.
- [x] **T5 — Buton:** `enabled = false` **yok**; 44 dp dokunma alanı, kırmızı yıkıcı
      stil, ikon + metin, basınca `KitsugiConfirmDialog` (telefon + TV).
- [x] **T6 — Callback zinciri:** state `null` ise buton hiç çizilmez; dolayısıyla hiçbir
      ekranda callback'siz ölü buton bulunmaz. Her iki çağrı noktası
      (Ayarlar + MangaBrowse) gerçek `deleteAllMangaExtensions()` fonksiyonuna bağlı.
- [x] **T7 — Geri bildirim:** başarı / kısmi hata / boş liste / genel hata için ayrı
      mesajlar; `mangaIsBulkDeleting` `finally` içinde sıfırlanır.
- [x] **T8 — Test & yerelleştirme:** `MangaBulkDeleteWiringTest` (4 test) + TR/EN
      parity script'i geçti.

---

## 4. Kabul kriterleri

1. Manga → Installed ekranında **Toplu Sil** butonu görünür, dokunulabilir ve basınca
   onay diyaloğu açılır.
2. Onay sonrası indirilmiş tüm eklenti dosyaları diskten silinir; `Installed` sayacı
   güncellenir ve ekranda sonucu anlatan bir mesaj görünür.
3. Yerleşik Kotatsu kaynakları listeden/aramadan çıkar; **Geri getir (N)** butonu
   görünür ve basınca kaynaklar geri gelir.
4. Hiç silinecek kaynak yokken buton yine çalışır ve bilgilendirici mesaj gösterir.
5. İşlem sırasında hata oluşsa bile buton bir sonraki denemede çalışır.
6. TR/EN string anahtarları ve placeholder'ları eşleşir.

---

## 5. Doğrulama

- `python3 scripts/check_localization_resource_parity.py` → **PASS** (17708 string + 4 dizi).
- Yeni 12 string TR + EN tanımlı; koddaki tüm `R.string.manga_bulk_*` referansları çözülüyor.
- JUnit wiring sözleşmeleri statik olarak da doğrulandı: buton `enabled` değil,
  gerçek callback çağrılıyor, silme disk tabanlı, `finally` ile bayrak sıfırlanıyor.
- ⚠️ Bu ortamda Java/Android SDK bulunmadığından `./gradlew assembleDebug` ve
  `./gradlew testDebugUnitTest` çalıştırılamadı. CI veya lokal Android Studio derlemesi
  ile doğrulanmalı.

---

## 6. Cihazda elle test senaryosu

1. Ayarlar → Eklentiler & Kaynaklar → Manga Kaynakları → Installed.
2. **Toplu Sil** → onay → sonuç mesajını ve sayaç değişimini gözle.
3. **Geri getir (N)** → kaynakların geri geldiğini gözle.
4. Tekrar **Toplu Sil**, ardından uygulamayı kapat/aç → **Geri getir** hâlâ görünür.
5. Hiç kaynak yokken **Toplu Sil** → bilgilendirici mesaj, buton hâlâ aktif.
