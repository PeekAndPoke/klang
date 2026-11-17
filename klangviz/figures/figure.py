# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# What the band's figures share (KLANG-00446): a human skeleton of points with a thickness each (Skin modifier),
# shaped by extra points along the limbs and the trunk, flattened front to back, with an armature of the real joints
# only; and the steps a character shapes its body with. Every figure has this one skeleton, so every motion of the
# show fits everyone. What makes a character is in its own file (sirene.py, skalpell.py, ...): its measures on this
# skeleton and its own shaping, written out step by step.
import bpy, math
from mathutils import Vector

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
    # only the underside of the head; the chin that shows is drawn out of the skin (draw_chin()). Pushed forward to show,
    # this point grew a stalk under the face that read as a neck bending down (the drummer, 2026-11-16)
    ("chin",  "head",  (0, -0.060, 1.588), 0.045, 1.0, False, "head"),
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
    # also read where set: eye_drop (how much lower the eyes sit, in m), eye_shape (an eye's scale) and long_skull
    # (the eyes sit where a long skull's forehead is)
)

# The right hand of who picks strings is a little shorter than a plain hand: held half closed, it reaches less far.
# (A hand modelled closed, with fingers, a thumb and a plectrum, was tried and looked crippled: the maintainer,
# 2026-11-16; see 6d4c101b7.) For a character's `move`.
PICK_HAND = {"handR": (0, 0, 0.032)}


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


# The skin's colour, and the hair's: near black. (Two materials of one colour: the show tells them apart by name.)
DARK = (0.012, 0.012, 0.016)


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


def mane(head, size, mat, length=0.46):
    """
    Long hair worn open, as one smooth mane: from the back of the skull down over the shoulders and the back to
    between the shoulder blades, widening as it falls and wrapping round the head and the neck. Its top moves with the head, its end lies on the back (the chest),
    and it stretches between the two, so it does not swing like a board when the head nods.
    """
    root = head + Vector((0, 0.055, 0.085)) * size
    end = head + Vector((0, 0.150, 0.075 - length)) * size
    axis = (end - root).normalized()
    across = Vector((1, 0, 0))
    out = axis.cross(across).normalized()
    if out.y < 0: out = -out
    half = (end - root).length / 2
    middle = (root + end) / 2
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=24, ring_count=20, location=(0, 0, 0))
    obj = bpy.context.object
    head_w, chest_w = [], []
    for v in obj.data.vertices:
        t = (1 - v.co.z) / 2                                # 0 at the root, 1 at the end
        # open hair: it widens as it falls, over the shoulders, and wraps round the head and the neck: its edges
        # come forward past the ears, less so further down where it lies on the back
        wide = (0.105 + 0.105 * min(1.0, t / 0.55) - 0.030 * max(0.0, t - 0.7) / 0.3) * size
        thick = (0.030 + 0.012 * math.sin(math.pi * t)) * size
        bow = out * (0.022 * size * math.sin(math.pi * t))
        x, y = v.co.x, v.co.y
        wrap = out * (-(0.085 - 0.045 * t) * size * x * x)
        # the ends hang uneven, strand by strand
        ragged = axis * (0.030 * size * math.sin(x * 9.0) * max(0.0, t - 0.6) / 0.4)
        v.co = middle - axis * (v.co.z * half) + across * (x * wide) + out * (y * thick) + bow + wrap + ragged
        on_back = min(1.0, max(0.0, (t - 0.25) / 0.6))
        head_w.append((v.index, 1 - on_back)); chest_w.append((v.index, on_back))
    bpy.ops.object.shade_smooth()
    obj.data.materials.append(mat)
    for name, weights in (("head", head_w), ("chest", chest_w)):
        group = obj.vertex_groups.new(name=name)
        for index, weight in weights:
            if weight > 0: group.add([index], weight, 'REPLACE')
    return obj


