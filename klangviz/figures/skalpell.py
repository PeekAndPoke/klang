# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

import bpy
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

from figure import CENTRE, Figure, PICK_HAND, DARK, activate, add_eyes, draw_chin, join_into, mane, material, skull

# His body (the maintainer, 2026-11-21: "a little more musculature", "athletic twins"; he is the older and the
# shorter of the two, a little more filled out than the bass): lean and defined like a swimmer, not a bodybuilder.
# All lengths in metres on the finished figure (he is 1.80 m to the top of his head), x toward the figure's own
# side (outward), z up; heights are heights above the floor.

# The trunk's outline seen from the front, by height: its width against what the skin made. A V from under the arms
# (the back's broad muscles) to a narrow waist, and narrow hips.
WIDTH = [(1.44, 1.0), (1.38, 1.08), (1.32, 1.18), (1.27, 1.16), (1.21, 1.11), (1.15, 1.09), (1.08, 1.06), (1.00, 0.99),
         (0.92, 0.95), (0.86, 0.93), (0.80, 0.96), (0.72, 1.0)]
# The same for its depth, before and behind the line through the trunk's middle: (height, front, back). The belly
# flat, the chest carried.
DEPTH = [(1.50, 1.0, 1.0), (1.42, 1.09, 1.04), (1.34, 1.11, 1.06), (1.26, 1.03, 1.05), (1.18, 0.95, 1.0), (1.10, 0.93, 1.0),
         (1.02, 0.95, 1.0), (0.92, 1.0, 1.0)]

# The chest muscle, a plate on each side of the breastbone with a lower edge that reads: how far it stands out, the
# height of its lower edge at its lowest, how much higher the edge is at the armpit and at the breastbone, over what
# height the edge rises (short: a step) and over what height the plate fades out upward toward the collarbone, and
# from where to where it reaches across.
PEC = dict(out=0.016, edge=1.330, up_outside=0.060, up_inside=0.012, step=0.034, full=0.030, fade=0.100,
           inside=(0.0, 0.050), outside=(0.120, 0.190))

# Lines drawn into the trunk as shallow grooves: (which side of the trunk, the line's points as (x, height), how
# deep, how wide (the fall-off to each side), both sides mirrored or only the middle)
GROOVES = [
    # the breastbone and the middle line of the belly, down to the navel: a hint
    ("front", [(0.0, 1.31), (0.0, 1.20), (0.0, 1.07)], 0.0030, 0.013, False),
    # between the chest's muscle and the shoulder's
    ("front", [(0.118, 1.495), (0.150, 1.440), (0.172, 1.385)], 0.0035, 0.014, True),
    # the outer edges of the belly's straight muscle
    ("front", [(0.068, 1.29), (0.062, 1.16), (0.048, 1.03)], 0.0024, 0.014, True),
    # the lower edge of the ribs
    ("front", [(0.030, 1.292), (0.065, 1.262), (0.105, 1.215)], 0.0018, 0.014, True),
    # the line from the hip bone down to the groin
    ("front", [(0.128, 1.010), (0.090, 0.935), (0.045, 0.880)], 0.0026, 0.014, True),
    # the spine, from the hips to between the shoulder blades
    ("back", [(0.0, 0.97), (0.0, 1.20), (0.0, 1.44)], 0.0060, 0.016, False),
]

# Soft rises on the trunk: (which side, (x, height), the direction of the rise as (outward, backward, up), how
# high, how far across, how far up and down)
MOUNDS = [
    # the back's broad muscle, under the arm: with WIDTH it makes the V of the back
    ("back", (0.105, 1.300), (0.45, 0.90, 0.0), 0.010, 0.050, 0.075),
    # the shoulder blade
    ("back", (0.085, 1.420), (0.0, 1.0, 0.0), 0.006, 0.045, 0.060),
    # the long muscles beside the spine, in the small of the back
    ("back", (0.034, 1.100), (0.0, 1.0, 0.0), 0.004, 0.020, 0.090),
    # the buttock: round and high
    ("back", (0.092, 0.890), (0.0, 1.0, -0.15), 0.026, 0.058, 0.066),
    # the small of the back, drawn in (a hollow: a rise with a minus)
    ("back", (0.0, 1.090), (0.0, 1.0, 0.0), -0.011, 0.100, 0.070),
    # the hollow in the buttock's side that a trained seat has
    ("back", (0.150, 0.880), (1.0, 0.3, 0.0), -0.004, 0.030, 0.045),
]

