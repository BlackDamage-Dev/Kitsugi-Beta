// Gerçek 3B render hattı (SDF + ışın yürütme) — 3dicons render'larıyla aynı malzeme.
//
// Neden?
// ------
// SVG ile "gövde + kaydırılmış kopya + gradyan" yaklaşımı düz/poster görünüyordu.
// 3dicons nesneleri GERÇEK 3B: yuvarlatılmış kenarlı hacim, yüzey normaline göre
// gölgeleme, geniş spekülar parlama, fresnel kenar ışığı ve temas gölgesi.
//
// Bu modül, glif siluetini (SVG maskesi) alıp:
//   1. işaretli mesafe alanına (SDF) çevirir (kesin Öklid uzaklık dönüşümü),
//   2. silueti kalınlığı `2*halfT` olan, kenarları `r` yarıçapla yuvarlatılmış
//      bir levhaya ekstrüde eder,
//   3. her piksel için ışını yürütüp yüzey normalini bulur,
//   4. plastik/cam malzeme ile boyar: Lambert + Blinn-Phong spekülar + fresnel +
//      kenar karartma (AO) + yumuşak temas gölgesi.
//
// Çıktı: saydam PNG (dünya birimi 24, kamera ortografik, ışık sol-üst-ön).

const sharp = require('./node_modules/sharp');

// ── renk rampası (3dicons front/gradient ölçümü) ────────────────────────────
const RAMP = [
  [0.0, [255, 196, 124]],   // sıcak kayısı
  [0.18, [255, 145, 106]],
  [0.40, [246, 96, 143]],
  [0.60, [209, 66, 176]],
  [0.78, [156, 62, 203]],
  [1.0, [104, 74, 213]],    // derin mor
];

function ramp(t) {
  t = Math.min(1, Math.max(0, t));
  for (let i = 1; i < RAMP.length; i++) {
    if (t <= RAMP[i][0]) {
      const [t0, c0] = RAMP[i - 1];
      const [t1, c1] = RAMP[i];
      const k = (t - t0) / (t1 - t0 || 1);
      return [0, 1, 2].map((j) => c0[j] + (c1[j] - c0[j]) * k);
    }
  }
  return RAMP[RAMP.length - 1][1].slice();
}

// ── 1D kare mesafe dönüşümü (Felzenszwalb & Huttenlocher) ───────────────────
function edt1d(f, n, d, v, z) {
  let k = 0;
  v[0] = 0;
  z[0] = -Infinity;
  z[1] = Infinity;
  for (let q = 1; q < n; q++) {
    let s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    while (s <= z[k]) {
      k--;
      s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2 * q - 2 * v[k]);
    }
    k++;
    v[k] = q;
    z[k] = s;
    z[k + 1] = Infinity;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    d[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
  }
}

/** İkili maskeden kesin Öklid mesafe alanı (piksel birimi, içeride negatif). */
function signedDistance(alpha, w, h) {
  const INF = 1e9;
  const fg = new Float64Array(w * h);
  const bg = new Float64Array(w * h);
  for (let i = 0; i < w * h; i++) {
    const inside = alpha[i] > 127;
    fg[i] = inside ? 0 : INF;
    bg[i] = inside ? INF : 0;
  }
  const tmpF = new Float64Array(Math.max(w, h));
  const tmpD = new Float64Array(Math.max(w, h));
  const v = new Int32Array(Math.max(w, h) + 1);
  const z = new Float64Array(Math.max(w, h) + 1);

  const pass = (grid) => {
    for (let x = 0; x < w; x++) {
      for (let y = 0; y < h; y++) tmpF[y] = grid[y * w + x];
      edt1d(tmpF, h, tmpD, v, z);
      for (let y = 0; y < h; y++) grid[y * w + x] = tmpD[y];
    }
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) tmpF[x] = grid[y * w + x];
      edt1d(tmpF, w, tmpD, v, z);
      for (let x = 0; x < w; x++) grid[y * w + x] = tmpD[x];
    }
  };
  pass(fg);
  pass(bg);

  const out = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) out[i] = Math.sqrt(fg[i]) - Math.sqrt(bg[i]);
  return out;
}

