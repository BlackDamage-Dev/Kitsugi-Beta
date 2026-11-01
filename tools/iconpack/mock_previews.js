// Yeni 3B ikon setiyle ekranların nasıl görüneceğine dair önizleme görselleri.
// Uygulamayı çalıştırmaz; sadece tasarım kontrolü içindir.
// sharp, tools/iconpack içinde `npm install sharp` ile kurulur; yoksa geliştirme
// ortamındaki ortak kuruluma düşer.
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');

const BG = '#080A10', SURF = '#10131D', BORDER = '#252B3A', CHIP = 'rgba(251,146,60,0.16)',
  T1 = '#F4F7FB', T2 = '#A9B0C2', T3 = '#727A8F', ACCENT = '#FB923C';
const ICON_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const { KEYMAP } = require('./icons.map.js');
const iconPath = (k) => path.join(ICON_DIR, 'ic_kitsugi_' + (KEYMAP[k] || k).replace(/-/g, '_') + '.png');

const SETTINGS = [
  ['HESAP', [
    ['account', 'Kitsugi Hesabı', 'Giriş yap, arama geçmişini cihazlar arasında eşitle'],
    ['connections', 'Hesap Bağlantıları', 'AniList, MyAnimeList, Simkl, Kitsu ve Shikimori hesap eşitlemeleri'],
  ]],
  ['UYGULAMA', [
    ['palette', 'Görünüm & Tercihler', 'Tema, liste görünümü, puanlama, dil ve bildirim ayarları'],
    ['player', 'Oynatıcı', 'Dahili oynatıcı, jestler, altyazı, ses ve codec ayarları'],
  ]],
  ['KAYNAKLAR & ENTEGRASYONLAR', [
    ['extensions', 'Eklentiler & Akış Kaynakları', 'Torrent, video sağlayıcıları, debrid ve manga kaynakları'],
    ['integrations', 'Harici Entegrasyonlar', 'TMDB, Fanart.tv, DNS ve diğer dış servisler'],
  ]],
  ['VERİ VE DEPOLAMA', [
    ['database', 'Veri ve Yedekleme', 'Yedek al, geri yükle ve kitaplığını taşı'],
    ['storage', 'Depolama', 'Önbellek, wiki verisi ve artık dosyaları temizle'],
    ['tune', 'İndirme Ayarları', 'Eşzamanlı indirme, kalite ve klasör tercihleri'],
    ['download', 'İndirmeler', 'İndirilen bölümleri ve devam eden işleri yönet'],
    ['history', 'İzleme Geçmişi', 'Nerede kaldığını hatırla, geçmişi temizle'],
  ]],
  ['DESTEK', [
    ['info', 'Hakkında', 'Sürüm 2.4.340 · lisanslar ve katkıda bulunanlar'],
    ['feedback', 'Geri Bildirim', 'Hata bildir, öneri gönder'],
    ['warning', 'Platform Hata Kodları', 'Tanılama kataloğu ve hata açıklamaları'],
  ]],
];

const PLAYER_TABS = [
  ['play', 'Dahili oynatıcı'], ['gestures', 'Hareketler'], ['decoder', 'Kod çözücü'],
  ['subtitles', 'Altyazılar'], ['audio', 'Ses'], ['buttons', 'Özel butonlar'],
  ['code', 'Kod düzenleyici'], ['advanced', 'Gelişmiş'],
];

const PLAYER_ROWS = [
  ['play', 'Varsayılan Video Oynatıcı', 'Hangi oynatma motorunun kullanılacağını seçin'],
  ['audio', 'Otomatik Oynatma', 'Sonraki bölüme otomatik olarak geçiş yap'],
  ['timer', 'Intro Atlama Süresi', '+85s atlama butonunun başlangıçta intro süresine göre ayarlanması'],
  ['memo', 'Bölüm Başlığı Göster', 'Üst kontrolde oynatılan medyanın adını göster'],
  ['tv', 'Çözünürlük Göster', 'Üst kontrolde video çözünürlüğünü göster (örn: 1080p)'],
];