# Rises with a place in space, for where the trunk has neither a front nor a back: (the middle as (x, how far behind
# the trunk's middle line, height), the direction of the rise as (outward, backward, up), how high, its size across,
# front to back and in height)
LUMPS = [
    # the muscle from the neck's foot out to the shoulder: the shoulders' line slopes a little, as a trained one does
    ((0.078, 0.012, 1.512), (0.0, 0.25, 1.0), 0.013, (0.045, 0.050, 0.030)),
]

# The limbs' muscles: (the limb as its two joints, where along it (0 at the first joint, 1 at the second), the side
# of the limb it lies on as (outward, forward), how high, how long toward the first joint and toward the second
# (its fall-off each way), how wide across)
LIMBS = [
    # the shoulder's cap: over the joint, running down to a point on the arm's outside
    ("shoulder", "elbow", 0.14, (1.0, 0.0), 0.030, (0.055, 0.060), 0.046),
    # under it the arm's outside is drawn in a little, so the cap ends
    ("shoulder", "elbow", 0.62, (1.0, 0.0), -0.002, (0.050, 0.060), 0.030),
    # the cap's front and its back, a little
    ("shoulder", "elbow", 0.06, (0.55, 0.85), 0.007, (0.040, 0.060), 0.035),
    ("shoulder", "elbow", 0.08, (0.55, -0.85), 0.007, (0.040, 0.060), 0.035),
    # the upper arm: its front (biceps) and, higher up, its back (triceps)
    ("shoulder", "elbow", 0.52, (0.0, 1.0), 0.015, (0.060, 0.055), 0.026),
    ("shoulder", "elbow", 0.42, (0.15, -1.0), 0.015, (0.070, 0.065), 0.028),
    # the forearm: thick below the elbow on the outside and the inside, slim to the wrist
    ("elbow", "wrist", 0.22, (0.9, 0.45), 0.007, (0.040, 0.075), 0.024),
    ("elbow", "wrist", 0.25, (-0.9, -0.45), 0.006, (0.040, 0.070), 0.022),
    # the thigh: its front, its outside, the teardrop inside above the knee, and its back
    ("hip", "knee", 0.42, (0.10, 1.0), 0.014, (0.110, 0.110), 0.036),
    ("hip", "knee", 0.58, (1.0, 0.25), 0.010, (0.090, 0.100), 0.036),
    ("hip", "knee", 0.86, (-0.80, 0.60), 0.007, (0.050, 0.035), 0.022),
    ("hip", "knee", 0.50, (0.0, -1.0), 0.010, (0.100, 0.110), 0.036),
    # the knee: a hint of its cap
    ("hip", "knee", 1.0, (0.0, 1.0), 0.003, (0.025, 0.025), 0.020),
    # the calf: high under the knee, its inner head a little lower and fuller, a short fall to the heel's tendon
    ("knee", "ankle", 0.27, (0.45, -1.0), 0.016, (0.060, 0.070), 0.026),
    ("knee", "ankle", 0.31, (-0.50, -1.0), 0.018, (0.065, 0.070), 0.026),
    # the shin's own muscle, outside the bone
    ("knee", "ankle", 0.30, (0.9, 0.5), 0.004, (0.060, 0.090), 0.020),
]


# He stands upright: of the stoop he had (0.07) only a trace is left.
STOOP = 0.02


def lift(z):
    """How much of the stoop a point at the shared skeleton's height [z] takes (as figure.points() has it)."""
    return (max(0.0, z - 0.98) / 0.66) ** 2


# The stoop he had, and the shared skeleton's heights of the neck's end and of the head's points
WAS = 0.07
NECK = next(c[2][2] for c in CENTRE if c[0] == "neck")
HEAD = {name: next(c[2][2] for c in CENTRE if c[0] == name) for name in ("head", "crown", "chin")}


def carried(z):
    """How far a point of the head (at the shared skeleton's height [z]) is set forward, so that the head sits on the
    neck's end exactly as it did under the old stoop: the trunk straightens, the head and its carriage stay his."""
    return -(WAS - STOOP) * (lift(z) - lift(NECK))


