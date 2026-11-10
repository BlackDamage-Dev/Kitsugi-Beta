# 07 · ARAÇLAR VE CI DOĞRULAMA HATTI (`2026-10-23` · `c8e77e5`)

## 1. GitHub Actions Otomatik Tam Derleme, JUnit ve R8 DEX Hattı (`arena/86b99c20-kitsugi-beta`)
Her `git push` sonrasında üç bağımsız iş akışı JDK 17 + Android SDK 36 + NDK ile otomatik çalışır:
1. **`🏗️ Android Derleme Doğrulama` (`.github/workflows/android-build.yml`)**
   - Tüm çapraz eşitleme, kimlik, 720-permütasyon, veritabanı güvenliği ve HTTP istemci JUnit testleri
   - `assembleFossDebug` + `assembleFossRelease` (R8 tam küçültme)
   - `python3 scripts/verify_apk_dex_bytecode.py` (`VerifyError = 0` ve 255 yazmaç sınırı kanıtı)
2. **`🧩 Ortak Taksonomi Doğrulama` (`.github/workflows/taxonomy-master.yml`)**
   - `python3 scripts/verify_taxonomy_master.py` + `TaxonomyLabelsTest`
3. **`🎌 Bangumi MASTER Doğrulama` (`.github/workflows/bangumi-master.yml`)**
   - `verify_bangumi_master.py` + `generate_kotlin_from_master.py --check` + `kotlinc Main.kt` + `BangumiMasterDictionaryTest`

## 2. Yardımcı Python Betikleri (Drive Klasörü)
- `kcheck.py`: Tree-sitter hızlı Kotlin sözdizimi kontrolü.
- `fuzz_merge.py` / `fuzz2.py`: `CrossSyncMergePolicy` model invaryant simülasyonu.

## 3. Yerel Geliştirme Makinesinde Çalıştırma
```bash
./gradlew :app:testFossDebugUnitTest :app:assembleFossDebug
```
