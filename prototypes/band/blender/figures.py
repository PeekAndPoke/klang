# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# The band's figures (KLANG-00446): a human skeleton of points with a thickness each (Skin modifier), shaped by extra
# points along the limbs and the trunk, flattened front to back, with an armature of the real joints only.
# One skeleton, six characters by their build.
#
#   blender -b --factory-startup -P figures.py -- <previews dir> [preview] [export]
import bpy, os, sys
from mathutils import Vector

args = sys.argv[sys.argv.index("--") + 1:]
OUT = args[0]
PREVIEW = "preview" in args
EXPORT = "export" in args

# The base figure, a man of 1.78 m looking along -Y, Z up; the left side (x > 0), the right is mirrored.
# name: (parent, (x, y, z), radius, depth (front to back, as a share of the radius), is a joint, group)
CENTRE = [
    ("hips",  None,    (0, 0.00, 0.98), 0.150, 0.72, True,  "torso"),
    ("belly", "hips",  (0, 0.00, 1.12), 0.130, 0.72, True,  "waist"),
    ("ribs",  "belly", (0, -0.005, 1.26), 0.150, 0.70, False, "torso"),
    ("chest", "ribs",  (0, -0.01, 1.40), 0.160, 0.68, True,  "chest"),
    ("neck",  "chest", (0, -0.01, 1.535), 0.055, 1.0, True,  "neck"),
    ("head",  "neck",  (0, -0.025, 1.645), 0.092, 1.08, True, "head"),
    ("crown", "head",  (0, -0.015, 1.715), 0.082, 1.12, False, "head"),
    # a chin that shows in profile: well ahead of the neck and below the face
    ("chin",  "head",  (0, -0.082, 1.562), 0.036, 1.0, False, "head"),
]
SIDE = [
    ("shoulder", "chest",    (0.190, 0.00, 1.445), 0.068, 0.95, True,  "shoulder"),
    ("bicep",    "shoulder", (0.225, 0.00, 1.30),  0.050, 1.0, False, "arm"),
    ("elbow",    "bicep",    (0.245, 0.01, 1.15),  0.038, 1.0, True,  "arm"),
    ("forearm",  "elbow",    (0.255, -0.01, 1.06), 0.042, 1.0, False, "arm"),
    ("wrist",    "forearm",  (0.265, -0.05, 0.90), 0.027, 1.0, True,  "arm"),
    ("palm",     "wrist",    (0.270, -0.065, 0.83), 0.040, 0.55, False, "hand"),
    ("hand",     "palm",     (0.270, -0.075, 0.75), 0.026, 0.55, True, "hand"),
    ("hip",      "hips",     (0.092, 0.00, 0.92),  0.098, 0.95, True,  "thigh"),
    ("thigh",    "hip",      (0.100, -0.01, 0.73), 0.080, 1.0, False, "thigh"),
    ("knee",     "thigh",    (0.100, -0.01, 0.52), 0.054, 1.0, True,  "leg"),
    ("calf",     "knee",     (0.105, 0.02, 0.38),  0.057, 1.0, False, "leg"),
    ("ankle",    "calf",     (0.105, 0.015, 0.085), 0.036, 1.0, True, "leg"),
    ("toe",      "ankle",    (0.118, -0.17, 0.035), 0.034, 1.0, True, "leg"),
]

BASE = dict(
    height=1.0,            # all lengths
    scale={},              # group -> radius factor
    radius={},             # point -> radius factor, on top
    move={},               # point -> (x, y, z) added to its place, in m
    shoulders=1.0,         # how wide the shoulders stand
    hips=1.0,              # how wide the hip joints stand
    neck=1.0,              # the neck's length
    stoop=0.0,             # how far the head is ahead of the hips, in m
    extras=[],             # (name, parent, offset, radius, depth, is a joint)
    eye=(1.0, 0.35, 0.05), eye_size=1.0,
)

