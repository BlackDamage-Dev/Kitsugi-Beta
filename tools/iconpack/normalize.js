// 3Dicons (CC0) render'larını Android drawable-nodpi'ye normalize eder.
// - saydam kenarları kırpar (trim)
// - her ikonu aynı optik kutuya oturtur (192px tuval, %92 doldurma)
// sharp, tools/iconpack içinde `npm install sharp` ile kurulur; yoksa geliştirme
// ortamındaki ortak kuruluma düşer.
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || '/home/user/.iconwork/node_modules/sharp');
})();
const fs = require('fs');
const path = require('path');

const ROOT = __dirname;
const SRC = path.join(ROOT, 'raw3d');
const DST = path.join(ROOT, '..', '..', 'app', 'src', 'main', 'res', 'drawable-nodpi');
const CANVAS = 192;
const FILL = 0.92;
// Koyu temada kaybolan varlıklar için hafif parlaklık düzeltmesi (optik dengeleme).
const BRIGHTEN = {};
// Optik dengeleme: oranı uç olan (kart, bavul, monitör gibi) ikonlar aynı kutuda
// küçük görünür. Alan tabanlı hafif büyütme + tuval sınırı uygulanır.
const MAX_BOOST = 1.45;
// Bu alfa değerinin altındaki pikseller tamamen saydam yapılır (gölge kalıntısı temizliği).
const ALPHA_FLOOR = 26;

(async () => {
  fs.mkdirSync(DST, { recursive: true });
  const files = fs.readdirSync(SRC).filter((f) => f.endsWith('.png'));
  let total = 0;
  for (const f of files) {
    const key = path.basename(f, '.png');
    const input = path.join(SRC, f);
    const bright = BRIGHTEN[key] || 1;
    let pipe = sharp(input);
    if (bright !== 1) pipe = pipe.modulate({ brightness: bright });
    const trimmed = await pipe.trim({ threshold: 1 }).toBuffer();
    const meta = await sharp(trimmed).metadata();
    const box = Math.round(CANVAS * FILL);
    const maxDim = Math.max(meta.width, meta.height);
    const areaRatio = (meta.width * meta.height) / (maxDim * maxDim);
    const boost = Math.min(MAX_BOOST, Math.max(1, 1 / Math.sqrt(areaRatio)));
    const limit = CANVAS * 0.99;
    let scale = (box / maxDim) * boost;
    if (maxDim * scale > limit) scale = limit / maxDim;
    const w = Math.max(1, Math.round(meta.width * scale));
    const h = Math.max(1, Math.round(meta.height * scale));
    // Yumuşak gölge/hâle kalıntılarını temizle: çok düşük alfa değerleri sıfırlanır.
    const resized = await sharp(trimmed).resize(w, h, { fit: 'fill' }).ensureAlpha().raw().toBuffer();
    for (let i = 3; i < resized.length; i += 4) {
      if (resized[i] < ALPHA_FLOOR) resized[i] = 0;
    }
    const body = await sharp(resized, { raw: { width: w, height: h, channels: 4 } })
      .png({ compressionLevel: 9, palette: true })
      .toBuffer();
    const out = path.join(DST, 'ic_kitsugi_' + key.replace(/-/g, '_') + '.png');
    await sharp({
      create: { width: CANVAS, height: CANVAS, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
    })
      .composite([{ input: body, left: Math.round((CANVAS - w) / 2), top: Math.round((CANVAS - h) / 2) }])
      .png({ compressionLevel: 9, palette: true })
      .toFile(out);
    const size = fs.statSync(out).size;
    total += size;
    console.log(key.padEnd(14), String(size).padStart(6) + ' B');
  }
  console.log('\n' + files.length + ' ikon, toplam ' + (total / 1024).toFixed(0) + ' KB -> ' + DST);
})();
