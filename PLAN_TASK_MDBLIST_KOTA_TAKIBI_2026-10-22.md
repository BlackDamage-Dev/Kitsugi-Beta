# PLAN & TASK — MDBList API Kota Takibi ve "Kota Doldu" Uyarısı

**Tarih:** 2026-10-22
**Sürüm hedefi:** v2.4.341+
**Dal:** `arena/1e61735d-kitsugi-beta`
**İlgili çözüm raporu:** `COZUM_RAPORU_MDBLIST_KOTA_TAKIBI_2026-10-22.md`

---

## 1. İstek (kullanıcı)

1. MDBList API anahtarına özgü **kalan kota** bilinmeli.
2. API anahtarı girili ve özellik açıksa kota;
   - **Ayarlar → Harici Entegrasyonlar → MDBList** sekmesinde,
   - **Detay sayfasındaki** MDBList puan bloğunun bulunduğu yerde gösterilmeli.
3. Kota bitince **bittiğine dair uyarı** verilmeli.
4. Ücretli/ücretsiz hesap ayrımı yapılmamalı (limit hesaba göre okunmalı).

---

## 2. Kapsam

| # | İş | Durum |
|---|---|---|
| 1 | Kota modeli + merkez (`MdbListQuota`, `MdbListQuotaCenter`) | ✅ |
| 2 | `/user` uç noktasından limit okuma (`fetchQuota`) | ✅ |
| 3 | Her yanıttaki `X-RateLimit-*` başlıklarını kaydetme | ✅ |
| 4 | 429 → "kota bitti" işaretleme (anahtar geçersiz sayılmaz) | ✅ |
| 5 | Anahtara özgü kalıcı kayıt (SharedPreferences, parmak izi) | ✅ |
| 6 | UTC 00:00 sıfırlanma + rollover | ✅ |
| 7 | Ayarlar sekmesinde kota paneli (çubuk + yenile + plan rozeti) | ✅ |
| 8 | Detay kartında kota rozeti + uyarı satırı | ✅ |
| 9 | Tek seferlik "kota doldu" bilgi balonu | ✅ |
| 10 | Kota bitince ağa çıkmama, son bilinen puanları gösterme | ✅ |
| 11 | TR/EN metinler | ✅ |
| 12 | Birim testleri (başlık, gövde, rollover, plan) | ✅ |

**Kapsam dışı:** Puan araması hâlâ mevcut `mdblist.com/api/` uç noktasını kullanır; bu
davranış değiştirilmedi. 5 dakikalık okuma/yazma limiti (tier'den bağımsız) ayrı bir iş
olarak ele alınmadı.

---

## 3. Tasarım kararları

- **Limit asla sabit değil.** Ücretsiz 1.000; destekçi kademeleri 10.000 → 250.000.
  Bu yüzden `X-RateLimit-Limit` / `api_requests` okunur; hiçbir yerde sabit sayı yok.
- **Kota anahtara özgü.** Kayıt SHA-256 parmak iziyle etiketlenir; anahtar değişince eski
  sayı gösterilmez.
- **Tek kaynak.** Ayarlar paneli ve detay kartı aynı `StateFlow`'u dinler; iki ekran ayrı
  ayrı istek atmaz.
- **İstek bütçesi korunur.** `/user` doğrulaması mutex + `maxAgeMillis` ile kısıtlanır
  (detay: 10 dk, ayarlar: 5 dk). Puan yanıtları zaten başlıklarla kotayı günceller.
- **Bilinmeyen bilinmeyen kalır.** Başlık/gövde okunamazsa uydurma sayı gösterilmez;
  "Kota bilgisi alınamadı" + yenile düğmesi çizilir.
- **Bayat "0" sorunu.** Günlük sıfırlanma geçtiyse kalan hak yeniden limite çekilir.

---

## 4. Dosya değişiklikleri

### Yeni
- `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListQuota.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListQuotaTest.kt`

### Değişen
- `app/src/main/java/com/kitsugi/animelist/data/remote/MdbListClient.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIntegrationsSettingsDialog.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/kitsugi/animelist/data/remote/MdbListClientTest.kt`

---

## 5. Kabul kriterleri

- [x] Anahtar girili + özellik açıkken ayarlar sekmesinde `kalan / limit` + oran çubuğu görünür.
- [x] Aynı sayı detay sayfasındaki "Harici Puanlar" kartında da görünür.
- [x] Kota 0 olduğunda iki yerde de kırmızı "Kota doldu" uyarısı çıkar, yenilenme saati yazar.
- [x] Uyarı bir kez bilgi balonu olarak gösterilir, her detay sayfasında tekrarlamaz.
- [x] Ücretsiz hesapta 1.000, destekçi hesapta gerçek limit görünür (sabit varsayım yok).
- [x] Anahtar değiştirilince eski anahtarın kotası gösterilmez.
- [x] Kota bitince puan araması 429 üretmez; önbellekteki son puanlar gösterilir.
- [x] TR/EN metin paritesi bozulmaz (`check_localization_resource_parity.py`).
- [ ] Cihazda derleme + görsel doğrulama (bu ortamda Android SDK/JDK yok).

---

## 6. Test planı

**Birim (JVM, ağ yok):**
- `parseQuotaHeaders`: limit/kalan/reset ayrıştırma, bozuk değerde `null`.
- `normalizeEpochSeconds`: ms → sn.
- `parseUserQuota`: ücretli planda 25.000, ücretsiz planda 1.000 + tükenmiş kota, bozuk
  gövdede başlık değerlerinin korunması.
- `MdbListQuota.rolledOver`: sıfırlanma öncesi/sonrası, bilinmeyen değerlerin korunması.
- `nextDailyResetEpochSeconds`: her zaman gelecekte ve UTC gece yarısı.

**Elle (cihaz):**
1. Geçerli anahtar gir → sekmede kota görünmeli.
2. Kotası dolu bir anahtarla doğrula → "geçersiz anahtar" DEĞİL, kota uyarısı gelmeli.
3. Detay sayfası aç → kartta aynı sayı; kota 0 iken uyarı satırı.
4. Uygulamayı kapat/aç → son bilinen kota hemen görünmeli.
5. Saati ertesi güne al → kota yenilenmiş görünmeli.

---

## 7. Riskler

| Risk | Etki | Önlem |
|---|---|---|
| `/user` uç noktası şema değişikliği | Kota okunamaz | Gövde + başlık iki kaynak; ikisi de yoksa "bilinmiyor" |
| Eski uç nokta başlık göndermiyor | Sayı bayat | 10 dakikalık `/user` doğrulaması |
| Gereksiz istek artışı | Kota tüketimi | Mutex + kısa devre + kota bitince ağa çıkmama |
| Paylaşılan anahtar (başka uygulama) | Sayı beklenenden hızlı düşer | Başlıklar gerçek zamanlı; belgele |
