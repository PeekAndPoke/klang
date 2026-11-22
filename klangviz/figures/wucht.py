# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

import bmesh
import numpy as np
from mathutils import Vector
from mathutils.bvhtree import BVHTree

from figure import Figure, draw_chin

# His body (the maintainer, 2026-11-21: "i like today's shape better. Can we keep it roughly but accentuate the
# musculature", "maybe half way between today's and the candidate body shape", and of his head: "I really do not
# like how his head sits on his shoulders. From the front this looks strange, like a falcon or some bird of prey"):
# the heavy one: very wide shoulders, a deep chest over a small waist, heavy arms, and a strong man's muscles on that
# shape, in the twins' language (skalpell.py, beben.py) scaled up to a heavyweight. All lengths in metres on the
# finished figure (he is 1.80 m to the top of his head), x toward the figure's own side (outward), y back (negative:
# forward), z up; heights are heights above the floor.

# How much of a solid lifter's build he has: 0 = the proportions he had (a wasp's waist under the widest shoulders,
# balloon upper arms), 1 = the solid block of the first candidate (a waist nearly as wide as the hips, slimmer upper
# arms, heavier legs). The muscles and the head on the shoulders are the same at every value.
BUILD = 0.5

# How much of the hood his head and neck keep (the maintainer, 2026-11-21, of a head on a clear neck: "now the neck
# is too thin", of a bull's neck: "still too narrow neck, it should almost not be there, almost"): 0 = a head on a
# short neck that shows as a neck, narrower than the jaw; 0.5 = a bull's neck, as wide as the head and widening down
# into the shoulders; 1 = the hood the skin makes, without its beak: the muscle from the shoulders runs up the head's
# sides to the eyes, the outline flares out from the dome down, as wide as the hood was or a little wider, and the
# chin sits nearly on the chest, its edge a slim shadow line.
HOOD = 0.9

# The skin is cut finer where it must carry form: no edge longer than FINE on the chest, the shoulders and the upper
# back (from FINE_FROM up to the eyes), than FINE_LOW below (the skin's own edges are up to 12 cm long), and finer
# still where the head meets the shoulders.
FINE = 0.030
FINE_FROM = 1.30
FINE_LOW = 0.045
FINE_NECK = 0.016
# (and under the jaw: there the hood's lip is spread out over the jaw's underside and the throat, and stretched)
FINE_JAW = 0.011
# Nothing above this height is touched: the dome of his head and his eyes' place stay as the skin made them.
FACE_TOP = 1.738
# (every step fades out of reach over this height under it)
FADE = 0.035

# ---- the build (see BUILD) ----

# The trunk's outline by height (from the top down): how many times as wide, as deep in front of the spine and as
# deep behind it as the skin made it, at BUILD 1.
TRUNK = [
    (1.575, 1.00, 1.00, 1.00), (1.520, 1.00, 1.28, 1.16), (1.460, 1.02, 1.32, 1.21), (1.400, 1.07, 1.31, 1.21),
    (1.340, 1.16, 1.32, 1.18), (1.280, 1.23, 1.33, 1.19), (1.220, 1.29, 1.40, 1.20), (1.160, 1.30, 1.44, 1.20),
    (1.100, 1.18, 1.36, 1.12), (1.040, 1.09, 1.18, 1.02), (0.980, 1.05, 1.05, 1.00), (0.900, 1.03, 1.00, 1.00),
    (0.820, 1.00, 1.00, 1.00),
]
# The depth is the trunk's middle's: full to this far from the middle line, fading to nothing over the next stretch,
# so the trunk becomes round and not a deeper slab, and the shoulders keep their depth.
DEEP = (0.14, 0.12)
# The limbs' thickness along their two pieces (0 at the first joint, 1 at the second, 2 at the third): how many
# times as thick as the skin made them, across and front to back, at BUILD 1.
THICK = [
    ("arm", ("shoulder", "elbow", "wrist"),
     [(0.0, 0.90, 0.83), (0.5, 0.90, 0.83), (0.9, 0.93, 0.90), (1.0, 0.98, 0.98), (1.3, 1.12, 1.12), (1.75, 1.04, 1.04),
      (2.0, 1.0, 1.0)]),
    ("leg", ("hip", "knee", "ankle"),
     [(0.0, 1.05, 1.00), (0.4, 1.16, 1.13), (0.85, 1.06, 1.06), (1.0, 1.02, 1.02), (1.3, 1.10, 1.08), (1.7, 1.02, 1.02),
      (2.0, 1.0, 1.0)]),
]

