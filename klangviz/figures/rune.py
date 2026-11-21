# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from mathutils import Vector

import head
from figure import CENTRE, Figure, MUSCLES, shape_bust, shape_muscles
# the frontwoman's mittens and her way of setting the trunk's outline by height fit her too
from sirene import shape_hands, shape_line, smooth_region

# Her head and her tail of hair are one surface (the maintainer's picture, 2026-11-20): an egg of a head, broad at
# the skull and coming to a small chin, that grows out of a slender neck, its back sweeping up and back into one
# thick tail that arches over and falls behind her in a gentle S to the waist (built by head.py, see its SPEC for
# what each part means). In metres, from the head's point (where the bone "head" ends), y back, z up, x to her left.
#
# The head is drawn (the maintainer, 2026-11-21): two outlines of named points, DRAWN's profile and outline, which
# head.py runs a smooth curve through. To change the head, move a point; to try a change by eye, turn a dial (DIALS).

# The frontwoman's long skull, whose line from the brow over the top the maintainer likes: figure.skull()'s own
# measures at her size (its length, 0.098 x 1.30, and its height, 0.107, times her head's size, 0.9408; its tilt).
SKULL = dict(long=0.1199, high=0.1007, tilt=30.0)
# Rune's forehead and top follow it: the same egg, [scale] times as large, the tip of its forehead at [tip] (y, z),
# so that it stands on her brow and its top is as high as her head is.
FIT = dict(scale=0.90, tip=(-0.0954, -0.0455))


def on_skull(name, turn):
    """A point of Rune's profile on the frontwoman's skull as FIT lays it on her head: [turn] degrees round the egg
    from its forehead's tip (90: straight above its middle; see head.egg_line)."""
    tip, at = head.egg_line([0.0, turn], **SKULL)
    return (name,) + tuple(round(float(FIT["tip"][k] + FIT["scale"] * (at[k] - tip[k])), 4) for k in range(2))


