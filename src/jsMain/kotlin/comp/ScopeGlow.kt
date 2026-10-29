/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.ScopeGlow(strength: Double) = comp(
    ScopeGlow.Props(strength = strength)
) {
    ScopeGlow(it)
}

/**
 * The light of the scope's signal behind its trace: a soft glow in the trace's own colour, the brighter the louder the
 * signal ([MotorReadings.signal]). It fills its parent; laid under the scope. Its own component, so only it redraws
 * with the signal. [Props.strength] is how far the loudest signal takes it: the visual's hole 0.55, the code page's
 * screen less (the maintainer, 2026-10-29: "give the osci in the code song page the same glow as in the visual ...
 * maybe a bit more descent").
 */
class ScopeGlow(ctx: Ctx<Props>) : Component<ScopeGlow.Props>(ctx) {

    data class Props(
        val strength: Double,
    )

    companion object {
        /** The visual's hole: the glow fills it at a loud signal. */
        const val HOLE = 0.55

        /** The code page's screen: quieter, the spectrum under it stays to be read. */
        const val SCREEN = 0.3
    }

    /** The scope's signal now, 0 (silence) to 1 (full scale), smoothed. */
    private val signal by subscribingTo(MotorReadings.signal())

    override fun VDom.render() {
        val percent = ((signal ?: 0.0) * 100 * props.strength).roundToInt()

        div {
            attributes["aria-hidden"] = "true"
            css {
                put("position", "absolute")
                put("inset", "0")
                put("pointer-events", "none")
                put(
                    "background",
                    "radial-gradient(ellipse at center, " +
                        "color-mix(in srgb, var(--kl-scope-trace) $percent%, transparent) 0%, transparent 75%)",
                )
            }
        }
    }
}
