# Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
# SPDX-License-Identifier: AGPL-3.0-or-later

from figure import Figure, MUSCLES, draw_chin, shape_bust, shape_face, shape_muscles, skull


class Sirene(Figure):
    """The frontwoman, who plays the trumpet (the song's "trommel"): small, a little athletic, a waist and hips."""
    key = "sirene"
    frame = dict(
        height=0.92, scale=dict(torso=0.94, waist=0.75, chest=0.86, shoulder=0.83, arm=0.80, hand=0.86, thigh=1.07,
                                leg=0.91, neck=0.76, head=0.98),
        shoulders=0.93, hips=1.08, neck=1.32, eye=(1.0, 0.24, 0.32), eye_size=1.0, eye_shape=(1.42, 0.6, 0.66),
        eye_drop=-0.012, long_skull=True,
        radius=dict(hips=1.20, ribs=0.88, hip=1.08, calf=1.05, bicep=1.08, crown=1.06),
        # a long neck, carried forward
        move=dict(head=(0, -0.022, 0), crown=(0, -0.030, 0.004), chin=(0, -0.022, -0.004),
                  # proud shoulders: level, not sloping, and held back a little
                  shoulderL=(0, 0.014, 0.026), shoulderR=(0, 0.014, 0.026)),
        extras=[("nose", "head", (0, -0.094, -0.014), 0.014, 1.0, False)],
    )

    def sculpt(self, w):
        # who has a bust has no chest muscle drawn over it
        shape_muscles(w.body, w.P, w.height, only=[m for m in MUSCLES if m != "pec"])
        shape_bust(w.body, w.P["chest"], w.height, apart=0.054, down=0.045, out=0.036, size=0.050)
        # a face that comes to a small chin, the throat set back under it
        shape_face(w.body, w.head, w.hs, taper=0.24, throat=0.030)
        # the chin sits low: drawn down more than forward, as large as it was (it pointed up, the maintainer,
        # 2026-11-17)
        draw_chin(w.body, w.head, w.hs, 0.7, drop=1.6, fore=0.6)

    def back_of_head(self, w):
        # her head after the long skulls of ancient Egyptian sculpture (the maintainer's picture, 2026-11-16): see
        # skull(). (A sea shell for a hat was tried and taken off: shell() in the history, see 0eb7378ad^.)
        return skull(w.head.pos, w.hs, w.body.data.materials[0])
