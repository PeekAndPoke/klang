# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, PICK_HAND, draw_chin, shape_muscles


class Beben(Figure):
    """The bass: the tall thin one, a long neck, stooped."""
    key = "beben"
    frame = dict(
        height=1.10, scale=dict(torso=0.80, waist=0.78, chest=0.82, shoulder=0.85, arm=0.78, thigh=0.76, leg=0.80,
                                neck=0.85, head=0.94),
        shoulders=0.92, hips=0.88, neck=1.35, stoop=0.11, eye=(0.15, 0.95, 1.0), eye_size=0.9,
        eye_drop=0.016,                      # his eyes sit lower in the face
        move=dict(PICK_HAND),
    )

    def sculpt(self, w):
        shape_muscles(w.body, w.P, w.height)
        draw_chin(w.body, w.head, w.hs, 1.0)