def skull(head, size, mat, long=1.30, high=0.107, back=0.030, rise=0.056, tilt=30.0, wide=0.082):
    """
    The long back of a head: an egg, its long axis rising toward the back (by [tilt] degrees), so the skull sweeps
    up and back from the brow in one smooth line. With the defaults (the frontwoman's) it is about 1.2 times as long
    as it is high; it is widest from its middle back and only a little narrower at the forehead. The lead guitarist
    passes his own measures: a plain round back of the head.
    """
    high = high * size
    long = 0.098 * size * long
    wide = wide * size
    bpy.ops.mesh.primitive_uv_sphere_add(radius=1.0, segments=28, ring_count=18, location=(0, 0, 0))
    obj = bpy.context.object
    tilt = math.radians(tilt)
    axis = Vector((0, math.cos(tilt), math.sin(tilt)))
    up = Vector((0, -math.sin(tilt), math.cos(tilt)))
    centre = head + Vector((0, back * size, rise * size))
    for v in obj.data.vertices:
        along = v.co.y                                   # -1 at the forehead, 1 at the back
        egg = 0.97 + 0.03 * math.sin((along + 1.0) / 2.0 * math.pi * 0.83) / math.sin(math.pi * 0.83 / 2) if along < 0.33 else 1.0
        egg = min(1.0, egg)
        v.co = centre + axis * (along * long) + up * (v.co.z * high * egg) + Vector((v.co.x * wide * egg, 0, 0))
    bpy.ops.object.shade_smooth()
    obj.data.materials.append(mat)
    obj.vertex_groups.new(name="head").add(range(len(obj.data.vertices)), 1.0, 'REPLACE')
    return obj


def join_into(body, part):
    """Makes [part] a part of [body]'s mesh. One part at a time, in the order the parts are made: the order of the
    joins is the order of the materials and of the vertices."""
    bpy.ops.object.select_all(action='DESELECT')
    part.select_set(True); body.select_set(True)
    bpy.context.view_layer.objects.active = body
    bpy.ops.object.join()


def skin_body(key, P, names):
    """The body as the skeleton's skin: a point each with its thickness, the Skin modifier round them."""
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
    return body


def name_pieces(body, P, names):
    """Names every piece of the skin `seg_<the point it ends at>`, and makes the skin real geometry."""
    # Blender's armature for the skeleton gives every piece of the skin its group, named here after the point the
    # piece ends at; the armature itself is not kept
    bpy.ops.object.skin_armature_create(modifier="Skin")
    gen = bpy.context.view_layer.objects.active
    for bone in list(gen.data.bones):
        tail = gen.matrix_world @ bone.tail_local
        near = min(names, key=lambda n: (P[n].pos - tail).length)
        new = near if (P[near].pos - tail).length < 1e-4 else "hips"
        vg = body.vertex_groups.get(bone.name)
        if vg:
            vg.name = "seg_" + new
    activate(body)
    bpy.ops.object.modifier_apply(modifier="Skin")
    for m in list(body.modifiers):
        body.modifiers.remove(m)
    bpy.data.objects.remove(gen, do_unlink=True)


def flatten(body, P):
    """Reads the `seg_` names, so it comes after name_pieces() and before bind_to_joints(); and it shapes the coarse
    mesh, so before subdivide()."""
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


def bind_to_joints(body, P, names, extras):
    """Turns the pieces' groups into the joints' (the bones' names), before subdivide() spreads them."""
    # A piece moves with the joint its limb ends at; a piece that only shapes (a chin, a belly) with the joint it grows from
    def owner(n):
        if P[n].joint:
            return n
        kids = [c for c in names if P[c].parent == n]
        chain = n
        while len(kids) == 1 and not P[kids[0]].joint:
            chain = kids[0]
            kids = [c for c in names if P[c].parent == chain]
        if len(kids) == 1 and n not in ("crown",) and all(e[0] != n for e in extras):
            return kids[0]
        up = P[n].parent
        while not P[up].joint:
            up = P[up].parent
        return up

    for n in names:
        merge_group(body, "seg_" + n, "tmp_" + owner(n))
    for g in body.vertex_groups:
        g.name = g.name[4:]


