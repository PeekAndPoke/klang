# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

import bmesh

from mathutils import Vector

from figure import Figure, PICK_HAND, flatten_face, shape_face, shape_muscles

# Where the neck's foot is: this far above the shoulder joints, in metres. Nothing above it is touched: his head, his
# face, his cap and its brim stay as the skin made them. Out on the shoulders, away from the neck, the limit lies
# higher by SHOULDER_TOP (from NECK_WIDE out, over NECK_FADE), so the shoulders' tops can be rounded.
NECK_FOOT = 0.045
SHOULDER_TOP = 0.060
NECK_WIDE = 0.075
NECK_FADE = 0.055

# The skin is cut finer from here up (metres): the trunk, the arms and the thighs carry his form; the shins and the
# feet stay as the skin made them.
FINE_FROM = 0.44

# The trunk's outline by height (metres, from the top down): how many times as wide at the flank (where the arm
# hangs: the arm lies against him there, so the flank cannot go out), as wide in front of and behind the arm, as deep
# in front and as deep behind as the skin made it. The skin made a sack: narrow under the arms, widening all the way
# down, the belly sliding into the legs. A portly man with a frame under it has a broad, deep chest above the belly,
# a round upper back, the belly full and carried forward with its lower edge turning in to a waist line, a hollow
# small of the back and a seat. The belly's front stays where it was and nothing comes in front of it: his guitar
# hangs a hand's width before it (StringPlayer.kt), so the belly gets its shape from what is taken away above and
# below it.
TRUNK = [
    (1.385, 1.00, 1.00, 1.00, 1.00),
    (1.340, 1.00, 1.04, 1.07, 1.07),
    (1.300, 1.00, 1.12, 1.10, 1.11),
    (1.260, 1.00, 1.17, 1.07, 1.11),
    (1.220, 1.00, 1.17, 1.02, 1.08),
    (1.180, 1.01, 1.13, 0.98, 1.03),
    (1.140, 1.03, 1.10, 0.97, 0.98),
    (1.100, 1.05, 1.08, 0.985, 0.94),
    (1.060, 1.05, 1.06, 1.00, 0.91),
    (1.020, 1.03, 1.03, 1.00, 0.90),
    (0.980, 1.00, 1.00, 1.00, 0.93),
    (0.940, 0.985, 0.985, 0.97, 0.98),
    (0.900, 0.975, 0.975, 0.91, 1.02),
    (0.860, 0.975, 0.975, 0.89, 1.03),
    (0.820, 0.985, 0.985, 0.94, 1.01),
    (0.780, 1.00, 1.00, 1.00, 1.00),
]

# The limbs' thickness along their length, over two pieces (0 at the first joint, 1 at the second, 2 at the third):
# how many times as thick as the skin made them. Solid arms, thickest in the upper arm and under the elbow; the legs
# are heavy already, so the thigh only loses a little toward the knee and the calf gets its place.
LIMBS = [
    ("arm", ("shoulder", "elbow", "wrist"), [(0.0, 1.06), (0.45, 1.10), (1.0, 1.06), (1.3, 1.09), (1.75, 1.03), (2.0, 1.0)]),
    ("leg", ("hip", "knee", "ankle"), [(0.0, 1.0), (0.4, 1.0), (0.85, 0.95), (1.0, 0.95), (1.3, 1.03), (1.7, 0.97), (2.0, 1.0)]),
]

# Where the bones' weights are evened out once more, round a joint: (joint, how far round it in metres, how often).
# The skin's own weights change over a few centimetres there, and a thigh that swings forward or an arm that reaches
# ahead (his guitar) folds the skin along that line.
SOFT = [("hip", 0.17, 10), ("shoulder", 0.14, 6)]

