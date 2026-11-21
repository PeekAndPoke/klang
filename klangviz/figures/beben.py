# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

import bmesh

from mathutils import Vector

from figure import Figure, PICK_HAND, draw_chin, relax, smooth, soften_joint

# How upright he stands: the head's way ahead of the hips (the frame's "stoop"). It was 0.11, a stooped figure; an
# athlete stands upright, and a trace of it is left as his character (the maintainer, 2026-11-21).
STOOP = 0.03
# His head as it was: the stoop also leans the head (its top goes further ahead than its middle, its underside less
# far). These are the shares of the stoop the head's points took at 0.11, so the head keeps its lean at any stoop.
HEAD_LEAN = dict(crown=-0.2380, chin=0.1764)

# Where the neck's foot is: this far above the shoulder joints, in metres. Nothing above it is touched.
NECK_FOOT = 0.035

# The trunk's outline by height (metres, from the top down): how many times as wide, as deep in front of the spine
# and as deep behind it as the skin made it. The V of a swimmer: wide under the arms (the back's big muscles), in a
# straight line down to a narrow waist, the belly flat.
TRUNK = [
    (1.570, 1.00, 1.00, 1.00),
    (1.530, 1.075, 1.08, 1.02),
    (1.490, 1.22, 1.08, 1.05),
    (1.450, 1.26, 1.08, 1.06),
    (1.410, 1.27, 1.06, 1.05),
    (1.370, 1.26, 1.00, 1.03),
    (1.330, 1.21, 0.96, 1.00),
    (1.290, 1.14, 0.93, 1.00),
    (1.250, 1.065, 0.91, 1.00),
    (1.210, 0.99, 0.91, 1.00),
    (1.150, 0.97, 0.94, 1.00),
    (1.070, 1.00, 1.00, 1.00),
]

# The limbs' thickness along their length, over two pieces (0 at the first joint, 1 at the second, 2 at the third):
# how many times as thick as the skin made them. (The frame's own thicknesses are left alone: the skin's mesh folds
# its shoulders into a shelf when they change.) The wrists, the knees and the ankles stay slim: the muscle lies
# between the joints.
LIMBS = [
    ("arm", ("shoulder", "elbow", "wrist"), [(0.0, 1.10), (0.45, 1.12), (1.0, 1.05), (1.25, 1.14), (1.7, 1.04), (2.0, 1.0)]),
    ("leg", ("hip", "knee", "ankle"), [(0.0, 1.08), (0.4, 1.12), (0.85, 1.02), (1.0, 1.0), (1.3, 1.10), (1.7, 1.0), (2.0, 1.0)]),
]

# Where the bones' weights are evened out once more, round a joint: (joint, how far round it in metres, how often).
# The skin's own weights change over a few centimetres there, and a thigh that swings forward or an arm that reaches
# ahead folds the skin along that line.
SOFT = [("hip", 0.16, 10), ("shoulder", 0.13, 6)]