def subdivide(body):
    sub = body.modifiers.new("Subsurf", "SUBSURF")
    sub.levels = 2
    bpy.ops.object.modifier_apply(modifier="Subsurf")
    bpy.ops.object.shade_smooth()


def soften_weights(body):
    """After subdivide() (how far it spreads goes by the mesh's fineness), before any part is joined on (eyes, skull
    and mane keep their own plain weights)."""
    # Soft joints. So far every piece of skin belongs wholly to one bone, so a joint that bends folds the skin along
    # a hard line: a kink, worst on thick limbs (the drummer's arms, the maintainer, 2026-11-16). Each point's weights
    # are now averaged with its neighbours' a few times over, so the skin bends over a stretch round the joint.
    mesh = body.data
    near = [[] for _ in mesh.vertices]
    for e in mesh.edges:
        a_, b_ = e.vertices
        near[a_].append(b_); near[b_].append(a_)
    weights = [{g.group: g.weight for g in v.groups} for v in mesh.vertices]
    for _ in range(7):
        mixed = []
        for i, own_w in enumerate(weights):
            total = dict(own_w)
            for j in near[i]:
                for g, w in weights[j].items():
                    total[g] = total.get(g, 0.0) + w
            share = 1.0 / (1 + len(near[i]))
            mixed.append({g: w * share for g, w in total.items()})
        weights = mixed
    for g in body.vertex_groups:
        g.remove(range(len(mesh.vertices)))
    for i, w in enumerate(weights):
        best = sorted(w.items(), key=lambda x: -x[1])[:4]
        total = sum(x[1] for x in best)
        for g, x in best:
            if x / total > 0.01:
                body.vertex_groups[g].add([i], x / total, 'REPLACE')


def draw_chin(body, head, hs, strength, drop=1.0, fore=1.0):
    # The chin is no added piece but the head's own skin, drawn out: the point of the real surface under the front of
    # the face is found, and the skin round it is pulled forward and down, most at that point and fading smoothly
    # round it, narrower across than along, so it comes to a soft point and runs back into the jaw without an edge.
    # [strength]: how far. [drop]: how much of the usual way down the skin is pulled (less: the chin juts forward and
    # the face is no longer), [fore]: how much of the usual way forward.
    near = [v for v in body.data.vertices if (v.co - head.pos).length < 1.05 * head.radius and v.co.z < head.pos.z]
    if near:
        tip = max(near, key=lambda v: -0.62 * (v.co.y - head.pos.y) - 0.78 * (v.co.z - head.pos.z) - 6.0 * abs(v.co.x)).co.copy()
        pull = Vector((0, -0.80 * fore, -0.60 * drop)) * (0.050 * hs * strength)
        for v in body.data.vertices:
            d = v.co - tip
            if d.length < 0.16 * hs:
                w = math.exp(-((d.x / (0.028 * hs)) ** 2 + (d.y / (0.042 * hs)) ** 2 + (d.z / (0.034 * hs)) ** 2) / 2)
                v.co += pull * w


def shape_face(body, head, hs, taper=0.0, throat=0.0):
    """
    A face that comes to a chin, for a head that else runs straight down into its neck. [taper]: how much narrower
    the head is at the chin than at the eyes (seen from the front it becomes a V, and the upper neck stays that
    slim). [throat]: how far, in metres at a head of normal size, the front of the neck is set back under the chin,
    so the chin stands out before it and a jaw line runs back from it. Call it before draw_chin().
    """
    chin_z = head.pos.z - 0.085 * hs

    def smooth(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)

    for v in body.data.vertices:
        d = v.co - head.pos
        if d.length > 2.4 * head.radius or d.z > 0.012 * hs:
            continue
        # narrower from the eyes down to the chin, held through the upper neck, and out again to the shoulders
        down = smooth((head.pos.z - v.co.z) / (head.pos.z - chin_z))
        out = smooth((chin_z - 0.05 * hs - v.co.z) / (0.09 * hs))
        v.co.x *= 1.0 - taper * down * (1.0 - out)
        # the throat: the neck's front goes back, most a little under the chin
        front = smooth((head.pos.y + 0.020 * hs - v.co.y) / (0.060 * hs))
        under = math.exp(-((v.co.z - (chin_z - 0.050 * hs)) / (0.034 * hs)) ** 2 / 2)
        v.co.y += throat * hs * front * under