# His masses on top of the shared muscles (figure.py's `shape_muscles`: the seat, the calves, the thighs' fronts and
# the rest, as he had them), each a soft rise drawn out of the skin, on his left side (the right is its mirror):
#   name: (point of the figure, the rise's middle from that point, the way it rises, how high, its size, options)
# all in metres; x is toward his own side (outward), y back (negative: forward), z up; a height below zero is a
# hollow. Options: `on`: whose skin moves ("arm": that side's arm alone, "body": everything but the arms, "leg":
# that side's leg alone), `half`: only the skin in front of the point or behind it, `flat`: above 1 the rise has a
# flatter top and a firmer edge, `under`: how many times as far the rise reaches below its middle as above it,
# `square`: above 2 the rise's outline seen from the front is squarer.
MUSCLES = {
    # round shoulders: the cap round the joint's outer side; and the skin made a corner on top of the shoulder, a
    # coat hanger: the corner is taken off
    "deltoid":  ("shoulder", (0.026, 0.000, -0.050), (1.0, 0.0, -0.05), 0.012, (0.036, 0.055, 0.055), dict(flat=1.4)),
    "corner":   ("shoulder", (0.040, 0.000, 0.062), (1.0, 0.0, 1.0), -0.020, (0.040, 0.060, 0.034), dict()),
    # solid arms: the upper arm's back
    "tricep":   ("bicep", (0.008, 0.030, 0.030), (0.35, 1.0, 0.0), 0.006, (0.030, 0.034, 0.060), dict(on="arm")),
    # the chest: broad and heavy, a soft plate on each side with a soft edge below, over the belly
    "pec":      ("chest", (0.092, -0.090, -0.075), (0.0, -1.0, -0.10), 0.008, (0.080, 0.080, 0.060),
                 dict(on="body", half="front", flat=1.6, under=0.60, square=3.0)),
    # the back: the shoulder blades (a round upper back), the long muscles beside the spine
    "blade":    ("chest", (0.085, 0.080, -0.030), (0.0, 1.0, 0.0), 0.004, (0.060, 0.050, 0.065), dict(on="body", half="back", flat=1.4)),
    "erector":  ("belly", (0.034, 0.060, 0.040), (0.0, 1.0, 0.0), 0.005, (0.028, 0.040, 0.110), dict(on="body", half="back", flat=1.2)),
}


class Klaue(Figure):
    """The rhythm guitar, "the manager": the shorter, heavier one, portly and sure of himself: a full belly carried
    forward under a broad chest, round shoulders, a broad face, a flat cap."""
    key = "klaue"
    frame = dict(
        height=0.94, scale=dict(torso=1.25, waist=1.44, chest=1.18, shoulder=1.15, arm=1.2, hand=1.1, thigh=1.25,
                                leg=1.2, neck=0.95, head=1.08),
        move=dict(
            PICK_HAND,
            # the shoulders a little lower than plain: between pulled up (as first built) and hanging (too much, the
            # maintainer)
            shoulderL=(0, 0, -0.020), shoulderR=(0, 0, -0.020),
            # the belly is the trunk's own, carried forward and full: a piece of its own set before the trunk left a
            # hard edge across the chest and corners at its sides, a box on the upper body (the maintainer,
            # 2026-11-17: "this rectangular thing needs to go away")
            # (a hint less of it than at first: the maintainer, the same day, "not so much waist fat")
            belly=(0, -0.036, -0.010), ribs=(0, -0.018, 0), hips=(0, -0.010, 0),
        ),
        radius=dict(belly=1.12),
        # a bit of a neck, not as thick as it was
        hips=1.2, shoulders=1.06, neck=1.06, eye=(0.30, 1.0, 0.34),
        extras=[("cap", "crown", (0, 0.0, 0.035), 0.088, 1.15, False),
                ("brim", "cap", (0, -0.13, -0.015), 0.022, 1.0, False)],
    )

    def sculpt(self, w):
        # the shared muscles and the head first, on the skin as it was made, exactly as before: so his head, his face
        # and his neck come out as they were
        shape_muscles(w.body, w.P, w.height)
        # A chin seen from the side: the throat is set back under it, so the chest no longer runs straight up into
        # the face (the maintainer, 2026-11-17: "we need a little Kinn when seen from the side"). Nothing is drawn
        # out of the face for it: a chin pulled forward left a hollow above it that read as a mouth wide open
        shape_face(w.body, w.head, w.hs, taper=0.0, throat=0.040)
        # and the face stays flat: the hollow between the head and the chin under it is filled
        flatten_face(w.body, w.head, w.hs)

        # everything below works under the neck's foot only (the maintainer, 2026-11-21: a real body, "a little less
        # soft, a little more a person"; the frame above is as it was, so every joint stays where it is)
        top = w.P["shoulderL"].pos.z + NECK_FOOT
        mend_weights(w.body, w.P)
        refine(w.body, top, FINE_FROM, longest=0.024)
        for joint, reach, times in SOFT:
            for side in ("L", "R"):
                soften_joint(w.body, w.P[joint + side].pos, reach, times)

        skin = Skin(w.body, w.P, top)
        relax(skin, 3, trunk=True)
        shape_trunk(skin, TRUNK)
        for limb in LIMBS:
            shape_limb(skin, *limb)

        for name in MUSCLES:
            shape_muscle(skin, *MUSCLES[name])

        # and the small folds the mesh's uneven faces leave under the masses' edges
        relax(skin, 1)


