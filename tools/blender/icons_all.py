"""55 Kitsugi ikonu — Blender/Cycles, 3dicons hattinin birebir karsiligi."""
import sys, os, math
sys.path.insert(0, '/tmp/bl')
import kit
from kit import *

D = math.pi / 180


def srgb(c):
    c = c / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


CANON = [(0.00, (66, 24, 198)), (0.20, (120, 40, 196)), (0.42, (172, 54, 176)),
         (0.62, (206, 78, 146)), (0.82, (228, 138, 106)), (1.00, (246, 182, 120))]
LIN = [(p, tuple(srgb(v) for v in c)) for p, c in CANON]
CREAM = (0.96, 0.92, 0.86)
INK = (0.09, 0.02, 0.15)
DARK = (0.13, 0.05, 0.22)
CHROME = (0.82, 0.80, 0.78)
WARM = (0.99, 0.90, 0.78)


def build(name, parts, ang=38, span=23.0, tilt=6.0, res=384, sat=1.10, rough=0.38):
    sc = reset()
    sc.cycles.samples = 72
    parts()
    gm = gradient_material(None, ang=ang, stops=LIN, rough=rough, coat=0.05, sat=sat, soft=9.5)
    objs = [o for o in PARTS if o.name in bpy.data.objects]
    for o in objs:
        if len(o.data.materials) == 0:
            o.data.materials.append(gm)
    ob = join(name)
    studio(sc, scale=1.0, power=0.12)
    sc.world.node_tree.nodes["Background"].inputs[1].default_value = 1.5
    camera(sc, span=span, tilt=tilt)
    shoot(sc, '/tmp/bl/png/%s.png' % name, res=res)
    return ob


def dir_pts(deg, pts):
    c, s = math.cos(deg * D), math.sin(deg * D)
    return [(x * c - y * s, x * s + y * c) for (x, y) in pts]


def arrow_parts(deg, body_r=1.55, head_r=3.05, head_h=4.5, tail=-7.6, neck=0.6):
    """0=sag, 90=yukari, 180=sol, -90=asagi. Govde +x yonunde kurulur."""
    stroke(dir_pts(deg, [(tail, 0), (neck + 0.4, 0)]), body_r)
    tri = [(neck, -head_r), (neck, head_r), (neck + head_h, 0)]
    plate(dir_pts(deg, tri), 3.3, 1.0)

def chevron_parts(deg, r=1.8, w=6.3, h=2.9):
    stroke(dir_pts(deg, [(-w, -h), (0, h), (w, -h)]), r)


# ─── kontrol glifleri ──────────────────────────────────────────────────────
def I_close():
    stroke([(-6.4, -6.4), (6.4, 6.4)], 1.95)
    stroke([(-6.4, 6.4), (6.4, -6.4)], 1.95)


def I_minus():
    boxp(6.0, 1.85, 1.55, bevel=1.5)


def I_chevron_up():
    chevron_parts(0)


def I_chevron_down():
    chevron_parts(180)


def I_chevron_left():
    chevron_parts(90)


def I_chevron_right():
    chevron_parts(-90)


def I_arrow_up():
    arrow_parts(90)


def I_arrow_down():
    arrow_parts(-90)


def I_arrow_left():
    arrow_parts(180)


def I_arrow_right():
    arrow_parts(0)


def I_doubleArrow():
    stroke([(-2.4, 4.4), (-7.4, 0), (-2.4, -4.4)], 1.65)
    stroke([(2.4, 4.4), (7.4, 0), (2.4, -4.4)], 1.65)


def I_eye():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1.0)
    ob = bpy.context.object
    ob.scale = (8.4, 5.2, 2.0)
    bpy.ops.object.transform_apply(scale=True)
    bpy.ops.object.shade_smooth()
    kit._obj(ob)
    o = discp(2.8, 0.6, loc=(0, 0, 2.15), bevel=0.3)
    paint(o, DARK, rough=0.22)
    o = ballp(1.55, (0, 0, 2.5))
    paint(o, (0.04, 0.01, 0.07), rough=0.18)
    o = ballp(0.5, (0.62, 0.72, 3.2))
    paint(o, (1, 1, 1), rough=0.12)

def I_eyeOff():
    I_eye()
    stroke([(-8.2, -6.0), (8.2, 6.0)], 1.15, z=2.6)

