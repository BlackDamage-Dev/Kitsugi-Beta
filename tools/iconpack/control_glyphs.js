// 3dicons setinde KAPAT / CHEVRON / OK / FİLTRE / SIRALAMA / GÖZ / GÖRÜNÜM MODU
// gibi "kontrol" işaretleri yok. Bu betik, setin 3B dilini birebir uygulayarak
// eksik kontrol gliflerini üretir.
//
// Çizim hattı: tools/iconpack/glyph3d.js
//   temas gölgesi + extrude edilmiş gövde + dikey cam rampası + pah + üst parlama
// Rampa gerçek 3dicons render'larından ölçüldü (6 dikey bant ortalaması).
//
// Çıktı: app/src/main/res/drawable-nodpi/ic_kitsugi_glyph_<ad>.png (128×128, saydam)
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
const CANVAS = 128;
const FILL = 0.94;
const ALPHA_FLOOR = 26;

/** path: 24×24 koordinat uzayında; fill=true ise kapalı şekil dolgulu çizilir. */
const GLYPHS = {
  // kapat: parlak emaye çarpı
  close: { d: 'M6.2 6.2 L17.8 17.8 M17.8 6.2 L6.2 17.8', mat: 'gloss' },
  // eksi: yumuşak mat seramik hap
  minus: { d: 'M5.3 10.85 h13.4 a1.15 1.15 0 0 1 0 2.3 h-13.4 a1.15 1.15 0 0 1 0 -2.3 z', fill: true, mat: 'clay' },
  // yön oku: jöle (şeker) ok
  chevron: { d: 'M6.2 14.8 L12 9.2 L17.8 14.8', mat: 'gel', rot: { up: 0, down: 180, left: 270, right: 90 } },
  // ince kâğıt ok
  arrow: { d: 'M12 19 V5.6 M7.2 10.4 L12 5.6 L16.8 10.4', mat: 'paper', rot: { up: 0, down: 180, right: 90, left: 270 } },
  // göz: cam gövde + parlak emaye bebek
  eye: {
    d: 'M2.9 12 C5.6 7.6 8.8 5.7 12 5.7 C15.2 5.7 18.4 7.6 21.1 12 C18.4 16.4 15.2 18.3 12 18.3 C8.8 18.3 5.6 16.4 2.9 12 Z M12 14.6 A2.6 2.6 0 1 1 12 9.4 A2.6 2.6 0 1 1 12 14.6 Z',
    mat: 'glass',
    parts: [
      { d: 'M12 9.9 a2.1 2.1 0 0 1 0 4.2 a2.1 2.1 0 0 1 0 -4.2 Z', fill: true, w: 0.4, mat: 'obsidian', halfT: 0.62, radius: 0.30, phase: 0.55 },
      { d: 'M12 9.5 a2.5 2.5 0 0 1 0 5 a2.5 2.5 0 0 1 0 -5 Z M12 10.1 a1.9 1.9 0 0 0 0 3.8 a1.9 1.9 0 0 0 0 -3.8 Z', fill: true, w: 0.24, mat: 'chrome', halfT: 0.26, radius: 0.12 },
    ],
  },
  eyeOff: {
    d: 'M2.9 12 C5.6 7.6 8.8 5.7 12 5.7 C15.2 5.7 18.4 7.6 21.1 12 C18.4 16.4 15.2 18.3 12 18.3 C8.8 18.3 5.6 16.4 2.9 12 Z M4 20 L20 4',
    mat: 'glass',
  },
  // huni: camdan süzgeç
  funnel: {
    d: 'M3.6 5.4 H20.4 L13.9 13.1 V19.6 L10.1 17.4 V13.1 Z', fill: true, mat: 'glass',
    detail: 'M6.4 8.0 H17.6 M8.9 10.6 H15.1',
    parts: [{ d: 'M9.6 10.9 h4.8 V12.2 h-4.8 z', fill: true, w: 0.26, mat: 'gel', halfT: 0.45, radius: 0.22, phase: 0.62 }],
  },
  // sıralama: fırçalanmış metal çubuklar + ok
  sort: { d: 'M7 19.5 V4.8 M4.1 16.6 L7 19.5 L9.9 16.6 M13 6.5 H20 M13 11.5 H18.4 M13 16.5 H16.8', mat: 'gloss' },
  // ızgara: kalın mat seramik karolar
  grid: {
    d: 'M3.2 3.2 h17.6 v17.6 h-17.6 z', fill: true, mat: 'obsidian', radius: 0.6,
    parts: [
      { d: 'M4.6 4.6 h5.1 v5.1 h-5.1 z', fill: true, w: 0.3, mat: 'clay', halfT: 1.15, radius: 0.5, phase: 0.06 },
      { d: 'M14.3 4.6 h5.1 v5.1 h-5.1 z', fill: true, w: 0.3, mat: 'clay', halfT: 1.15, radius: 0.5, phase: 0.22 },
      { d: 'M4.6 14.3 h5.1 v5.1 h-5.1 z', fill: true, w: 0.3, mat: 'clay', halfT: 1.15, radius: 0.5, phase: 0.38 },
      { d: 'M14.3 14.3 h5.1 v5.1 h-5.1 z', fill: true, w: 0.3, mat: 'clay', halfT: 1.15, radius: 0.5, phase: 0.52 },
    ],
  },
  list: { d: 'M4.6 7.2 H19.4 M4.6 12 H19.4 M4.6 16.8 H19.4', mat: 'paper' },
  stream: { d: 'M4.6 8.4 H19.4 M4.6 15.6 H19.4', mat: 'rubber' },
  carousel: {
    d: 'M8.4 6.2 h7.2 v11.6 h-7.2 z', fill: true, mat: 'gloss',
    parts: [
      { d: 'M3.6 8.2 h3.9 v7.6 h-3.9 z', fill: true, w: 0.35, mat: 'gloss', halfT: 0.72, radius: 0.40, phase: 0.34 },
      { d: 'M16.5 8.2 h3.9 v7.6 h-3.9 z', fill: true, w: 0.35, mat: 'gloss', halfT: 0.72, radius: 0.40, phase: 0.34 },
    ],
  },
  more: { d: 'M10.1 6.6 a1.9 1.9 0 1 1 3.8 0 a1.9 1.9 0 1 1 -3.8 0 Z M10.1 12 a1.9 1.9 0 1 1 3.8 0 a1.9 1.9 0 1 1 -3.8 0 Z M10.1 17.4 a1.9 1.9 0 1 1 3.8 0 a1.9 1.9 0 1 1 -3.8 0 Z', fill: true, width: 0.4, depth: 4, mat: 'gel' },
  external: {
    d: 'M17.8 13.6 V18.6 a1.2 1.2 0 0 1 -1.2 1.2 H5.8 a1.2 1.2 0 0 1 -1.2 -1.2 V7.8 a1.2 1.2 0 0 1 1.2 -1.2 H11', mat: 'obsidian',
    parts: [{ d: 'M13.4 4.6 H19.4 V10.6 M19.2 4.8 L11.4 12.6', w: 2.4, mat: 'gloss', halfT: 0.85, radius: 0.38 }],
  },
  fullscreen: { d: 'M4 9.2 V4 h5.2 M14.8 4 H20 v5.2 M20 14.8 V20 h-5.2 M9.2 20 H4 v-5.2', mat: 'obsidian' },
  crop: { d: 'M7.2 3.4 v12.2 a1.2 1.2 0 0 0 1.2 1.2 h12.2 M3.4 7.2 h12.2 a1.2 1.2 0 0 1 1.2 1.2 v12.2', mat: 'gloss' },
  rotate: { d: 'M19.6 12 a7.6 7.6 0 1 1 -2.3 -5.4 M20.0 3.2 v5.2 h-5.2', mat: 'gloss' },
  aspect: { d: 'M3.8 7.8 h16.4 v8.4 h-16.4 z', fill: true, mat: 'clay', detail: 'M8.6 8.2 V15.8 M15.4 8.2 V15.8' },
  // damla: cam
  blur: { d: 'M12 3.6 C15.6 7.4 17.8 10.2 17.8 13 A5.8 5.8 0 0 1 6.2 13 C6.2 10.2 8.4 7.4 12 3.6 Z', fill: true, mat: 'glass' },
  alignLeft: { d: 'M4 6.8 H20 M4 12 H13.6 M4 17.2 H17.2', mat: 'paper' },
  alignCenter: { d: 'M4 6.8 H20 M7.2 12 H16.8 M5.4 17.2 H18.6', mat: 'clay' },
  alignJustify: { d: 'M4 6.8 H20 M4 12 H20 M4 17.2 H20', mat: 'rubber' },
  border: { d: 'M4 4 h16 v16 h-16 z M8.4 8.4 h7.2 v7.2 h-7.2 z', mat: 'obsidian' },
  selectAll: { d: 'M4.2 4.2 h15.6 v15.6 h-15.6 z', dash: '2.6 3.4', width: 2.6, mat: 'rubber' },
  drag: {
    d: 'M8.5 9.4 H15.5 M8.5 14.6 H15.5', width: 3.0, mat: 'rubber',
    parts: [
      { d: 'M7.2 8.2 a1.2 1.2 0 0 1 0 2.4 a1.2 1.2 0 0 1 0 -2.4 Z M7.2 13.4 a1.2 1.2 0 0 1 0 2.4 a1.2 1.2 0 0 1 0 -2.4 Z', fill: true, w: 0.24, mat: 'chrome', halfT: 0.28, radius: 0.13 },
      { d: 'M16.8 8.2 a1.2 1.2 0 0 1 0 2.4 a1.2 1.2 0 0 1 0 -2.4 Z M16.8 13.4 a1.2 1.2 0 0 1 0 2.4 a1.2 1.2 0 0 1 0 -2.4 Z', fill: true, w: 0.24, mat: 'chrome', halfT: 0.28, radius: 0.13 },
    ],
  },
  density: { d: 'M4.2 8 H19.8 M4.2 12 H19.8 M4.2 16 H19.8', width: 2.2, mat: 'paper' },
  // klavye: kauçuk gövde + parlak tuşlar
  keyboard: {
    d: 'M3.2 7.4 h17.6 v9.2 h-17.6 z', fill: true, mat: 'rubber',
    parts: [
      { d: 'M5.6 9.4 h2.6 v2.2 h-2.6 z M9.4 9.4 h2.6 v2.2 h-2.6 z M13.2 9.4 h2.6 v2.2 h-2.6 z M17 9.4 h1.6 v2.2 h-1.6 z', fill: true, w: 0.3, mat: 'gloss', halfT: 0.5, radius: 0.22, phase: 0.08 },
      { d: 'M6.6 12.8 h10.8 v1.9 h-10.8 z', fill: true, w: 0.3, mat: 'gloss', halfT: 0.5, radius: 0.22, phase: 0.08 },
    ],
  },
  input: {
    d: 'M6.4 7.8 h11.2 v4.4 l-3.2 4.4 h-4.8 l-3.2 -4.4 z', fill: true, mat: 'gloss',
    detail: 'M8.8 10.2 H15.4',
    parts: [{ d: 'M15.0 9.6 a1.0 1.0 0 0 1 0 2.0 a1.0 1.0 0 0 1 0 -2.0 Z', fill: true, w: 0.24, mat: 'chrome', halfT: 0.3, radius: 0.14 }],
  },
  doubleArrow: { d: 'M8.4 7.6 L4.2 12 L8.4 16.4 M15.6 7.6 L19.8 12 L15.6 16.4', mat: 'gel' },
  home: {
    d: 'M3.8 10.8 L12 4.2 L20.2 10.8 V19.8 h-5.6 v-5.2 h-5.2 v5.2 H3.8 z', fill: true, mat: 'clay',
    parts: [
      { d: 'M3.3 11.3 L12 3.7 L20.7 11.3 L20.7 12.6 L12 5.1 L3.3 12.6 Z', fill: true, w: 0.3, mat: 'clay', halfT: 1.7, radius: 0.9, phase: 0.22 },
      { d: 'M9.2 19.8 v-5.2 h5.6 v5.2 z', fill: true, w: 0.3, mat: 'obsidian', halfT: 0.42, radius: 0.18, phase: 0.5 },
    ],
  },
  leaf: {
    d: 'M4.8 19.2 C4.8 11 11 4.8 19.2 4.8 C19.2 13 13 19.2 4.8 19.2 Z', fill: true, mat: 'gel',
    parts: [{ d: 'M4.4 20.0 L13.6 10.8', w: 1.9, mat: 'clay', halfT: 0.55, radius: 0.26, phase: 0.35 }],
  },
  snow: {
    d: 'M12 3.4 V20.6 M4.4 7.7 L19.6 16.3 M19.6 7.7 L4.4 16.3', mat: 'glass',
    parts: [{ d: 'M12 10.2 a1.8 1.8 0 0 1 0 3.6 a1.8 1.8 0 0 1 0 -3.6 Z', fill: true, w: 0.24, mat: 'chrome', halfT: 0.45, radius: 0.2 }],
  },
  // kek: mat sünger + parlak mumlar
  cake: {
    d: 'M4.4 20.4 V11.4 h15.2 V20.4 z', fill: true, mat: 'clay',
    parts: [
      { d: 'M3.2 20.2 h17.6 v1.5 h-17.6 z', fill: true, w: 0.28, mat: 'chrome', halfT: 0.34, radius: 0.16 },
      { d: 'M4.4 11.0 h15.2 v1.9 h-15.2 z', fill: true, w: 0.3, mat: 'gel', halfT: 0.85, radius: 0.42, phase: 0.55 },
      { d: 'M7.6 10.8 v-3.2 h1.9 v3.2 z', fill: true, w: 0.3, mat: 'gloss', halfT: 0.42, radius: 0.20, phase: 0.12 },
      { d: 'M14.5 10.8 v-3.2 h1.9 v3.2 z', fill: true, w: 0.3, mat: 'gloss', halfT: 0.42, radius: 0.20, phase: 0.12 },
      { d: 'M11.05 10.8 v-5.6 h1.9 v5.6 z', fill: true, w: 0.3, mat: 'gloss', halfT: 0.42, radius: 0.20, phase: 0.12 },
    ],
  },
  // kamera: parlak gövde + cam mercek
  camera: {
    d: 'M3.8 8.4 h4.4 l2 -2.4 h3.6 l2 2.4 h4.4 v10 h-16.4 z', fill: true, mat: 'gloss',
    parts: [
      { d: 'M8.9 13.2 a3.1 3.1 0 0 1 6.2 0 a3.1 3.1 0 0 1 -6.2 0 Z', fill: true, w: 0.35, mat: 'obsidian', halfT: 0.66, radius: 0.32, phase: 0.62 },
      { d: 'M12 9.6 a3.6 3.6 0 0 1 0 7.2 a3.6 3.6 0 0 1 0 -7.2 Z M12 10.6 a2.6 2.6 0 0 0 0 5.2 a2.6 2.6 0 0 0 0 -5.2 Z', fill: true, w: 0.35, mat: 'chrome', halfT: 0.42, radius: 0.20 },
      { d: 'M15.6 7.4 a1.05 1.05 0 0 1 0 2.1 a1.05 1.05 0 0 1 0 -2.1 Z', fill: true, w: 0.26, mat: 'chrome', halfT: 0.3, radius: 0.14 },
      { d: 'M6.4 7.2 h3.2 v1.1 h-3.2 z', fill: true, w: 0.24, mat: 'obsidian', halfT: 0.25, radius: 0.12 },
    ],
  },
  textQuote: { d: 'M8.6 8.1 c-2.2 0.7 -3.7 2.7 -3.7 5 v3.5 h5 v-5 h-2.4 c0 -1.2 0.6 -2.1 1.7 -2.5 z M16.6 8.1 c-2.2 0.7 -3.7 2.7 -3.7 5 v3.5 h5 v-5 h-2.4 c0 -1.2 0.6 -2.1 1.7 -2.5 z', fill: true, mat: 'clay' },
  textHash: { d: 'M9.9 4.4 L7.9 19.6 M16.1 4.4 L14.1 19.6 M5.4 9.1 H20.2 M4.2 15.1 H19', w: 2.7, mat: 'obsidian' },
};

