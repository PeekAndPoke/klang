# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

import math

from mathutils import Vector

from figure import Figure, MUSCLES, draw_chin, shape_bust, shape_face, shape_muscles, skull


class Sirene(Figure):
    """The frontwoman, who plays the trumpet (the song's "trommel"): small, a little athletic, a waist and hips."""
    key = "sirene"
    frame = dict(
        height=0.92, scale=dict(torso=0.94, waist=0.72, chest=0.83, shoulder=0.80, arm=0.69, hand=1.0, thigh=1.10,
                                leg=0.88, neck=0.62, head=0.98),
        shoulders=0.90, hips=1.08, neck=1.32, eye=(1.0, 0.24, 0.32), eye_size=1.0, eye_shape=(1.42, 0.6, 0.66),
        eye_drop=-0.012, long_skull=True,
        radius=dict(hips=1.32, ribs=0.78, hip=1.12, calf=1.12, knee=0.88, ankle=0.65, toe=0.78, thigh=0.92, wrist=1.10, forearm=1.06, palm=0.92, hand=1.15, elbow=0.92, bicep=1.08,
                    crown=1.06),
        # a long neck, carried forward
        move=dict(head=(0, -0.022, 0), crown=(0, -0.030, 0.004), chin=(0, -0.022, -0.004),
                  # proud shoulders: level, not sloping, and held back a little
                  shoulderL=(0, 0.020, 0.030), shoulderR=(0, 0.020, 0.030),
                  # a proud chest: carried forward and a little up over the waist
                  chest=(0, -0.012, 0.0), ribs=(0, -0.006, 0.0),
                  # the legs come together toward the knees (a woman's thighs run inward), the feet closer
                  thighL=(-0.012, 0, 0), thighR=(0.012, 0, 0), kneeL=(-0.016, 0, 0), kneeR=(0.016, 0, 0), calfL=(-0.018, 0, 0.020), calfR=(0.018, 0, 0.020),
                  # a high-arched foot, as on invisible heels: the ankle (and with it the heel) stands high, the
                  # foot runs steeply down to the ball, which is on the floor, and the toes come to a point
                  ankleL=(-0.020, 0.005, 0.050), ankleR=(0.020, 0.005, 0.050),
                  toeL=(-0.025, 0.080, -0.004), toeR=(0.025, 0.080, -0.004)),
        extras=[("nose", "head", (0, -0.094, -0.014), 0.014, 1.0, False),
                ("tipL", "toeL", (0.004, -0.060, -0.011), 0.011, 1.0, False),
                ("tipR", "toeR", (-0.004, -0.060, -0.011), 0.011, 1.0, False),
                ("heelL", "ankleL", (0.0, 0.032, -0.028), 0.021, 1.0, False),
                ("heelR", "ankleR", (0.0, 0.032, -0.028), 0.021, 1.0, False),
                # the mitten's fingers, beyond the hand's joint: a hand as long as a hand is (the joint stays put)
                ("fingersL", "handL", (0, -0.004, -0.035), 0.019, 0.55, False),
                ("fingersR", "handR", (0, -0.004, -0.035), 0.019, 0.55, False)],
    )

    def sculpt(self, w):
        # who has a bust has no chest muscle drawn over it
        shape_muscles(w.body, w.P, w.height, only=[m for m in MUSCLES if m not in ("pec", "glute")], gain=dict(calf=1.25))
        # her seat: round and high, not wide (the shared buttock sits lower and spreads more)
        shape_seat(w.body, w.P)
        shape_bust(w.body, w.P["chest"], w.height, apart=0.056, down=0.046, out=0.042, size=0.056)
        # a face that comes to a small chin, the throat set back under it
        shape_face(w.body, w.head, w.hs, taper=0.24, throat=0.030)
        # the chin sits low: drawn down more than forward, as large as it was (it pointed up, the maintainer,
        # 2026-11-17)
        draw_chin(w.body, w.head, w.hs, 0.7, drop=1.6, fore=0.6)
        # a long, smooth line from the waist to the hips: the trunk a little wider where the waist flares into
        # the hips (the widest at the hip joints, not below), at heights in metres
        shape_line(w.body, [(1.16, 1.0), (1.10, 0.97), (1.06, 1.04), (1.00, 1.07), (0.95, 1.06), (0.90, 1.03),
                            (0.85, 1.0), (0.80, 0.975), (0.74, 0.955), (0.66, 0.95), (0.58, 1.0)])
        shape_hands(w.body, w.P)
        # the neck's base runs smoothly into the shoulders (the skin left a crease there)
        chest = w.P["chest"].pos
        smooth_region(w.body, Vector((0.0, chest.y, w.P["neck"].pos.z - 0.035)), 0.075, 6)
        # and the seat runs softly into the back of the thigh
        for side in ("L", "R"):
            hip = w.P["hip" + side].pos
            smooth_region(w.body, hip + Vector((0.4 * hip.x, 0.085, -0.115)), 0.065, 5)

    def back_of_head(self, w):
        # her head after the long skulls of ancient Egyptian sculpture (the maintainer's picture, 2026-11-16): see
        # skull(). (A sea shell for a hat was tried and taken off: shell() in the history, see 0eb7378ad^.)
        return skull(w.head.pos, w.hs, w.body.data.materials[0])


