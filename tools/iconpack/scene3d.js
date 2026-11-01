// Gerçek 3B sahne render'ı — her ikon ayrı bir 3B kompozisyon (SDF ray marching).
//
// Neden yeni hat?
// ---------------
// Önceki hat (sdf3d.js) 2B silueti "levha" olarak ekstrüde ediyordu: her glif aynı
// düz ön yüz + aynı kalınlık + aynı gradyandı. 3dicons nesneleri ise GERÇEK hacim:
// gövde, kapak, çerçeve, sap, mercek, ayak… ayrı ayrı 3B parçalar. Bu yüzden her
// ikon burada kendi ilkel geometrileriyle (yuvarlatılmış kutu, küre, kapsül,
// silindir, torus, koni, ekstrüde poligon) kuruluyor; tek bir stüdyo ışığı altında
// ama KENDİ malzemesi, kendi renk ekseni ve kendi parça hiyerarşisiyle.
//
// Işık/gölge modeli orijinal render'lardan ölçülerek kalibre edildi:
//   • yumuşak ipek/silikon yüzey: keskin beyaz parlama yok, geniş saten parlama var
//   • gövde parlaklığı dar aralıkta (0.86–1.06), gölge yumuşak
//   • silüet kenarı çekirdekten hafif koyu (rim/core ≈ 0.96)
//   • renk rampası ikon başına farklı eksende akıyor (sıcak → macenta → çivit)

const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const mix = (a, b, t) => a + (b - a) * t;

// ── 3B ilkeller ────────────────────────────────────────────────────────────
function sdSphere(x, y, z, r) {
  return Math.hypot(x, y, z) - r;
}
function sdRoundBox(x, y, z, hx, hy, hz, r) {
  const qx = Math.abs(x) - hx + r;
  const qy = Math.abs(y) - hy + r;
  const qz = Math.abs(z) - hz + r;
  return Math.hypot(Math.max(qx, 0), Math.max(qy, 0), Math.max(qz, 0)) +
    Math.min(Math.max(qx, qy, qz), 0) - r;
}
function sdCapsule(x, y, z, ax, ay, az, bx, by, bz, r) {
  const pax = x - ax, pay = y - ay, paz = z - az;
  const bax = bx - ax, bay = by - ay, baz = bz - az;
  const bb = bax * bax + bay * bay + baz * baz;
  let h = bb > 0 ? (pax * bax + pay * bay + paz * baz) / bb : 0;
  h = clamp(h, 0, 1);
  return Math.hypot(pax - bax * h, pay - bay * h, paz - baz * h) - r;
}
function sdCylY(x, y, z, r, h, rr) {
  const dx = Math.hypot(x, z) - r + rr;
  const dy = Math.abs(y) - h + rr;
  return Math.hypot(Math.max(dx, 0), Math.max(dy, 0)) + Math.min(Math.max(dx, dy), 0) - rr;
}
function sdCylZ(x, y, z, r, h, rr) {
  const d = Math.hypot(x, y) - r + rr;
  const dz = Math.abs(z) - h + rr;
  return Math.hypot(Math.max(d, 0), Math.max(dz, 0)) + Math.min(Math.max(d, dz), 0) - rr;
}
function sdTorusZ(x, y, z, R, r) {
  return Math.hypot(Math.hypot(x, y) - R, z) - r;
}
function sdTorusX(x, y, z, R, r) {
  return Math.hypot(Math.hypot(y, z) - R, x) - r;
}
function sdTorusY(x, y, z, R, r) {
  return Math.hypot(Math.hypot(x, z) - R, y) - r;
}
// tabanı y=0, tepesi y=h olan yuvarlatılmış koni.
// Yarım düzlem profili: taban kapak + eğik yan + üst kapak (üç yarım uzay kesişimi).
// Yuvarlatma: profil rr kadar içeri ötelenip sonuçtan rr çıkarılır.
const CONE_CACHE = new Map();
function coneProfile(r1, r2, h, rr) {
  rr = Math.min(rr, Math.max(0.002, Math.min(r1, r2)) * 0.7, (h / 2) * 0.7, h * 0.25);
  const yb = -h / 2 + rr, yt = h / 2 - rr;
  const dr = r2 - r1, dy = h;
  const len = Math.hypot(dr, dy) || 1;
  const nx = dy / len, ny = -dr / len;
  const c = nx * r1 + ny * (-h / 2) - rr;
  return { yb, yt, nx, ny, c, rr };
}
function sdCone(x, y, z, r1, r2, h, rr) {
  const key = `${r1.toFixed(3)}|${r2.toFixed(3)}|${h.toFixed(3)}|${rr.toFixed(3)}`;
  let p = CONE_CACHE.get(key);
  if (!p) { p = coneProfile(r1, r2, h, rr); CONE_CACHE.set(key, p); }
  const q = Math.hypot(x, z) || 1e-6;
  const yy = y - h / 2;
  const dSide = p.nx * q + p.ny * yy - p.c;
  const dBot = p.yb - yy;
  const dTop = yy - p.yt;
  return Math.max(dSide, dBot, dTop) - p.rr;
}
function sdPoly2(px, py, pts) {
  const n = pts.length;
  let d = (px - pts[0][0]) ** 2 + (py - pts[0][1]) ** 2;
  let s = 1;
  for (let i = 0, j = n - 1; i < n; j = i, i++) {
    const ex = pts[j][0] - pts[i][0], ey = pts[j][1] - pts[i][1];
    const wx = px - pts[i][0], wy = py - pts[i][1];
    const t = clamp((wx * ex + wy * ey) / (ex * ex + ey * ey || 1), 0, 1);
    const bx = wx - ex * t, by = wy - ey * t;
    d = Math.min(d, bx * bx + by * by);
    const c1 = py >= pts[i][1], c2 = py < pts[j][1], c3 = ex * wy > ey * wx;
    if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) s = -s;
  }
  return s * Math.sqrt(d);
}
function extrudeZ(px, py, pz, pts, hz, r) {
  const d2 = sdPoly2(px, py, pts) - r;
  const dz = Math.abs(pz) - Math.max(0.02, hz - r);
  return Math.hypot(Math.max(d2, 0), Math.max(dz, 0)) + Math.min(Math.max(d2, dz), 0) - r * 0;
}

