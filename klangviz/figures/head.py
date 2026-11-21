# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

# A head that grows out of its neck, with a face of soft planes, eyes that lie in sockets of its surface and, where
# one is worn, a tail of hair, as one smooth surface, for any figure (the maintainer, 2026-11-20: a shared head
# builder); and the join that makes it one surface with the body it stands on (see Join: where it lies and how the
# two meet). A character describes its head in numbers (see SPEC, EYES and JOIN); this file makes the surface, lays a
# mesh on it, sets the eyes into it, weights it to the bones and welds it to the body's skin: a character calls
# refine() first in its sculpt(), build() from its back_of_head() and weld() from its hair(). The first part works
# on numbers only (numpy), so it could be carried elsewhere one day; the second hands the result to Blender. All
# lengths in metres, from the head's point (where the bone "head" ends), y back, z up, x to the figure's left.
import math

import numpy as np

# What a character gives (its own values replace these), and what each means.
SPEC = dict(
    # The head's shape is drawn: two outlines, each a smooth curve through named points that passes through every one
    # of them and does not swing out between them (see _through). A point is moved by hand, one at a time.
    # [profile]: the side outline on the middle line, (name, y, z) each: from the chin's front up the face, over the
    # brow, the forehead and the top, down the back of the head and forward under the skull to the throat. Its first
    # and its last point lie at one height, the chin's. From each end up to its highest place it may only rise.
    # [outline]: the left half of the front outline, (name, x, z) each: from the chin up over the jaw, the cheek, the
    # cheekbone and the temple to the top, its last point, on the middle line (its height is the profile's highest,
    # whatever is written). It may only rise too.
    profile=[], outline=[],
    # The cross-sections between the two follow from them: at each height an oval as wide as the outline and as deep
    # as the profile, widest at [widest] of the way from the front to the back. [face_square], [back_square]: how
    # square that oval is before its widest place and behind it (2: an ellipse; more: a face that is a plane with
    # round corners; less: one that comes to a point in the middle, seen from above). [widest] and [face_square] may
    # each be a table by height, (height, value), falling heights: a brow that is a plane over a jaw that comes to a
    # point at the chin.
    widest=0.55, face_square=2.0, back_square=2.0,
    # Above [cap] the surface is worked out as a cap that closes at the top (how it is computed, not what it looks
    # like: the two outlines give the shape there too). Below the chin the head is rounded off over [chin_down].
    cap=0.0, chin_down=0.015,
    # Soft rises and hollows of the face that no outline shows (a cheekbone, the hollow under it):
    # (name, across, z, half size across, half size up and down, height) each: its middle is the place on the head's
    # surface, seen from the front, at the height [z] and [across] of the way from the middle line to the outline
    # there (so it stays on the cheek when the outline is moved); its height in metres (below zero: a hollow). One
    # beside the middle line is made on both sides.
    swells=[],
    # The face's planes: soft rises grown out of the head: (middle, half sizes, softness) each.
    features=[],
    # The eyes, where the head brings its own: the left eye, as EYES has it (the right is its mirror). Each lies in a
    # socket of the head's surface (see Eye and eyes()). None: the head has no eyes of its own (the figure sets them
    # on it, figure.add_eyes).
    eyes=None,
    # The neck, part of the head's surface: rounded columns (see Shape.neck), the first the neck itself, down into
    # the body (deep into it, where there is a join: the foot's rounding reads it); smoothly one ([neck_soft]) and
    # grown into the head ([neck_blend]). A point of the neck moves with the head above neck_fade[0] and with the
    # neck's own bone below neck_fade[1].
    neck=[], neck_soft=0.02, neck_blend=0.03, neck_fade=(-0.085, -0.115),
    # The join: where the neck grows out of the body, and the two become one surface (see JOIN and Join): a character
    # gives its own values for it.
    join={},
    # The tail of hair, where one is worn: its joints along its middle line, (name, place, radius) each, the first in
    # the back of the skull where it grows out (a point, not a joint), the last its tip; how softly it grows out of
    # the skull; its twirl (an oval turning along it: how much of its radius, radians a metre).
    tail=None, tail_blend=0.06, twirl=0.0, twirl_turn=10.0,
    # The mesh: the line the rings over the head turn about (with a tail: in the hollow between the skull and the
    # tail, see sleeve()), points round a ring, how much closer toward the face, the steps between rings (over the
    # head, along the tail, through the neck), and how often the points are evened out.
    hinge=(0.0, 0.15, 0.0), around=48, crowd=0.55, page_step=0.0065, ring_step=0.015, level_step=0.004, relax=3,
)

# An eye and its socket, as a character gives them for the left eye (its own values replace these). Lengths lie in
# the eye's own plane: the face's surface at the eye's middle, before the socket is pressed into it.
EYES = dict(
    # The eye's middle: how far from the face's middle line, and how high (from the head's point).
    at=(0.032, -0.030),
    # Its outline, an almond: half its length; how far the upper lid's arc rises over the line through the two
    # corners and how far the lower lid's falls below it; how many degrees the outer corner stands higher than the
    # inner one; and how pointed the corners are (0.5: round, an ellipse's ends; 1: a lens's points).
    half=0.021, upper=0.009, lower=0.007, slant=0.0, point=0.75,
    # The socket, a soft hollow of the face: how deep; its floor, level at that depth, and its rim, where it has run
    # out into the face: each an oval round the eye's middle, by how far it reaches toward the nose, the brow, the
    # temple and the cheek. A rim close over the floor is a brow; one far below it, a cheek's plane.
    socket=0.006, floor=(0.023, 0.010, 0.022, 0.009), rim=(0.029, 0.018, 0.036, 0.026),
    # The eye in it: its edge lies this far behind the lids' edge (the lid's thickness), and its middle stands this
    # far before its own edge: the front of an eyeball.
    lid=0.0012, bulge=0.0035,
    # The mesh: the head's own faces go where the socket is, to [border] beyond its rim (the two eyes' must leave the
    # face's middle line standing), and rings round the eye take their place: so many points round each; so many
    # rings through the socket's skin from the rim to the lids' edge, and so many over the eye to its middle.
    border=0.002, around=48, rings=(5, 4),
)

# The join of a neck and a body, as a character gives it (its own values replace these).
JOIN = dict(
    # Where the join's plane crosses the neck's middle line, (y, z), and how many degrees that plane rises toward the
    # back: square to a neck that leans forward, low enough in front to pass under the chin.
    at=(0.0, -0.13), tilt=0.0,
    # The hinge: an oval round the neck, in the air beside it: how far from the middle in front, at the sides and
    # behind; and how high over the plane it stands there (in front, under a chin, in the plane itself; the higher,
    # the more squarely the foot's rays meet the shoulders). The rings of the neck's foot turn about it, from the
    # plane downward.
    reach=(0.08, 0.09, 0.07), lift=(0.0, 0.03, 0.01),
    # How many degrees the last of those rings is turned down, in front, at the sides and behind: it lies on the
    # body's skin, and there the skin is cut and the two are zipped.
    end=(70.0, 75.0, 70.0),
    # The foot's rounding: the neck and the body run into each other over this length, in front, at the sides (where
    # the neck's foot flares into the shoulders) and behind.
    soft=(0.012, 0.060, 0.012),
    # The step between the foot's rings.
    step=0.005,
)


def spec_of(character):
    """A character's description, with SPEC's values where it gives none."""
    unknown = set(character) - set(SPEC)
    if unknown:
        raise ValueError("No such part of a head: " + ", ".join(sorted(unknown)))
    out = dict(SPEC, **character)
    if out["eyes"] is not None:
        unknown = set(out["eyes"]) - set(EYES)
        if unknown:
            raise ValueError("No such part of an eye: " + ", ".join(sorted(unknown)))
        out["eyes"] = dict(EYES, **out["eyes"])
    unknown = set(out["join"]) - set(JOIN)
    if unknown:
        raise ValueError("No such part of a join: " + ", ".join(sorted(unknown)))
    out["join"] = dict(JOIN, **out["join"])
    return out


# --- the numbers -------------------------------------------------------------------------------------------------

def _catmull(p0, p1, p2, p3, u):
    """The Catmull-Rom curve through p1 (u = 0) and p2 (u = 1)."""
    return 0.5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                  + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)


def _smooth_table(rows, z):
    """Each column of [rows] (by their first value, falling) at the heights [z], smoothly (Catmull-Rom)."""
    t = np.array(rows, dtype=float)
    zs = t[:, 0]
    out = np.zeros((len(z), t.shape[1] - 1))
    n = len(zs)
    i = np.clip(np.searchsorted(-zs, -z) - 1, 0, n - 2)          # the row above each z
    u = np.clip((zs[i] - z) / (zs[i] - zs[i + 1]), 0.0, 1.0)
    for c in range(1, t.shape[1]):
        out[:, c - 1] = _catmull(t[np.maximum(i - 1, 0), c], t[i, c], t[i + 1, c], t[np.minimum(i + 2, n - 1), c], u)
    return out


def _by_height(value, z, low, high):
    """A value that is one number, or a table by height (height, value), falling heights, at the heights [z]."""
    if np.isscalar(value):
        return value
    return _smooth_table(value, np.clip(z, max(low, value[-1][0]), min(high, value[0][0])))[:, 0]


