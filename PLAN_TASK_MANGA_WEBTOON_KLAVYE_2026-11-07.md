# PLAN / TASK — Manga-Webtoon kesintisiz okuyucu ve klavye denetimi

**Tarih:** 2026-11-07
**Dal:** `arena/2b0a5520-kitsugi-beta`
**Baz commit:** `b78cbc4cdc39808cb0ec475026edef6b464b71ed`
**Durum:** Kod, araştırma ve yerel kontroller tamamlandı; tam Android derlemesi ve cihaz kabulü bekliyor. Bu belge bir APK veya cihaz doğrulama raporu değildir.

## 1. Kullanıcı isteği / kapsam

- Webtoon görsellerinin ayrı ayrı kaydırılması yerine tek, kesintisiz dikey şerit.
- Açık kaynak okuyucular araştırılarak uygulamaya uygun mimari ve özelliklerin uyarlanması.
- Okuyucu ve açılan panellerde aynı işlevi tekrarlayan kontrollerin kaldırılması.
- Mevcut oynatıcıdaki odak/tuş sahipliği yaklaşımıyla manga okuyucusuna kapsamlı klavye denetimi.
- Değişen dosyalar ile plan/task ve araştırma belgesinin ZIP olarak teslimi.

## 2. Uygulama görevleri

| Görev | Durum | Çıktı |
|---|---|---|
| T01 — Mihon, Aniyomi, Kotatsu, Suwayomi ve Telephoto kaynak/lisans araştırması | [x] | Sabit commit bağlantıları ve uyarlama gerekçeleri araştırma raporunda |
| T02 — Bağımsız Webtoon sayfa pan/zoom alanlarını kaldırma | [x] | Tek lazy şerit ve ortak viewport dönüşümü |
| T03 — Uzun görselleri kontrollü bellekle çizme | [x] | EXIF bilgisi, region decode, sınırlı önbellek ve eşzamanlılık |
| T04 — Ortak pinch, çift dokunma ve yakınlaştırma altında pan | [x] | Şerit genelinde zoom/pan; kaynak konumuna bağlı geometri |
| T05 — Ekran boyuna göre dokunma/tuşla ilerleme ve otomatik kaydırma | [x] | Ayarlanabilir ilerleme mesafesi, hız ve durdurma koşulları |
| T06 — Ayar/panel tekrarlarını temizleme ve TV/mobil ortaklaştırma | [x] | Tek ayar girişi, birbirini dışlayan paneller ve ortak okuyucu |
| T07 — Merkezi klavye politikası ve odak koruması | [x] | Gezinme, bölüm, panel, sayfa, görünüm, auto-scroll, zoom ve yardım |
| T08 — Tercih kimliklerini ve eski değerleri normalleştirme | [x] | Kanonik okuma/filtre seçenekleri; gerçek VerticalPager |
| T09 — Telephoto 0.8 / Coil 2 uyumluluğu ve yükleyici durumları | [x] | Kaynak önbelleği ve metadata hazır olmadan Ready yayımlamama |
| T10 — Uzun görsel içi ilerleme ve geçmiş koruma | [x] | Sayfa içi oran, sıralı/debounce kayıt, yaşam döngüsünde flush |
| T11 — Room 29 → 30 geçişi | [x] | Varsayılan 0 oranı, ALTER migration ve şema 30; gerçek Android/Room doğrulaması ayrıca bekliyor |
| T12 — TR/EN kaynaklar, regresyon kontrolleri ve araştırma raporu | [x] | Yerel kontroller geçti; ayrıntılar aşağıda |
| T13 — Dört Android instrumentation testi yazma | [x] | Eşit genişlik/sıfır boşluk, tek scroll sahibi, zoom sonrası son ve region decode |
| T14 — Tam Android/Compose/KSP derlemesi ve cihaz kabulü | [ ] | Gradle indirmesinde SSL/EOF engeli; Android SDK yok; APK üretilmedi |

