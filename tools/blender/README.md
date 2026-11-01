# Kitsugi ikon hattı — Blender/Cycles (3dicons ile aynı hat)

Orijinal 3dicons seti Blender/Cycles ile üretilmiş render'lar: kalın hacimli
şekiller, geniş yuvarlatmalar, nesne uzayında diagonal renk rampası, yumuşak
stüdyo ışığı ve şeffaf zemin. Bu klasördeki betikler **aynı hattı** birebir
kuruyor; SDF/ray-march denemeleri (tools/iconpack/scene3d.js) bu yüzden bırakıldı.

## Kurulum (bu sandbox'ta çalıştı)

```bash
python3 -m venv /tmp/blenv
/tmp/blenv/bin/pip install "bpy==4.2.23"        # Blender 4.2, Python modülü olarak
```

`bpy` çalışırken birkaç X11 kütüphanesi istiyor (libXrender, libXi, libGL …).
Headless render için bunlar hiç çağrılmıyor; bu yüzden boş "stub" kütüphaneler
üretip `LD_LIBRARY_PATH` ile veriyoruz:

```bash
python3 tools/blender/x11_stubgen.py            # /tmp/fakelibs/*.so üretir
LD_LIBRARY_PATH=/tmp/fakelibs /tmp/blenv/bin/python tools/blender/icons_all.py
```

## Hat

| Aşama | Ayar |
|---|---|
| Geometri | `kit.py`: curve+kapsül çubuklar, solidify+bevel plakalar, silindir/küre/torus; tüm parçalar **boolean UNION** ile tek gövdeye indirilir (z-fighting yok) |
| Renk | Nesne uzayı koordinatı → eksen vektörüyle dot → Map Range → ColorRamp. Duraklar orijinal render'lardan **pikselden ölçüldü**: `(66,24,198) → (120,40,196) → (172,54,176) → (206,78,146) → (228,138,106) → (246,182,120)` |
| Malzeme | Principled BSDF, roughness 0.38, coat 0.05; krem/mürekkep/krom parçalar ayrı malzeme (union malzeme yuvalarını korur) |
| Işık | 3 alan ışığı (key/fill/rim, 0.12 güç) + parlak gri dünya (1.5) — orijinallerdeki "her yeri eşit aydınlık" görünümü |
| Kamera | 135 mm, ~6° üstten, ortografik etkiye yakın |
| Render | Cycles CPU, 72 örnek, OIDN denoise, film transparent, 384 px → kırp → 128/192 px |
