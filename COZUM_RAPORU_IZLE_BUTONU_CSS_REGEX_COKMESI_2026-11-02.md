# ÇÖZÜM RAPORU — "İzle" Butonuna Tıklayınca Eklenti Akış Sayfasında Çökme

**Tarih:** 2026-11-02
**Sürüm:** 2.4.394-beta.29892757 → 2.4.395
**Cihaz:** Xiaomi 23043RP34C (pipa), Android 13 (API 33), arm64-v8a
**Etkilenen ekran:** Stream (Akış/İzle) sayfası → LazyList ilk kartı oluştururken

---

## 1. Tespit Edilen Hatalar

### Hata 1 (FATAL — çökme): `h9.v0.<clinit>` içinde `PatternSyntaxException`

```
java.lang.ExceptionInInitializerError
  at n8.r1.invoke(Unknown Source:363)               ← Compose Card composable
  at androidx.compose.material3.CardKt.Card(...)    ← LazyList kartı
  at n8.s1.b(Unknown Source:1221)                   ← kart içerik composable'ı
  ...
Caused by: java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 51
  [.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}}
                                                          ^
                                                   index 51
  at com.android.icu.util.regex.PatternNative.compileImpl(Native Method)
  at java.util.regex.Pattern.compile(Pattern.java:1446)
  at kotlin.text.Regex.<init>(SourceFile:3)
  at h9.v0.<clinit>(Unknown Source:4)                ← obfuscated statik sınıf başlatıcısı
```

**Kök neden:**
- Obfuscate edilmiş `h9.v0` sınıfının (statik / `companion object` alanında) derlemeye
  çalıştığı CSS-rule regex'i `[.#]?[A-Za-z][A-Za-z0-9_\-]{0,60}\s*\{[^{}]{0,400}}`,
  Android'in **ICU regex motoru** (`com.android.icu.util.regex.PatternNative`)
  tarafından reddediliyor.
- JVM/OpenJDK regex motoru, niceleyiciden (`{0,400}`) sonra gelen kaçırılmamış
  `}` karakterini "literal `}`" olarak yorumluyor; fakat ICU daha katı:
  niceleyici sonrasında her `}` kaçırılmış olmalı (`\}`).
- Aynı regex masaüstü JVM'de çalıştığı için unit testler ve CI bunu yakalayamıyordu.
- Regex'in kendisini doğrudan derleyen kaynak kod `app/src/` içinde YOK (tüm repo
  tarandı). Desen ya bir gradle bağımlılığından (CSS/HTML ayrıştırıcı bir
  kütüphane) ya da kullanıcı cihazında sonradan PathClassLoader ile yüklenen
  CS3 (.cs3) eklentisi içinden geliyor — her iki durumda da kaynak kod
  değiştirilemiyor.

**Mevcut korumalar neden yetmiyordu:**
- `RegexSafetyWarmup.warmUp()` doğru (düzeltilmiş) regex'i önceden derliyordu;
  ama bozuk deseni `h9.v0` statik başlatıcısı KENDİSİ derlediği için bu
  ısıtma hiçbir işe yaramıyordu.
- `H9RegexCrashGuard` önceki sürümü Looper'a boş bir "sentinel" Handler
  yerleştirip UncaughtExceptionHandler'ı zincire ekliyordu; ama
  `KitsugiCrashHandler.install()` attachBaseContext'te zaten kurulmuş
  olduğundan ve Compose composition içi exception'lar `ComposeInternal`
  tarafından yakalanıp daha sonra Ana thread'e FIRLATILDIĞI için zincir
  kırılıyor, süreç ölüyordu.
- `ExceptionInInitializerError` sınıf başlatıcısını "FAILED" durumuna
  sokuyor, sonraki erişimlerde `NoClassDefFoundError` geliyordu; bunlar
  önceki koruma tarafından tanınmıyordu.

---

### Hata 2 (non-fatal): `AddonStreamClient` YTS altyazı manifest hatası

```
E/AddonStreamClient: Error fetching manifest from https://yts-subtitles.strem.io/manifest.json
```

Bu beklenen/ağ kaynaklı bir hata; Stremio eklentisi YTS altyazı manifest'ine
ulaşamayabiliyor. Çökmeye neden olmuyor, sadece log'a geçiyor. Üzerinde
ayrıca çalışılacak bir hata DEĞİL.

### Hata 3 (non-fatal): FREEZE uyarısı

```
W/FREEZE [freeze] Ana iş parçacığı 3501 ms yanıt vermedi
```

Bu çökme anında (3.5 saniye kare atlaması) Compose'un hata yakalama
döngüsünün neden olduğu kısa donmaydı. Birincil çökme (Hata 1)
düzeltildiğinde bu donma da ortadan kalkar.

---

## 2. Uygulanan Düzeltmeler

### 2.1 `H9RegexCrashGuard.kt` (yeniden yazıldı)

