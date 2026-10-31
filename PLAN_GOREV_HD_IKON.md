# PLAN / GÖREV DOSYASI — Uygulama Geneli HD İkon Geçişi

Tarih: 2026-10-31 · Dal: `arena/10430a8e-kitsugi-beta`

## Kullanıcı isteği (birebir)
- Üst çubuk aksiyon butonları **her ekranda aynı konumda ve aynı sırada**, bildirim **her zaman en sağda**.
- Tümü **tek görünüm** (boyut/şekil/boşluk aynı) ve **arka plansız, şeffaf, HD** gerçek görseller.
- Kapsam **uygulama geneli**: her buton ve tıklanır alan — açılır-kapanır kontroller, Ayarlar sayfası ve içindekiler dahil. Atlanan hiçbir şey olmayacak.
- İkonlar bulunduğu alanın anlamına **uygun** olacak; aynı eylem **asla** farklı ikonla, farklı eylem aynı yanıltıcı ikonla gösterilmeyecek.
- **Hiçbir ikonda arka plan olmayacak** (bu yüzden dairesel `SettingsIcon` plakası kaldırıldı).
- Sonuç **kanıtla** sunulacak: iddia değil, çalıştırılmış kontroller.

## Görev listesi ve durumu

| # | Görev | Durum |
|---|---|---|
| 1 | Ortak üst çubuk aksiyon seti + kanonik sıra (NOTIFICATIONS = 100, en sağ) | ✅ |
| 2 | HD vektör ikon seti (Lucide, ISC) — 266 taban / 522 özellik | ✅ |
| 3 | `KitsugiIcons as Icons` göçü (251 dosya) + Material ikon importlarının kaldırılması | ✅ |
| 4 | Animasyonlu `KitsugiChevronIcon` (0° ↔ 180°, spring) — tüm açılır-kapanır alanlar | ✅ |
| 5 | `KitsugiIconButton` / `KitsugiStateIcon` tek birim | ✅ |
| 6 | Emoji → HD ikon eşlemesi (`KitsugiEmojiIcons`, 135 anahtar) | ✅ |
| 7 | `KitsugiEmojiLabel` — emoji önekli etiketleri ikon + metne çeviren tek birim | ✅ |
| 8 | Aile birleştirme: filtre / settings / history / refresh / notify… tek ikon | ✅ |
| 9 | Skor-yıldız, durum rozetleri, galeri kategorileri, plugin türleri → HD ikon | ✅ |
| 10 | QA: çözülmemiş referans, import, parametre, string, denge kontrolleri | ✅ |
| 11 | Derleme doğrulaması (`./gradlew assembleDebug`) | ⛔ ortamda JDK/Gradle/Android SDK yok |

## Kapsam dışı bırakılanlar (bilinçli)
- Ülke **bayrakları**: veri; bilinmeyen dilde `Icons.Globe` gösterilir.
- Cloudstream tanılama **markdown raporu** içindeki `⚠️ / 📦` işaretleri: indirilen .md dosyasının içeriği, arayüz ikonu değil.
- Kullanıcının kendi seçtiği koleksiyon klasörü emojileri: kullanıcı verisi.

## Yeniden üretim komutları
```
python3 /tmp/gen_icons.py      # KitsugiIcons.kt üret
python3 /tmp/unify.py          # aile birleştirmelerini yeniden uygula (ZORUNLU)
python3 /tmp/migrate_icons.py  # Material → KitsugiIcons göçü
python3 /tmp/chevron.py        # chevron dönüşümü
node /tmp/r/render3.js         # ikon önizleme sayfası
```

## Kanonik üst çubuk sırası
FILTER 10 → SEARCH 15 → REFRESH 20 → HISTORY 30 → DOWNLOADS 40 → EXTENSIONS 50 →
STATS 60 → SHARE 70 → FAVORITE 75 → GALLERY 80 → FOLDER 85 → DIAGNOSTICS 88 →
RANDOM 90 → CLEAR 95 → **NOTIFICATIONS 100 (en sağ)**

## Görsel ölçü
Buton 40dp / ikon 20dp / boşluk 8dp / köşe 14dp · `Compact` varyantı 34dp ·
vurgu gradyanı α0.14 (seçili α0.28) · kenarlık 1dp α0.30 (seçili α0.65) · basma ölçeği 0.90
