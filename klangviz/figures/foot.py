# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# A foot in a flat shoe that grows out of its leg as one surface, for any figure (the maintainer, 2026-11-20: "she
# should wear comfortable footware so she can walk around a lot easily"). A character describes the shoe in numbers
# (see SHOE); this file makes its surface, lays a mesh on it, cuts the body's own foot off above the ankle and
# joins the new one to the shin there, so the ankle flows into the foot. The skin's own foot (a tube from the ankle
# to the toe) could only be a ramp or an arch: its heel never came down without a fold.
#
# All lengths in metres. The foot's own space: s along the foot from the ankle joint toward the toes (the way the
# bone "toe" points, seen from above), t across it toward the figure's outside, z up from the floor.
import math

import bmesh
import numpy as np
from mathutils import Vector

# What a character gives (its own values replace these), and what each means.
SHOE = dict(
    # The shoe's length: how far it reaches behind the ankle joint and ahead of it.
    back=0.048, front=0.153,
    # Seen from above: half as wide at the heel and at the ball; where the ball is (-1 the heel's end, 1 the toes'
    # tip); how short the round of each end is (2: the whole half is an ellipse, more: a shorter, blunter end);
    # and how far the toes' tip stands toward the figure's inside (the big toe's side) of the foot's middle line.
    heel=0.027, ball=0.0315, ball_at=0.45, round_heel=5.0, round_toe=2.7, swing=0.004,
    # Seen from the side: the top's height along the foot (s, height), in between smoothly, rounded down to the
    # rim at the two ends as from above; the sole's height over the floor, and how short the round of its ends is
    # (high: it lies flat nearly all its length).
    tops=((-0.048, 0.052), (-0.022, 0.064), (0.0, 0.070), (0.030, 0.063), (0.065, 0.046), (0.100, 0.031), (0.153, 0.026)),
    sole=0.0, round_sole=12.0,
    # A slice across the foot: widest at the height [rim] (at the heel's end, from the ball on; a heel is round, so
    # its widest is higher); above it as square as [square_top] (2: an ellipse), below as [square_sole] (high: a
    # flat sole with a small round edge).
    rim=(0.024, 0.010), square_top=2.2, square_sole=4.0,
    # The ankle: a round column, the shin's skin carried on down from where it is cut, as thick at the ankle joint
    # as [ankle] times the skeleton's ankle (the shin goes on thinning to 1; an ankle is wider than the shin just
    # above it), growing into the foot over [blend]. Where it grows into the shoe's sides it widens them a little,
    # as an ankle's bones do. Inside the foot it thins to nothing between the heights [stem] (from the top down;
    # the plain shoe: just over the floor, so its sides are a little wider nearly all the way down; over a sole
    # of its own: above that sole, or the shoe's sides swell out over the sole's edge under the ankle).
    ankle=1.15, blend=0.022, stem=(0.014, -0.012),
    # A sole of its own under the shoe, where one is worn: (its height at the heel, at the toes, how far it
    # stands out round the shoe).
    welt=None,
    # The mesh: where the skin is cut above the ankle joint and the gap closed by the join; the line the rings
    # turn about in the bend from the shin into the foot (s, z: before the ankle, over the instep, outside the
    # surface); points round a ring; rings in the bend and along the foot.
    cut=0.045, gap=0.008, hinge=(0.060, 0.105), around=40, bend=22, along=13,
)


def _smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def _smin(a, b, k):
    """The smaller of two fields, rounded off where they are within [k] of each other: a smooth union."""
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b * (1 - h) + a * h - k * h * (1 - h)


def _curve(points, x):
    """The smooth curve through [points] ((x, y), rising x) at [x]: a cubic through each pair, its slope at a point
    that of the line through its two neighbours. Beyond the ends: the end's value."""
    px, py = np.array(points, dtype=float).T
    slope = np.gradient(py, px)
    x = np.clip(x, px[0], px[-1])
    i = np.clip(np.searchsorted(px, x) - 1, 0, len(px) - 2)
    h = px[i + 1] - px[i]
    u = (x - px[i]) / h
    return ((2 * u ** 3 - 3 * u ** 2 + 1) * py[i] + (u ** 3 - 2 * u ** 2 + u) * h * slope[i]
            + (-2 * u ** 3 + 3 * u ** 2) * py[i + 1] + (u ** 3 - u ** 2) * h * slope[i + 1])