// ── dönüşüm (parçayı yerel uzayda konumlandır) ─────────────────────────────
function rotator(rx = 0, ry = 0, rz = 0) {
  const cx = Math.cos(rx), sx = Math.sin(rx);
  const cy = Math.cos(ry), sy = Math.sin(ry);
  const cz = Math.cos(rz), sz = Math.sin(rz);
  // ters dönüş: p_local = Rx(-rx)·Ry(-ry)·Rz(-rz)·(p - c)
  return (x, y, z) => {
    let X = x * cz + y * sz, Y = -x * sz + y * cz, Z = z;
    let t = X * cy - Z * sy; Z = X * sy + Z * cy; X = t;
    t = Y * cx - Z * sx; Z = Y * sx + Z * cx; Y = t;
    return [X, Y, Z];
  };
}

// ── malzemeler ─────────────────────────────────────────────────────────────
// 3dicons yüzeyi: mat ipek / yumuşak silikon. Keskin beyaz parlama yok; geniş,
// düşük alfalı saten parlama + hafif kenar koyulaşması var. Metal ve krom
// parçalar (zil kancası, kadran) nötr krem-gri.
const MAT = {
  clay:    { sat: 1.02, dark: 1.00, amb: 0.96, key: 0.13, edge: 0.15, sheen: 0.10, sheenW: 0.50, spec: 0.04, shin: 16, env: 0.10, metal: 0 },
  gloss:   { sat: 1.04, dark: 1.00, amb: 0.94, key: 0.17, edge: 0.14, sheen: 0.13, sheenW: 0.34, spec: 0.10, shin: 34, env: 0.18, metal: 0.05 },
  gel:     { sat: 1.05, dark: 1.00, amb: 0.95, key: 0.16, edge: 0.13, sheen: 0.16, sheenW: 0.42, spec: 0.05, shin: 22, env: 0.14, metal: 0 },
  rubber:  { sat: 0.84, dark: 0.84, amb: 0.94, key: 0.13, edge: 0.14, sheen: 0.12, sheenW: 0.60, spec: 0.03, shin: 10, env: 0.06, metal: 0 },
  paper:   { sat: 0.90, dark: 0.96, amb: 0.98, key: 0.11, edge: 0.08, sheen: 0.10, sheenW: 0.70, spec: 0.02, shin: 10, env: 0.05, metal: 0 },
  obsidian:{ sat: 1.10, dark: 0.58, amb: 0.92, key: 0.22, edge: 0.20, sheen: 0.22, sheenW: 0.30, spec: 0.14, shin: 46, env: 0.30, metal: 0.2 },
  glass:   { sat: 1.04, dark: 1.00, amb: 0.93, key: 0.18, edge: 0.15, sheen: 0.24, sheenW: 0.28, spec: 0.16, shin: 40, env: 0.34, metal: 0.1 },
  metal:   { sat: 0.20, dark: 1.00, amb: 0.90, key: 0.14, edge: 0.13, sheen: 0.34, sheenW: 0.30, spec: 0.14, shin: 26, env: 0.55, metal: 1 },
  chrome:  { sat: 0.06, dark: 1.00, amb: 0.92, key: 0.12, edge: 0.12, sheen: 0.40, sheenW: 0.25, spec: 0.22, shin: 60, env: 0.75, metal: 1 },
  ink:     { sat: 0.85, dark: 0.30, amb: 0.92, key: 0.20, edge: 0.30, sheen: 0.12, sheenW: 0.24, spec: 0.14, shin: 60, env: 0.30, metal: 0.15 },
  cream:   { sat: 0.10, dark: 1.26, amb: 0.97, key: 0.10, edge: 0.07, sheen: 0.10, sheenW: 0.65, spec: 0.03, shin: 12, env: 0.08, metal: 0 },
};