# ---- the head on the shoulders ----

# The skin makes his head a hood: from the eyes it widens without a break down into the shoulders, and its front
# hangs over the chest as a lip (drawn out to a point it was a beak: a bird of prey seen from the front). Here the
# head ends in a jaw, under it stands a short thick neck, and from the neck's foot the muscle runs out to each
# shoulder. The three are one surface round an upright axis through the head (AXIS: how far ahead of the figure's
# middle it stands), given by height as a rounded box: half as wide, as deep to the front and to the back as the
# row says, the last number its squareness (2: an ellipse; more: squarer, a heavy jaw).
AXIS = -0.042
HEAD = [
    (1.740, 0.086, 0.092, 0.082, 2.2), (1.720, 0.092, 0.095, 0.088, 2.2), (1.700, 0.096, 0.097, 0.092, 2.3),
    (1.680, 0.100, 0.097, 0.094, 2.6), (1.660, 0.101, 0.096, 0.096, 2.9), (1.640, 0.099, 0.093, 0.098, 3.0),
    (1.600, 0.099, 0.093, 0.098, 3.0),
]
NECK = [
    (1.720, 0.090, 0.060, 0.090, 2.3), (1.660, 0.090, 0.060, 0.097, 2.3), (1.640, 0.091, 0.061, 0.102, 2.3),
    (1.620, 0.093, 0.062, 0.109, 2.3), (1.600, 0.096, 0.064, 0.118, 2.3), (1.560, 0.100, 0.066, 0.128, 2.3),
    (1.520, 0.102, 0.068, 0.138, 2.3), (1.480, 0.102, 0.068, 0.140, 2.3),
]
# The jaw's lower edge: its height under the chin, how much higher it is at the jaw's corner under the ear and at
# the back of the head, and over what height the head's width comes out of the neck's (under the chin: short, an
# edge; at the back: long, the skull runs into the nape).
JAW = dict(chin=1.622, corner=0.026, back=0.056, edge=0.042, edge_back=0.050)
# The neck's foot: from this height down the surface runs out from the neck, at the front onto the chest's top, at
# the sides as the muscle to the shoulder: (height of the foot, how far it runs out, over what height)
# Each number is three: without the hood, with half of it, and with all of it (see HOOD).
FOOT = dict(front=((1.598, 1.605, 1.620), (0.014, 0.017, 0.0), (0.030, 0.030, 0.030)),
            side=((1.648, 1.678, 1.735), (0.150, 0.130, 0.125), (0.050, 0.085, 0.120)),
            back=((1.600, 1.600, 1.600), (0.0, 0.0, 0.0), (0.030, 0.030, 0.030)))
# How far the throat comes forward toward the chin (0: where NECK has it, 1: as far as the chin, no jaw's edge left):
# without the hood, with half of it, with all of it.
THROAT = (0.0, 0.5, 0.87)
FRONT_MOST = 0.088
# How far from the axis the skin is laid onto this surface (all of it, then fading to none)
REACH = (0.20, 0.12)

# The shoulders' corners: the skin makes the shoulders a level shelf with a corner over each arm. The shelf's outer
# end is rounded down by this much, from this far inside the shoulder joint outward.
CORNER = dict(drop=0.004, inside=0.030, over=0.090, pull=0.004)

# ---- the muscles ----

# The chest: a barrel (the maintainer, 2026-11-21, of plates standing out of the skin's own chest: "the chest
# appears 'indented'"; the skin's chest is a trough: its middle lies 3 cm behind its sides, and with plates on the
# sides the middle was a valley 4 cm deep, and the belly stood further forward than the breastbone). The chest's
# front is filled out to one full mass: by height how far ahead of the shoulder joints its foremost line stands
# (CHEST: from the throat out to the chest's fullest and back in to the belly), and across how it falls away to the
# sides (by CHEST_ROUND[0] at CHEST_ROUND[1] from the middle, CHEST_ROUND[2]: how square). Skin that stands
# further forward already (the shoulders' fronts) stays where it is.
CHEST = [(1.610, 0.113), (1.570, 0.123), (1.540, 0.133), (1.510, 0.141), (1.480, 0.147), (1.450, 0.150), (1.420, 0.150),
         (1.390, 0.144), (1.360, 0.133), (1.330, 0.124), (1.300, 0.118), (1.270, 0.110)]
