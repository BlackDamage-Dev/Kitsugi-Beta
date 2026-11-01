// Glif → 3B render yardımcısı.
//
// Her glif KENDİ malzemesiyle ve gerekiyorsa PARÇALARIYLA render edilir:
//   - gövde  : ana siluet (mat: clay / gloss / gel / metal / glass / rubber / paper)
//   - parts  : gövdenin üstüne oturan, farklı malzemeli ikinci parçalar
//              (kilit kancası, kamera merceği, klavye tuşları, kek mumları…)
//   - detail : render sonrası oyulan ince iç çizgiler
//
// Böylece 3dicons setindeki gibi "her nesnenin kendi 3B stili" korunur.
const path = require('path');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();
const g3d = require(path.join(__dirname, 'glyph3d.js'));
const sdf3d = require(path.join(__dirname, 'sdf3d.js'));

const DEFAULTS = { world: 24, render: 512, mask: 768 };

async function maskAlpha(spec, maskSize, rot) {
  const uri = g3d.maskSvg(spec, maskSize, rot || 0);
  const raw = await sharp(Buffer.from(uri)).ensureAlpha().raw().toBuffer();
  const alpha = new Uint8Array(maskSize * maskSize);
  for (let i = 0; i < maskSize * maskSize; i++) alpha[i] = raw[i * 4 + 3];
  return alpha;
}

function matSpec(spec) {
  // maske için gereken alanlar
  return {
    d: spec.d,
    fill: !!spec.fill,
    w: spec.w || spec.width || 3.3,
    dash: spec.dash,
    detail: spec.detail,
    inner: spec.inner,
    text: spec.text,
  };
}

/**
 * @param {object} spec  { d, fill, w, detail, mat, halfT, radius, phase, span,
 *                         parts:[{d, fill, w, mat, halfT, radius, phase, span}] }
 * @param {object} o     { render, mask, overlay (bool) }
 * @returns {Promise<Buffer>} saydam PNG
 */
async function renderGlyph(spec, o = {}) {
  const render = o.render || DEFAULTS.render;
  const maskSize = o.mask || DEFAULTS.mask;

  const alpha = await maskAlpha(matSpec(spec), maskSize, spec.rot);
  const bbox = sdf3d.bboxOf(alpha, maskSize, DEFAULTS.world);

  let img = await sdf3d.render(alpha, maskSize, {
    out: render,
    material: spec.mat,
    halfT: spec.halfT,
    radius: spec.radius,
    phase: spec.phase,
    span: spec.span,
  });

  for (const part of spec.parts || []) {
    const pAlpha = await maskAlpha(matSpec(part), maskSize, 0);
    const pImg = await sdf3d.render(pAlpha, maskSize, {
      out: render,
      material: part.mat || spec.mat,
      halfT: part.halfT,
      radius: part.radius,
      phase: part.phase ?? spec.phase,
      span: part.span ?? spec.span,
      bbox,          // renk rampası gövdeyle aynı yerde kalsın
      // her parça altındaki yüzeye kendi yumuşak gölgesini düşürür →
      // katmanlı, gerçek nesne görüntüsü
      shadow: part.shadow !== false,
      shadowOffset: [-0.16, 0.20],
      shadowAlpha: part.shadowAlpha ?? 0.34,
      shadowBlur: 3.0,
    });
    img = await sharp(img).composite([{ input: pImg, top: 0, left: 0 }]).png().toBuffer();
  }

  if (o.overlay !== false) {
    const ov = g3d.detailOverlay(matSpec(spec), render);
    if (ov) img = await sharp(img).composite([{ input: Buffer.from(ov), top: 0, left: 0 }]).png().toBuffer();
  }
  return img;
}

module.exports = { renderGlyph, maskAlpha };
