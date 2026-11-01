// Her glifin 3B stil kimliğini belgeler: malzeme + gerekçe + görsel harita.
// Çıktı:
//   docs/ikon-malzeme-haritasi.md   (tablo: glif → işlev → malzeme → neden)
//   docs/ikon-stil-haritasi.png     (malzemeye göre gruplanmış görsel sayfa)
const fs = require('fs');
const path = require('path');
const sharp = require(path.join(__dirname, 'node_modules', 'sharp'));
const I3 = require(path.join(__dirname, 'icons3d.js'));

// ikonun baskın malzemesi: aksan (krem/ekran/krom) dışındaki en çok kullanılan
function dominantMat(scene) {
  const skip = new Set(['cream', 'ink', 'chrome']);
  const count = {};
  for (const p of scene.parts || []) {
    if (p.sub) continue;
    const m = p.mat || 'gloss';
    if (skip.has(m)) continue;
    count[m] = (count[m] || 0) + 1;
  }
  let best = 'gloss', n = -1;
  for (const [m, c] of Object.entries(count)) if (c > n) { best = m; n = c; }
  return best;
}
const LETTER_MAT = { textBold: 'clay', textItalic: 'gloss', textTitle: 'clay', textStrike: 'gel' };

const ROOT = path.join(__dirname, '..', '..');
const RES = path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi');
const DOCS = path.join(ROOT, 'docs');

// malzeme adı → Türkçe okunuş
const MAT_TR = {
  clay: 'mat seramik (kil)',
  gloss: 'parlak emaye',
  gel: 'jöle / yarı saydam',
  glass: 'cam',
  obsidian: 'obsidyen (koyu cam)',
  rubber: 'mat kauçuk',
  paper: 'ince kâğıt',
  chrome: 'krom (gümüş aksan)',
};

