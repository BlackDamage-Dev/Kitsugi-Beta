// Orijinal 3dicons render'larını ÖLÇER: her ikon için
//   - siluet / bbox / doluluk
//   - parlaklık merkezi (highlight centroid) ve alanı
//   - gölge vektörü (alpha<0.5 piksellerin merkezi) ve ortalama alfası
//   - kenar karakteri: siluetin hemen içi parlak mı koyu mu (rim ratio)
//   - gradyan yönü (luminansın u,v'ye doğrusal fit açısı)
//   - ortalama doygunluk, hue yayılımı
// Çıktı: docs/orijinal-3b-analizi.md + docs/orijinal-3b-analizi.png
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));

const ROOT = path.join(__dirname, '..', '..');
const RES = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const DOCS = path.join(ROOT, 'docs');

function rgb2hsv(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d > 1e-6) {
    if (mx === r) h = ((g - b) / d) % 6;
    else if (mx === g) h = (b - r) / d + 2;
    else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return [h, mx === 0 ? 0 : d / mx, mx];
}

async function analyze(file) {
  const img = sharp(path.join(RES, file)).ensureAlpha();
  const meta = await img.metadata();
  const { data, info } = await img.raw().toBuffer({ resolveWithObject: true });
  const W = info.width, H = info.height;
  const A = (i) => data[i * 4 + 3];
  const RGB = (i) => [data[i * 4], data[i * 4 + 1], data[i * 4 + 2]];

  let x0 = 1e9, x1 = -1, y0 = 1e9, y1 = -1, n = 0;
  let sx = 0, sy = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    if (A(y * W + x) > 128) {
      n++; sx += x; sy += y;
      if (x < x0) x0 = x; if (x > x1) x1 = x;
      if (y < y0) y0 = y; if (y > y1) y1 = y;
    }
  }
  const bw = Math.max(1, x1 - x0), bh = Math.max(1, y1 - y0);

  // gölge: siluet dışındaki yarı saydam pikseller
  let shx = 0, shy = 0, shn = 0, sha = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const a = A(y * W + x);
    if (a > 8 && a <= 128) { shx += x; shy += y; shn++; sha += a; }
  }
  const shadow = shn > 30
    ? { dx: (shx / shn - sx / n) / bw, dy: (shy / shn - sy / n) / bh, alpha: sha / shn / 255 }
    : { dx: 0, dy: 0, alpha: 0 };

  // parlaklık merkezi (üst %5)
  const lums = [];
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const i = y * W + x;
    if (A(i) > 200) { const [r, g, b] = RGB(i); lums.push([x, y, 0.299 * r + 0.587 * g + 0.114 * b]); }
  }
  lums.sort((p, q) => q[2] - p[2]);
  const topN = Math.max(1, Math.floor(lums.length * 0.05));
  let hx = 0, hy = 0, hpeak = 0;
  for (let i = 0; i < topN; i++) { hx += lums[i][0]; hy += lums[i][1]; hpeak += lums[i][2]; }
  const highlight = { u: (hx / topN - x0) / bw, v: (hy / topN - y0) / bh, lum: hpeak / topN / 255 };

  // gradyan yönü (luminans ~ u,v doğrusal fit)
  let su = 0, sv = 0, sl = 0, suu = 0, svv = 0, suv = 0, sul = 0, svl = 0, m = 0;
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const i = y * W + x;
    if (A(i) > 200) {
      const [r, g, b] = RGB(i); const L = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
      const u = (x - x0) / bw, v = (y - y0) / bh;
      m++; su += u; sv += v; sl += L; suu += u * u; svv += v * v; suv += u * v; sul += u * L; svl += v * L;
    }
  }
  const mu = su / m, mv = sv / m, ml = sl / m;
  const cuu = suu / m - mu * mu, cvv = svv / m - mv * mv, cuv = suv / m - mu * mv;
  const cul = sul / m - mu * ml, cvl = svl / m - mv * ml;
  const det = cuu * cvv - cuv * cuv;
  const bu = (cul * cvv - cvl * cuv) / (det || 1e-9);
  const bv = (cvl * cuu - cul * cuv) / (det || 1e-9);
  const gradAngle = (Math.atan2(bv, bu) * 180) / Math.PI; // 0=sağ, 90=aşağı
  const gradMag = Math.hypot(bu, bv);

  // kenar karakteri: siluetin 2px içi vs ortalama
  let rimSum = 0, rimN = 0, coreSum = 0, coreN = 0;
  for (let y = y0 + 2; y <= y1 - 2; y++) for (let x = x0 + 2; x <= x1 - 2; x++) {
    const i = y * W + x;
    if (A(i) < 200) continue;
    let edge = false;
    for (const [dx, dy] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) if (A((y + dy) * W + x + dx) < 128) edge = true;
    const [r, g, b] = RGB(i); const L = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    if (edge) { rimSum += L; rimN++; } else { coreSum += L; coreN++; }
  }
  const rimRatio = (rimSum / Math.max(1, rimN)) / (coreSum / Math.max(1, coreN));

  // doygunluk
  let satSum = 0, satN = 0, hs = [];
  for (let i = 0; i < W * H; i++) {
    if (A(i) < 200) continue;
    const [r, g, b] = RGB(i); const [h, s] = rgb2hsv(r, g, b);
    satSum += s; satN++; if (s > 0.15) hs.push(h);
  }

  return {
    file, w: W, h: H,
    fill: n / (bw * bh),
    highlight, shadow, rimRatio,
    gradAngle, gradMag,
    sat: satSum / Math.max(1, satN),
    hueSpread: hs.length
      ? hs.reduce((a, h) => { const hh = h < 180 ? h + 360 : h; return [Math.min(a[0], hh), Math.max(a[1], hh)]; }, [999, -999])
          .reduce((a, b) => b - a)
      : 0,
  };
}