def _through(points, per=48):
    """A smooth curve through [points] (n x 2), in their order: a centripetal Catmull-Rom curve, which passes through
    every point and neither loops nor swings out between points that lie unevenly apart. [per] samples a span, the
    last point included; and for each point the index of its sample."""
    P = np.array(points, dtype=float)
    if (np.linalg.norm(np.diff(P, axis=0), axis=1) < 1e-6).any():
        raise ValueError("Two points of a drawn curve lie in one place")
    P = np.vstack([2 * P[0] - P[1], P, 2 * P[-1] - P[-2]])
    out = []
    for k in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[k - 1:k + 3]
        t1 = np.linalg.norm(p1 - p0) ** 0.5
        t2 = t1 + np.linalg.norm(p2 - p1) ** 0.5
        t3 = t2 + np.linalg.norm(p3 - p2) ** 0.5
        t = np.linspace(t1, t2, per, endpoint=False)[:, None]
        a1 = (t1 - t) / t1 * p0 + t / t1 * p1
        a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2
        a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3
        b1 = (t2 - t) / t2 * a1 + t / t2 * a2
        b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
        out.append((t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2)
    out.append(P[-2][None])
    return np.vstack(out), [k * per for k in range(len(P) - 2)]


def _rising(line, at, names, what):
    """Checks that the drawn curve [line] (samples: across, height) only rises; else says near which point it does
    not ([at]: the points' samples, [names]: their names)."""
    bad = np.nonzero(np.diff(line[:, 1]) <= 0)[0]
    if len(bad):
        near = names[int(np.argmin(np.abs(np.array(at) - bad[0])))]
        raise ValueError("The head's %s does not rise all the way near its point '%s': a point's height has to lie "
                         "between its neighbours' (the curve turns over there)" % (what, near))


def egg_line(turn, long, high, tilt, taper=0.03):
    """The middle line of the long skull that figure.skull() makes (the frontwoman's head), from its own middle, as
    (y, z) at the places [turn] round it (degrees: 0 the forehead's tip, 90 the top, 180 the back's tip, 270 the
    underside): an egg, [long] and [high] its half sizes, its long axis rising toward the back by [tilt] degrees,
    a little slimmer at the forehead ([taper], as skull() has it). A head that is to follow that skull takes points
    of its profile from here."""
    t = np.radians(np.asarray(turn, dtype=float))
    along, up = -np.cos(t), np.sin(t)
    egg = np.where(along < 0.33, np.minimum(1.0, 1.0 - taper + taper * np.sin((along + 1.0) / 2.0 * math.pi * 0.83)
                                            / math.sin(math.pi * 0.83 / 2)), 1.0)
    a = math.radians(tilt)
    return np.stack([math.cos(a) * along * long - math.sin(a) * up * high * egg,
                     math.sin(a) * along * long + math.cos(a) * up * high * egg], axis=-1)


def dialled(drawn, moves, dials):
    """
    A head's description [drawn] (see SPEC) with dials turned: for tuning a head by eye. [dials]: {dial: how far it
    is turned} (0: as drawn). [moves]: {dial: what one whole turn of it moves}, a list of (part, name, by): [part] is
    "profile", "outline" or "swells", [name] a point of that list, and [by] is added to the numbers after its name
    (a profile's point: y, z; an outline's: x, z; a swell: across, z, half sizes, height); or [part] is "features" and
    [name] the feature's place in the list: [by] is added to its middle (x, y, z).
    """
    out = dict(drawn)
    for dial, turn in dials.items():
        if dial not in moves:
            raise ValueError("No such dial: " + dial)
        for part, name, by in moves[dial] if turn else ():
            rows = list(out[part])
            if part == "features":
                centre, half, soft = rows[name]
                rows[name] = (tuple(c + turn * b for c, b in zip(centre, by)), half, soft)
            else:
                hits = [i for i, row in enumerate(rows) if row[0] == name]
                if len(hits) != 1:
                    raise ValueError("The dial '%s' moves '%s' of the %s, which has %d of that name"
                                     % (dial, name, part, len(hits)))
                row = rows[hits[0]]
                rows[hits[0]] = (row[0],) + tuple(v + turn * b for v, b in zip(row[1:], by))
            out[part] = rows
    return out


def _smin(a, b, k):
    """The smaller of two fields, rounded off where they are within [k] of each other: a smooth union."""
    h = np.clip(0.5 + 0.5 * (b - a) / k, 0.0, 1.0)
    return b * (1 - h) + a * h - k * h * (1 - h)


def _smooth(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3 - 2 * x)


def _ease(x):
    """As _smooth, and as level at both ends as a surface that is to catch no line of light there has to be."""
    x = np.clip(x, 0.0, 1.0)
    return x * x * x * (x * (6 * x - 15) + 10)


def _spline(points, step):
    """A Catmull-Rom curve through [points] (n x 3), sampled about every [step] metres: samples, and for each point
    of [points] the index of its sample."""
    P = np.array(points, dtype=float)
    P = np.vstack([2 * P[0] - P[1], P, 2 * P[-1] - P[-2]])
    out, at = [], []
    for k in range(1, len(P) - 2):
        n = max(2, int(np.linalg.norm(P[k + 1] - P[k]) / step))
        at.append(len(out))
        out += [_catmull(P[k - 1], P[k], P[k + 1], P[k + 2], j / n) for j in range(n)]
    at.append(len(out))
    out.append(P[-2])
    return np.array(out), at


class Shape:
    """A head (its neck, its tail) as a field: below zero inside, above outside, near the surface about the distance
    to it. [spec]: see SPEC."""

    def __init__(self, spec):
        self.spec = spec
        self._draw()
        # the eyes take their place and their way from the head as drawn, before the swells: a cheekbone that is
        # made stronger does not turn an eye
        self.eyes, self.swells = [], []
        if spec["eyes"]:
            self.eyes = [Eye(self, spec["eyes"], side) for side in (1.0, -1.0)]
        swells = []
        for name, share, z, across, up, height in spec["swells"]:
            x = share * float(np.interp(z, self.side[:, 1], self.side[:, 0]))
            for side in ((1.0, -1.0) if x else (1.0,)):
                start, back = np.array([[side * x, -0.4, z]]), np.array([[0.0, 1.0, 0.0]])
                middle = (start + back * _enter(self.drawn, start, back, reach=0.6)[0])[0]
                g = [(self.drawn(middle[None] + d)[0] - self.drawn(middle[None] - d)[0]) / 0.001
                     for d in 0.0005 * np.eye(3)]
                # (the head's field rises this much a metre there: a height in metres moves it by that much)
                swells.append((middle, np.array([across, max(across, up), up]), height * float(np.linalg.norm(g))))
        self.swells = swells
        tail = spec["tail"]
        if not tail:
            return
        self.curve, at = _spline([t[1] for t in tail], 0.002)
        radii = [t[2] for t in tail]
        r = np.zeros(len(self.curve))
        for k in range(len(at) - 1):
            u = _smooth(np.linspace(0, 1, at[k + 1] - at[k], endpoint=False))
            r[at[k]:at[k + 1]] = radii[k] + (radii[k + 1] - radii[k]) * u
        r[-1] = radii[-1]
        self.radius = r
        self.s = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(self.curve, axis=0), axis=1))])
        self.joint_s = [self.s[i] for i in at]
        tang = np.gradient(self.curve, axis=0)
        self.tang = tang / np.linalg.norm(tang, axis=1)[:, None]
        across = np.cross(np.array([1.0, 0.0, 0.0]), self.tang)
        self.across = across / np.linalg.norm(across, axis=1)[:, None]
        fade = np.clip(self.s / 0.12, 0, 1) * np.clip((self.s[-1] - self.s) / 0.15, 0, 1)
        self.twirl = spec["twirl"] * _smooth(fade)

    def _draw(self):
        """The two drawn outlines as tables by height: the profile's front and back, and the outline's half width."""
        sp = self.spec
        names = [row[0] for row in sp["profile"]]
        line, at = _through([row[1:3] for row in sp["profile"]])
        top = int(np.argmax(line[:, 1]))
        if abs(line[0, 1] - line[-1, 1]) > 1e-6:
            raise ValueError("The profile's two ends, '%s' and '%s', have to lie at one height: the chin's"
                             % (names[0], names[-1]))
        _rising(line[:top + 1], at, names, "profile")
        _rising(line[top:][::-1], [len(line) - 1 - i for i in at], names, "profile")
        self.top, self.chin = float(line[top, 1]), float(line[0, 1])
        self.front, self.back = line[:top + 1], line[top:][::-1]
        # the outline closes round over the top: drawn on through its mirror image, and only its left half kept
        names = [row[0] for row in sp["outline"]]
        half = [row[1:3] for row in sp["outline"][:-1]] + [(0.0, self.top)]
        if sp["outline"][-1][1] != 0.0:
            raise ValueError("The outline's last point, '%s', is the top: on the middle line (x 0)" % names[-1])
        line, at = _through(half + [(-x, z) for x, z in half[-2::-1]])
        self.side = line[:at[len(half) - 1] + 1]
        _rising(self.side, at[:len(half)], names, "outline")
        if abs(self.side[0, 1] - self.chin) > 1e-6:
            raise ValueError("The outline's first point, '%s', has to lie at the chin's height, as the profile's "
                             "ends do" % names[0])

    def _sizes(self, z):
        """A slice of the head as drawn, at the heights [z] (between the chin and just under the top): its half width,
        how deep it is before and behind its widest place, and where that place is; through the cap each size as
        that of the ellipsoid whose slice at that height it is (see drawn())."""
        sp = self.spec
        cap, top = sp["cap"], self.top
        front = np.interp(z, self.front[:, 1], self.front[:, 0])
        back = np.interp(z, self.back[:, 1], self.back[:, 0])
        w = np.interp(z, self.side[:, 1], self.side[:, 0])
        # (through the cap the widest place drifts to the slice's middle: the middles of an egg's slices lie on one
        # straight line, also where it is tilted, so there the cap's sizes hardly change with the height)
        widest = _by_height(sp["widest"], z, self.chin, top)
        mid = front + (widest + (0.5 - widest) * _smooth((z - cap) / (top - cap))) * (back - front)
        shrink = np.sqrt(1.0 - np.clip((z - cap) / (top - cap), 0.0, 1.0) ** 2)
        return w / shrink, (mid - front) / shrink, (back - mid) / shrink, mid

    def drawn(self, p):
        """The head as its two outlines draw it: no eyes' sockets, no swells, none of the face's planes."""
        sp = self.spec
        x, y, z = p[:, 0], p[:, 1], p[:, 2]
        cap, top, chin = sp["cap"], self.top, self.chin
        # The cap: above [cap] the surface is that of an ellipsoid's upper half, whose half sizes and whose middle at
        # each height are such that it is exactly as wide and as deep there as the outlines say (for a head that is
        # an egg up there, upright or tilted, the sizes are the same at every height and the middle runs along a
        # straight line; for any other they change slowly). So the top is closed and round, and the field stays
        # even over it, where a slice's own size runs out to nothing.
        # The outlines at each point's height; below the chin as at the chin. Through the last sixth of the cap's
        # height the sizes change ever more slowly and at the top they stand still (each height there takes them
        # from a little lower, at the top from half that sixth below it): the very top closes as an egg's would,
        # as high as the profile says, and the field has no crease at the top's height, where a tail that leaves
        # the head level with its top blends with it. (The drawn points up there steer the top through its height
        # and through how the curves arrive at that last sixth.)
        last = (top - cap) / 6.0
        into = np.maximum(z - (top - last), 0.0) / last
        part = np.minimum(into, 1.0)
        w, before, behind, mid = self._sizes(np.maximum(z, chin) - last * (into - part + 0.5 * part * part))
        over = np.maximum(z - cap, 0.0) / (top - cap)
        # below the chin: the last slice, rounded off
        over = np.where(z < chin, (chin - z) / sp["chin_down"], over)
        depth = np.maximum(np.where(y < mid, before, behind), 1e-4)
        w = np.maximum(w, 1e-4)
        n = np.where(y < mid, _by_height(sp["face_square"], z, chin, top), sp["back_square"])
        flat = (np.abs(x / w) ** n + np.abs((y - mid) / depth) ** n) ** (1.0 / n)
        return (np.sqrt(flat * flat + over * over) - 1.0) * np.minimum(w, depth)

    def head(self, p):
        """The head alone: as drawn, its swells and its eyes' sockets in it, its face's planes on it."""
        sp = self.spec
        f = self.drawn(p)
        for middle, half, rise in self.swells:
            q = (((p - middle) / half) ** 2).sum(axis=1)
            f = f - rise * np.maximum(1.0 - q, 0.0) ** 2
        # the eyes' sockets, pressed into the head before its face's planes are grown on it: the nose's ridge stands
        # between them as high as it was
        for eye in self.eyes:
            f = f + eye.socket(p)
        for centre, half, soft in sp["features"]:
            q = (p - np.array(centre)) / np.array(half)
            f = _smin(f, (np.linalg.norm(q, axis=1) - 1.0) * min(half), soft)
        return f

    def head_distance(self, p, h=0.0005):
        """The head's field divided by how steeply it rises: about the distance to its surface, so it blends with the
        neck's and the tail's evenly all round. (Far from the head, where a jaw's slices change quickly with the
        height, the field is steep without the head being near: the division stops at 2, or a tail hanging well
        behind would take a ring from it.)"""
        g = np.zeros_like(p)
        for k in range(3):
            e = np.zeros(3)
            e[k] = h
            g[:, k] = (self.head(p + e) - self.head(p - e)) / (2 * h)
        return self.head(p) / np.clip(np.linalg.norm(g, axis=1), 0.2, 2.0)

    def neck(self, p):
        """The neck's columns, smoothly one. A column: (the points its middle line runs through, radius), or with a
        third value, how deep: an oval, its radius across and that many times it front to back."""
        f = None
        for path, r, *deep in self.spec["neck"]:
            d = None
            for a, b in zip(path, path[1:]):
                a, b = np.array(a), np.array(b)
                t = np.clip(((p - a) @ (b - a)) / ((b - a) @ (b - a)), 0.0, 1.0)
                q = p - (a + t[:, None] * (b - a))
                if deep:
                    q = q * np.array([1.0, 1.0 / deep[0], 1.0])
                piece = np.linalg.norm(q, axis=1) - r
                d = piece if d is None else np.minimum(d, piece)
            f = d if f is None else _smin(f, d, self.spec["neck_soft"])
        return f

    def head_and_neck(self, p):
        if not self.spec["neck"]:
            return self.head_distance(p)
        return _smin(self.head_distance(p), self.neck(p), self.spec["neck_blend"])

    def tail(self, p, want_s=False):
        """The tail: the distance to its surface (and, with [want_s], the nearest sample of its middle line)."""
        best = np.full(len(p), 1e9)
        idx = np.zeros(len(p), dtype=int)
        for a in range(0, len(self.curve), 32):
            c = self.curve[a:a + 32]
            mid = c.mean(axis=0)
            reach = np.linalg.norm(c - mid, axis=1).max()
            # only the points this piece of the tail can be nearer to than what was found so far
            near = np.nonzero(np.linalg.norm(p - mid, axis=1) - reach - self.radius[a:a + 32].max() < best)[0]
            if not len(near):
                continue
            d = np.linalg.norm(p[near, None, :] - c[None, :, :], axis=2) - self.radius[None, a:a + 32]
            j = np.argmin(d, axis=1)
            m = d[np.arange(len(near)), j]
            better = m < best[near]
            best[near[better]] = m[better]
            idx[near[better]] = a + j[better]
        d = p - self.curve[idx]
        angle = np.arctan2((d * self.across[idx]).sum(axis=1), d[:, 0])
        best = best - self.radius[idx] * self.twirl[idx] * np.cos(2 * (angle - self.spec["twirl_turn"] * self.s[idx]))
        return (best, idx) if want_s else best

    def field(self, p):
        if not self.spec["tail"]:
            return self.head_and_neck(p)
        return _smin(self.head_and_neck(p), self.tail(p), self.spec["tail_blend"])

    def grad(self, p, h=0.0004):
        g = np.zeros_like(p)
        for k in range(3):
            e = np.zeros(3)
            e[k] = h
            g[:, k] = (self.field(p + e) - self.field(p - e)) / (2 * h)
        return g


