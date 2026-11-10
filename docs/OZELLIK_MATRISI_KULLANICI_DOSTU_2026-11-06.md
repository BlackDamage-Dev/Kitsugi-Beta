# 🎛️ KULLANICI DOSTU ÖZELLİK MATRİSİ
## Açık Kaynak Ambiyans Uygulamaları Ne Sunuyor — Bizde Ne Var, Ne Eksik

**Tarih:** 2026-11-06
**Amaç:** "Hiçbir buton eksik kalmasın" — rakip uygulamaların **tüm** kullanıcı özelliklerini çıkarıp bizimle karşılaştırmak

---

## 1. İNCELENEN UYGULAMALAR

| Uygulama | Lisans | Platform | Yıldız | Özellik sayısı |
|---|---|---|---|---|
| **[Chiarella](https://github.com/carellice/chiarella)** | MIT | Android + Windows + Web | — (30 commit, bugün güncellendi) | ⭐⭐⭐⭐⭐ 24 özellik |
| **[Metiq](https://github.com/metiq-xyz/android-app)** | GPL-3 | Android (F-Droid + Play) | **250** | ⭐⭐⭐⭐ 16 özellik |
| **[MixNotice](https://github.com/small-rose/MixNotice)** | MIT | Android + Windows | — | ⭐⭐⭐ 11 özellik |
| **[Rainfold](https://github.com/butul0ve/rainfold)** | — | Android | — | ⭐⭐ 8 özellik |
| **[XMSLEEP](https://github.com/Tosencen/XMSLEEP)** | MIT | Android | — | ⭐ 5 özellik |

---

## 2. TAM ÖZELLİK MATRİSİ

### 🎵 Ses & Çalma

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 1 | Çoklu ses katmanı (mix) | 12 eşzamanlı | 6+ | 6 kanal | 2 kanal | 🟡 **Az** |
| 2 | Ses başına bağımsız ses seviyesi | ✅ | ✅ | ✅ | ⚠️ Sadece 2 kanal | 🟡 |
| 3 | **Genel ses seviyesi** (tek buton, per-ses bozmadan) | ✅ | ✅ | ✅ | ✅ | ✅ |
| 4 | **Ses başına sessize alma (mute)** | — | ✅ | ✅ | ❌ | 🔴 |
| 5 | Play / Pause | ✅ | ✅ | ✅ | ✅ | ✅ |
| 6 | **"Tümünü Durdur"** | ✅ | ✅ | ✅ | ✅ | ✅ |
| 7 | Yumuşak fade in/out | ✅ | ✅ | ✅ | ✅ | ✅ |
| 8 | Seamless loop (dikişsiz) | ✅ 2 player handoff | ✅ | ✅ 1sn crossfade | ✅ sentez doğal | ✅ |
| 9 | **Limiter / clip önleme** | — | ✅ | ✅ | ❌ | 🔴 |
| 10 | **Ses seviyesi normalizasyonu (LUFS)** | — | ✅ −22 LUFS | ✅ loudnorm | ✅ sentez sabit | ✅ (sentez) |

### ⏱️ Zamanlayıcı

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 11 | **Uyku zamanlayıcısı** | ✅ 15dk–2sa + özel 5dk–4sa | ✅ saat/dk/sn + hazır | ✅ 5–480 dk | ❌ | 🔴 **YOK** |
| 12 | **Timer bitişinde fade-out** | ✅ 12 saniye | ✅ | ✅ son 5 dk | ❌ | 🔴 |
| 13 | **Timer geri sayım göstergesi** | ✅ | ✅ | ✅ | ❌ | 🔴 |
| 14 | **Tersten çalışan mod** (rest/sabah uyandırma) | — | — | ✅ "dinlenme modu" | ❌ | 🟡 |
| 15 | **Duraklatınca timer dursun** | ✅ | — | ✅ (sistem saati) | ❌ | 🟡 |

### 💾 Profil / Kayıt

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 16 | **Kendi mix'ini kaydet (preset)** | ✅ | ✅ | ✅ 8 hazır | ⚠️ 12 hazır var, kullanıcı kaydı YOK | 🟡 |
| 17 | Kaydedilen mix'i sil | ✅ uzun bas | ✅ | — | ❌ | 🔴 |
| 18 | **Son mix'i otomatik geri yükle** | ✅ | ✅ | ✅ | ❌ | 🔴 |
| 19 | **Mix dışa/içe aktar (JSON)** | — | ✅ | ✅ | ❌ | 🟡 |
| 20 | Çalan profile tekrar bas → duraklat | ✅ | — | — | ❌ | 🟡 |

### 🔔 Arka Plan

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 21 | **Foreground service** | ✅ Live Update | ✅ | ✅ | ❌ | 🔴 **KRİTİK** |
| 22 | **Bildirim kontrolleri** | ✅ canlı güncellenen | ✅ | ✅ | ❌ | 🔴 |
| 23 | **Kilit ekranı kontrolleri** | ✅ | ✅ | ✅ | ❌ | 🔴 |
| 24 | **Bluetooth kulaklık tuşları** | ✅ | ✅ | ✅ | ❌ | 🔴 |
| 25 | **Kulaklık çıkarınca duraklat** | ✅ audio focus | ✅ | ✅ | ⚠️ ExoPlayer'da var, sentezde yok | 🟡 |
| 26 | Ekran kapanınca devam | ✅ | ✅ | ✅ | ❌ | 🔴 |
| 27 | Pil dostu | ✅ | ✅ AudioTrack | ✅ | ✅ AudioTrack | ✅ |

### 📱 Widget / Kısayol

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 28 | **Ana ekran widget'ı** | ✅ Glance | — | — | ❌ | 🟡 |
| 29 | **Hızlı ayar karosu (Quick Setting tile)** | — | — | — | ❌ (Rainfold 2'de var) | 🟡 |
| 30 | Son mix'i tek dokunuşla başlat | ✅ | — | — | ❌ | 🟡 |

### 🎨 Kişiselleştirme

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 31 | Tema seçimi | ✅ 4 tema | ✅ Material You? | ✅ 4 tema + özel vurgu | ✅ tema var ama müziğe özel yok | 🟡 |
| 32 | **Animasyonları azalt (erişilebilirlik)** | ✅ | ✅ | — | ❌ | 🟡 |
| 33 | Haptik geri bildirim | ✅ | — | — | ❌ | 🟡 |
| 34 | **Çoklu dil** | ✅ IT/EN | ✅ 5 dil | ✅ ZH/EN | ✅ TR/EN | ✅ |
| 35 | Ses ikonları/renkleri | ✅ | ✅ | ✅ | ⚠️ Tek tip ikon | 🟡 |

### 📖 Ses Keşfi & Bilgi

| # | Özellik | Chiarella | Metiq | MixNotice | Biz | Durum |
|---|---|---|---|---|---|---|
| 36 | **Ses detay kartı** (uzun bas) | ✅ açıklama + ideal anlar + uyumlu sesler | — | — | ❌ | 🟡 |
| 37 | **"Bununla iyi gider" önerisi** | ✅ | — | — | ⚠️ Preset'lerde var, ses bazında yok | 🟡 |
| 38 | Ses arama | — | — | — | ❌ (kutu var, boş) | 🔴 |
| 39 | **Favoriler / yer imleri** | — | — | — | ❌ | 🟡 |
| 40 | **Son çalınanlar geçmişi** | — | — | — | ❌ | 🟡 |

### 🎯 Bizim Özgün Olanlarımız

| # | Özellik | Chiarella | Metiq | MixNotice | Biz |
|---|---|---|---|---|---|
| 41 | **Tür bazlı otomatik öneri** | ❌ | ❌ | ❌ | ✅ **SADECE BİZDE** |
| 42 | **Atmosfer preset (katmanlı)** | ⚠️ profiller var | ✅ hazır presetler | ✅ 8 hazır | ✅ 12 |
| 43 | **BYOK çoklu API kaynağı** | ❌ | ❌ | ❌ | ✅ **SADECE BİZDE** |
| 44 | **Prosedürel ses sentezi** | ❌ (dosya) | ❌ (dosya) | ❌ (dosya) | ✅ **SADECE BİZDE** |
| 45 | **5 boyutlu duygu vektörü** | ❌ | ❌ | ❌ | ✅ **SADECE BİZDE** |

### 🧠 Ekstra (Chiarella'da var, çoğunda yok)

| # | Özellik | Açıklama | Bizde olmalı mı? |
|---|---|---|---|
| 46 | **Rehberli nefes egzersizi** | İnspirasyon/ekspirasyon sesleri, görsel animasyon | 🟡 Manga okumada anlamlı olabilir ("nefes al" molası) |
| 47 | ❌ **AI sohbet** (Cloudflare Workers AI) | Ses önerisi için sohbet | 🔴 Gereksiz, kota sorunu |
| 48 | **"Un momento per te"** | Kategori seç (film, oyun, kitap…) → kişisel öneri | 🟢 **Uyarlanabilir**: okuduğun manga türü zaten bu |
| 49 | **Sistem ses seviyesi uyarısı** | "Telefonun sesi kısık, %100'e çek?" | 🟢 **İyi fikir** |
| 50 | **Uygulama içi güncelleme** | GitHub release'ten APK indir | ⚪ Zaten var (repo'da updater var mı bakarız) |
| 51 | **Binaural beats** | Delta–Gamma beyin dalgası tonları | 🟡 İlginç, sonra |
| 52 | Çok platform (Win/Web) | — | ⚪ İlgisiz |

---

## 3. ÖZET SKOR

| Kategori | Toplam | Bizde var | Eksik | Tamamlanma |
|---|---|---|---|---|
| Ses & Çalma | 10 | 7 | 3 | %70 |
| Zamanlayıcı | 5 | 0 | 5 | **%0** 🔴 |
| Profil / Kayıt | 5 | 1 | 4 | %20 🔴 |
| Arka Plan | 7 | 2 | 5 | %29 🔴 |
| Widget | 3 | 0 | 3 | %0 🟡 |
| Kişiselleştirme | 5 | 2 | 3 | %40 |
| Keşif & Bilgi | 5 | 1 | 4 | %20 |
| **TOPLAM** | **40** | **13** | **27** | **%32** |

**Bizim özgün özelliklerimiz (rakiplerde yok):** 5 adet ⭐

---

## 4. EKSİK BUTONLARIN TAM LİSTESİ

Senin istediğin "eksik buton kalmasın" için **27 eksik**, öncelik sırasıyla:

### 🔴 P0 — Kritik (bunlar olmadan "yarım" hisseder)

| # | Buton / Özellik | Nerede olacak |
|---|---|---|
| A | **Uzak katalog paneli** — kaynak bazlı sonuç listesi | Panel → Çevrimiçi sekmesi |
| B | **Atıf/lisans gösterimi** (ⓘ ikonu) | Her uzak sesin altında |
| C | **Kaynak durum noktası** (🟢🟡🔴⚪) | Her kaynak satırında |
| D | **Arama kutusu** (çalışan) | Panel üstü |
| E | **Uyku zamanlayıcısı** | Panel üstü, saat ikonu |
| F | **Timer geri sayım göstergesi** | Mini player'da |
| G | **Tümünü durdur** | ✅ Zaten var |

### 🟠 P1 — Önemli (kullanıcı arar)

| # | Buton / Özellik |
|---|---|
| H | **Kendi mix'ini kaydet** (+ isim ver) |
| I | **Kaydedilen mix'i sil** |
| J | **Son mix'i otomatik geri yükle** (ayar) |
| K | **Ses başına mute** (küçük hoparlör ikonu) |
| L | **Ses detay kartı** (uzun bas → açıklama + uyumlu sesler) |
| M | **Favoriler** (kalp ikonu) |
| N | **Son çalınanlar** |
| O | **Foreground service** (ekran kapanınca devam) |
| P | **Bildirim kontrolleri** |
| Q | **Kulaklık çıkarınca duraklat** (sentez tarafı da) |
| R | **Limiter** (kırpılma önleme) |
| S | **Kota göstergesi** (kaynak başına kalan istek) |

### 🟡 P2 — Güzel olur

| # | Buton / Özellik |
|---|---|
| T | Widget (1×1: son mix'i başlat) |
| U | Hızlı ayar karosu |
| V | Mix dışa/içe aktar (JSON) |
| W | Sistem ses seviyesi uyarısı |
| X | Animasyonları azalt (erişilebilirlik) |
| Y | Haptik geri bildirim |
| Z | Ses ikonu/renk çeşitliliği |
| AA | TV uzaktan kumanda odağı |
| AB | "Bununla iyi gider" önerisi (ses bazında) |
| AC | Tersten timer (sabah uyandırma) |
| AD | Binaural beats |
| AE | Rehberli nefes molası |

---

## 5. TASARIM: TAM BUTON HARİTASI

Panelin **son hâli** şöyle olmalı:

```
┌────────────────────────────────────────────────────────┐
│  🎵  Ortam Sesi     ⏱️ 24:15    ⏸ ⏹          ✕        │  ← timer + kontroller
├────────────────────────────────────────────────────────┤
│  [Senin İçin] [Ambiyans] [Müzik] [🌐 Çevrimiçi] [Karışım] │
├────────────────────────────────────────────────────────┤
│  🔍  Ses ara…                          ⚙️ Filtre       │
├────────────────────────────────────────────────────────┤
│  ⏱️  Uyku zamanlayıcısı                                │
│  [Kapalı] [15dk] [30dk] [1sa] [2sa] [Özel…]           │
├────────────────────────────────────────────────────────┤
│  💾 Kaydedilen Mixlerim                    + Kaydet    │
│  ┌──────┐ ┌──────┐ ┌──────┐                           │
│  │ 🌧️   │ │ 🔥   │ │ ☕   │  ← uzun bas → sil        │
│  │Yağmur│ │Ateş  │ │Kafe  │                           │
│  └──────┘ └──────┘ └──────┘                           │
├────────────────────────────────────────────────────────┤
│  ATMOSFER PRESETLERİ                                   │
│  ┌──────┐ ┌──────┐ ┌──────┐                           │
│  │ 🌧️   │ │ ⚔️   │ │ 👻   │                           │
│  │Romant│ │Antren│ │Korku │                           │
│  └──────┘ └──────┘ └──────┘                           │
├────────────────────────────────────────────────────────┤
│  👁️  Açık kanallar (2/6)                               │
│  ┌────────────────────────────────────────────────┐   │
│  │ 🔊 Yağmur Hafif        ▁▁▁▁▁●▁▁  70%  🔇  ❤️  │   │
│  │ 🔊 Ambiyans Piyano     ▁▁▁●▁▁▁▁  35%  🔇  ❤️  │   │
│  └────────────────────────────────────────────────┘   │
├────────────────────────────────────────────────────────┤
│  🌐 ÇEVRİMİÇİ KAYNAKLAR                                │
│  ├─ 🟢 Openverse       · 200/gün · 47 kullanıldı      │
│  │    ├─ Rain Sound…  ▶  ⓘ                            │
│  │    └─ Thunder…     ▶  ⓘ                            │
│  ├─ 🟢 Internet Archive · sınırsız                    │
│  ├─ 🟡 Jamendo        · Anahtar girilmedi  ⚙          │
│  ├─ 🔴 Freesound      · Anahtar geçersiz   🔄         │
│  └─ ⚪ Audius         · Kapalı                        │
└────────────────────────────────────────────────────────┘
```

### İkon sözlüğü
| İkon | Anlam |
|---|---|
| 🔊 | Ses seviyesi kaydırıcısı |
| 🔇 | Mute (sesi tek tek kapat) |
| ❤️ | Favori |
| ⓘ | Atıf + lisans bilgisi |
| ⏱️ | Zamanlayıcı |
| 💾 | Mix kaydet |
| ⚙️ | Kaynak ayarlarına git |
| 🔄 | Anahtarı yeniden doğrula |
| 🟢🟡🔴⚪ | Kaynak durumu |

---

## 6. ÖNERİM

**Faz 1 (P0):** A–G → 8–10 saat → *"Yarım" hissi biter*
**Faz 2 (P1):** H–S → 12–14 saat → *"Tam özellikli" hissi*
**Faz 3 (P2):** T–AE → 10–12 saat → *"Rakiplerden iyi" hissi*

**Toplam: 30–36 saat.**

> Senin "hiçbir buton eksik kalmasın" isteğini karşılamak için **27 eksik** var. Hepsini listeledim, önceliklendirdim, nereye geleceğini çizdim.

---

## 7. KAYNAKÇA (doğrulanmış, 2026-11-06)

- [Chiarella](https://github.com/carellice/chiarella) — MIT, bugün v1.17; 12 ses, rehberli nefes, AI chat, widget, profiller, timer, Live Update bildirimi
- [Metiq](https://github.com/metiq-xyz/android-app) — GPL-3, 250 yıldız, F-Droid + Play; binaural beats, 5 dil, −22 LUFS, AudioTrack
- [MixNotice](https://github.com/small-rose/MixNotice) — MIT; 23 gerçek ses, 6 kanal, çift timer, CC lisans listesi CSV
- [Rainfold](https://github.com/butul0ve/rainfold) — MediaSessionService mimarisi, DSP çekirdeği Android'den bağımsız
- [Rainfold 2](https://github.com/shayanmohd/rainfold) — Limiter, Poisson scheduler, SleepPlan, Quick Settings tile

---

*Rapor sonu.*