// renk rampası (3dicons gradient ailesi: şeftali → macenta → çivit)
const RAMP = [
  [0.00, [238, 184, 112]],
  [0.15, [226, 142, 104]],
  [0.32, [212, 96, 140]],
  [0.50, [188, 68, 158]],
  [0.68, [148, 52, 186]],
  [0.85, [106, 40, 200]],
  [1.00, [72, 34, 208]],
];
function ramp(t) {
  t = clamp(t, 0, 1);
  for (let i = 1; i < RAMP.length; i++) {
    if (t <= RAMP[i][0]) {
      const [t0, c0] = RAMP[i - 1];
      const [t1, c1] = RAMP[i];
      const k = (t - t0) / (t1 - t0 || 1);
      return [mix(c0[0], c1[0], k), mix(c0[1], c1[1], k), mix(c0[2], c1[2], k)];
    }
  }
  return RAMP[RAMP.length - 1][1].slice();
}

// stüdyo ortamı — geniş üst ışık + sağ ince şerit + alt dolgu
function envSample(x, y, z) {
  let v = 0.22;
  v += 0.80 * Math.exp(-((x + 0.22) ** 2) / 0.34 - ((y - 0.55) ** 2) / 0.38);
  v += 0.95 * Math.exp(-((x - 0.62) ** 2) / 0.05 - ((y - 0.05) ** 2) / 0.65);
  v += 0.42 * Math.exp(-((y + 0.70) ** 2) / 0.10 - (x * x) / 0.90);
  v += 0.20 * Math.exp(-((x + 0.78) ** 2) / 0.07 - ((y + 0.05) ** 2) / 0.70);
  return v * Math.max(0, 0.32 + 0.68 * z);
}

/**
 * Sahneyi render eder.
 * scene = {
 *   parts: [{ f(x,y,z), mat }],        // ilkeller; mat: MAT anahtarı
 *   bbox: [x0,x1,y0,y1,z0,z1],
 *   tilt, dirDeg, phase, span,         // kamera eğimi ve renk ekseni
 *   light: [x,y,z], exposure
 * }
 * Dönüş: { rgba, size }
 */