DRAWN = dict(
    # the side outline, (name, y, z): up the face to the brow as it was (the eyes, the nose and the mouth sit in it);
    # the forehead and the top on the frontwoman's skull, to past its highest place; the crown, where the tail
    # leaves, a little above that skull's line, so the top runs into the tail without a dip; the back of the head,
    # which begins behind the nape and runs up and back in one line, standing out less than a ball would; and
    # forward under the skull to the throat (inside the neck)
    profile=[
        ("chin", -0.0870, -0.134),
        ("chin_front", -0.0930, -0.122),
        ("lip", -0.0995, -0.108),
        ("mouth", -0.1040, -0.092),
        ("nose_foot", -0.1020, -0.072),
        ("nose", -0.1050, -0.052),
        ("eyes", -0.1060, -0.032),
        ("brow", -0.1060, -0.012),
        on_skull("forehead_low", 38.0),
        on_skull("forehead", 56.0),
        on_skull("forehead_high", 76.0),
        on_skull("top_front", 97.0),
        on_skull("top", 118.0),
        on_skull("top_back", 140.0),
        ("crown", 0.0740, 0.0930),
        ("back_high", 0.1040, 0.062),
        ("back", 0.1200, 0.022),
        ("back_low", 0.1100, -0.018),
        ("back_begins", 0.0780, -0.062),
        ("skull_base", 0.0450, -0.088),
        ("under_jaw", 0.0150, -0.108),
        ("throat_high", -0.0250, -0.122),
        ("throat", -0.0460, -0.134),
    ],
    # the front outline's left half, (name, x, z): a small chin, a narrow jaw, cheeks that run in a nearly straight
    # line up to the cheekbones (finer than they were: the maintainer, 2026-11-21), the skull's round over them
    outline=[
        ("chin", 0.0220, -0.134),
        ("jaw_low", 0.0310, -0.122),
        ("jaw", 0.0400, -0.108),
        ("cheek", 0.0490, -0.092),
        ("cheek_high", 0.0600, -0.072),
        ("cheekbone", 0.0730, -0.052),
        ("eye_level", 0.0800, -0.032),
        ("temple", 0.0830, -0.012),
        ("skull", 0.0830, 0.012),
        ("skull_high", 0.0750, 0.045),
        ("crown_side", 0.0530, 0.080),
        ("top", 0.0, 0.0),
    ],
    # the face flatter than the back of the head: a plane with round corners at the brow, rounder under the
    # cheekbones and toward the chin
    widest=0.55, face_square=[(-0.040, 2.6), (-0.060, 2.5), (-0.080, 2.15), (-0.105, 2.05), (-0.134, 2.0)],
    back_square=2.1,
    # the face narrows evenly from the cheekbones to a small, pointed chin (the maintainer, 2026-11-21: no broad U),
    # which is rounded off below
    cap=0.0, chin_down=0.012,
    # the cheekbones, a low rise each under the eye's outer half, and a soft hollow under each
    swells=[
        ("cheekbone", 0.78, -0.055, 0.022, 0.014, 0.0020),
        ("cheek_hollow", 0.72, -0.082, 0.020, 0.018, -0.0030),
    ],
    # a narrow ridge of the nose down to its small tip, which catches a line of light; under it the plane of a closed
    # mouth, level, standing a little before the chin, and a small round of the chin: two low rises with a faint
    # hollow between them, no more than reads as stillness; no brows of their own (the eyes' sockets make them): a
    # still face
    features=[
        ((0.0, -0.1060, -0.034), (0.0055, 0.0065, 0.028), 0.010),
        ((0.0, -0.1075, -0.058), (0.0075, 0.0075, 0.0065), 0.008),
        ((0.0, -0.0945, -0.088), (0.016, 0.008, 0.010), 0.012),
        ((0.0, -0.0860, -0.124), (0.011, 0.008, 0.010), 0.010),
    ],
    # the eyes, below the middle of her face and far apart (the maintainer's picture, 2026-11-20): long almonds that
    # slant a little up toward the outside and reach round toward the temples, each in a soft hollow under a gentle
    # brow, the nose's ridge between them, a cheek's plane running out below (see head.EYES)
    eyes=dict(at=(0.040, -0.033), half=0.0215, upper=0.0105, lower=0.0085, slant=4.0, point=0.68,
              socket=0.006, floor=(0.024, 0.0115, 0.0225, 0.009), rim=(0.036, 0.021, 0.042, 0.034),
              lid=0.0012, bulge=0.0035),
    # the neck: an oval column, deeper than wide, leaning forward as it rises, from inside the chest up into the head;
    # and the nape, from under the back of the skull into its back
    neck=[
        (((0.0, -0.024, -0.100), (0.0, 0.018, -0.185), (0.0, 0.018, -0.300)), 0.0375, 1.20),
        (((0.0, 0.034, -0.070), (0.0, 0.032, -0.150)), 0.020),
    ],
    neck_soft=0.02, neck_blend=0.030, neck_fade=(-0.085, -0.115),
    # the join: its plane square to the neck, passing under the chin; the hinge in the air under the chin, beside the
    # neck over the shoulders and behind the nape
    join=dict(at=(0.003, -0.137), tilt=19.0, reach=(0.075, 0.110, 0.120), lift=(0.0, 0.030, 0.0),
              end=(80.0, 75.0, 50.0), soft=(0.008, 0.060, 0.008)),
    # the tail's joints (hair1 in the back of the skull, where the tail grows out; the last the tip) and its radius
    # round each; it twirls a little
    tail=[
        ("hair1", (0.0, 0.066, 0.050), 0.042),
        ("hair2", (0.0, 0.179, 0.066), 0.036),
        ("hair3", (0.0, 0.261, -0.027), 0.035),
        ("hair4", (0.0, 0.289, -0.137), 0.035),
        ("hair5", (0.0, 0.256, -0.247), 0.034),
        ("hair6", (0.0, 0.204, -0.350), 0.031),
        ("hair7", (0.0, 0.167, -0.447), 0.028),
        ("hair8", (0.0, 0.214, -0.544), 0.020),
        ("hair9", (0.0, 0.286, -0.637), 0.003),
    ],
    tail_blend=0.065, twirl=0.09, twirl_turn=10.0,
    hinge=(0.0, 0.175, -0.010),
)