const LETTERS = {
  textBold: { ch: 'B', weight: 700, size: 17, mat: 'obsidian' },
  textItalic: { ch: 'I', weight: 700, size: 17, italic: true, mat: 'gloss' },
  textTitle: { ch: 'A', weight: 700, size: 17, mat: 'clay' },
  textStrike: { ch: 'S', weight: 700, size: 17, strike: true, mat: 'gel' },
};

/** Glif adına göre renk yerleşimi — her ikonun gradyanı farklı yere düşsün. */
function hueJitter(name) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) % 1000;
  return { phase: (h % 34) / 100, span: 0.78 + ((h >> 3) % 18) / 100, bulk: 0.94 + ((h >> 6) % 13) / 100 };
}

/** GLYPHS girdisini glyph3d spec'ine çevirir. */
function specOf(spec, rot) {
  return {
    d: spec.d,
    fill: !!spec.fill,
    detail: spec.detail,
    inner: spec.inner,
    w: spec.width || 3.3,
    depth: spec.depth || 12,
    dx: 0.26,
    dy: 0.31,
    rot: rot || 0,
  };
}

const svgFor = (spec, rot) => g3d.svg(specOf(spec, rot), RENDER);

const svgForLetter = (spec) => g3d.svg({
  text: spec.ch,
  size: spec.size,
  weight: spec.weight,
  italic: !!spec.italic,
  strike: !!spec.strike,
  baseline: 18,
  w: 0.55,
  depth: 6,
  dx: 0.24,
  dy: 0.30,
  shadowRx: 5.2,
  shadowRy: 1.4,
}, RENDER);

