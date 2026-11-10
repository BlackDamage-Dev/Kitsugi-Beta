# Kitsugi Beta v2.4.446

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Mangitto Eski Bölüm Kimliği Onarımı (Chapter Identity Restore)
- **Eski Kayıtlar İçin Güvenli Adres Çözümleme**:
  - Eski Mangitto kayıtlarında yalnızca bölüm numarası (`2`) depolandığında oluşan `Key mangaSlug is missing in the map` ve seri adresi eksik hataları giderildi.
  - `MangaChapter.mangaUrl` bilgisi kullanılarak `SChapter.url` okuyucuya iletilmeden önce `/manga/<slug>/<bölüm>` tam adresine güvenli biçimde dönüştürülür (`restoreMangittoChapterUrl`).
- **Katı Kapsam ve İzolasyon**:
  - Yalnızca `eu.kanade.tachiyomi.extension.tr.mangitto` paketi için çalışır; Manga-TR, MangaciX ve diğer eklentilerin adreslerine dokunulmaz.
  - Göreli (`/manga/chainsaw-man`) ve mutlak (`https://mangtto.com/manga/chainsaw-man`) ebeveyn adreslerini destekler; ondalıklı bölümlerin (`0.01`) baştaki sıfırlarını korur.
  - Global durum tutulmaz; seriler arasında geçiş yapıldığında seri kimliği başka bölümlere sızmaz. Ebeveyn eksik veya hatalıysa seri tahmini yapılmaz.
- **Kapsamlı Test Doğrulaması**:
  - Paketteki 8 yeni bağımsız JVM/JUnit regresyon testi (`MihonChapterIdentityTest`) entegre edildi ve projenin test suitinde yeşil geçti.

---

## 🇬🇧 English Release Notes

### 1. Mangitto Legacy Chapter Identity Restoration
- **Safe Parent-Based URL Resolution**:
  - Resolved `Key mangaSlug is missing in the map` and missing series slug errors caused by legacy chapter records storing solely a chapter number (`2`).
  - Automatically reconstructs full `/manga/<slug>/<chapter>` path via `restoreMangittoChapterUrl` using the chapter's own `MangaChapter.mangaUrl` before passing `SChapter` to the reader.
- **Strict Scope & Isolation**:
  - Targeted exclusively to `eu.kanade.tachiyomi.extension.tr.mangitto`; other sources (Manga-TR, MangaciX, etc.) remain untouched.
  - Supports both relative (`/manga/chainsaw-man`) and absolute HTTPS parent URLs, preserving leading zeros for fractional chapters (`0.01`).
  - Zero global state leakage across manga switching; strictly avoids speculative series guessing on malformed or missing parents.
- **Automated Regression Test Suite**:
  - Integrated 8 independent JVM/JUnit tests (`MihonChapterIdentityTest`), all passing successfully in the local build suite.