// glif → işlev + stil gerekçesi
const WHY = {
  close: ['Kapat', 'parlak emaye çarpı — dokunmatik kapatma düğmesi gibi sert ve net'],
  minus: ['Simge durumuna küçült', 'yumuşak kil hap — pasif, geri çekilebilir eylem'],
  chevron_up: ['Yukarı / aç', 'jöle ok — parmağın altında esneyen yön göstergesi'],
  chevron_down: ['Aşağı / kapa', 'jöle ok'],
  chevron_left: ['Geri', 'jöle ok'],
  chevron_right: ['İleri', 'jöle ok'],
  arrow_up: ['Yukarı taşı / çık', 'ince kâğıt ok — sayfa içinde sürüklenen katman'],
  arrow_down: ['Aşağı taşı / in', 'ince kâğıt ok'],
  arrow_right: ['Sağa taşı', 'ince kâğıt ok'],
  arrow_left: ['Sola taşı', 'ince kâğıt ok'],
  eye: ['Göster / önizle', 'cam gövde + obsidyen bebek — gerçek göz gibi saydam'],
  eyeOff: ['Gizle', 'cam gövde + üstü çizili'],
  funnel: ['Filtrele', 'cam huni + ızgara çizgileri — süzme hissi'],
  sort: ['Sırala', 'parlak emaye çubuklar + ok — aktif eylem'],
  grid: ['Izgara görünüm', 'dört kalın kil karo — fiziksel döşeme'],
  list: ['Liste görünüm', 'kâğıt satırlar — ince, düz bilgi şeritleri'],
  stream: ['Akış görünüm', 'kalın kauçuk çubuklar — ağır, sabit bloklar'],
  carousel: ['Vitrin / karusel', 'emaye orta kart + yan kartlar — üç ayrı panel'],
  more: ['Daha fazla', 'jöle üç nokta — yumuşak, davetkâr'],
  external: ['Dışa aç', 'obsidyen çerçeve + ok — koyu teknik çerçeve'],
  fullscreen: ['Tam ekran', 'obsidyen köşe parantezleri'],
  crop: ['Kırp', 'parlak emaye bıçaklar — kesme aleti gibi'],
  rotate: ['Döndür', 'parlak emaye yay + ok'],
  aspect: ['En-boy oranı', 'kil çerçeve + iç kılavuz çizgileri'],
  blur: ['Bulanıklaştır', 'cam damla — saydamlık ve kırılma'],
  alignLeft: ['Sola hizala', 'kâğıt satırlar'],
  alignCenter: ['Ortala', 'kil satırlar — daha tok'],
  alignJustify: ['İki yana yasla', 'kauçuk satırlar — en ağır blok'],
  border: ['Kenarlık', 'obsidyen çift çerçeve'],
  selectAll: ['Tümünü seç', 'kauçuk kesikli kare — seçim bandı'],
  drag: ['Sürükle', 'kauçuk tutamak çubukları'],
  density: ['Yoğunluk', 'ince kâğıt çizgiler'],
  keyboard: ['Klavye kısayolları', 'kauçuk gövde + emaye tuşlar (gerçek tuş dizilimi)'],
  input: ['Girdi alanı', 'emaye yuva + iç oluk'],
  doubleArrow: ['Yatay boyutlandır', 'jöle çift ok'],
  home: ['Ana sayfa', 'yumuşak kil ev — sıcak, oturmuş hacim'],
  leaf: ['İçerik / doğa', 'jöle yaprak — yarı saydam'],
  snow: ['Kış teması', 'cam kar tanesi'],
  cake: ['Doğum günü', 'kil sünger + emaye mumlar (gerçek mum dizilimi)'],
  camera: ['Kamera / fotoğraf', 'emaye gövde + obsidyen mercek + oyuk halka'],
  textQuote: ['Alıntı', 'kil tırnak işaretleri'],
  textHash: ['Etiket / hashtag', 'obsidyen metal çubuklar'],
  textBold: ['Kalın', 'obsidyen B — ağır harf'],
  textItalic: ['İtalik', 'parlak emaye I'],
  textTitle: ['Başlık', 'kil A'],
  textStrike: ['Üstü çizili', 'jöle S'],
  obj_sync: ['Eşitle / yenile', 'parlak emaye dairesel ok'],
  obj_sliders: ['Ayar kaydırıcıları', 'emaye ray + krom düğmeler (gerçek slider)'],
  obj_download: ['İndir', 'jöle ok + tepsi'],
  obj_upload: ['Yükle', 'ince kâğıt ok + tepsi'],
  obj_timer: ['Zamanlayıcı', 'kil gövde + emaye tepe düğmesi (kronometre)'],
  obj_code: ['Kod', 'obsidyen parantezler'],
  obj_tv: ['TV / yayın', 'kauçuk gövde + obsidyen ekran + krom ayak'],
  obj_tag: ['Etiket', 'ince kâğıt etiket + delik'],
  obj_backup: ['Yedekle', 'jöle bulut + ok'],
};

function rows() {
  const out = [];
  for (const name of Object.keys(I3.ICONS)) {
    const sc = I3.ICONS[name]();
    const isObj = I3.OBJ_NAMES && I3.OBJ_NAMES.has(name);
    out.push({
      file: `ic_kitsugi_${isObj ? 'obj' : 'glyph'}_${name}`,
      key: isObj ? `obj_${name}` : name,
      mat: dominantMat(sc), parts: sc.parts,
    });
  }
  for (const name of Object.keys(I3.LETTERS)) {
    out.push({ file: `ic_kitsugi_glyph_${name}`, key: name, mat: LETTER_MAT[name] || 'clay' });
  }
  return out;
}