# The dials: for tuning her head by eye (the maintainer turns them as sliders in a viewer made for it, KLANG-00478,
# and sends the values back). Each names what one whole turn of it (1.0) moves, as head.dialled() reads it: points
# of DRAWN's profile (by y, z) and outline (by x, z), swells (by across, z, half sizes, height) and features (their
# middle, by x, y, z). A whole turn is clearly too much, either way.
MOVES = dict(
    # the forehead rounder (a fuller dome, up and forward) or flatter (a slope)
    forehead_round=[("profile", "forehead_low", (-0.004, 0.003)), ("profile", "forehead", (-0.010, 0.008)),
                    ("profile", "forehead_high", (-0.010, 0.010)), ("profile", "top_front", (-0.004, 0.004))],
    # the top of the head higher or lower
    top_height=[("profile", "forehead_high", (0.0, 0.006)), ("profile", "top_front", (0.0, 0.011)),
                ("profile", "top", (0.0, 0.015)), ("profile", "top_back", (0.0, 0.014)),
                ("profile", "crown", (0.0, 0.010)),
                ("outline", "skull_high", (0.0, 0.005)), ("outline", "crown_side", (0.0, 0.010))],
    # where the back of the head begins behind the nape: higher or lower
    back_begins=[("profile", "back_begins", (0.0, 0.020)), ("profile", "back_low", (0.0, 0.008)),
                 ("profile", "skull_base", (0.0, 0.008))],
    # how far the back of the head stands out: more (a balloon) or less
    back_out=[("profile", "back_high", (0.012, 0.0)), ("profile", "back", (0.020, 0.0)),
              ("profile", "back_low", (0.020, 0.0)), ("profile", "back_begins", (0.010, 0.0))],
    # the cheeks fuller or finer
    cheeks=[("outline", "cheek_high", (0.006, 0.0)), ("outline", "cheek", (0.007, 0.0)),
            ("outline", "jaw", (0.004, 0.0)), ("swells", "cheek_hollow", (0.0, 0.0, 0.0, 0.0, 0.003))],
    # the cheekbones stronger or weaker
    cheekbones=[("swells", "cheekbone", (0.0, 0.0, 0.0, 0.0, 0.003)), ("outline", "cheekbone", (0.003, 0.0))],
    # the cheekbones higher or lower
    cheekbone_height=[("swells", "cheekbone", (0.0, 0.012, 0.0, 0.0, 0.0)),
                      ("swells", "cheek_hollow", (0.0, 0.008, 0.0, 0.0, 0.0)), ("outline", "cheekbone", (0.0, 0.008))],
    # the jaw wider or narrower
    jaw=[("outline", "chin", (0.004, 0.0)), ("outline", "jaw_low", (0.007, 0.0)), ("outline", "jaw", (0.008, 0.0)),
         ("outline", "cheek", (0.004, 0.0))],
    # the chin longer or shorter (the profile's two ends and the outline's first point go together)
    chin=[("profile", "chin", (0.0, -0.010)), ("profile", "chin_front", (0.0, -0.009)),
          ("profile", "lip", (0.0, -0.004)), ("profile", "throat", (0.0, -0.010)),
          ("profile", "throat_high", (0.0, -0.007)), ("outline", "chin", (0.0, -0.010)),
          ("outline", "jaw_low", (0.0, -0.008)), ("outline", "jaw", (0.0, -0.004)),
          ("features", 3, (0.0, 0.0, -0.009))],
    # the skull wider or narrower, seen from the front
    skull_width=[("outline", "eye_level", (0.003, 0.0)), ("outline", "temple", (0.006, 0.0)),
                 ("outline", "skull", (0.007, 0.0)), ("outline", "skull_high", (0.006, 0.0)),
                 ("outline", "crown_side", (0.004, 0.0))],
)
# How far each dial is turned (0: as drawn). The viewer's tool, rune_dials.py, writes a line copied from the viewer
# in here; once a head is settled, its dials' moves are written into DRAWN's points and the dials go back to 0.
DIALS = dict(
    forehead_round=0.0,
    top_height=0.0,
    back_begins=0.0,
    back_out=0.0,
    cheeks=0.0,
    cheekbones=0.0,
    cheekbone_height=0.0,
    jaw=0.0,
    chin=0.0,
    skull_width=0.0,
)

HEAD = head.spec_of(head.dialled(DRAWN, MOVES, DIALS))


