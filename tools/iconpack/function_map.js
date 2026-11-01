// İşlev haritası: her 3B varlık hangi işlevi temsil ediyor, kaç Material ikon adı
// ona bağlanıyor? Kaynak: KitsugiIconAssets.kt (NAME_MAP + enum).
// Çıktı: docs/ikon-islev-haritasi.png
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));

const ROOT = path.join(__dirname, '..', '..');
const KT = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'kitsugi', 'animelist', 'ui', 'icons', 'KitsugiIconAssets.kt');
const RES = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const FONT = 'DejaVu Sans, sans-serif';

const src = fs.readFileSync(KT, 'utf8');

// enum: sanat -> drawable
const art2res = {};
for (const m of src.matchAll(/^ {4}([A-Z][A-Za-z0-9]*)\(\s*R\.drawable\.([A-Za-z0-9_]+)/gm)) art2res[m[1]] = m[2];

// NAME_MAP: yorum etiketleri + eşlemeler
const block = src.slice(src.indexOf('private val NAME_MAP'), src.indexOf('fun fromVector'));
const groups = [];
let cur = null;
for (const line of block.split('\n')) {
  const c = line.match(/^\s*\/\/\s*(.+?)\s*$/);
  if (c) { cur = { label: c[1], art: null, names: [] }; groups.push(cur); continue; }
  for (const p of line.matchAll(/"([A-Za-z0-9]+)" to KitsugiArt\.([A-Za-z0-9]+)/g)) {
    if (!cur) { cur = { label: p[2], art: null, names: [] }; groups.push(cur); }
    cur.names.push(p[1]);
    if (!cur.art) cur.art = p[2];
  }
}

// Aynı varlığa birden fazla grup düşerse birleştir
const byArt = new Map();
for (const g of groups) {
  if (!g.art) continue;
  if (!byArt.has(g.art)) byArt.set(g.art, { art: g.art, label: g.label, names: [] });
  byArt.get(g.art).names.push(...g.names);
}
const rows = [...byArt.values()].sort((a, b) => b.names.length - a.names.length);

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const text = (x, y, s, o = {}) => {
  const { size = 15, fill = '#E7EAF2', weight = 400, anchor = 'start', ls = 0 } = o;
  return `<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-weight="${weight}" font-family="${FONT}" text-anchor="${anchor}" letter-spacing="${ls}">${esc(s)}</text>`;
};

(async () => {
  const COLS = 5, CW = 272, CH = 132, PAD = 44;
  const R = Math.ceil(rows.length / COLS);
  const W = PAD * 2 + COLS * CW + (COLS - 1) * 12;
  const H = 150 + R * (CH + 12) + 90;
  const parts = [];
  const items = [];
  parts.push(`<rect width="${W}" height="${H}" fill="#080A10"/>`);
  parts.push(text(PAD, 62, 'İkon ↔ işlev haritası', { size: 31, weight: 700, fill: '#F6F8FC' }));
  parts.push(text(PAD, 94, `${rows.length} 3B varlık · ${rows.reduce((s, r) => s + r.names.length, 0)} Material ikon adı · her ad kendi işlevine en yakın görsele bağlı`, { size: 14, fill: '#98A1B5' }));

  for (let i = 0; i < rows.length; i++) {
    const r = rows[i];
    const x = PAD + (i % COLS) * (CW + 12);
    const y = 128 + Math.floor(i / COLS) * (CH + 12);
    parts.push(`<rect x="${x}" y="${y}" width="${CW}" height="${CH}" rx="16" fill="#10131D" stroke="#232B3C"/>`);
    const res = art2res[r.art] ? path.join(RES, art2res[r.art] + '.png') : null;
    if (res && fs.existsSync(res)) {
      const b = await sharp(res).resize(72, 72, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
      items.push({ input: b, left: x + 14, top: y + 22 });
    }
    parts.push(text(x + 98, y + 34, r.art, { size: 15, weight: 700, fill: '#F2F5FA' }));
    parts.push(text(x + 98, y + 56, r.label.length > 30 ? r.label.slice(0, 29) + '…' : r.label, { size: 11.5, fill: '#B78CF7' }));
    const sample = r.names.slice(0, 3).join(', ');
    parts.push(text(x + 98, y + 78, sample.length > 34 ? sample.slice(0, 33) + '…' : sample, { size: 11, fill: '#8A93A6' }));
    parts.push(text(x + 98, y + 100, `+${Math.max(0, r.names.length - 3)} ad`, { size: 11, fill: '#5F6A80' }));
  }
  parts.push(text(PAD, H - 34, 'Kaynak: KitsugiIconAssets.kt NAME_MAP · yeniden üret: node tools/iconpack/function_map.js', { size: 12, fill: '#5F6A80' }));

  await sharp({ create: { width: W, height: H, channels: 4, background: '#080A10' } })
    .composite([{ input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${parts.join('\n')}</svg>`), top: 0, left: 0 }, ...items])
    .png().toFile(path.join(ROOT, 'docs', 'ikon-islev-haritasi.png'));

  const dupes = rows.filter((r) => r.names.length > 12).map((r) => `${r.art}: ${r.names.length}`);
  console.log(`yazıldı docs/ikon-islev-haritasi.png | ${rows.length} varlık | en kalabalık: ${dupes.slice(0, 6).join(', ')}`);
})();
