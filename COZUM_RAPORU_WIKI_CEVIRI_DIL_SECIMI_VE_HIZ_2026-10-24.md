# Wiki Sayfa Araçlarından Dil Seçimi + Otomatik Çeviri Hızlandırma — Çözüm Raporu

**Tarih:** 2026-10-24
**Dal:** `arena/22d8caa2-kitsugi-beta`
**Şikâyet (ekran görüntüsü: Wikipedia "My Wife Is the Student Council President", Sayfa araçları):**

1. Otomatik çeviride **çevrilen / çevrilecek dil**, uygulama ayarlarına girmeden
   buradan (Sayfa araçları → Çeviri satırından) seçilebilsin.
2. Otomatik çeviri sayfayı hâlâ **feci yavaş** çeviriyor ("Çevriliyor… (162 metin)").
   Mevcut çeviri mantığı bozulmadan fişek gibi hızlansın; araştırma yapılarak.

---

## 1. Araştırma özeti (açık kaynak / dokümantasyon)

- **Google Translate tek istek sınırı 5000 karakterdir** (resmî olmayan `gtx`
  uç noktasını kullanan kütüphaneler ve Google yardım sayfası aynı sınırı verir):
  https://github.com/ahmeterenodaci/easygoogletranslate ,
  https://www.npmjs.com/package/google-translate-api-x ("maximum text length 5000,
  batch endpoint supports multiple queries in one request"),
  https://support.google.com/translate/answer/6142478
- **Toplu (batch) gönderim + istek birleştirme**, açık kaynak sayfa çeviricilerinin
  (Immersive Translate, KISS Translator — GPL; kod kopyalanmadı, yalnızca yaklaşım
  incelendi) temel hız tekniğidir: metinleri paketle, tekrarları ele, önbellekle,
  eşzamanlılığı sınırla:
  https://github.com/fishjar/kiss-translator ("Aggregate and send translated texts in batches"),
  https://deepwiki.com/immersive-translate/immersive-translate/6.1-supported-languages
  (request batching + rate limiting + caching mimarisi)
- **Döngü içinde `getBoundingClientRect()` / `getComputedStyle()`** çağrıları
  zorunlu eşzamanlı layout ("layout thrashing") üretir; görünürlük için tek
  yerleşik çağrı (`checkVisibility()`) ve önbellekleme önerilir:
  https://dev.to/aayla_secura/layout-thrashing-what-is-it-and-how-to-eliminate-it-n2j ,
  https://developer.mozilla.org/en-US/docs/Web/API/Intersection_Observer_API

## 2. Kök nedenler (ölçülen mimari, tahmin değil)

| # | Kök neden | Etki |
|---|-----------|------|
| H1 | `WikiPageEnhancements` döngüsü **tamamen seri**: take(≤8 metin/≤1000 krk) → ağ bekle → apply → **120 ms uyu** | 162 metin ≥21 ardışık ağ turu; tur başına RTT + 120 ms ölü süre |
| H2 | Paket 1000 karakterle sınırlı; çevirmen parçası 1400 | Google'ın 5000 sınırının ~1/5'i kullanılıyor → istek sayısı ~3x şişiyor |
| H3 | JS `eligible()` her düğümde tüm üst öğelerde `getComputedStyle` + `getBoundingClientRect` + `getClientRects` + uzun `closest(skip)` + `closest('[lang]')` | Binlerce pahalı stil/layout sorgusu; 8 ms'lik tarama bütçesi birkaç düğümde doluyor |
| H4 | Çevrilmiş/bekleyen düğümler de her taramada H3'ten yeniden geçiyor; `status()` her çağrıda tüm kayıtları `trim()` karşılaştırmasıyla sayıyor | Sayfa büyüdükçe tarama giderek yavaşlıyor (O(tüm kayıt × tur)) |
| H5 | Önbellek **paket bütünü** anahtarlı: paket dizilişi değişirse (kaydırma konumu, sayfaya dönüş) önbellek hiç tutmuyor; bellek içi katman yok, her sorgu Room/SQLite | Aynı paragraflar tekrar tekrar ağa gidiyor |
| H6 | Her çeviri çağrısı maskeleme Regex'lerini yeniden derliyor; düz metinler bile 6 regex taramasından geçiyor | Yüzlerce çağrıda CPU + tahsisat |
| H7 | Reklam stili betiği her döngü turunda JS köprüsünden gönderiliyor | Gereksiz renderer turu |