class Rune(Figure):
    """The marimba: the oracle: still, long and narrow, an egg of a head with a face, and a long tail of hair."""
    key = "rune"
    frame = dict(
        height=0.97, scale=dict(torso=0.90, waist=0.74, chest=0.80, shoulder=0.62, arm=0.70, hand=0.95, thigh=1.0,
                                leg=0.80, neck=0.42, head=0.95),
        # (the skin's own head, crown and neck are small: the head and the upper neck she shows are made round them,
        # see head.py)
        radius=dict(hips=1.20, ribs=0.86, hip=1.06, calf=0.95, ankle=0.65, toe=0.78, head=0.5, crown=0.4),
        # (the shoulder joints 14.7 cm from her middle: 8 mm closer than they were, so the arms hang where the
        # maintainer's picture of 2026-11-20 has them)
        shoulders=0.797, hips=1.06, neck=1.15,
        # her eyes' light (the eyes themselves are part of her head: see HEAD's eyes)
        eye=(0.50, 0.18, 1.0),
        # a high-arched foot, as on invisible heels (as the frontwoman's): the ankle (and with it the heel) stands
        # high, the foot runs steeply down to the ball, which is on the floor, and the toes come to a point
        move=dict(ankleL=(0, 0.005, 0.050), ankleR=(0, 0.005, 0.050), toeL=(0, 0.080, -0.004), toeR=(0, 0.080, -0.004)),
        # the tail of hair: part of the skeleton, with joints of its own, so the show can swing it; the feet's tips and
        # heels and the mittens' fingers as the frontwoman has them
        extras=head.tail_extras(HEAD, 0.97, *(next(c[2] for c in CENTRE if c[0] == n) for n in ("head", "crown")))
        + [
            ("tipL", "toeL", (0.004, -0.060, -0.011), 0.010, 1.0, False),
            ("tipR", "toeR", (-0.004, -0.060, -0.011), 0.010, 1.0, False),
            ("heelL", "ankleL", (0.0, 0.032, -0.028), 0.019, 1.0, False),
            ("heelR", "ankleR", (0.0, 0.032, -0.028), 0.019, 1.0, False),
            # the mitten's fingers, beyond the hand's joint: a hand as long as a hand is (the joint stays put)
            ("fingersL", "handL", (0, -0.004, -0.035), 0.017, 0.55, False),
            ("fingersR", "handR", (0, -0.004, -0.035), 0.017, 0.55, False)],
    )

    def sculpt(self, w):
        # the skin round the neck's base as fine as the neck that will grow out of it
        head.refine(w, HEAD)
        # who has a bust has no chest muscle drawn over it; and no shoulder muscle: narrow, sloping shoulders
        shape_muscles(w.body, w.P, w.height, only=[m for m in MUSCLES if m not in ("pec", "deltoid")])
        # a small bust: she is long and narrow (it was fuller: the maintainer, 2026-11-17; the picture of 2026-11-20
        # has it small)
        shape_bust(w.body, w.P["chest"], w.height, apart=0.058, down=0.046, out=0.028, size=0.054)
        # narrow shoulders that slope softly from a long, slender neck (the slope rises to the neck's foot, as the
        # maintainer's picture has it) and end round (the slope stops short of the shoulder's end), its edge evened
        # out
        shape_slope(w.body, neck=(0.045, 1.455), shoulder=(0.172, 1.400), bend=1.0)
        for sx in (1.0, -1.0):
            smooth_region(w.body, Vector((sx * 0.10, w.P["chest"].pos.y, 1.43)), 0.06, 8)
            smooth_region(w.body, Vector((sx * 0.155, w.P["chest"].pos.y, 1.41)), 0.05, 8)
            # and the arm's root: the groove between the chest (and the back) and the arm ends low, at the armpit
            for y in (-0.040, 0.045):
                smooth_region(w.body, Vector((sx * 0.130, w.P["chest"].pos.y + y, 1.365)), 0.065, 12)
        # long, slim thighs (at heights in metres: the width there against what the skin made)
        shape_line(w.body, [(0.92, 1.0), (0.86, 0.965), (0.80, 0.885), (0.74, 0.855), (0.66, 0.875), (0.58, 0.93),
                            (0.50, 1.0)])
        shape_hands(w.body, w.P)

    def back_of_head(self, w):
        # her head, her neck and her tail, as one surface (see head.py)
        return head.build(w, HEAD, "rune_head")

    def eyes(self, w, p, back):
        # her eyes lie in her head's own surface: head.build() has made them
        pass

    def hair(self, w):
        # the head is part of the body's mesh by now: its neck's foot and the body's skin become one surface
        head.weld(w)


def shape_slope(body, neck, shoulder, bend=1.0, give=0.015, fade_out=0.04):
    """
    The line from the neck down toward the shoulders, seen from the front: a slope from [neck] to [shoulder] (each the
    distance from her middle and the height, in metres), steep at the neck and flatter outward as [bend] is above 1
    (1: straight), level between the two sides under the neck; the skin above it is brought down toward [give] below
    the line (the skin's own stump of a neck with it: the neck she shows is made by head.py and stands on this),
    fading out over [fade_out] beyond the slope's end, where the shoulder's own round end takes over. The arms are left
    as they are.
    """
    arms = {body.vertex_groups[n + s].index for n in ("elbow", "wrist", "hand") for s in "LR"}
    (x0, z0), (x1, z1) = neck, shoulder

    def smooth(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)

    for v in body.data.vertices:
        ax = abs(v.co.x)
        if ax > x1 + fade_out or v.co.z > z0 + 0.08 or v.co.z < z1 - 0.04:
            continue
        line = z1 + (z0 - z1) * max(0.0, min(1.0, (x1 - ax) / (x1 - x0))) ** bend
        over = v.co.z - (line - give)
        if over <= 0:
            continue
        arm = min(1.0, sum(g.weight for g in v.groups if g.group in arms))
        v.co.z -= 0.8 * over * smooth((x1 + fade_out - ax) / fade_out) * (1.0 - arm)
