# Kitsugi — Özgün logo/simge planı ve task özeti

Tarih: 4 Kasım 2026
Dal: `arena/1afceef0-kitsugi-beta`
Başlangıç commit: `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`

## İstek ve kapsam

Kaynak/platform logoları korunacak; uygulamaya ait diğer düğmeler, sekmeler,
menüler, açılır-kapanır kontroller ve özel tıklanabilir alanlar yaptıkları işe uygun
Kitsugi simgeleriyle işaretlenecek. Aynı mantıksal işlem aynı simgeyi paylaşabilir;
her düğme örneği için birbirinden farklı bir logo üretilmiş değildir.

## Tamamlanan işler

- [x] 24×24, yuvarlatılmış 1.75 birim çizgili özgün vektör sistemini oluştur.
- [x] 195 simgeyi `KitsugiGlyphs.kt` olarak üret; SVG/PNG önizlemelerini yenile.
- [x] Uygulama simgelerini taşı; kaynak logolarını kapsam dışında tut.
- [x] Ana sekmeler, ayarlar, menüler, filtre çipleri ve metin düğmelerini tara.
- [x] Özel telefon ve TV kartlarını, DPAD/klavye kontrollerini tara.
- [x] Giriş alanları, sayısal kontroller ve oynatıcı işaretlerini tamamla.
- [x] Poster/galeri/avatar açma, başlık kopyalama ve satır içi bağlantıları işaretle.
- [x] Dolu/boş yıldız, abonelik ve açık/kapalı yanıt durumlarını ayır.
- [x] Görünmez kapatma perdeleri, hareket katmanları ve ortak yardımcıları ayrı incele.
- [x] 2.523 etkileşim kaydını dayanaklarıyla envantere al; çözümlenmemiş kaydı sıfırla.
- [x] 16 otomatik kontrolü çalıştır; tamamı başarılı.
- [x] Değişen/yeni Kotlin dosyalarında ek sözdizimi hatası olmadığını kontrol et.
- [x] `git diff --check` kontrolünü geçir.
- [x] `KitsugiPlatformLogo.kt` ve `KitsugiSourceBadge.kt` dosyalarının başlangıçla
  bayt düzeyinde aynı kaldığını doğrula.

## Ortam nedeniyle yapılmayan doğrulamalar

- [ ] Android APK derlemesi: çalışma ortamında Java ve Android SDK yok.
- [ ] Gerçek cihaz/emülatörde küçük ekran, RTL, TalkBack ve TV fokus görsel testi.

Bunlar simge envanterindeki açık kayıtlar değil, henüz yapılmamış derleme/çalışma
zamanı doğrulamalarıdır. Kaynak kod denetimi, cihaz testi yapılmış anlamına gelmez.
Harici WebView sayfalarının kendi HTML arayüzleri bu Compose taramasında değiştirilmedi.

## Paket içeriği ve uygulama

ZIP, bütün projenin yerine yalnızca bu çalışmada değişen/yeni dosyaların son hallerini
orijinal depo yollarıyla taşır. `.git`, bağımlılıklar, önbellekler ve kimlik bilgileri
pakete alınmaz. Silinen bir dosya varsa paket manifestinde listelenir.

1. Hedef deponun yedeğini alın; başlangıç commit/dal uyumunu kontrol edin.
2. `Kitsugi-Beta/` altındaki dosyaları hedef depo köküne aynı yollarla uygulayın.
3. Paket manifestindeki silme kayıtları varsa ayrıca değerlendirin.
4. Aşağıdaki doğrulama adımlarını uygulayın; Android ortamında APK derleyin.

```sh
python3 -m venv /tmp/kitsugi-glyphs-venv
/tmp/kitsugi-glyphs-venv/bin/pip install -r tools/kitsugi_glyphs/requirements-dev.txt
/tmp/kitsugi-glyphs-venv/bin/python -m unittest discover -s tools/kitsugi_glyphs/tests -v
/tmp/kitsugi-glyphs-venv/bin/python tools/kitsugi_glyphs/audit_interactions.py --check
```

## Ayrıntılı belgeler

- `tools/kitsugi_glyphs/VERIFICATION.md`: sonuç ve kapsam dökümü.
- `tools/kitsugi_glyphs/interaction_coverage.json`: 2.523 kaydın dosya/işlev/dayanak listesi.
- `tools/kitsugi_glyphs/README.md`: üretici, taşıma araçları ve doğrulama kullanımı.
- `gorseller/kitsugi_ikon_seti.png`: ikon seti önizlemesi.