def I_plus():
    boxp(1.85, 6.0, 1.55, bevel=1.5)
    boxp(6.0, 1.85, 1.55, bevel=1.5)


# ─── sayfa / panel glifleri ────────────────────────────────────────────────
def page(rows, lens, hw=5.6, hh=6.4):
    boxp(hw, hh, 0.55, bevel=0.65)
    for i, y in enumerate(rows):
        stroke([(-hw + 1.3, y), (lens[i], y)], 0.62, z=0.78)

def I_alignLeft():
    page([4.4, 1.3, -1.8, -4.9], [3.4, 0.6, 3.0, -0.6])

def I_alignCenter():
    page([4.4, 1.3, -1.8, -4.9], [2.2, 3.2, 0.9, 2.4])

def I_alignJustify():
    page([4.4, 1.3, -1.8, -4.9], [4.3, 4.3, 3.2, 4.3])

def I_grid():
    boxp(5.6, 5.6, 0.6, bevel=0.6)
    for sx in (-2.75, 2.75):
        for sy in (-2.75, 2.75):
            boxp(2.15, 2.15, 0.55, bevel=0.42, loc=(sx, sy, 0.8))

def I_list():
    boxp(5.6, 6.4, 0.55, bevel=0.65)
    for y in (3.6, 1.2, -1.2, -3.6):
        ballp(0.42, (-3.9, y, 0.72))
        stroke([(-2.6, y), (3.9, y)], 0.5, z=0.72)

def I_stream():
    for y in (3.8, 0.0, -3.8):
        stroke([(-7.6, y), (7.6, y)], 1.55)


def I_sort():
    for i, y in enumerate((5.2, 1.2, -2.8)):
        plate([(-7.6, y - 0.85), (-7.6, y + 0.85), (2.4 - i * 1.7, y + 0.85),
               (2.4 - i * 1.7, y - 0.85)], 2.0, 0.55)
    stroke([(6.2, 6.6), (6.2, -1.0)], 1.3)
    plate([(-2.4, -1.2), (2.4, -1.2), (0, -5.2)], 2.9, 0.85)

def I_funnel():
    # geniş ağız (üstte) -> dar boyun -> kısa emzik
    plate([(-6.8, 4.6), (6.8, 4.6), (1.5, -1.0), (-1.5, -1.0)], 4.6, 0.9)
    stroke([(0, -0.4), (0, -6.4)], 1.35)

def I_more():
    for y in (5.6, 0.0, -5.6):
        ballp(1.6, (0, y, 0))


def I_external():
    stroke([(-8.4, 8.4), (-8.4, 2.0)], 1.45)
    stroke([(-8.4, 8.4), (-2.0, 8.4)], 1.45)
    stroke([(-6.6, -6.6), (3.0, 3.0)], 1.6)
    plate(dir_pts(45, [(0.0, -2.9), (0.0, 2.9), (6.0, 0)]), 3.2, 1.0)

def I_fullscreen():
    for sx in (-1, 1):
        for sy in (-1, 1):
            stroke([(sx * 7.9, sy * 3.2), (sx * 7.9, sy * 7.9), (sx * 3.2, sy * 7.9)], 1.55)


def I_crop():
    stroke([(-4.6, 8.2), (-4.6, -4.6), (8.2, -4.6)], 1.55)
    stroke([(4.6, -8.2), (4.6, 4.6), (-8.2, 4.6)], 1.55)


def I_selectAll():
    for s in (-1, 1):
        for a in (-6.2, 2.6):
            stroke([(a, s * 7.6), (a + 3.6, s * 7.6)], 1.25)
            stroke([(s * 7.6, a), (s * 7.6, a + 3.6)], 1.25)

def I_drag():
    for y in (2.9, -2.9):
        stroke([(-4.6, y), (4.6, y)], 1.55)
        for x in (-6.2, 6.2):
            ballp(1.05, (x, y, 0))

def I_density():
    for y in (5.4, 2.7, 0.0, -2.7, -5.4):
        stroke([(-7.8, y), (5.4 if y < -4 else 7.8, y)], 0.95)


def I_aspect():
    boxp(5.6, 3.8, 0.6, bevel=0.65)
    for x in (-1.9, 1.9):
        boxp(0.42, 2.7, 0.5, bevel=0.2, loc=(x, 0, 0.8))