def flatten_face(body, head, hs):
    """
    A flat face: from the eyes down to the chin the skin that lies behind the face's front is brought forward to it,
    so no hollow is left between the head and the chin under it (lit from above it read as a mouth wide open).
    The front is where the skin is at the eyes' height; it fades out toward the sides, the brow and under the chin.
    """
    def smooth(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)

    eye_z = head.pos.z + 0.010 * hs
    chin_z = head.pos.z - 0.085 * hs
    at_eyes = [v.co.y for v in body.data.vertices
               if abs(v.co.x) < 0.03 * hs and abs(v.co.z - eye_z) < 0.03 * hs and v.co.y < head.pos.y
               and (v.co - head.pos).length < 1.6 * head.radius]
    if not at_eyes:
        return
    front = min(at_eyes)
    for v in body.data.vertices:
        if (v.co - head.pos).length > 1.8 * head.radius or v.co.y > head.pos.y or v.co.y <= front:
            continue
        high = smooth((eye_z + 0.03 * hs - v.co.z) / (0.03 * hs))
        low = smooth((v.co.z - (chin_z - 0.02 * hs)) / (0.025 * hs))
        side = smooth((0.065 * hs - abs(v.co.x)) / (0.03 * hs))
        v.co.y += (front - v.co.y) * high * low * side


# How deep an eye's socket is, in metres at a head of normal size
SOCKET = 0.010


# The muscles that show under the skin, each a soft rise drawn out of it: at which point of the figure, where
# from that point and how far out, and how wide, all in that point's own radius, so a thin figure gets thin muscles
# and a heavy one heavy ones. x is toward the figure's own side (outward), y back (negative: forward), z up.
#   (point, offset, direction of the rise, height, size, which half of the limb it lies on)
MUSCLES = {
    "glute":   ("hip",      (0.05, 0.70, -0.25), (0.0, 1.0, -0.15), 0.32, (0.70, 0.70, 0.80), "back"),
    "calf":    ("calf",     (0.00, 0.75, 0.50),  (0.0, 1.0, 0.0),   0.26, (0.65, 0.80, 1.30), "back"),
    "quad":    ("thigh",    (0.10, -0.75, 0.40), (0.0, -1.0, 0.0),  0.13, (0.55, 0.60, 1.30), "front"),
    "deltoid": ("shoulder", (0.45, 0.00, -0.15), (1.0, 0.0, 0.2),   0.16, (0.70, 0.80, 0.80), None),
    "bicep":   ("bicep",    (0.00, -0.65, 0.00), (0.0, -1.0, 0.0),  0.18, (0.60, 0.70, 1.20), "front"),
    "blade":   ("chest",    (0.45, 0.60, 0.00),  (0.0, 1.0, 0.0),   0.07, (0.33, 0.30, 0.45), "back"),
    "pec":     ("chest",    (0.42, -0.62, -0.15), (0.0, -1.0, 0.0), 0.06, (0.33, 0.30, 0.28), "front"),
}