CHEST_ROUND = (0.043, 0.210, 2.6)

# The chest muscle, a plate on each side of the breastbone with a lower edge that reads: how far it stands out, the
# height of its lower edge at its lowest, how much higher the edge is at the armpit (rising from `rise`, over the
# next stretch) and at the breastbone, over what height the edge rises (short: a step) and over what height the plate
# fades out upward toward the collarbone, and from where to where it reaches across.
# (On the barrel the plates are swellings, low and soft: the chest's mass is the barrel's.)
PEC = dict(out=0.010, edge=1.385, up_outside=0.075, up_inside=0.014, rise=(0.115, 0.130), step=0.048, full=0.035,
           fade=0.150, inside=(-0.06, 0.03), outside=(0.165, 0.255))

# Lines drawn into the trunk as shallow grooves: (which side of the trunk, the line's points as (x, height), how
# deep, how wide (the fall-off to each side), both sides mirrored or only the middle)
GROOVES = [
    # the breastbone, between the chest's plates: a narrow line, no valley
    ("front", [(0.0, 1.535), (0.0, 1.46), (0.0, 1.385)], 0.0025, 0.008, False),
    # the middle line of the belly, down to the navel: a hint
    ("front", [(0.0, 1.37), (0.0, 1.24), (0.0, 1.09)], 0.0034, 0.015, False),
    # between the chest's muscle and the shoulder's
    ("front", [(0.160, 1.560), (0.205, 1.495), (0.238, 1.425)], 0.0030, 0.017, True),
    # the outer edges of the belly's straight muscle
    ("front", [(0.076, 1.335), (0.068, 1.20), (0.052, 1.06)], 0.0034, 0.015, True),
    # the lower edge of the ribs
    ("front", [(0.034, 1.338), (0.074, 1.302), (0.118, 1.250)], 0.0028, 0.015, True),
    # the line from the hip bone down to the groin
    ("front", [(0.140, 1.050), (0.100, 0.975), (0.050, 0.915)], 0.0036, 0.015, True),
    # the spine, from the hips to between the shoulder blades
    ("back", [(0.0, 1.01), (0.0, 1.26), (0.0, 1.52)], 0.0105, 0.019, False),
]

# Soft rises on the trunk: (which side, (x, height), the direction of the rise as (outward, backward, up), how
# high, how far across, how far up and down)
MOUNDS = [
    # the back's broad muscle, under the arm: it carries the V of the back
    ("back", (0.150, 1.345), (0.45, 0.90, 0.0), 0.017, 0.062, 0.090),
    # the shoulder blade
    ("back", (0.118, 1.475), (0.0, 1.0, 0.0), 0.014, 0.055, 0.065),
    # the long muscles beside the spine, in the small of the back
    ("back", (0.040, 1.140), (0.0, 1.0, 0.0), 0.006, 0.024, 0.100),
    # the buttock: round and high
    ("back", (0.100, 0.925), (0.0, 1.0, -0.15), 0.036, 0.066, 0.072),
    # the small of the back, drawn in (a hollow: a rise with a minus)
    ("back", (0.0, 1.135), (0.0, 1.0, 0.0), -0.012, 0.110, 0.075),
]