def under_neck(co, top):
    """How much of a point of the skin may move (0 to 1): nothing above the neck's foot [top], fading in over the
    3 cm under it; the limit lies higher out on the shoulders (see NECK_FOOT)."""
    limit = top + SHOULDER_TOP * smooth((abs(co.x) - NECK_WIDE) / NECK_FADE)
    return smooth((limit - co.z) / 0.03)


def mend_weights(body, P):
    """Takes a limb's bones off skin that is not that limb's. The skin's pieces meet at the hips and at the chest,
    and there the shared weights give skin to the wrong limb: a patch of the lower belly on his right moved with the
    left thigh (a third of it), the upper back between the shoulders with both upper arms. A thigh that swung
    forward folded the groin of the other side, an arm that reached ahead wrinkled the back. Here a limb's bones
    lose the skin across the figure's middle (fading over 4 cm to each side of it), and the arm's bones the skin
    further in than 9 cm from the shoulder joint (fading over 5 cm); what is left is made whole again."""
    groups = {g.index: g.name for g in body.vertex_groups}
    limbs = ("shoulder", "elbow", "wrist", "hand", "hip", "knee", "ankle", "toe")
    arm_from = P["shoulderL"].pos.x - 0.14
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

        for g, x in kept.items():
            if x / total < 0.005:
                body.vertex_groups[g].remove([v.index])
            else:
                body.vertex_groups[g].add([v.index], x / total, 'REPLACE')


def refine(body, top, low, longest):
    """A finer skin between the height [low] and the neck's foot [top]: every edge longer than [longest] is cut in
    two (the skin's own faces are up to 5 cm across on the trunk, too coarse to carry a form), the new points set on
    the round between the old ones. The old points stay where they are and the head keeps its mesh. A new point's
    bones are the mean of its neighbours' among the old points (of all its neighbours' where it has no two old ones:
    a point in the middle of a face); the four strongest are kept (a point is moved by four bones at most)."""
    mesh = body.data
    old = len(mesh.vertices)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    edges = [e for e in bm.edges if e.calc_length() > longest
             and all(v.co.z > low and under_neck(v.co, top) > 0.0 for v in e.verts)]
    bmesh.ops.subdivide_edges(bm, edges=edges, cuts=1, use_grid_fill=True, smooth=1.0)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()

    near = {}
    for e in mesh.edges:
        a, b = e.vertices
        near.setdefault(a, []).append(b)
        near.setdefault(b, []).append(a)
    weights = {v.index: {g.group: g.weight for g in v.groups} for v in mesh.vertices if v.index < old}
    fresh = [i for i in range(old, len(mesh.vertices)) if i in near]
    # the points on a cut edge first (their two old neighbours are the edge's ends), then the ones inside a face
    for inner in (False, True):
        mixed = {}
        for i in fresh:
            around = [j for j in near[i] if j in weights and (inner or j < old)]
            if i in weights or len(around) < 2:
                continue
            total = {}
            for j in around:
                for g, x in weights[j].items():
                    total[g] = total.get(g, 0.0) + x / len(around)
            mixed[i] = total
        weights.update(mixed)

    for i in fresh:
        if i not in weights:
            continue
        best = sorted(weights[i].items(), key=lambda x: -x[1])[:4]
        total = sum(x for _, x in best)
        for g in [g.group for g in mesh.vertices[i].groups if g.group not in dict(best)]:
            body.vertex_groups[g].remove([i])

        for g, x in best:
            body.vertex_groups[g].add([i], x / total, 'REPLACE')