class Shape:
    """A shoe with its ankle as a field over the foot's space: below zero inside, above outside, near the surface
    about the distance to it. [shoe]: see SHOE. [column]: the ankle's middle line and radius, ((s, t, z) at the
    cut, radius there, (s, t, z) at the ankle joint, radius there, how much thinner the shin gets a metre down)."""

    def __init__(self, shoe, column):
        self.shoe, self.column = shoe, column

    def middle(self, s):
        """Where the foot's middle line is at [s] along it (t): it swings to the inside toward the toes."""
        f = self.shoe
        a = (s - (f["front"] - f["back"]) / 2) / ((f["back"] + f["front"]) / 2)
        return -f["swing"] * _smooth((a - 0.1) / 0.9)

    def rim(self, a):
        """How high the shoe is widest, by where along it (-1 the heel's end, 1 the toes' tip)."""
        f = self.shoe
        return f["rim"][0] + (f["rim"][1] - f["rim"][0]) * _smooth((a + 1) / (f["ball_at"] + 1))

    def last(self, p, wider=0.0, top=None):
        """The shoe alone (a shoemaker's last): slices across the foot, each a rounded shape widest at the rim.
        [wider]: that much wider all round; [top]: a flat top at these heights, the heel's and the toes' (the sole
        of its own, from the floor up)."""
        f = self.shoe
        s, t, z = p[:, 0], p[:, 1], p[:, 2]
        half = (f["back"] + f["front"]) / 2 + wider
        a = (s - (f["front"] - f["back"]) / 2) / half
        beyond = np.maximum(np.abs(a) - 1.0, 0.0) * half
        a = np.clip(a, -0.9995, 0.9995)
        end = np.sqrt(1 - np.abs(a) ** np.where(a < 0, f["round_heel"], f["round_toe"]))
        width = (f["heel"] + (f["ball"] - f["heel"]) * _smooth((a + 1) / (f["ball_at"] + 1)) + wider) * end
        across = np.abs(t - self.middle(s)) / width
        height = _curve(f["tops"], s) if top is None else top[0] + (top[1] - top[0]) * _smooth((a + 1) / 2)
        rim = self.rim(a) if top is None else height / 2
        flat = np.sqrt(1 - np.abs(a) ** f["round_sole"])
        up = (height - rim) * (end if top is None else flat)
        down = (rim - (f["sole"] if top is None else 0.0)) * flat
        n = np.where(z >= rim, f["square_top"] if top is None else 6.0, f["square_sole"])
        reach = np.where(z >= rim, up, down)
        level = (across ** n + (np.abs(z - rim) / reach) ** n) ** (1 / n)
        d = (level - 1) * np.minimum(width, reach)
        return np.where(beyond > 0, np.maximum(d, beyond), d)

    def axis(self, z):
        """The ankle's middle line at the height [z]: (s, t)."""
        (s1, t1, z1), _, (s0, t0, z0), _, _ = self.column
        u = (z - z0) / (z1 - z0)
        return s0 + (s1 - s0) * u, t0 + (t1 - t0) * u

    def ankle(self, p):
        """The ankle: a round column along the shin's middle line, as thick as the shin where the skin is cut and
        thinning as it does there, then going over to its radius at the ankle joint. Inside the foot it thins to
        nothing between the heights [stem]."""
        (_, _, z1), r1, (_, _, z0), r0, thinning = self.column
        down = np.clip(z1 - p[:, 2], None, z1 - z0)
        r = r1 - thinning * down + (r0 - (r1 - thinning * (z1 - z0))) * (np.maximum(down, 0.0) / (z1 - z0)) ** 2
        high, low = self.shoe["stem"]
        r = r * _smooth((p[:, 2] - low) / (high - low))
        s, t = self.axis(p[:, 2])
        return np.maximum(np.hypot(p[:, 0] - s, p[:, 1] - t) - r, low - p[:, 2])

    def field(self, p):
        """The shoe and the ankle, grown into one; nothing of it below the sole (the ankle's column drew the sole
        down a millimetre under it)."""
        return np.maximum(_smin(self.last(p), self.ankle(p), self.shoe["blend"]), self.shoe["sole"] - p[:, 2])


def _through(field, origin, far):
    """Where the line from [origin] along [far] goes into the surface [field] and where it leaves it: two distances
    from [origin] (to a millimetre)."""
    steps = np.arange(0.0, 0.30, 0.001)
    inside = field(origin + steps[:, None] * far) < 0
    if not inside.any():
        raise ValueError("A ring of the foot's mesh misses the foot: " + str(origin) + " " + str(far))
    return steps[np.argmax(inside)], steps[len(inside) - 1 - np.argmax(inside[::-1])]