FIGURES = {
    # the lead guitar: slim, a little stooped, long shaggy hair
    "guitar1": dict(height=1.02, scale=dict(torso=0.90, waist=0.88, chest=0.92, arm=0.88, thigh=0.86, leg=0.88),
                    stoop=0.07, eye=(1.0, 0.15, 0.1),
                    extras=[("hairB", "crown", (0, 0.07, -0.03), 0.085, 1.0, False),
                            ("hairL", "hairB", (0.075, 0.02, -0.15), 0.05, 1.0, True),
                            ("hairR", "hairB", (-0.075, 0.02, -0.15), 0.05, 1.0, True),
                            ("hairL2", "hairL", (0.02, 0.0, -0.13), 0.028, 1.0, True),
                            ("hairR2", "hairR", (-0.02, 0.0, -0.13), 0.028, 1.0, True)]),
    # the bass: the tall thin one, a long neck, stooped
    "bass": dict(height=1.10, scale=dict(torso=0.80, waist=0.78, chest=0.82, shoulder=0.85, arm=0.78, thigh=0.76,
                                         leg=0.80, neck=0.85, head=0.94),
                 shoulders=0.92, hips=0.88, neck=1.35, stoop=0.11, eye=(0.3, 0.6, 1.0), eye_size=0.9,
                 eye_drop=0.016),                      # his eyes sit lower in the face
    # the drums: the muscular one: wide shoulders, a deep chest over a small waist, heavy arms, a thick neck
    "drums": dict(height=1.04, scale=dict(torso=1.08, waist=1.02, chest=1.30, shoulder=1.55, arm=1.55, hand=1.25,
                                          thigh=1.25, leg=1.25, neck=1.75, head=1.0),
                  radius=dict(bicep=1.25, ribs=1.18), shoulders=1.30, hips=1.05, neck=0.75, stoop=0.03,
                  eye=(1.0, 0.25, 0.02)),
    # the trumpet (the song's "trommel"), the frontwoman: small, a little athletic, a waist and hips
    "eimer": dict(height=0.92, scale=dict(torso=0.94, waist=0.75, chest=0.86, shoulder=0.83, arm=0.80, hand=0.86,
                                          thigh=1.07, leg=0.91, neck=0.76, head=0.98),
                  shoulders=0.93, hips=1.08, neck=1.32, eye=(0.9, 1.0, 0.2),
                  # her head after the long skulls of ancient Egyptian sculpture (the maintainer's picture, 2026-11-16):
                  # see skull(). A long neck, carried forward; the shell hat (shell()) is kept but not worn.
                  radius=dict(hips=1.20, ribs=0.88, hip=1.08, calf=1.05, bicep=1.08, crown=1.06), skull=True,
                  move=dict(head=(0, -0.022, 0), crown=(0, -0.030, 0.004), chin=(0, -0.028, -0.004),
                            # proud shoulders: level, not sloping, and held back a little
                            shoulderL=(0, 0.014, 0.026), shoulderR=(0, 0.014, 0.026)),
                  extras=[("bustL", "chest", (0.058, -0.084, -0.035), 0.058, 1.0, False),
                          ("bustR", "chest", (-0.058, -0.084, -0.035), 0.058, 1.0, False),
                          ("nose", "head", (0, -0.094, -0.014), 0.014, 1.0, False)]),
    # the marimba: a woman: narrow shoulders, a waist, hips, long legs, a long tail of hair
    "marimba": dict(height=0.97, scale=dict(torso=0.90, waist=0.74, chest=0.80, shoulder=0.72, arm=0.70, hand=0.82,
                                            thigh=1.0, leg=0.80, neck=0.72, head=0.95),
                    radius=dict(hips=1.20, ribs=0.86, hip=1.06, calf=0.95), shoulders=0.84, hips=1.06, neck=1.15,
                    eye=(0.4, 1.0, 0.8), eye_size=1.0,
                    extras=[("bustL", "chest", (0.062, -0.085, -0.035), 0.060, 1.0, False),
                            ("bustR", "chest", (-0.062, -0.085, -0.035), 0.060, 1.0, False),
                            ("hair1", "crown", (0, 0.085, 0.015), 0.062, 1.0, False),
                            ("hair2", "hair1", (0, 0.06, -0.14), 0.050, 1.0, True),
                            ("hair3", "hair2", (0, 0.02, -0.18), 0.040, 1.0, True),
                            ("hair4", "hair3", (0, 0.01, -0.18), 0.026, 1.0, True),
                            ("hair5", "hair4", (0, 0.02, -0.14), 0.010, 1.0, True)]),
    # the rhythm guitar: the shorter, heavier one: a belly, a broad face, a flat cap
    "guitar2": dict(height=0.94, scale=dict(torso=1.25, waist=1.55, chest=1.18, shoulder=1.15, arm=1.2, hand=1.1,
                                            thigh=1.25, leg=1.2, neck=1.5, head=1.08),
                    hips=1.2, shoulders=1.06, neck=0.7, eye=(1.0, 0.6, 0.1),
                    extras=[("gut", "belly", (0, -0.09, -0.01), 0.165, 1.0, False),
                            ("cap", "crown", (0, 0.0, 0.035), 0.088, 1.15, False),
                            ("brim", "cap", (0, -0.13, -0.015), 0.022, 1.0, False)]),
}
ORDER = ["guitar1", "bass", "drums", "eimer", "marimba", "guitar2"]


