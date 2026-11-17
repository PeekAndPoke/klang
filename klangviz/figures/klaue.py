# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, PICK_HAND, flatten_face, shape_face, shape_muscles


class Klaue(Figure):
    """The rhythm guitar: the shorter, heavier one: a belly, a broad face, a flat cap."""
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
        hips=1.2, shoulders=1.06, neck=1.06, eye=(0.15, 1.0, 0.20),
        extras=[("cap", "crown", (0, 0.0, 0.035), 0.088, 1.15, False),
                ("brim", "cap", (0, -0.13, -0.015), 0.022, 1.0, False)],
    )

    def sculpt(self, w):
        shape_muscles(w.body, w.P, w.height)
        # a chin seen from the side: the throat is set back under it, so the chest no longer runs straight up into
        # the face (the maintainer, 2026-11-17: "we need a little Kinn when seen from the side"). Nothing is drawn
        # out of the face for it: a chin pulled forward left a hollow above it that read as a mouth wide open
        shape_face(w.body, w.head, w.hs, taper=0.0, throat=0.040)
        # and the face stays flat: the hollow between the head and the chin under it is filled
        flatten_face(w.body, w.head, w.hs)
