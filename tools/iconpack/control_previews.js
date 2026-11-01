// Kontrol gliflerinin GERÇEK ekran bağlamında önizlemesi (docs/ikon-kontrol-baglam.png)
// ve dp bazlı okunabilirlik testi (docs/ikon-kontrol-boyut.png).
// Uygulamadaki gerçek PNG'leri kullanır; hiçbir şey "yaklaşık" çizilmez.
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));

const ROOT = path.join(__dirname, '..', '..');
const RES = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const FONT = 'DejaVu Sans, sans-serif';

const asset = (n) => {
  const glyph = path.join(RES, `ic_kitsugi_glyph_${n}.png`);
  if (fs.existsSync(glyph)) return glyph;
  return path.join(RES, `ic_kitsugi_${n.replace(/-/g, '_')}.png`);
};

const bufCache = new Map();
async function iconBuf(rel, px) {
  const key = `${rel}@${px}`;
  if (bufCache.has(key)) return bufCache.get(key);
  const b = await sharp(rel).resize(px, px, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
  bufCache.set(key, b);
  return b;
}

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function text(x, y, s, o = {}) {
  const { size = 15, fill = '#E7EAF2', weight = 400, anchor = 'start', ls = 0 } = o;
  return `<text x="${x}" y="${y}" fill="${fill}" font-size="${size}" font-weight="${weight}" font-family="${FONT}" text-anchor="${anchor}" letter-spacing="${ls}">${esc(s)}</text>`;
}
function card(x, y, w, h, r = 20, fill = '#10131D', stroke = '#242B3B') {
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${r}" fill="${fill}" stroke="${stroke}"/>`;
}
function chip(x, y, w, h, label, active) {
  const fill = active ? 'url(#chipOn)' : '#161B26';
  const stroke = active ? 'none' : '#2A3244';
  const col = active ? '#0B0E14' : '#C3CAD8';
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${h / 2}" fill="${fill}" stroke="${stroke}"/>` +
    text(x + w / 2, y + h / 2 + 5, label, { size: 13, weight: 700, fill: col, anchor: 'middle' });
}

async function compose({ W, H, svg, items, out }) {
  await sharp({ create: { width: W, height: H, channels: 4, background: '#080A10' } })
    .composite([{ input: Buffer.from(svg), top: 0, left: 0 }, ...items]).png().toFile(out);
}

(async () => {
  // ───────────────────────────────────────────────────────────────
  // 1) Bağlam önizlemesi
  // ───────────────────────────────────────────────────────────────
  const W = 1480, PAD = 48;
  const p = [];
  const it = [];
  p.push('<defs><linearGradient id="chipOn" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#B78CF7"/><stop offset="1" stop-color="#F17FB2"/></linearGradient></defs>');

  async function icon(name, px, x, y) {
    const b = await iconBuf(asset(name), px);
    it.push({ input: b, left: Math.round(x - px / 2), top: Math.round(y - px / 2) });
  }

  p.push(text(PAD, 62, 'Kontrol glifleri — gerçek ekran bağlamı', { size: 31, weight: 700, fill: '#F6F8FC' }));
  p.push(text(PAD, 94, 'Her tıklanabilir alan, her buton, her aç/kapa oku 3B setin parçası. Görseller uygulamanın drawable-nodpi/ic_kitsugi_*.png dosyaları.', { size: 14, fill: '#98A1B5' }));

  // ---- Panel A ----
  const AX = PAD, AY = 124, AW = 690, AH = 608;
  p.push(card(AX, AY, AW, AH));
  p.push(text(AX + 24, AY + 34, 'Ayarlar · liste', { size: 14, weight: 700, fill: '#7E8AA0', ls: 1.4 }));

  await icon('arrow_left', 44, AX + 44, AY + 84);
  p.push(text(AX + 82, AY + 91, 'Ayarlar', { size: 20, weight: 700 }));
  await icon('more', 40, AX + AW - 44, AY + 84);
  p.push(`<line x1="${AX + 20}" y1="${AY + 120}" x2="${AX + AW - 20}" y2="${AY + 120}" stroke="#1D2331"/>`);

  p.push(`<rect x="${AX + 20}" y="${AY + 136}" width="${AW - 40}" height="52" rx="26" fill="#131826" stroke="#232B3C"/>`);
  await icon('zoom', 30, AX + 52, AY + 162);
  p.push(text(AX + 78, AY + 168, 'Ayarlarda ara', { size: 15, fill: '#7E8AA0' }));
  await icon('close', 30, AX + AW - 54, AY + 162);

  const rows = [
    ['setting', 'Görünüm ve renk', 'Vurgu, tema, gradyan'],
    ['video_camera', 'Oynatıcı', 'Kodek, altyazı, hız'],
    ['wifi', 'Ağ ve eşitleme', 'Bağlantı, önbellek'],
    ['bell', 'Bildirimler', 'Yeni bölüm, hatırlatma'],
    ['lock', 'Gizlilik', 'Kilit, ekran görüntüsü'],
  ];
  let ry = AY + 212;
  for (const [ic, title, sub] of rows) {
    await icon(ic, 46, AX + 50, ry + 26);
    p.push(text(AX + 86, ry + 20, title, { size: 16, weight: 700 }));
    p.push(text(AX + 86, ry + 41, sub, { size: 12.5, fill: '#8A93A6' }));
    await icon('chevron_right', 30, AX + AW - 46, ry + 26);
    ry += 66;
  }

  // ---- Panel B ----
  const BX = PAD + AW + 32, BY = AY, BW = W - BX - PAD, BH = 608;
  p.push(card(BX, BY, BW, BH));
  p.push(text(BX + 24, BY + 34, 'Filtre · sıralama · görünüm', { size: 14, weight: 700, fill: '#7E8AA0', ls: 1.4 }));

  await icon('arrow_left', 42, BX + 44, BY + 84);
  p.push(text(BX + 80, BY + 91, 'Filtrele', { size: 20, weight: 700 }));
  await icon('close', 34, BX + BW - 44, BY + 84);

  // filtre satırı
  p.push(text(BX + 24, BY + 142, 'TÜR', { size: 12, weight: 700, fill: '#7E8AA0', ls: 1.2 }));
  await icon('funnel', 26, BX + BW - 122, BY + 137);
  await icon('sort', 26, BX + BW - 68, BY + 137);
  const chips = ['Aksiyon', 'Fantastik', 'Drama', 'Komedi'];
  let cx = BX + 24;
  chips.forEach((c, i) => { const w = 32 + c.length * 8; p.push(chip(cx, BY + 154, w, 38, c, i < 2)); cx += w + 10; });

  // görünüm modu
  p.push(text(BX + 24, BY + 234, 'GÖRÜNÜM', { size: 12, weight: 700, fill: '#7E8AA0', ls: 1.2 }));
  const segX = BX + 24, segY = BY + 246, segW = 268, segH = 46;
  p.push(`<rect x="${segX}" y="${segY}" width="${segW}" height="${segH}" rx="23" fill="#131826" stroke="#232B3C"/>`);
  p.push(`<rect x="${segX + 3}" y="${segY + 3}" width="${segW / 4 - 2}" height="${segH - 6}" rx="20" fill="url(#chipOn)"/>`);
  const modes = ['grid', 'list', 'stream', 'carousel'];
  for (let i = 0; i < 4; i++) await icon(modes[i], 28, segX + segW / 8 + i * (segW / 4), segY + segH / 2);
  await icon('doubleArrow', 26, segX + segW + 42, segY + segH / 2);
  await icon('fullscreen', 26, segX + segW + 92, segY + segH / 2);

  // açılır bölümler
  p.push(text(BX + 24, BY + 330, 'AÇILIR BÖLÜMLER', { size: 12, weight: 700, fill: '#7E8AA0', ls: 1.2 }));
  let ey = BY + 344;
  for (const [label, chevron] of [['Çözünürlük', 'chevron_up'], ['Önbellek', 'chevron_down']]) {
    p.push(`<rect x="${BX + 20}" y="${ey}" width="${BW - 40}" height="44" rx="14" fill="#131826" stroke="#232B3C"/>`);
    p.push(text(BX + 40, ey + 28, label, { size: 15, weight: 700 }));
    await icon(chevron, 26, BX + BW - 46, ey + 22);
    ey += 52;
  }
  p.push(text(BX + 40, ey + 8, 'Etkin: 1080p · 60 fps', { size: 12.5, fill: '#8A93A6' }));

  // metin araç çubuğu
  const tbY = ey + 30;
  p.push(text(BX + 24, tbY + 16, 'METİN ARAÇ ÇUBUĞU', { size: 12, weight: 700, fill: '#7E8AA0', ls: 1.2 }));
  const tb = ['textBold', 'textItalic', 'textTitle', 'textStrike', 'textHash', 'textQuote', 'alignLeft', 'alignCenter', 'alignJustify'];
  let tx = BX + 26;
  for (const t of tb) { await icon(t, 30, tx + 14, tbY + 48); tx += 42; }

  // adet kontrolü: minus / plus
  const stY = tbY + 78;
  p.push(`<rect x="${BX + 24}" y="${stY - 20}" width="196" height="44" rx="22" fill="#131826" stroke="#232B3C"/>`);
  await icon('minus', 26, BX + 52, stY + 2);
  p.push(text(BX + 100, stY + 9, '2', { size: 17, weight: 700, anchor: 'middle' }));
  await icon('plus', 28, BX + 168, stY + 2);

  // ---- alt şerit ----
  const SY = AY + AH + 28, SH = 118;
  p.push(card(PAD, SY, W - PAD * 2, SH));
  p.push(text(PAD + 24, SY + 32, 'Düzenleme ve seçim', { size: 14, weight: 700, fill: '#7E8AA0', ls: 1.4 }));
  const edits = ['crop', 'rotate', 'aspect', 'blur', 'border', 'selectAll', 'drag', 'density', 'keyboard', 'input', 'external', 'home', 'leaf', 'snow', 'cake', 'camera', 'eye', 'eyeOff'];
  let ex = PAD + 30;
  for (const e of edits) { await icon(e, 40, ex + 20, SY + 76); ex += 58; }

  const H = SY + SH + 86;
  p.push(text(PAD, SY + SH + 44, `3dicons front/gradient (CC0) + aynı cam/gradyan dilinde üretilen ${fs.readdirSync(RES).filter((f) => f.startsWith('ic_kitsugi_glyph_')).length} kontrol glifi · saydam PNG · arka plan plakası yok`, { size: 13, fill: '#6C7488' }));

  await compose({ W, H, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">${p.join('\n')}</svg>`, items: it, out: path.join(ROOT, 'docs', 'ikon-kontrol-baglam.png') });

  // ───────────────────────────────────────────────────────────────
  // 2) Boyut testi: 12 / 14 / 16 / 18 / 20 / 24 / 30 dp (xxhdpi ×3)
  // ───────────────────────────────────────────────────────────────
  const sizes = [12, 14, 16, 18, 20, 24, 30];
  const DENS = 3;
  const names = ['close', 'chevron_down', 'chevron_right', 'arrow_left', 'funnel', 'sort', 'grid', 'eye', 'minus', 'more'];
  const CW = 148, CH = 124;
  const NOFF = 108;
  const SW = NOFF + names.length * CW + 36, SHt = 130 + sizes.length * CH + 30;
  const sp = [];
  const sit = [];
  sp.push(`<rect width="${SW}" height="${SHt}" fill="#05070C"/>`);
  sp.push(text(40, 52, 'Kontrol glifleri — boyut okunabilirliği', { size: 28, weight: 700, fill: '#F6F8FC' }));
  sp.push(text(40, 80, 'Satırlar dp, değerler gerçek piksel (xxhdpi ×3). 14–18 dp aralığı ana kullanım alanı.', { size: 13, fill: '#98A1B5' }));
  for (let c = 0; c < names.length; c++) sp.push(text(NOFF + c * CW + CW / 2, 112, names[c], { size: 12, fill: '#7E8AA0', anchor: 'middle' }));
  for (let r = 0; r < sizes.length; r++) {
    const dp = sizes[r];
    const px = Math.round(dp * DENS);
    const rowY = 130 + r * CH;
    sp.push(`<rect x="24" y="${rowY + 4}" width="${SW - 48}" height="${CH - 10}" rx="16" fill="${r % 2 ? '#0B0F18' : '#10141E'}"/>`);
    sp.push(text(48, rowY + CH / 2 + 2, `${dp} dp`, { size: 15, weight: 700, fill: '#C3CAD8' }));
    for (let c = 0; c < names.length; c++) {
      const b = await iconBuf(asset(names[c]), px);
      sit.push({ input: b, left: Math.round(NOFF + c * CW + CW / 2 - px / 2), top: Math.round(rowY + CH / 2 - px / 2) });
    }
  }
  await compose({ W: SW, H: SHt, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${SW}" height="${SHt}">${sp.join('\n')}</svg>`, items: sit, out: path.join(ROOT, 'docs', 'ikon-kontrol-boyut.png') });

  // ───────────────────────────────────────────────────────────────
  // 3) Tam sayfa: 46 glif, adlarıyla (8 sütun)
  // ───────────────────────────────────────────────────────────────
  const glyphs = fs.readdirSync(RES).filter((f) => f.startsWith('ic_kitsugi_glyph_')).sort();
  const COLS = 8, CELL = 156, GAP = 10;
  const gr = Math.ceil(glyphs.length / COLS);
  const PW = 48 + COLS * (CELL + GAP) - GAP + 48;
  const PH = 150 + gr * (CELL + GAP) - GAP + 78;
  const gp = [];
  const gi = [];
  gp.push(`<rect width="${PW}" height="${PH}" fill="#080A10"/>`);
  gp.push(text(48, 62, 'Kontrol glifleri — tam set', { size: 30, weight: 700, fill: '#F6F8FC' }));
  gp.push(text(48, 92, `${glyphs.length} glif · 128×128 saydam PNG · 3dicons front/gradient paletiyle üretildi · tools/iconpack/control_glyphs.js`, { size: 13, fill: '#98A1B5' }));
  for (let i = 0; i < glyphs.length; i++) {
    const cx = 48 + (i % COLS) * (CELL + GAP);
    const cy = 130 + Math.floor(i / COLS) * (CELL + GAP);
    gp.push(`<rect x="${cx}" y="${cy}" width="${CELL}" height="${CELL}" rx="18" fill="#10131D" stroke="#1E2532"/>`);
    const b = await iconBuf(path.join(RES, glyphs[i]), 86);
    gi.push({ input: b, left: cx + (CELL - 86) / 2, top: cy + 22 });
    const nm = glyphs[i].replace('ic_kitsugi_glyph_', '').replace('.png', '');
    gp.push(text(cx + CELL / 2, cy + CELL - 18, nm, { size: 11.5, fill: '#7E8AA0', anchor: 'middle' }));
  }
  gp.push(text(48, PH - 34, 'Aynı ışık, aynı gölge, aynı gradyan yönü — konu ikonlarıyla birlikte tek aile.', { size: 13, fill: '#6C7488' }));
  await compose({ W: PW, H: PH, svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${PW}" height="${PH}">${gp.join('\n')}</svg>`, items: gi, out: path.join(ROOT, 'docs', 'ikon-kontrol-sayfasi.png') });

  console.log('yazıldı docs/ikon-kontrol-baglam.png + docs/ikon-kontrol-boyut.png + docs/ikon-kontrol-sayfasi.png');
})();
