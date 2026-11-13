# İnternet Hız Analizi — Modem / Anakart / Kablo Gerçek Kapasiteleri

> Bu rapor, `internet_hiz_analiz` klasöründeki fotoğraftaki modem etiketi incelenerek ve
> üretici/operatör dokümanları taranarak hazırlandı. Tarih: 2026-11-13

---

## 1. Fotoğraftaki modemin marka / modeli

**Etiketten okunanlar (kesin bilgi):**

| Alan | Değer |
|---|---|
| Marka | **Huawei** |
| Model | **OptiXstar HG8245X6-10** (etiket: "Huawei OptiXstar HG8245X6-10 GPON Terminal") |
| Ürün kodu (PROD ID) | 2150085067LDM004422 |
| Seri no (SN) | 4857544360B15AA7 |
| WAN MAC | 44272CF8B579 |
| Arayüz IP | 192.168.1.1 |
| Kullanıcı / şifre | admin / superonline *(etiketin üstünde yazıyor)* |
| Wi-Fi adı (SSID) | SUPERONLINE_WiFi_5AA7 |
| WLAN Key | 7FTKH34EUR9V |
| Güç | +12 V, 1.5 A |
| Sınıf | GPON ONT, Class B+, Class 1 Laser |

Yani cihaz **fiber (GPON) terminali + router**: fiber kablo buna giriyor, evin interneti buradan dağıtılıyor.

---

## 2. En önemli soru: 1000 Mbps (1 Gbps) destekliyor mu?

# ✅ EVET — 1000 Mbps destekliyor.

Doğru cevap "kutuda 1000 Mbps yazıyor" değil; **iki ayrı sınır** var, ikisi de 1000 Mbps'i kaldırıyor:

| Katman | Kapasite | 1000 Mbps için sonuç |
|---|---|---|
| **LAN portları (4 adet RJ45)** | 10/100/**1000** Mbps (gigabit) | ✅ Port başına gerçekte **~940 Mbps** |
| **Wi-Fi 6 – 5 GHz** | 2402 Mbps (teorik, 2×2 MIMO) | ✅ Gerçekte **~600–950 Mbps** |
| Wi-Fi 6 – 2.4 GHz | 574 Mbps (teorik) | ⚠️ Gerçekte ~250–400 Mbps (yetersiz) |
| GPON fiber hattı | 2.488 Gbps ↓ / 1.244 Gbps ↑ | ✅ 1000 Mbps paket için fazlasıyla yeterli |
| Cihaz yönlendirme (NAT) | ~1 Gbps (gigabit sınıfı SoC) | ✅ Sınırda ama yeterli |

**Özet:**
- **Kablo ile:** 1000 Mbps paketi tam kapasiteye yakın (940–960 Mbps) alırsın.
- **Wi-Fi 6 / 5 GHz ile:** 600–950 Mbps arası normal; **1000 Mbps'in tamamı kablosuzda beklenmez.**
- **2.4 GHz ile:** 1000 Mbps kullanılamaz, ~300 Mbps civarında kalırsın.
- **1000 Mbps üstü (2 Gbps / 5 Gbps) paketler:** bu cihazla **kablo üzerinden de** alınamaz, çünkü portlar gigabit. 2 Gbps+ için 2.5GE portlu bir ONT gerekir (örn. HN8145XR, EN8245X6s-8N, V864).

> Not: Etiket "GPON Terminal" diyor. Aynı ailede **DN8245X6-10** adlı bir VDSL/bakır modeli daha var — karıştırılmamalı. Seninki fiber model, yani **HG8245X6-10** (başında D yok).

---

## 3. Anakart / Ethernet kartı tarafı (PC)

Anakartın kendisi hız belirlemez; belirleyen **üzerindeki ethernet yongası** ve **link anlaşması**dır. Araç bunu otomatik çıkarır:

- **Kartın desteklediği maksimum:** sürücünün hız seçeneklerinden okunur → 100 Mbps / 1 Gbps / 2.5 Gbps / 5 Gbps / 10 Gbps
- **Gerçek link hızı:** `Get-NetAdapter` → örn. `1 Gbps`. **Bu değer 100 Mbps ise sorun kablo/port/modem ayarındadır.**
- 2020 sonrası çoğu laptop/desktop kartı **1 Gbps (gigabit)**; yeni anakartlarda sıkça **2.5 Gbps (Realtek RTL8125 / Intel I225-I226)**.

**Kritik nokta:** Anakart 2.5 Gbps olsa bile modem portu gigabit olduğu için **ağ hızı 1 Gbps'te kalır** — anakart fazlası boşa gider, zarar vermez.

---

## 4. Kablo tarafı (en sık darboğaz burada çıkar)

| Kablo | Teorik | Pratikte |
|---|---|---|
| Cat5 (eski) | 100 Mbps | 100 Mbps |
| **Cat5e** | 1 Gbps | 1 Gbps'e kadar (kısa mesafede 2.5G) |
| **Cat6** | 1 Gbps (55 m'de 10G) | 1 Gbps rahat |
| Cat6a / Cat7 / Cat8 | 10 Gbps+ | 10 Gbps'e kadar |

**1000 Mbps için şart: kablonun 8 telinin tamamı sağlam olmalı.**
Bir tel kopuk/ezik ise kart kendini **100 Mbps'e düşürür** — bu, "1000 Mbps paket aldım ama 100 geliyor" şikayetinin 1 numaralı sebebidir.

Kontrol listesi:
- [ ] Duvar prizi / ek yeri / uzatma kullanılıyorsa kaldır
- [ ] Kabloyu **LAN2, LAN3, LAN4** portlarında da dene
- [ ] Kabloyu yenile (Cat6 önerilir, ucuz ve 1 Gbps garanti)
- [ ] Modem arayüzünde (192.168.1.1) **LAN → port hızı 1000 Mbps** görünüyor mu bak
- [ ] Modemde QoS / hız limiti / ebeveyn kontrolü kapalı olsun

---

## 5. Araç neyi gösteriyor? (`internet_hiz_analiz.bat`)

Klasördeki eski `.ps1` iki ciddi hata içeriyordu:
1. **LF satır sonları + BOM yok + kod sayfası ayarı yok** → Türkçe karakterler bozuk, betik bazı sistemlerde hiç çalışmıyordu.
2. **Modem modeli/hızları hiç gösterilmiyordu**, sadece anlık link hızı yazıyordu.

Yeni sürüm (v2.0) şunları yapıyor:

| Bölüm | İçerik |
|---|---|
| 1 | Anakart üreticisi/modeli, işlemci, RAM |
| 2 | Her ethernet kartının **desteklediği MAX** + **gerçek link hızı** + Wi-Fi link hızı |
| 3 | **Kablo sınıfı** (Cat5/Cat5e/Cat6…), kablo uzunluğu tahmini, paket hatası sayacı, modem arayüzüne erişim testi |
| 4 | **Modem modeli tespiti** (PnP + modem web arayüzü + etiketten yazma) ve modelin **LAN / Wi-Fi / PON son hızları** |
| 5 | **Zincir (darboğaz) tablosu:** ISP paketi → modem portu → anakart kartı → kablo → en zayıf halka kırmızı |
| 6 | İsteğe bağlı **10 saniyelik gerçek hız ölçümü** |
| 7 | **Özet** + masaüstüne `internet_hiz_raporu_*.txt` kaydı |

Gömülü modem veritabanı: HG8245X6-10, HG8245X6, HG8245H, HG8245, EG8145X6, HG8145X6, HN8145XR, V864, DN8245X6-10, HG658 V2, TP-Link VR600 v3.

### Nasıl çalıştırılır
1. Klasördeki **`internet_hiz_analiz.bat`** dosyasına çift tıkla (yönetici izni gerekmez).
2. Modem modelini sorarsa etiketteki modeli yaz: `HG8245X6-10`
3. ISP paket hızını sorarsa yaz: `1000` (veya `1 Gbps`)
4. Sonuç ekranda + masaüstünde rapor olarak.

### 1000 Mbps paket için beklenen sonuç tablosu

| Bağlantı | Beklenen gerçek hız | Durum |
|---|---|---|
| Kablo (Cat5e/Cat6 + gigabit port) | 900–960 Mbps | ✅ Tam performans |
| Wi-Fi 6, 5 GHz, yakın mesafe | 600–950 Mbps | ✅ İyi |
| Wi-Fi 6, 2.4 GHz | 250–400 Mbps | ⚠️ Beklenen sınır |
| Kablo ama link 100 Mbps'e düşmüş | ~94 Mbps | ❌ Kablo/port sorunu |

---

## 6. Kaynaklar
- Huawei OptiXstar HG8245X6-10 — Wi-Fi Alliance sertifikası (Wi-Fi 6, 2.4/5 GHz, EasyMesh, WPA3): WFA113896
- Huawei ONT ürün datasheet'i (HG8245X6 ailesi: 4×GE + 2×POTS + 1×USB + 2.4G/5G Wi-Fi 6)
- Netia (operatör) HG8245X6-10 bağlantı kılavuzu: 2.4 GHz 574 Mbps / 5 GHz 2402 Mbps Wi-Fi 6
- Technopat / ShiftDelete kullanıcı ölçümleri: 4 LAN portunun tamamı gigabit, gigabit link ile ~940 Mbps
- GPON standart hızları: ITU-T G.984 → 2.488 Gbps ↓ / 1.244 Gbps ↑

---

## 7. Senin çıktının yorumu (2026-11-13 15:20 testi)

Gönderdiğin ekran çıktısı şunu söylüyor:

| Bulgu | Değer | Yorum |
|---|---|---|
| PC IP | 192.168.1.101 | DHCP çalışıyor, modemle iletişim sağlıklı |
| Kablo linki | **1 Gbps (1000 Mbps)** | ✅ **Kablo + port + PC kartı gigabit** |
| Ping (modem) | 0 ms | ✅ LAN tarafı kusursuz |
| Modem arayüzü | Erişildi (80/443) | ✅ Port ve kablo sağlam |
| Modem MAC | FC-9F-2A-90-CB-08 | Üretici: **Zyxel** (Huawei değil — ikinci cihaz) |
| ISP paketi | 100 Mbps | Paket küçük; 1 Gbps hattın boşa gidiyor |

**Sonuç: donanımda hiçbir sorun yok.** Kablo 1 Gbps anlaşmış, yani ev içi ağın
1000 Mbps'e hazır. 100 Mbps paket alıyorsan normalde **85–94 Mbps** görmen gerekir.
Bu rakamın altında kalıyorsan sebep donanım değil, paket/hat tarafıdır.

### Çıktıdaki 2 yazılım hatası (v2.1'de düzeltildi)

1. **"Kart max 100 Mbps" yanlış okundu.** Türkçe Windows'ta sürücü seçenekleri
   `"1,0 Gbps Tam Çift Yönlü"` biçiminde geliyor; eski kod `1 Gbps/1G` kalıbını
   aradığı için bulamayıp yedek tahmine (100 Mbps) düşüyordu. Aynı çıktıda kablo
   1 Gbps göründüğü hâlde kart 100 Mbps yazması bu çelişkinin kanıtı.
   **v2.1:** virgüllü ondalık + Gbps/Mbps metinleri + sürücü indeksleri + gerçek
   link hızı birlikte değerlendiriliyor.
2. **"Hatalı paket 160" uyarısı abartılıydı.** Sayaçlar bilgisayar açıldığından beri
   birikimlidir; 41 milyon pakette 160 hata = **%0,0004**, yani önemsiz. v2.1 artık
   yüzdeyi hesaplayıp %0,01 altını "normal" sayıyor, sadece gerçek bozulmayı uyarıyor.
3. **Zincir tablosu düzeltildi:** ISP paketi artık tabloda "darboğaz" olarak
   işaretlenmiyor; sadece modem portu / anakart kartı / kablo arasında darboğaz
   aranıyor. Ayrıca "bu paketle 1 Gbps alınır mı" karşılaştırma satırı eklendi.

### MAC FC:9F:2A → Zyxel
Etiketteki cihaz Huawei HG8245X6-10 (fiber ONT). Ağ geçidi MAC'i Zyxel'e ait
olduğuna göre hatta **ikinci bir Zyxel cihaz** var (router/access point).
Hangisinin LAN portu gigabit, aşağıdaki tabloya göre belirlenir:

| Zyxel model | LAN | 100 Mbps | 1000 Mbps |
|---|---|---|---|
| VMG3312-B10B | 4 × 100 Mbps | ✅ | ❌ |
| VMG1312-B10D | 4 × 100 Mbps | ✅ | ❌ |
| VMG3625-T50B | 4 × Gigabit | ✅ | ✅ |
| VMG8623-T50B | 4 × Gigabit | ✅ | ✅ |
| DX3301-T0 / DX5401-B0 | 4 × Gigabit + Wi-Fi 6 | ✅ | ✅ |

Modeli etiketten okuyup araca yazarsan (örn. `VMG8623-T50B`) LAN/Wi-Fi son hızlarını
ve darboğaz kararını kendisi verir.

### Senin durumunda yapılacak
- Kablo 1 Gbps anlaşıyor → **kablo/port değiştirmeye gerek yok**.
- 100 Mbps paket ile 85–94 Mbps beklenir; bunun altındaysan ISS'ye başvur.
- 1000 Mbps pakete geçersen: modem tarafında **Huawei HG8245X6-10 zaten uygun**,
  arada Zyxel varsa **gigabit LAN'lı model** olması yeterli (yukarıdaki tablo).
- Wi-Fi 6 5 GHz'de 600–950 Mbps görülür; 2.4 GHz'de 300 Mbps civarı normaldir.