/** SDF alanını yumuşatır (ikili maskeden gelen basamakları yok eder). */
function smooth(field, size, passes) {
  let a = Float32Array.from(field);
  let b = new Float32Array(field.length);
  for (let p = 0; p < passes; p++) {
    for (let y = 0; y < size; y++) {
      for (let x = 0; x < size; x++) {
        let sum = 0, n = 0;
        for (let dy = -1; dy <= 1; dy++) {
          const yy = y + dy;
          if (yy < 0 || yy >= size) continue;
          for (let dx = -1; dx <= 1; dx++) {
            const xx = x + dx;
            if (xx < 0 || xx >= size) continue;
            sum += a[yy * size + xx];
            n++;
          }
        }
        b[y * size + x] = sum / n;
      }
    }
    const t = a; a = b; b = t;
  }
  return a;
}

/** SDF'den iki doğrusal örnekleme (dünya birimi). */
function sampler(sdf, size, world) {
  const scale = size / world; // px per unit
  const W = size, H = size;
  return (x, y) => {
    let px = x * scale + W / 2 - 0.5;
    let py = -y * scale + H / 2 - 0.5;
    px = Math.min(W - 1.001, Math.max(0, px));
    py = Math.min(H - 1.001, Math.max(0, py));
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const fx = px - x0, fy = py - y0;
    const i00 = y0 * W + x0;
    const a = sdf[i00], b = sdf[i00 + 1], c = sdf[i00 + W], d = sdf[i00 + W + 1];
    return ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy) / scale;
  };
}


// ── MALZEMELER ─────────────────────────────────────────────────────────────
// 3dicons setinde her nesne kendi malzemesiyle render edilmiş: kulaklık mat
// deri + koyu ped, kalp yumuşak mat seramik, anahtar parlak emaye, laboratuvar
// camı + sıvı, kasa fırçalanmış metal kadran, kâğıt uçak ince mat kâğıt.
// Tek malzemeyle hepsini boyamak "hepsi aynı plastik" görüntüsü veriyordu.
// Bu yüzden her glif kendi malzemesini seçiyor; ışık yönü ve renk rampası ortak
// kaldığı için set yine tek aile gibi duruyor.
const MATERIALS = {
  // yumuşak mat seramik/kıl — kalp, ev, kek gövdesi
  clay: {
    halfT: 1.52, radius: 0.98, shin: 26, spec: 0.30, env: 0.10, fres: 0.04, rimDark: 0.09,
    amb: 0.80, dif: 0.40, bounce: 0.20, sat: 1.26, dark: 1.0, grain: 0.010,
  },
  // parlak emaye/plastik — anahtar, oklar, küçük kontroller
  gloss: {
    halfT: 1.10, radius: 0.56, shin: 120, spec: 0.85, env: 0.55, fres: 0.10, rimDark: 0.09,
    amb: 0.54, dif: 0.64, bounce: 0.04, sat: 1.46, dark: 1.0, grain: 0.0,
  },
  // jöle/şeker — içinden ışık geçen parlak yarı saydam
  gel: {
    halfT: 1.34, radius: 0.90, shin: 80, spec: 0.55, env: 0.34, fres: 0.16, rimDark: 0.09,
    amb: 0.66, dif: 0.48, bounce: 0.10, sat: 1.56, dark: 1.0, grain: 0.0,
    inner: 0.30,
  },
  // fırçalanmış metal — çubuklar, çerçeve, kasa kadranı
  metal: {
    halfT: 0.92, radius: 0.30, shin: 46, spec: 0.30, env: 1.45, fres: 0.42,
    amb: 0.32, dif: 0.28, bounce: 0.02, sat: 0.58, dark: 1.08, grain: 0.010,
    metallic: 1.0, streak: 1.0, halfT: 1.24, radius: 0.52,
  },
  // cam — mercek, huni, kar tanesi, damla
  glass: {
    halfT: 1.10, radius: 0.74, shin: 240, spec: 0.95, env: 1.05, fres: 0.34, rimDark: 0.09,
    amb: 0.62, dif: 0.50, bounce: 0.08, sat: 1.40, dark: 1.06, grain: 0.0,
    glassy: 1.0, inner: 0.18,
  },
  // obsidyen/grafit cam — koyu gövde, keskin spekülar, parlak kenar
  obsidian: {
    halfT: 1.18, radius: 0.58, shin: 200, spec: 1.15, env: 0.95, fres: 0.44, rimDark: 0.11,
    amb: 0.56, dif: 0.68, bounce: 0.03, sat: 1.50, dark: 0.68, grain: 0.008,
  },
  // krom / gümüş — küçük aksan parçaları (mercek halkası, kadran, çerçeve)
  chrome: {
    halfT: 0.46, radius: 0.22, shin: 320, spec: 1.6, env: 1.2, fres: 0.9,
    amb: 0.55, dif: 0.18, bounce: 0.02, sat: 0.10, dark: 2.05, grain: 0.0,
    metallic: 1.0,
  },
  // mat kauçuk/koyu gövde — tutamak, ped, tuş takımı gövdesi
  rubber: {
    halfT: 1.46, radius: 0.78, shin: 12, spec: 0.18, env: 0.05, fres: 0.03, rimDark: 0.09,
    amb: 0.60, dif: 0.34, bounce: 0.08, sat: 1.10, dark: 0.74, grain: 0.040,
  },
  // ince kâğıt/levha — satırlar, oklar, metin blokları
  paper: {
    halfT: 0.56, radius: 0.22, shin: 16, spec: 0.22, env: 0.12, fres: 0.06, rimDark: 0.09,
    amb: 0.84, dif: 0.50, bounce: 0.03, sat: 1.18, dark: 1.0, grain: 0.026,
    sheet: 1.0,
  },
};

