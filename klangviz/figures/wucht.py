# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, draw_chin, shape_muscles


class Wucht(Figure):
    """The drums: the muscular one: wide shoulders, a deep chest over a small waist, heavy arms, a thick neck."""
    key = "wucht"
    frame = dict(
        height=1.04, scale=dict(torso=1.08, waist=1.02, chest=1.30, shoulder=1.55, arm=1.55, hand=1.25, thigh=1.25,
                                leg=1.25, neck=1.7, head=1.24),
        # a head to match the frame: a quarter bigger, on a real neck: short and thick, but a neck (sunk into the
        # shoulders he had none, the maintainer, 2026-11-16)
        move=dict(crown=(0, 0, 0.004)),
        radius=dict(bicep=1.25, ribs=1.18), shoulders=1.30, hips=1.05, neck=1.25, stoop=0.03,
        eye=(1.0, 0.46, 0.22),
    )

    def sculpt(self, w):
        shape_muscles(w.body, w.P, w.height)
        # a strong jaw
        draw_chin(w.body, w.head, w.hs, 1.5)