# The limbs' muscles: (the limb as its two joints, where along it (0 at the first joint, 1 at the second), the side
# of the limb it lies on as (outward, forward), how high, how long toward the first joint and toward the second
# (its fall-off each way), how wide across)
LIMBS = [
    # the shoulder's cap: over the joint, running down to a point on the arm's outside
    ("shoulder", "elbow", 0.13, (1.0, 0.0), 0.0180, (0.065, 0.075), 0.058),
    # under it the arm's outside is drawn in a little, so the cap ends
    ("shoulder", "elbow", 0.60, (1.0, 0.0), -0.0060, (0.050, 0.060), 0.034),
    # the cap's front and its back
    ("shoulder", "elbow", 0.07, (0.55, 0.85), 0.0100, (0.050, 0.075), 0.045),
    ("shoulder", "elbow", 0.09, (0.55, -0.85), 0.0100, (0.050, 0.075), 0.045),
    # the upper arm: its front (biceps) and, higher up, its back (triceps)
    ("shoulder", "elbow", 0.56, (0.0, 1.0), 0.0120, (0.070, 0.060), 0.030),
    ("shoulder", "elbow", 0.42, (0.2, -1.0), 0.0100, (0.080, 0.070), 0.032),
    # the forearm: thick below the elbow on the outside and the inside, slimmer to the wrist
    ("elbow", "wrist", 0.22, (0.9, 0.45), 0.0060, (0.045, 0.085), 0.030),
    ("elbow", "wrist", 0.25, (-0.9, -0.45), 0.0050, (0.045, 0.080), 0.028),
    # the thigh: its front, its outside, the teardrop inside above the knee, and its back
    ("hip", "knee", 0.42, (0.10, 1.0), 0.0160, (0.120, 0.120), 0.046),
    ("hip", "knee", 0.58, (1.0, 0.25), 0.0080, (0.100, 0.110), 0.044),
    ("hip", "knee", 0.86, (-0.80, 0.60), 0.0110, (0.058, 0.040), 0.028),
    ("hip", "knee", 0.50, (0.0, -1.0), 0.0080, (0.110, 0.120), 0.044),
    # the knee: a hint of its cap
    ("hip", "knee", 1.0, (0.0, 1.0), 0.0060, (0.030, 0.030), 0.024),
    # the calf: high under the knee, its inner head a little lower and fuller, a short fall to the heel's tendon
    ("knee", "ankle", 0.27, (0.45, -1.0), 0.0240, (0.065, 0.080), 0.032),
    ("knee", "ankle", 0.31, (-0.50, -1.0), 0.0260, (0.070, 0.080), 0.032),
    # the shin's own muscle, outside the bone
    ("knee", "ankle", 0.30, (0.9, 0.5), 0.0075, (0.065, 0.100), 0.024),
]


class Wucht(Figure):
    """The drums: the heavy one: very wide shoulders, a deep chest over a small waist, heavy arms, a strong man's
    muscles, and the dome of his head on a short thick neck."""
    key = "wucht"
    # the frame is as it was: the skin's mesh folds differently when a thickness changes, and the dome of his head is
    # made in one with it; all of the body's shape is in sculpt()
    frame = dict(
        height=1.04, scale=dict(torso=1.08, waist=1.02, chest=1.30, shoulder=1.55, arm=1.55, hand=1.25, thigh=1.25,
                                leg=1.25, neck=1.7, head=1.24),
        move=dict(crown=(0, 0, 0.004)),
        radius=dict(bicep=1.25, ribs=1.18), shoulders=1.30, hips=1.05, neck=1.25, stoop=0.03,
        eye=(1.0, 0.46, 0.22),
    )

    def sculpt(self, w):
        # The chin drawn out of the skin, as it always was, but kept only from the eyes up: it shapes the face up to
        # there, and the eyes sit on that face. Below, the skin stays as it was made (drawn out it was the beak), and
        # his jaw is part of the head's base, see HEAD.
        made = [v.co.copy() for v in w.body.data.vertices]
        draw_chin(w.body, w.head, w.hs, 1.5)
        for v, was in zip(w.body.data.vertices, made):
            v.co = was + (v.co - was) * float(smooth((was.z - (FACE_TOP - FADE)) / FADE))

        refine(w.body, [(FINE_LOW, below_eyes)] * 2 + [(FINE, chest_up)] + [(FINE_NECK, round_neck)] * 3
               + [(FINE_JAW, under_jaw)] * 2)
        clay = Clay(w.body)
        seat_head(clay, unfold=150, times=60)
        # the trunk's skin evened out (the skin's mesh leaves folds on it)
        clay.relax(clay.trunk * band(clay.V[:, 2], 1.00, 1.56, 0.05) * (1.0 - clay.seated), 8)
        shape_build(clay, w.P, BUILD)
        round_corners(clay, w.P)
        fill_chest(clay, w.P)
        shape_trunk(clay, w.P)
        shape_chest(clay, w.P)
        shape_limbs(clay, w.P)
        clay.write()


def smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def band(z, low, high, edge):
    """1 between two heights, fading to 0 over [edge] outside them."""
    return smooth((z - (low - edge)) / edge) * smooth((high + edge - z) / edge)


def below_eyes(c):
    return c.z < FACE_TOP


def chest_up(c):
    return FINE_FROM < c.z < FACE_TOP


def round_neck(c):
    return 1.54 < c.z < FACE_TOP and math.hypot(c.x, c.y - AXIS) < 0.24


def under_jaw(c):
    return 1.575 < c.z < 1.70 and math.hypot(c.x, c.y - AXIS) < 0.17 and c.y < AXIS + 0.03