def _ring(field, origin, far, across, around):
    """
    A ring of the surface [field] in the plane through [origin] spanned by [far] and [across]: the plane's part
    that lies inside is found along [far] from [origin], and from its middle [around] rays go out to the surface,
    the first along [far], turning toward [across]. The rays are spread as round an ellipse as long and as wide as
    the ring, so a flat ring gets its points evenly and not all on its flat sides.
    """
    def reach(rays):
        near, off = np.zeros(len(rays)), np.full(len(rays), 0.25)
        for _ in range(26):
            mid = (near + off) / 2
            out = field(centre + mid[:, None] * rays) > 0
            off = np.where(out, mid, off)
            near = np.where(out, near, mid)
        return (near + off) / 2

    into, out_of = _through(field, origin, far)
    centre = origin + far * (into + out_of) / 2
    long, wide = (out_of - into) / 2, reach(np.array([across, -across])).mean()
    turn = np.arange(around) * (2 * math.pi / around)
    phi = np.arctan2(wide * np.sin(turn), long * np.cos(turn))
    rays = np.cos(phi)[:, None] * far + np.sin(phi)[:, None] * across
    return centre + reach(rays)[:, None] * rays


def _loft(shape, top):
    """
    The mesh of a foot as rings, from the shin down and along the foot: level rings down the ankle from the height
    [top] to the hinge's, rings turning about the hinge through the heel (the bend from the shin into the foot),
    and upright rings along the foot to the toes. The rings' points, ring by ring ((rings, around, 3)), and the
    toes' tip, the last point.
    """
    f, field = shape.shoe, shape.field
    hs, hz = f["hinge"]
    tip = np.array([f["front"], shape.middle(f["front"]), f["rim"][1]])
    t_ = np.array([0.0, 1.0, 0.0])
    rings = []
    levels = max(1, int(round((top - hz) / 0.008)))
    for j in range(levels):
        z = top + (hz - top) * j / levels
        rings.append(_ring(field, np.array([hs, shape.axis(z)[1], z]), np.array([-1.0, 0.0, 0.0]), t_, f["around"]))
    # in the bend the rings are set by where they are furthest apart: evenly down the back of the ankle and along
    # the sole, and closer where that line turns, round the heel (evenly turned, they left the heel's round to
    # three points)
    hinge = np.array([hs, 0.0, hz])
    turns = np.linspace(0.0, 0.5 * math.pi, 181)
    fars = np.array([[-math.cos(a), 0.0, -math.sin(a)] for a in turns])
    outer = np.array([hinge + far * _through(field, hinge, far)[1] for far in fars])
    steps = np.diff(outer, axis=0)
    heading = np.unwrap(np.arctan2(steps[:, 2], steps[:, 0]))
    turning = np.concatenate([[0.0], np.abs(np.diff(np.convolve(heading, np.ones(9) / 9, mode="same")))])
    way = np.concatenate([[0.0], np.cumsum(np.linalg.norm(steps, axis=1) + 0.03 * turning)])
    for alpha in np.interp(np.arange(f["bend"]) * way[-1] / f["bend"], way, turns):
        rings.append(_ring(field, hinge, np.array([-math.cos(alpha), 0.0, -math.sin(alpha)]), t_, f["around"]))
    for j in range(f["along"]):
        # closer toward the tip, which is round
        s = hs + (tip[0] - hs) * math.sin(0.5 * math.pi * j / f["along"])
        rings.append(_ring(field, np.array([s, shape.middle(s), hz]), np.array([0.0, 0.0, -1.0]), t_, f["around"]))
    return np.array(rings), tip


def _welt(shape, f, around=48, along=26):
    """A sole of its own under the shoe (f["welt"]: its heights and how far it stands out), as rings from the heel's
    end to the toes' tip, a point at each end."""
    heel, toes, out = f["welt"]
    high = max(heel, toes)

    def field(p):
        return shape.last(p, wider=out, top=(heel, toes))

    s0, s1 = -f["back"] - out, f["front"] + out
    rings = []
    for j in range(1, along):
        s = (s0 + s1) / 2 - (s1 - s0) / 2 * math.cos(math.pi * j / along)
        rings.append(_ring(field, np.array([s, shape.middle(s), high + 0.05]), np.array([0.0, 0.0, -1.0]),
                           np.array([0.0, 1.0, 0.0]), around))
    ends = (np.array([s0, shape.middle(s0), heel / 2]), np.array([s1, shape.middle(s1), toes / 2]))
    return np.array(rings), ends


def _skin(bm, rings, first=None, last=None):
    """The faces between [rings] (lists of the mesh's points, ring by ring), a fan to the point [first] before the
    first ring and to [last] after the last, where given. The new faces."""
    faces = []
    for a, b in zip(rings, rings[1:]):
        n = len(a)
        faces += [bm.faces.new((a[i], a[(i + 1) % n], b[(i + 1) % n], b[i])) for i in range(n)]
    for end, ring in ((first, rings[0]), (last, rings[-1])):
        if end is not None:
            n = len(ring)
            faces += [bm.faces.new((ring[i], ring[(i + 1) % n], end)) for i in range(n)]
    return faces


