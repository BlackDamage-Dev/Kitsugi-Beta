# Kitsugi Beta v2.4.447

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Manga Okuyucu Kararlılığı & Son Değişikliklerin Geri Alınması
- **Manga Okuyucu Düzeltmesi Geri Alındı**:
  - v2.4.446 sürümünde eklenen Mangitto bölüm URL çözümleme mantığı (`restoreMangittoChapterUrl`) okuyucunun sayfa listesi yüklemesini bozduğu için tamamen geri alındı.
  - `MihonSourceWrapper.kt` içerisinde bölüm URL aktarımı önceki stabil ve doğrudan haline (`url = chapter.url`) döndürüldü.
  - `MihonChapterIdentity.kt` ve `MihonChapterIdentityTest.kt` dosyaları projeden kaldırıldı.
- **Mevcut Özelliklerin Korunması**:
  - v2.4.445 ile gelen kaynak bazlı giriş sistemi, Kotatsu/Mihon oturum ve çerez eşitleme yetenekleri, auth hata akışları ve dize simetrisi eksiksiz olarak korunmaktadır.

---

## 🇬🇧 English Release Notes

### 1. Manga Reader Stability & Rollback
- **Reverted Mangitto Chapter Identity Transformation**:
  - Fully rolled back the Mangitto chapter URL rewrite logic (`restoreMangittoChapterUrl`) introduced in v2.4.446 that disrupted reader page loading.
  - Restored `MihonSourceWrapper.kt` to its original, direct chapter URL forwarding (`url = chapter.url`).
  - Removed `MihonChapterIdentity.kt` and `MihonChapterIdentityTest.kt`.
- **Preserved Prior Features**:
  - All source-based authentication, Kotatsu/Mihon session management, cookie synchronization, and auth error recovery flows from v2.4.445 remain intact.