def soften_joint(body, centre, reach, times):
    """The bones' weights within [reach] of [centre], each point's averaged with its neighbours' [times] over (most
    at the middle, fading to nothing at the edge), so the skin bends over a longer stretch round the joint. A point
    keeps four bones at most."""
    mesh = body.data
    near = {}
    for e in mesh.edges:
        a, b = e.vertices
        near.setdefault(a, []).append(b)
        near.setdefault(b, []).append(a)
    # (only points that are part of the skin: its mesh holds a few loose ones, inside the body)
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
    """Evens the skin below the neck's foot, [times] over: each point goes half the way to the middle of its
    neighbours. All of it: and then as far back out again, so thin limbs do not shrink (this takes out the small
    folds the mesh's uneven faces leave). The [trunk] alone, from the hips up: without the way back, a real
    smoothing (the trunk is thick enough to lose nothing by it)."""
    mesh = skin.body.data
    near = [[] for _ in mesh.vertices]
    for e in mesh.edges:
        a, b = e.vertices
        near[a].append(b); near[b].append(a)
    low = skin.P["hips"].pos.z
    inside = []
    for v in mesh.vertices:
        share = skin.below_neck(v) * (1.0 - smooth(2.0 * skin.ends.get(v.index, 0.0)))
        if trunk:
            share *= (1.0 - skin.arms(v.index)) * smooth((v.co.z - low) / 0.06)

        # (a loose point, of which the skin's mesh holds a few inside the body, has no neighbours)
        if share > 0.0 and near[v.index]:
            inside.append((v.index, share))

    for _ in range(times):
        for step in ((0.5,) if trunk else (0.5, -0.53)):
            moved = [(i, mesh.vertices[i].co + (sum((mesh.vertices[j].co for j in near[i]), Vector()) / len(near[i])
                                                - mesh.vertices[i].co) * (step * share)) for i, share in inside]
            for i, co in moved:
                mesh.vertices[i].co = co


class Skin:
    """The body's skin while it is shaped: its points, and how much of each belongs to an arm or a leg (by the bones'
    weights), so that a muscle of the arm moves no skin of the trunk beside it, and the head and the neck stay."""
    def __init__(self, body, P, top):
        self.body, self.P, self.top = body, P, top
        groups = {g.index: g.name for g in body.vertex_groups}
        self.arm = {"L": {}, "R": {}}
        self.leg = {"L": {}, "R": {}}
        self.ends = {}                      # the hands and the feet: they stay as they are
        for v in body.data.vertices:
            for g in v.groups:
                name = groups[g.group]
                if name[:-1] in ("elbow", "wrist", "hand"):
                    self.arm[name[-1]][v.index] = self.arm[name[-1]].get(v.index, 0.0) + g.weight
                if name[:-1] in ("hand", "toe"):
                    self.ends[v.index] = self.ends.get(v.index, 0.0) + g.weight
                if name[:-1] in ("knee", "ankle", "toe"):
                    self.leg[name[-1]][v.index] = self.leg[name[-1]].get(v.index, 0.0) + g.weight

    def arms(self, i):
        return min(1.0, self.arm["L"].get(i, 0.0) + self.arm["R"].get(i, 0.0))

    def below_neck(self, v):
        return under_neck(v.co, self.top)


