# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# What the band's figures share (KLANG-00446): a human skeleton of points with a thickness each (Skin modifier),
# shaped by extra points along the limbs and the trunk, flattened front to back, with an armature of the real joints
# only; and the steps a character shapes its body with. Every figure has this one skeleton, so every motion of the
# show fits everyone. What makes a character is in its own file (sirene.py, skalpell.py, ...): its measures on this
# skeleton and its own shaping, written out step by step.
import bmesh, bpy, heapq, math
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


def smooth(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def mend_weights(body, P):
    """Takes a limb's bones off skin that is not that limb's. The skin's pieces meet at the hips and at the chest,
    and there the shared weights give skin to the wrong limb: on the rhythm guitarist a patch of the lower belly on
    his right moved with the left thigh (a third of it), the upper back between the shoulders with both upper arms.
    A thigh that swung forward folded the groin of the other side, an arm that reached ahead wrinkled the back. Here
    a limb's bones lose the skin across the figure's middle (fading over 4 cm to each side of it), and the arm's
    bones the skin further in than 9 cm from the shoulder joint (fading over 5 cm); what is left is made whole again.
    (For a character's sculpt(), before it shapes by the bones' weights; fit_limbs() mends every figure at the end.)"""
    groups = {g.index: g.name for g in body.vertex_groups}
    limbs = ("shoulder", "elbow", "wrist", "hand", "hip", "knee", "ankle", "toe")
    arm_from = P["shoulderL"].pos.x - 0.14
    mended = []
    for v in body.data.vertices:
        kept, changed = {}, False
        for g in v.groups:
            name, share = groups[g.group], 1.0
            if name[:-1] in limbs and name[-1] in "LR":
                out = v.co.x if name[-1] == "L" else -v.co.x
                share = smooth((out + 0.04) / 0.08)
                if name[:-1] in ("elbow", "wrist", "hand"):
                    share *= smooth((out - arm_from) / 0.05)
            changed = changed or share < 1.0
            kept[g.group] = g.weight * share
        total = sum(kept.values())
        if not changed or total < 0.2:
            continue

        mended.append(v.index)
        for g, x in kept.items():
            if x / total < 0.005:
                body.vertex_groups[g].remove([v.index])
            else:
                body.vertex_groups[g].add([v.index], x / total, 'REPLACE')
    # which points: fit_limbs() evens how the trunk's share is split there (what a limb's share leaves behind is
    # ragged: behind the neck's foot, where both arms had a share, the neck's bone was cut by the four-bone limit)
    body["mended"] = mended


def soften_joint(body, centre, reach, times):
    """The bones' weights within [reach] of [centre], each point's averaged with its neighbours' [times] over (most
    at the middle, fading to nothing at the edge), so the skin bends over a longer stretch round the joint. A point
    keeps four bones at most. (For a character's sculpt(), where it shapes a limb by the bones' weights and wants
    that shape to run out softly; how the skin bends at the shoulders and the hips is set by fit_limbs() at the end.)"""
    mesh = body.data
    near = {}
    for e in mesh.edges:
        a, b = e.vertices
        near.setdefault(a, []).append(b)
        near.setdefault(b, []).append(a)
    # (only points that are part of the skin: its mesh may hold a few loose ones, inside the body)
    inside = {v.index: 1.0 - (v.co - centre).length / reach for v in mesh.vertices
              if v.index in near and (v.co - centre).length < reach}
    weights = {i: {g.group: g.weight for g in mesh.vertices[i].groups}
               for i in set(inside) | {j for i in inside for j in near[i]}}
    for _ in range(times):
        mixed = {}
        for i, share in inside.items():
            total = dict(weights[i])
            for j in near[i]:
                for g, x in weights[j].items():
                    total[g] = total.get(g, 0.0) + x
            k = min(1.0, 2.0 * share)
            mixed[i] = {g: weights[i].get(g, 0.0) * (1.0 - k) + x / (1 + len(near[i])) * k for g, x in total.items()}
        weights.update(mixed)

    for i in inside:
        best = sorted(weights[i].items(), key=lambda x: -x[1])[:4]
        total = sum(x for _, x in best)
        # (the list first: taking a point out of a group changes the point's groups under the loop)
        for g in [g.group for g in mesh.vertices[i].groups if g.group not in dict(best)]:
            body.vertex_groups[g].remove([i])

        for g, x in best:
            body.vertex_groups[g].add([i], x / total, 'REPLACE')


def relax(skin, times, trunk=False):
    """Evens the skin a character shapes, [times] over: each point goes half the way to the middle of its
    neighbours. All of it: and then as far back out again, so thin limbs do not shrink (this takes out the small
    folds the mesh's uneven faces leave). The [trunk] alone, from the hips up: without the way back, a real
    smoothing (the trunk is thick enough to lose nothing by it). [skin] is the character's own view of its skin
    while it shapes it: its `body` and `P`, `below_neck(v)` (how much of a point may move), `arms(i)` (how much of
    a point is an arm's) and, where it has them, `ends` (the hands' and the feet's shares by point: they stay)."""
    mesh = skin.body.data
    near = [[] for _ in mesh.vertices]
    for e in mesh.edges:
        a, b = e.vertices
        near[a].append(b); near[b].append(a)
    low = skin.P["hips"].pos.z
    ends = getattr(skin, "ends", None)
    inside = []
    for v in mesh.vertices:
        share = skin.below_neck(v)
        if ends is not None:
            share *= 1.0 - smooth(2.0 * ends.get(v.index, 0.0))

        if trunk:
            share *= (1.0 - skin.arms(v.index)) * smooth((v.co.z - low) / 0.06)

        # (a loose point, of which the skin's mesh may hold a few inside the body, has no neighbours)
        if share > 0.0 and near[v.index]:
            inside.append((v.index, share))

    for _ in range(times):
        for step in ((0.5,) if trunk else (0.5, -0.53)):
            moved = [(i, mesh.vertices[i].co + (sum((mesh.vertices[j].co for j in near[i]), Vector()) / len(near[i])
                                                - mesh.vertices[i].co) * (step * share)) for i, share in inside]
            for i, co in moved:
                mesh.vertices[i].co = co


# How a limb's skin meets the trunk's (fit_limbs()): the limb's bones, the joint it turns about, the point that
# gives the joint's size, and the bone that takes the trunk's share where a point has no trunk bone yet.
#   reach: over how far to either side of the border the skin goes from the trunk's to the limb's, in the joint's
#          radius; tilt: the border above an arm's joint, see fit_limbs(); fine: no edge of the skin round the joint
#          is left longer than this, in the joint's radius
LIMB_FIT = [
    dict(bones=("elbow", "wrist", "hand"), joint="shoulder", trunk="shoulder", reach=2.0, tilt=30.0, fine=0.4),
    dict(bones=("knee", "ankle", "toe"), joint="hip", trunk="hip", reach=1.5, tilt=None, fine=0.4),
]
# Bones that never turn against each other in the show (Rig.kt turns no shoulder and no hip bone): where a point has
# more than four bones, such a pair is counted as one before the weakest is dropped.
AS_ONE = {"shoulderL": "chest", "shoulderR": "chest", "hipL": "hips", "hipR": "hips"}


def fit_limbs(body, P, fits=LIMB_FIT):
    """
    How the skin is shared between the trunk and each limb, round the shoulder and the hip joints: set anew on the
    finished body, the last step before the rig, so it changes no shape. What soften_weights() (and a character's
    own soften_joint()) leave there goes by the mesh's edges, not by distance: on a big figure with a coarse skin
    the whole upper arm was half the trunk's (the drummer: from 15 cm above the joint to 20 cm below it), far from
    the joint skin kept a share of a limb it has nothing to do with (the upper back up to 0.4 of each arm, the
    lower belly of the other side's thigh), and the border lay level through the joint, so the shoulder's cap
    stayed behind when the arm went up. A raised arm then thinned to a pipe, drew a flat web out of the back and
    left the cap standing off the chest as a shell.

    Here each point is first wholly the limb's or the trunk's: the limb's where the limb's bones had more than half
    of it; and above an arm's joint by place: the limb's on the outer side of a plane through the joint that leans
    outward by [tilt] degrees (the cap goes with the arm, the shoulder's top toward the neck stays: a raglan
    sleeve's line). Then the border is made soft over the same distance everywhere, [reach] joint's radii to each
    side, measured along the skin (so a hanging arm takes none of the flank beside it), and a limb gets nothing
    across the figure's middle. The limb's share is split between its bones as it was, the trunk's between the
    trunk's (evened where a limb's share was taken away, see even() below).

    For this the skin round the four joints is cut finer first, each long edge in two, twice at most: no point that
    was there moves, the new ones lie on the faces as the model's file had them, and each faces the light as its
    place did, so the figure at rest is the same surface and looks the same. Measured on the six figures
    (2026-11-21, KLANG-00478): the skin that shrinks to under 0.6 of its area with both arms straight up fell to
    about half (the drummer: 2134 to 1100 cm2), the web behind the raised arms and the crumpled groin of a step
    are gone. What is left: far overhead (from about 120 degrees) the armpit still creases and the shoulder's top
    sinks a little; two bones blended in a straight line cannot do more there.
    """
    mesh = body.data
    # the skin round the joints first made fine enough to carry a soft border: the skin's own faces are up to 10 cm
    # long where a chest meets an arm. A long edge is cut in two, its new point on the edge (no point that was there
    # moves; a face is only cut into smaller ones), and takes its bones from the edge's two ends
    # (and the light falls on the skin as before: each point keeps the direction its skin faced, a new point gets
    # the mean of its edge's two ends'; left to themselves the new points would face as their flat faces do, and
    # the large faces of a chest would show)
    facing = mesh.attributes.new("facing", 'FLOAT_VECTOR', 'POINT')
    facing.data.foreach_set("vector", [x for n in mesh.vertex_normals for x in n.vector])
    mesh.calc_loop_triangles()
    cut = {}                                      # a four-sided face -> the two points its file's triangles share
    for t in mesh.loop_triangles:
        if len(mesh.polygons[t.polygon_index].vertices) == 4:
            cut[t.polygon_index] = sorted(set(cut.get(t.polygon_index, t.vertices)) & set(t.vertices))
    bm = bmesh.new()
    bm.from_mesh(mesh)

    def round_a_joint(co):
        return any((co - P[fit["joint"] + side].pos).length < (2.2 + fit["reach"]) * P[fit["joint"] + side].radius
                   for fit in fits for side in ("L", "R"))

    # (the faces there as triangles first, cut the way the model's file cuts them: a four-sided face of the skin is
    # not flat, and cut into smaller faces any other way its surface would lie up to 7 mm from where it was)
    bm.verts.ensure_lookup_table()
    for f in [f for f in bm.faces if f.material_index == 0 and len(f.verts) == 4 and all(round_a_joint(v.co) for v in f.verts)]:
        a, b = cut[f.index]
        bmesh.utils.face_split(f, bm.verts[a], bm.verts[b])
    faces = [f for f in bm.faces if f.material_index == 0 and len(f.verts) > 3 and all(round_a_joint(v.co) for v in f.verts)]
    bmesh.ops.triangulate(bm, faces=faces, quad_method='FIXED', ngon_method='EAR_CLIP')
    for _ in range(2):
        bm.edges.index_update()
        edges = set()
        for fit in fits:
            for side in ("L", "R"):
                joint = P[fit["joint"] + side]
                far = (2.2 + fit["reach"]) * joint.radius
                edges.update(e for e in bm.edges if e.calc_length() > fit["fine"] * joint.radius
                             and all((v.co - joint.pos).length < far for v in e.verts)
                             and e.link_faces and all(f.material_index == 0 and len(f.verts) == 3 for f in e.link_faces))
        if edges:
            # (in the mesh's own order: a set's order differs from run to run, and with it the new points')
            bmesh.ops.subdivide_edges(bm, edges=sorted(edges, key=lambda e: e.index), cuts=1,
                                      use_grid_fill=True, use_single_edge=True, smooth=0.0)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()
    facing = mesh.attributes["facing"]
    ways = [Vector(d.vector) for d in facing.data]
    mesh.attributes.remove(facing)
    ways = [w.normalized() if w.length > 1e-6 else Vector((0.0, 0.0, 1.0)) for w in ways]
    # (a face that is not smooth, an eye's, keeps facing its own way)
    mesh.normals_split_custom_set([tuple(ways[v]) if f.use_smooth else tuple(f.normal) for f in mesh.polygons for v in f.vertices])

    count = len(mesh.vertices)
    names = {g.index: g.name for g in body.vertex_groups}
    index = {g.name: g.index for g in body.vertex_groups}
    weights = [{g.group: g.weight for g in v.groups} for v in mesh.vertices]
    place = [v.co.copy() for v in mesh.vertices]

    # the ways along the skin: its edges, and across its four-sided faces
    near = [[] for _ in range(count)]
    pairs = {tuple(sorted(e.vertices)) for e in mesh.edges}
    for f in mesh.polygons:
        if len(f.vertices) == 4:
            pairs.add(tuple(sorted((f.vertices[0], f.vertices[2]))))
            pairs.add(tuple(sorted((f.vertices[1], f.vertices[3]))))
    for a, b in sorted(pairs):
        d = (place[a] - place[b]).length
        near[a].append((b, d)); near[b].append((a, d))

    def within(starts, limit):
        """The points within [limit] of [starts] along the skin, with how far."""
        far = {i: 0.0 for i in starts}
        heap = [(0.0, i) for i in starts]
        while heap:
            d, i = heapq.heappop(heap)
            if d > far[i]:
                continue
            for j, step in near[i]:
                nd = d + step
                if nd < limit and nd < far.get(j, limit):
                    far[j] = nd
                    heapq.heappush(heap, (nd, j))
        return far

    all_limbs = {index[b + side] for fit in fits for b in fit["bones"] for side in ("L", "R") if b + side in index}
    split = {}                                    # point -> the trunk's bones' shares among themselves, evened
    shares = {}                                   # (the limb's first bone's name) -> the limb's new share by point

    def even(i, ball, reach):
        """How the trunk's share of the point [i] is split between the trunk's bones: evened over half the [reach]
        ([ball]: the points round it along the skin, with how far), each point counting by how much of it was the
        trunk's. Under a limb's large share the split was left ragged by the four-bone limit (behind the neck's
        foot, where both arms had a share, the neck's bone was cut), and it shows once the limb's share is gone."""
        if i in split:
            return
        mixed = {}
        for j, d in ball.items():
            k = 1.0 - 2.0 * d / reach
            if k > 0.0:
                for g, x in weights[j].items():
                    if g not in all_limbs:
                        mixed[g] = mixed.get(g, 0.0) + k * x
        split[i] = mixed

    for fit in fits:
        for side, sx in (("L", 1.0), ("R", -1.0)):
            bones = [index[b + side] for b in fit["bones"] if b + side in index]
            if not bones:
                continue
            joint = P[fit["joint"] + side]
            first = P[fit["bones"][0] + side].pos
            along = (first - joint.pos).normalized()
            had = [sum(w.get(b, 0.0) for b in bones) for w in weights]
            own = [1.0 if x > 0.5 else 0.0 for x in had]
            if fit["tilt"] is not None:
                out = Vector((sx, 0.0, 0.0))
                out = (out - along * out.dot(along)).normalized()
                tilt = math.radians(fit["tilt"])
                normal = along * math.cos(tilt) + out * math.sin(tilt)
                for i in range(count):
                    d = place[i] - joint.pos
                    if d.dot(along) < 0.0 and d.length < 2.2 * joint.radius and sx * place[i].x > 0.0 and near[i]:
                        own[i] = 1.0 if d.dot(normal) > 0.0 else 0.0

            reach = fit["reach"] * joint.radius
            border = {i for i in range(count) for j, _ in near[i] if own[i] != own[j]}
            new = list(own)
            for i in within(border, reach):
                ball = within([i], reach)
                total = sum(1.0 - d / reach for d in ball.values())
                new[i] = sum((1.0 - d / reach) * own[j] for j, d in ball.items()) / total
                even(i, ball, reach)
            # nothing of a limb across the figure's middle
            for i in range(count):
                if new[i] > 0.0:
                    new[i] *= smooth((sx * place[i].x + 0.04) / 0.08)
                # (skin far from the border that loses a limb's share: its trunk's split is evened as well)
                if i not in split and had[i] - new[i] > 0.02 and near[i]:
                    even(i, within([i], 0.5 * reach), reach)
            shares[fit["bones"][0] + side] = (bones, had, new, index.get(fit["trunk"] + side))

    # the points a character's own mend_weights() took a limb's share from (their numbers hold: new points come last)
    mended = {i for i in body.get("mended", []) if i < count and near[i]}
    # (and the points beside them: the new ones cut into their edges above took their bones from them)
    for _ in range(2):
        mended |= {j for i in mended for j, _ in near[i]}
    if "mended" in body:
        del body["mended"]
    if mended and fits:
        reach = fits[0]["reach"] * P[fits[0]["joint"] + "L"].radius
        for i in mended:
            even(i, within([i], 0.5 * reach), reach)

    limb_bones = {b for bones, _, _, _ in shares.values() for b in bones}
    changed = 0
    for i in range(count):
        if i not in mended and not any(abs(new[i] - had[i]) > 1e-4 for _, had, new, _ in shares.values()):
            continue
        old = weights[i]
        limbs = sum(new[i] for _, _, new, _ in shares.values())
        scale = 1.0 / limbs if limbs > 1.0 else 1.0
        fresh = {}
        trunk_bone = None
        for bones, had, new, trunk in shares.values():
            share = new[i] * scale
            if share <= 0.0:
                continue
            trunk_bone = trunk_bone if trunk_bone is not None else trunk
            if had[i] > 1e-6:
                for b in bones:
                    if old.get(b, 0.0) > 0.0:
                        fresh[b] = old[b] / had[i] * share
            else:
                fresh[bones[0]] = share
        rest = max(0.0, 1.0 - sum(fresh.values()))
        if rest > 0.0:
            trunk = {g: x for g, x in old.items() if g not in limb_bones}
            total = sum(trunk.values())
            evened = split.get(i)
            if evened:
                # the evened split takes over as far as the point was a limb's: skin that was all the trunk's
                # keeps its own (a neck's foot, a waist)
                k = 1.0 if total <= 1e-6 or i in mended else smooth(2.0 * (1.0 - total))
                whole = sum(evened.values())
                trunk = {g: (trunk.get(g, 0.0) / total * (1.0 - k) if total > 1e-6 else 0.0) + evened.get(g, 0.0) / whole * k
                         for g in set(trunk) | set(evened)}
                total = sum(trunk.values())
            if total > 1e-6:
                for g, x in trunk.items():
                    fresh[g] = fresh.get(g, 0.0) + x / total * rest
            else:
                # (a point that was all a limb's: the trunk's share goes to the bone the limb hangs on)
                near_trunk = next((t for b, _, new, t in shares.values() if new[i] > 0.0 and t is not None), trunk_bone)
                fresh[near_trunk] = fresh.get(near_trunk, 0.0) + rest

        fresh = {g: x for g, x in fresh.items() if x > 0.002}
        # four bones at most: bones that never turn against each other count as one before the weakest goes
        while len(fresh) > 4:
            pair = next(((g, index[AS_ONE[names[g]]]) for g in sorted(fresh, key=fresh.get)
                         if names[g] in AS_ONE and AS_ONE[names[g]] in index and index[AS_ONE[names[g]]] in fresh), None)
            if pair:
                fresh[pair[1]] += fresh.pop(pair[0])
            else:
                fresh.pop(min(fresh, key=fresh.get))

        total = sum(fresh.values())
        for g in old:
            if g not in fresh:
                body.vertex_groups[g].remove([i])

        for g, x in fresh.items():
            body.vertex_groups[g].add([i], x / total, 'REPLACE')
        changed += 1
    return changed


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


def shape_muscles(body, P, H, only=None, gain=None):
    """
    A little more of a body under the skin (the maintainer, 2026-11-16: "a little butt", "a little more emphasis on
    the musculature everywhere, but not too much"): the buttocks, the calves, the thighs' fronts, the shoulders,
    the upper arms, the shoulder blades and the chest (see MUSCLES; [only]: just these of them; [gain]: a muscle's
    height times this, by name), the small of the back drawn in above the buttocks, and a shallow groove down the spine.
    """
    for name, (point, offset, direction, height, size, half) in MUSCLES.items():
        if only is not None and name not in only:
            continue
        for side, sx in (("L", 1.0), ("R", -1.0)):
            at = P[point + side] if point + side in P else P[point]
            R = at.radius
            centre = at.pos + Vector((sx * offset[0], offset[1], offset[2])) * R
            rise = Vector((sx * direction[0], direction[1], direction[2])).normalized() * (height * R * (gain or {}).get(name, 1.0))
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
    back of its head where it has one of its own ([back_of_head]), its eyes where they are not the plain ones ([eyes])
    and its hair ([hair]). [build] makes the figure:
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

    def eyes(self, w, p, back):
        """The eyes ([p]: the figure's measures, [back]: its own skull, or None): set on the head's real surface, each
        in a shallow socket pressed into it. A head that brings its eyes with it (head.py) does nothing here."""
        head = w.head
        surface = [v.co.copy() for v in w.body.data.vertices if (v.co - head.pos).length < 2.2 * head.radius]
        if back:
            surface += [v.co.copy() for v in back.data.vertices]
        add_eyes(w.body, p, head, w.hs, surface, [w.body] + ([back] if back else []))

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
        self.eyes(w, p, back)
        if back:
            join_into(body, back)
        self.hair(w)
        # last, on the finished body: how the skin is shared between the trunk and the limbs (it moves no point)
        fit_limbs(body, P)

        rig = make_rig(key, P, names, body)
        missing = [g.name for g in body.vertex_groups if g.name not in rig.data.bones]
        print("BUILT", key, "bones", len(rig.data.bones), "verts", len(body.data.vertices), "groups without a bone", missing)
        return body, rig
