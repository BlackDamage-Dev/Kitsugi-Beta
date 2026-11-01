// Nesne glifleri — 3dicons paketinde karşılığı olmayan ama uygulamada farklı
// işlevleri temsil eden kavramlar (eşitleme, indirme/yükleme, zamanlayıcı, kod,
// TV, etiket, ayar kaydırıcıları). Aynı 3B hattı (glyph3d.js) kullanır; böylece
// konu ikonlarıyla aynı hacim/ışık dilinde durur.
//
// Çıktı: app/src/main/res/drawable-nodpi/ic_kitsugi_obj_<ad>.png (192×192, saydam)
const fs = require('fs');
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();
const g3d = require(path.join(__dirname, 'glyph3d.js'));
const sdf3d = require(path.join(__dirname, 'sdf3d.js'));
const { renderGlyph } = require(path.join(__dirname, 'render_glyph.js'));

const DST = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const RENDER = 512;      // 3B render çözünürlüğü
const MASK = 768;        // siluet maskesi çözünürlüğü
const CANVAS = 192;
const FILL = 0.94;
const ALPHA_FLOOR = 26;

const OBJECTS = {
  // iki yay + oklar: eşitleme / yenileme
  sync: {
    // dairesel ok: açık yay + ok başı (tek parça emaye)
    d: 'M18.6 12.4 A6.2 6.2 0 1 1 12.4 6.2 M12.4 6.2 L15.9 3.6 M12.4 6.2 L15.6 9.2',
    w: 2.3, mat: 'gloss',
  },
  // üç kaydırıcı: ince ayar / parametreler
  sliders: {
    d: 'M3.8 7.4 H20.2 M3.8 12 H20.2 M3.8 16.6 H20.2',
    detail: 'M9.2 5.6 a1.85 1.85 0 1 1 -0.01 0 Z M15 10.2 a1.85 1.85 0 1 1 -0.01 0 Z ' +
            'M7.6 14.8 a1.85 1.85 0 1 1 -0.01 0 Z',
    w: 2.6, depth: 11, mat: 'gloss',
    parts: [
      { d: 'M6.4 6.4 a1.7 1.7 0 0 1 3.4 0 a1.7 1.7 0 0 1 -3.4 0 Z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.8, radius: 0.38 },
      { d: 'M12.2 11 a1.7 1.7 0 0 1 3.4 0 a1.7 1.7 0 0 1 -3.4 0 Z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.8, radius: 0.38 },
      { d: 'M4.8 15.6 a1.7 1.7 0 0 1 3.4 0 a1.7 1.7 0 0 1 -3.4 0 Z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.8, radius: 0.38 },
    ],
  },
  // tepsiye inen ok
  download: {
    d: 'M12 3.9 V14.3 M7.5 10.2 L12 14.7 L16.5 10.2',
    w: 2.9, depth: 11, mat: 'gel',
    parts: [{ d: 'M4.4 18.6 H19.6', w: 2.6, mat: 'chrome', halfT: 0.7, radius: 0.3 }],
  },
  // tepsiye çıkan ok
  upload: {
    d: 'M12 15.1 V4.6 M7.5 9 L12 4.5 L16.5 9',
    w: 2.9, depth: 11, mat: 'paper',
    parts: [{ d: 'M4.4 18.6 H19.6', w: 2.6, mat: 'gloss', halfT: 0.8, radius: 0.35 }],
  },
  // kronometre: gövde + tepe düğmesi + ibre
  timer: {
    d: 'M12 8.0 A5.5 5.5 0 1 1 11.99 8.0 Z',
    detail: 'M12 13.2 V10.2',
    w: 2.55, mat: 'clay',
    parts: [
      { d: 'M11.4 4.6 h1.2 v2.2 h-1.2 z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.5, radius: 0.22 },
      { d: 'M10.2 3.1 h3.6 v1.2 h-3.6 z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.38, radius: 0.16 },
    ],
  },
  // kod: açılı parantezler
  code: {
    d: 'M9.2 7.4 L4.4 12 L9.2 16.6 M14.8 7.4 L19.6 12 L14.8 16.6',
    w: 3.0, depth: 11, mat: 'obsidian',
  },
  // TV / yayın: ekran + ayak
  tv: {
    d: 'M3.9 6.9 H20.2 V16.5 H3.9 Z',
    fill: true, w: 0.4, depth: 12, mat: 'rubber',
    parts: [
      { d: 'M5.9 8.6 H18.2 V14.4 H5.9 Z', fill: true, w: 0.35, mat: 'obsidian', halfT: 0.55, radius: 0.24, phase: 0.62 },
      { d: 'M11.3 16.2 h1.4 v3.2 h-1.4 z', fill: true, w: 0.26, mat: 'chrome', halfT: 0.3, radius: 0.14 },
      { d: 'M8.7 19.2 h6.6 v1.4 h-6.6 z', fill: true, w: 0.26, mat: 'chrome', halfT: 0.32, radius: 0.15 },
    ],
  },
  // etiket: delikli etiket gövdesi
  tag: {
    d: 'M4.3 11.3 V4.9 H10.7 L19.2 13.4 L13.4 19.2 Z',
    parts: [
      { d: 'M7.7 6.8 a1.4 1.4 0 0 1 0 2.8 a1.4 1.4 0 0 1 0 -2.8 Z M7.7 7.5 a0.7 0.7 0 0 0 0 1.4 a0.7 0.7 0 0 0 0 -1.4 Z', fill: true, w: 0.3, mat: 'chrome', halfT: 0.35, radius: 0.16 },
    ],
    w: 2.6, depth: 12, mat: 'paper',
  },
  // bulut yedeği: bulut + ok
  backup: {
    d: 'M7.4 17.6 A3.9 3.9 0 0 1 7.6 9.9 A5.2 5.2 0 0 1 17.4 10.7 A3.6 3.6 0 0 1 17.2 17.6 Z',
    w: 2.3, depth: 11, mat: 'gel',
    parts: [{ d: 'M12 15.4 V11.4 M10.1 13.3 L12 11.4 L13.9 13.3', w: 2.0, mat: 'gloss', halfT: 0.6, radius: 0.28, phase: 0.3 }],
  },
};

module.exports = { GLYPHS: typeof GLYPHS !== 'undefined' ? GLYPHS : undefined, LETTERS: typeof LETTERS !== 'undefined' ? LETTERS : undefined, OBJECTS: typeof OBJECTS !== 'undefined' ? OBJECTS : undefined };

(async () => {
  if (require.main !== module) return; // sadece doğrudan çalıştırıldığında üret
  fs.mkdirSync(DST, { recursive: true });
  let made = 0;
  for (const [name, spec] of Object.entries(OBJECTS)) {
    // her nesne kendi malzemesiyle: glif adına göre renk yerleşimi
    let h = 0;
    for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) % 1000;
    const phase = (h % 30) / 100;
    const span = 0.80 + ((h >> 3) % 16) / 100;
    let raw = await renderGlyph(
      { d: spec.d, fill: !!spec.fill, w: spec.w || 2.8, detail: spec.detail, mat: spec.mat || 'gloss',
        parts: spec.parts, phase, span },
      { render: RENDER, mask: MASK },
    );
    const trimmed = await sharp(raw).trim({ threshold: 1 }).toBuffer();
    const meta = await sharp(trimmed).metadata();
    const box = Math.round(CANVAS * FILL);
    const maxDim = Math.max(meta.width, meta.height);
    const areaRatio = (meta.width * meta.height) / (maxDim * maxDim);
    const boost = Math.min(1.45, Math.max(1, 1 / Math.sqrt(areaRatio)));
    let scale = (box / maxDim) * boost;
    if (maxDim * scale > CANVAS * 0.99) scale = (CANVAS * 0.99) / maxDim;
    const w = Math.max(1, Math.round(meta.width * scale));
    const h2 = Math.max(1, Math.round(meta.height * scale));
    const resized = await sharp(trimmed).resize(w, h2, { fit: 'fill', kernel: 'lanczos3' }).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < resized.length; i += 4) if (resized[i] < ALPHA_FLOOR) resized[i] = 0;
    const body = await sharp(resized, { raw: { width: w, height: h2, channels: 4 } })
      .png({ compressionLevel: 9, effort: 10 }).toBuffer();
    await sharp({ create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h2) / 2) }])
      .png({ compressionLevel: 9, effort: 10 })
      .toFile(path.join(DST, `ic_kitsugi_obj_${name}.png`));
    made += 1;
  }
  console.log(`${made} nesne glifi üretildi -> ${DST}`);
})();