def refine(body, cuts):
    """A finer skin: for each of [cuts] (the longest edge allowed, where), every longer edge there is cut in two,
    and with it the edges across from it in its faces, on and on while they are not short already, so the skin stays
    rows of four-cornered faces and gets no fans of slivers. The new points are set on the round between the old
    ones. The old points stay where they are, and above FACE_TOP nothing is cut. A new point takes its bones from
    its edge's two ends; where that makes more than four, the smallest go (a point is moved by four bones at most)."""
    mesh = body.data
    for longest, inside in cuts:
        bm = bmesh.new()
        bm.from_mesh(mesh)
        cut = {e for e in bm.edges if e.calc_length() > longest and all(inside(v.co) for v in e.verts)}
        todo = list(cut)
        while todo:
            e = todo.pop()
            for f in e.link_faces:
                if len(f.edges) != 4:
                    continue

                across = next(o for o in f.edges if not set(o.verts) & set(e.verts))
                if across not in cut and across.calc_length() > 0.6 * longest and all(inside(v.co) for v in across.verts):
                    cut.add(across)
                    todo.append(across)

        bmesh.ops.subdivide_edges(bm, edges=sorted(cut, key=lambda e: e.index), cuts=1, use_grid_fill=True, smooth=1.0)
        # (a face left with more than four corners has no point inside it, however large it is: cut into
        # three-cornered ones, the next round cuts its long edges)
        many = [f for f in bm.faces if len(f.verts) > 4]
        if many:
            bmesh.ops.triangulate(bm, faces=many, quad_method='BEAUTY', ngon_method='BEAUTY')

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

        self.arm = {s: np.clip(sum(self.G[b + s] for b in ("elbow", "wrist", "hand")), 0.0, 1.0) for s in "LR"}
        self.leg = {s: np.clip(sum(self.G[b + s] for b in ("knee", "ankle", "toe")), 0.0, 1.0) for s in "LR"}
        # how much of a point is trunk and legs (1) and how much arm (0): the arms hang close beside the trunk and the
        # thighs, and what shapes one must not pull at the other
        self.trunk = 1.0 - np.clip(self.arm["L"] + self.arm["R"], 0.0, 1.0)
        # nothing above the face's top moves: every step's share fades to nothing under it
        self.below = smooth((FACE_TOP - self.V[:, 2]) / FADE)
        # how much of a point lies on the head's and the neck's own surface (set by seat_head)
        self.seated = np.zeros(n)

        edges = np.empty(len(body.data.edges) * 2, dtype=np.int32)
        body.data.edges.foreach_get("vertices", edges)
        self.edges = edges.reshape(-1, 2)
        count = np.zeros(n)
        np.add.at(count, self.edges[:, 0], 1.0)
        np.add.at(count, self.edges[:, 1], 1.0)
        # (the skin has a point no edge ends at: it has no neighbours and stays where it is)
        self.count = np.maximum(count, 1.0)
        self.lone = count == 0

    def middle(self):
        """The middle of each point's neighbours."""
        V, (a, b) = self.V, self.edges.T
        total = np.zeros_like(V)
        np.add.at(total, a, V[b])
        np.add.at(total, b, V[a])
        out = total / self.count[:, None]
        out[self.lone] = V[self.lone]
        return out

    def relax(self, much, times):
        """Evens the skin, [times] over: each point goes half of [much] (a share for each point) of the way to the
        middle of its neighbours."""
        for _ in range(times):
            self.V += (self.middle() - self.V) * (0.5 * much * self.below)[:, None]

    def write(self):
        self.body.data.vertices.foreach_set("co", self.V.ravel())
        self.body.data.update()


def by_height(table, z, column=1, outside=1.0):
    """The value of [table] (rows from the top down, the height first) at each height of [z]: one smooth curve
    through the rows; outside the table [outside], or the nearest row's value where that is None."""
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
    if outside is None:
        return out
    return np.where((z > h[0]) & (z < h[-1]), out, outside)


def box(wide, front, back, square, sin, cos):
    """How far from the axis a rounded box (half as [wide], as deep to the [front] and to the [back], see HEAD)
    reaches in the direction given by [sin] (toward the side) and [cos] (toward the front)."""
    deep = np.where(cos > 0, front, back)
    return (np.abs(sin / wide) ** square + np.abs(cos / deep) ** square) ** (-1.0 / square)