## 3. Uygulanan düzeltmeler (mantık/sözleşme değişmeden)

### 3.1 Dil seçimi — Sayfa araçlarından, ayarlara girmeden
- `WikiPageTools.kt`: Çeviri anahtarının altına **"Çevrilen dil"** ve **"Çevrilecek dil"**
  satırları eklendi (seçili dil adı + açılır işareti). Satıra dokununca
  **aranabilir** dil seçici açılır (132 dil, kaynak tarafta "Otomatik (Algıla)";
  `LazyColumn` ile yalnızca görünür satırlar bileşir).
- Seçim, Ayarlar'daki aynı küresel ayarlara yazılır (`SettingsDataStore.setTranslateSourceLanguage` /
  `setTranslateTargetLanguage`) → iki yer her zaman senkron; mevcut akış
  (ayar değişimi → yeniden çeviri) aynen çalışır, yeni durum makinesi yok.
- **Dil seçmek = bu dile çevirmek istemek**: çeviri kapalıysa seçim bu sayfada
  çeviriyi açar (`pendingTranslationEnable` tek kullanımlık bayrağı, ayar akışı
  manuel seçimi sıfırlarken niyeti korur; yazım başarısız olursa bayrak temizlenir).
  Ayarlar sayfasından gelen değişikliklerde eski "otomatiği izle" davranışı korunur.
- Yeni metinler TR+EN: `wiki_translate_source_language`, `wiki_translate_target_language`,
  `wiki_translate_language_search`; "Sayfa araçları hakkında" açıklaması güncellendi.

### 3.2 Boru hattı paralelleştirme — `WikiPageEnhancements.kt`
- Seri döngü → **boru hattı**: en çok 4 paket aynı anda çevrilir (çevirmenin ağ
  kapısıyla birebir aynı sınır; 429 riski artmaz). JS köprüsü çağrıları (`take`/`apply`)
  tek coroutine'de sıralı kalır; sonuçlar belge sırasıyla uygulanır.
- Paket sonrası sabit **120 ms bekleme kaldırıldı** (boşta bekleme süreleri aynı: tararken 120 ms, boşta 900 ms).
- Reklam stili artık en sık 2 saniyede bir tazelenir (H7).

### 3.3 Büyük paket + metin bazlı önbellek — `WikiDomTranslationBatch.kt`, `TranslationManager.kt`
- Paket: 8 metin/1000 krk → **16 metin/3200 krk**; sayfa çevirisi parça boyutu
  1400 → **3600** (`WEB_MAX_CHUNK_CHARS`, yalnızca web yolu; açıklama/biyografi
  akışının 1400 grenliliği aynı). Bir paket = tek ağ isteği.
- Metinler önce **tek tek önbelleğe** sorulur; yalnızca eksikler paketlenir.
  Başarılı çeviriler tek tek (toplu tek Room işlemiyle) saklanır → önbellek
  paket dizilişinden bağımsız çalışır (H5). Anahtar biçimi (`webdom-v1_…`) aynı,
  eski önbellek geçerli.
- Paketteki **aynı metinler tekilleştirilir**; ayraç bozulursa tekil yedekler
  paralel koşar. Kesintide "yelpazeleme yok" kuralı aynen korunur.
- Room'un önüne **600 kayıt'lık süreç içi LRU** bellek önbelleği (aynı özet anahtarları):
  tekrarlar disksiz döner; parça önbelleği de aynı katmanı kullanır → yerli okuyucu da hızlanır.
- Uzun tek düğümün parçaları artık paralel istenir (sıra korunur, kapı sınırlı).
- Maskeleme: Regex'ler tek kez derlenir; korunan kalıp tetikleyicisi içermeyen
  düz metinler hızlı yoldan geçer (şüphede tam maskeleme; koruma davranışı aynı) (H6).

### 3.4 DOM tarayıcısı — `dom-translator.js`
- Öğe başına skip/görünürlük/dil sonucu **WeakMap önbelleğinde**; mutasyonda
  yalnızca değişen alt ağaç geçersizleşir, her yeni tarama turu taze başlar
  (`content-visibility`/medya sorgusu gibi mutasyonsuz değişimler de yakalanır);
  `resize` ve `<details open>` da izlenir (H3).