## 3. Doğrulama durumu

- [x] Gerçek üretim Kotlin politikaları: **37 test grubu** geçti.
- [x] SQLite geçişi, şema ve statik entegrasyon sözleşmeleri: **15 kontrol** geçti.
- [x] Mevcut oynatıcı politika regresyonu: **27 test grubu** geçti.
- [x] Değişen/yeni Kotlin dosyalarının PSI sözdizimi: **30 dosya** geçti. Bu kontrol Android/Compose tip derlemesi değildir.
- [x] Yerelleştirme anahtar/format eşliği: **17.733 string, 4 array** geçti.
- [x] `git diff --check` temiz.
- [ ] `:app:compileFossDebugKotlin`: Gradle 8.13 indirmesinde SSLHandshakeException/EOF nedeniyle tamamlanamadı.
- [ ] APK üretimi ve gerçek Android/Room açılışı.
- [ ] Eklenen dört instrumentation testinin emülatör/cihazda çalıştırılması.
- [ ] Solo Leveling kaynağı, düşük RAM cihazı, Bluetooth/USB klavye ve TV kumandası ile kabul testleri.

**Şema notu:** `30.json` bu ortamda KSP tarafından üretilmedi. Room kimlik algoritması mevcut 25–29 şemalarıyla birebir doğrulandıktan sonra türetildi; tam Android/KSP kontrolü bekliyor.

Yeniden çalıştırma:

```bash
JAVA_HOME=/path/to/java17 \
KOTLINC=/path/to/kotlin-2.0.21/bin/kotlinc \
bash scripts/run_manga_reader_checks.sh

python3 scripts/check_localization_resource_parity.py
git diff --check

# Android SDK ve Gradle erişimi bulunan ortamda:
./gradlew :app:testFossDebugUnitTest --tests '*MangaReaderPolicyTest'
./gradlew :app:assembleFossDebug
./gradlew :app:connectedFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kitsugi.animelist.ui.screens.manga.MangaWebtoonReaderTest
```

## 4. Teslim ve dosya uygulama notu

- Arşiv, baz commit'e göre değişen ve yeni dosyaları proje içindeki yollarıyla içerir; tüm depo veya APK değildir.
- Bu plan/task belgesi ve `docs/MANGA_WEBTOON_KEYBOARD_AUDIT_2026-11-07.md` araştırma raporu pakete dahildir.
- Tam değişiklik yaması (`CHANGES.patch`), dosya listesi/hash'leri (`MANIFEST.json`), silinenler listesi (`DELETED_FILES.txt`) ve uygulama açıklaması (`README_TR.md`) ayrıca bulunur.
- **Silinmesi gereken eski dosya:** `app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaReaderControls.kt`. Yalnızca dosyaları üzerine kopyalamak silme işlemini gerçekleştirmez; yama veya silinenler listesi dikkate alınmalıdır.
- Arşiv Drive'a yüklenecek; indirme bağlantısı sohbetten verilir. `.git`, kimlik bilgileri, yerel derleme/SDK önbellekleri ve ilgisiz dosyalar teslim kapsamı dışındadır.

## 5. Ayrıntılı rapor / sonraki kabul adımları

Araştırma kaynakları, lisans yaklaşımı, mimari, tam kısayol tablosu, bellek sınırları ve cihaz kontrol listesi:

[docs/MANGA_WEBTOON_KEYBOARD_AUDIT_2026-11-07.md](docs/MANGA_WEBTOON_KEYBOARD_AUDIT_2026-11-07.md)

Bilinçli kapsam dışı: OCR/çeviri, otomatik panel ayırma/kırpma, sessiz bölüm atlama, yeni indirme motoru, seri bazlı tam tercih profili ve kullanıcı tarafından tuş yeniden eşleme. Programatik `+/-` zoom Webtoon içindir; Telephoto 0.8 pager tarafı için desteklenmeyen bir API vaat edilmedi.