(async () => {
  const MINE = process.argv.includes('--mine');
  const manifest = MINE ? (process.argv[3] || path.join(__dirname, 'mine_manifest.json')) : path.join(__dirname, 'orig_manifest.json');
  const orig = JSON.parse(fs.readFileSync(manifest, 'utf8'));
  const rows = [];
  for (const f of orig) rows.push(await analyze(f));

  const med = (arr) => arr.slice().sort((a, b) => a - b)[Math.floor(arr.length / 2)];
  const stat = {
    highlightU: med(rows.map((r) => r.highlight.u)),
    highlightV: med(rows.map((r) => r.highlight.v)),
    highlightLum: med(rows.map((r) => r.highlight.lum)),
    shadowDx: med(rows.map((r) => r.shadow.dx)),
    shadowDy: med(rows.map((r) => r.shadow.dy)),
    shadowAlpha: med(rows.map((r) => r.shadow.alpha)),
    rimRatio: med(rows.map((r) => r.rimRatio)),
    gradAngle: med(rows.map((r) => r.gradAngle)),
    gradMag: med(rows.map((r) => r.gradMag)),
    sat: med(rows.map((r) => r.sat)),
    fill: med(rows.map((r) => r.fill)),
  };

  // markdown
  const md = ['# Orijinal 3dicons render analizi', '',
    `${rows.length} orijinal render ölçüldü (192 px, saydam).`, '',
    '## Ölçülen ortak gramer (medyan)', '',
    '| Özellik | Değer | Anlamı |', '|---|---|---|',
    `| Parlaklık merkezi | u=${stat.highlightU.toFixed(2)}, v=${stat.highlightV.toFixed(2)} | ışık sol-üstten, tepe parlaklığı nesnenin sol-üst %${Math.round(stat.highlightU * 100)}/${Math.round(stat.highlightV * 100)} noktasında |`,
    `| Parlaklık tepe değeri | ${(stat.highlightLum * 100).toFixed(0)}% | tepe nokta tam beyaza yakın değil; yumuşak parlama |`,
    `| Gölge vektörü | dx=${stat.shadowDx.toFixed(3)}, dy=${stat.shadowDy.toFixed(3)} | gölge sağa+aşağı kayıyor (ışık sol-üst) |`,
    `| Gölge alfası | ${(stat.shadowAlpha * 100).toFixed(0)}% | yumuşak, düşük yoğunluklu temas gölgesi |`,
    `| Kenar / çekirdek parlaklık oranı | ${stat.rimRatio.toFixed(2)} | ${stat.rimRatio < 1 ? 'kenar KOYU (parlak halka yok)' : 'kenar parlak'} |`,
    `| Gradyan yönü | ${stat.gradAngle.toFixed(0)}° | ${stat.gradAngle > 0 ? 'soldan sağa + yukarıdan aşağı koyulaşır' : 'ters'} |`,
    `| Gradyan şiddeti | ${stat.gradMag.toFixed(2)} | rampa nesnenin tamamına yayılıyor |`,
    `| Ortalama doygunluk | ${stat.sat.toFixed(2)} | yüksek doygun, canlı renk |`,
    '', '## İkon bazında ölçümler', '',
    '| İkon | Parlaklık (u,v) | Gölge (dx,dy,α) | Kenar/çekirdek | Gradyan | Doygunluk |',
    '|---|---|---|---|---|---|'];
  const styleOf = (r) => {
    const parts = [];
    const u = r.highlight.u, v = r.highlight.v;
    const horiz = u < 0.38 ? 'sol' : u > 0.62 ? 'sağ' : 'orta';
    const vert = v < 0.3 ? 'üst' : v > 0.7 ? 'alt' : 'orta';
    parts.push(`parlama ${horiz}-${vert}`);
    if (r.rimRatio < 0.85) parts.push('belirgin koyu kenar');
    else if (r.rimRatio > 1.08) parts.push('parlak kenar (rim light)');
    else parts.push('nötr ince kenar');
    const a = Math.abs(r.gradAngle);
    if (a > 110) parts.push('dikeye yakın açık→koyu');
    else if (a < 60) parts.push('yatay/köşegen renk akışı');
    else parts.push('dikey açık→koyu');
    parts.push(r.sat > 0.62 ? 'yüksek doygun' : r.sat > 0.5 ? 'orta doygun' : 'yumuşak doygun');
    if (Math.abs(r.shadow.dx) > 0.04 || Math.abs(r.shadow.dy) > 0.06) parts.push('kaydırılmış gölge');
    else parts.push('merkezde yumuşak gölge');
    return parts.join(', ');
  };
  md.push('');
  md.push('## Her orijinalin kişisel 3B imzası (ölçümden türetildi)');
  md.push('');
  for (const r of rows) {
    md.push(`- **${r.file.replace('ic_kitsugi_', '').replace('.png', '')}** — ${styleOf(r)}`);
  }
  for (const r of rows) {
    md.push(`| \`${r.file}\` | ${r.highlight.u.toFixed(2)}, ${r.highlight.v.toFixed(2)} | ${r.shadow.dx.toFixed(2)}, ${r.shadow.dy.toFixed(2)}, ${(r.shadow.alpha * 100).toFixed(0)}% | ${r.rimRatio.toFixed(2)} | ${r.gradAngle.toFixed(0)}° | ${r.sat.toFixed(2)} |`);
  }
  if (!MINE) {
    fs.writeFileSync(path.join(DOCS, 'orijinal-3b-analizi.md'), md.join('\n'), 'utf8');
    fs.writeFileSync(path.join(__dirname, 'orig_stats.json'), JSON.stringify(stat, null, 2));
  } else {
    fs.writeFileSync(path.join(__dirname, 'mine_stats.json'), JSON.stringify(stat, null, 2));
  }
  console.log(JSON.stringify(stat, null, 2));
})();
