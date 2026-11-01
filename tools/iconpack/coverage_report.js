// Kapsam denetimi: uygulamadaki tüm ikon çağrılarını sayar, 3B'ye bağlananları ve
// bilinçli olarak mono kalan kontrol gliflerini raporlar (docs/ikon-kapsam-denetimi.png).
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');
const sharp = (() => {
  try { return require('sharp'); } catch (e) { /* fallback */ }
  return require(process.env.SHARP_MODULE || path.join(__dirname, 'node_modules', 'sharp'));
})();

const ROOT = path.join(__dirname, '..', '..');
const ASSETS = path.join(ROOT, 'app', 'src', 'main', 'java', 'com', 'kitsugi', 'animelist', 'ui', 'icons', 'KitsugiIconAssets.kt');

const files = execSync('find app/src -name "*.kt"', { cwd: ROOT }).toString().trim().split('\n');
const assetsSrc = fs.readFileSync(ASSETS, 'utf8');
const mapped = new Set([...assetsSrc.matchAll(/"([A-Za-z0-9]+)"/g)].map((m) => m[1]));
const ARTIFACTS = new Set(['Rounded', 'Filled', 'Default', 'Outlined']);

const counts = new Map();
let totalIcons = 0;
let routedFiles = 0;
const unrouted = [];