async function main() {
  const list = rows();
  // ── markdown tablo
  const md = [];
  md.push('# Kitsugi 3B ikon seti — glif başına stil kimliği\n');
  md.push('Her glif kendi işlevine göre ayrı bir 3B malzeme ve gövde kurgusuyla üretilir.');
  md.push('Işık yönü, renk rampası ve temas gölgesi ortak olduğu için set tek aile gibi durur;');
  md.push('malzeme, kalınlık, kenar yuvarlaklığı ve parça kurgusu glif başına değişir.\n');
  md.push('| # | Glif | İşlev | Malzeme | Parça | Neden bu stil |');
  md.push('|---|------|-------|---------|-------|----------------|');
  list.forEach((r, i) => {
    const w = WHY[r.key] || ['', ''];
    md.push(`| ${i + 1} | \`${r.file}.png\` | ${w[0]} | ${MAT_TR[r.mat] || r.mat} | ${r.parts ? r.parts.length + ' parça' : '—'} | ${w[1] || ''} |`);
  });
  const counts = {};
  for (const r of list) counts[r.mat] = (counts[r.mat] || 0) + 1;
  md.push('\n## Malzeme dağılımı\n');
  md.push('| Malzeme | Glif sayısı |');
  md.push('|---------|-------------|');
  for (const [m, c] of Object.entries(counts).sort((a, b) => b[1] - a[1])) md.push(`| ${MAT_TR[m] || m} | ${c} |`);
  md.push(`| **Toplam** | **${list.length}** |\n`);
  md.push('## Malzeme tanımları\n');
  md.push('- **Mat seramik (kil)** — geniş yumuşak kenar, düşük spekülar, yüksek ortam ışığı; kalp/ev gibi sıcak formlar.');
  md.push('- **Parlak emaye** — sıkı ve parlak spekülar, canlı doygunluk; düğme ve aletler.');
  md.push('- **Jöle** — kalın, yuvarlak, iç ışıma; esneyen/yönlendiren işaretler.');
  md.push('- **Cam** — ince parlak kenar, kalınlaşan koyu duvar; saydamlık anlatan işlevler.');
  md.push('- **Obsidyen** — koyu gövde, çok keskin spekülar, parlak kenar; teknik çerçeve ve harfler.');
  md.push('- **Mat kauçuk** — en koyu ve en mat; ağır bloklar, tutamaklar.');
  md.push('- **İnce kâğıt** — çok ince levha (kalınlık 0.54), geniş temas gölgesi; düz bilgi şeritleri.\n');
  fs.writeFileSync(path.join(DOCS, 'ikon-malzeme-haritasi.md'), md.join('\n'), 'utf8');

  // ── görsel harita (malzemeye göre gruplu)
  const C = 150, LAB = 34, COLS = 7;
  const order = ['clay', 'gloss', 'gel', 'glass', 'obsidian', 'rubber', 'paper'];
  const sorted = order.flatMap((m) => list.filter((r) => r.mat === m));
  const rowsN = Math.ceil(sorted.length / COLS);
  const W = COLS * C + 40, H = rowsN * (C + LAB) + 90;
  const comps = [];
  for (let i = 0; i < sorted.length; i++) {
    const r = sorted[i];
    const col = i % COLS, row = Math.floor(i / COLS);
    const x = 20 + col * C, y = 80 + row * (C + LAB);
    const buf = await sharp(path.join(RES, `${r.file}.png`))
      .resize(C - 22, C - 22, { fit: 'contain', kernel: 'lanczos3' }).toBuffer();
    comps.push({ input: buf, left: x + 11, top: y + 6 });
    const label = `<svg xmlns="http://www.w3.org/2000/svg" width="${C}" height="${LAB}">
      <text x="${C / 2}" y="14" font-family="Arial" font-size="13" fill="#F2E9FA" text-anchor="middle">${r.key}</text>
      <text x="${C / 2}" y="29" font-family="Arial" font-size="11" fill="#9A8FB0" text-anchor="middle">${MAT_TR[r.mat]}</text></svg>`;
    comps.push({ input: Buffer.from(label), left: x, top: y + C - 12 });
  }
  const head = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="80">
    <text x="20" y="36" font-family="Arial" font-size="26" font-weight="700" fill="#FFFFFF">Glif başına 3B stil kimliği — 55 glif, 7 malzeme</text>
    <text x="20" y="62" font-family="Arial" font-size="15" fill="#9A8FB0">Mat seramik · parlak emaye · jöle · cam · obsidyen · mat kauçuk · ince kâğıt — her glif kendi işlevine göre seçildi</text></svg>`;
  comps.push({ input: Buffer.from(head), left: 0, top: 0 });
  await sharp({ create: { width: W, height: H, channels: 4, background: '#0C0A12' } })
    .composite(comps).png().toFile(path.join(DOCS, 'ikon-stil-haritasi.png'));

  console.log(`yazıldı docs/ikon-malzeme-haritasi.md + docs/ikon-stil-haritasi.png | ${list.length} glif | ${JSON.stringify(counts)}`);
}

main();
