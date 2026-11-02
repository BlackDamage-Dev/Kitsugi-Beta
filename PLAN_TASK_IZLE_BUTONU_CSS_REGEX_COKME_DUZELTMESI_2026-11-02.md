# PLAN TASK — İzle Butonu CSS Regex Çökme Düzeltmesi
**Tarih:** 2026-11-02
**Sürüm hedefi:** 2.4.395
**Öncelik:** P0 (FATAL crash)

## Hedef
"İzle" butonuna tıklanıp eklenti video akış sayfasına girildiği anda LazyList ilk kartı oluştururken ana thread'de meydana gelen `ExceptionInInitializerError` (Caused by `PatternSyntaxException`, `[.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}}` → Android ICU `}` kaçırımsızlığı) çökmesini kalıcı olarak düzeltmek.

## Değişiklik Listesi

| # | Dosya | Değişiklik Tipi | Açıklama |
|---|-------|----------------|----------|
| 1 | `app/src/main/java/com/kitsugi/animelist/core/regex/H9RegexCrashGuard.kt` | YENİDEN YAZMA | Cockroach tarzı Looper sarımı kuruldu. UncaughtExceptionHandler zincirine eklenen H9AwareHandler, bilinen H9 regex çökmesinde süreci öldürmek yerine `Looper.loop()` metodunu tekrar çağırarak ana iş parçacığını canlı tutuyor. Heuristik genişletildi: sadece tam bilinen desen değil, sondaki `}` kaçırılmamış olan ve öncesinde `{n}` / `{n,m}` niceleyicisi bulunan BÜTÜN ICU regex hataları da tanınıyor. Sonsuz döngü koruması (`insideGuard` bayrağı) eklendi. |
| 2 | `app/src/main/java/com/kitsugi/animelist/core/regex/RegexSafetyWarmup.kt` | GENİŞLETME | `compileSafely(pattern)` metoduna Android ICU sanitization katmanı eklendi: ilk derleme denemesi `PatternSyntaxException` ile başarısız olursa, sondaki kaçırılmamış `}` karakteri `\}` olarak kaçırılarak ikinci bir deneme yapılıyor. Yeni `sanitizeForAndroidIcu(pattern)` iç metodu eklendi. |
| 3 | `COZUM_RAPORU_IZLE_BUTONU_CSS_REGEX_COKMESI_2026-11-02.md` | YENİ | Kök neden, çözüm, doğrulama ve geleceğe notları içeren tam çözüm raporu. |

## Doğrulama Adımları (manuel test)
1. Uygulamayı APK olarak derle ve Xiaomi 23043RP34C (pipa, Android 13) veya benzer ARM64 cihaza yükle.
2. "The Mentalist" veya benzer bir dizi için ara ve HDFilmCehennemi eklentisi üzerinden akış ekranına gir.
3. İzle butonuna tıklayınca video akış kart listesinin sorunsuz açıldığını ve uygulamanın çökmediğini doğrula.
4. Kartlara tıklayıp oynatıcı başlatabildiğini kontrol et.
5. Başka dizilerde/filmlerde ve farklı eklentilerde de aynı senaryoyu tekrarla (HDFilmİzle, DiziBox, vb.).

## Riskler ve Azaltmalar
- **Risk:** Cockroach tekniği bazı hatalarda yarım UI durumu bırakabilir. **Azaltma:** Sadece bilinen H9 regex imzası ve benzer ICU-kaçırılmamış-`}` PSE'leri yakalanıyor; diğer TÜM hatalar normal KitsugiCrashHandler'a devrediliyor.
- **Risk:** Statik sınıf FAILED durumuna düştükten sonra aynı sınıfa erişim `NoClassDefFoundError` fırlatır. **Azaltma:** `NoClassDefFoundError` ve `LinkageError` da zincirde taranıyor, PSE ile birlikte görüldüğünde bilinen çökme olarak kabul ediliyor.
- **Risk:** Looper.loop() yeniden çağrılması ANR riski doğurabilir. **Azaltma:** Yeni loop yalnızca exception sonrası bir canlandırma post edildikten sonra başlıyor; Compose bir sonraki frame ile yeniden compose etmeyi deneyecektir.

## Kapsam Dışı (bu düzeltme ile ilgili olmayan)
- `CF_PATIENCE [240000ms timeout]` logları (ağ/Cloudflare; bu bir çökme değil, normal bekleme).
- `yts-subtitles.strem.io` manifest erişim hatası (ağ/bağlantı sorunları; fatal değil).
- `MediaEntryRepository.deduplicateSameSourceDuplicates` içindeki MIUIScout 2502 ms uyarısı (ayrı bir optimizasyon konusu).

## Tamamlanma Kriteri
- İzle akış ekranına girişte `F/CRASH  [main] ÇÖKME: ExceptionInInitializerError` logunun artık OLMAMASI
- Süreç ölümünün (Process.kill) gerçekleşmemesi
- Listenin kartları çizilmeye devam etmesi
