// Hızlı gramer denetimi: örnek glifleri 192 px render edip orijinal ölçümleriyle
// karşılaştırır. Hedef: orijinal medyanlarına yakınsamak.
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));
const { renderGlyph } = require(path.join(__dirname, 'render_glyph.js'));
const { GLYPHS, LETTERS } = require(path.join(__dirname, 'control_glyphs.js'));
const { OBJECTS } = require(path.join(__dirname, 'object_glyphs.js'));

const SAMPLE = ['home', 'close', 'chevron_up', 'eye', 'textHash', 'drag', 'list', 'camera', 'funnel'];

function rgb2hsv(r, g, b) {
  r /= 255; g /= 255; b /= 255;
  const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
  let h = 0;
  if (d > 1e-6) {
    if (mx === r) h = ((g - b) / d) % 6; else if (mx === g) h = (b - r) / d + 2; else h = (r - g) / d + 4;
    h *= 60; if (h < 0) h += 360;
  }
  return [h, mx === 0 ? 0 : d / mx, mx];
}

function metrics(data, W, H) {
  const A = (i) => data[i * 4 + 3];
  let x0 = 1e9, x1 = -1, y0 = 1e9, y1 = -1, sx = 0, sy = 0, n = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (A(y * W + x) > 128) {
    n++; sx += x; sy += y;
    if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
  }
  const bw = Math.max(1, x1 - x0), bh = Math.max(1, y1 - y0);
  let shx = 0, shy = 0, shn = 0, sha = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const a = A(y * W + x);
    if (a > 8 && a <= 128) { shx += x; shy += y; shn++; sha += a; }
  }
  const shadow = shn > 30 ? { dx: (shx / shn - sx / n) / bw, dy: (shy / shn - sy / n) / bh, alpha: sha / shn / 255 } : { dx: 0, dy: 0, alpha: 0 };
  const lums = [];
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    const i = y * W + x;
    if (A(i) > 200) { const [r, g, b] = [data[i * 4], data[i * 4 + 1], data[i * 4 + 2]]; lums.push([x, y, 0.299 * r + 0.587 * g + 0.114 * b]); }
  }
  if (!lums.length) return null;
  lums.sort((p, q) => q[2] - p[2]);
  const topN = Math.max(1, Math.floor(lums.length * 0.05));
  let hx = 0, hy = 0, hl = 0;
  for (let i = 0; i < topN; i++) { hx += lums[i][0]; hy += lums[i][1]; hl += lums[i][2]; }
  let rS = 0, rN = 0, cS = 0, cN = 0, satS = 0, satN = 0;
  for (let y = y0 + 2; y <= y1 - 2; y++) for (let x = x0 + 2; x <= x1 - 2; x++) {
    const i = y * W + x;
    if (A(i) < 200) continue;
    let edge = false;
    for (const [dx, dy] of [[2, 0], [-2, 0], [0, 2], [0, -2]]) if (A((y + dy) * W + x + dx) < 128) edge = true;
    const r = data[i * 4], g = data[i * 4 + 1], b = data[i * 4 + 2];
    const L = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
    if (edge) { rS += L; rN++; } else { cS += L; cN++; }
    const [, s] = rgb2hsv(r, g, b); satS += s; satN++;
  }
  return {
    u: (hx / topN - x0) / bw, v: (hy / topN - y0) / bh, lum: hl / topN / 255,
    shadow, rim: (rS / Math.max(1, rN)) / (cS / Math.max(1, cN)), sat: satS / Math.max(1, satN),
    fill: n / (bw * bh),
  };
}

(async () => {
  const out = [];
  for (const name of SAMPLE) {
    const spec = GLYPHS[name];
    const jit = { phase: 0.16, span: 0.86 };
    const img = await renderGlyph({ ...spec, rot: 0, ...jit }, { render: 384, mask: 768 });
    const trimmed = await sharp(img).trim({ threshold: 1 }).png().toBuffer();
    const m = await sharp(trimmed).resize(192, 192, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
    const mm = metrics(m.data, m.info.width, m.info.height);
    if (mm) out.push({ name, ...mm }); else console.log('atlandı:', name);
  }
  const med = (a) => a.slice().sort((p, q) => p - q)[Math.floor(a.length / 2)];
  const mine = {
    highlightU: med(out.map((r) => r.u)), highlightV: med(out.map((r) => r.v)),
    highlightLum: med(out.map((r) => r.lum)),
    shadowDx: med(out.map((r) => r.shadow.dx)), shadowDy: med(out.map((r) => r.shadow.dy)),
    shadowAlpha: med(out.map((r) => r.shadow.alpha)),
    rimRatio: med(out.map((r) => r.rim)), sat: med(out.map((r) => r.sat)),
    fill: med(out.map((r) => r.fill)),
  };
  const target = JSON.parse(fs.readFileSync(path.join(__dirname, 'orig_stats.json'), 'utf8'));
  const rows = [];
  rows.push(['parlaklık u', mine.highlightU.toFixed(2), target.highlightU.toFixed(2)]);
  rows.push(['parlaklık v', mine.highlightV.toFixed(2), target.highlightV.toFixed(2)]);
  rows.push(['tepe parlaklık', mine.highlightLum.toFixed(2), target.highlightLum.toFixed(2)]);
  rows.push(['gölge dx', mine.shadowDx.toFixed(2), target.shadowDx.toFixed(2)]);
  rows.push(['gölge dy', mine.shadowDy.toFixed(2), target.shadowDy.toFixed(2)]);
  rows.push(['gölge alfa', mine.shadowAlpha.toFixed(2), target.shadowAlpha.toFixed(2)]);
  rows.push(['kenar/çekirdek', mine.rimRatio.toFixed(2), target.rimRatio.toFixed(2)]);
  rows.push(['doygunluk', mine.sat.toFixed(2), target.sat.toFixed(2)]);
  console.log('ölçüm            benim  hedef  fark');
  for (const [k, a, b] of rows) {
    const d = (parseFloat(a) - parseFloat(b)).toFixed(2);
    console.log(`${k.padEnd(16)} ${a}  ${b}  ${d}`);
  }
})();