def I_blur():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=4.7,
                                         location=(0, -1.8, 0))
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    kit._obj(ob)
    bpy.ops.mesh.primitive_cone_add(vertices=64, radius1=3.6, radius2=0.25, depth=9.4,
                                    location=(0, 3.2, 0), rotation=(-90 * D, 0, 0))
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    kit._obj(ob)

def I_rotate():
    boxp(5.2, 5.6, 0.7, bevel=0.7)
    ringp(3.3, 0.62, loc=(0, -0.5, 0.9))
    plate(dir_pts(115, [(-1.7, 3.6), (1.7, 3.6), (0, 6.8)]), 2.2, 0.6)

def I_sync():
    for a0, a1 in ((28, 152), (208, 332)):
        pts = [(math.cos((a0 + (a1 - a0) * t / 24) * D) * 6.3,
                math.sin((a0 + (a1 - a0) * t / 24) * D) * 6.3) for t in range(25)]
        stroke(pts, 1.0)
        ca, sa = math.cos(a1 * D), math.sin(a1 * D)
        ta, tb = -sa, ca          # teget (saat yonu)
        tipx, tipy = ca * 6.3 + ta * 3.4, sa * 6.3 + tb * 3.4
        nx, ny = ca * 2.5, sa * 2.5
        plate([(ca * 6.3 + nx, sa * 6.3 + ny), (ca * 6.3 - nx, sa * 6.3 - ny),
               (tipx, tipy)], 2.3, 0.7)

def I_carousel():
    boxp(1.5, 3.2, 0.5, bevel=0.35, loc=(-4.6, 0, 0))
    boxp(1.5, 3.2, 0.5, bevel=0.35, loc=(4.6, 0, 0))
    boxp(2.3, 4.0, 0.7, bevel=0.45, loc=(0, 0, 0.5))

def I_home():
    boxp(4.2, 3.4, 2.0, bevel=0.55, loc=(0, -2.4, 0))
    plate([(-5.4, 0.6), (5.4, 0.6), (0, 5.2)], 3.2, 0.85)
    boxp(1.2, 1.6, 0.4, bevel=0.22, loc=(0, -4.4, 2.1))
    boxp(1.2, 0.9, 0.35, bevel=0.2, loc=(-2.4, -0.8, 2.0))

def I_leaf():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1.0)
    ob = bpy.context.object
    ob.scale = (6.6, 3.9, 1.4)
    ob.rotation_euler = (0, 0, 42 * D)
    bpy.ops.object.transform_apply(scale=True, rotation=True)
    bpy.ops.object.shade_smooth()
    kit._obj(ob)
    stroke([(-7.4, -8.0), (-3.6, -4.4)], 0.85)

def I_snow():
    for i in range(6):
        a = i * 60 * D
        stroke([(0, 0), (math.cos(a) * 7.6, math.sin(a) * 7.6)], 0.62)
        for sgn in (-1, 1):
            b = a + sgn * 48 * D
            x0, y0 = math.cos(a) * 4.6, math.sin(a) * 4.6
            stroke([(x0, y0), (x0 + math.cos(b) * 2.4, y0 + math.sin(b) * 2.4)], 0.42)
    ballp(1.15)


def I_cake():
    discp(5.9, 0.6, loc=(0, -3.0, 0), bevel=0.3)
    discp(4.6, 1.9, loc=(0, -0.9, 0), bevel=0.45)
    ringp(4.2, 0.6, loc=(0, 1.0, 0))
    for x in (-2.5, 0, 2.5):
        discp(0.5, 1.8, loc=(x, 2.3, 0), bevel=0.15)
        o = ballp(0.62, (x, 4.6, 0))
        paint(o, (1.0, 0.88, 0.6), rough=0.3)

def I_camera():
    boxp(5.2, 3.4, 1.4, bevel=0.6)
    o = discp(2.6, 1.0, loc=(0, -0.2, 2.0), bevel=0.3)
    paint(o, DARK, rough=0.15)
    o = discp(1.7, 0.55, loc=(0, -0.2, 2.7), bevel=0.25)
    paint(o, (0.06, 0.02, 0.10), rough=0.1)
    boxp(1.1, 0.45, 0.35, bevel=0.15, loc=(-3.2, 2.4, 1.6))
    boxp(1.3, 0.5, 0.3, bevel=0.15, loc=(3.4, 2.6, 1.55))