for (const rel of files) {
  const s = fs.readFileSync(path.join(ROOT, rel), 'utf8');
  const calls = [...s.matchAll(/Icons\.(?:Rounded|Outlined|Filled|Default|AutoMirrored|TwoTone|Sharp)\.([A-Za-z0-9]+)/g)];
  for (const c of calls) {
    const n = c[1];
    if (ARTIFACTS.has(n)) continue;
    counts.set(n, (counts.get(n) || 0) + 1);
    totalIcons += 1;
  }
  if (/(?<![A-Za-z0-9_.])Icon\(/.test(s)) {
    const ok = s.includes('import com.kitsugi.animelist.ui.theme.gradient.Icon') || s.includes('KitsugiIcon as Icon');
    if (ok) routedFiles += 1; else unrouted.push(rel);
  }
}

const art = [];
const mono = [];
for (const [name, n] of counts) (mapped.has(name) ? art : mono).push([name, n]);
art.sort((a, b) => b[1] - a[1]);
mono.sort((a, b) => b[1] - a[1]);
const artUses = art.reduce((s, x) => s + x[1], 0);
const monoUses = mono.reduce((s, x) => s + x[1], 0);

const W = 1400, PAD = 52, FONT = 'DejaVu Sans, sans-serif';
const rowsMono = Math.max(1, Math.ceil(mono.length / 3));
const H = 118 + 210 + 110 + (mono.length ? rowsMono * 26 : 86) + 96;

const barW = W - PAD * 2;
const artPct = (artUses / Math.max(1, artUses + monoUses)) * 100;

const parts = [];
parts.push(`<rect width="${W}" height="${H}" fill="#080A10"/>`);
parts.push(`<text x="${PAD}" y="64" fill="#F4F7FB" font-size="33" font-weight="700" font-family="${FONT}">İkon kapsam denetimi</text>`);
parts.push(`<text x="${PAD}" y="98" fill="#A9B0C2" font-size="16" font-family="${FONT}">Kaynak koddan sayıldı: ${files.length} Kotlin dosyası · ${totalIcons} ikon çağrısı · ${counts.size} farklı ikon adı · hedef: 0 mono</text>`);

let y = 132;
parts.push(`<rect x="${PAD}" y="${y}" width="${barW}" height="34" rx="10" fill="#151A24"/>`);
parts.push(`<rect x="${PAD}" y="${y}" width="${Math.round(barW * artPct / 100)}" height="34" rx="10" fill="url(#artBar)"/>`);
parts.push(`<text x="${PAD + 14}" y="${y + 23}" fill="#0B0E14" font-size="15" font-weight="700" font-family="${FONT}">3B sete bağlı: ${artUses} (%${artPct.toFixed(1)})</text>`);
parts.push(`<text x="${PAD + barW - 14}" y="${y + 23}" fill="#F4F7FB" font-size="15" font-weight="700" text-anchor="end" font-family="${FONT}">mono kontrol glifi: ${monoUses} (%${(100 - artPct).toFixed(1)})</text>`);

y += 74;
const glyphFiles = fs.readdirSync(path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi'))
  .filter((f) => f.startsWith('ic_kitsugi_glyph_'));
const cards = [
  ['3B ikon çağrısı', `${artUses}`, `${artUses === totalIcons ? 'tamamı' : totalIcons + ' çağrıdan'} · ${art.length} ad`],
  ['Tek giriş noktası', `${routedFiles} dosya`, `kaçak: ${unrouted.length}`],
  ['Mono kalan', `${monoUses}`, mono.length === 0 ? 'sıfır — hedef tuttu' : `${mono.length} ad`],
  ['Kontrol glifi', `${glyphFiles.length}`, 'aynı cam/gradyan dili'],
];
const cw = Math.floor((barW - 3 * 18) / 4);
cards.forEach(([t, v, s], i) => {
  const x = PAD + i * (cw + 18);
  parts.push(`<rect x="${x}" y="${y}" width="${cw}" height="104" rx="16" fill="#10131D" stroke="#252B3A"/>`);
  parts.push(`<text x="${x + 20}" y="${y + 34}" fill="#727A8F" font-size="13" font-weight="700" letter-spacing="1" font-family="${FONT}">${t.toUpperCase()}</text>`);
  parts.push(`<text x="${x + 20}" y="${y + 70}" fill="#F4F7FB" font-size="26" font-weight="700" font-family="${FONT}">${v}</text>`);
  parts.push(`<text x="${x + 20}" y="${y + 92}" fill="#727A8F" font-size="12" font-family="${FONT}">${s}</text>`);
});

y += 152;
if (mono.length === 0) {
  parts.push(`<text x="${PAD}" y="${y}" fill="#7BE3A6" font-size="19" font-weight="700" font-family="${FONT}">Tek renkli (mono) ikon kalmadı — hedef: 0</text>`);
  parts.push(`<text x="${PAD}" y="${y + 24}" fill="#727A8F" font-size="13" font-family="${FONT}">Kapat / geri / onay / chevron / filtre / sırala / görünüm modu / tam ekran / kırpma / metin biçimi dahil her ad 3B sete bağlı.</text>`);
  parts.push(`<text x="${PAD}" y="${y + 46}" fill="#727A8F" font-size="13" font-family="${FONT}">3dicons paketinde bu işaretler yoktu; setin paleti ve ışık kurallarıyla tools/iconpack/control_glyphs.js üretti.</text>`);
} else {
  parts.push(`<text x="${PAD}" y="${y}" fill="#F4F7FB" font-size="19" font-weight="700" font-family="${FONT}">Hâlâ mono kalan ikon adları</text>`);
  parts.push(`<text x="${PAD}" y="${y + 24}" fill="#727A8F" font-size="13" font-family="${FONT}">Bunlar 3B sete bağlanmalı — kapsam hedefi 0.</text>`);
}
y += 46;
y += 46;
mono.forEach(([name, n], i) => {
  const col = i % 3;
  const row = Math.floor(i / 3);
  const x = PAD + col * Math.floor(barW / 3);
  const yy = y + row * 26;
  parts.push(`<text x="${x}" y="${yy}" fill="#A9B0C2" font-size="13" font-family="${FONT}">• ${name}</text>`);
  parts.push(`<text x="${x + Math.floor(barW / 3) - 26}" y="${yy}" fill="#5C6478" font-size="12" text-anchor="end" font-family="${FONT}">${n}</text>`);
});
if (mono.length > 0) y += rowsMono * 26 + 26;
parts.push(`<text x="${PAD}" y="${y}" fill="#727A8F" font-size="13" font-family="${FONT}">3B yolundan geçmeyen dosya: ${unrouted.length === 0 ? '0 — tüm Icon(...) çağrıları tek giriş noktasından geçiyor' : unrouted.join(', ')}</text>`);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
<defs><linearGradient id="artBar" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#A78BFA"/><stop offset="0.55" stop-color="#F472B6"/><stop offset="1" stop-color="#FB923C"/></linearGradient></defs>
${parts.join('\n')}</svg>`;

fs.mkdirSync(path.join(ROOT, 'docs'), { recursive: true });
sharp(Buffer.from(svg)).png().toFile(path.join(ROOT, 'docs', 'ikon-kapsam-denetimi.png'))
  .then(() => console.log(`yazıldı docs/ikon-kapsam-denetimi.png | 3B ${artUses} / mono ${monoUses} / kaçak dosya ${unrouted.length}`));