def dial(three):
    """A number that goes with HOOD: [three] are its values at 0, 0.5 and 1, straight lines between."""
    return float(np.interp(HOOD, (0.0, 0.5, 1.0), three))


def seat(turn, z):
    """The surface of the head's base, the neck and the neck's foot (HEAD, NECK, JAW, FOOT): how far from the axis
    it is in the direction [turn] (0: front, a quarter turn: the side, a half: the back) at the height [z]."""
    sin, cos = np.sin(turn), np.cos(turn)
    side = np.abs(sin)
    # 0 at the front, 1 at the side, 2 at the back
    round_ = np.where(cos > 0, side ** 2, 2.0 - side ** 2)
    front, at_side, back = np.clip(1.0 - round_, 0, 1), 1.0 - np.abs(round_ - 1.0), np.clip(round_ - 1.0, 0, 1)
    j = JAW
    edge_at = j["chin"] + j["corner"] * smooth(round_) + (j["back"] - j["corner"]) * smooth(round_ - 1.0)
    edge = j["edge"] + (j["edge_back"] - j["edge"]) * smooth(round_ - 0.9)
    head = [by_height(HEAD, z, c, None) for c in (1, 2, 3, 4)]
    neck = [by_height(NECK, z, c, None) for c in (1, 2, 3, 4)]
    # (with the hood the throat comes forward toward the chin: the jaw's edge stands less far before it)
    neck[1] = neck[1] + (head[1] - neck[1]) * dial(THROAT)
    neck = box(*neck, sin, cos)
    out = neck + (box(*head, sin, cos) - neck) * smooth((z - edge_at) / edge)
    # the foot
    foot, far, over = (sum(part * dial(FOOT[name][k]) for part, name in ((front, "front"), (at_side, "side"), (back, "back")))
                       for k in range(3))
    t = np.clip((foot - z) / over, 0.0, None)
    # (at the front it runs out no further than the chest's top stands: FRONT_MOST from the axis)
    return np.maximum(out, np.minimum(out + far * t ** 2, front * FRONT_MOST + (1.0 - front) * 0.45))


def seat_head(clay, unfold, times):
    """Lays the skin between the eyes and the shoulders onto the head's base, the neck and its foot (see HEAD): each
    point goes to the nearest place of that surface, and the points are spread evenly over it, [times] over, so the
    skin's old folds (the hood's lip) open out."""
    V = clay.V
    turns = np.linspace(0.0, 2.0 * math.pi, 145)[:-1]
    heights = np.arange(1.470, 1.780, 0.0025)
    T, Z = np.meshgrid(turns, heights)
    R = seat(T, Z)
    grid = np.stack([R * np.sin(T), AXIS - R * np.cos(T), Z], axis=2)
    rows, cols = grid.shape[:2]
    faces = [(r * cols + c, r * cols + (c + 1) % cols, (r + 1) * cols + (c + 1) % cols, (r + 1) * cols + c)
             for r in range(rows - 1) for c in range(cols)]
    tree = BVHTree.FromPolygons([Vector(p) for p in grid.reshape(-1, 3)], faces)

    far = np.hypot(V[:, 0], V[:, 1] - AXIS)
    share = clay.below * smooth((V[:, 2] - 1.515) / 0.060) * (1.0 - smooth((far - REACH[0]) / REACH[1]))
    clay.seated = share
    inside = np.nonzero(share > 0)[0]
    # first the old folds are evened away ([unfold] times over), then the skin is laid on
    clay.relax(share, unfold)
    was = V.copy()
    for step in range(times):
        if step:
            mid = clay.middle()
            V[inside] += (mid[inside] - V[inside]) * 0.6
        for i in inside:
            place = tree.find_nearest(Vector(V[i]))[0]
            V[i] = was[i] + (np.array(place) - was[i]) * share[i]

    # Under the chin and on the throat that leaves pleats (the hood's lip has more skin than the jaw needs, and on
    # the nearest place of a hollow corner a pleat stays a pleat). There the points are spread out by where they are
    # on the surface, not in space: by their direction round the axis and their height, each set to the middle of its
    # neighbours' until they rest. That opens every pleat.
    turn = np.arctan2(V[:, 0], AXIS - V[:, 1])
    front = (share > 0.999) & (np.abs(turn) < math.radians(72)) & (V[:, 2] > 1.588) & (V[:, 2] < 1.70) & ~clay.lone
    place = np.stack([np.cos(turn), np.sin(turn), V[:, 2]], axis=1)
    a, b = clay.edges.T
    for _ in range(400):
        total = np.zeros_like(place)
        np.add.at(total, a, place[b])
        np.add.at(total, b, place[a])
        place[front] = (total / clay.count[:, None])[front]
        place[:, :2] /= np.linalg.norm(place[:, :2], axis=1)[:, None]

    turn, z = np.arctan2(place[front, 1], place[front, 0]), place[front, 2]
    far_ = seat(turn, z)
    V[front] = np.stack([far_ * np.sin(turn), AXIS - far_ * np.cos(turn), z], axis=1)

    # where the neck's foot runs out into the chest and the shoulders the skin is evened once more: the hood's
    # corners left ridges there, down the chest and along the shoulders' tops
    yoke = band(V[:, 2], 1.50, 1.612, 0.03) * (1.0 - smooth((far - 0.30) / 0.10))
    clay.relax(yoke, 10)
    # and under the eyes, where the skin that was laid on meets the skin that stayed
    clay.relax(band(V[:, 2], 1.69, 1.74, 0.02) * (far < 0.2), 6)