class Point:
    def __init__(self, name, parent, pos, radius, depth, joint):
        self.name, self.parent, self.pos, self.radius, self.depth, self.joint = name, parent, Vector(pos), radius, depth, joint


def points(p):
    """The figure's points in order, parents first."""
    H = p["height"]
    out = {}

    def add(name, parent, pos, r, depth, joint, group):
        x, y, z = pos
        mx, my, mz = p["move"].get(name, (0, 0, 0))
        x, y, z = x + mx, y + my, z + mz
        if group == "shoulder" or group in ("arm", "hand"):
            x = (abs(x) - 0.190 + 0.190 * p["shoulders"]) * (1 if x >= 0 else -1)
        if group in ("thigh", "leg"):
            x *= p["hips"]
        if name in ("neck", "head", "crown", "chin"):
            z += (p["neck"] - 1.0) * 0.11
        # the stoop: the higher above the hips, the further ahead
        lift = max(0.0, z - 0.98) / 0.66
        y -= p["stoop"] * lift * lift
        r *= p["scale"].get(group, 1.0) * p["radius"].get(name.rstrip("LR") if name[-1:] in "LR" else name, 1.0)
        out[name] = Point(name, parent, (x * H, y * H, z * H), r * (0.5 + 0.5 * H), depth, joint)

    for name, parent, pos, r, depth, joint, group in CENTRE:
        add(name, parent, pos, r, depth, joint, group)
    for side, s in (("L", 1.0), ("R", -1.0)):
        for name, parent, pos, r, depth, joint, group in SIDE:
            par = parent if parent in ("chest", "hips") else parent + side
            add(name + side, par, (pos[0] * s, pos[1], pos[2]), r, depth, joint, group)
    for name, parent, off, r, depth, joint in p["extras"]:
        out[name] = Point(name, parent, out[parent].pos + Vector(off) * H, r, depth, joint)
    return out


def material(name, color, rough=0.45, emission=None, strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*color, 1)
    b.inputs["Roughness"].default_value = rough
    if emission:
        b.inputs["Emission Color"].default_value = (*emission, 1)
        b.inputs["Emission Strength"].default_value = strength
    return m


def activate(obj):
    bpy.ops.object.select_all(action='DESELECT')
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj


def merge_group(body, src, dst):
    sg = body.vertex_groups.get(src)
    if sg is None or src == dst:
        return
    dg = body.vertex_groups.get(dst) or body.vertex_groups.new(name=dst)
    moves = [(v.index, g.weight) for v in body.data.vertices for g in v.groups if g.group == sg.index]
    for index, weight in moves:
        dg.add([index], weight, 'ADD')
    body.vertex_groups.remove(sg)