function materialOf(m) {
  if (!m) return MATERIALS.gloss;
  if (typeof m === 'string') return Object.assign({}, MATERIALS.gloss, MATERIALS[m] || {});
  return Object.assign({}, MATERIALS.gloss, m);
}

// stüdyo ortamı (yansıma): büyük üst ışık + sağ ince şerit + alt soğuk dolgu
function envSample(R) {
  const x = R[0], y = R[1], z = R[2];
  let v = 0.16;
  v += 0.62 * Math.exp(-(((x + 0.30) ** 2) / 0.24 + ((y - 0.58) ** 2) / 0.28));
  v += 1.00 * Math.exp(-(((x - 0.60) ** 2) / 0.050 + ((y - 0.02) ** 2) / 0.55));
  v += 0.36 * Math.exp(-(((y + 0.66) ** 2) / 0.10) - (x * x) / 0.85);
  v += 0.20 * Math.exp(-(((x + 0.72) ** 2) / 0.07 + ((y + 0.06) ** 2) / 0.60));
  return v * Math.max(0, 0.28 + 0.72 * z);
}

function hash2(x, y) {
  const h = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453;
  return h - Math.floor(h);
}

/** Maske sınır kutusu (dünya birimi) — parçaların renk rampasını hizalamak için. */
function bboxOf(alpha, size, world) {
  const w = world || 24;
  let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
  for (let j = 0; j < size; j++) {
    for (let i = 0; i < size; i++) {
      if (alpha[j * size + i] > 127) {
        const wx = ((i + 0.5) / size) * w - w / 2;
        const wy = -(((j + 0.5) / size) * w - w / 2);
        if (wx < x0) x0 = wx;
        if (wx > x1) x1 = wx;
        if (wy < y0) y0 = wy;
        if (wy > y1) y1 = wy;
      }
    }
  }
  return [x0, x1, y0, y1];
}

/**
 * Siluet maskesinden 3B render.
 * @param {Uint8Array} alpha  size*size alfa kanalı (0-255)
 * @param {number} size       maske çözünürlüğü
 * @param {object} opts  { out, halfT, radius, world, light, exposure,
 *                         material, phase, span, tint }
 */
