// 55 ikonun TEK TEK kurulmuş 3B kompozisyonları.
//
// Her ikon kendi ilkellerinden oluşuyor: gövde, kapak, çerçeve, sap, mercek,
// ayak, kadran, ibre… Parçalar gerçek 3B hacimler; birleşim yerleri gerektiğinde
// yumuşak kaynaşma (k), gerektiğinde keskin ek yeri ile kuruluyor. Renk ekseni
// (dirDeg), rampa başlangıcı (phase) ve malzeme ailesi ikon başına farklı.
//
// Bu dosya yalnızca GEOMETRİ + MALZEME tanımlar; render scene3d.js'te.

const S = require('./scene3d.js');
const { rotator, clamp } = S;

// ── yerleştirme yardımcıları ───────────────────────────────────────────────
function place(o, local) {
  const rot = o.rot ? rotator(o.rot[0] || 0, o.rot[1] || 0, o.rot[2] || 0) : null;
  const cx = o.cx || 0, cy = o.cy || 0, cz = o.cz || 0;
  const f = rot
    ? (x, y, z) => {
      const [X, Y, Z] = rot(x - cx, y - cy, z - cz);
      return local(X, Y, Z);
    }
    : (x, y, z) => local(x - cx, y - cy, z - cz);
  const p = { f, mat: o.mat || 'gloss' };
  if (o.k) p.k = o.k;
  if (o.sub) p.sub = true;
  if (o.col) p.col = o.col;
  return p;
}
// yuvarlatılmış kutu
const box = (cx, cy, cz, hx, hy, hz, r, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdRoundBox(x, y, z, hx, hy, hz, r));
// küre / elipsoid
const sph = (cx, cy, cz, r, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdSphere(x, y, z, r));
const ell = (cx, cy, cz, rx, ry, rz, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => {
  const k = Math.min(rx, ry, rz);
  const d = Math.hypot(x / rx, y / ry, z / rz) - 1;
  return d * k;
});
// kapsül
const cap = (x1, y1, z1, x2, y2, z2, r, o = {}) => place({ ...o }, (x, y, z) => S.sdCapsule(x, y, z, x1, y1, z1, x2, y2, z2, r));
// silindir (axis: 'x'|'y'|'z')
const cyl = (axis, cx, cy, cz, r, h, rr, o = {}) => {
  const local = axis === 'y' ? (x, y, z) => S.sdCylY(x, y, z, r, h, rr)
    : axis === 'z' ? (x, y, z) => S.sdCylZ(x, y, z, r, h, rr)
      : (x, y, z) => S.sdCylZ(z, x, y, r, h, rr);
  return place({ cx, cy, cz, ...o }, local);
};
// torus (axis: eksen adı)
const tor = (axis, cx, cy, cz, R, r, o = {}) => {
  const local = axis === 'z' ? (x, y, z) => S.sdTorusZ(x, y, z, R, r)
    : axis === 'y' ? (x, y, z) => S.sdTorusY(x, y, z, R, r)
      : (x, y, z) => S.sdTorusX(x, y, z, R, r);
  return place({ cx, cy, cz, ...o }, local);
};
// koni: taban y=0'da, tepe y=h'de
const cone = (cx, cy, cz, r1, r2, h, rr, o = {}) => place({ cx, cy, cz, ...o }, (x, y, z) => S.sdCone(x, y, z, r1, r2, h, rr));
// 2B çokgeni z boyunca ekstrüde et
const poly = (pts, cz, hz, r, o = {}) => place({ cz, ...o }, (x, y, z) => S.extrudeZ(x, y, z, pts, hz, r) - r * 0);

const D2R = Math.PI / 180;
const rot2 = (deg, x, y) => {
  const a = deg * D2R, c = Math.cos(a), s = Math.sin(a);
  return [x * c - y * s, x * s + y * c];
};
// yay ucuna teğet oturan ok başı (taban y=0, tepe y=h; +y ekseni yön)
function tangentHead(cx, cy, tangent, r1, h, rr, o = {}) {
  const [tx, ty] = tangent;
  const ang = Math.atan2(-tx, ty);
  return cone(cx, cy, 0, r1, 0.28, h, rr, { rot: [0, 0, ang], ...o });
}

// yay üzerinde kapsül zinciri
function arc(radius, a0, a1, r, seg, o = {}) {
  // segment sayısı: kiriş aralığı yarıçapın %55'ini geçmesin → pürüzsüz yay
  const len = Math.abs(a1 - a0) * radius;
  seg = Math.max(seg, Math.min(48, Math.ceil(len / (r * 0.55))));
  const out = [];
  for (let i = 0; i < seg; i++) {
    const t0 = a0 + (a1 - a0) * (i / seg);
    const t1 = a0 + (a1 - a0) * ((i + 1) / seg);
    const x0 = Math.cos(t0) * radius, y0 = Math.sin(t0) * radius;
    const x1 = Math.cos(t1) * radius, y1 = Math.sin(t1) * radius;
    out.push(cap(x0, y0, 0, x1, y1, 0, r, { ...o, k: o.k === undefined ? 1.0 : o.k }));
  }
  return out;
}

// ── ikon başına renk ekseni (isimden türetilen, tekrar etmeyen dağılım) ────
function sig(name) {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 131 + name.charCodeAt(i)) % 100000;
  return {
    dirDeg: 222 + (h % 96),                // sıcak uç üstte, çivit altta; eğim ikon başına
    phase: ((h >> 5) % 16) / 100,
    span: 0.86 + ((h >> 9) % 12) / 100,
  };
}

const BB = [-10.8, 10.8, -10.8, 10.8, -5, 5];
// nesne glifleri (obje öneki ile kaydedilir)
const OBJ_NAMES = new Set(['sync', 'sliders', 'download', 'upload', 'timer', 'code', 'tv', 'tag', 'backup']);

// ══════════════════════════════════════════════════════════════════════════
// KONTROL GLİFLERİ
// ══════════════════════════════════════════════════════════════════════════
const ICONS = {};

// ── incelen kol (konik kapsül): iki nokta arasında, uçta daralan ──────────
function taper(x1, y1, x2, y2, r1, r2, rr, o = {}) {
  const dx = x2 - x1, dy = y2 - y1;
  const len = Math.hypot(dx, dy);
  const ang = Math.atan2(-dx, dy);
  return cone(x1, y1, o.z || 0, r1, r2, len, rr, { ...o, rot: [0, 0, ang] });
}