def shell(head, size, mat):
    """
    A snail's shell worn as a hat: a tube wound in a spiral that grows by the golden ratio squared with every turn
    (by the golden ratio every half turn), each whorl lying against the one before. The spiral lies in the plane of
    her profile, so from the side one sees it whole; its mouth opens downward over the top of her head, the whorls
    rise over the head and coil behind it, and its tip stands out a little to her left.
    """
    import math
    PHI = (1 + 5 ** 0.5) / 2
    grow = math.log(PHI * PHI) / (2 * math.pi)
    r_end = 0.070 * size                       # the mouth: a cap on the top of the head
    R_end = r_end / 0.60                       # the tube is 0.6 of its distance from the spiral's middle
    mouth = head + Vector((0, 0.005 * size, 0.078 * size))
    middle = mouth + Vector((0, R_end, 0))     # behind the head (the figure looks along -y)
    turns, steps, around = 2.7, 120, 16
    verts, faces = [], []
    for i in range(steps + 1):
        a = -turns * 2 * math.pi * (1 - i / steps)
        R = R_end * math.exp(grow * a)
        r = 0.60 * R
        radial = Vector((0, -math.cos(a), -math.sin(a)))
        centre = middle + radial * R + Vector((0.052 * size * (1 - R / R_end), 0, 0))
        for j in range(around):
            b = 2 * math.pi * j / around
            verts.append(centre + (radial * math.cos(b) + Vector((1, 0, 0)) * math.sin(b)) * r)
    for i in range(steps):
        for j in range(around):
            a0, a1 = i * around + j, i * around + (j + 1) % around
            faces.append((a0, a1, a1 + around, a0 + around))
    faces.append(tuple(range(around)))                                             # the tip, closed
    faces.append(tuple(reversed(range(steps * around, (steps + 1) * around))))    # the mouth, closed (the head is in it)
    mesh = bpy.data.meshes.new("shell")
    mesh.from_pydata(verts, [], faces)
    obj = bpy.data.objects.new("shell", mesh)
    bpy.context.collection.objects.link(obj)
    activate(obj)
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.ops.object.shade_smooth()
    obj.data.materials.append(mat)
    obj.vertex_groups.new(name="head").add(range(len(verts)), 1.0, 'REPLACE')
    return obj


def skull(head, size, mat):
    """
    The long back of a head: an egg, its narrow end in the forehead, its long axis rising toward the back at 30
    degrees, so the skull sweeps up and back from the brow in one smooth line. It is 1.3 times as long as it is high
    (the golden ratio's 1.618 was too long for the maintainer's eye), and widest two thirds of the way back.
    """
    import math
    high = 0.107 * size
    long = 0.098 * size * 1.30
    wide = 0.082 * size
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=28, ring_count=18, location=(0, 0, 0))
    obj = bpy.context.object
    tilt = math.radians(30)
    axis = Vector((0, math.cos(tilt), math.sin(tilt)))
    up = Vector((0, -math.sin(tilt), math.cos(tilt)))
    centre = head + Vector((0, 0.030 * size, 0.056 * size))
    for v in obj.data.vertices:
        along = v.co.y                                   # -1 at the forehead, 1 at the back
        egg = 0.97 + 0.03 * math.sin((along + 1.0) / 2.0 * math.pi * 0.83) / math.sin(math.pi * 0.83 / 2) if along < 0.33 else 1.0
        egg = min(1.0, egg)
        v.co = centre + axis * (along * long) + up * (v.co.z * high * egg) + Vector((v.co.x * wide * egg, 0, 0))
    bpy.ops.object.shade_smooth()
    obj.data.materials.append(mat)
    obj.vertex_groups.new(name="head").add(range(len(obj.data.vertices)), 1.0, 'REPLACE')
    return obj


