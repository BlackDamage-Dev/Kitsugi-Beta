// Tema + renk seçimi uyum sayfası.
// Gösterir: (A) doğal palet koyu/AMOLED/açık temada, (B) 6 vurgu renginde otomatik
// ton kaydırması + seçili hâle, (C) aynı ekranın "doğal" ve "otomatik" hâli.
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();
const fs = require('fs');
const { KEYMAP } = require('./icons.map.js');

const ICON_DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const OUT = path.join(__dirname, '..', '..', 'docs', 'ikon-tema-uyumu.png');
const FONT = 'DejaVu Sans, sans-serif';
const NATIVE_HUE = 303.7;

const fileFor = (key) => path.join(ICON_DIR, 'ic_kitsugi_' + (KEYMAP[key] || key).replace(/-/g, '_') + '.png');
const esc = (t) => String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function hueOf(hex) {
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d) {
    if (mx === r) h = ((g - b) / d) % 6;
    else if (mx === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  return h;
}
const shiftFor = (hex) => {
  const d = hueOf(hex) - NATIVE_HUE;
  return Math.round(((d + 540) % 360) - 180);
};

const ACCENTS = [
  ['Mint', '#C8F4EF', '#8FE3D6'],
  ['Pembe', '#F472B6', '#FB7185'],
  ['Mor', '#A78BFA', '#8B5CF6'],
  ['Mavi', '#60A5FA', '#38BDF8'],
  ['Yeşil', '#4ADE80', '#22C55E'],
  ['Turuncu', '#FB923C', '#F97316'],
];
const ROWS = [
  ['account', 'Kitsugi Hesabı'],
  ['palette', 'Görünüm & Tercihler'],
  ['download', 'İndirmeler'],
  ['history', 'İzleme Geçmişi'],
];
const NAV = ['nav_explore', 'nav_list', 'nav_search', 'nav_profile', 'nav_settings'];

const W = 1440;
const PAD = 48;

(async () => {
  const comps = [];
  const push = (key, left, top, size, shift = 0) => comps.push({ key, left: Math.round(left), top: Math.round(top), size: Math.round(size), shift });
  const parts = [];
  let y = 0;

  const defs = ACCENTS.map((a, i) =>
    `<radialGradient id="glow${i}"><stop offset="0" stop-color="${a[1]}" stop-opacity="0.55"/><stop offset="0.55" stop-color="${a[1]}" stop-opacity="0.18"/><stop offset="1" stop-color="${a[1]}" stop-opacity="0"/></radialGradient>
<linearGradient id="acc${i}" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="${a[1]}"/><stop offset="1" stop-color="${a[2]}"/></linearGradient>`).join('\n');

  // ── Başlık
  parts.push(`<rect x="0" y="0" width="${W}" height="118" fill="#080A10"/>`);
  parts.push(`<text x="${PAD}" y="58" fill="#F4F7FB" font-size="33" font-weight="700" font-family="${FONT}">İkon seti × renk seçimi × tema</text>`);
  parts.push(`<text x="${PAD}" y="92" fill="#A9B0C2" font-size="16" font-family="${FONT}">Vurgu rengi (gradyan dâhil) seçilince ikonların tonu otomatik kayar; doygunluk ve cam görünüm korunur</text>`);
  y = 118;

  // ── A) Doğal palet, üç tema
  const blocks = [
    ['A · DOĞAL PALET / KOYU TEMA', '#080A10', '#10131D', '#252B3A', '#F4F7FB', '#727A8F', false],
    ['B · DOĞAL PALET / AMOLED', '#000000', '#080808', '#1F1F1F', '#F4F7FB', '#727A8F', false],
    ['C · DOĞAL PALET / AÇIK TEMA (yumuşak gölge)', '#F3F4F6', '#FFFFFF', '#E5E7EB', '#111827', '#9CA3AF', true],
  ];
  for (const [title, bg, card, border, t1, t2, light] of blocks) {
    parts.push(`<rect x="0" y="${y}" width="${W}" height="300" fill="${bg}"/>`);
    parts.push(`<text x="${PAD}" y="${y + 38}" fill="${light ? '#6B7280' : '#727A8F'}" font-size="14" font-weight="700" letter-spacing="1.3" font-family="${FONT}">${esc(title)}</text>`);
    ROWS.forEach(([key, label], i) => {
      const cy = y + 74 + i * 56;
      parts.push(`<rect x="${PAD}" y="${cy - 24}" width="${W - PAD * 2}" height="52" rx="14" fill="${card}" stroke="${border}"/>`);
      parts.push(`<text x="${PAD + 60}" y="${cy + 5}" fill="${t1}" font-size="17" font-weight="600" font-family="${FONT}">${esc(label)}</text>`);
      if (light) parts.push(`<circle cx="${PAD + 31}" cy="${cy}" r="20" fill="url(#shade)"/>`);
      push(key, PAD + 12, cy - 18, 36, 0);
    });
    y += 300;
  }

  // ── B) Vurgu rengine göre otomatik ton
  parts.push(`<rect x="0" y="${y}" width="${W}" height="392" fill="#080A10"/>`);
  parts.push(`<text x="${PAD}" y="${y + 38}" fill="#727A8F" font-size="14" font-weight="700" letter-spacing="1.3" font-family="${FONT}">D · VURGUYA GÖRE OTOMATİK TON — aynı set, 6 renk seçimi (seçili sekmede vurgu hâlesi)</text>`);
  const cellW = Math.floor((W - PAD * 2) / ACCENTS.length);
  ACCENTS.forEach(([name, c1, c2], i) => {
    const cx = PAD + i * cellW;
    const top = y + 58;
    const shift = shiftFor(c1);
    parts.push(`<rect x="${cx}" y="${top}" width="${cellW - 20}" height="286" rx="18" fill="#0E1119" stroke="#1E2433"/>`);
    parts.push(`<text x="${cx + 18}" y="${top + 30}" fill="#F4F7FB" font-size="16" font-weight="700" font-family="${FONT}">${esc(name)}</text>`);
    parts.push(`<text x="${cx + 18}" y="${top + 50}" fill="#727A8F" font-size="12" font-family="${FONT}">${c1} · ton ${shift > 0 ? '+' : ''}${shift}°</text>`);
    // ayar satırı örneği
    parts.push(`<rect x="${cx + 14}" y="${top + 64}" width="${cellW - 48}" height="52" rx="12" fill="#10131D" stroke="#252B3A"/>`);
    push('palette', cx + 22, top + 73, 34, shift);
    parts.push(`<text x="${cx + 64}" y="${top + 88}" fill="#F4F7FB" font-size="13" font-weight="600" font-family="${FONT}">Görünüm</text>`);
    parts.push(`<rect x="${cx + 64}" y="${top + 96}" width="56" height="7" rx="4" fill="url(#acc${i})"/>`);
    // gezinme çubuğu
    const navTop = top + 140;
    parts.push(`<rect x="${cx + 14}" y="${navTop}" width="${cellW - 48}" height="70" rx="14" fill="#10131D" stroke="#252B3A"/>`);
    NAV.forEach((k, j) => {
      const nx = cx + 14 + 12 + j * 34;
      const selected = j === 2;
      if (selected) parts.push(`<circle cx="${nx + 12}" cy="${navTop + 26}" r="20" fill="url(#glow${i})"/>`);
      push(k, nx, navTop + 14, selected ? 24 : 20, shift);
    });
    parts.push(`<rect x="${cx + 14}" y="${navTop + 52}" width="${cellW - 48}" height="4" rx="2" fill="url(#acc${i})" opacity="0.85"/>`);
    parts.push(`<text x="${cx + 18}" y="${top + 250}" fill="#A9B0C2" font-size="12" font-family="${FONT}">seçili sekme: vurgu hâlesi</text>`);
    parts.push(`<text x="${cx + 18}" y="${top + 268}" fill="#727A8F" font-size="12" font-family="${FONT}">ikon tonu vurguya kayar</text>`);
  });
  y += 392;

  // ── C) Doğal vs otomatik
  parts.push(`<rect x="0" y="${y}" width="${W}" height="248" fill="#0B0E15"/>`);
  parts.push(`<text x="${PAD}" y="${y + 38}" fill="#727A8F" font-size="14" font-weight="700" letter-spacing="1.3" font-family="${FONT}">E · AYNI EKRAN, İKİ SEÇENEK — kapatmak için KitsugiAnimeListTheme(iconsFollowAccent = false)</text>`);
  [['Doğal palet (kapalı)', 0, 'mor → pembe → turuncu sabit'], ['Otomatik (varsayılan)', shiftFor('#4ADE80'), 'yeşil vurgu seçilince yeşil ailesi']].forEach(([label, shift, note], i) => {
    const cx = PAD + i * ((W - PAD * 2) / 2);
    parts.push(`<rect x="${cx}" y="${y + 58}" width="${(W - PAD * 2) / 2 - 24}" height="120" rx="16" fill="#10131D" stroke="#252B3A"/>`);
    parts.push(`<text x="${cx + 18}" y="${y + 84}" fill="#F4F7FB" font-size="15" font-weight="700" font-family="${FONT}">${esc(label)}</text>`);
    parts.push(`<text x="${cx + 18}" y="${y + 104}" fill="#727A8F" font-size="12" font-family="${FONT}">${esc(note)}</text>`);
    ['palette', 'download', 'history', 'setting'].forEach((k, j) => push(k, cx + 18 + j * 58, y + 116, 46, shift));
  });
  y += 248;

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${y}">
<defs>
<radialGradient id="shade"><stop offset="0" stop-color="#0B1020" stop-opacity="0.26"/><stop offset="0.6" stop-color="#0B1020" stop-opacity="0.10"/><stop offset="1" stop-color="#0B1020" stop-opacity="0"/></radialGradient>
${defs}
</defs>
${parts.join('\n')}</svg>`;

  const layers = [{ input: Buffer.from(svg), top: 0, left: 0 }];
  let cache = new Map();
  for (const c of comps) {
    const key = `${c.key}|${c.size}|${c.shift}`;
    if (!cache.has(key)) {
      let img = sharp(fs.readFileSync(fileFor(c.key)));
      const deg = ((c.shift % 360) + 360) % 360;
      if (deg) img = img.modulate({ hue: deg });
      cache.set(key, await img.resize(c.size, c.size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer());
    }
    layers.push({ input: cache.get(key), left: c.left, top: c.top });
  }
  await sharp({ create: { width: W, height: y, channels: 4, background: '#080A10' } }).composite(layers).png().toFile(OUT);
  console.log('yazıldı', OUT, W + 'x' + y);
})();
