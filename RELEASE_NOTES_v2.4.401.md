# Kitsugi Beta v2.4.401 — Sürüm Notları / Release Notes

---

## 🇹🇷 Türkçe Sürüm Notları

### 🎬 Anizm & Video Sağlayıcıları Çözümleme ve Doğrulama Denetimi (Stream Audit)

* **Sistenn & AnizmPlayer İmzalı HLS Desteği**: `anizmplayer.com`, `sistenn.uns.bio`, `anizm.rpmvid.com` ve `anizm.strp2p.com` üzerindeki `/v4/`, `/cdn/hls/`, `/hlsmod/` gibi uzantısız imzalı adresler doğrudan medya akışı olarak tanındı. Bu adreslerin HTML sayfası sanılarak gereksiz GET istekleriyle bozulması engellendi ve doğrudan Media3 / MPV HLS oynatıcısına yönlendirildi.
* **Google Drive Çözücü & Playback API**: Eklentiler tarafından önceden oluşturulmuş `/download` ve `/uc` bağlantıları, oturum çerezleri ve kaynak anahtarları korunarak doğrudan kontrol edildi. Workspace Video Playback API entegrasyonu ve onay formu tespiti ile Google Drive kaynaklarının hata vermesi engellendi.
* **SendVid Çözücü & Geçici Hata Koruması**: SendVid'in `/<id>.mp4` biçimindeki oynatıcı sayfalarının doğrudan medya dosyası sanılması engellendi. Sayfa HTML'inden gerçek video bağlantısı ayıklanarak doğru Referer bağlandı; sunucunun "video geçici olarak kullanılamaz" yanıtı vermesi durumunda kullanıcı net bir uyarıyla bilgilendirildi.
* **Voe Çözücü (F7 JSON & Host Aynaları)**: Voe için yeni F7 JSON ayrıştırıcısı, rot13 ve ek topluluk alan adları entegre edildi. Voe oynatıcılarında Origin başlığı sonundaki gereksiz eğik çizgi kaldırılarak 403 Forbidden hataları önlendi.
* **Eklenti Başlık ve Çerez Korunması**: Extractor fallback aşamasında eklentilerin sağladığı Referer, Origin ve özel başlıkların kaybolması engellendi; segment isteklerine kadar taşınması güvenceye alındı.
* **Eklenti Kurulum Güvenliği (SHA-256 Hash Kontrolü)**: SHA-256 sağlama toplamı eşleşmeyen aday `.cs3` paketleri reddedilerek alternatif yansımaya geçilmesi sağlandı.
* **Güncel Eklenti Bildirimi**: Anizm (v21) ve TrAnimeIzle (v45) eklentileri `plugins.json` içinde en güncel derleme URL'leri ve SHA-256 sağlama toplamlarıyla güncellendi.

---

## 🇬🇧 English Release Notes

### 🎬 Anizm & Video Provider Stream Audit & Verification Overhaul

* **Sistenn & AnizmPlayer Signed HLS Ingestion**: Extensionless signed streaming endpoints (`sistenn.uns.bio`, `anizmplayer.com`, `anizm.rpmvid.com`) containing `/v4/`, `/cdn/hls/`, and `/hlsmod/` are now recognized as direct media. Preventing spurious HTML fetching and ensuring correct HLS player routing.
* **Google Drive Playback API & Direct Form Resolution**: Pre-formed `/download` and `/uc` links with cookie and resource keys are probed directly. Added Workspace Video Playback API resolution and download form inspection.
* **SendVid Resolver & Status Tracking**: Addressed SendVid `/<id>.mp4` player route confusion by extracting the real progressive stream and supplying the appropriate hotlink Referer. Added provider-level tracking for "This video is temporarily unavailable" notices.
* **Voe Extractor Updates**: Added support for F7 JSON payloads, rot13 mapping, and new community mirrors. Fixed trailing slash in Origin headers that caused 403 Forbidden responses.
* **Provider Header Preservation**: Extracted headers (Referer, Origin, cookies, and tokens) are preserved through embed resolution all the way to playback segment requests.
* **Plugin SHA-256 Verification**: Reject candidate `.cs3` downloads on hash mismatch and fall through to subsequent mirrors to ensure integrity.
* **Plugin Metadata Updates**: Updated Anizm (v21) and TrAnimeIzle (v45) build URLs and hashes in `plugins.json`.
