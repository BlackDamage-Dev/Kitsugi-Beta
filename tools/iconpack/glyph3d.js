// 3B glif motoru — 3dicons `front/gradient` render'larıyla AYNI malzeme dili.
//
// Önceki sürüm 12 katmanlı yarı saydam ekstrüzyon + geniş parlama maskesi
// kullanıyordu; sonuç "bulanık/mürekkep" görünüyordu. Bu sürüm 3dicons'un
// gerçek malzeme kurallarını uygular:
//
//   1. TEMAS GÖLGESİ  — nesnenin ALTINDA, siluetten uzakta, yumuşak (tek katman)
//   2. KALINLIK       — 3 kaydırılmış kopya (0.62/0.34/0.15), hepsi OPAK →
//                       kenarlar keskin kalır, iç içe geçme bulanıklığı olmaz
//   3. ÖN YÜZ         — çapraz cam rampası (sol-üst açık → sağ-alt doygun)
//   4. SPEKÜLAR       — üst-sol kenarda ince, OPAK beyaz çizgi (şekle kırpılmış)
//   5. PARLAMA        — üst %40'ta düşük alfalı beyaz gradyan (şekle kırpılmış)
//
// Tümü 24×24 ızgarada çizilir; çağıran taraf 1024 px render edip LANCZOS3 ile
// 192 px'e indirir. Böylece kenarlar 3dicons render'ları kadar net olur.

const FACE = ['#FFB673', '#FA8A7E', '#F0679B', '#CE4FB4', '#A248C6', '#7B4FCB'];
const THICK = ['#6E3390', '#4A1D5F', '#361246'];
const SHADOW = '#2E0B3E';

const PALETTE = { face: FACE, thick: THICK, shadow: SHADOW, specular: '#FFF6EC' };

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** Şekli (path veya metin) istenen dolgu/konturla çizer. */
function shape(spec, { fill, stroke, w, opacity = 1 }) {
  const common = `fill="${fill}" stroke="${stroke}" stroke-width="${w}" stroke-linejoin="round" stroke-linecap="round"` +
    (opacity !== 1 ? ` opacity="${opacity}"` : '');
  if (spec.text) {
    const style = `font-family="DejaVu Sans, sans-serif" font-weight="${spec.weight || 700}" font-size="${spec.size || 17}"` +
      (spec.italic ? ' font-style="italic"' : '');
    return `<text x="12" y="${spec.baseline || 18}" text-anchor="middle" ${style} ${common}>${esc(spec.text)}</text>`;
  }
  const dash = spec.dash ? ` stroke-dasharray="${spec.dash}"` : '';
  return `<path d="${spec.d}" fill-rule="evenodd" ${common}${dash}/>`;
}

function defs(spec) {
  const w = spec.w || 3.3;
  const solidMask = spec.fill || spec.text;
  const maskShape = shape(spec, { fill: solidMask ? '#fff' : 'none', stroke: solidMask ? 'none' : '#fff', w });
  return `<defs>
    <linearGradient id="face" gradientUnits="userSpaceOnUse" x1="5.5" y1="2.5" x2="19.5" y2="21.5">
      ${FACE.map((c, i) => `<stop offset="${(i / (FACE.length - 1)).toFixed(2)}" stop-color="${c}"/>`).join('')}
    </linearGradient>
    <linearGradient id="thick" gradientUnits="userSpaceOnUse" x1="6" y1="6" x2="20" y2="23">
      <stop offset="0" stop-color="${THICK[0]}"/><stop offset="1" stop-color="${THICK[2]}"/>
    </linearGradient>
    <linearGradient id="gloss" gradientUnits="userSpaceOnUse" x1="12" y1="2" x2="12" y2="13">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.38"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </linearGradient>
    <radialGradient id="hl" gradientUnits="userSpaceOnUse" cx="8.4" cy="6.4" r="9.5">
      <stop offset="0" stop-color="#FFFFFF" stop-opacity="0.24"/>
      <stop offset="1" stop-color="#FFFFFF" stop-opacity="0"/>
    </radialGradient>
    <linearGradient id="ao" gradientUnits="userSpaceOnUse" x1="12" y1="14" x2="12" y2="23">
      <stop offset="0" stop-color="#3A0F4C" stop-opacity="0"/>
      <stop offset="1" stop-color="#3A0F4C" stop-opacity="0.30"/>
    </linearGradient>
    <filter id="soft" x="-50%" y="-50%" width="200%" height="200%">
      <feGaussianBlur stdDeviation="${spec.shadowBlur ?? 1.2}"/>
    </filter>
    <mask id="m" maskUnits="userSpaceOnUse" x="-6" y="-6" width="36" height="36">${maskShape}</mask>
  </defs>`;
}