def build(key):
    p = dict(BASE); p.update(FIGURES[key])
    P = points(p)
    names = list(P.keys())
    mesh = bpy.data.meshes.new(key)
    mesh.from_pydata([P[n].pos for n in names],
                     [(names.index(P[n].parent), names.index(n)) for n in names if P[n].parent], [])
    body = bpy.data.objects.new(key, mesh)
    bpy.context.collection.objects.link(body)
    activate(body)

    body.modifiers.new("Skin", "SKIN")
    for i, n in enumerate(names):
        v = mesh.skin_vertices[0].data[i]
        v.radius = (P[n].radius, P[n].radius)
        v.use_root = (n == "hips")

    # Blender's armature for the skeleton gives every piece of the skin its group, named here after the point the
    # piece ends at; the armature itself is not kept
    bpy.ops.object.skin_armature_create(modifier="Skin")
    gen = bpy.context.view_layer.objects.active
    for bone in list(gen.data.bones):
        tail = gen.matrix_world @ bone.tail_local
        near = min(names, key=lambda n: (P[n].pos - tail).length)
        new = near if (P[near].pos - tail).length < 1e-4 and near != "hips" else "hips"
        vg = body.vertex_groups.get(bone.name)
        if vg:
            vg.name = "seg_" + new
    activate(body)
    bpy.ops.object.modifier_apply(modifier="Skin")
    for m in list(body.modifiers):
        body.modifiers.remove(m)
    bpy.data.objects.remove(gen, do_unlink=True)

    # Flatter front to back: each piece by its two ends' depths
    groups = {g.index: g.name[4:] for g in body.vertex_groups}
    for v in body.data.vertices:
        if not v.groups:
            continue
        n = groups[max(v.groups, key=lambda g: g.weight).group]
        b = P[n]
        a = P[b.parent] if b.parent else b
        ab = b.pos - a.pos
        t = 0.0 if ab.length < 1e-6 else max(0.0, min(1.0, (v.co - a.pos).dot(ab) / ab.length_squared))
        cy = a.pos.y + ab.y * t
        v.co.y = cy + (v.co.y - cy) * (a.depth + (b.depth - a.depth) * t)

    # A piece moves with the joint its limb ends at; a piece that only shapes (a chin, a belly) with the joint it grows from
    def owner(n):
        if P[n].joint:
            return n
        kids = [c for c in names if P[c].parent == n]
        chain = n
        while len(kids) == 1 and not P[kids[0]].joint:
            chain = kids[0]
            kids = [c for c in names if P[c].parent == chain]
        if len(kids) == 1 and n not in ("crown",) and all(e[0] != n for e in p["extras"]):
            return kids[0]
        up = P[n].parent
        while not P[up].joint:
            up = P[up].parent
        return up

    for n in names:
        merge_group(body, "seg_" + n, "tmp_" + owner(n))
    for g in body.vertex_groups:
        g.name = g.name[4:]

    sub = body.modifiers.new("Subsurf", "SUBSURF")
    sub.levels = 2
    bpy.ops.object.modifier_apply(modifier="Subsurf")
    bpy.ops.object.shade_smooth()
    body.data.materials.append(material(key + "_dark", (0.012, 0.012, 0.016)))
    body.data.materials.append(material(key + "_eyes", (0, 0, 0), emission=p["eye"], strength=25.0))

    # The eyes: narrow, on the face, part of the body, moved by the head
    head = P["head"]
    size = p["eye_size"] * head.radius / 0.092
    for s in (1.0, -1.0):
        # set into the face, not on it: the smoothed skin lies inside the points' radius
        # under a long skull's forehead the eyes sit a little lower and further out, or it covers them
        deep, low = (0.90, 0.002) if p.get("skull") else (0.80, 0.012)
        at = head.pos + Vector((0.034 * s * size, -head.radius * deep, (low - p.get("eye_drop", 0.0)) * size))
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.015 * size, segments=12, ring_count=8, location=at)
        eye = bpy.context.object
        eye.scale = (1.5, 0.6, 0.55)
        bpy.ops.object.transform_apply(scale=True)
        eye.data.materials.append(body.data.materials[1])
        eye.vertex_groups.new(name="head").add(range(len(eye.data.vertices)), 1.0, 'REPLACE')
        bpy.ops.object.select_all(action='DESELECT')
        eye.select_set(True); body.select_set(True)
        bpy.context.view_layer.objects.active = body
        bpy.ops.object.join()

    if p.get("skull"):
        back = skull(head.pos, head.radius / 0.092, body.data.materials[0])
        bpy.ops.object.select_all(action='DESELECT')
        back.select_set(True); body.select_set(True)
        bpy.context.view_layer.objects.active = body
        bpy.ops.object.join()

    if p.get("shell"):
        hat = shell(head.pos, head.radius / 0.092, material(key + "_shell", (0.030, 0.027, 0.024), rough=0.22))
        bpy.ops.object.select_all(action='DESELECT')
        hat.select_set(True); body.select_set(True)
        bpy.context.view_layer.objects.active = body
        bpy.ops.object.join()

    # The armature: a bone for each joint, from the joint before it; named after the joint it ends at
    arm = bpy.data.armatures.new(key + "_rig")
    rig = bpy.data.objects.new(key + "_rig", arm)
    bpy.context.collection.objects.link(rig)
    activate(rig)
    bpy.ops.object.mode_set(mode='EDIT')
    for n in names:
        if not P[n].joint:
            continue
        bone = arm.edit_bones.new(n)
        up = P[n].parent
        while up and not P[up].joint:
            up = P[up].parent
        bone.head = P[up].pos if up else P[n].pos - Vector((0, 0, 0.10))
        bone.tail = P[n].pos
        if up:
            bone.parent = arm.edit_bones[up]
    bpy.ops.object.mode_set(mode='OBJECT')
    body.modifiers.new("Armature", "ARMATURE").object = rig
    body.parent = rig
    missing = [g.name for g in body.vertex_groups if g.name not in arm.bones]
    print("BUILT", key, "bones", len(arm.bones), "verts", len(body.data.vertices), "groups without a bone", missing)
    return body, rig


