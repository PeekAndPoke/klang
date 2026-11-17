# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, MUSCLES, draw_chin, shape_bust, shape_muscles


class Rune(Figure):
    """The marimba: a woman: narrow shoulders, a waist, hips, long legs, a long tail of hair."""
    key = "rune"
    frame = dict(
        height=0.97, scale=dict(torso=0.90, waist=0.74, chest=0.80, shoulder=0.72, arm=0.70, hand=0.82, thigh=1.0,
                                leg=0.80, neck=0.72, head=0.95),
        radius=dict(hips=1.20, ribs=0.86, hip=1.06, calf=0.95), shoulders=0.84, hips=1.06, neck=1.15,
        eye=(0.4, 1.0, 0.8), eye_size=1.0,
        # the tail of hair: part of the skeleton, with joints of its own, so the show can swing it
        extras=[("hair1", "crown", (0, 0.085, 0.015), 0.062, 1.0, False),
                ("hair2", "hair1", (0, 0.06, -0.14), 0.050, 1.0, True),
                ("hair3", "hair2", (0, 0.02, -0.18), 0.040, 1.0, True),
                ("hair4", "hair3", (0, 0.01, -0.18), 0.026, 1.0, True),
                ("hair5", "hair4", (0, 0.02, -0.14), 0.010, 1.0, True)],
    )

    def sculpt(self, w):
        # who has a bust has no chest muscle drawn over it
        shape_muscles(w.body, w.P, w.height, only=[m for m in MUSCLES if m != "pec"])
        shape_bust(w.body, w.P["chest"], w.height, apart=0.056, down=0.045, out=0.038, size=0.052)
        draw_chin(w.body, w.head, w.hs, 1.0)