def shape_build(clay, P, build):
    """The trunk's outline and the limbs' thickness (TRUNK, THICK), as much of them as [build] says."""
    V, trunk = clay.V, clay.trunk * clay.below
    z = V[:, 2]
    spine = [P[n].pos for n in ("hips", "belly", "ribs", "chest")]
    mid = np.interp(z, [p.z for p in spine], [p.y for p in spine])
    wide, front, back = (1.0 + (by_height(TRUNK, z, c) - 1.0) * build for c in (1, 2, 3))
    V[:, 0] *= 1.0 + (wide - 1.0) * trunk
    middle = 1.0 - smooth((np.abs(V[:, 0]) - DEEP[0]) / DEEP[1])
    deep = np.where(V[:, 1] < mid, front, back)
    V[:, 1] = mid + (V[:, 1] - mid) * (1.0 + (deep - 1.0) * trunk * middle)

    for on, joints, thickness in THICK:
        t_, fx_, fy_ = (np.array([row[k] for row in thickness]) for k in range(3))
        for side, sx in (("L", 1.0), ("R", -1.0)):
            a, b, c = (np.array(P[j + side].pos) for j in joints)
            own = clay.arm[side] if on == "arm" else clay.leg[side]
            if on == "arm":
                # the shoulder's cap: by the bones it is the trunk's, but it is the arm's upper end
                cap = smooth((sx * V[:, 0] - (a[0] * sx - 0.07)) / 0.08) * smooth((V[:, 2] - (a[2] - 0.15)) / 0.07)
                own = np.maximum(own, cap)

            # where along the limb: on the first piece, or past its end on the second
            t1 = (V - a) @ (b - a) / (b - a).dot(b - a)
            t2 = np.clip((V - b) @ (c - b) / (c - b).dot(c - b), 0.0, 1.0)
            first = t1 <= 1.0
            t = np.where(first, np.clip(t1, 0.0, 1.0), 1.0 + t2)
            line = np.where(first[:, None], a + np.outer(np.clip(t1, 0.0, 1.0), b - a), b + np.outer(t2, c - b))
            fx = 1.0 + (np.interp(t, t_, fx_) - 1.0) * build * own * clay.below
            fy = 1.0 + (np.interp(t, t_, fy_) - 1.0) * build * own * clay.below
            V[:, 0] = line[:, 0] + (V[:, 0] - line[:, 0]) * fx
            V[:, 1] = line[:, 1] + (V[:, 1] - line[:, 1]) * fy


def round_corners(clay, P):
    """The shoulders' corners rounded (CORNER), and the skin evened there."""
    V, c = clay.V, CORNER
    joint = P["shoulderL"].pos
    out = smooth((np.abs(V[:, 0]) - (joint.x - c["inside"])) / c["over"])
    high = smooth((V[:, 2] - (joint.z + 0.005)) / 0.075)
    V[:, 2] -= c["drop"] * out * high * clay.below
    V[:, 0] -= np.sign(V[:, 0]) * c["pull"] * out * high * clay.below
    top = smooth((V[:, 2] - (joint.z - 0.05)) / 0.05) * smooth((np.abs(V[:, 0]) - 0.12) / 0.06) * (1.0 - clay.seated)
    clay.relax(top, 1)


