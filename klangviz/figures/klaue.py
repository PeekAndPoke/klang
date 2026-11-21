# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

import bmesh

from mathutils import Vector

from figure import Figure, PICK_HAND, flatten_face, mend_weights, relax, shape_face, shape_muscles, smooth, soften_joint

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
