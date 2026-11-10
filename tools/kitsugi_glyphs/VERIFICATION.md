# Kitsugi logo/simge doğrulaması

## Sonuç

Uygulamanın sahip olduğu Kotlin/Compose etkileşimlerini kapsayan genişletilmiş kaynak
kod taramasında **2.523 kayıt incelendi; çözümlenmemiş kayıt kalmadı**.
Bu sayı 2.523 farklı düğme veya 2.523 farklı logo anlamına gelmez.

Set **195 özgün vektör simge** içerir. Aynı mantıksal işlem aynı simgeyi paylaşır.
Kaynak/platform logoları korunmuştur; `KitsugiPlatformLogo.kt` ve
`KitsugiSourceBadge.kt` dosyaları başlangıç sürümüyle aynıdır.

## Bu doğrulamada kapatılan eksikler

- Özel filtre ve kategori çipleri, seçim ve açılır-kapanır kontroller.
- TV menüleri ve klavye/DPAD ile kullanılan eklenti kurma/silme düğmeleri.
- Tıklanabilir kullanıcı adları, medya/kişi satırları ve bağlantılı bilgi değerleri.
- Poster, profil resmi, galeri ve Markdown görsel açma alanları.
- Başlığa çift dokunarak kopyalama alanları ve satır içi Wiki bağlantıları.
- Basit ve özel metin girişleri, sayısal girişler ve zaman çizgisi işaretleri.
- Dolu/boş yıldız, abonelik durumu ve açılmış/kapalı yanıt simgeleri.

## Kapsam dökümü

| Sınıf | Kayıt |
|---|---:|
| Kontrol içinde açık Kitsugi glyph referansı | 1.724 |
| Görseli ortak bileşeninde doğrulanmış çağrı | 564 |
| Görsel/state kontrolü veya korunmuş kaynak işareti | 168 |
| Çağıranın verdiği ikon/içerik yuvası | 23 |
| İşleve özel çizilmiş sayfa/harf seçim işareti | 2 |
| Görünür bir kontrolden ayrı düğme olmayan kapatma/hareket katmanı | 9 |
| Kaynak puanı veri kurucusu; kendisi UI değil | 7 |
| Bitişik simge, satır içi bağlantı veya modifier sahibinde doğrulanmış simge | 20 |
| Paylaşılan giriş/fokus/hareket yönlendirme yardımcıları | 6 |
| **Çözümlenmemiş** | **0** |

Her kaydın dosyası, satırı, işlevi ve dayanağı `interaction_coverage.json` içindedir.
Görünmez kapatma perdesine veya oynatıcı hareket katmanına bağımsız yeni düğmeler
konulmadı. Bunlar aynı işlemin görünür kontrolünden ayrı logo gerektiren yeni bir
arayüz öğesi olarak sayılmadı.

## Kontroller

- **16 otomatik kontrol**: vektör referansları, üretici tutarlılığı, kaynak istisnaları,
  stok Material ikonlarının kalmaması, açık/kapanmış durum ayrımı, geniş etkileşim
  envanteri, resmin logo sanılmaması, zorunlu ikon parametreleri ve Kotlin sözdizimi.
- Değişen ve yeni Kotlin dosyalarında başlangıç sürümüne göre ek sözdizimi hatası yok.
- `git diff --check`: temiz.
- Aynı kaynak geometriden üretilen SVG/PNG ikon sayfası yenilendi.

## Ortam sınırı

Bu ortamda **Java ve Android SDK bulunmuyor**; APK derlemesi veya cihaz üzerinde
çalıştırma yapılmadı. Dolayısıyla bu rapor kaynak kod doğrulamasıdır; Android
çalışma zamanı, tüm ekran boyutları, TalkBack, TV fokus ve RTL görsel testi geçtiği
iddiası değildir. Harici WebView sayfalarının kendi HTML arayüzleri değiştirilmedi.