# The muscles, each a soft rise drawn out of the skin, on his left side (the right is its mirror):
#   name: (point of the figure, the rise's middle from that point, the way it rises, how high, its size, options)
# all in metres; x is toward his own side (outward), y back (negative: forward), z up. Options: `on`: whose skin
# moves ("arm": that side's arm alone, "body": everything but the arms, "leg": that side's leg alone), `half`: only
# the skin in front of the point or behind it, `flat`: above 1 the rise has a flatter top and a firmer edge, `under`:
# how many times as far the rise reaches below its middle as above it (a chest's muscle ends in an edge below),
# `square`: above 2 the rise's outline seen from the front is squarer, a plate and no mound.
MUSCLES = {
    # the shoulder's cap, round the joint's outer side and down the arm to where it ends in the upper arm
    "deltoid":  ("shoulder", (0.030, 0.000, -0.055), (1.0, 0.0, -0.10), 0.022, (0.034, 0.050, 0.048), dict(flat=1.5)),
    # the upper arm: its front and its back
    "bicep":    ("bicep", (-0.004, -0.028, 0.000), (0.0, -1.0, 0.0), 0.017, (0.024, 0.030, 0.046), dict(on="arm")),
    "tricep":   ("bicep", (0.008, 0.028, 0.035), (0.35, 1.0, 0.0), 0.013, (0.026, 0.030, 0.050), dict(on="arm")),
    # the forearm: thick under the elbow, on its outer and its inner side, running out toward the wrist
    "forearm":  ("forearm", (0.012, -0.004, 0.035), (1.0, -0.3, 0.0), 0.010, (0.020, 0.030, 0.045), dict(on="arm")),
    "flexor":   ("forearm", (-0.012, 0.002, 0.030), (-1.0, 0.2, 0.0), 0.008, (0.020, 0.030, 0.045), dict(on="arm")),
    # the chest: a plate from the breastbone to the arm, with an edge below
    "pec":      ("chest", (0.086, -0.080, -0.060), (0.0, -1.0, 0.0), 0.011, (0.070, 0.080, 0.062),
                 dict(on="body", half="front", flat=2.2, under=0.50, square=3.5)),
    # the belly: a long low rise on each side of its middle line, a hint of its lines and no more
    "belly":    ("belly", (0.032, -0.080, 0.080), (0.0, -1.0, 0.0), 0.008, (0.022, 0.050, 0.100),
                 dict(on="body", half="front", flat=1.2)),
    # the back: the big muscle under the arm, the shoulder blade, and the long muscles beside the spine
    "lat":      ("ribs", (0.125, 0.030, 0.050), (0.6, 0.8, 0.0), 0.008, (0.045, 0.050, 0.090), dict(on="body", half="back")),
    "blade":    ("chest", (0.078, 0.070, -0.020), (0.0, 1.0, 0.0), 0.015, (0.045, 0.040, 0.050), dict(on="body", half="back", flat=1.5)),
    "erector":  ("belly", (0.030, 0.060, 0.030), (0.0, 1.0, 0.0), 0.007, (0.022, 0.040, 0.120), dict(on="body", half="back", flat=1.2)),
    # the seat, and the thigh: its front, its outer side, the drop over the knee's inner side, its back
    "glute":    ("hip", (0.005, 0.055, -0.020), (0.0, 1.0, -0.15), 0.022, (0.050, 0.055, 0.058), dict(on="body", half="back", flat=1.3)),
    "quad":     ("thigh", (0.005, -0.050, 0.050), (0.0, -1.0, 0.0), 0.014, (0.032, 0.040, 0.100), dict(on="leg", half="front")),
    "vastus":   ("thigh", (0.045, -0.020, 0.000), (1.0, -0.4, 0.0), 0.013, (0.030, 0.045, 0.090), dict(on="leg")),
    "teardrop": ("knee", (-0.022, -0.028, 0.065), (-0.8, -1.0, 0.0), 0.011, (0.022, 0.025, 0.036), dict(on="leg")),
    "hamstring": ("thigh", (0.000, 0.050, 0.020), (0.0, 1.0, 0.0), 0.010, (0.040, 0.040, 0.100), dict(on="leg", half="back")),
    # the calf: high on the shin's back, a little of it to both sides
    "calf":     ("calf", (0.000, 0.035, 0.035), (0.0, 1.0, 0.0), 0.016, (0.032, 0.035, 0.060), dict(on="leg", half="back")),
    "calf_out": ("calf", (0.030, 0.010, 0.035), (1.0, 0.3, 0.0), 0.007, (0.025, 0.035, 0.050), dict(on="leg")),
    "calf_in":  ("calf", (-0.030, 0.010, 0.015), (-1.0, 0.3, 0.0), 0.012, (0.025, 0.035, 0.050), dict(on="leg")),
}


class Beben(Figure):
    """The bass: the younger twin, the tall one. An athlete's build, lean and long, on a long neck."""
    key = "beben"
    frame = dict(
        height=1.10, scale=dict(torso=0.80, waist=0.78, chest=0.82, shoulder=0.85, arm=0.78, thigh=0.76, leg=0.80,
                                neck=0.85, head=0.94),
        shoulders=0.92, hips=0.88, neck=1.35, stoop=STOOP, eye=(0.15, 0.95, 1.0), eye_size=0.9,
        eye_drop=0.016,                      # his eyes sit lower in the face
        move=dict(PICK_HAND, crown=(0, HEAD_LEAN["crown"] * (0.11 - STOOP), 0), chin=(0, HEAD_LEAN["chin"] * (0.11 - STOOP), 0)),
    )

    def sculpt(self, w):
        # the chin first, on the head as the skin made it
        draw_chin(w.body, w.head, w.hs, 1.0)
        # everything below works under the neck's foot only: his neck and his head stay as they were
        top = w.P["shoulderL"].pos.z + NECK_FOOT
        refine(w.body, top, longest=0.022)
        # softer joints where the trunk's mass now meets a limb: the hips and the shoulders
        for joint, reach, times in SOFT:
            for side in ("L", "R"):
                soften_joint(w.body, w.P[joint + side].pos, reach, times)

        skin = Skin(w.body, w.P, top)
        # the trunk's skin evened out first (the skin's mesh leaves folds on it, one behind the left shoulder)
        relax(skin, 4, trunk=True)
        shape_trunk(skin, TRUNK)
        for limb in LIMBS:
            shape_limb(skin, *limb)

        for name in MUSCLES:
            shape_muscle(skin, *MUSCLES[name])

        shape_back(skin, w.height)
        # and the small folds the mesh's uneven faces leave under the muscles' edges
        relax(skin, 1)