def shape_muscles(body, P, H, only=None):
    """
    A little more of a body under the skin (the maintainer, 2026-11-16: "a little butt", "a little more emphasis on
    the musculature everywhere, but not too much"): the buttocks, the calves, the thighs' fronts, the shoulders,
    the upper arms, the shoulder blades and the chest (see MUSCLES; [only]: just these of them), the small of the
    back drawn in above the buttocks, and a shallow groove down the spine.
    """
    for name, (point, offset, direction, height, size, half) in MUSCLES.items():
        if only is not None and name not in only:
            continue
        for side, sx in (("L", 1.0), ("R", -1.0)):
            at = P[point + side] if point + side in P else P[point]
            R = at.radius
            centre = at.pos + Vector((sx * offset[0], offset[1], offset[2])) * R
            rise = Vector((sx * direction[0], direction[1], direction[2])).normalized() * (height * R)
            for v in body.data.vertices:
                d = v.co - centre
                q = (d.x / (size[0] * R)) ** 2 + (d.y / (size[1] * R)) ** 2 + (d.z / (size[2] * R)) ** 2
                if q > 9.0:
                    continue
                if half == "back" and v.co.y < at.pos.y:
                    continue
                if half == "front" and v.co.y > at.pos.y:
                    continue
                v.co += rise * math.exp(-q / 2)

    # the small of the back, drawn in: with the buttocks below it the back gets its curve
    hips, belly, chest = P["hips"], P["belly"], P["chest"]
    for v in body.data.vertices:
        if v.co.y <= belly.pos.y or abs(v.co.x) > 0.25 * H:
            continue
        if hips.pos.z - 0.05 * H < v.co.z < chest.pos.z + 0.08 * H:
            hollow = math.exp(-((v.co.z - belly.pos.z) / (0.07 * H)) ** 2 / 2 - (v.co.x / (0.10 * H)) ** 2 / 2)
            # the spine's groove: narrow, from the hips to between the shoulder blades, fading at both ends
            low = min(1.0, max(0.0, (v.co.z - hips.pos.z) / (0.06 * H)))
            high = min(1.0, max(0.0, (chest.pos.z + 0.06 * H - v.co.z) / (0.06 * H)))
            groove = low * high * math.exp(-(v.co.x / (0.018 * H)) ** 2 / 2)
            v.co.y -= (0.012 * hollow + 0.006 * groove) * H


def shape_bust(body, chest, H, apart, down, out, size):
    """
    A hint of breasts, drawn out of the chest's own skin (two added pieces left a flat plate with a crease round
    it). Each is a soft round rise on the chest's front: [apart] from the middle, [down] below the chest's point,
    [out] high at its middle and [size] across (its fall-off), all in metres at a figure of height 1. A little
    fuller below its middle than above, as weight makes it.
    """
    for side in (1.0, -1.0):
        cx, cz = chest.pos.x + side * apart * H, chest.pos.z - down * H
        for v in body.data.vertices:
            dx, dz = (v.co.x - cx) / (size * H), (v.co.z - cz) / (size * H)
            if dx * dx + dz * dz > 12.0:
                continue
            # on the front only, fading out round the sides (no edge where the waist's skin lies further back)
            front = min(1.0, max(0.0, (chest.pos.y + 0.045 * H - v.co.y) / (0.05 * H)))
            front = front * front * (3 - 2 * front)
            dz *= 1.25 if dz > 0 else 0.85
            rise = out * H * front * math.exp(-(dx * dx + dz * dz) / 2)
            v.co.y -= rise
            v.co.z -= 0.10 * rise