def I_textQuote():
    for cx in (-2.9, 2.9):
        ballp(1.7, (cx, 2.0, 0))
        stroke([(cx + 0.3, 1.5), (cx - 1.6, -3.2)], 0.95)

def I_textHash():
    for x in (-2.5, 2.5):
        boxp(0.75, 6.6, 0.75, bevel=0.35, loc=(x, 0, 0), rot=-7 * D)
    for y in (-2.5, 2.5):
        boxp(8.2, 0.75, 0.75, bevel=0.35, loc=(0, y, 0), rot=-7 * D)

def I_sliders():
    for y, kx in ((4.6, -2.4), (0.0, 2.0), (-4.6, -0.7)):
        boxp(4.3, 0.5, 0.55, bevel=0.25, loc=(0, y, 0))
        o = discp(1.3, 0.75, loc=(kx, y, 0.7), bevel=0.3)
        o.rotation_euler = (90 * D, 0, 0)
        bpy.context.view_layer.objects.active = o
        bpy.ops.object.transform_apply(rotation=True)

def I_download():
    stroke([(0, 7.0), (0, 0.8)], 1.45)
    plate([(-2.9, 0.9), (2.9, 0.9), (0, -3.4)], 3.1, 0.9)
    stroke([(-5.2, -5.6), (-5.2, -7.8), (5.2, -7.8), (5.2, -5.6)], 0.95)

def I_upload():
    stroke([(0, -5.6), (0, -0.2)], 1.45)
    plate([(-2.9, -0.4), (2.9, -0.4), (0, 3.8)], 3.1, 0.9)
    stroke([(-5.2, -6.2), (-5.2, -8.4), (5.2, -8.4), (5.2, -6.2)], 0.95)

def I_timer():
    discp(6.0, 0.7, loc=(0, -0.8, 0), bevel=0.3)
    discp(4.9, 0.4, loc=(0, -0.8, 0.75), bevel=0.25)
    discp(0.85, 0.9, loc=(0, 6.0, 0), bevel=0.2)
    boxp(1.6, 0.4, 0.7, bevel=0.15, loc=(0, 7.1, 0))
    stroke([(0, -0.8), (2.2, 1.9)], 0.5, z=1.1)
    stroke([(0, -0.8), (-1.4, -2.9)], 0.42, z=1.1)

def I_code():
    for s in (-1, 1):
        stroke([(s * 4.2, 5.4), (s * 9.2, 0), (s * 4.2, -5.4)], 1.5)


def I_tv():
    boxp(5.0, 3.5, 1.2, bevel=0.7)
    o = boxp(4.2, 2.7, 0.35, bevel=0.3, loc=(0, 0.2, 1.3))
    paint(o, DARK, rough=0.12)
    boxp(2.0, 0.45, 0.5, bevel=0.2, loc=(0, -4.6, 0))

def I_tag():
    p = plate([(-4.6, 0.8), (-4.6, 4.6), (-0.8, 4.6), (4.6, -0.8), (0.8, -4.6), (-0.8, -4.6)],
              1.6, 0.55, z=0)
    p.scale = (1.55, 1.55, 1.0)
    bpy.context.view_layer.objects.active = p
    bpy.ops.object.transform_apply(scale=True)
    p.rotation_euler = (0, 0, -18 * D)
    bpy.ops.object.transform_apply(rotation=True)
    bpy.ops.mesh.primitive_cylinder_add(vertices=48, radius=1.3, depth=6, location=(-5.8, 4.6, 0))
    cut(p, bpy.context.object)

def I_backup():
    ballp(2.8, (-3.6, 0.0, 0))
    ballp(2.5, (2.0, -0.4, 0))
    ballp(2.7, (-0.6, 1.9, 0))
    boxp(2.9, 1.1, 1.5, bevel=1.3, loc=(0, -2.2, 0))
    stroke([(0, 3.4), (0, 5.6)], 0.62)
    plate([(-1.9, 5.4), (1.9, 5.4), (0, 8.6)], 1.6, 0.5)

