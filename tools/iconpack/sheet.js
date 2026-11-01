// Tüm 3B ikon setinin etiketli önizleme sayfası (docs/ikon-seti-onizleme.png).
const sharp = (() => {
  try { return require('sharp'); } catch (e) {}
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');
const { KEYMAP, LABELS } = require('./icons.map.js');

const ICON_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const OUT = path.join(__dirname, '..', '..', 'docs', 'ikon-seti-onizleme.png');
const BG = '#0A0D14', CELL_BG = '#10131D', BORDER = '#232A38', T1 = '#F4F7FB', T2 = '#A9B0C2', T3 = '#727A8F';
const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const ORDER = [
  'account', 'connections', 'palette', 'player', 'extensions', 'integrations',
  'database', 'storage', 'tune', 'download', 'history', 'info',
  'feedback', 'warning', 'help', 'memo', 'play', 'pause',
  'next', 'rewind', 'gestures', 'decoder', 'subtitles', 'audio',
  'buttons', 'code', 'advanced', 'speed', 'timer', 'sun',
  'moon', 'tv', 'network', 'sync', 'key', 'star',
  'image', 'heart', 'clipboard', 'chart', 'check_circle', 'lock',
  'folder', 'bell', 'calendar', 'sparkles', 'thumbup', 'mobile',
  'pin', 'nav_explore', 'nav_list', 'nav_search', 'nav_profile', 'nav_settings',
];

const COLS = 6, CELL = 220, PAD = 26, HEADER = 150, GRID_TOP = HEADER, FOOTER = 190;
const ROWS = Math.ceil(ORDER.length / COLS);
const W = COLS * CELL + PAD * 2;
const H = GRID_TOP + ROWS * CELL + FOOTER;

const fileFor = (key) => path.join(ICON_DIR, 'ic_kitsugi_' + (KEYMAP[key] || key).replace(/-/g, '_') + '.png');

(async () => {
  const parts = [];
  const comps = [];
  parts.push(`<text x="${PAD}" y="66" fill="${T1}" font-size="40" font-weight="700" font-family="DejaVu Sans, sans-serif">Kitsugi 3B İkon Seti</text>`);
  const assetCount = new Set(Object.values(KEYMAP).map((v) => v.replace(/-/g, '_'))).size;
  parts.push(`<text x="${PAD}" y="106" fill="${T2}" font-size="19" font-family="DejaVu Sans, sans-serif">${assetCount} varlık · ${ORDER.length} kullanım · saydam zemin · arka plansız · 3Dicons (CC0) · cam + gradyan</text>`);

  ORDER.forEach((key, i) => {
    const x = PAD + (i % COLS) * CELL;
    const y = GRID_TOP + Math.floor(i / COLS) * CELL;
    parts.push(`<rect x="${x + 8}" y="${y + 8}" width="${CELL - 16}" height="${CELL - 16}" rx="24" fill="${CELL_BG}" stroke="${BORDER}"/>`);
    parts.push(`<text x="${x + CELL / 2}" y="${y + CELL - 46}" fill="${T2}" font-size="17" text-anchor="middle" font-family="DejaVu Sans, sans-serif">${esc(LABELS[key] || key)}</text>`);
    comps.push({ file: fileFor(key), left: Math.round(x + CELL / 2 - 52), top: y + 26, size: 104 });
  });

  const fy = GRID_TOP + ROWS * CELL + 20;
  parts.push(`<text x="${PAD}" y="${fy + 26}" fill="${T3}" font-size="17" font-weight="700" letter-spacing="1.2" font-family="DejaVu Sans, sans-serif">SATIR İÇİNDE (30 dp)</text>`);
  const cellW = Math.floor((W - PAD * 2) / 5);
  const STRIP = [
    ['account', 'Hesap'], ['palette', 'Görünüm'], ['extensions', 'Eklentiler'],
    ['storage', 'Depolama'], ['history', 'Geçmiş'],
  ];
  STRIP.forEach(([key, short], i) => {
    const x = PAD + i * cellW;
    const cy = fy + 74;
    parts.push(`<rect x="${x}" y="${cy - 34}" width="${cellW - 14}" height="68" rx="18" fill="${CELL_BG}"/>`);
    parts.push(`<text x="${x + 76}" y="${cy - 3}" fill="${T1}" font-size="17" font-weight="600" font-family="DejaVu Sans, sans-serif">${esc(short)}</text>`);
    parts.push(`<text x="${x + 76}" y="${cy + 18}" fill="${T3}" font-size="12" font-family="DejaVu Sans, sans-serif">${esc(LABELS[key])}</text>`);
    comps.push({ file: fileFor(key), left: x + 12, top: Math.round(cy - 27), size: 54 });
  });

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${parts.join('\n')}</svg>`;
  const layers = [{ input: Buffer.from(svg), top: 0, left: 0 }];
  for (const c of comps) {
    const buf = await sharp(fs.readFileSync(c.file)).resize(c.size, c.size).png().toBuffer();
    layers.push({ input: buf, left: c.left, top: c.top });
  }
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  await sharp({ create: { width: W, height: H, channels: 4, background: BG } })
    .composite(layers).png().toFile(OUT);
  await sharp(OUT).resize(Math.round(W / 2)).png().toFile('/home/user/onizleme_tum_ikonlar.png');
  console.log('yazıldı', OUT, W + 'x' + H);
})();
