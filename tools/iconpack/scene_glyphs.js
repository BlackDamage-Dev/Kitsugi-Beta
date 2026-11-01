// 55 ikonu 3B sahnelerden üretir (scene3d + icons3d).
//
// Çıktı dosya adları DEĞİŞMEZ (uygulama referansları aynı kalır):
//   app/src/main/res/drawable-nodpi/ic_kitsugi_glyph_<ad>.png   (128 px)
//   app/src/main/res/drawable-nodpi/ic_kitsugi_obj_<ad>.png     (192 px)
//
// Her ikon: sahne → 320 px render → kırp → ölçekle → temas gölgesi + saydam tuval.
const fs = require('fs');
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();
const S = require('./scene3d.js');
const I = require('./icons3d.js');
const g3d = require('./glyph3d.js');
const sdf3d = require('./sdf3d.js');

const DST = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const R = 320;            // 3B render çözünürlüğü
const MASK = 512;
const FILL = 1.02;
const WORLD = 24;

// ── harf maskesi → SDF parçası ────────────────────────────────────────────
async function maskPart(spec, hz, r, mat) {
  const uri = g3d.maskSvg(spec.d
    ? { d: spec.d, fill: true, w: spec.w || 2.4 }
    : { text: spec.ch, size: spec.size, weight: spec.weight, italic: !!spec.italic, w: 0.4 }, MASK);
  const raw = await sharp(Buffer.from(uri)).ensureAlpha().raw().toBuffer();
  const alpha = new Uint8Array(MASK * MASK);
  for (let i = 0; i < MASK * MASK; i++) alpha[i] = raw[i * 4 + 3];
  const dist = sdf3d.smooth(sdf3d.signedDistance(alpha, MASK, MASK), MASK, 3);
  const smp = sdf3d.sampler(dist, MASK, WORLD);
  return {
    mat,
    f: (x, y, z) => {
      const d2 = smp(x, y) - r;
      const dz = Math.abs(z) - Math.max(0.02, hz - r);
      return Math.hypot(Math.max(d2, 0), Math.max(dz, 0)) + Math.min(Math.max(d2, dz), 0);
    },
  };
}

// ── tek ikon üret ─────────────────────────────────────────────────────────
async function buildOne(name, scene, canvas) {
  const rgba = S.render(scene, R);
  const raw = await sharp(rgba, { raw: { width: R, height: R, channels: 4 } }).png().toBuffer();
  const trimmed = await sharp(raw).trim({ threshold: 1 }).toBuffer();
  const meta = await sharp(trimmed).metadata();
  const maxDim = Math.max(meta.width, meta.height);
  const box = Math.round(canvas * FILL);
  let w = Math.max(1, Math.round(meta.width * (box / maxDim)));
  let h = Math.max(1, Math.round(meta.height * (box / maxDim)));
  if (Math.max(w, h) > canvas) {
    const k = canvas / Math.max(w, h);
    w = Math.max(1, Math.floor(w * k));
    h = Math.max(1, Math.floor(h * k));
  }
  const body = await sharp(trimmed).resize(w, h, { fit: 'fill', kernel: 'lanczos3' }).ensureAlpha().toBuffer();

  // temas gölgesi: siluetin yumuşak kopyası (orijinallerde ölçülen: α 0.28, merkezde)
  const bodyRaw = await sharp(body).ensureAlpha().raw().toBuffer();
  const gray = Buffer.alloc(w * h);
  for (let i = 0; i < w * h; i++) gray[i] = Math.round(bodyRaw[i * 4 + 3] * 0.34);
  const blurred = await sharp(gray, { raw: { width: w, height: h, channels: 1 } })
    .blur(Math.max(1.1, canvas * 0.018)).raw().toBuffer();
  const shRaw = Buffer.alloc(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    shRaw[i * 4] = 42; shRaw[i * 4 + 1] = 14; shRaw[i * 4 + 2] = 58; shRaw[i * 4 + 3] = blurred[i];
  }
  const shadow = await sharp(shRaw, { raw: { width: w, height: h, channels: 4 } }).png().toBuffer();

  const left = Math.round((canvas - w) / 2);
  const top = Math.round((canvas - h) / 2);
  const out = await sharp({
    create: { width: canvas, height: canvas, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
  }).composite([
    { input: shadow, left, top },
    { input: body, left, top },
  ]).png({ compressionLevel: 9, effort: 10 }).toFile(path.join(DST, name));
  return out;
}

(async () => {
  if (require.main !== module) return;
  fs.mkdirSync(DST, { recursive: true });
  const only = process.argv[2] && !process.argv[2].startsWith('-') ? process.argv[2].split(',') : null;
  const OBJ = new Set(['sync', 'sliders', 'download', 'upload', 'timer', 'code', 'tv', 'tag', 'backup']);
  const list = Object.keys(I.ICONS);
  let made = 0;
  const t0 = Date.now();
  for (const name of list) {
    if (only && !only.includes(name)) continue;
    const scene = I.scene(name);
    const isObj = OBJ.has(name);
    await buildOne(`ic_kitsugi_${isObj ? 'obj' : 'glyph'}_${name}.png`, scene, isObj ? 192 : 128);
    made++;
    console.log(`  ${name} (${Date.now() - t0} ms)`);
  }
  // harf glifleri
  for (const [name, spec] of Object.entries(I.LETTERS)) {
    if (only && !only.includes(name)) continue;
    const part = await maskPart(spec, 1.9, 1.35, 'clay');
    const parts = [part];
    if (spec.strike) parts.push(I.helpers.box(0, 0, 1.5, 7.4, 0.85, 0.55, 0.4, { mat: 'obsidian' }));
    const scene = Object.assign({
      parts, bbox: [-10, 10, -10, 10, -4, 4], tilt: 0.22, exposure: 1.0,
    }, I.sig(name));
    await buildOne(`ic_kitsugi_glyph_${name}.png`, scene, 128);
    made++;
    console.log(`  ${name} (${Date.now() - t0} ms)`);
  }
  // yol tabanlı glif (tırnak): maskeden kalın ekstrüzyon
  for (const [name, spec] of Object.entries(I.TEXT_GLYPHS || {})) {
    if (only && !only.includes(name)) continue;
    const part = await maskPart({ ch: spec.ch, size: spec.size, weight: spec.weight }, 1.6, 0.55, spec.mat || 'clay');
    const scene = Object.assign({
      parts: [part], bbox: [-10, 10, -10, 10, -4, 4], tilt: 0.24,
    }, I.sig(name));
    await buildOne(`ic_kitsugi_glyph_${name}.png`, scene, 128);
    made++;
    console.log(`  ${name} (${Date.now() - t0} ms)`);
  }
  console.log(`${made} ikon üretildi -> ${DST}  (${((Date.now() - t0) / 1000).toFixed(1)} s)`);
})();

module.exports = { buildOne, maskPart };