class Skalpell(Figure):
    """The lead guitar: an athlete, slim and upright, long hair worn open."""
    key = "skalpell"
    frame = dict(
        height=1.02, scale=dict(torso=0.90, waist=0.88, chest=0.95, arm=0.88, thigh=0.86, leg=0.88),
        # narrower hips than the shared skeleton gives, fuller arms
        radius=dict(hip=0.90, hips=0.96, shoulder=1.06, bicep=1.08, elbow=0.97, forearm=1.04),
        stoop=STOOP, eye=(1.0, 0.32, 0.24),
        move=dict(PICK_HAND, **{name: (0.0, carried(z), 0.0) for name, z in HEAD.items()}),
    )

    def sculpt(self, w):
        # his chin first, on the shared skin, as it always was drawn: the head stays his
        draw_chin(w.body, w.head, w.hs, 1.0)
        # (and where his eyes sit is read from this skin, see eyes())
        self.face = [v.co.copy() for v in w.body.data.vertices if (v.co - w.head.pos).length < 2.2 * w.head.radius]
        # then a finer skin than the shared one: the muscles' edges need it
        clay = refine(w.body, w.P)
        shape_trunk(clay, w.P)
        shape_chest(clay, w.P)
        shape_limbs(clay, w.P)
        # the breastbone, evened out: the skin's mesh has a knot there that showed as a nick
        clay.even((0.0, w.P["chest"].pos.y - 0.09, 1.350), 0.065, 6)
        clay.write()

    def back_of_head(self, w):
        # a plain round back of the head: the hair alone left it flat
        return skull(w.head.pos, w.hs, w.body.data.materials[0], long=1.0, high=0.094, back=0.034, rise=0.022, tilt=8.0,
                     wide=0.086)

    def eyes(self, w, p, back):
        # as the shared eyes(), but set by the skin as it was before it was made finer: his eyes stay where they were
        surface = self.face + [v.co.copy() for v in back.data.vertices]
        add_eyes(w.body, p, w.head, w.hs, surface, [w.body, back])

    def hair(self, w):
        # long hair: combed straight back over the skull as a pattern in the light (the show's Pattern.kt), and from
        # there one smooth mane down his back, see mane(). (Hanging locks were tried and not wanted: hair() in the
        # history, see 0eb7378ad^.)
        hair = material(self.key + "_hair", DARK)
        w.body.data.materials.append(hair)
        locks = mane(w.head.pos, w.hs, hair, length=0.46)
        # the mane as it was, and lying on his back as it did: his head is carried further back over the trunk now,
        # so below the neck each height of the mane goes with the back at that height, not with the head
        neck = w.P["neck"].pos.z
        for v in locks.data.vertices:
            if v.co.z < neck:
                v.co.y -= (WAS - STOOP) * (lift(NECK) - lift(v.co.z / w.height)) * w.height
        join_into(w.body, locks)


def refine(body, P):
    """
    The skin once more subdivided (four times the faces); the bones' weights go with it. Subdividing rounds a skin
    off a little, and his head, hands and feet stay as they are: there (from the shoulders' top up, on the hands and
    on the feet) the finer skin is laid back onto the skin as it was. Returns the skin as Clay.
    """
    mesh = body.data
    was = BVHTree.FromPolygons([v.co.copy() for v in mesh.vertices], [tuple(f.vertices) for f in mesh.polygons])
    activate(body)
    body.modifiers.new("Fine", "SUBSURF").levels = 1
    bpy.ops.object.modifier_apply(modifier="Fine")
    bpy.ops.object.shade_smooth()
    clay = Clay(body)
    V = clay.V
    top = P["neck"].pos.z - 0.020
    keep = smooth((V[:, 2] - (top - 0.040)) / 0.040)
    for name in ("handL", "handR", "toeL", "toeR"):
        keep = np.maximum(keep, smooth((clay.G[name] - 0.2) / 0.6))
    for i in np.nonzero(keep > 0)[0]:
        place = was.find_nearest(Vector(V[i]))[0]
        V[i] += (np.array(place) - V[i]) * keep[i]
    return clay


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