def add_eyes(body, p, head, hs, surface, skins):
    """
    [surface]: the points of the head's real surface (skin and skull), taken before the first eye is joined.
    [skins]: the meshes that make that surface; each eye gets a shallow socket pressed into them.
    """
    # The eyes: narrow, in the face, part of the body, moved by the head. Each is set on the real surface: the skin
    # (and the skull) is looked up in front of the eye's place, and the eye sits half sunk into it, from every side
    size = p["eye_size"] * hs
    low = (-0.008 if p.get("long_skull") else 0.012) - p.get("eye_drop", 0.0)
    for s in (1.0, -1.0):
        x, z = head.pos.x + 0.034 * s * size, head.pos.z + low * size
        front = [c.y for c in surface if abs(c.x - x) < 0.020 * hs and abs(c.z - z) < 0.016 * hs]
        y = (min(front) if front else head.pos.y - head.radius * 0.7) + 0.003 * hs
        # the socket: the skin round the eye's place goes back a little, most at its middle, a little wider and
        # higher than the eye; the eye sits that much deeper, so it lies in the skull and not on it
        deep = SOCKET * hs
        for skin in skins:
            for v in skin.data.vertices:
                if v.co.y < y + 0.030 * hs and abs(v.co.x - x) < 0.07 * hs and abs(v.co.z - z) < 0.05 * hs:
                    v.co.y += deep * math.exp(-(((v.co.x - x) / (0.024 * size)) ** 2 + ((v.co.z - z) / (0.015 * size)) ** 2) / 2)
        y += 0.8 * deep
        bpy.ops.mesh.primitive_uv_sphere_add(radius=0.015 * size, segments=12, ring_count=8, location=(x, y, z))
        eye = bpy.context.object
        eye.scale = p.get("eye_shape", (1.5, 0.6, 0.55))        # wide, flat, low: a slit; rounder where set
        bpy.ops.object.transform_apply(scale=True)
        eye.data.materials.append(body.data.materials[1])
        eye.vertex_groups.new(name="head").add(range(len(eye.data.vertices)), 1.0, 'REPLACE')
        join_into(body, eye)


def make_rig(key, P, names, body):
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
    return rig


class Work:
    """A figure while it is built: its [body], its points [P] by name, the [head]'s point, the head's size against
    a plain one ([hs]) and the figure's [height]."""
    def __init__(self, body, P, p):
        self.body, self.P = body, P
        self.head = P["head"]
        self.hs = self.head.radius / 0.092
        self.height = p["height"]


class Figure:
    """
    One character of the band. It says who it is ([key]: its file's name and its name in the show), its measures on
    the shared skeleton ([frame], see BASE) and how its body is shaped: [sculpt] (muscles, bust, face, chin), the
    back of its head where it has one of its own ([back_of_head]) and its hair ([hair]). [build] makes the figure:
    the shared steps in their fixed order, the character's own between them.
    """
    key = None
    frame = {}

    def sculpt(self, w):
        """The body's own shape, on the smooth skin: called before the eyes are set."""
        raise NotImplementedError

    def back_of_head(self, w):
        """A skull of its own behind the head, as a mesh; None for the plain round head."""
        return None

    def hair(self, w):
        """Hair joined to the body, after the eyes."""

    def build(self):
        """The figure: its body and its rig. The steps' order matters; each step says what it needs."""
        key = self.key
        p = dict(BASE); p.update(self.frame)
        P = points(p)
        names = list(P.keys())
        body = skin_body(key, P, names)
        name_pieces(body, P, names)
        flatten(body, P)
        bind_to_joints(body, P, names, p["extras"])
        subdivide(body)
        soften_weights(body)
        # the materials, in the order the show knows them by: the dark skin, the eyes (and the hair, where there is some)
        body.data.materials.append(material(key + "_dark", DARK))
        body.data.materials.append(material(key + "_eyes", (0, 0, 0), emission=p["eye"], strength=25.0))

        w = Work(body, P, p)
        self.sculpt(w)
        # a skull of its own, where one is worn, before the eyes: they have to find the forehead it makes
        back = self.back_of_head(w)
        head = w.head
        surface = [v.co.copy() for v in body.data.vertices if (v.co - head.pos).length < 2.2 * head.radius]
        if back:
            surface += [v.co.copy() for v in back.data.vertices]
        add_eyes(body, p, head, w.hs, surface, [body] + ([back] if back else []))
        if back:
            join_into(body, back)
        self.hair(w)

        rig = make_rig(key, P, names, body)
        missing = [g.name for g in body.vertex_groups if g.name not in rig.data.bones]
        print("BUILT", key, "bones", len(rig.data.bones), "verts", len(body.data.vertices), "groups without a bone", missing)
        return body, rig