class Eye:
    """
    One eye of a head (see EYES; [side]: 1 the left, -1 the right, its mirror): where it lies and which way it faces.
    [middle]: the place on the face under the eye's middle, before the socket is pressed in; [ahead]: the way the
    face looks there, and so the eye; [along]: toward the eye's outer corner; [up]: square to both. A point's place
    in the eye's plane is (along, up) from the middle.

    The socket is part of the head's surface (Shape.head() adds socket()): a soft hollow, level over its floor and
    running out to its rim. The eye is made by eyes(): in the head's mesh, an almond's outline on the socket's
    floor is the lids' edge, and within it the eye's own surface lies set back and rounded.
    """

    def __init__(self, shape, e, side):
        self.e, self.side = e, side
        x, z = side * e["at"][0], e["at"][1]
        start = np.array([[x, -0.4, z]])
        back = np.array([[0.0, 1.0, 0.0]])
        self.middle = (start + back * _enter(shape.head, start, back, reach=0.6)[0])[0]
        g = np.array([(shape.head(self.middle[None] + d)[0] - shape.head(self.middle[None] - d)[0]) / 0.001
                      for d in 0.0005 * np.eye(3)])
        # (the head's field rises this much a metre there: a socket's depth in metres is pressed in by that much)
        self.steep = float(np.linalg.norm(g))
        self.ahead = g / self.steep
        out = np.array([side, 0.0, 0.0])
        out = out - (out @ self.ahead) * self.ahead
        out /= np.linalg.norm(out)
        rise = np.cross(self.ahead, out) * side
        t = math.radians(e["slant"])
        self.along = math.cos(t) * out + math.sin(t) * rise
        self.up = math.cos(t) * rise - math.sin(t) * out

    def plane(self, p):
        """The points [p] in the eye's plane: along it, up, and how far before it."""
        rel = p - self.middle
        return rel @ self.along, rel @ self.up, rel @ self.ahead

    def point(self, u, v, before=0.0):
        """The point at (u, v) of the eye's plane, [before] it."""
        return (self.middle + np.asarray(u)[:, None] * self.along + np.asarray(v)[:, None] * self.up
                + np.asarray(before)[:, None] * self.ahead)

    def oval(self, u, v, reach):
        """How far out the points (u, v) of the eye's plane are, in an oval's own reach that way ([reach]: toward the
        nose, the brow, the temple and the cheek)."""
        return np.hypot(u / np.where(u < 0, reach[0], reach[2]), v / np.where(v > 0, reach[1], reach[3]))

    def socket(self, p):
        """How much the socket adds to the head's field at the points [p] (so the surface goes in by its depth)."""
        e = self.e
        u, v, w = self.plane(p)
        floor, rim = self.oval(u, v, e["floor"]), self.oval(u, v, e["rim"])
        # 0 on the floor's edge, 1 on the rim
        between = (floor - 1.0) * rim / np.maximum(floor - rim, 1e-9)
        # (only the face: nothing behind it, where the same plane cuts the back of the head)
        return e["socket"] * self.steep * (1.0 - _ease(between)) * (1.0 - _smooth((np.abs(w) - 0.03) / 0.02))

    def outline(self, n):
        """The almond's outline as [n] points round it, (n x 2) in the eye's plane: half of them evenly apart along
        the upper lid's arc and half along the lower one's, so each corner is one of them; and for each the way out
        of the eye there, square to the outline."""
        e = self.e
        line = []
        for start, height in ((0.0, e["upper"]), (math.pi, -e["lower"])):
            turn = np.linspace(start, start + math.pi, 2000)
            arc = np.stack([e["half"] * np.cos(turn), height * np.abs(np.sin(turn)) ** (2 * e["point"])], axis=1)
            run = np.concatenate([[0.0], np.cumsum(np.linalg.norm(np.diff(arc, axis=0), axis=1))])
            at = np.linspace(0.0, run[-1], n // 2, endpoint=False)
            line.append(np.stack([np.interp(at, run, arc[:, k]) for k in range(2)], axis=1))
        line = np.vstack(line)
        along = np.roll(line, -1, axis=0) - np.roll(line, 1, axis=0)
        along /= np.linalg.norm(along, axis=1)[:, None]
        return line, np.stack([along[:, 1], -along[:, 0]], axis=1)


class Join:
    """
    Where a neck grows out of a body (a head's "join", see JOIN): the one named place where the head's surface and
    the body's skin meet, and how.

    Where it lies: a plane crosses the neck, tilted (square to a neck that leans, and so that it passes under the
    chin). Round the neck, in the air beside it, lies an oval, the hinge: in the plane under the chin, lifted over it
    at the sides, where the shoulders are.

    The head's side: above the plane the head's rings are the head's own. Below it, the rings of the neck's foot turn
    about the hinge like the pages of a book, from the plane downward: each point is where a ray from the hinge first
    meets the neck or the body's skin, rounded where the two are near each other (JOIN's soft), so the neck runs out
    into the shoulders, the chest and the back. The last pages lie on the body's skin alone.

    The body's side: its skin is cut along the last page (new points where the cut crosses its edges), and what is
    inside the cut goes: the skin's own stump of a neck and its small head. (For the rays, that stump is not there:
    the skin is closed with a lid where it stood, see Skin.)

    The meeting: the two open rings (the skin's cut, the foot's lowest ring) lie on the same skin a ring's step apart
    and are zipped with triangles (zipper(), weld()): one closed surface, nothing overlapping, nothing hidden. The
    foot's last points move as the skin they lie on (the very place under them); up the foot that passes into the
    neck's own bone and, under the jaw, into the head's (weights()).
    """

    def __init__(self, spec):
        j = spec["join"]
        self.j = j
        self.centre = np.array([0.0, j["at"][0], j["at"][1]])
        t = math.radians(j["tilt"])
        self.side = np.array([1.0, 0.0, 0.0])
        self.ahead = np.array([0.0, -math.cos(t), -math.sin(t)])      # in the plane, toward the face
        self.up = np.array([0.0, -math.sin(t), math.cos(t)])          # square to the plane

    def _round(self, values, c):
        """A value given in front, at the sides and behind, round the neck ([c]: 1 in front, -1 behind)."""
        front, side, back = values
        return side + (front - side) * np.maximum(0.0, c) ** 2 + (back - side) * np.maximum(0.0, -c) ** 2

    def hinge(self, c, s):
        """The hinge in the direction (s to the side, c ahead): how far it is from the middle (an oval), and how high
        it stands over the plane."""
        front, side, back = self.j["reach"]
        return 1.0 / np.sqrt((s / side) ** 2 + (c / np.where(c > 0, front, back)) ** 2), self._round(self.j["lift"], c)

    def round_the_neck(self, p):
        """Where each point [p] lies round the join: how high over the plane, how far from the middle, and in which
        direction (ahead, to the side: each -1 to 1)."""
        rel = p - self.centre
        h = rel @ self.up
        flat = rel - h[:, None] * self.up
        r = np.maximum(np.linalg.norm(flat, axis=1), 1e-9)
        return h, r, (flat @ self.ahead) / r, (flat @ self.side) / r

    def beyond(self, p):
        """How many degrees each point [p] lies beyond the cut, seen from the hinge (below zero: inside it, where the
        body's skin goes), and whether it is near the join at all (far parts of a body never count)."""
        h, r, c, s = self.round_the_neck(p)
        reach, lift = self.hinge(c, s)
        angle = np.degrees(np.arctan2(lift - h, reach - r))
        near = (h > -0.14) & (r < 2.0 * max(self.j["reach"]))
        return angle - self._round(self.j["end"], c), near

    def crossing(self, a, b):
        """Where between the points [a] and [b] (n x 3 each, one inside the cut, one beyond) the cut lies: the share
        of the way from a to b."""
        lo, hi = np.zeros(len(a)), np.ones(len(a))
        inside_a = self.beyond(a)[0] < 0
        for _ in range(14):
            mid = (lo + hi) / 2
            same = (self.beyond(a + (b - a) * mid[:, None])[0] < 0) == inside_a
            lo, hi = np.where(same, mid, lo), np.where(same, hi, mid)
        return (lo + hi) / 2

    def foot(self, shape, skin, turns):
        """
        The rings of the neck's foot, from the lowest (the last before the cut, on the body's skin) up to the one in
        the plane: points (rings x around x 3), and for each that lies on the skin the triangle it lies in and where
        (-1: not on the skin); how far the foot is from the skin where it should lie on it (by direction), and how
        far the rounding still reaches at the plane (both should be next to nothing).
        """
        j = self.j
        turns = np.array(turns)
        c, s = np.sin(turns), np.cos(turns)
        out = s[:, None] * self.side + c[:, None] * self.ahead
        reach, lift = self.hinge(c, s)
        # the ring in the plane: the neck as the head's own rings above have it; each page's ray starts on the hinge
        # over the plane and turns from that ring's point down to the cut
        level = _enter(shape.head_and_neck, self.centre + reach[:, None] * out, -out)
        first, last = np.arctan2(lift, level), np.radians(self._round(j["end"], c))
        n = max(6, int(math.ceil(float(((last - first) * np.hypot(level, lift)).max()) / j["step"])))
        angle = np.concatenate([first + (last - first) * k / n for k in range(n - 1, -1, -1)])
        origin = np.tile(self.centre + reach[:, None] * out + lift[:, None] * self.up, (n, 1))
        direction = -np.cos(angle)[:, None] * np.tile(out, (n, 1)) - np.sin(angle)[:, None] * self.up
        neck = np.minimum(_enter(shape.head_and_neck, origin, direction), 1.0)
        over = ((origin + direction * neck[:, None] - self.centre) @ self.up)[neck < 1.0]
        if len(over) and over.max() > 0.003:
            raise ValueError("The join's hinge sees the head above the join's plane (by %.1f mm): less lift, or more "
                             "reach" % (over.max() * 1000))
        body, tri, uvw = skin.hit(origin, direction)
        soft = np.tile(self._round(j["soft"], c), n)
        t = _smin(neck, np.minimum(body, 1.0), soft)
        # the ring in the plane is the neck's own, as the head's rings above it are: the rounding has to end below it
        early = float((neck - t)[-len(turns):].max())
        t[-len(turns):] = neck[-len(turns):]
        # the last rings on the body's skin alone
        on_skin = np.repeat(_smooth((np.arange(n - 1, -1, -1) - (n - 4)) / 2.0), len(turns))
        if not np.isfinite(body[on_skin > 0]).all():
            raise ValueError("The neck's foot misses the body: %d rays of its last pages"
                             % (~np.isfinite(body[on_skin > 0])).sum())
        short = np.where(on_skin > 0, np.abs(t - body), 0.0).reshape(n, len(turns)).max(axis=0)
        t = np.where(on_skin > 0, t + (np.minimum(body, 1.0) - t) * on_skin, t)
        if (t >= 1.0).any():
            raise ValueError("The neck's foot meets nothing: %d rays" % (t >= 1.0).sum())
        shape_ = (n, len(turns))
        on = np.where(np.abs(body - t) < 0.002, tri, -1)
        points = (origin + direction * t[:, None]).reshape(*shape_, 3)
        return points, on.reshape(shape_), uvw.reshape(*shape_, 3), short, early


class Skin:
    """A body's skin as numbers: its points (n x 3), its faces (lists of indices), and which bones move each point
    (a table n x bones, and the bones' names). [apart]: the bones whose skin is not the body's but the head's own
    (the skin's small head and tail, which the new head replaces): what is mostly theirs is not looked at, and where
    they move the rest a little, that share is given to the body's bones ([table] is without them)."""

    def __init__(self, points, faces, names, table, apart=()):
        self.points, self.names = np.asarray(points, dtype=float), list(names)
        self.table = np.asarray(table, dtype=float)
        self.apart = [self.names.index(n) for n in apart if n in self.names]
        self.own = self.table[:, self.apart].sum(axis=1) <= 0.5
        # the body's skin as the body's bones alone move it: the head's share given to the others
        self.table = self.table.copy()
        self.table[:, self.apart] = 0.0
        self.table /= np.maximum(self.table.sum(axis=1), 1e-9)[:, None]
        tris = np.array([(f[0], f[k], f[k + 1]) for f in faces for k in range(1, len(f) - 1)])
        tris = tris[self.own[tris].all(axis=1)]
        self.edges = np.array(sorted({(min(a, b), max(a, b)) for f in faces for a, b in zip(f, list(f[1:]) + [f[0]])}))
        # where the head's own skin stood on the body, the body is closed with a lid (each open edge to their middle)
        sides = np.concatenate([tris[:, [0, 1]], tris[:, [1, 2]], tris[:, [2, 0]]])
        key = np.sort(sides, axis=1)
        _, first, count = np.unique(key, axis=0, return_index=True, return_counts=True)
        rim = sides[first[count == 1]]
        self.corners, self.shares = self.points, self.table.copy()    # what hit() looks at: the skin and its lid
        if len(rim):
            on = np.unique(rim)
            self.corners = np.vstack([self.points, self.points[on].mean(axis=0)])
            self.shares = np.vstack([self.table, self.table[on].mean(axis=0)])
            tris = np.vstack([tris, np.column_stack([rim, np.full(len(rim), len(self.points))])])
        self.tris = tris

    def even(self, where, rounds=12):
        """The bones' shares evened out over the points [where] (each with its neighbours' mean, [rounds] times): the
        skin's own shares are cut off at four bones a point, which leaves steps where many bones meet; the foot of a
        neck that takes its shares from this skin would show each one as a crease when the head turns."""
        a, b = self.edges[:, 0], self.edges[:, 1]
        count = np.bincount(np.concatenate([a, b]), minlength=len(self.points)) + 1.0
        for _ in range(rounds):
            total = self.table.copy()
            np.add.at(total, a, self.table[b])
            np.add.at(total, b, self.table[a])
            self.table[where] = (total / count[:, None])[where]
        self.shares[:len(self.table)] = self.table
        if len(self.shares) > len(self.table):
            self.shares[-1] = self.table[np.unique(self.tris[self.tris[:, 2] == len(self.table)][:, :2])].mean(axis=0)

    def hit(self, origin, direction, within=0.30):
        """Where each ray first meets the skin: how far along it (inf: nowhere), the triangle, and where in it (the
        three corners' shares)."""
        A, B, C = (self.corners[self.tris[:, k]] for k in range(3))
        close = np.linalg.norm((A + B + C) / 3 - origin.mean(axis=0), axis=1) < within
        idx = np.nonzero(close)[0]
        A, e1, e2 = A[idx], B[idx] - A[idx], C[idx] - A[idx]
        t_out = np.full(len(origin), np.inf)
        tri = np.full(len(origin), -1)
        uvw = np.zeros((len(origin), 3))
        for i, (o, d) in enumerate(zip(origin, direction)):
            p = np.cross(d, e2)
            det = (e1 * p).sum(axis=1)
            ok = np.abs(det) > 1e-12
            inv = np.where(ok, 1.0 / np.where(ok, det, 1.0), 0.0)
            s = o - A
            u = (s * p).sum(axis=1) * inv
            q = np.cross(s, e1)
            v = (q @ d) * inv
            t = (e2 * q).sum(axis=1) * inv
            good = ok & (u >= -1e-9) & (v >= -1e-9) & (u + v <= 1 + 1e-9) & (t > 1e-6)
            if good.any():
                k = np.nonzero(good)[0][np.argmin(t[good])]
                t_out[i], tri[i], uvw[i] = t[k], idx[k], (1 - u[k] - v[k], u[k], v[k])
        return t_out, tri, uvw

    def at(self, tri, uvw):
        """The bones' weights at a place in a triangle (see hit())."""
        return (self.shares[self.tris[tri]] * uvw[:, :, None]).sum(axis=1)


def project(shape, p, steps):
    """The points [p] brought onto the surface, [steps] times along the field's slope (a cm a step at most)."""
    for _ in range(steps):
        g = shape.grad(p)
        move = -(shape.field(p) / np.maximum((g * g).sum(axis=1), 1e-8))[:, None] * g
        move *= np.minimum(1.0, 0.01 / np.maximum(np.linalg.norm(move, axis=1), 1e-9))[:, None]
        p = p + move
    return p


def _cast(shape, origin, direction, reach=0.22, step=0.002):
    """Where each ray from [origin] (inside) along [direction] first leaves the surface (to a tenth of a mm)."""
    hit = np.full(len(origin), reach)
    found = np.zeros(len(origin), dtype=bool)
    t = 0.0
    while t < reach and not found.all():
        t += step
        out = shape.field(origin + direction * t) > 0
        hit[out & ~found] = t
        found |= out
    lo, hi = np.maximum(hit - step, 0.0), hit
    for _ in range(10):
        mid = (lo + hi) / 2
        out = shape.field(origin + direction * mid[:, None]) > 0
        hi = np.where(out, mid, hi)
        lo = np.where(out, lo, mid)
    return origin + direction * hi[:, None]


def _enter(field, origin, direction, reach=0.25, step=0.002):
    """How far along each ray from [origin] (outside) the [field]'s surface is first entered (inf: not within reach)."""
    hit = np.full(len(origin), np.inf)
    t = 0.0
    while t < reach and not np.isfinite(hit).all():
        t += step
        inside = field(origin + direction * t) < 0
        hit[inside & ~np.isfinite(hit)] = t
    found = np.isfinite(hit)
    lo, hi = np.where(found, hit - step, 0.0), np.where(found, hit, 0.0)
    for _ in range(10):
        mid = (lo + hi) / 2
        inside = field(origin + direction * mid[:, None]) < 0
        hi = np.where(inside, mid, hi)
        lo = np.where(inside, lo, mid)
    return np.where(found, hi, np.inf)


def _inside_run(shape, start, direction, reach=0.45, step=0.002):
    """Along a ray: where it first enters the surface and where it leaves it again, or None."""
    t = np.arange(step, reach, step)
    f = shape.field(start[None, :] + direction[None, :] * t[:, None])
    ins = np.nonzero(f < 0)[0]
    if not len(ins):
        return None
    b = ins[0]
    while b + 1 < len(t) and f[b + 1] < 0:
        b += 1
    return t[ins[0]], t[b]


def sleeve(shape, skin):
    """
    A mesh on the surface of [shape]: rings, from the neck's foot up. First the foot's rings (see Join; [skin]: the
    body's), the lowest on the body's skin (open: it is zipped to the skin's cut). Then, through the neck and the
    lower head, planes that all turn about a far line behind the figure, from the join's tilted plane to level at the
    hinge's height; each ring's middle lies on one line from the join's middle up into the head. With a tail: from
    the hinge's height on, over the brow and the top of the head to the tail, in half planes that all turn about the
    hinge (a line across, in the hollow between the skull and the tail: pages of a book), and from where the tail runs
    square to those on, square to the tail to its tip, which closes it. Without one: levels to the top, closed there.
    Each point of a ring is where a ray from the ring's middle, in the ring's plane, leaves the surface; the planes
    meet only in the air, so no ring crosses another. Round each ring the points lie closer together toward the face
    ([crowd]), so its planes are drawn finely enough to catch the light.
    Points (n x 3), faces, and the foot: a dict (ring: the open ring's points; count: how many points the foot has;
    tris, uvw: for each of those that lies on the skin, the skin's triangle under it and where in it; short, early:
    see Join.foot()).
    """
    sp = shape.spec
    around, hinge = sp["around"], np.array(sp["hinge"])
    turns = [math.pi / 2 + b - sp["crowd"] * math.sin(b)
             for b in (2 * math.pi * k / around - math.pi for k in range(around))]
    side, ahead = np.array([1.0, 0.0, 0.0]), np.array([0.0, -1.0, 0.0])
    centres, dirs = [], []

    def ring(c, u):
        for a in turns:
            centres.append(c)
            dirs.append(math.cos(a) * side + math.sin(a) * u)

    join = Join(sp)
    rings, tris, uvw, short, early = join.foot(shape, skin, turns)
    first = rings.reshape(-1, 3)
    foot = dict(ring=list(range(around)), count=len(first), tris=tris.ravel(), uvw=uvw.reshape(-1, 3), short=short,
                early=early)
    # the planes through the neck and the lower head: all through a far line behind, at the hinge's height (for an
    # upright neck, whose join is level, very far behind)
    behind = hinge[1] - 0.01 if sp["tail"] else 0.25              # in the air behind the head
    tilt = math.radians(max(sp["join"]["tilt"], 0.5))
    far = np.array([0.0, join.centre[1] + (hinge[2] - join.centre[2]) / math.tan(tilt), hinge[2]])
    run = _inside_run(shape, np.array([0.0, behind, hinge[2]]), ahead)
    spine = np.array([0.0, behind - (run[0] + run[1]) / 2, hinge[2]]) - join.centre
    reach = far[1] - behind + run[1]
    angle = tilt - sp["level_step"] / reach
    while angle > sp["level_step"] / reach / 2:
        normal = np.array([0.0, -math.sin(angle), math.cos(angle)])
        ring(join.centre + spine * (normal @ (far - join.centre)) / (normal @ spine),
             np.array([0.0, -math.cos(angle), -math.sin(angle)]))
        angle -= sp["level_step"] / reach
    # without a tail: levels from the hinge's height to the top
    z = hinge[2]
    while not sp["tail"] and z < shape.top - sp["level_step"] / 2:
        run = _inside_run(shape, np.array([0.0, behind, z]), ahead)
        if run is None:
            break
        ring(np.array([0.0, behind - (run[0] + run[1]) / 2, z]), ahead)
        z += sp["level_step"]
    if sp["tail"]:
        curve, tang = shape.curve, shape.tang
        # where the tail first runs square to the half planes: from there on, rings square to the tail
        dots = (tang * (curve - hinge)).sum(axis=1)
        j0 = int(np.nonzero((dots[:-1] < 0) & (dots[1:] >= 0))[0][0])
        rel = curve[j0] - hinge
        phi, phi_tail = math.pi, math.atan2(rel[2], rel[1])
        while phi > phi_tail:
            u = np.array([0.0, math.cos(phi), math.sin(phi)])
            run = _inside_run(shape, hinge, u)
            ring(hinge + u * (run[0] + run[1]) / 2, u)
            phi -= sp["page_step"] / max(run[1], 0.05)
        here = shape.s[j0]
        while here < shape.s[-1] - 0.003:
            i = min(np.searchsorted(shape.s, here), len(curve) - 1)
            w = np.cross(side, tang[i])
            ring(curve[i], w / np.linalg.norm(w))
            here += sp["ring_step"]
        end = curve[-1:]
    else:
        end = project(shape, np.array([[0.0, shape.front[-1, 0], shape.top]]), 12)
    p = np.vstack([first, _cast(shape, np.array(centres), np.array(dirs)), end])
    n_ring = (len(p) - 1) // around
    faces = [(j * around + k, j * around + (k + 1) % around, j * around + (k + 1) % around + around,
              j * around + k + around) for j in range(n_ring - 1) for k in range(around)]
    last = (n_ring - 1) * around
    faces += [(last + k, last + (k + 1) % around, len(p) - 1) for k in range(around)]
    # evened out a little over the surface (each point toward its neighbours' middle, along the surface); the foot and
    # the open ring stay as they are
    near = [set() for _ in p]
    for f in faces:
        for i in range(len(f)):
            near[f[i]].add(f[(i + 1) % len(f)])
            near[f[(i + 1) % len(f)]].add(f[i])
    near = [list(n) for n in near]
    fixed = foot["count"]
    for _ in range(sp["relax"]):
        mean = np.array([p[n].mean(axis=0) for n in near])
        g = shape.grad(p[fixed:])
        g /= np.maximum(np.linalg.norm(g, axis=1), 1e-9)[:, None]
        d = (mean - p)[fixed:]
        d -= (d * g).sum(axis=1)[:, None] * g
        p[fixed:] = project(shape, p[fixed:] + 0.5 * d, 3)
    return p, faces, foot


def eyes(shape, p, faces):
    """
    Sets the eyes of [shape] (see EYES, Eye) into the head's mesh ([p], [faces], from sleeve()): the mesh as it is
    after that (points, faces), and for each eye its own points' indices (the faces between them are the eye's lit
    surface; all else is skin).

    Round each eye the head's own faces go, over the whole socket and a little beyond its rim, and rings round the eye
    take their place, each of as many points, evenly apart: the first on the socket's rim, zipped to the hole's edge
    with triangles (zipper(), as a neck's foot is to a body's skin); then through the skin of the socket, square to
    the lids' edge, down to that edge, which is the almond's outline on the socket's floor, a clean line; from there
    a short wall straight back into the head, the lid's thickness; and at its foot the eye's own surface, ring by
    ring to its middle, each point lifted off the socket's floor the more the nearer the middle: rounded like the
    front of an eyeball, its edge behind the lids, its middle before its edge. (The socket's slopes are steeper than
    the head's mesh is fine: the rings draw them, the brow and the nose's flank, where the head's own faces would
    break them into flats.) The points the head's mesh no longer needs are taken out (they are never among the
    first, the neck's foot).
    """
    p = np.array(p)
    faces = [tuple(f) for f in faces]
    own = []
    for eye in shape.eyes:
        e = eye.e
        if e["rim"][0] + e["border"] >= e["at"][0]:
            raise ValueError("The eyes' sockets meet in the face's middle: the rim toward the nose, and the border "
                             "beyond it, have to end before it")
        u, v, w = eye.plane(p)
        near = (np.abs(w) < 0.05) & (eye.oval(u, v, [r + e["border"] for r in e["rim"]]) < 1.0)
        hole = [f for f in faces if near[list(f)].any()]
        faces = [f for f in faces if not near[list(f)].any()]
        # the hole's edge, run round as the faces that went ran round it
        sides = {(f[i], f[(i + 1) % len(f)]) for f in hole for i in range(len(f))}
        nxt = {a: b for a, b in sides if (b, a) not in sides}
        edge = [next(iter(nxt))]
        while nxt.get(edge[-1]) not in (edge[0], None) and len(edge) <= len(nxt):
            edge.append(nxt[edge[-1]])
        if len(edge) != len(nxt) or nxt.get(edge[-1]) != edge[0]:
            raise ValueError("The hole for an eye is not one clean ring: %d points, %d of them in the first ring"
                             % (len(nxt), len(edge)))
        edge = np.array(edge)
        # the lids' edge, run round the same way, and straight out of the eye from each of its points the socket's rim
        lids, way_out = eye.outline(e["around"])

        def turn(q):
            """Which way a ring of the eye's plane runs round."""
            return np.sign((q[:, 0] * np.roll(q[:, 1], -1) - np.roll(q[:, 0], -1) * q[:, 1]).sum())

        if turn(lids) != turn(np.stack([u[edge], v[edge]], axis=1)):
            lids, way_out = lids[::-1], way_out[::-1]
        lo, hi = np.zeros(len(lids)), np.full(len(lids), 4.0 * max(e["rim"]))
        for _ in range(24):
            mid = (lo + hi) / 2
            at = lids + way_out * mid[:, None]
            inside = eye.oval(at[:, 0], at[:, 1], e["rim"]) < 1.0
            lo, hi = np.where(inside, mid, lo), np.where(inside, hi, mid)
        rim = lids + way_out * lo[:, None]
        skin_rings, eye_rings = e["rings"]

        def on_floor(q, lift):
            """The points (u, v) on the socket's surface, [lift] before it."""
            origin = eye.point(q[:, 0], q[:, 1], np.full(len(q), 0.04))
            depth = _enter(shape.field, origin, np.tile(-eye.ahead, (len(q), 1)), reach=0.10, step=0.001)
            if not np.isfinite(depth).all():
                raise ValueError("An eye's socket is not on the head: %d points miss it" % (~np.isfinite(depth)).sum())
            return origin - eye.ahead * (depth - lift)[:, None]

        new, rings = [], []
        count = len(p)

        def add(points):
            nonlocal count
            new.append(points)
            rings.append(np.arange(count, count + len(points)))
            count += len(points)

        for k in range(skin_rings + 1):
            add(on_floor(rim + (lids - rim) * k / skin_rings, 0.0))
        lit = count
        for k in range(eye_rings):
            part = 1.0 - k / eye_rings
            add(on_floor(lids * part, e["bulge"] * (1.0 - part * part) - e["lid"]))
        new.append(on_floor(np.zeros((1, 2)), e["bulge"] - e["lid"]))
        n = len(lids)
        both = (edge, rings[0])
        faces += [tuple(int(both[r][i]) for r, i in tri) for tri in zipper(p[edge], new[0])]
        for a, b in zip(rings, rings[1:]):
            faces += [(int(a[i]), int(a[(i + 1) % n]), int(b[(i + 1) % n]), int(b[i])) for i in range(n)]
        faces += [(int(rings[-1][i]), int(rings[-1][(i + 1) % n]), count) for i in range(n)]
        own.append(np.arange(lit, count + 1))
        p = np.vstack([p] + new)
    # without the points no face needs any more
    used = np.zeros(len(p), dtype=bool)
    used[[i for f in faces for i in f]] = True
    index = np.cumsum(used) - 1
    faces = [tuple(int(index[i]) for i in f) for f in faces]
    return p[used], faces, [index[o] for o in own]


def triangles(p, faces):
    """
    The mesh's faces as triangles: each four-sided face cut along the diagonal that follows the surface better. (A
    face of a curved surface is twisted; cut one way its two halves make a small ridge, the other way a small groove,
    and only one of them is the surface's own bend there: left to chance, mirrored places come out unlike, and a
    fine ridge shows where the surface should be hollow.) The surface's direction at each point: the mean of the
    faces round it.
    """
    quads = np.array([f for f in faces if len(f) == 4])
    rest = [tuple(f) for f in faces if len(f) != 4]
    q = p[quads]
    normal = np.cross(q[:, 2] - q[:, 0], q[:, 3] - q[:, 1])
    at = np.zeros_like(p)
    for k in range(4):
        np.add.at(at, quads[:, k], normal)
    at /= np.maximum(np.linalg.norm(at, axis=1), 1e-12)[:, None]

    def fit(i, j, k):
        """How well the triangle of a quad's corners i, j, k lies in the surface."""
        n = np.cross(q[:, j] - q[:, i], q[:, k] - q[:, i])
        n /= np.maximum(np.linalg.norm(n, axis=1), 1e-12)[:, None]
        return (n * (at[quads[:, i]] + at[quads[:, j]] + at[quads[:, k]])).sum(axis=1)

    first = np.minimum(fit(0, 1, 2), fit(0, 2, 3)) >= np.minimum(fit(0, 1, 3), fit(1, 2, 3))
    out = []
    for f, way in zip(quads.tolist(), first):
        out += [(f[0], f[1], f[2]), (f[0], f[2], f[3])] if way else [(f[0], f[1], f[3]), (f[1], f[2], f[3])]
    return out + rest


def folds(shape, p, faces, skip=0):
    """How many faces lie against the surface (a ring folded over its neighbour), and where; the faces of the first
    [skip] points (a neck's foot, which is not on the head's own surface) and the small triangles that close the top
    or the tail's tip are not looked at."""
    F = np.array([f for f in faces if len(f) == 4 and min(f) >= skip])
    nrm = np.cross(p[F[:, 2]] - p[F[:, 0]], p[F[:, 3]] - p[F[:, 1]])
    cen = p[F].mean(axis=1)
    sign = np.sign((nrm * shape.grad(cen)).sum(axis=1))
    bad = sign != np.sign(np.median(sign))
    return int(bad.sum()), cen[bad]


def weights(shape, p, faces, skin, foot, under="neck", most=4, rounds=4):
    """
    Which bones move each point [p] of the new surface: {bone name: array}. The tail's joints along the tail, each
    blending into the next round its joint; the head, with its jaw and chin, wholly the head's; the neck less and less
    downward (neck_fade; the step from the jaw to the neck under it evened out over the mesh, [faces], [rounds]
    times), and for the rest the bone [under] the head's; and through the neck's foot ([foot], see sleeve()) more and
    more as the body's skin ([skin]: the mean of its few nearest points; where the foot lies on the skin, the skin's
    very place under the point): so the neck bends evenly and its foot goes with the skin it has become part of. At
    most [most] bones a point, the strongest, adding up to one.
    """
    sp = shape.spec
    out = {}
    still = np.ones(len(p))
    if sp["tail"]:
        _, idx = shape.tail(p, want_s=True)
        ts, js = shape.s[idx], shape.joint_s
        owner = np.clip(0.5 + (shape.head_and_neck(p) - shape.tail(p)) / 0.03, 0.0, 1.0)     # 1: the tail's

        def past(k):
            """How far past joint k (0: where the tail grows out) a point is, blended over a little either side."""
            if k == 0:
                return _smooth((ts - js[0]) / 0.06)
            b = 0.35 * min(js[k] - js[k - 1], js[k + 1] - js[k] if k + 1 < len(js) else 0.06)
            return _smooth((ts - js[k] + b) / (2 * b))

        for k in range(1, len(sp["tail"])):
            upper = past(k) if k + 1 < len(sp["tail"]) else np.zeros_like(ts)
            out[sp["tail"][k][0]] = owner * (past(k - 1) - upper)
        still = (1.0 - owner) + owner * (1.0 - past(0))
    down = np.zeros(len(p))
    if sp["neck"]:
        top, low = sp["neck_fade"]
        necks = _smooth(0.5 + (shape.head_distance(p) - shape.neck(p)) / 0.05)                # 1: the neck's
        down = necks * _smooth((top - p[:, 2]) / (top - low))
        ends = np.array([(f[i], f[(i + 1) % len(f)]) for f in faces for i in range(len(f))])
        count = np.bincount(ends[:, 0], minlength=len(p)) + 1.0
        for _ in range(rounds):
            total = down.copy()
            np.add.at(total, ends[:, 0], down[ends[:, 1]])
            down = total / count
    # below the head: the neck's own bone; and through the foot, from the join's plane down to the cut, more and more
    # as the body's skin: all of it, also what the head still moves there (under a chin the head's share reaches
    # down to the foot; left to the head at the cut, it would pinch the skin there when she nods)
    skins = np.zeros(len(p))
    rings = foot["count"] // sp["around"]
    if rings:
        skins[:foot["count"]] = np.repeat(_smooth((0.8 - np.arange(rings) / (rings - 1.0)) / 0.6), sp["around"])
    out["head"] = still * (1.0 - down) * (1.0 - skins)
    out[under] = still * down * (1.0 - skins)
    ask = np.nonzero(skins > 0.0)[0]
    if len(ask):
        d = np.linalg.norm(p[ask, None, :] - skin.points[None, skin.own, :], axis=2)
        beside = skin.table[np.nonzero(skin.own)[0][np.argsort(d, axis=1)[:, :4]]].mean(axis=1)
        placed = foot["tris"][ask] >= 0
        beside[placed] = skin.at(foot["tris"][ask][placed], foot["uvw"][ask][placed])
        for c, name in enumerate(skin.names):
            if beside[:, c].any():
                out.setdefault(name, np.zeros(len(p)))[ask] += still[ask] * skins[ask] * beside[:, c]
    order = list(out)
    table = np.stack([out[n] for n in order], axis=1)
    np.put_along_axis(table, np.argsort(table, axis=1)[:, :-most], 0.0, axis=1)
    table /= np.maximum(table.sum(axis=1), 1e-9)[:, None]
    return {name: table[:, c] for c, name in enumerate(order)}


def zipper(a, b):
    """
    Triangles between two closed rings of points that run round the same way ([a] n x 3, [b] m x 3): from the nearest
    pair on, always the shorter of the two next steps. Each triangle as three (ring, index) pairs, ring 0 or 1.
    """
    n, m = len(a), len(b)
    j0 = int(np.argmin(np.linalg.norm(b - a[0], axis=1)))
    i, j, out = 0, 0, []
    while i < n or j < m:
        ai, bj = a[i % n], b[(j0 + j) % m]
        step_a = np.linalg.norm(a[(i + 1) % n] - bj) if i < n else np.inf
        step_b = np.linalg.norm(b[(j0 + j + 1) % m] - ai) if j < m else np.inf
        if step_a <= step_b:
            out.append(((0, i % n), (0, (i + 1) % n), (1, (j0 + j) % m)))
            i += 1
        else:
            out.append(((0, i % n), (1, (j0 + j + 1) % m), (1, (j0 + j) % m)))
            j += 1
    return out


def tail_extras(spec, height, head, crown, skin=0.6):
    """The tail as the skeleton's extra points (see figure.BASE's extras): its first point on the crown, each next one
    on the one before, all but the first joints, with a thin skin of their own ([skin] of the tail's radius: the
    tail made round them hides it, and it is taken out). [head], [crown]: those points of the plain skeleton, and
    [height] the figure's, by which the extras' places are scaled."""
    out, prev = [], (np.array(crown) - np.array(head)) * height
    tail = spec["tail"]
    for i, (name, at, r) in enumerate(tail):
        at = np.array(at)
        parent = "crown" if i == 0 else tail[i - 1][0]
        out.append((name, parent, tuple((at - prev) / height), max(skin * r, 0.006), 1.0, i > 0))
        prev = at
    return out


# --- Blender ------------------------------------------------------------------------------------------------------

def build(w, spec, name):
    """
    The head of the figure being built ([w]: figure.Work) after [spec] (a character's description, see SPEC), as a
    mesh of its own in the body's skin's material (its eyes, where it brings them, in the figure's second one, the
    eyes'), weighted to the bones; the body's skin it replaces is taken out (with a join: cut along the join's last
    page, see Join). Called from Figure.back_of_head(), so the figure's plain eyes, where the head brings none, find
    the face it makes; a head with a join is made one surface with the body after that, by weld() (from Figure.hair()).
    """
    import bmesh
    import bpy

    origin = np.array(w.head.pos)
    shape = Shape(spec)
    # the body's skin as numbers
    body = w.body
    names = [g.name for g in body.vertex_groups]
    table = np.zeros((len(body.data.vertices), len(names)))
    for v in body.data.vertices:
        for g in v.groups:
            table[v.index, g.group] = g.weight
    tail_names = [t[0] for t in (spec["tail"] or [])[1:]]
    skin = Skin(np.array([tuple(v.co) for v in body.data.vertices]) - origin,
                [tuple(f.vertices) for f in body.data.polygons], names, table, apart=["head"] + tail_names)

    join = Join(spec)
    beyond, near = join.beyond(skin.points)
    skin.even(near)
    p, faces, foot = sleeve(shape, skin)
    n_bad, where = folds(shape, p, faces, foot["count"])
    if n_bad:
        print("WARNING", name, ": the head's mesh is folded at", n_bad, "faces, round",
              np.round(where[:3] + origin, 3))
    if foot["short"].max() > 0.0025:
        print("WARNING", name, ": the neck's foot is not yet the body's skin where it ends, by",
              round(foot["short"].max() * 1000, 1), "mm")
    if foot["early"] > 0.0005:
        print("WARNING", name, ": the foot's rounding reaches up to the join's plane, by",
              round(foot["early"] * 1000, 1), "mm")
    p, faces, own = eyes(shape, p, faces)
    lit = np.zeros(len(p), dtype=bool)
    for points in own:
        lit[points] = True
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata([tuple(v + origin) for v in p], [], triangles(p, faces))
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    # the skin's material, and the eyes' for the faces that lie between an eye's own points
    for material in body.data.materials[:2 if own else 1]:
        obj.data.materials.append(material)
    for poly in mesh.polygons:
        poly.use_smooth = True
        poly.material_index = 1 if lit[list(poly.vertices)].all() else 0
    for bone, wv in weights(shape, p, faces, skin, foot).items():
        g = obj.vertex_groups.get(bone) or obj.vertex_groups.new(name=bone)
        for i in np.nonzero(wv > 0.002)[0]:
            g.add([int(i)], float(wv[i]), 'REPLACE')

    # the body's skin the head replaces goes: its own tail, and what lies inside the join, cut along its last page
    gone = table[:, [names.index(n) for n in tail_names if n in names]].sum(axis=1) > 0.5
    bm = bmesh.new()
    bm.from_mesh(body.data)
    verts = list(bm.verts)
    bones = bm.verts.layers.deform.verify()

    def moved_by(v, row):
        """Sets which bones move a point of the skin: the four strongest of [row], adding up to one."""
        best = np.argsort(row)[-4:]
        v[bones].clear()
        for g in best[row[best] > 0.002]:
            v[bones][int(g)] = float(row[g] / row[best].sum())

    # what stays is the body's alone: no bone of the head moves it any more, and round the join its shares are evened
    for v, row, was in zip(verts, skin.table, table):
        if np.abs(row - was).max() > 1e-6:
            moved_by(v, row)
    inside = (beyond < 0) & near
    a, b = skin.edges[:, 0], skin.edges[:, 1]
    cross = ((inside[a] != inside[b]) & near[a] & near[b] & ~gone[a] & ~gone[b]
             & (np.abs(beyond[a] - beyond[b]) < 90))
    a, b = a[cross], b[cross]
    share = join.crossing(skin.points[a], skin.points[b])
    length = np.linalg.norm(skin.points[a] - skin.points[b], axis=1)
    share = np.clip(share, 0.001 / length, 1.0 - 0.001 / length)
    edge_of = {tuple(sorted((e.verts[0].index, e.verts[1].index))): e for e in bm.edges}
    cut = []
    for i, j, s in zip(a, b, share):
        # a new point on each edge the cut crosses, where it crosses it, moved as the edge's two ends in those shares
        _, v = bmesh.utils.edge_split(edge_of[(int(i), int(j))], verts[int(i)], float(s))
        v.co = tuple(origin + skin.points[i] + (skin.points[j] - skin.points[i]) * s)
        moved_by(v, skin.table[i] * (1 - s) + skin.table[j] * s)
        cut.append(v)
    on_cut = set(cut)
    for f in list(bm.faces):
        ends = [v for v in f.verts if v in on_cut]
        if len(ends) == 2:
            bmesh.utils.face_split(f, ends[0], ends[1])
        elif len(ends) > 2:
            print("WARNING", name, ": the join's cut crosses a face of the skin", len(ends), "times")
    bmesh.ops.delete(bm, geom=[verts[i] for i in np.nonzero(gone | inside)[0]], context='VERTS')
    # points of the cut that lie almost in one place are one
    bmesh.ops.remove_doubles(bm, verts=[v for v in cut if v.is_valid], dist=0.0025)
    bm.verts.index_update()
    w.join = dict(skin=_loop(bm), head=len(p), ring=foot["ring"])
    bm.to_mesh(body.data)
    bm.free()
    body.data.update()
    return obj


def refine(w, spec, cuts=2, rounds=3):
    """
    The body's skin round a join made finer (each edge there cut [cuts] times, the new points rounded out and evened
    [rounds] times), before the body is shaped: the skin of a trunk's top is a few large faces, and a neck's foot that
    lies on them, as fine as a head, would show every one of them as a flat. Call it first in a character's sculpt().
    """
    import bmesh

    join = Join(spec)
    body = w.body
    bm = bmesh.new()
    bm.from_mesh(body.data)
    h, r, c, s = join.round_the_neck(np.array([tuple(v.co) for v in bm.verts]) - np.array(w.head.pos))
    near = (r < 1.25 * join.hinge(c, s)[0]) & (h > -0.13) & (h < 0.03)
    edges = [e for e in bm.edges if near[e.verts[0].index] and near[e.verts[1].index]]
    made = bmesh.ops.subdivide_edges(bm, edges=edges, cuts=cuts, smooth=1.0, use_grid_fill=True)
    inner = [v for v in made["geom_inner"] if isinstance(v, bmesh.types.BMVert)]
    for _ in range(rounds):
        bmesh.ops.smooth_vert(bm, verts=inner, factor=0.5, use_axis_x=True, use_axis_y=True, use_axis_z=True)
    # (a point the cutting left on no edge would stop the steps that even the skin by its edges)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if not v.link_edges], context='VERTS')
    bm.to_mesh(body.data)
    bm.free()
    body.data.update()
    for poly in body.data.polygons:
        poly.use_smooth = True


def _loop(bm):
    """The open edge of a mesh as one ring: its points' indices in order (an error if it is not exactly one ring)."""
    nxt = {}
    for e in bm.edges:
        if e.is_boundary:
            for a, b in ((e.verts[0].index, e.verts[1].index), (e.verts[1].index, e.verts[0].index)):
                nxt.setdefault(a, []).append(b)
    if not nxt or any(len(v) != 2 for v in nxt.values()):
        raise ValueError("The skin's cut is not one clean ring: %d points, %d of them forked"
                         % (len(nxt), sum(len(v) != 2 for v in nxt.values())))
    start = next(iter(nxt))
    ring, prev = [start], None
    while True:
        here = ring[-1]
        step = nxt[here][0] if nxt[here][0] != prev else nxt[here][1]
        if step == start:
            break
        ring.append(step)
        prev = here
    if len(ring) != len(nxt):
        raise ValueError("The skin's cut is more than one ring: %d of %d points in the first" % (len(ring), len(nxt)))
    return ring


def weld(w):
    """
    Makes a head with a join one surface with the body: called when the head's mesh has become part of the body's
    (Figure.build() joins it after the eyes are set, so from Figure.hair()). The two open rings (the skin's cut and
    the head's lowest ring, both on the body's skin, see Join) are zipped with triangles; the shading and the weights
    run through, as both rings carry the skin's own.
    """
    import bmesh

    mesh = w.body.data
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bm.verts.ensure_lookup_table()
    base = len(bm.verts) - w.join["head"]
    rings = [[bm.verts[i] for i in w.join["skin"]], [bm.verts[base + i] for i in w.join["ring"]]]
    a, b = (np.array([tuple(v.co) for v in r]) for r in rings)

    def turn(r):
        """Which way a ring runs round, seen from above."""
        return np.sign(np.sum(r[:, 0] * np.roll(r[:, 1], -1) - np.roll(r[:, 0], -1) * r[:, 1]))

    if turn(a) != turn(b):
        rings[1].reverse()
        b = b[::-1]
    made = 0
    for tri in zipper(a, b):
        vs = [rings[r][i] for r, i in tri]
        f = bm.faces.new(vs)
        f.smooth = True
        f.material_index = 0
        made += 1
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    left = sum(1 for e in bm.edges if e.is_boundary)
    odd = sum(1 for e in bm.edges if len(e.link_faces) > 2)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()
    if left or odd:
        print("WARNING: the head and the body are not one closed surface:", left, "open edges,", odd,
              "edges of more than two faces (", len(rings[0]), "points of the skin's cut,", len(rings[1]),
              "of the head's ring,", made, "triangles between )")