class Clay:
    """The body's skin as numbers, to shape it: its points ([V], a row each) and each bone's share of each point."""
    def __init__(self, body):
        self.body = body
        n = len(body.data.vertices)
        flat = np.empty(n * 3)
        body.data.vertices.foreach_get("co", flat)
        self.V = flat.reshape(n, 3)
        names = [g.name for g in body.vertex_groups]
        self.G = {name: np.zeros(n) for name in names}
        for v in body.data.vertices:
            for g in v.groups:
                self.G[names[g.group]][v.index] = g.weight
        arms = sum(self.G[b + s] for b in ("elbow", "wrist", "hand") for s in "LR")
        # how much of a point is trunk and legs (1) and how much arm (0): the arms hang close beside the trunk and the
        # thighs, and what shapes one must not pull at the other
        self.trunk = 1.0 - np.clip(arms, 0.0, 1.0)

        edges = np.empty(len(body.data.edges) * 2, dtype=np.int32)
        body.data.edges.foreach_get("vertices", edges)
        self.edges = edges.reshape(-1, 2)

    def even(self, centre, radius, times):
        """Evens the skin within [radius] of [centre], [times] over: each point goes halfway to the middle of its
        neighbours, most at the centre, fading to nothing at the edge."""
        V, (a, b) = self.V, self.edges.T
        much = 0.5 * np.clip(2.0 * (1.0 - np.linalg.norm(V - np.array(centre), axis=1) / radius), 0.0, 1.0)
        for _ in range(times):
            total = np.zeros_like(V)
            count = np.zeros(len(V))
            np.add.at(total, a, V[b]); np.add.at(total, b, V[a])
            np.add.at(count, a, 1.0); np.add.at(count, b, 1.0)
            V += (total / count[:, None] - V) * much[:, None]

    def write(self):
        self.body.data.vertices.foreach_set("co", self.V.ravel())
        self.body.data.update()


def by_height(table, z, column=1):
    """The value of [table] (rows from the top down, the height first) at each height of [z]: one smooth curve
    through the rows, 1 outside the table."""
    h = np.array([row[0] for row in table][::-1])
    f = np.array([row[column] for row in table][::-1])
    # the curve's slope at each row: that of the line through its two neighbours (level at the two ends)
    slope = np.zeros(len(h))
    slope[1:-1] = (f[2:] - f[:-2]) / (h[2:] - h[:-2])
    i = np.clip(np.searchsorted(h, z) - 1, 0, len(h) - 2)
    step = h[i + 1] - h[i]
    t = np.clip((z - h[i]) / step, 0.0, 1.0)
    out = (2 * t ** 3 - 3 * t ** 2 + 1) * f[i] + (t ** 3 - 2 * t ** 2 + t) * step * slope[i] \
        + (-2 * t ** 3 + 3 * t ** 2) * f[i + 1] + (t ** 3 - t ** 2) * step * slope[i + 1]
    return np.where((z > h[0]) & (z < h[-1]), out, 1.0)


def middle_line(P, z):
    """Where the trunk's middle is (y) at each height of [z]: the line through the skeleton's points."""
    points = [P[n].pos for n in ("hips", "belly", "ribs", "chest", "neck")]
    return np.interp(z, [p.z for p in points], [p.y for p in points])


def shape_trunk(clay, P):
    """The trunk's outline and depth by height (WIDTH, DEPTH), its soft rises (MOUNDS) and its lines (GROOVES)."""
    V, trunk = clay.V, clay.trunk
    V[:, 0] *= 1.0 + (by_height(WIDTH, V[:, 2]) - 1.0) * trunk
    mid = middle_line(P, V[:, 2])
    deep = np.where(V[:, 1] < mid, by_height(DEPTH, V[:, 2], 1), by_height(DEPTH, V[:, 2], 2))
    V[:, 1] = mid + (V[:, 1] - mid) * (1.0 + (deep - 1.0) * trunk)

    for face, (x, z), toward, height, across, along in MOUNDS:
        for side in ((1.0, -1.0) if x > 0 else (1.0,)):
            mid = middle_line(P, V[:, 2])
            on = facing(V, mid, face) * trunk
            q = ((V[:, 0] - side * x) / across) ** 2 + ((V[:, 2] - z) / along) ** 2
            way = np.array([side * toward[0], toward[1], toward[2]])
            way /= np.linalg.norm(way)
            V += way * (height * np.exp(-q / 2) * on)[:, None]

    below_neck = 1.0 - smooth((V[:, 2] - (P["neck"].pos.z - 0.060)) / 0.040)
    for (x, behind, z), toward, height, size in LUMPS:
        for side in (1.0, -1.0):
            mid = middle_line(P, V[:, 2])
            q = ((V[:, 0] - side * x) / size[0]) ** 2 + ((V[:, 1] - mid - behind) / size[1]) ** 2 \
                + ((V[:, 2] - z) / size[2]) ** 2
            way = np.array([side * toward[0], toward[1], toward[2]])
            way /= np.linalg.norm(way)
            V += way * (height * np.exp(-q / 2) * trunk * below_neck)[:, None]

    for face, line, depth, wide, both in GROOVES:
        for side in ((1.0, -1.0) if both else (1.0,)):
            mid = middle_line(P, V[:, 2])
            on = facing(V, mid, face) * trunk
            d, t = to_line(V[:, 0] * side, V[:, 2], line)
            # the line fades in and out at its ends
            ends = smooth(t / 0.15) * smooth((1.0 - t) / 0.15)
            V[:, 1] += (1.0 if face == "front" else -1.0) * depth * np.exp(-(d / wide) ** 2 / 2) * ends * on


