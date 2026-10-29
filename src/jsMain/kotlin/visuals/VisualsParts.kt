/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.ultra.html.css
import kotlinx.html.FlowContent
import kotlinx.html.div

/** The pieces the visuals' tiles are built from: cut corners and the glass over a display. */
object VisualsParts {

    /** A box with its four corners cut off at 45 degrees, [cut] px each way, as a `clip-path`. */
    fun cutCorners(cut: Int): String =
        "polygon(${cut}px 0, calc(100% - ${cut}px) 0, 100% ${cut}px, 100% calc(100% - ${cut}px), " +
            "calc(100% - ${cut}px) 100%, ${cut}px 100%, 0 calc(100% - ${cut}px), 0 ${cut}px)"

    /** The class of a dome's lamp highlight: the board moves these ([highlightTransform], [highlightOpacity]). */
    const val HIGHLIGHT = "kl-glass-highlight"

    /** How far the highlight follows the lamp, as a share of the dome: at the sway's end, this far off the middle. */
    private const val FOLLOW = 22.0

    /** The highlight's size and resting middle, in per cent of the dome: the lamp's soft patch of old. */
    private const val HIGHLIGHT_WIDTH = 120.0
    private const val HIGHLIGHT_HEIGHT = 170.0
    private const val HIGHLIGHT_MIDDLE_Y = 32.0

    /** The highlight's brightest, at twice the lamp's rest; its opacity scales it down. */
    private const val HIGHLIGHT_ALPHA = 0.12

    /**
     * The highlight's transform for the lamp at [swayX], [swayY] in its sway (-1 to 1, up positive): toward the lamp
     * from the middle, up is up. A translate's per cent is of the highlight's own size, so the dome's [FOLLOW] per cent
     * is turned into the highlight's.
     */
    fun highlightTransform(swayX: Double, swayY: Double): String {
        val x = swayX * FOLLOW * 100.0 / HIGHLIGHT_WIDTH
        val y = -swayY * FOLLOW * 100.0 / HIGHLIGHT_HEIGHT

        return "translate($x%, $y%)"
    }

    /** The highlight's opacity for the lamp's [level] (1 at rest): its old brightness at rest, twice that at the most. */
    fun highlightOpacity(level: Double): Double = (level / 2.0).coerceIn(0.0, 1.0)

    /**
     * A clear glass dome over what lies under it (the maintainer, 2026-10-29: "Both need transparent glass domes that
     * react to the light"): its curve, a brighter band at its top and a darker rim at its foot, painted once; and the
     * lamp mirrored in it, a soft highlight ([HIGHLIGHT]) that the board moves with the lamp's sway and brightens with
     * its light. The highlight is a layer of its own, moved by its transform and its opacity only, so the glass is
     * never painted again while the lamp swings ("we simulate the lights on the displays", the caricature's way). The
     * middle stays clear; the mouse goes through it.
     */
    fun FlowContent.glassDome() {
        div(classes = "kl-glass-dome") {
            attributes["aria-hidden"] = "true"
            css {
                put("position", "absolute")
                put("inset", "0")
                put("overflow", "hidden")
                put("pointer-events", "none")
                put(
                    "background",
                    listOf(
                        // The dome round all the way (the maintainer, 2026-10-29: "or maybe even more round ... more
                        // domed", after a flatter try): an arc of light along its top, a faint glow at its foot where
                        // the light comes through, and its edges darker all round, the middle clear; the reflections
                        // quiet ("still too much reflection": the lamp's 0.14 and the arc's 0.14 before)
                        "radial-gradient(ellipse 75% 45% at 50% 0%, rgba(255, 255, 255, 0.08) 0%, transparent 70%)",
                        "radial-gradient(ellipse 60% 30% at 50% 100%, rgba(255, 255, 255, 0.05) 0%, transparent 70%)",
                        "radial-gradient(ellipse 70% 75% at 50% 50%, transparent 45%, rgba(0, 0, 0, 0.5) 100%)",
                    ).joinToString(", "),
                )
                // The shoulders: the glass rolling away along every edge (the maintainer, 2026-10-29: "more domed toward
                // the edges"), the fine bright line on its upper edge
                put("box-shadow", "inset 0 0 18px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.18)")
            }

            // The lamp mirrored, a wide soft glow (the maintainer, 2026-10-29: a small sharp one was "the wrong
            // direction", "even wider than before"): the size of the old one, its middle a third down, moved and lit by
            // the board
            div(classes = HIGHLIGHT) {
                css {
                    put("position", "absolute")
                    put("left", "${50.0 - HIGHLIGHT_WIDTH / 2}%")
                    put("top", "${HIGHLIGHT_MIDDLE_Y - HIGHLIGHT_HEIGHT / 2}%")
                    put("width", "$HIGHLIGHT_WIDTH%")
                    put("height", "$HIGHLIGHT_HEIGHT%")
                    put("background", "radial-gradient(closest-side, rgba(255, 255, 255, $HIGHLIGHT_ALPHA) 0%, transparent 75%)")
                    put("opacity", "0.5")
                    put("will-change", "transform, opacity")
                }
            }
        }
    }
}