def spot(loc, at, color, energy, size=0.8):
    bpy.ops.object.light_add(type='SPOT', location=loc)
    l = bpy.context.object
    l.data.energy = energy; l.data.color = color; l.data.spot_size = size; l.data.spot_blend = 0.7
    l.rotation_euler = (Vector(at) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()


def camera(loc, at, lens):
    bpy.ops.object.camera_add(location=loc)
    cam = bpy.context.object
    cam.rotation_euler = (Vector(at) - Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    cam.data.lens = lens
    bpy.context.scene.camera = cam


bpy.ops.wm.read_factory_settings(use_empty=True)
for i, key in enumerate(ORDER):
    body, rig = build(key)
    if EXPORT:
        bpy.ops.object.select_all(action='DESELECT')
        body.select_set(True); rig.select_set(True)
        bpy.ops.export_scene.gltf(filepath=os.path.join(OUT, "..", "models", key + ".glb"), use_selection=True)
    rig.location.x = (i - (len(ORDER) - 1) / 2) * 1.05

if PREVIEW:
    bpy.ops.mesh.primitive_plane_add(size=60)
    bpy.context.object.data.materials.append(material("floor", (0.02, 0.02, 0.025), rough=0.22))
    # a dim glowing backdrop: the figures stand against it as silhouettes
    bpy.ops.mesh.primitive_plane_add(size=1, location=(0, 5, 3))
    back = bpy.context.object
    back.scale = (30, 8, 1); back.rotation_euler = (1.5708, 0, 0)
    back.data.materials.append(material("back", (0, 0, 0), emission=(0.10, 0.20, 0.55), strength=0.22))
    for x, c in ((-3.3, (0.2, 0.5, 1.0)), (-1.1, (1.0, 0.2, 0.5)), (1.1, (0.2, 0.5, 1.0)), (3.3, (1.0, 0.2, 0.5))):
        spot((x, 3.0, 3.6), (x * 0.8, 0, 1.2), c, 3000)
    spot((-2, -6, 4), (0, 0, 1), (1.0, 0.9, 0.8), 500, size=1.4)
    world = bpy.data.worlds.new("W"); bpy.context.scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.003, 0.003, 0.006, 1)
    s = bpy.context.scene
    s.render.engine = 'CYCLES'; s.cycles.samples = 40; s.cycles.device = 'CPU'; s.cycles.use_denoising = True
    s.render.resolution_x = 1500; s.render.resolution_y = 700
    camera((0, -9.5, 1.3), (0, 0, 0.98), 50)
    s.render.filepath = os.path.join(OUT, "lineup-front.png")
    bpy.ops.render.render(write_still=True)
    camera((6.5, -7.0, 1.7), (0, 0, 0.95), 50)
    s.render.filepath = os.path.join(OUT, "lineup-side.png")
    bpy.ops.render.render(write_still=True)
print("DONE")