module.exports = { GLYPHS: typeof GLYPHS !== 'undefined' ? GLYPHS : undefined, LETTERS: typeof LETTERS !== 'undefined' ? LETTERS : undefined, OBJECTS: typeof OBJECTS !== 'undefined' ? OBJECTS : undefined };

(async () => {
  if (require.main !== module) return; // sadece doğrudan çalıştırıldığında üret
  fs.mkdirSync(DST, { recursive: true });
  let made = 0;
  const jobs = [];
  for (const [name, spec] of Object.entries(GLYPHS)) {
    if (spec.rot) for (const [dir, deg] of Object.entries(spec.rot)) jobs.push([`${name}_${dir}`, { ...spec, rotate: deg }]);
    else jobs.push([name, spec]);
  }
  for (const [name, spec] of Object.entries(LETTERS)) jobs.push([name, spec, true]);

  for (const [name, spec, isLetter] of jobs) {
    // 1) siluet maskesi + malzeme/renk yerleşimi → 2) SDF + analitik 3B render
    const jit = hueJitter(name);
    const maskSpec = isLetter
      ? { text: spec.ch, size: spec.size, weight: spec.weight, italic: !!spec.italic, strike: !!spec.strike, mat: spec.mat, phase: 0.30, span: 0.86 }
      : { ...spec, rot: spec.rotate || 0, phase: jit.phase, span: jit.span };
    const raw0 = await renderGlyph(maskSpec, { render: RENDER, mask: MASK });
    let raw = raw0;
    const trimmed = await sharp(raw).trim({ threshold: 1 }).toBuffer();
    const meta = await sharp(trimmed).metadata();
    const box = Math.round(CANVAS * FILL);
    const maxDim = Math.max(meta.width, meta.height);
    const areaRatio = (meta.width * meta.height) / (maxDim * maxDim);
    const boost = Math.min(1.45, Math.max(1, 1 / Math.sqrt(areaRatio)));
    let scale = (box / maxDim) * boost;
    if (maxDim * scale > CANVAS * 0.99) scale = (CANVAS * 0.99) / maxDim;
    const w = Math.max(1, Math.round(meta.width * scale));
    const h = Math.max(1, Math.round(meta.height * scale));
    const resized = await sharp(trimmed).resize(w, h, { fit: 'fill', kernel: 'lanczos3' }).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < resized.length; i += 4) if (resized[i] < ALPHA_FLOOR) resized[i] = 0;
    const body = await sharp(resized, { raw: { width: w, height: h, channels: 4 } })
      .png({ compressionLevel: 9, effort: 10 }).toBuffer();
    await sharp({ create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h) / 2) }])
      .png({ compressionLevel: 9, effort: 10 })
      .toFile(path.join(DST, `ic_kitsugi_glyph_${name}.png`));
    made += 1;
  }
  console.log(`${made} kontrol glifi üretildi -> ${DST}`);
})();
