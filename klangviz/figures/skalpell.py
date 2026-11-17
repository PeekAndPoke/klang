# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, PICK_HAND, DARK, draw_chin, join_into, mane, material, shape_muscles, skull


class Skalpell(Figure):
    """The lead guitar: slim, a little stooped, long hair worn open."""
    key = "skalpell"
    frame = dict(
        height=1.02, scale=dict(torso=0.90, waist=0.88, chest=0.92, arm=0.88, thigh=0.86, leg=0.88),
        stoop=0.07, eye=(1.0, 0.32, 0.24), move=dict(PICK_HAND),
    )

    def sculpt(self, w):
        shape_muscles(w.body, w.P, w.height)
        draw_chin(w.body, w.head, w.hs, 1.0)

    def back_of_head(self, w):
        # a plain round back of the head: the hair alone left it flat
        return skull(w.head.pos, w.hs, w.body.data.materials[0], long=1.0, high=0.094, back=0.034, rise=0.022, tilt=8.0,
                     wide=0.086)

    def hair(self, w):
        # long hair: combed straight back over the skull as a pattern in the light (the show's Pattern.kt), and from
        # there one smooth mane down his back, see mane(). (Hanging locks were tried and not wanted: hair() in the
        # history, see 0eb7378ad^.)
        hair = material(self.key + "_hair", DARK)
        w.body.data.materials.append(hair)
        join_into(w.body, mane(w.head.pos, w.hs, hair, length=0.46))