def _zip(bm, a, b, centre):
    """Joins two rings of different numbers of points round [centre] (seen from above) by triangles: both are
    walked round by their angle, and the one that is behind steps on. The new faces."""
    def angle(v):
        return math.atan2(v.co.y - centre.y, v.co.x - centre.x)

    a, b = sorted(a, key=angle), sorted(b, key=angle)
    turn_a = [angle(v) for v in a] + [angle(a[0]) + 2 * math.pi]
    turn_b = [angle(v) for v in b] + [angle(b[0]) + 2 * math.pi]
    faces = []
    i = j = 0
    while i < len(a) or j < len(b):
        if j == len(b) or (i < len(a) and turn_a[i + 1] <= turn_b[j + 1]):
            faces.append(bm.faces.new((a[i % len(a)], a[(i + 1) % len(a)], b[j % len(b)])))
            i += 1
        else:
            faces.append(bm.faces.new((b[j % len(b)], b[(j + 1) % len(b)], a[i % len(a)])))
            j += 1
    return faces


def flat_feet(body, P, shoe):
    """
    Gives [body] its feet in flat shoes ([shoe]: a character's own values over SHOE): the skin is cut level above
    each ankle and what hangs below (its own foot) goes; the new foot is built on the floor under the ankle joint,
    pointing where the bone "toe" points, its ankle as thick as the shin where it was cut and joined to it point
    by point. The foot moves with the bone "toe"; up the ankle it goes over to the shin's bone, as the skin did.
    Call it last in a character's sculpt(): what smooths or shapes the skin after it would not know the feet.
    """
    unknown = set(shoe) - set(SHOE)
    if unknown:
        raise ValueError("No such part of a shoe: " + ", ".join(sorted(unknown)))
    f = dict(SHOE, **shoe)
    bm = bmesh.new()
    bm.from_mesh(body.data)
    deform = bm.verts.layers.deform.verify()
    level = P["ankleL"].pos.z + f["cut"]
    cut = bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:], dist=1e-6, plane_co=(0, 0, level),
                                 plane_no=(0, 0, 1), clear_inner=True)
    rim = [v for v in cut["geom_cut"] if isinstance(v, bmesh.types.BMVert)]
    new = []
    for side, sx in (("L", 1.0), ("R", -1.0)):
        ankle, toe = P["ankle" + side].pos, P["toe" + side].pos
        shin, foot = body.vertex_groups["ankle" + side].index, body.vertex_groups["toe" + side].index
        ahead = Vector((toe.x - ankle.x, toe.y - ankle.y, 0.0)).normalized()
        out = Vector((-ahead.y, ahead.x, 0.0)) * sx

        def to_foot(co):
            d = Vector((co.x - ankle.x, co.y - ankle.y, 0.0))
            return np.array([d.dot(ahead), d.dot(out), co.z])

        def to_body(p):
            return Vector((ankle.x, ankle.y, 0.0)) + ahead * p[0] + out * p[1] + Vector((0.0, 0.0, p[2]))

        edge = [v for v in rim if v.co.x * sx > 0]
        middle = sum((v.co for v in edge), Vector()) / len(edge)
        radius = sum((v.co - middle).length for v in edge) / len(edge)
        # the shin's skin goes on thinning down to the ankle joint as it did above the cut
        calf = P["calf" + side]
        thinning = (calf.radius - P["ankle" + side].radius) / (calf.pos.z - ankle.z)
        column = (to_foot(middle), radius, to_foot(ankle), P["ankle" + side].radius * f["ankle"], thinning)
        shape = Shape(f, column)
        top = level - f["gap"]
        rings, tip = _loft(shape, top)
        share = sum(v[deform].get(shin, 0.0) for v in edge) / len(edge)
        print("FOOT", side, "shin share at the cut", round(share, 3))

        def add(p):
            v = bm.verts.new(to_body(p))
            # up the ankle the foot goes over to the shin's bone, as much at the cut as the skin there
            up = share * float(_smooth((p[2] - (ankle.z - 0.015)) / (top - (ankle.z - 0.015))))
            v[deform][foot] = 1.0 - up
            if up > 0.0:
                v[deform][shin] = up
            return v

        made = [[add(p) for p in ring] for ring in rings]
        new += _skin(bm, made, last=add(tip))
        new += _zip(bm, edge, made[0], middle)
        if f["welt"]:
            sole, (heel_end, toe_end) = _welt(shape, f)
            new += _skin(bm, [[add(p) for p in ring] for ring in sole], first=add(heel_end), last=add(toe_end))
    for face in new:
        face.smooth = True
        face.material_index = 0
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    bm.to_mesh(body.data)
    bm.free()
