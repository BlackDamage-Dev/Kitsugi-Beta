# Doğrulama raporu — 2026-10-24

## Güncel çalıştırılan kontroller

| Kontrol | Sonuç | Kanıt/kapsam |
|---|---:|---|
| Kimlik, provider-scope, Listem gruplama, migrasyon, TMDB contract ve notification-policy testleri | **61/61 PASS** | Gerçek üretim kaynakları; `/tmp/kitsugi-pure/test-tmdb-current.log` |
| Notification scheduler policy | **7/7 PASS** | `/tmp/kitsugi-pure/test-scheduler-latest.log` |
| Cross-write verification gate | **3/3 PASS** | `/tmp/kitsugi-pure/test-gate-latest.log` |
| Fonksiyonel TMDB akışları | **8/8 grup PASS** | Gerçek auth/client/mutex/coordinator/sync/poller/backup/drainer kaynakları; `/tmp/kitsugi-functional/test.log` |
| Backup JUnit testleri | **2/2 PASS** | Fonksiyonel paketin içinde `MediaEntryBackupTmdbTest` çalıştırılır |
| TMDB hata sınıflandırması | **12/12 PASS** | `/tmp/kitsugi-error-test/test.log`; ilgili production kaynakları testten sonra değişmedi |
| Room 29→30 SQL ve identity hash | **PASS** | 35 kolon; 30 hash `7ac1f9538df18d2e5dd257582a0f27df` |
| TR/EN XML | **PASS** | Her dosyada 17.597 string; mükerrer isim yok |
| `git diff --check` | **PASS** | Whitespace hatası yok |

Seçili saf birim testlerinin toplamı **71** (61 + 7 + 3). Fonksiyonel gruplar ve iki backup testi ayrıca raporlanır; bunları tam Gradle test sayısı gibi yorumlamamak gerekir.

## Fonksiyonel pakette kanıtlanan akışlar

1. Auth URL, session üretimi, yanlış callback'in yeni tokenı silmemesi ve sekiz yarışan tüketiciden yalnız birinin tokenı alması.
2. Altı koleksiyonun film/TV ve native alanlarla birleşimi; her HTTP isteğinde limiter.
3. Alan-maskeli yazma, bounded 429 retry, account-state preflight, idempotent yazmama, rating DELETE ve pre/mid-write account-switch guard.
4. Bilinçli bloklanan snapshot sırasında yazmanın ağa çıkmaması; ortak mutex ile sıralama.
5. Hesap değişen snapshot'ın local import'a hiç ulaşmaması.
6. Bildirim ilk-baseline sessizliği, teslim hatasında baseline'ın ilerlememesi, güvenli retry ve hesap-bazlı anahtar.
7. Native alanlı backup round-trip ve legacy merge.
8. Pending mask/account round-trip, yanlış hesaba kuyruk işi göndermeme, legacy fallback ve bozuk işin retry için korunması.

## Yeni regresyon kapsamı

- Aynı başlık/yıl, çelişen provider ID'lerini mükerrer saymaz; TMDB hesap lookup'ı Simkl/katalog üyeliğini ödünç almaz.
- Native Watchlist ve yarım rating local status/Int score'dan yeniden üretilmez.
- Insert'te false/null yoklukları uzak silme değildir; update'te yalnız değişen native alanlar yazılır.
- Canlı account-state ile aynı olan alanlar için POST/DELETE atılmaz; rating temizleme uzakta puan varsa DELETE olur.
- Otomatik senkron kapalıyken doğrudan `tmdb_account` kullanıcı işlemi yazılabilir; kardeş sağlayıcıdan otomatik fan-out kapalı kalır.
- Pending kuyrukta pozitif `_tmdbAccountId` hedef hesabı, `0` işlem anında hesap olmadığını, alanın yokluğu legacy işi belirtir.
- Bildirim baseline'ı account ID'ye bağlıdır; film ve aynı sayısal ID'li TV ayrı fingerprint üretir.

## Çalıştırılamayan / henüz kanıtlanmayan

- **Tam Gradle/Android build ve APK üretimi yapılmadı.** Sandbox'ta Android SDK yok; Gradle/JDK edinme yolları ortam ağ engeline takıldı.
- Son Compose, auth-manager, notification-store ve ViewModel değişiklikleri tam uygulama derlemesinden geçirilmedi; izole/fonksiyonel harness tam Android build'in yerine geçmez.
- Gerçek TMDB ağı/hesabı, kullanıcı onayı, session üretimi ve uzaktan yazma bu ortamda çalıştırılmadı.
- Telefon/TV görsel test, erişilebilirlik ve Room cihaz migrasyon testi yapılmadı.
- v4 mixed/private özel liste yazımı kapsam dışıdır ve v3 session ile taklit edilmez.

Bu ayrım kasıtlıdır: “testler geçti” ifadesi yalnız yukarıdaki gerçekten çalıştırılan kontrolleri kapsar; APK üretildiği veya gerçek hesabın değiştirildiği iddia edilmez.