// uç: (x,y) tabanından (dx,dy) yönüne bakan konik baş
function head(x, y, dx, dy, r1, h, rr, o = {}) {
  const ang = Math.atan2(-dx, dy);
  return cone(x, y, o.z || 0, r1, 0.24, h, rr, { ...o, rot: [0, 0, ang] });
}

// kapat: makine parçası gibi X — dışta kalın, ortada ince + krem perçin
ICONS.close = () => {
  const c = 6.3;
  return { parts: [
    taper(-c, -c, c, c, 1.85, 1.55, 0.55, { mat: 'gloss' }),
    taper(-c, c, c, -c, 1.85, 1.55, 0.55, { mat: 'gloss', k: 1.2 }),
    cyl('z', 0, 0, 0.9, 1.15, 0.55, 0.3, { mat: 'cream', col: [248, 236, 218] }),
    cyl('z', 0, 0, 1.3, 0.5, 0.35, 0.15, { mat: 'ink', col: [70, 40, 92] }),
  ] };
};

// eksi: fiziksel tuş — gövde + oyuk oluk + krem iç bar
ICONS.minus = () => ({
  parts: [
    box(0, 0, 0, 5.9, 1.85, 1.35, 1.3, { mat: 'clay' }),
    box(0, 0, 1.35, 5.0, 0.62, 0.30, 0.22, { mat: 'ink', col: [78, 44, 100], sub: true }),
    box(0, 0, 1.2, 4.9, 0.55, 0.28, 0.2, { mat: 'cream', col: [246, 232, 212] }),
  ],
  bbox: [-7, 7, -3.4, 3.4, -3, 3],
});

// chevron: uca doğru incelen iki kol + krem uç boncuğu
function chevron(deg) {
  const P = (x, y) => rot2(deg, x, y);
  const [ax, ay] = P(-6.3, -3.4);
  const [bx, by] = P(0, 2.5);
  const [cx2, cy2] = P(6.3, -3.4);
  return { parts: [
    taper(ax, ay, bx, by, 1.75, 1.0, 0.5, { mat: 'gel' }),
    taper(bx, by, cx2, cy2, 1.0, 1.75, 0.5, { mat: 'gel', k: 1.1 }),
    sph(bx, by, 0.35, 0.72, { mat: 'cream', col: [250, 238, 220] }),
  ] };
}
ICONS.chevron_up = () => chevron(0);
ICONS.chevron_down = () => chevron(180);
ICONS.chevron_left = () => chevron(90);
ICONS.chevron_right = () => chevron(-90);

// ok: incelen gövde + krem bilezik + konik uç (dart gibi)
function arrow(deg) {
  const P = (x, y) => rot2(deg, x, y);
  const [x0, y0] = P(0, -7.4);
  const [x1, y1] = P(0, 1.0);
  const [fx, fy] = P(0, 0.6);
  const [hx, hy] = P(0, 1.4);
  return { parts: [
    taper(x0, y0, x1, y1, 1.15, 1.45, 0.5, { mat: 'gel' }),
    cyl('z', fx, fy, 0, 1.62, 0.36, 0.16, { mat: 'cream', col: [248, 236, 218] }),
    cone(hx, hy, 0, 2.75, 0.28, 4.6, 0.6, { rot: [0, 0, (deg + 180) * D2R - Math.PI], mat: 'gel', k: 0.7 }),
  ] };
}
ICONS.arrow_up = () => arrow(0);
ICONS.arrow_down = () => arrow(180);
ICONS.arrow_left = () => arrow(90);
ICONS.arrow_right = () => arrow(-90);

// göz: badem sklera + krom halka + obsidyen iris + cam bebek
ICONS.eye = () => ({
  parts: [
    ell(0, 0, 0, 8.4, 4.9, 1.75, { mat: 'glass' }),
    tor('z', 0, -0.1, 1.55, 2.85, 0.5, { mat: 'cream', col: [246, 232, 212] }),
    cyl('z', 0, -0.1, 1.1, 2.45, 1.0, 0.28, { mat: 'obsidian' }),
    // iris dokusu: koyu halka + hafif açık halka
    tor('z', 0, -0.1, 1.75, 1.55, 0.30, { mat: 'ink', col: [122, 74, 150] }),
    sph(0, -0.1, 2.2, 1.25, { mat: 'obsidian' }),
    sph(0.45, 0.42, 3.0, 0.4, { mat: 'cream', col: [252, 246, 240] }),
    sph(-0.5, -0.7, 2.95, 0.22, { mat: 'cream', col: [232, 218, 236] }),
  ],
  bbox: [-9.2, 9.2, -5.8, 5.8, -3, 4],
});

// göz kapalı: göz + çapraz bant
ICONS.eyeOff = () => ({
  parts: [
    ell(0, 0.4, 0, 8.6, 5.0, 1.6, { mat: 'glass' }),
    tor('z', 0, 0.4, 1.4, 3.0, 0.66, { mat: 'chrome' }),
    cyl('z', 0, 0.4, 1.0, 2.5, 1.0, 0.3, { mat: 'obsidian' }),
    cap(-7.6, -7.0, 1.2, 7.6, 7.8, 1.2, 1.15, { mat: 'obsidian' }),
  ],
  bbox: [-9.6, 9.6, -8.6, 9.0, -3, 4],
});

// huni: koni gövde + krom ağız + boyun + jöle bant
ICONS.funnel = () => ({
  parts: [
    cone(0, 1.0, 0, 1.35, 5.7, 6.4, 0.6, { mat: 'glass' }),
    tor('y', 0, 7.3, 0, 5.6, 0.34, { mat: 'cream', col: [232, 214, 194] }),
    cyl('y', 0, -0.2, 0, 1.35, 1.35, 0.4, { mat: 'glass', k: 0.8 }),
    tor('y', 0, -1.6, 0, 1.4, 0.34, { mat: 'cream', col: [236, 220, 200] }),
  ],
  bbox: [-6.6, 6.6, -3.6, 8.2, -3, 3],
});

// sıralama: alçalan metal çubuklar + parlak ok başı
ICONS.sort = () => ({
  parts: [
    cap(-8.2, 6.0, 0, 4.8, 6.0, 0, 1.1, { mat: 'metal' }),
    cap(-8.2, 0, 0, 2.2, 0, 0, 1.1, { mat: 'metal' }),
    cap(-8.2, -6.0, 0, -0.4, -6.0, 0, 1.1, { mat: 'metal' }),
    taper(7.6, 6.2, 7.6, -3.2, 0.95, 1.15, 0.4, { mat: 'gloss' }),
    head(7.6, -3.4, 0, -1, 2.35, 3.6, 0.5, { mat: 'gloss', k: 0.8 }),
  ],
  bbox: [-10, 10, -9.2, 8, -3, 3],
});