def smooth(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def shape_trunk(skin, profile):
    """The trunk's outline, set by height: see TRUNK. Widths go from the figure's middle, depths from the plane the
    hip and the shoulder joints stand in. The arms keep their shape (the skin under the arm goes with the trunk as
    far as it belongs to it)."""
    def factors(z):
        if z >= profile[0][0] or z <= profile[-1][0]:
            return None
        for hi, lo in zip(profile, profile[1:]):
            if lo[0] <= z <= hi[0]:
                t = smooth((hi[0] - z) / (hi[0] - lo[0]))
                return [hi[k] + (lo[k] - hi[k]) * t for k in (1, 2, 3, 4)]
        return None

    for v in skin.body.data.vertices:
        f = factors(v.co.z)
        if f is None:
            continue
        own = 1.0 - skin.arms(v.index)
        # the flank where the arm hangs, and the chest and the back before and behind it
        beside = smooth((abs(v.co.y) - 0.025) / 0.06)
        wide = f[0] + (f[1] - f[0]) * beside
        v.co.x *= 1.0 + (wide - 1.0) * own
        v.co.y *= 1.0 + ((f[2] if v.co.y < 0.0 else f[3]) - 1.0) * own


def shape_limb(skin, on, joints, thickness):
    """A limb over its two pieces, thicker or thinner about its own middle line, on both sides: see LIMBS. Only the
    limb's own skin moves (by the bones' weights), so it runs out softly into the trunk."""
    for side in ("L", "R"):
        a, b, c = (skin.P[j + side].pos for j in joints)
        own = (skin.arm if on == "arm" else skin.leg)[side]
        for v in skin.body.data.vertices:
            share = own.get(v.index, 0.0)
            if share <= 0.0:
                continue

            # where along the limb: on the first piece, or past its end on the second
            t = (v.co - a).dot(b - a) / (b - a).length_squared
            if t <= 1.0:
                t = max(0.0, t)
                middle = a + (b - a) * t
            else:
                u = min(1.0, max(0.0, (v.co - b).dot(c - b) / (c - b).length_squared))
                middle = b + (c - b) * u
                t = 1.0 + u

            f = thickness[-1][1]
            for (t0, f0), (t1, f1) in zip(thickness, thickness[1:]):
                if t0 <= t <= t1:
                    f = f0 + (f1 - f0) * smooth((t - t0) / (t1 - t0))
                    break

            v.co = middle + (v.co - middle) * (1.0 + (f - 1.0) * share * skin.below_neck(v))


def shape_muscle(skin, point, offset, direction, height, size, options):
    """One muscle on both sides, see MUSCLES."""
    on, half = options.get("on"), options.get("half")
    flat, under, square = options.get("flat", 1.0), options.get("under", 1.0), options.get("square", 2.0)
    for side, sx in (("L", 1.0), ("R", -1.0)):
        at = skin.P[point + side] if point + side in skin.P else skin.P[point]
        centre = at.pos + Vector((sx * offset[0], offset[1], offset[2]))
        rise = Vector((sx * direction[0], direction[1], direction[2])).normalized() * height
        for v in skin.body.data.vertices:
            d = v.co - centre
            dz = d.z / (size[2] * (under if d.z < 0 else 1.0))
            q = (abs(d.x / size[0]) ** square + abs(dz) ** square) ** (2.0 / square) + (d.y / size[1]) ** 2
            if q > 9.0:
                continue

            share = skin.below_neck(v)
            if half == "back":
                share *= smooth((v.co.y - at.pos.y) / 0.03 + 0.5)
            elif half == "front":
                share *= smooth((at.pos.y - v.co.y) / 0.03 + 0.5)

            if on == "arm":
                share *= skin.arm[side].get(v.index, 0.0)
            elif on == "body":
                share *= 1.0 - skin.arms(v.index)
            elif on == "leg":
                share *= (1.0 - skin.arms(v.index)) * smooth(sx * v.co.x / 0.012)

            if share <= 0.0:
                continue

            v.co += rise * (share * math.exp(-(q ** flat) / 2))
