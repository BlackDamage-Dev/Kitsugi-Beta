# Kitsugi Beta v2.4.452 — Manga Okuyucu Müzik & Ambiyans Sistemi (Ambient Audio Engine)

### 🇹🇷 Türkçe Güncelleme Özeti

Bu sürümle birlikte Kitsugi Beta, manga okuma deneyimini yeni bir boyuta taşıyan çok kanallı **Manga Okuyucu Müzik & Ambiyans Sistemi**'ne kavuşuyor. Okunan manganın türü, adı ve açıklaması anlık olarak çözümlenip en uygun atmosfer ve müzik parçaları otomatik olarak önerilir ve çalınır.

#### 🎵 Yenilikler ve Öne Çıkan Özellikler:
1. **Manga Okuyucu Ambiyans Paneli (7 Sekme):**
   - Manga okurken alt bardaki yeni Melodi düğmesi ile açılan hafif ve güçlü alt kontrol sayfası.
   - **Senin İçin (For You):** Manga türüne ve duygu profiline göre otomatik atmosfer presetleri ve ses önerileri.
   - **Ambiyans & Müzik:** Yağmur, rüzgar, şömine, orman, fırtına, piyano, koto, lo-fi ve prosedürel sentezleyiciler.
   - **Karıştırıcı (Mixer):** Aynı anda 4 farklı ses kanalını bağımsız ses seviyeleri ve susturma (mute) seçenekleriyle birlikte çalabilme.
   - **Çevrimiçi Arama & Keşif:** Jamendo, Freesound, Openverse, Internet Archive ve Audius üzerinden yüz binlerce lisanslı müzik ve ses efekti arama.
   - **Çevrimdışı İndirme:** Beğenilen ortam seslerini cihaza kaydederek internet bağlantısı olmadan çevrimdışı çalabilme.
   - **Karışım Profilleri & Paylaşım:** Kendi oluşturduğunuz özel ses karışımlarını profil olarak kaydetme, panoya kopyalayarak paylaşma ve içe aktarma.
   - **Uyku Zamanlayıcısı (Sleep Timer):** 15, 30, 45, 60 dakika ve özel sürelerle otomatik ses kapatma.

2. **Duygu ve Tür Öneri Motoru (Mood & Recommendation Engine):**
   - 12 dilde 58.000+ Bangumi/AniList etiket ve tür veritabanı desteği (`BangumiTagDictionary`, `GenreMoodMapper`, `ContentMoodResolver`).
   - Manganın türlerine ve ruh haline göre (aksiyon, romantizm, gizem, korku, bilim kurgu, günlük yaşam vb.) nokta atışı müzik eşleştirmesi.

3. **6 Farklı Ses Kaynağı Desteği:**
   - 4 kaynak anahtarsız ve doğrudan çalışır (Yerel/Prosedürel Sentezleyiciler, Jamendo, Openverse, Internet Archive).
   - Freesound ve Audius için opsiyonel kişisel API anahtarı desteği.

4. **Arka Plan Çalma ve Bildirim Desteği (`AmbientPlaybackService`):**
   - Okuyucudan çıkılsa dahi müziği kesintisiz devam ettirebilme ve Android medya bildiriminden kontrol edebilme.

5. **Ayarlar Entegrasyonu:**
   - Ayarlar -> Entegrasyonlar sekmesine 5. sekme olarak "Ortam Sesi / Müzik" kontrolleri eklendi (Otomatik başlatma gecikmesi, ana ses düzeyi, arka planda çalma, kaynak aç/kapa).

---

### 🇬🇧 English Release Notes

Kitsugi Beta v2.4.452 introduces the all-new multi-channel **Manga Reader Ambient Audio & Music Engine**, elevating your reading immersion with procedural atmosphere synths and catalog audio.

#### 🎵 What's New:
1. **Manga Ambient Bottom Sheet (7 Tabs):**
   - Access via the new Melody button in the reader's bottom control bar.
   - **For You:** Dynamic mood matching recommending tailored presets and ambient tracks for the current manga.
   - **Ambience & Music:** Atmospheric procedural audio including rain, wind, fire, forest, waves, piano, koto, and lo-fi melodies.
   - **Multi-channel Mixer:** Blend up to 4 audio tracks simultaneously with individual volume faders, mute switches, and master volume control.
   - **Catalog Search:** Search and stream free creative commons audio from Jamendo, Freesound, Openverse, Internet Archive, and Audius.
   - **Offline Audio Downloads:** Download your favorite ambient loops and music tracks for 100% offline playback.
   - **Mix Profiles & Clipboard Export/Import:** Save custom soundscapes and share them with simple JSON strings.
   - **Sleep Timer:** Automatic fade-out timer (15, 30, 60 min or custom duration).

2. **Smart Content Mood Resolver:**
   - Multilingual genre & tag analysis supporting 58k+ tags across 12 languages.
   - Accurately matches tone (Horror, Romance, Action, Sci-Fi, Slice of Life, Mystery) to the appropriate audio soundscapes.

3. **Background Media Playback (`AmbientPlaybackService`):**
   - Foreground media service supporting continuous playback when navigating outside the reader, with full notification controls.

4. **Settings & Integrations:**
   - Added dedicated "Ambient Audio / Music" tab in Integrations settings dialog.