// ızgara: obsidyen tepsi + 4 mat karo
ICONS.grid = () => ({
  parts: [
    box(0, 0, -1.2, 8.9, 8.9, 1.2, 1.0, { mat: 'obsidian' }),
    box(-4.15, 4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(4.15, 4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(-4.15, -4.15, 0.45, 3.05, 3.05, 1.25, 0.5, { mat: 'clay' }),
    box(4.15, -4.15, 0.55, 3.05, 3.05, 1.35, 0.5, { mat: 'clay' }),
    // seçili karo: krem çerçeve + hafif yükselti
    box(4.15, -4.15, 1.55, 2.1, 2.1, 0.55, 0.35, { mat: 'cream', col: [244, 230, 210] }),
    box(4.15, -4.15, 1.85, 1.3, 1.3, 0.35, 0.25, { mat: 'ink', col: [86, 48, 108] }),
  ],
  bbox: [-9.6, 9.6, -9.6, 9.6, -4, 3],
});

// liste: 3 satır + madde işaretleri
ICONS.list = () => ({
  parts: [
    // krem sayfa
    box(0, 0, -0.9, 8.4, 7.2, 0.85, 0.75, { mat: 'cream', col: [238, 226, 208] }),
    sph(-6.2, 4.6, 0.9, 0.9, { mat: 'obsidian' }),
    sph(-6.2, 0, 0.9, 0.9, { mat: 'obsidian' }),
    sph(-6.2, -4.6, 0.9, 0.9, { mat: 'obsidian' }),
    taper(-4.0, 4.6, 7.4, 4.6, 1.25, 1.05, 0.45, { mat: 'clay' }),
    taper(-4.0, 0, 7.4, 0, 1.25, 1.05, 0.45, { mat: 'clay' }),
    taper(-4.0, -4.6, 4.6, -4.6, 1.25, 1.05, 0.45, { mat: 'clay' }),
  ],
  bbox: [-9.4, 9.6, -7.2, 7.2, -3, 3],
});

// akış: iki kalın kauçuk şerit
ICONS.stream = () => ({
  parts: [
    box(0, 3.3, 0, 8.2, 1.75, 1.5, 1.4, { mat: 'rubber' }),
    box(0, -3.3, 0, 8.2, 1.75, 1.5, 1.4, { mat: 'rubber' }),
    box(0, 3.3, 1.4, 3.0, 0.45, 0.35, 0.3, { mat: 'ink', col: [78, 44, 100], sub: true }),
    box(0, -3.3, 1.4, 3.0, 0.45, 0.35, 0.3, { mat: 'ink', col: [78, 44, 100], sub: true }),
  ],
  bbox: [-9.6, 9.6, -5.6, 5.6, -3, 3],
});

// carousel: orta kart + iki yan plaka (hafif dönük)
ICONS.carousel = () => ({
  parts: [
    box(0, 0, 0.8, 4.4, 6.2, 1.1, 0.9, { mat: 'gloss' }),
    box(0, 0, 2.0, 2.4, 3.5, 0.5, 0.35, { mat: 'cream', col: [232, 216, 194] }),
    box(-7.2, 0, -0.5, 1.9, 4.4, 0.7, 0.6, { rot: [0, 0.6, 0], mat: 'gloss', k: 0.4 }),
    box(7.2, 0, -0.5, 1.9, 4.4, 0.7, 0.6, { rot: [0, -0.6, 0], mat: 'gloss', k: 0.4 }),
  ],
  bbox: [-10.2, 10.2, -7.2, 7.2, -3, 3],
});

// üç nokta: üç jöle küre
ICONS.more = () => ({
  parts: [
    sph(0, 6.2, 0, 1.75, { mat: 'gel' }),
    sph(0, 0, 0, 1.9, { mat: 'gel' }),
    sph(0, -6.2, 0, 1.75, { mat: 'gel' }),
    sph(-0.5, 6.7, 1.4, 0.34, { mat: 'cream', col: [252, 242, 226] }),
    sph(-0.5, 0.5, 1.5, 0.36, { mat: 'cream', col: [252, 242, 226] }),
    sph(-0.5, -5.7, 1.4, 0.34, { mat: 'cream', col: [252, 242, 226] }),
  ],
  bbox: [-3, 3, -8.4, 8.4, -3, 3],
});

// dışa aç: C çerçeve + çapraz ok
ICONS.external = () => ({
  parts: [
    cap(-6.6, 5.6, 0, -6.6, -5.6, 0, 1.3, { mat: 'obsidian' }),
    cap(-6.6, -5.6, 0, 1.6, -5.6, 0, 1.3, { mat: 'obsidian' }),
    cap(-6.6, 5.6, 0, -1.2, 5.6, 0, 1.3, { mat: 'obsidian' }),
    taper(0.6, 0.6, 4.6, 4.6, 0.8, 1.0, 0.35, { mat: 'gloss' }),
    head(4.3, 4.3, 0.707, 0.707, 2.15, 3.4, 0.45, { mat: 'gloss', k: 0.6 }),
  ],
  bbox: [-9, 10.4, -8.4, 9.4, -3, 3],
});

// tam ekran: 4 köşe dirseği
ICONS.fullscreen = () => {
  const parts = [];
  for (const sx of [-1, 1]) for (const sy of [-1, 1]) {
    parts.push(taper(sx * 8.0, sy * 2.6, sx * 8.0, sy * 8.0, 1.10, 1.45, 0.5, { mat: 'obsidian' }));
    parts.push(taper(sx * 2.6, sy * 8.0, sx * 8.0, sy * 8.0, 1.10, 1.45, 0.5, { mat: 'obsidian', k: 1.35 }));
    parts.push(sph(sx * 7.15, sy * 7.15, 0.55, 0.72, { mat: 'cream', col: [242, 228, 208] }));
  }
  return { parts, bbox: [-9.9, 9.9, -9.9, 9.9, -3, 3] };
};

// kırpma: iki dirsek
ICONS.crop = () => ({
  parts: [
    cap(-4.2, 7.4, 0, -4.2, -4.2, 0, 1.25, { mat: 'gloss' }),
    cap(-4.2, -4.2, 0, 7.4, -4.2, 0, 1.25, { mat: 'gloss', k: 1.3 }),
    cap(4.2, -7.4, 0, 4.2, 4.2, 0, 1.25, { mat: 'gloss' }),
    cap(4.2, 4.2, 0, -7.4, 4.2, 0, 1.25, { mat: 'gloss', k: 1.3 }),
    sph(-4.2, -4.2, 0.9, 0.55, { mat: 'cream', col: [244, 230, 210] }),
    sph(4.2, 4.2, 0.9, 0.55, { mat: 'cream', col: [244, 230, 210] }),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// döndür: yay + ok başı
ICONS.rotate = () => {
  const parts = [
    box(0, 0, 0, 6.3, 6.3, 0.58, 1.0, { mat: 'obsidian' }),
    box(0, 0, 0.70, 5.15, 5.15, 0.24, 0.55, { mat: 'ink', col: [38, 21, 54], sub: true }),
  ];
  const R = 3.6, a0 = 100 * D2R, a1 = -195 * D2R;
  parts.push(...arc(R, a0, a1, 0.46, 26, { mat: 'cream', col: [242, 228, 208], cz: 1.05 }));
  const tan = [Math.sin(a1), -Math.cos(a1)];
  parts.push(head(Math.cos(a1) * R, Math.sin(a1) * R, tan[0], tan[1], 1.5, 2.8, 0.45,
    { z: 1.05, mat: 'cream', col: [242, 228, 208] }));
  return { parts, bbox: [-7.2, 7.2, -7.2, 7.2, -3, 3.2] };
};

// en-boy: çerçeve + iki ince bölme
ICONS.aspect = () => ({
  parts: [
    box(0, 0, 0, 9.0, 6.0, 1.1, 0.95, { mat: 'gloss' }),
    box(-2.9, 0, 1.35, 0.42, 4.4, 0.5, 0.3, { mat: 'obsidian' }),
    box(2.9, 0, 1.35, 0.42, 4.4, 0.5, 0.3, { mat: 'obsidian' }),

    box(0, 0, -0.2, 9.0, 6.0, 0.6, 0.5, { mat: 'clay', k: 1.0 }),
  ],
  bbox: [-9.8, 9.8, -6.8, 6.8, -3, 3],
});

// bulanıklık: damla (küre merdiveni + yumuşak kaynaşma → organik gözyaşı)
ICONS.blur = () => ({
  parts: [
    sph(0, -3.6, 0, 4.2, { mat: 'glass' }),
    sph(0, -0.6, 0, 3.5, { mat: 'glass', k: 1.6 }),
    sph(0, 1.9, 0, 2.7, { mat: 'glass', k: 1.5 }),
    sph(0, 3.9, 0, 1.8, { mat: 'glass', k: 1.3 }),
    sph(0, 5.4, 0, 0.85, { mat: 'glass', k: 1.1 }),
    sph(0, 6.5, 0, 0.4, { mat: 'glass', k: 0.8 }),
    sph(-1.5, -3.2, 3.6, 0.5, { mat: 'cream', col: [250, 242, 232] }),
    // yanında iki küçük damla
    sph(-6.0, -4.6, 0, 1.35, { mat: 'glass' }),
    sph(6.0, -5.4, 0, 0.95, { mat: 'glass' }),
  ],
  bbox: [-7.8, 7.8, -8.2, 7.4, -4, 4],
});

// hizalama üçlüsü
function textPage(rows, matr) {
  const parts = [box(0, 0, -0.9, 8.5, 7.2, 0.8, 0.7, { mat: 'cream', col: [236, 224, 206] })];
  for (const [x0, x1, y] of rows) parts.push(taper(x0, y, x1, y, 1.3, 1.1, 0.45, { mat: matr }));
  return parts;
}
ICONS.alignLeft = () => ({
  parts: textPage([[-7.2, 7.2, 4.6], [-7.2, 1.6, 0], [-7.2, 5.4, -4.6]], 'clay'),
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});
ICONS.alignCenter = () => ({
  parts: textPage([[-6.9, 6.9, 4.6], [-3.4, 3.4, 0], [-5.6, 5.6, -4.6]], 'clay'),
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});
ICONS.alignJustify = () => ({
  parts: textPage([[-7.2, 7.2, 4.6], [-7.2, 7.2, 0], [-7.2, 7.2, -4.6]], 'rubber'),
  bbox: [-9.6, 9.6, -7.2, 7.2, -3, 3],
});

// kenarlık: içi boş kare çerçeve + iç plaka
ICONS.border = () => ({
  parts: [
    box(0, 0, 0, 8.6, 8.6, 1.0, 1.0, { mat: 'obsidian' }),
    box(0, 0, 0, 6.6, 6.6, 2.2, 0.6, { mat: 'obsidian', sub: true }),
    box(0, 0, -0.2, 4.6, 4.6, 0.9, 0.45, { mat: 'clay' }),
    sph(-7.1, 7.1, 0.8, 0.5, { mat: 'cream', col: [236, 220, 198] }),
    sph(7.1, 7.1, 0.8, 0.5, { mat: 'cream', col: [236, 220, 198] }),
    sph(-7.1, -7.1, 0.8, 0.5, { mat: 'cream', col: [236, 220, 198] }),
    sph(7.1, -7.1, 0.8, 0.5, { mat: 'cream', col: [236, 220, 198] }),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// tümünü seç: 8 kesikli çizgi
ICONS.selectAll = () => {
  const parts = [];
  const R = 7.4, L = 3.9, c = 3.2;
  for (const s of [-1, 1]) {
    parts.push(cap(-c - L, s * R, 0, -c, s * R, 0, 0.92, { mat: 'gloss' }));
    parts.push(cap(c, s * R, 0, c + L, s * R, 0, 0.92, { mat: 'gloss', k: 1.25 }));
    parts.push(cap(s * R, -c - L, 0, s * R, -c, 0, 0.92, { mat: 'gloss' }));
    parts.push(cap(s * R, c, 0, s * R, c + L, 0, 0.92, { mat: 'gloss', k: 1.25 }));
  }
  return { parts, bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3] };
};

// sürükle: iki tutamak çubuğu + krom boncuklar
ICONS.drag = () => {
  const parts = [];
  for (const sy of [2.4, -2.4]) {
    parts.push(box(0, sy, 0, 5.6, 0.9, 1.0, 0.8, { mat: 'rubber' }));
    for (const dx of [-0.9, 0, 0.9]) {
      parts.push(box(dx, sy, 1.25, 0.16, 0.6, 0.2, 0.14, { mat: 'cream', col: [236, 224, 206] }));
    }
    parts.push(sph(-4.6, sy, 1.15, 0.6, { mat: 'chrome', col: [232, 224, 210] }));
    parts.push(sph(4.6, sy, 1.15, 0.6, { mat: 'chrome', col: [232, 224, 210] }));
  }
  return { parts, bbox: [-7, 7, -4.4, 4.4, -3, 3] };
};

// yoğunluk: üç ince satır
ICONS.density = () => ({
  parts: [
    taper(-8.2, 5.4, 8.2, 5.4, 1.0, 0.9, 0.35, { mat: 'paper' }),
    taper(-8.2, 2.7, 8.2, 2.7, 0.95, 0.85, 0.35, { mat: 'paper' }),
    taper(-8.2, 0, 6.4, 0, 0.95, 0.85, 0.35, { mat: 'paper' }),
    taper(-8.2, -2.7, 8.2, -2.7, 0.95, 0.85, 0.35, { mat: 'paper' }),
    taper(-8.2, -5.4, 5.2, -5.4, 1.0, 0.9, 0.35, { mat: 'paper' }),
    cyl('z', 8.6, 0, 0, 1.0, 1.4, 0.4, { mat: 'cream', col: [236, 220, 198] }),
  ],
  bbox: [-9.8, 10.4, -6.6, 6.6, -3, 3],
});

// klavye: kauçuk gövde + parlak tuş başlıkları
ICONS.keyboard = () => {
  const parts = [box(0, 0, 0, 9.4, 5.6, 1.15, 0.95, { mat: 'rubber' }),
    box(0, 0, -1.35, 9.0, 5.2, 0.55, 0.45, { mat: 'obsidian' })];
  const key = (cx, cy, hx, hy) => parts.push(box(cx, cy, 1.5, hx, hy, 0.62, 0.35, { mat: 'gloss' }));
  for (let r = 0; r < 3; r++) {
    const y = 3.2 - r * 2.4;
    for (let c = 0; c < 5; c++) key(-7.0 + c * 2.6 - (r === 2 ? 0 : 0.1), y, 0.95, 0.85);
  }
  parts.push(box(2.2, -3.4, 1.5, 3.6, 0.75, 0.6, 0.35, { mat: 'gloss' }));
  parts.push(box(-7.0, -3.4, 1.75, 1.5, 0.75, 0.55, 0.28, { mat: 'cream', col: [240, 226, 206] }));
  return { parts, bbox: [-10, 10, -6.4, 6.4, -3, 3] };
};

// giriş alanı: yuvarlak alan + üç nokta + krom imleç
ICONS.input = () => ({
  parts: [
    box(0, 0, 0, 9.0, 4.0, 1.0, 0.9, { mat: 'gloss' }),
    box(0, 0, 1.0, 8.3, 3.3, 0.3, 0.5, { mat: 'cream', col: [240, 226, 204] }),
    taper(-7.4, 1.15, -1.5, 1.15, 0.42, 0.32, 0.3, { mat: 'ink', col: [72, 40, 96], z: 1.34 }),
    taper(-7.4, -1.15, -4.0, -1.15, 0.42, 0.32, 0.3, { mat: 'ink', col: [72, 40, 96], z: 1.34 }),
    box(0.25, 0, 1.34, 0.26, 1.7, 0.32, 0.12, { mat: 'ink', col: [58, 30, 80] }),
  ],
  bbox: [-9.4, 9.4, -4.6, 4.6, -3, 3],
});

// kek: krom tabak + sünger + krema + 3 mum
ICONS.cake = () => ({
  parts: [
    cyl('y', 0, -6.0, 0, 7.1, 0.72, 0.32, { mat: 'cream', col: [250, 236, 214] }),
    cyl('y', 0, -3.3, 0, 5.6, 2.4, 0.6, { mat: 'clay' }),
    tor('y', 0, -0.5, 0, 4.9, 0.95, { mat: 'gel' }),
    cyl('y', -3.0, 1.9, 0, 0.72, 2.4, 0.32, { mat: 'gloss' }),
    cyl('y', 0, 2.5, 0, 0.72, 2.9, 0.32, { mat: 'gloss' }),
    cyl('y', 3.0, 1.9, 0, 0.72, 2.4, 0.32, { mat: 'gloss' }),
    sph(-3.0, 4.7, 0, 0.78, { mat: 'cream', col: [255, 216, 150] }),
    sph(0, 5.8, 0, 0.82, { mat: 'cream', col: [255, 224, 160] }),
    sph(3.0, 4.7, 0, 0.78, { mat: 'cream', col: [255, 216, 150] }),
    // krema gövdeden sarkan damlalar
    sph(-4.7, -1.6, 2.5, 0.55, { mat: 'cream', col: [244, 228, 206] }),
    sph(-1.7, -2.0, 2.8, 0.5, { mat: 'cream', col: [244, 228, 206] }),
    sph(1.7, -2.0, 2.8, 0.5, { mat: 'cream', col: [244, 228, 206] }),
    sph(4.7, -1.6, 2.5, 0.55, { mat: 'cream', col: [244, 228, 206] }),
    // serpme
    box(-2.4, 0.3, 2.6, 0.5, 0.15, 0.3, 0.13, { rot: [0, 0, 0.6], mat: 'cream', col: [252, 238, 214] }),
    box(1.2, 0.5, 2.9, 0.45, 0.14, 0.3, 0.12, { rot: [0, 0, -0.5], mat: 'cream', col: [252, 238, 214] }),
    box(3.4, 0.3, 2.3, 0.4, 0.13, 0.28, 0.11, { rot: [0, 0, 0.2], mat: 'cream', col: [252, 238, 214] }),
  ],
  bbox: [-8.2, 8.2, -7.4, 6.4, -3, 3],
});

// kamera: gövde + namlu + krom halka + cam mercek + deklanşör
ICONS.camera = () => ({
  parts: [
    box(0, 0.3, 0, 7.7, 4.7, 2.0, 1.15, { mat: 'gloss' }),
    box(-6.2, 0.1, 1.1, 1.3, 3.6, 0.9, 0.55, { mat: 'obsidian' }),
    box(4.5, 3.9, -0.3, 1.45, 0.75, 1.05, 0.3, { mat: 'cream', col: [246, 232, 214] }),
    box(-4.9, 3.6, 0.9, 1.4, 0.5, 0.7, 0.22, { mat: 'cream', col: [246, 232, 214] }),
    cyl('z', 0, -0.5, 2.35, 3.3, 1.05, 0.45, { mat: 'obsidian' }),
    tor('z', 0, -0.5, 2.68, 2.85, 0.48, { mat: 'cream', col: [250, 238, 222] }),
    cyl('z', 0, -0.5, 3.15, 2.3, 0.55, 0.28, { mat: 'ink', col: [30, 14, 46] }),
    cyl('z', 0, -0.5, 3.0, 1.75, 0.28, 0.12, { mat: 'ink', col: [58, 32, 78] }),
    sph(0.8, 0.4, 3.6, 0.42, { mat: 'cream', col: [255, 250, 244] }),
  ],
  bbox: [-8.6, 8.6, -6.0, 6.0, -3, 4],
});

// çift ok: iki dışa bakan ok — incelen gövde + krem boncuk
ICONS.doubleArrow = () => ({
  parts: [
    taper(-2.0, 3.6, -7.6, 0, 1.45, 1.2, 0.5, { mat: 'gel' }),
    taper(-7.6, 0, -2.0, -3.6, 1.2, 1.45, 0.5, { mat: 'gel', k: 1.5 }),
    taper(2.0, 3.6, 7.6, 0, 1.45, 1.2, 0.5, { mat: 'gel' }),
    taper(7.6, 0, 2.0, -3.6, 1.2, 1.45, 0.5, { mat: 'gel', k: 1.5 }),
    sph(-7.6, 0, 0.9, 0.62, { mat: 'cream', col: [242, 228, 208] }),
    sph(7.6, 0, 0.9, 0.62, { mat: 'cream', col: [242, 228, 208] }),
  ],
  bbox: [-9.4, 9.4, -5.2, 5.2, -3, 3],
});

// ev: gövde + çatı + krem mahya + bölmeli pencere + kapı
ICONS.home = () => ({
  parts: [
    box(0, -3.4, 0, 6.6, 3.5, 1.5, 0.9, { mat: 'clay' }),
    poly([[-8.0, 0], [8.0, 0], [0, 4.8]], 0, 1.75, 0.95, { cy: 1.0, mat: 'clay', k: 0.7 }),
    cyl('y', 3.3, 5.2, -0.6, 0.78, 1.5, 0.32, { mat: 'obsidian' }),
    // krem mahya çizgisi
    taper(-7.5, 0.9, 7.5, 0.9, 0.55, 0.35, 0.3, { mat: 'cream', col: [244, 230, 210] }),
    // pencere: krem çerçeve + koyu cam + bölme
    box(-4.1, -1.4, 1.5, 1.55, 1.4, 0.5, 0.35, { mat: 'cream', col: [240, 226, 206] }),
    box(-4.1, -1.4, 1.85, 1.12, 1.0, 0.32, 0.22, { mat: 'ink', col: [56, 30, 76] }),
    box(-4.1, -1.4, 2.05, 0.16, 1.0, 0.16, 0.08, { mat: 'cream', col: [236, 222, 202] }),
    box(-4.1, -1.4, 2.05, 1.12, 0.14, 0.16, 0.08, { mat: 'cream', col: [236, 222, 202] }),
    // kapı + krem eşik
    box(0, -4.3, 1.6, 1.85, 2.35, 0.55, 0.45, { mat: 'obsidian' }),
    sph(1.15, -4.3, 2.15, 0.3, { mat: 'cream', col: [252, 242, 228] }),
    box(0, -6.6, 1.55, 2.4, 0.32, 0.5, 0.16, { mat: 'cream', col: [238, 224, 204] }),
  ],
  bbox: [-9.4, 9.4, -8.4, 7.8, -3, 3],
});

// yaprak: eğik elipsoid + krem ana damar + yan damarlar
ICONS.leaf = () => {
  const a = 0.72, ca = Math.cos(a), sa = Math.sin(a);
  const P = (t, n) => [t * ca - n * sa, t * sa + n * ca];
  const parts = [
    ell(0, 0, 0, 7.2, 4.0, 1.35, { rot: [0, 0, a], mat: 'gel' }),
  ];
  // sap: yaprağın uzun ekseni boyunca dışa uzanan kısa dal
  const [sx1, sy1] = P(-6.4, 0), [sx2, sy2] = P(-9.4, 0);
  parts.push(taper(sx1, sy1, sx2, sy2, 1.0, 0.42, 0.35, { mat: 'clay', k: 1.1 }));
  // krem ana damar
  const [mx1, my1] = P(-5.0, 0), [mx2, my2] = P(4.6, 0);
  parts.push(cap(mx1, my1, 1.15, mx2, my2, 1.15, 0.36, { mat: 'cream', col: [242, 230, 212] }));
  // yan damarlar
  for (const t of [-2.4, 0.4, 3.0]) {
    for (const sgn of [-1, 1]) {
      const [ax, ay] = P(t, 0);
      const [bx, by] = P(t - 1.4, sgn * 2.4);
      parts.push(cap(ax, ay, 1.22, bx, by, 1.05, 0.22, { mat: 'cream', col: [236, 224, 206] }));
    }
  }
  return { parts, bbox: [-9.6, 8.4, -9.6, 8.4, -3, 3] };
};

// kar tanesi: ince konik kollar + krem uç buzları + V dallar
ICONS.snow = () => {
  const parts = [
    sph(0, 0, 0.9, 1.2, { mat: 'glass' }),
    sph(0, 0, 1.95, 0.72, { mat: 'cream', col: [244, 232, 216] }),
  ];
  for (let i = 0; i < 6; i++) {
    const a = i * 60 * D2R;
    const dx = Math.cos(a), dy = Math.sin(a);
    const rot = [0, 0, Math.atan2(-dx, dy)];
    parts.push(taper(dx * 0.6, dy * 0.6, dx * 6.3, dy * 6.3, 0.60, 0.30, 0.3, { mat: 'glass' }));
    parts.push(cone(dx * 6.0, dy * 6.0, 0, 0.36, 0.24, 1.6, 0.24, { rot, mat: 'cream', col: [236, 242, 252] }));
    for (const sgn of [-1, 1]) {
      const ba = a + sgn * 0.88;
      const bx = Math.cos(ba), by = Math.sin(ba);
      parts.push(taper(dx * 3.5, dy * 3.5, dx * 3.5 + bx * 2.5, dy * 3.5 + by * 2.5, 0.33, 0.2, 0.2, { mat: 'glass', k: 0.5 }));
      parts.push(sph(dx * 3.5 + bx * 2.6, dy * 3.5 + by * 2.6, 0, 0.22, { mat: 'cream', col: [238, 244, 252] }));
    }
  }
  return { parts, bbox: [-8.4, 8.4, -8.4, 8.4, -3, 3] };
};

// tırnak: topuz + ince kuyruk (” formu, iki işaret)
function quoteMark(cx) {
  return [
    sph(cx, 1.5, 0, 1.85, { mat: 'clay' }),
    taper(cx + 0.55, 1.1, cx - 1.7, -3.5, 1.15, 0.32, 0.32, { mat: 'clay', k: 0.5 }),
  ];
}
ICONS.textQuote = () => ({
  parts: [...quoteMark(-2.9), ...quoteMark(2.9)],
  bbox: [-5.9, 5.9, -4.4, 4.0, -3, 3],
});

// diyez: 4 eğik çubuk
ICONS.textHash = () => ({
  parts: [
    box(-2.5, 0, 0, 0.85, 7.6, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(2.5, 0, 0, 0.85, 7.6, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(0, 2.5, 0, 7.6, 0.85, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    box(0, -2.5, 0, 7.6, 0.85, 1.05, 0.82, { rot: [0, 0, -0.16], mat: 'obsidian' }),
    ...[[-2.5, 2.5], [2.5, 2.5], [-2.5, -2.5], [2.5, -2.5]].map(([x, y]) =>
      sph(x, y, 0.95, 0.62, { mat: 'cream', col: [242, 228, 208] })),
  ],
  bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3],
});

// ── nesne glifleri ────────────────────────────────────────────────────────
// eşitleme: yay + ok başı
ICONS.sync = () => {
  const R = 6.0, parts = [];
  const segs = [[40, 176], [220, 356]];
  for (const [d0, d1] of segs) {
    const a0 = d0 * D2R, a1 = d1 * D2R;
    parts.push(...arc(R, a0, a1, 0.82, 14, { mat: 'gloss', k: 1.5 }));
    const tan = [-Math.sin(a1), Math.cos(a1)];
    parts.push(head(Math.cos(a1) * R + tan[0] * 0.55, Math.sin(a1) * R + tan[1] * 0.55, tan[0], tan[1],
      1.95, 3.0, 0.45, { mat: 'cream', col: [242, 228, 208] }));
  }
  return { parts, bbox: [-9.4, 9.4, -9.4, 9.4, -3, 3] };
};

// kaydırıcılar: 3 ray + 3 krom topuz
ICONS.sliders = () => {
  const parts = [];
  const rails = [[5.6, -2.6], [0, 2.2], [-5.6, -0.8]];
  for (const [y, kx] of rails) {
    parts.push(box(0, y, 0, 7.8, 0.78, 0.9, 0.36, { mat: 'obsidian' }));
    parts.push(box(0, y, 0.92, 6.7, 0.26, 0.2, 0.1, { mat: 'ink', col: [46, 26, 62] }));
    for (const sx of [-1, 1]) {
      parts.push(cyl('z', sx * 3.9, y, 0, 0.52, 1.05, 0.18, { mat: 'cream', col: [238, 224, 204] }));
    }
    parts.push(cyl('z', kx, y, 0.95, 1.72, 1.35, 0.5, { mat: 'chrome' }));
    parts.push(cyl('z', kx, y, 2.35, 1.34, 0.5, 0.24, { mat: 'cream', col: [242, 230, 210] }));
    parts.push(cyl('z', kx, y, 2.6, 0.62, 0.36, 0.16, { mat: 'ink', col: [48, 26, 64] }));
  }
  return { parts, bbox: [-9.4, 9.4, -7.6, 7.6, -3, 3.4] };
};

// indir: aşağı ok + krom tepsi
ICONS.download = () => ({
  parts: [
    taper(0, 7.6, 0, 0.6, 1.15, 1.35, 0.45, { mat: 'gel' }),
    cyl('z', 0, 0.4, 0, 1.55, 0.32, 0.14, { mat: 'cream', col: [242, 228, 208] }),
    cone(0, -0.6, 0, 3.0, 0.3, 4.9, 0.7, { rot: [0, 0, Math.PI], mat: 'gel', k: 0.8 }),
    box(0, -8.0, 0, 7.3, 0.75, 1.5, 0.55, { mat: 'cream', col: [238, 224, 204] }),
    box(-6.5, -6.6, 0, 0.8, 1.6, 1.4, 0.5, { mat: 'cream', col: [228, 213, 192] }),
    box(6.5, -6.6, 0, 0.8, 1.6, 1.4, 0.5, { mat: 'cream', col: [228, 213, 192] }),
  ],
  bbox: [-9.4, 9.4, -9.6, 9, -3, 3],
});

// yükle: yukarı ok + parlak tepsi
ICONS.upload = () => ({
  parts: [
    taper(0, -6.8, 0, 0.2, 1.15, 1.35, 0.45, { mat: 'paper' }),
    cyl('z', 0, 0.5, 0, 1.55, 0.32, 0.14, { mat: 'cream', col: [242, 228, 208] }),
    cone(0, 1.0, 0, 3.0, 0.3, 4.9, 0.7, { mat: 'paper', k: 0.8 }),
    box(0, -8.6, 0, 7.3, 0.72, 1.45, 0.55, { mat: 'gloss' }),
    box(-6.5, -7.3, 0, 0.78, 1.5, 1.35, 0.5, { mat: 'gloss', k: 1.3 }),
    box(6.5, -7.3, 0, 0.78, 1.5, 1.35, 0.5, { mat: 'gloss', k: 1.3 }),
  ],
  bbox: [-9.4, 9.4, -10.2, 6.6, -3, 3],
});

// kronometre: krom çerçeve + kadran + tepe + ibre
ICONS.timer = () => ({
  parts: [
    tor('z', 0, -0.6, 0.55, 5.9, 0.8, { mat: 'cream', col: [244, 230, 212] }),
    cyl('z', 0, -0.6, 0.15, 5.5, 0.85, 0.35, { mat: 'clay' }),
    cyl('y', 0, 5.9, 0, 0.95, 1.0, 0.3, { mat: 'cream', col: [244, 230, 212] }),
    cyl('y', 0, 7.0, 0, 1.8, 0.45, 0.2, { mat: 'cream', col: [244, 230, 212] }),
    box(0.9, 0.7, 1.1, 2.1, 0.34, 0.45, 0.25, { rot: [0, 0, 0.6], mat: 'ink', col: [58, 30, 74] }),
    sph(0, -0.6, 1.3, 0.55, { mat: 'ink', col: [58, 30, 74] }),
    sph(3.1, 2.3, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
    sph(-3.3, 1.6, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
    sph(0.4, -3.6, 1.2, 0.28, { mat: 'cream', col: [250, 238, 222] }),
    box(4.3, -0.6, 1.0, 0.75, 0.2, 0.25, 0.08, { mat: 'cream', col: [246, 234, 216] }),
    box(-4.3, -0.6, 1.0, 0.75, 0.2, 0.25, 0.08, { mat: 'cream', col: [246, 234, 216] }),
    box(0, 4.0, 1.0, 0.2, 0.75, 0.25, 0.08, { mat: 'cream', col: [246, 234, 216] }),
    box(0, -4.9, 1.0, 0.2, 0.75, 0.25, 0.08, { mat: 'cream', col: [246, 234, 216] }),
    box(0, 7.7, 0, 0.35, 0.5, 1.25, 0.14, { mat: 'ink', col: [56, 30, 74] }),
    box(-0.85, 7.3, 0, 0.3, 0.4, 1.15, 0.12, { mat: 'cream', col: [246, 234, 216] }),
    box(0.85, 7.3, 0, 0.3, 0.4, 1.15, 0.12, { mat: 'cream', col: [246, 234, 216] }),
  ],
  bbox: [-7.6, 7.6, -7.4, 8.4, -3, 3],
});

// kod: iki açılı parantez
ICONS.code = () => ({
  parts: [
    taper(-4.4, 4.9, -9.0, 0, 1.45, 1.15, 0.5, { mat: 'obsidian' }),
    taper(-9.0, 0, -4.4, -4.9, 1.15, 1.45, 0.5, { mat: 'obsidian', k: 1.5 }),
    taper(4.4, 4.9, 9.0, 0, 1.45, 1.15, 0.5, { mat: 'obsidian' }),
    taper(9.0, 0, 4.4, -4.9, 1.15, 1.45, 0.5, { mat: 'obsidian', k: 1.5 }),
    sph(-4.2, 5.2, 0, 1.15, { mat: 'cream', col: [242, 228, 208] }),
    sph(-4.2, -5.2, 0, 1.15, { mat: 'cream', col: [242, 228, 208] }),
    sph(4.2, 5.2, 0, 1.15, { mat: 'cream', col: [242, 228, 208] }),
    sph(4.2, -5.2, 0, 1.15, { mat: 'cream', col: [242, 228, 208] }),
  ],
  bbox: [-10.6, 10.6, -6.8, 6.8, -3, 3],
});

// TV: kauçuk kabin + obsidyen ekran + krom ayak
ICONS.tv = () => ({
  parts: [
    box(0, 1.6, 0, 9.0, 6.2, 1.7, 1.1, { mat: 'rubber' }),
    box(0, 2.0, 1.2, 7.5, 4.7, 0.9, 0.5, { mat: 'ink', col: [52, 30, 78] }),
    box(0, 3.6, 1.9, 6.4, 2.0, 0.35, 0.3, { mat: 'ink', col: [86, 54, 122] }),
    box(-2.5, 4.1, 2.0, 3.0, 0.26, 0.16, 0.12, { rot: [0, 0, -0.60], mat: 'cream', col: [212, 220, 240] }),
    sph(3.1, -2.0, 1.75, 0.5, { mat: 'cream', col: [240, 228, 210] }),
    sph(1.7, -2.0, 1.75, 0.38, { mat: 'cream', col: [240, 228, 210] }),
    cyl('y', 0, -6.4, 0, 0.65, 1.3, 0.28, { mat: 'cream', col: [240, 226, 208] }),
    box(0, -8.0, 0, 3.4, 0.75, 1.3, 0.45, { mat: 'cream', col: [240, 226, 208] }),
  ],
  bbox: [-9.8, 9.8, -9.4, 8.4, -3, 3],
});

// etiket: ekstrüde etiket gövdesi + krom kuşgözü
ICONS.tag = () => ({
  parts: [
    poly([[-8.6, 1.4], [-8.6, 8.6], [-1.4, 8.6], [8.6, -1.4], [1.4, -8.6], [-1.4, -8.6]], 0, 1.5, 0.85, { rot: [0, 0, -0.35], mat: 'paper' }),
    tor('z', -5.4, 5.4, 1.55, 1.3, 0.5, { mat: 'cream', col: [242, 228, 210] }),
    cyl('z', -5.4, 5.4, 1.25, 0.8, 1.1, 0.28, { mat: 'ink', col: [54, 28, 72] }),
    box(1.1, -0.6, 1.55, 3.2, 2.1, 0.22, 0.4, { rot: [0, 0, -0.35], mat: 'cream', col: [244, 232, 214] }),
    box(1.3, 0.8, 1.72, 2.3, 0.22, 0.14, 0.07, { rot: [0, 0, -0.35], mat: 'ink', col: [58, 30, 76] }),
    box(1.1, -0.7, 1.72, 1.6, 0.2, 0.14, 0.07, { rot: [0, 0, -0.35], mat: 'ink', col: [58, 30, 76] }),
  ],
  bbox: [-10.4, 10.4, -10.4, 10.4, -3, 3],
});

// yedekle: bulut + yukarı ok
ICONS.backup = () => ({
  parts: [
    sph(-3.4, 0.2, 0, 2.55, { mat: 'gel' }),
    sph(1.9, -0.3, 0, 2.4, { mat: 'gel' }),
    sph(-0.4, 1.9, 0, 2.5, { mat: 'gel', k: 2.4 }),
    box(0, -1.7, 0, 4.5, 1.75, 1.6, 1.25, { mat: 'gel', k: 2.0 }),
    cyl('z', 0, 3.6, 0.75, 1.15, 0.6, 0.26, { mat: 'ink', col: [62, 34, 84] }),
    cap(0, 3.2, 0.95, 0, 6.4, 0.95, 0.58, { mat: 'cream', col: [250, 240, 226] }),
    head(0, 6.4, 0, 1, 1.95, 2.9, 0.5, { z: 0.95, mat: 'cream', col: [250, 240, 226] }),
  ],
  bbox: [-7.4, 7.0, -5.2, 9.6, -3, 4],
});

// ── harf glifleri (maske→SDF ile kalın ekstrüzyon) ────────────────────────
const LETTERS = {
  textBold: { ch: 'B', weight: 800, size: 18 },
  textItalic: { ch: 'I', weight: 800, size: 18, skew: 0.18 },
  textTitle: { ch: 'A', weight: 800, size: 18 },
  textStrike: { ch: 'S', weight: 800, size: 18, strike: true },
};

function scene(name) {
  if (ICONS[name]) {
    const s = ICONS[name]();
    return Object.assign({ bbox: BB, tilt: 0.30 }, s, sig(name));
  }
  // harfler ve isim eşlemeleri çağıran tarafta
  return null;
}

module.exports = { ICONS, LETTERS, OBJ_NAMES, scene, sig, BB, helpers: { box, sph, ell, cap, cyl, tor, cone, poly, arc, rot2 } };