def I_keyboard():
    boxp(5.8, 3.6, 0.7, bevel=0.5)
    for y in (1.9, 0.0, -1.9):
        for x in (-4.4, -2.2, 0.0, 2.2, 4.4):
            boxp(0.9, 0.75, 0.35, bevel=0.15, loc=(x, y, 0.9))

def I_input():
    boxp(5.6, 2.3, 0.8, bevel=0.6)
    o = boxp(4.9, 1.7, 0.3, bevel=0.25, loc=(0, 0, 0.9))
    paint(o, CREAM, rough=0.4)
    o = boxp(1.9, 0.3, 0.2, bevel=0.1, loc=(-2.2, 0.45, 1.2))
    paint(o, DARK, rough=0.4)
    o = boxp(1.4, 0.3, 0.2, bevel=0.1, loc=(-2.4, -0.45, 1.2))
    paint(o, DARK, rough=0.4)
    o = boxp(0.3, 1.3, 0.3, bevel=0.1, loc=(1.5, 0, 1.2))
    paint(o, DARK, rough=0.4)

def I_letters(ch, weight=800, size=17.2, strike=False, skew=0.0):
    cu = bpy.data.curves.new('t', 'FONT')
    cu.body = ch
    cu.size = size
    cu.extrude = 1.2
    cu.bevel_depth = 0.28
    cu.bevel_resolution = 4
    cu.align_x = 'CENTER'
    cu.align_y = 'CENTER'
    ob = bpy.data.objects.new('t', cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target='MESH')
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    kit._obj(ob)
    if strike:
        stroke([(-7.4, -4.4), (7.4, 4.4)], 0.85, z=1.5)




def I_border():
    boxp(5.9, 5.9, 0.7, bevel=0.55)
    hole = boxp(4.1, 4.1, 3.0, bevel=0.1, loc=(0, 0, 0))
    i = len(kit.PARTS) - 1
    ob = kit.PARTS[i - 1]
    kit.PARTS.pop()
    bpy.context.view_layer.objects.active = ob
    m = ob.modifiers.new('d', 'BOOLEAN')
    m.operation = 'DIFFERENCE'
    m.solver = 'EXACT'
    m.object = hole
    bpy.ops.object.modifier_apply(modifier=m.name)
    bpy.data.objects.remove(hole, do_unlink=True)


ICONS = {
    'close': I_close, 'minus': I_minus,
    'chevron_up': I_chevron_up, 'chevron_down': I_chevron_down,
    'chevron_left': I_chevron_left, 'chevron_right': I_chevron_right,
    'arrow_up': I_arrow_up, 'arrow_down': I_arrow_down,
    'arrow_left': I_arrow_left, 'arrow_right': I_arrow_right,
    'doubleArrow': I_doubleArrow, 'eye': I_eye, 'eyeOff': I_eyeOff,
    'alignLeft': I_alignLeft, 'alignCenter': I_alignCenter, 'alignJustify': I_alignJustify,
    'grid': I_grid, 'list': I_list, 'stream': I_stream, 'sort': I_sort,
    'funnel': I_funnel, 'more': I_more, 'external': I_external,
    'fullscreen': I_fullscreen, 'crop': I_crop, 'selectAll': I_selectAll,
    'drag': I_drag, 'density': I_density, 'aspect': I_aspect, 'blur': I_blur,
    'rotate': I_rotate, 'sync': I_sync, 'carousel': I_carousel, 'home': I_home,
    'leaf': I_leaf, 'snow': I_snow, 'cake': I_cake, 'camera': I_camera,
    'textQuote': I_textQuote, 'textHash': I_textHash, 'sliders': I_sliders,
    'download': I_download, 'upload': I_upload, 'timer': I_timer, 'code': I_code,
    'tv': I_tv, 'tag': I_tag, 'backup': I_backup, 'keyboard': I_keyboard,
    'input': I_input, 'border': I_border,
    'textBold': lambda: I_letters('B'),
    'textItalic': lambda: I_letters('I', skew=0.18),
    'textTitle': lambda: I_letters('A'),
    'textStrike': lambda: I_letters('S', strike=True),
}

if __name__ == '__main__':
    os.makedirs('/tmp/bl/png', exist_ok=True)
    only = sys.argv[1:] if len(sys.argv) > 1 else list(ICONS.keys())
    for k in only:
        build(k, ICONS[k])
        print('rendered', k)