def shape_hands(body, P):
    """
    Rough hands, a mitten each: the flat of the hand turned to face the thigh (the skin is made flat front to back,
    as a hand that hangs is not: here it is thin across and wide front to back), and a hint of a thumb on its front.
    """
    def smooth(x):
        x = min(1.0, max(0.0, x))
        return x * x * (3 - 2 * x)

    for side, sx in (("L", 1.0), ("R", -1.0)):
        # only the hand's own skin (the thigh beside it is near enough to be caught)
        # (the wrist's own skin too, where it meets the hand: the wrist widens into the hand)
        groups = (body.vertex_groups["hand" + side].index, body.vertex_groups["wrist" + side].index)
        own = {}
        for v in body.data.vertices:
            for g in v.groups:
                if g.group in groups:
                    own[v.index] = min(1.0, own.get(v.index, 0.0) + g.weight)
        wrist, palm, tip = P["wrist" + side].pos, P["palm" + side].pos, P["fingers" + side].pos
        axis = tip - wrist
        length = axis.length
        axis = axis / length
        for v in body.data.vertices:
            if own.get(v.index, 0.0) < 0.01:
                continue
            d = v.co - wrist
            t = d.dot(axis)
            if t < -0.045 or t > length + 0.04:
                continue
            c = wrist + axis * max(0.0, min(length, t))
            off = v.co - c
            if off.length > 0.07:
                continue
            k = smooth((t + 0.040) / 0.075) * own[v.index]   # from nothing above the wrist to all of it on the hand
            v.co.x = c.x + off.x * (1.0 - 0.25 * k)
            v.co.y = c.y + off.y * (1.0 + 0.70 * k)
        # the thumb: a soft rise on the front of the hand, toward the thigh, from the palm's top downward
        centre = palm + Vector((-sx * 0.006, -0.030, -0.004))
        for v in body.data.vertices:
            d = v.co - centre
            q = (d.x / 0.012) ** 2 + (d.y / 0.016) ** 2 + (d.z / 0.032) ** 2
            if q < 9.0 and v.co.y < palm.y and own.get(v.index, 0.0) > 0.5:
                v.co += Vector((-sx * 0.2, -1.0, 0.0)).normalized() * (0.014 * math.exp(-q / 2))


def smooth_region(body, centre, radius, times):
    """Evens the skin within [radius] of [centre] [times] over, most at the middle, fading to nothing at the edge."""
    mesh = body.data
    near = {}
    for e in mesh.edges:
        a, b = e.vertices
        near.setdefault(a, []).append(b)
        near.setdefault(b, []).append(a)
    inside = [v.index for v in mesh.vertices if (v.co - centre).length < radius]
    for _ in range(times):
        moved = {}
        for i in inside:
            v = mesh.vertices[i]
            mean = sum((mesh.vertices[j].co for j in near[i]), Vector()) / len(near[i])
            w = 1.0 - (v.co - centre).length / radius
            moved[i] = v.co + (mean - v.co) * 0.5 * min(1.0, 2.0 * w)
        for i, co in moved.items():
            mesh.vertices[i].co = co


def shape_line(body, profile):
    """
    The trunk's outline seen from the front and the back, set by height: at each height of [profile] (metres, from
    the top down) its width is that many times what the skin made, in between it runs smoothly from one to the
    next; outside the profile nothing changes. The arms (and the hands beside the hips) are left as they are.
    """
    arms = {body.vertex_groups[n + s].index for n in ("elbow", "wrist", "hand") for s in "LR"}
    zs = [z for z, _ in profile]

    def factor(z):
        if z >= zs[0] or z <= zs[-1]:
            return 1.0
        for (z0, f0), (z1, f1) in zip(profile, profile[1:]):
            if z1 <= z <= z0:
                t = (z0 - z) / (z0 - z1)
                t = t * t * (3 - 2 * t)
                return f0 + (f1 - f0) * t
        return 1.0

    for v in body.data.vertices:
        if any(g.group in arms and g.weight > 0.01 for g in v.groups):
            continue
        f = factor(v.co.z)
        if f != 1.0:
            v.co.x *= f


def shape_seat(body, P, gain=0.85, lift=0.15, narrow=0.88):
    """
    The buttocks as the shared "glute" of MUSCLES draws them, but [lift] (in the hip's radius) higher, a little
    narrower across ([narrow] of its width) and [gain] of its height: a round, high seat.
    """
    point, offset, direction, height, size, half = MUSCLES["glute"]
    for side, sx in (("L", 1.0), ("R", -1.0)):
        at = P[point + side]
        R = at.radius
        centre = at.pos + Vector((sx * offset[0], offset[1], offset[2] + lift)) * R
        rise = Vector((sx * direction[0], direction[1], direction[2])).normalized() * (height * R * gain)
        sizes = (size[0] * narrow, size[1], size[2])
        for v in body.data.vertices:
            d = v.co - centre
            q = (d.x / (sizes[0] * R)) ** 2 + (d.y / (sizes[1] * R)) ** 2 + (d.z / (sizes[2] * R)) ** 2
            if q > 9.0 or v.co.y < at.pos.y:
                continue
            v.co += rise * math.exp(-q / 2)