function render(scene, out = 288) {
  const parts = scene.parts;
  const n = parts.length;
  const pmat = parts.map((p) => MAT[p.mat || 'gloss'] || MAT.gloss);
  const pcol = parts.map((p) => p.col || null);
  const tilt = scene.tilt ?? 0.30;
  const D = (() => {
    const v = [0, -Math.sin(tilt), -Math.cos(tilt)];
    const l = Math.hypot(v[0], v[1], v[2]);
    return [v[0] / l, v[1] / l, v[2] / l];
  })();
  const Lv = scene.light || [-0.16, 0.42, 0.89];
  const Ll = Math.hypot(Lv[0], Lv[1], Lv[2]);
  const L = [Lv[0] / Ll, Lv[1] / Ll, Lv[2] / Ll];
  const world = scene.world || 24;
  const px = world / out;
  const exposure = scene.exposure ?? 0.88;
  const bb = scene.bbox || [-11, 11, -11, 11, -3, 3];

  const evalScene = (x, y, z) => {
    let d = 1e9;
    for (let i = 0; i < n; i++) {
      if (parts[i].sub) continue;
      const di = parts[i].f(x, y, z);
      const k = parts[i].k;
      if (k) {
        const hh = clamp(0.5 + 0.5 * (d - di) / k, 0, 1);
        d = mix(d, di, hh) - k * hh * (1 - hh);
      } else if (di < d) d = di;
    }
    for (let i = 0; i < n; i++) {
      if (!parts[i].sub) continue;
      d = Math.max(d, -parts[i].f(x, y, z));
    }
    return d;
  };
  const evalId = (x, y, z) => {
    let d = 1e9, id = 0;
    for (let i = 0; i < n; i++) {
      if (parts[i].sub) continue;
      const di = parts[i].f(x, y, z);
      if (di < d) { d = di; id = i; }
    }
    return id;
  };
  const normalAt = (x, y, z, e) => {
    const dx = evalScene(x + e, y, z) - evalScene(x - e, y, z);
    const dy = evalScene(x, y + e, z) - evalScene(x, y - e, z);
    const dz = evalScene(x, y, z + e) - evalScene(x, y, z - e);
    const l = Math.hypot(dx, dy, dz) || 1;
    return [dx / l, dy / l, dz / l];
  };
  const aoAt = (x, y, z, nx, ny, nz) => {
    let occ = 0, sca = 1;
    for (let i = 1; i <= 4; i++) {
      const h = 0.10 + 0.20 * i;
      const d = evalScene(x + nx * h, y + ny * h, z + nz * h);
      occ += (h - d) * sca;
      sca *= 0.68;
    }
    return clamp(1 - 1.9 * occ, 0, 1);
  };
  const softShadow = (x, y, z, nx, ny, nz) => {
    // iq yumuşak gölge: ince adım + geniş penumbra → bantlanma/speckle yok
    let res = 1, t = 0.05;
    for (let i = 0; i < 18; i++) {
      const d = evalScene(x + L[0] * t + nx * 0.02, y + L[1] * t + ny * 0.02, z + L[2] * t + nz * 0.02);
      res = Math.min(res, 14 * d / t);
      t += clamp(d, 0.06, 0.55);
      if (res < 0.02 || t > 16) break;
    }
    return clamp(res, 0, 1);
  };

  // renk ekseni: NESNENİN gerçek izdüşüm aralığı (sahne kutusu değil)
  const ang = (scene.dirDeg ?? 90) * Math.PI / 180;
  const cdx = Math.cos(ang), cdy = Math.sin(ang);
  let cMin = 1e9, cMax = -1e9;
  {
    const step = 0.3;
    for (let x = bb[0]; x <= bb[1]; x += step) {
      for (let y = bb[2]; y <= bb[3]; y += step) {
        let inside = false;
        for (let z = bb[4]; z <= bb[5]; z += 0.6) {
          if (evalScene(x, y, z) < 0.25) { inside = true; break; }
        }
        if (inside) {
          const v = x * cdx + y * cdy;
          if (v < cMin) cMin = v;
          if (v > cMax) cMax = v;
        }
      }
    }
    if (cMin > cMax) { cMin = bb[0] * cdx + bb[2] * cdy; cMax = bb[1] * cdx + bb[3] * cdy; }
  }
  const cRange = cMax - cMin || 1;
  const phase = scene.phase ?? 0.06;
  const span = scene.span ?? 0.92;

  const rgba = Buffer.alloc(out * out * 4);
  const V = [-D[0], -D[1], -D[2]];

  for (let j = 0; j < out; j++) {
    const sy = -((j + 0.5) * px - world / 2);
    for (let i = 0; i < out; i++) {
      const sx = (i + 0.5) * px - world / 2;
      if (sx < bb[0] - 0.6 || sx > bb[1] + 0.6 || sy < bb[2] - 0.6 || sy > bb[3] + 0.6) continue;

      const startY = sy - D[1] * 40;
      const startZ = 40;
      let t = 0, hit = false;
      let hx = 0, hy = 0, hz = 0;
      for (let s = 0; s < 110; s++) {
        const x = sx + D[0] * t;
        const y = startY + D[1] * t;
        const z = startZ + D[2] * t;
        const d = evalScene(x, y, z);
        if (d < 0.004) { hit = true; hx = x; hy = y; hz = z; break; }
        t += d * 0.92;
        if (z < bb[4] - 2) break;
      }
      if (!hit) continue;

      const id = evalId(hx, hy, hz);
      const m = pmat[id];
      const [nx, ny, nz] = normalAt(hx, hy, hz, 0.010);
      const ndv = clamp(nx * V[0] + ny * V[1] + nz * V[2], 0, 1);
      const ndl = nx * L[0] + ny * L[1] + nz * L[2];
      const wrap = clamp(0.5 + 0.5 * ndl, 0, 1);
      const ao = aoAt(hx, hy, hz, nx, ny, nz);
      const sh = softShadow(hx, hy, hz, nx, ny, nz);

      // renk
      const cu = (hx * cdx + hy * cdy - cMin) / cRange;
      const tt = clamp(phase + cu * span, 0, 1);
      const fixed = pcol[id];
      const base = fixed || ramp(tt);
      const grey = (base[0] + base[1] + base[2]) / 3;
      const satK = scene.satK ?? 1.14;
      const boost = (c) => clamp(grey + (c - grey) * satK, 0, 255);
      let cr, cg, cb;
      if (fixed) {
        // sabit renkli parçalar (ekran, alev, krem aksan) rampa/doygunluk işlemi görmez
        cr = base[0] * m.dark; cg = base[1] * m.dark; cb = base[2] * m.dark;
      } else {
        cr = boost(mix(grey, base[0], m.sat)) * m.dark;
        cg = boost(mix(grey, base[1], m.sat)) * m.dark;
        cb = boost(mix(grey, base[2], m.sat)) * m.dark;
      }
      if (m.metal > 0.5) {
        // nötr metal: krem-gri, hafif renkli ortam yansıması
        const tint = ramp(clamp(tt * 0.9 + 0.05, 0, 1));
        cr = mix(206, tint[0], 0.14);
        cg = mix(198, tint[1], 0.14);
        cb = mix(188, tint[2], 0.14);
      }

      // ışık: orijinallerde ölçülen dar aralık (ortalama ≈ 1.0)
      const ambV = 0.90 + 0.06 * clamp(ny, 0, 1);
      const diff = (0.10 + m.key * 0.7) * (0.55 + 0.45 * wrap) * (0.60 + 0.40 * sh);
      const occ = 0.82 + 0.18 * ao;
      let lit = (ambV + diff) * occ;
      lit *= (1 - m.edge * Math.pow(1 - ndv, 2.2)) * (1 + 0.30 * Math.pow(1 - ndv, 2.4));

      // saten parlama (geniş, düşük alfa)
      const hxv = L[0] + V[0], hyv = L[1] + V[1], hzv = L[2] + V[2];
      const hl = Math.hypot(hxv, hyv, hzv);
      const ndh = clamp((nx * hxv + ny * hyv + nz * hzv) / hl, 0, 1);
      const sheen = Math.exp(-((1 - ndh) ** 2) / (m.sheenW * m.sheenW)) * m.sheen * (0.55 + 0.45 * ao);
      const rvx = 2 * ndv * nx - V[0], rvy = 2 * ndv * ny - V[1], rvz = 2 * ndv * nz - V[2];
      const envv = envSample(rvx, rvy, rvz);
      const specTight = Math.pow(ndh, m.shin) * m.spec * (0.4 + 0.6 * envv) * occ;

      let R = cr * lit + 236 * sheen * 0.55 + 244 * specTight + cr * envv * m.env * 0.16;
      let G = cg * lit + 228 * sheen * 0.55 + 238 * specTight + cg * envv * m.env * 0.16;
      let B = cb * lit + 244 * sheen * 0.55 + 248 * specTight + cb * envv * m.env * 0.16;

      const tm = (v) => (v <= 238 ? v : 238 + 17 * (1 - Math.exp(-(v - 238) / 24)));
      const k = (j * out + i) * 4;
      rgba[k] = clamp(tm(Math.max(0, R) * exposure), 0, 255);
      rgba[k + 1] = clamp(tm(Math.max(0, G) * exposure), 0, 255);
      rgba[k + 2] = clamp(tm(Math.max(0, B) * exposure), 0, 255);
      rgba[k + 3] = 255;
    }
  }

  // alfa kenarı yumuşat (3×3)
  const a1 = new Float32Array(out * out);
  for (let i = 0; i < out * out; i++) a1[i] = rgba[i * 4 + 3] / 255;
  for (let j = 0; j < out; j++) {
    for (let i = 0; i < out; i++) {
      let s = 0, c = 0;
      for (let dy = -1; dy <= 1; dy++) {
        const y = j + dy;
        if (y < 0 || y >= out) continue;
        for (let dx = -1; dx <= 1; dx++) {
          const x = i + dx;
          if (x < 0 || x >= out) continue;
          s += a1[y * out + x]; c++;
        }
      }
      rgba[(j * out + i) * 4 + 3] = clamp((s / c) * 255, 0, 255);
    }
  }
  return rgba;
}

module.exports = {
  render, MAT, RAMP, ramp, envSample,
  sdSphere, sdRoundBox, sdCapsule, sdCylY, sdCylZ, sdTorusZ, sdTorusX, sdTorusY,
  sdCone, sdPoly2, extrudeZ, rotator, clamp, mix,
};