def fill_chest(clay, P):
    """The chest's front filled out to a barrel (CHEST): skin that lies behind the barrel's front comes forward to
    it, skin before it stays. Then the chest's front is evened (where the barrel meets the skin that stayed, and
    the skin's own uneven faces under the neck's foot: in gloss they read as dents), and filled once more."""
    V = clay.V
    low, high = CHEST[-1][0], CHEST[0][0]
    drop, wide, square = CHEST_ROUND

    def fill():
        z = V[:, 2]
        front = P["shoulderL"].pos.y - (by_height(CHEST, z, 1, None) - drop * (np.abs(V[:, 0]) / wide) ** square)
        on = facing(V, middle_line(P, z), "front") * clay.trunk * clay.below * band(z, low + 0.02, high - 0.02, 0.02)
        V[:, 1] -= np.clip(V[:, 1] - front, 0.0, None) * on

    fill()
    chest = facing(V, middle_line(P, V[:, 2]), "front") * clay.trunk * band(V[:, 2], 1.34, 1.585, 0.03) \
        * (1.0 - smooth((np.abs(V[:, 0]) - 0.20) / 0.06))
    clay.relax(chest, 8)
    fill()
    clay.relax(chest, 2)


def middle_line(P, z):
    """Where the trunk's middle is (y) at each height of [z]: the line through the skeleton's points."""
    points = [P[n].pos for n in ("hips", "belly", "ribs", "chest", "neck")]
    return np.interp(z, [p.z for p in points], [p.y for p in points])


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


def shape_trunk(clay, P):
    """The trunk's soft rises (MOUNDS) and its lines (GROOVES)."""
    V, trunk = clay.V, clay.trunk * clay.below * (1.0 - clay.seated)
    for face, (x, z), toward, height, across, along in MOUNDS:
        for side in ((1.0, -1.0) if x > 0 else (1.0,)):
            on = facing(V, middle_line(P, V[:, 2]), face) * trunk
            q = ((V[:, 0] - side * x) / across) ** 2 + ((V[:, 2] - z) / along) ** 2
            way = np.array([side * toward[0], toward[1], toward[2]])
            way /= np.linalg.norm(way)
            V += way * (height * np.exp(-q / 2) * on)[:, None]

    for face, line, depth, wide, both in GROOVES:
        for side in ((1.0, -1.0) if both else (1.0,)):
            on = facing(V, middle_line(P, V[:, 2]), face) * trunk
            d, t = to_line(V[:, 0] * side, V[:, 2], line)
            # the line fades in and out at its ends
            ends = smooth(t / 0.15) * smooth((1.0 - t) / 0.15)
            V[:, 1] += (1.0 if face == "front" else -1.0) * depth * np.exp(-(d / wide) ** 2 / 2) * ends * on


def shape_chest(clay, P):
    """The chest's muscle (PEC): a plate that stands out of the chest's front, with a lower edge."""
    V, c = clay.V, PEC
    on = facing(V, middle_line(P, V[:, 2]), "front") * clay.trunk * clay.below * (1.0 - clay.seated)
    ax = np.abs(V[:, 0])
    edge = c["edge"] + c["up_outside"] * smooth((ax - c["rise"][0]) / c["rise"][1]) \
        + c["up_inside"] * smooth((c["inside"][1] - ax) / c["inside"][1])
    up = V[:, 2] - edge
    # (toward the breastbone the edge softens: the two plates run out there, they do not end in a corner)
    step = c["step"] * (1.0 + 1.5 * smooth((c["inside"][1] - ax) / c["inside"][1]))
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
                own = smooth((V[:, 0] * side - (a[0] * side - 0.065)) / 0.06) if first == "shoulder" else clay.arm[name]
                own = np.maximum(own, clay.arm[name]) * (V[:, 0] * side > 0)
            else:
                own = clay.trunk * smooth(V[:, 0] * side / 0.012)

            own = own * clay.below * (1.0 - clay.seated)
            ring = (np.abs(along - at * length) < 0.025) & (own > 0.5) & (far < 0.17)
            thick = far[ring].mean()
            d = V - (a + u * (at * length) + way * thick)
            du = d @ u
            across = np.linalg.norm(d - np.outer(du, u) - np.outer(d @ way, way), axis=1)
            q = (du / np.where(du < 0, long[0], long[1])) ** 2 + (across / wide) ** 2
            turned = smooth(((radial @ way) / np.maximum(far, 1e-6) + 0.25) / 0.75)
            V += way * (height * np.exp(-q / 2) * turned * own)[:, None]
