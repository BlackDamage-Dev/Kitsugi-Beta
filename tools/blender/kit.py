"""Kitsugi 3D icon kit — Blender/Cycles ile 3dicons hattinin birebir karsiligi."""
import bpy, math, os, sys
from mathutils import Vector, Euler

D2R = math.pi / 180

PARTS = []


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    PARTS.clear()
    sc = bpy.context.scene
    sc.render.engine = 'CYCLES'
    sc.cycles.device = 'CPU'
    sc.cycles.samples = 96
    sc.cycles.use_denoising = True
    sc.cycles.max_bounces = 8
    sc.cycles.transparent_max_bounces = 8
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = 'PNG'
    sc.render.image_settings.color_mode = 'RGBA'
    sc.view_settings.view_transform = 'Standard'
    return sc


def _obj(ob):
    PARTS.append(ob)
    return ob


def stroke(pts, r, z=0.0):
    """Yuvarlak uclu, dairesel kesitli cubuk (kapsul hatti). pts: [(x,y),...]"""
    cu = bpy.data.curves.new('c', 'CURVE')
    cu.dimensions = '3D'
    cu.resolution_u = 12
    cu.bevel_depth = r
    cu.bevel_resolution = 6
    cu.use_fill_caps = True
    sp = cu.splines.new('POLY')
    sp.points.add(len(pts) - 1)
    for i, (x, y) in enumerate(pts):
        sp.points[i].co = (x, y, z, 1)
        sp.points[i].radius = 1.0
    ob = bpy.data.objects.new('c', cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    bpy.ops.object.convert(target='MESH')
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def plate(pts, depth, bevel=0.35, z=0.0):
    """2B cokgen -> kalin plaka + yuvarlatilmis kenar."""
    me = bpy.data.meshes.new('p')
    verts = [(x, y, z) for (x, y) in pts]
    faces = [list(range(len(pts)))]
    me.from_pydata(verts, [], faces)
    me.update()
    ob = bpy.data.objects.new('p', me)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    sol = ob.modifiers.new('sol', 'SOLIDIFY')
    sol.thickness = depth
    sol.offset = 0
    bpy.ops.object.modifier_apply(modifier='sol')
    if bevel > 0:
        b = ob.modifiers.new('b', 'BEVEL')
        b.width = bevel
        b.segments = 6
        b.limit_method = 'ANGLE'
        b.angle_limit = 40 * D2R
        bpy.ops.object.modifier_apply(modifier='b')
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def boxp(hx, hy, hz, bevel=0.35, loc=(0, 0, 0), rot=0.0):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    ob = bpy.context.object
    ob.scale = (hx * 2, hy * 2, hz * 2)
    bpy.ops.object.transform_apply(scale=True)
    if rot:
        ob.rotation_euler = (0, 0, rot)
        bpy.ops.object.transform_apply(rotation=True)
    if bevel > 0:
        b = ob.modifiers.new('b', 'BEVEL')
        b.width = bevel
        b.segments = 6
        b.limit_method = 'ANGLE'
        bpy.ops.object.modifier_apply(modifier='b')
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def discp(r, hz, loc=(0, 0, 0), bevel=0.2, seg=72):
    bpy.ops.mesh.primitive_cylinder_add(vertices=seg, radius=r, depth=hz * 2, location=loc)
    ob = bpy.context.object
    if bevel > 0:
        b = ob.modifiers.new('b', 'BEVEL')
        b.width = bevel
        b.segments = 5
        b.limit_method = 'ANGLE'
        bpy.ops.object.modifier_apply(modifier='b')
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def ballp(r, loc=(0, 0, 0), seg=48):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=max(8, seg // 2), radius=r, location=loc)
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def ringp(R, r, loc=(0, 0, 0), seg=72):
    bpy.ops.mesh.primitive_torus_add(major_radius=R, minor_radius=r, location=loc,
                                     major_segments=seg, minor_segments=24)
    ob = bpy.context.object
    bpy.ops.object.shade_smooth()
    return _obj(ob)


def join(name='ICON'):
    """Tum parcalari boolean UNION ile tek gövdeye indir (z-fighting yok)."""
    objs = [o for o in PARTS if o.name in bpy.data.objects]
    base = objs[0]
    # malzeme yuvalarini birlestir: boolean sonrasi yuzeyler dogru malzemeyi korusun
    for o in objs[1:]:
        for m in o.data.materials:
            if m is not None and m.name not in [x.name for x in base.data.materials if x]:
                base.data.materials.append(m)
    bpy.ops.object.select_all(action='DESELECT')
    bpy.context.view_layer.objects.active = base
    for o in objs[1:]:
        m = base.modifiers.new('u', 'BOOLEAN')
        m.operation = 'UNION'
        m.solver = 'EXACT'
        m.object = o
        try:
            bpy.ops.object.modifier_apply(modifier=m.name)
        except Exception:
            base.modifiers.remove(m)
        bpy.data.objects.remove(o, do_unlink=True)
    base.name = name
    bpy.ops.object.select_all(action='DESELECT')
    base.select_set(True)
    bpy.context.view_layer.objects.active = base
    bpy.ops.object.origin_set(type='ORIGIN_GEOMETRY', center='BOUNDS')
    bpy.ops.object.shade_smooth()
    try:
        base.data.use_auto_smooth = True
        base.data.auto_smooth_angle = 0.7
    except Exception:
        pass
    return base


def gradient_material(ob=None, ang=90.0, stops=None, rough=0.42, coat=0.08, soft=None, sat=1.18):
    m = bpy.data.materials.new("grad")
    m.use_nodes = True
    nt = m.node_tree
    n = nt.nodes
    l = nt.links
    bsdf = n["Principled BSDF"]
    bsdf.inputs["Roughness"].default_value = rough
    if "Coat Weight" in bsdf.inputs:
        bsdf.inputs["Coat Weight"].default_value = coat
        bsdf.inputs["Coat Roughness"].default_value = 0.10
    tex = n.new("ShaderNodeTexCoord")
    sub = n.new("ShaderNodeVectorMath")
    sub.operation = 'SUBTRACT'
    sub.inputs[1].default_value = (0.0, 0.0, 0.0)
    dot = n.new("ShaderNodeVectorMath")
    dot.operation = 'DOT_PRODUCT'
    dot.inputs[1].default_value = (math.cos(ang * D2R), math.sin(ang * D2R), 0.0)
    mr = n.new("ShaderNodeMapRange")
    if soft is None:
        dim = max(ob.dimensions) if ob is not None else 10.0
        soft = dim * 0.52
    mr.inputs["From Min"].default_value = -soft
    mr.inputs["From Max"].default_value = soft
    mr.clamp = True
    ramp = n.new("ShaderNodeValToRGB")
    cr = ramp.color_ramp
    cr.interpolation = 'EASE'
    st = stops or [
        (0.00, (0.945, 0.560, 0.235)),
        (0.35, (0.870, 0.330, 0.470)),
        (0.65, (0.600, 0.180, 0.720)),
        (1.00, (0.230, 0.120, 0.830)),
    ]
    cr.elements[0].position = st[0][0]
    cr.elements[0].color = (*st[0][1], 1)
    cr.elements[1].position = st[-1][0]
    cr.elements[1].color = (*st[-1][1], 1)
    for pos, col in st[1:-1]:
        e = cr.elements.new(pos)
        e.color = (*col, 1)
    l.new(tex.outputs["Object"], sub.inputs[0])
    l.new(sub.outputs["Vector"], dot.inputs[0])
    l.new(dot.outputs["Value"], mr.inputs["Value"])
    l.new(mr.outputs["Result"], ramp.inputs["Fac"])
    hs = n.new("ShaderNodeHueSaturation")
    hs.inputs["Saturation"].default_value = sat
    l.new(ramp.outputs["Color"], hs.inputs["Color"])
    l.new(hs.outputs["Color"], bsdf.inputs["Base Color"])
    if ob is not None:
        ob.data.materials.clear()
        ob.data.materials.append(m)
    return m


def solid_material(ob, color, rough=0.35, metal=0.0, name="solid"):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    ob.data.materials.append(m)
    return m


def studio(sc, scale=1.0, power=1.0):
    def area(name, loc, rot, size, pw, col=(1, 1, 1)):
        d = bpy.data.lights.new(name, 'AREA')
        d.size = size
        d.energy = pw * power
        d.color = col
        d.shape = 'SQUARE'
        o = bpy.data.objects.new(name, d)
        sc.collection.objects.link(o)
        o.location = loc
        o.rotation_euler = rot
    area("key", (-7 * scale, 7 * scale, 10 * scale), (38 * D2R, -22 * D2R, 0), 10 * scale, 1100)
    area("fill", (9 * scale, -5 * scale, 6 * scale), (62 * D2R, 42 * D2R, 0), 9 * scale, 240, (0.93, 0.94, 1.0))
    area("rim", (1 * scale, 9 * scale, 5 * scale), (70 * D2R, 4 * D2R, 0), 7 * scale, 380, (1, 0.97, 0.93))
    w = bpy.data.worlds.new("w")
    sc.world = w
    w.use_nodes = True
    w.node_tree.nodes["Background"].inputs[0].default_value = (0.86, 0.86, 0.90, 1)
    w.node_tree.nodes["Background"].inputs[1].default_value = 0.30


def camera(sc, span=12.0, tilt=1.0, yaw=0.0):
    cam = bpy.data.cameras.new("cam")
    cam.lens = 135
    cam.sensor_width = 36
    co = bpy.data.objects.new("cam", cam)
    sc.collection.objects.link(co)
    sc.camera = co
    dist = span * cam.lens / cam.sensor_width
    co.location = (0, -math.sin(tilt * D2R) * dist, math.cos(tilt * D2R) * dist)
    co.rotation_euler = (tilt * D2R, 0, yaw * D2R)
    return co


def shoot(sc, path, res=384):
    sc.render.resolution_x = res
    sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)


def paint(ob, color, rough=0.34, metal=0.0):
    m = bpy.data.materials.new("p_%s" % (ob.name,))
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    ob.data.materials.clear()
    ob.data.materials.append(m)
    return ob


def cut(ob, cutter):
    bpy.context.view_layer.objects.active = ob
    m = ob.modifiers.new('d', 'BOOLEAN')
    m.operation = 'DIFFERENCE'
    m.solver = 'EXACT'
    m.object = cutter
    bpy.ops.object.modifier_apply(modifier=m.name)
    bpy.data.objects.remove(cutter, do_unlink=True)
    if ob.name in [o.name for o in PARTS]:
        pass
    else:
        PARTS.append(ob)
    return ob