/** Gövde: kalınlık + ön yüz + spekülar + parlama. */
function body(spec) {
  const w = spec.w || 3.3;
  // Harf glifleri her zaman DOLGU ile çizilir (kontur olsaydı içi boş görünürdü)
  const solid = spec.fill || spec.text;
  // 2) kalınlık: 3 opak kopya (kümülatif kaydırma)
  const steps = [
    { x: 0.55, y: 0.62 },
    { x: 0.26, y: 0.30 },
  ];
  const thick = steps.map((s, i) =>
    `<g transform="translate(${s.x} ${s.y})">` +
    shape(spec, { fill: solid ? THICK[i + 1] : 'none', stroke: solid ? 'none' : THICK[i + 1], w }) +
    `</g>`).join('');

  // 3) ön yüz
  const face = shape(spec, { fill: solid ? 'url(#face)' : 'none', stroke: solid ? 'none' : 'url(#face)', w });

  const specular = '';

  // 5) parlama + alt gölgeleme (ambient occlusion), şekle kırpılmış
  const gloss = `<g mask="url(#m)">` +
    `<rect x="-6" y="-6" width="36" height="36" fill="url(#hl)"/>` +
    `<rect x="-6" y="13" width="36" height="11" fill="url(#ao)"/>` +
    `</g>`;

  const detail = spec.detail
    ? `<path d="${spec.detail}" fill="none" stroke="#FFF1E3" stroke-opacity="0.62" ` +
      `stroke-width="${Math.max(0.95, w * 0.34).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`
    : '';
  const inner = spec.inner
    ? `<path d="${spec.inner}" fill="none" stroke="#FFF6EC" stroke-opacity="0.85" ` +
      `stroke-width="${Math.max(0.9, w * 0.28).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`
    : '';
  const strike = spec.strike
    ? `<path d="M7.1 12.9 H16.9" fill="none" stroke="#FFF1E3" stroke-opacity="0.95" ` +
      `stroke-width="1.7" stroke-linecap="round"/>`
    : '';

  return `${thick}${face}${detail}${inner}${specular}${strike}${gloss}`;
}

/** Tek glif için tam SVG (24×24 viewBox, istenen piksel boyutunda, saydam). */
function svg(spec, size = 1024) {
  const shadow = `<ellipse cx="13.4" cy="21.9" rx="${spec.shadowRx ?? 6.0}" ry="${spec.shadowRy ?? 1.5}" ` +
    `fill="${SHADOW}" opacity="0.34" filter="url(#soft)"/>`;
  const g = spec.rot ? `<g transform="rotate(${spec.rot} 12 12)">${body(spec)}</g>` : body(spec);
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24">${defs(spec)}${shadow}${g}</svg>`;
}

/** Siluet maskesi (beyaz, saydam zemin) — 3B render hattının girdisi. */
function maskSvg(spec, size, rot = 0) {
  const w = spec.w || 3.3;
  const solid = spec.fill || spec.text;
  // dolgulu şekillerde kontur çok ince tutulur; yoksa biçim yuvarlanıp kayboluyor
  const wMask = solid ? 0.45 : w;
  let el = shape(spec, { fill: solid ? '#fff' : 'none', stroke: '#fff', w: wMask });
  // ayrıntı çizgileri de maskeye girer → 3B yüzeyde kabartma/oluk olarak görünür
  if (spec.detail) el += `<path d="${spec.detail}" fill="none" stroke="#fff" stroke-width="${Math.max(0.85, w * 0.34).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`;
  if (spec.inner) el += `<path d="${spec.inner}" fill="none" stroke="#fff" stroke-width="${Math.max(0.8, w * 0.30).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/>`;
  const body = rot ? `<g transform="rotate(${rot} 12 12)">${el}</g>` : el;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24">${body}</svg>`;
}

/** Dolgulu şekillerde iç ayrıntıyı (mercek, tuşlar, mum…) oyar: koyu çizgi + alt ışık. */
function detailOverlay(spec, size) {
  if (!spec.detail && !spec.inner) return null;
  const w = spec.w || 3.3;
  const sw = Math.max(0.85, w * 0.30).toFixed(2);
  const parts = [];
  const draw = (d, sw2) => {
    parts.push(`<path d="${d}" fill="none" stroke="#4A175E" stroke-opacity="0.55" stroke-width="${sw2}" stroke-linecap="round" stroke-linejoin="round"/>`);
    parts.push(`<g transform="translate(0.10 0.13)"><path d="${d}" fill="none" stroke="#FFF2E4" stroke-opacity="0.45" stroke-width="${(+sw2 * 0.55).toFixed(2)}" stroke-linecap="round" stroke-linejoin="round"/></g>`);
  };
  if (spec.detail) draw(spec.detail, sw);
  if (spec.inner) draw(spec.inner, Math.max(0.8, w * 0.26).toFixed(2));
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24">${parts.join('')}</svg>`;
}

module.exports = { svg, body, defs, shape, maskSvg, detailOverlay, PALETTE, FACE, THICK };
