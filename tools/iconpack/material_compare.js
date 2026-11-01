// Orijinal 3dicons render'ları vs üretilen glifler — malzeme çeşitliliği kanıtı.
// Çıktı: docs/ikon-malzeme-karsilastirma.png
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));
const ROOT = path.join(__dirname, '..', '..');
const RES = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');

const ORIG = [
  ['ic_kitsugi_gym', 'Spor', 'orijinal 3dicons'],
  ['ic_kitsugi_heart', 'Kalp', 'orijinal 3dicons'],
  ['ic_kitsugi_key', 'Anahtar', 'orijinal 3dicons'],
  ['ic_kitsugi_lab', 'Laboratuvar', 'orijinal 3dicons'],
  ['ic_kitsugi_lock', 'Kilit', 'orijinal 3dicons'],
  ['ic_kitsugi_notebook', 'Defter', 'orijinal 3dicons'],
];
const PROD = [
  ['ic_kitsugi_glyph_home', 'Ev', 'mat seramik'],
  ['ic_kitsugi_glyph_close', 'Kapat', 'parlak emaye'],
  ['ic_kitsugi_glyph_chevron_up', 'Yukarı', 'jöle'],
  ['ic_kitsugi_glyph_eye', 'Göster', 'cam + obsidyen'],
  ['ic_kitsugi_glyph_textHash', 'Etiket', 'obsidyen'],
  ['ic_kitsugi_glyph_drag', 'Tutamak', 'mat kauçuk'],
  ['ic_kitsugi_glyph_list', 'Liste', 'ince kâğıt'],
  ['ic_kitsugi_obj_sliders', 'Kaydırıcı', 'emaye + kil düğme'],
  ['ic_kitsugi_glyph_camera', 'Kamera', 'emaye + cam mercek'],
];

(async () => {
  const CELL = 172, LAB = 46;
  const cols = 9;
  const W = cols * CELL + 40;
  const H = 100 + 2 * (CELL + LAB) + 30;
  const comps = [];
  const put = async (file, label, sub, i, row) => {
    const x = 20 + i * CELL;
    const y = 84 + row * (CELL + LAB);
    const buf = await sharp(path.join(RES, `${file}.png`))
      .resize(CELL - 26, CELL - 20, { fit: 'contain', kernel: 'lanczos3' }).toBuffer();
    comps.push({ input: buf, left: x + 13, top: y });
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${CELL}" height="${LAB}">
      <text x="${CELL / 2}" y="20" font-family="Arial" font-size="16" fill="#F4ECFB" text-anchor="middle">${label}</text>
      <text x="${CELL / 2}" y="38" font-family="Arial" font-size="13" fill="#9C90B3" text-anchor="middle">${sub}</text></svg>`;
    comps.push({ input: Buffer.from(svg), left: x, top: y + CELL - 12 });
  };
  for (let i = 0; i < ORIG.length; i++) await put(ORIG[i][0], ORIG[i][1], ORIG[i][2], i, 0);
  for (let i = 0; i < PROD.length; i++) await put(PROD[i][0], PROD[i][1], PROD[i][2], i, 1);

  const head = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="84">
    <text x="20" y="38" font-family="Arial" font-size="27" font-weight="700" fill="#FFFFFF">Her glif kendi malzemesiyle — orijinal 3dicons vs üretilenler</text>
    <text x="20" y="64" font-family="Arial" font-size="15" fill="#9C90B3">Üst sıra: orijinal render'lar. Alt sıra: üretilen glifler — malzeme glif başına değişir (kil · emaye · jöle · cam · obsidyen · kauçuk · kâğıt)</text></svg>`;
  comps.push({ input: Buffer.from(head), left: 0, top: 0 });

  await sharp({ create: { width: W, height: H, channels: 4, background: '#0C0A12' } })
    .composite(comps).png().toFile(path.join(ROOT, 'docs', 'ikon-malzeme-karsilastirma.png'));
  console.log('yazıldı docs/ikon-malzeme-karsilastirma.png');
})();