async function render(alpha, size, opts = {}) {
  const world = opts.world || 24;
  const mat = materialOf(opts.material);
  const out = opts.out || 512;
  const exposure = opts.exposure ?? 1.04;
  const lightDir = opts.light || [-0.40, 0.58, 0.72];
  const phase = opts.phase ?? 0;
  const span = opts.span ?? 1;
  const withShadow = opts.shadow !== false;
  // parçalar için kendi (daha dar, daha yumuşak) gölgesi — üstteki parça alttakine
  // gölge düşürsün, böylece katmanlı gerçek nesne görüntüsü oluşsun
  const shOffX = opts.shadowOffset ? opts.shadowOffset[0] : 0.10;
  const shOffY = opts.shadowOffset ? opts.shadowOffset[1] : -0.06;
  const shAlpha = opts.shadowAlpha ?? 1.05;
  const shBlur = opts.shadowBlur ?? 2.4;
  let halfT = opts.halfT ?? mat.halfT;
  let radius = opts.radius ?? mat.radius;
  radius = Math.min(radius, halfT * 0.98);

  let sdf = signedDistance(alpha, size, size);
  sdf = smooth(sdf, size, 4);
  const sampleXY = sampler(sdf, size, world);

  // nesne sınırları (renk rampası buna yayılır)
  const bb = opts.bbox || bboxOf(alpha, size, world);
  const bx0 = bb[0], bx1 = bb[1], by0 = bb[2], by1 = bb[3];
  const bbw = Math.max(0.001, bx1 - bx0);
  const bbh = Math.max(0.001, by1 - by0);

  const L = norm(lightDir);
  const V = [0, 0, 1];
  const Hv = norm([L[0] + V[0], L[1] + V[1], L[2] + V[2]]);
  const px = world / out;

  const col = Buffer.alloc(out * out * 4);
  const shadow = Buffer.alloc(out * out * 4);

  for (let j = 0; j < out; j++) {
    const y = -((j + 0.5) * px - world / 2);
    for (let i = 0; i < out; i++) {
      const x = (i + 0.5) * px - world / 2;
      const s2 = sampleXY(x, y);

      // temas gölgesi (ışık yönünün tersine kaydırılmış silüet)
      const sShadow = sampleXY(x + shOffX, y - shOffY);
      const kS = (j * out + i) * 4;
      if (withShadow && sShadow < 0.9) {
        const aS = Math.min(1, Math.max(0, (0.9 - sShadow) / 1.9)) * shAlpha;
        shadow[kS] = 46; shadow[kS + 1] = 11; shadow[kS + 2] = 62;
        shadow[kS + 3] = Math.round(aS * 255);
      }

      if (s2 > 0) continue; // siluet dışı

      // kesme (coverage) → kenar yumuşatma
      const cover = Math.min(1, Math.max(0, 0.5 - s2 / px));

      // yuvarlatılmış kenar (fillet) — analitik yüzey
      const a = Math.min(radius, Math.max(0, s2 + radius));
      const filletZ = Math.sqrt(Math.max(0, radius * radius - a * a));

      // 2B dış normal
      const e = 0.12;
      const gx = sampleXY(x + e, y) - sampleXY(x - e, y);
      const gy = sampleXY(x, y + e) - sampleXY(x, y - e);
      const gl = Math.hypot(gx, gy) || 1;
      const n2x = gx / gl, n2y = gy / gl;
      const k = radius > 0 ? a / radius : 0;
      // ── kubbe (pillow) profili: düz yüz yerine içe doğru yükselen yumuşak
      //    hacim. 3dicons render'larındaki "gerçek nesne" hissini veren şey bu.
      const domeAmp = mat.dome || 0;
      const domeW = mat.domeW || 3.2;
      const innerW = Math.min(1, Math.max(0, (-s2) / (radius * 3 + 1e-6)));
      const uDome = Math.min(1, Math.max(0, -s2 / domeW));
      const dh = domeAmp * (1 - uDome) * (2 / domeW);   // yüzey eğimi
      const Nf0 = [
        n2x * k - dh * n2x * innerW,
        n2y * k - dh * n2y * innerW,
        filletZ / radius,
      ];
      const N = norm([Nf0[0], Nf0[1], Math.max(0.05, Nf0[2])]);

      // renk: sol-üst sıcak → sağ-alt mor (glif başına faz/kaydırma)
      const u = (x - bx0) / bbw;
      const v = (y - by0) / bbh;
      let [r, g, b] = ramp(phase + span * (0.70 * u + 0.30 * (1 - v)));
      r *= mat.dark; g *= mat.dark; b *= mat.dark;

      const ndl = Math.max(0, N[0] * L[0] + N[1] * L[1] + N[2] * L[2]);
      const ndv = Math.max(0, N[2]);
      const ndh = Math.max(0, N[0] * Hv[0] + N[1] * Hv[1] + N[2] * Hv[2]);
      const R = [2 * ndv * N[0], 2 * ndv * N[1], 2 * ndv * N[2] - 1];
      let env = envSample(R);
      if (mat.streak) {
        // fırçalanmış metal: yatay mikro yansıma çizgileri
        const br = hash2(Math.floor(y * 26), 1.7);
        env *= 0.86 + 0.26 * br;
      }

      const shin = mat.shin;
      const specular = Math.pow(ndh, shin) * mat.spec;
      const specWide = Math.pow(ndh, Math.max(6, shin * 0.14)) * mat.spec * 0.30;
      const fresnel = Math.pow(1 - ndv, 3.2) * mat.fres;

      // gövde rengi (metal: dielektrik difüz düşük, yansıma renkli)
      let amb = mat.amb + 0.10 * Math.max(0, N[1]);
      const bounce = mat.bounce * Math.max(0, -N[1]) * Math.max(0, 1 - Math.abs(N[0]));
      let dif = mat.dif * Math.pow(ndl, 0.9);

      // geniş yumuşak parlama (stüdyo yansıması)
      const hx = x - 3.6, hy = y - 7.4;
      const sheen = mat.sheen ?? 0.26;
      const broad = Math.exp(-(hx * hx + hy * hy) / (2 * 3.4 * 3.4)) * sheen * 0.86 * ndv;

      // ışık geçirgenlik / iç ışıma
      let inner = 0, glassRim = 0;
      if (mat.grain) {
        const n = (hash2(i * 0.7, j * 0.7) - 0.5) * mat.grain * 255;
        r += n; g += n; b += n;
      }
      if (mat.inner) {
        const core = Math.min(1, Math.max(0, (-s2) / (halfT * 4.5 + 0.001)));
        inner = Math.pow(core, 1.3) * mat.inner * 46;
        r += inner * 0.9; g += inner * 0.5; b += inner * 0.7;
      }
      if (mat.glassy) {
        // cam: kalınlık arttıkça koyulaşan gövde + kenarda ışık kırılması
        // cam okuması: siluete yakın KOYU duvar çizgisi + hemen dışında parlak
        // kırılma çizgisi (laboratuvar şişesi görünümü)
        const q = Math.min(1, Math.max(0, a / radius));
        const wallDark = Math.exp(-(((q - 0.66) / 0.30) ** 2)) * 0.45;
        r *= 1 - wallDark; g *= 1 - wallDark * 1.08; b *= 1 - wallDark * 0.92;
        glassRim = Math.exp(-(((q - 0.55) / 0.22) ** 2)) * 0.10
                 + Math.pow(1 - ndv, 2.6) * 0.22;
      }

      // ── yüzey derinliği (orijinal render'lardaki "gerçek nesne" hissi) ──
      // a) alt tarafa doğru koyulaşma (zemine yakınlık)
      const yN = (y - by0) / bbh;
      const bottomAO = 0.80 + 0.20 * Math.min(1, Math.max(0, yN));
      // b) pah dibindeki ince gölge çizgisi — düz yüz ile yuvarlatılmış kenarın
      //    birleştiği yerde oluşan gerçek temas kırışıklığı
      const qa = radius > 0 ? a / radius : 0;
      const crease = Math.exp(-(((qa - 0.62) / 0.24) ** 2)) * 0.16;
      // c) üst yarıda yumuşak stüdyo ışık bandı (softbox yansıması)
      const band = Math.exp(-(((yN - 0.74) / 0.26) ** 2)) * 0.10 * ndv;

      // siluetin hemen içinde ince koyu kenar (arka planla temas gölgesi)
      const rimDark = 1 - (mat.rimDark ?? 0.12) * Math.pow(qa, 3.2);
      const vGrad = 0.80 + 0.28 * Math.min(1, Math.max(0, yN)); // tepe parlak, taban koyu
      const ao = Math.min(1, Math.max(0.60, 1 + s2 * 0.42)) * bottomAO * (1 - crease) * rimDark * vGrad;
      const sh = (amb + dif + bounce) * ao;
      r *= 1 + band; g *= 1 + band; b *= 1 + band;

      if (mat.metallic) {
        // METAL: difüz yok — renk tamamen stüdyo yansımasından gelir.
        // Yansıma iki ölçekte örneklenir (keskin + yumuşak) → fırçalanmış his.
        const Rs = norm([R[0] * 0.55, R[1] * 0.55, R[2] * 0.55 + 0.45]);
        const eSoft = 0.52 * env + 0.48 * envSample(Rs);
        const envVal = 0.24 + 1.90 * eSoft;
        const specSharp = Math.pow(ndh, 240) * 1.10 + Math.pow(ndh, 34) * 0.30;
        const rim = Math.pow(1 - ndv, 3.6) * 0.85;
        const albR = Math.min(1, r / 255), albG = Math.min(1, g / 255), albB = Math.min(1, b / 255);
        const lit = envVal * ao * (0.94 + 0.06 * (mat.streak ? hash2(Math.floor(y * 30), 2.3) : 0.5));
        r = 255 * (Math.min(1.12, albR * lit) + specSharp + rim * 0.85);
        g = 255 * (Math.min(1.12, albG * lit) + specSharp + rim * 0.85);
        b = 255 * (Math.min(1.15, albB * lit) + specSharp * 1.04 + rim * 0.9);
      } else {
        const white = 255 * (specular + specWide + fresnel * 0.55 + broad + glassRim) + env * 40 * mat.env;
        r = r * sh + white;
        g = g * sh + white;
        b = b * sh + white * 1.02;
      }

      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      const satK = mat.sat * 1.30;
      r = lum + (r - lum) * satK;
      g = lum + (g - lum) * satK;
      b = lum + (b - lum) * satK;

      col[kS] = Math.min(255, Math.max(0, Math.pow(Math.min(255, r) / 255, 0.90) * 255 * exposure));
      col[kS + 1] = Math.min(255, Math.max(0, Math.pow(Math.min(255, g) / 255, 0.90) * 255 * exposure));
      col[kS + 2] = Math.min(255, Math.max(0, Math.pow(Math.min(255, b) / 255, 0.90) * 255 * exposure));
      col[kS + 3] = Math.round(255 * cover * Math.min(1, Math.max(0, 0.5 - s2 / px + 0.5)));
    }
  }

  const obj = await sharp(col, { raw: { width: out, height: out, channels: 4 } }).png().toBuffer();
  const shp = await sharp(shadow, { raw: { width: out, height: out, channels: 4 } })
    .blur(shBlur).png().toBuffer();

  return sharp({ create: { width: out, height: out, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: shp, top: 0, left: 0 }, { input: obj, top: 0, left: 0 }])
    .png().toBuffer();
}

function norm(v) {
  const n = Math.hypot(v[0], v[1], v[2]) || 1;
  return [v[0] / n, v[1] / n, v[2] / n];
}

module.exports = { render, signedDistance, smooth, sampler, ramp, RAMP, MATERIALS, bboxOf };