async function renderSettings() {
  const W = 1240, ROW_H = 116, CARD_PAD = 12, SEC_H = 46, ICON = 62;
  const rowsSvg = [];
  const composites = [];
  let y = 108;

  for (const [title, rows] of SETTINGS) {
    rowsSvg.push(`<text x="36" y="${y + 22}" fill="${T3}" font-size="18" font-weight="700" letter-spacing="1.4" font-family="DejaVu Sans, sans-serif">${esc(title)}</text>`);
    y += SEC_H;
    const cardH = rows.length * ROW_H + CARD_PAD * 2;
    rowsSvg.push(`<rect x="24" y="${y}" width="${W - 48}" height="${cardH}" rx="26" fill="${SURF}"/>`);
    rows.forEach((row, i) => {
      const [key, t, d] = row;
      const ry = y + CARD_PAD + i * ROW_H;
      const cy = ry + ROW_H / 2;
      rowsSvg.push(`<text x="116" y="${cy - 3}" fill="${T1}" font-size="24" font-weight="600" font-family="DejaVu Sans, sans-serif">${esc(t)}</text>`);
      rowsSvg.push(`<text x="116" y="${cy + 26}" fill="${T2}" font-size="17" font-family="DejaVu Sans, sans-serif">${esc(d)}</text>`);
      rowsSvg.push(`<text x="${W - 60}" y="${cy + 8}" fill="${T3}" font-size="26" font-family="DejaVu Sans, sans-serif">›</text>`);
      if (i > 0) rowsSvg.push(`<rect x="116" y="${ry}" width="${W - 140}" height="1" fill="${BORDER}"/>`);
      composites.push({ file: iconPath(key), left: Math.round(46 + (ICON - 56) / 2), top: Math.round(cy - ICON / 2), size: ICON });
    });
    y += cardH + 30;
  }

  const H = y + 6;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
    <text x="36" y="66" fill="${T1}" font-size="42" font-weight="700" font-family="DejaVu Sans, sans-serif">Ayarlar</text>
    ${rowsSvg.join('\n')}
  </svg>`;
  await write('/home/user/onizleme_ayarlar.png', W, H, svg, composites);
}

async function renderPlayer() {
  const W = 1240, ROW_H = 116, CARD_PAD = 12, ICON = 62;
  const parts = [];
  const composites = [];

  parts.push(`<rect x="46" y="34" width="34" height="4" rx="2" fill="${T2}"/>`);
  parts.push(`<text x="46" y="96" fill="${T1}" font-size="36" font-weight="700" font-family="DejaVu Sans, sans-serif">Oynatıcı Ayarları</text>`);

  // sekme bandı
  const bandY = 124, bandH = 108;
  parts.push(`<rect x="24" y="${bandY}" width="${W - 48}" height="${bandH}" rx="20" fill="${SURF}" stroke="${BORDER}"/>`);
  const segW = (W - 48 - 8) / PLAYER_TABS.length;
  PLAYER_TABS.forEach(([key, label], i) => {
    const sx = 24 + 4 + i * segW;
    if (i === 0) {
      parts.push(`<rect x="${sx + 4}" y="${bandY + 8}" width="${segW - 8}" height="${bandH - 16}" rx="14" fill="${CHIP}" stroke="rgba(251,146,60,0.48)"/>`);
    }
    composites.push({ file: iconPath(key), left: Math.round(sx + segW / 2 - 15), top: bandY + 26, size: 44 });
    parts.push(`<text x="${sx + segW / 2}" y="${bandY + 92}" fill="${i === 0 ? ACCENT : T2}" font-size="15" font-weight="${i === 0 ? 700 : 400}" text-anchor="middle" font-family="DejaVu Sans, sans-serif">${esc(label)}</text>`);
  });

  // satırlar
  let y = bandY + bandH + 36;
  parts.push(`<text x="36" y="${y}" fill="${T3}" font-size="18" font-weight="700" letter-spacing="1.4" font-family="DejaVu Sans, sans-serif">OYNAYICI ARAYÜZÜ</text>`);
  y += 20;
  const cardH = PLAYER_ROWS.length * ROW_H + CARD_PAD * 2;
  parts.push(`<rect x="24" y="${y}" width="${W - 48}" height="${cardH}" rx="26" fill="${SURF}"/>`);
  PLAYER_ROWS.forEach((row, i) => {
    const [key, t, d] = row;
    const ry = y + CARD_PAD + i * ROW_H;
    const cy = ry + ROW_H / 2;
    parts.push(`<text x="116" y="${cy - 3}" fill="${T1}" font-size="24" font-weight="600" font-family="DejaVu Sans, sans-serif">${esc(t)}</text>`);
    parts.push(`<text x="116" y="${cy + 26}" fill="${T2}" font-size="17" font-family="DejaVu Sans, sans-serif">${esc(d)}</text>`);
    if (i > 0) parts.push(`<rect x="116" y="${ry}" width="${W - 140}" height="1" fill="${BORDER}"/>`);
    composites.push({ file: iconPath(key), left: Math.round(46 + (ICON - 56) / 2), top: Math.round(cy - ICON / 2), size: ICON });
    // sağda anahtar (switch) temsili
    parts.push(`<rect x="${W - 120}" y="${cy - 16}" width="64" height="32" rx="16" fill="rgba(251,146,60,0.85)"/>`);
    parts.push(`<circle cx="${W - 74}" cy="${cy}" r="12" fill="#0B0F16"/>`);
  });
  y += cardH + 24;

  const H = y;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${parts.join('\n')}</svg>`;
  await write('/home/user/onizleme_oynatici.png', W, H, svg, composites);
}

async function write(out, W, H, svg, comps) {
  const layers = [{ input: Buffer.from(svg), top: 0, left: 0 }];
  for (const c of comps) {
    const buf = await sharp(fs.readFileSync(c.file)).resize(c.size, c.size).png().toBuffer();
    layers.push({ input: buf, left: c.left, top: c.top });
  }
  await sharp({ create: { width: W, height: H, channels: 4, background: BG } })
    .composite(layers).png().toFile(out);
  console.log('yazıldı', out, W + 'x' + H);
}

(async () => {
  await renderSettings();
  await renderPlayer();
})();