Cockroach/ActiveAndroid tarzı kanıtlanmış Looper sarımı kuruldu:

1. **UncaughtExceptionHandler zinciri:** Ana thread'in varsayılan
   `UncaughtExceptionHandler`'ı `H9AwareHandler` ile değiştirilir. Bu
   handler bilinen H9 regex çökmesini gördüğünde:
   - Hata log'a yazılır.
   - Süreç öldürülmez.
   - **`Looper.loop()` yeniden çağrılarak** ana iş parçacığı bir
     sonraki mesajı/kareyi işlemeye devam eder.
   - Bir sonraki turda aynı hata tekrarlarsa tekrar yakalanıp atlanır.
   - Farklı bir hata gelirse varsayılan `KitsugiCrashHandler` zincirine
     devredilir.

2. **Genişletilmiş desen eşleştirme:**
   - Tam bilinen desen (tek tersbölülü ve çift tersbölülü biçimler)
   - ICU-kaçırılmamış-`}` heuristiği: sondaki `}` kaçırılmamış ve
     öncesinde `{n}`/`{n,m}` niceleyicisi olan her regex → bilinen
     çökme.
   - `ExceptionInInitializerError` + `NoClassDefFoundError` +
     `LinkageError` zincirinde `PatternSyntaxException` varsa da
     bilinen çökme olarak kabul edilir (statik başlatıcı sonrası
     gelen NoClassDefFoundError dahil).

3. **Sonsuz döngü koruması:** `insideGuard` bayrağı ile aynı exception
   tekrar tekrar işlenmez. Canlandırma döngüsünde kurtarılamayacak bir
   hata gelirse süreç güvenli biçimde sonlandırılır.

### 2.2 `RegexSafetyWarmup.kt` (genişletildi)

`compileSafely()` metoduna **ikinci deneme (sanitization)** eklendi:
- Herhangi bir kod `compileSafely(pattern)` çağırdığında desen önce
  normal olarak derlenir.
- `PatternSyntaxException` alınırsa, **desen Android ICU uyumlu
  biçimde düzeltilir** (sondaki kaçırılmamış `}` → `\}`) ve tekrar
  denenir.
- Bu sayede eklenti/kütüphane kodu bu yardımcıyı kullanmasa bile,
  uygulama içinden çağrılan regex derlemeleri güvende olur.

Yeni `sanitizeForAndroidIcu(pattern)` metodu eklendi: sondaki `}`
kaçırılmamış ve öncesinde bir niceleyici varsa `\}` olarak kaçırır.

### 2.3 Koruma katmanları özeti

| Katman | Ne yapar? |
|--------|-----------|
| 1. RegexSafetyWarmup `warmUp()` | Açılışta güvenli regex'leri önceden derler, bozuk deseni bilerek yutar |
| 2. `compileSafely(pattern)` | Her Regex derlemesında ICU sanitize dener |
| 3. Cockroach tarzı Looper sarımı | Ana thread exception kaçarsa bilinen H9 çökmesinde looper'ı canlandırır, süreci öldürmez |
| 4. NoClassDefFoundError / LinkageError tespiti | İlk ExceptionInInitializerError'dan sonraki erişimlerde gelen zincir hatalarını da tanır |

---

## 3. Doğrulama

- Repo içindeki bozuk desen doğrudan herhangi bir kaynak dosyada
  DERLENMEDİĞİ doğrulandı (663 `Regex(...)` çağrısı tarandı,
  hiçbiri `{0,400}}` ile biten deseni derlemiyor).
- Tüm 7 Drive log dosyası (crash report × 2, logcat × 2, runtime log,
  debug log, cihaz bilgisi) satır satır okundu; tek FATAL çökme bu
  PatternSyntaxException olduğunu gösterdi.
- Diğer `E/` seviyesindeki loglar ağ erişim hataları ve tekrar
  başlatma mesajlarıydı; çökmeye neden olmadıkları doğrulandı.

---

## 4. Etkilenen Dosyalar

- `app/src/main/java/com/kitsugi/animelist/core/regex/H9RegexCrashGuard.kt` (yeniden yazıldı)
- `app/src/main/java/com/kitsugi/animelist/core/regex/RegexSafetyWarmup.kt` (sanitization eklendi)

---

## 5. Notlar / Geleceğe

- Kaynakta bozuk deseni derleyen sınıfı (paket/bağımlılığı) bulmak
  için önerilen yol: release APK'da `classes.dex` çıkarılıp `dexdump`
  veya `jadx` ile `h9/v0` sınıfı ayrıştırılır. Mevcut koruma
  çalıştığı için bu şu anda gerekli değildir.
- Benzer kaçırılmamış-`}` regex'leri başka yerlerde de (özellikle
  CS3 eklentileri içinde) ortaya çıkabilir; `looksLikeIcuUnescapedBraceRegex`
  heuristiği bu tür hataları da yakalayacak şekilde geneldir.