- Görünürlük: `el.checkVisibility({checkOpacity, checkVisibilityCSS})` yerleşik
  kontrolü; yoksa eski `getComputedStyle` yürüyüşü yedek.
- Çevrilmiş/bekleyen/denemesi dolmuş düğümler yeniden taramada tam uygunluk
  testinden geçmez (H4); `status()` artık uygulama anında kaydedilen bayrağı sayar.
- Tarama bütçesi (8 ms) ve görüntü alanına yakınlık penceresi
  (-1 … +3 ekran) **aynı**; adım sınırı önbellekli taramaya uyumlu 1500.

### Korunan sözleşmeler (özellikle dokunulmadı)
Ayraç bütünlük denetimi (bozuk ayraçta konumsal tahmin yok), yalnızca `Text.nodeValue`
değişimi, rev/id doğrulaması, orijinale ağsız dönüş, gizli/form/kod içeriklerini
göndermeme, düğüm/karakter güvenlik sınırları (6000 / 1M), deneme sayısı ve geri
çekilme, ağ kapısı (4), uç nokta yedekleme sırası, önbellek anahtarları.

## 4. Beklenen etki (ekran görüntüsündeki sayfa, 162 metin)

- Önce: ≥21 ardışık ağ turu × (RTT + 120 ms ölü süre + tarama/JS turları).
- Sonra: ~11–14 istek, 4'ü paralel ⇒ ~3–4 ağ turu derinliği; tarama turu başına
  stil/layout sorguları öğe başına bir kez. **Duvar saati cinsinden kabaca 5–8 kat**
  (ağ hızına göre değişir). Sayfaya ikinci gelişte metinler tek tek önbellekten
  (bellek/Room) ağsız dolar. Kaydırma kasması tarama bütçesi (8 ms) aynı kaldığı
  için artmaz; tarama ucuzladığı için azalır.

## 5. Testler

- **JS işlev testi (jsdom, bu oturumda çalıştırıldı):** kök alan dışı metin,
  `display:none`, `opacity:0`, `<pre>`, `translate="no"` asla toplanmıyor;
  bağlantı/tablo metinleri çevriliyor; kapatınca orijinaller dönüyor; yeniden
  açınca önbellekten uygulanıyor; dil değişimi orijinali geri getirip yeniden
  topluyor; sayım doğru. `node --check` temiz.
- **Yeni birim testleri** (`WikiPageToolsTest`): önbellekteki satırlar ağa gitmez
  ve taze satırlar tek tek saklanır; paket tek istekte sıra korur; bozuk ayraçta
  satır satır yedek (tahmin yok); kesintide yelpazeleme olmaz (1 istek, sonuçlar null).
- **Not:** Bu çalışma ortamında JDK/Android SDK yok; Gradle derlemesi burada
  çalıştırılamadı (`java` bulunamadı). Değişiklikler derleme gözüyle satır satır
  gözden geçirildi; CI/yerel derlemede `:app:testFossDebugUnitTest --tests "*WikiPageToolsTest*"`
  koşulması önerilir.

## 6. Değişen dosyalar

| Dosya | Değişiklik |
|-------|-----------|
| `ui/screens/detail/wiki/WikiPageTools.kt` | Çevrilen/Çevrilecek dil satırları + aranabilir dil seçici |
| `ui/screens/detail/wiki/WikiOriginalPage.kt` | Dil seçimini ayarlara yazımı, `pendingTranslationEnable`, ortak kayıt iskeleti |
| `ui/screens/detail/wiki/WikiPageEnhancements.kt` | 4 paket paralel boru hattı, 120 ms kaldırma, reklam betiği kısma |
| `ui/screens/detail/wiki/WikiDomTranslationBatch.kt` | 16 satır, metin bazlı önbellek, tekilleştirme, paralel yedek |
| `data/local/TranslationManager.kt` | Bellek LRU, web parça 3600 + paralel parçalar, `cached/storeWebPageTexts`, maske hızlı yolu + derlenmiş Regex |
| `data/local/TranslationCacheDao.kt` | Toplu yazım (`insertTranslations`) |
| `app/src/main/assets/wiki/dom-translator.js` | Öğe bilgisi önbelleği, `checkVisibility`, yerleşmiş düğüm kestirmesi, ucuz sayım, büyük paket |
| `res/values|values-en/strings.xml` | 3 yeni metin + açıklama güncellemesi |
| `app/src/test/.../WikiPageToolsTest.kt` | 4 yeni test |
