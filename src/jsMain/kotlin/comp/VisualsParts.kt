/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.ultra.html.css
import kotlinx.html.FlowContent
import kotlinx.html.div

/** The pieces the visuals' tiles are built from: cut corners and the glass over a display. */
object VisualsParts {

    /** A box with its four corners cut off at 45 degrees, [cut] px each way, as a `clip-path`. */
    fun cutCorners(cut: Int): String =
        "polygon(${cut}px 0, calc(100% - ${cut}px) 0, 100% ${cut}px, 100% calc(100% - ${cut}px), " +
            "calc(100% - ${cut}px) 100%, ${cut}px 100%, 0 calc(100% - ${cut}px), 0 ${cut}px)"

    /**
     * A clear glass dome over what lies under it (the maintainer, 2026-10-29: "Both need transparent glass domes that
     * react to the light"): its curve, a brighter band at its top and a darker rim at its foot; and the lamp mirrored in
     * it, a soft highlight that moves with the lamp's sway (`--kl-glass-x`, `--kl-glass-y`, set by [VisualsBoard]) and
     * grows with its light (`--kl-glass-light`). The middle stays clear; the mouse goes through it.
     */
    fun FlowContent.glassDome() {
        div(classes = "kl-glass-dome") {
            attributes["aria-hidden"] = "true"
            css {
                put("position", "absolute")
                put("inset", "0")
                put("pointer-events", "none")
                put(
                    "background",
                    listOf(
                        // The lamp mirrored, a wide soft glow (the maintainer, 2026-10-29: a small sharp one was "the wrong
                        // direction", "even wider than before"): where the lamp is, toward it from the middle; up is up
                        "radial-gradient(ellipse 60% 85% at calc(50% + var(--kl-glass-x, 0) * 22%) " +
                            "calc(32% - var(--kl-glass-y, 0) * 22%), " +
                            "rgba(255, 255, 255, calc(var(--kl-glass-light, 1) * 0.06)) 0%, transparent 75%)",
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
        }
    }
}
