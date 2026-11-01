// Denetim sayfası: verilen önekteki tüm PNG'leri büyük boyutta etiketli dizer.
// kullanım: node tools/iconpack/audit_sheet.js <çıktı.png> [önek]
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));
const DIR = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');

(async () => {
  const out = process.argv[2] || '/home/user/audit.png';
  const pref = process.argv[3] || 'ic_kitsugi_glyph_';
  const files = fs.readdirSync(DIR).filter((f) => f.startsWith(pref) && f.endsWith('.png')).sort();
  const C = 200, LAB = 26, COLS = 6;
  const ROWS = Math.ceil(files.length / COLS);
  const W = COLS * C, H = ROWS * (C + LAB);
  const comps = [];
  for (let i = 0; i < files.length; i++) {
    const name = files[i].replace(pref, '').replace('.png', '');
    const buf = await sharp(path.join(DIR, files[i]))
      .resize(C - 16, C - 16, { fit: 'contain', kernel: 'lanczos3' }).toBuffer();
    const col = i % COLS, row = Math.floor(i / COLS);
    comps.push({ input: buf, left: col * C + 8, top: row * (C + LAB) + 8 });
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${C}" height="${LAB}"><text x="${C / 2}" y="18" font-family="Arial" font-size="15" fill="#cfc7d8" text-anchor="middle">${name}</text></svg>`;
    comps.push({ input: Buffer.from(svg), left: col * C, top: row * (C + LAB) + C - 4 });
  }
  await sharp({ create: { width: W, height: H, channels: 4, background: { r: 12, g: 10, b: 16, alpha: 1 } } })
    .composite(comps).png().toFile(out);
  console.log(`${files.length} glif -> ${out}`);
})();
