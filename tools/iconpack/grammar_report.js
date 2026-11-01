// Ölçülen orijinal gramer vs benim setim — karşılaştırma tablosu + görsel.
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));
const ROOT = path.join(__dirname, '..', '..');
const orig = JSON.parse(fs.readFileSync(path.join(__dirname, 'orig_stats.json'), 'utf8'));
const mine = JSON.parse(fs.readFileSync(path.join(__dirname, 'mine_stats.json'), 'utf8'));

const ROWS = [
  ['Parlaklık merkezi (u)', orig.highlightU, mine.highlightU, 2],
  ['Parlaklık merkezi (v)', orig.highlightV, mine.highlightV, 2],
  ['Tepe parlaklık', orig.highlightLum, mine.highlightLum, 2],
  ['Gölge dx', orig.shadowDx, mine.shadowDx, 2],
  ['Gölge dy', orig.shadowDy, mine.shadowDy, 2],
  ['Gölge alfası', orig.shadowAlpha, mine.shadowAlpha, 2],
  ['Kenar / çekirdek', orig.rimRatio, mine.rimRatio, 2],
  ['Gradyan şiddeti', orig.gradMag, mine.gradMag, 2],
  ['Doygunluk', orig.sat, mine.sat, 2],
];

(async () => {
  const W = 1180, ROW = 46, HEAD = 210;
  const H = HEAD + ROWS.length * ROW + 40;
  const parts = [];
  const head = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${HEAD}">
    <text x="40" y="58" font-family="Arial" font-size="30" font-weight="700" fill="#fff">Ölçülen gramer — orijinal 3dicons vs üretilen set</text>
    <text x="40" y="92" font-family="Arial" font-size="16" fill="#9C90B3">95 orijinal render piksel bazında ölçüldü; aynı metrikler kendi 55 glifimde de ölçüldü. Hedef: medyanları eşitlemek.</text>
    <line x1="40" y1="${HEAD - 24}" x2="${W - 40}" y2="${HEAD - 24}" stroke="#2A2236"/>
    <text x="40" y="${HEAD - 4}" font-family="Arial" font-size="15" fill="#7C6E92">ölçüm</text>
    <text x="520" y="${HEAD - 4}" font-family="Arial" font-size="15" fill="#7C6E92">orijinal</text>
    <text x="700" y="${HEAD - 4}" font-family="Arial" font-size="15" fill="#7C6E92">üretilen (v10)</text>
    <text x="900" y="${HEAD - 4}" font-family="Arial" font-size="15" fill="#7C6E92">fark</text>
  </svg>`;
  parts.push({ input: Buffer.from(head), left: 0, top: 0 });
  let y = HEAD;
  for (const [label, o, m, dec] of ROWS) {
    const d = m - o;
    const ok = Math.abs(d) <= 0.06;
    const barO = Math.min(1, Math.abs(o)) * 160;
    const barM = Math.min(1, Math.abs(m)) * 160;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${ROW}">
      <text x="40" y="30" font-family="Arial" font-size="17" fill="#E8E0F2">${label}</text>
      <rect x="520" y="14" width="${barO.toFixed(1)}" height="9" rx="4" fill="#5B4A78"/>
      <text x="700" y="30" font-family="Arial" font-size="17" fill="#B9A9D2">${o.toFixed(dec)}</text>
      <rect x="820" y="14" width="${barM.toFixed(1)}" height="9" rx="4" fill="#C15FE0"/>
      <text x="1000" y="30" font-family="Arial" font-size="17" fill="#F2E6FF">${m.toFixed(dec)}</text>
      <text x="1108" y="30" font-family="Arial" font-size="16" fill="${ok ? '#5FD08A' : '#F0A35E'}">${d >= 0 ? '+' : ''}${d.toFixed(2)}</text>
    </svg>`;
    parts.push({ input: Buffer.from(svg), left: 0, top: y });
    y += ROW;
  }
  await sharp({ create: { width: W, height: H, channels: 4, background: '#0C0A12' } })
    .composite(parts).png().toFile(path.join(ROOT, 'docs', 'ikon-gramer-karsilastirma.png'));
  console.log('yazıldı docs/ikon-gramer-karsilastirma.png');
})();