def refine(body, top, longest):
    """A finer skin below the height [top]: every edge longer than [longest] is cut in two (the skin's own faces are
    up to 5 cm across on the trunk, too coarse to carry a muscle), the new points set on the round between the old
    ones. The old points stay where they are and the head keeps its mesh. A new point takes its bones from its edge's
    two ends; where that makes more than four, the smallest go (a point is moved by four bones at most)."""
    mesh = body.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    edges = [e for e in bm.edges if e.calc_length() > longest and all(v.co.z < top for v in e.verts)]
    bmesh.ops.subdivide_edges(bm, edges=edges, cuts=1, use_grid_fill=True, smooth=1.0)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()
    for v in mesh.vertices:
        if len(v.groups) > 4:
            kept = sorted(((g.group, g.weight) for g in v.groups), key=lambda x: -x[1])
            total = sum(x[1] for x in kept[:4])
            for g, x in kept[4:]:
                body.vertex_groups[g].remove([v.index])

            for g, x in kept[:4]:
                body.vertex_groups[g].add([v.index], x / total, 'REPLACE')


class Skin:
    """The body's skin while it is shaped: its points, and how much of each belongs to an arm or a leg (by the bones'
    weights), so that a muscle of the arm moves no skin of the trunk beside it, and the head and the neck stay."""
    def __init__(self, body, P, top):
        self.body, self.P, self.top = body, P, top
        groups = {g.index: g.name for g in body.vertex_groups}
        self.arm = {"L": {}, "R": {}}
        self.leg = {"L": {}, "R": {}}
        for v in body.data.vertices:
            for g in v.groups:
                name = groups[g.group]
                if name[:-1] in ("elbow", "wrist", "hand"):
                    self.arm[name[-1]][v.index] = self.arm[name[-1]].get(v.index, 0.0) + g.weight
                if name[:-1] in ("knee", "ankle", "toe"):
                    self.leg[name[-1]][v.index] = self.leg[name[-1]].get(v.index, 0.0) + g.weight

    def arms(self, i):
        return min(1.0, self.arm["L"].get(i, 0.0) + self.arm["R"].get(i, 0.0))

    def below_neck(self, v):
        # nothing above the neck's foot moves: the skin fades out of reach over the last 3 cm under it
        return smooth((self.top - v.co.z) / 0.03)


def shape_trunk(skin, profile):
    """The trunk's outline, set by height: see TRUNK. The arms keep their shape (the skin under the arm goes with the
    trunk as far as it belongs to it)."""
    P = skin.P
    spine = [P[n].pos for n in ("hips", "belly", "ribs", "chest")]

    def middle(z):
        # the spine's place front to back at a height
        if z <= spine[0].z:
            return spine[0].y
        for a, b in zip(spine, spine[1:]):
            if z <= b.z:
                return a.y + (b.y - a.y) * (z - a.z) / (b.z - a.z)
        return spine[-1].y

    def factors(z):
        if z >= profile[0][0] or z <= profile[-1][0]:
            return None
        for hi, lo in zip(profile, profile[1:]):
            if lo[0] <= z <= hi[0]:
                t = smooth((hi[0] - z) / (hi[0] - lo[0]))
                return [hi[k] + (lo[k] - hi[k]) * t for k in (1, 2, 3)]
        return None

    for v in skin.body.data.vertices:
        f = factors(v.co.z)
        if f is None:
            continue
        own = 1.0 - skin.arms(v.index)
        y0 = middle(v.co.z)
        v.co.x *= 1.0 + (f[0] - 1.0) * own
        v.co.y = y0 + (v.co.y - y0) * (1.0 + ((f[1] if v.co.y < y0 else f[2]) - 1.0) * own)


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


def shape_back(skin, H):
    """The small of the back, drawn in, and a shallow groove down the spine between the back's long muscles."""
    P = skin.P
    hips, belly, chest = P["hips"], P["belly"], P["chest"]
    for v in skin.body.data.vertices:
        if abs(v.co.x) > 0.25 * H or not hips.pos.z - 0.05 * H < v.co.z < chest.pos.z + 0.08 * H:
            continue

        t = (v.co.z - hips.pos.z) / (chest.pos.z - hips.pos.z)
        if v.co.y <= hips.pos.y + (chest.pos.y - hips.pos.y) * min(1.0, max(0.0, t)):
            continue

        hollow = math.exp(-((v.co.z - belly.pos.z) / (0.07 * H)) ** 2 / 2 - (v.co.x / (0.10 * H)) ** 2 / 2)
        low = min(1.0, max(0.0, (v.co.z - hips.pos.z) / (0.06 * H)))
        high = min(1.0, max(0.0, (chest.pos.z + 0.06 * H - v.co.z) / (0.06 * H)))
        groove = low * high * math.exp(-(v.co.x / (0.018 * H)) ** 2 / 2)
        v.co.y -= (0.012 * hollow + 0.007 * groove) * H * (1.0 - skin.arms(v.index))