def facing(V, mid, face):
    """1 on the trunk's front (or back), 0 on the other side, smooth round its sides."""
    ahead = (mid - V[:, 1]) if face == "front" else (V[:, 1] - mid)
    return smooth((ahead + 0.010) / 0.035)


def to_line(x, z, line):
    """How far each point (x, z) is from the line through [line]'s points, and how far along it (0 to 1) the nearest
    place is."""
    lengths = [math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(line, line[1:])]
    total, done = sum(lengths), 0.0
    best = np.full(len(x), 1e9)
    where = np.zeros(len(x))
    for (a, b), length in zip(zip(line, line[1:]), lengths):
        ux, uz = (b[0] - a[0]) / length, (b[1] - a[1]) / length
        t = np.clip((x - a[0]) * ux + (z - a[1]) * uz, 0.0, length)
        d = np.hypot(x - (a[0] + ux * t), z - (a[1] + uz * t))
        closer = d < best
        best[closer] = d[closer]
        where[closer] = (done + t[closer]) / total
        done += length
    return best, where


def shape_chest(clay, P):
    """The chest's muscle (PEC): a plate that stands out of the chest's front, with a lower edge."""
    V, c = clay.V, PEC
    mid = middle_line(P, V[:, 2])
    on = facing(V, mid, "front") * clay.trunk
    ax = np.abs(V[:, 0])
    edge = c["edge"] + c["up_outside"] * smooth((ax - 0.085) / 0.10) + c["up_inside"] * smooth((0.045 - ax) / 0.045)
    up = V[:, 2] - edge
    # (toward the breastbone the edge softens: the two plates run out there, they do not end in a corner)
    step = c["step"] * (1.0 + 1.5 * smooth((0.045 - ax) / 0.045))
    high = smooth((up + 0.3 * step) / step) * (1.0 - smooth((up - c["full"]) / c["fade"]))
    across = smooth((ax - c["inside"][0]) / (c["inside"][1] - c["inside"][0])) \
        * (1.0 - smooth((ax - c["outside"][0]) / (c["outside"][1] - c["outside"][0])))
    V[:, 1] -= c["out"] * high * across * on


def shape_limbs(clay, P):
    """The arms' and the legs' muscles (LIMBS), each a soft rise on its side of the limb."""
    V = clay.V
    for first, second, at, toward, height, long, wide in LIMBS:
        arm = first in ("shoulder", "elbow")
        for name, side in (("L", 1.0), ("R", -1.0)):
            a, b = np.array(P[first + name].pos), np.array(P[second + name].pos)
            length = np.linalg.norm(b - a)
            u = (b - a) / length
            way = np.array([side * toward[0], -toward[1], 0.0])
            way -= u * way.dot(u)
            way /= np.linalg.norm(way)
            rel = V - a
            along = rel @ u
            radial = rel - np.outer(along, u)
            far = np.linalg.norm(radial, axis=1)
            # only this limb: an arm's muscle moves the arm and the shoulder it grows from, not the chest beside it
            if arm:
                own = smooth((V[:, 0] * side - 0.135) / 0.05) if first == "shoulder" else 1.0 - clay.trunk
                own = np.maximum(own, 1.0 - clay.trunk) * (V[:, 0] * side > 0)
            else:
                own = clay.trunk * smooth(V[:, 0] * side / 0.012)
            ring = (np.abs(along - at * length) < 0.02) & (own > 0.5) & (far < 0.12)
            thick = far[ring].mean()
            d = V - (a + u * (at * length) + way * thick)
            du = d @ u
            across = np.linalg.norm(d - np.outer(du, u) - np.outer(d @ way, way), axis=1)
            q = (du / np.where(du < 0, long[0], long[1])) ** 2 + (across / wide) ** 2
            turned = smooth(((radial @ way) / np.maximum(far, 1e-6) + 0.25) / 0.75)
            V += way * (height * np.exp(-q / 2) * turned * own)[:, None]
