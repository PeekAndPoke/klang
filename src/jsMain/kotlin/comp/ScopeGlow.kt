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
 * with the signal. [Props.strength] is how far the loudest signal takes it: the visual's hole 0.85, the code page's
 * screen less (the maintainer, 2026-10-29: "give the osci in the code song page the same glow as in the visual ...
 * maybe a bit more descent").
 */
class ScopeGlow(ctx: Ctx<Props>) : Component<ScopeGlow.Props>(ctx) {

    data class Props(
        val strength: Double,
    )

    companion object {
        /** The visual's hole: the glow fills it at a loud signal (0.55 before it was flatter; "a bit brighter now"). */
        const val HOLE = 0.85

        /** The code page's screen: quieter, the spectrum under it stays to be read. */
        const val SCREEN = 0.3

        /** How much of the light is left at [FADE_AT] % of the way out. */
        private const val FADE_SHARE = 1.0 / 3.0

        /** Where a third of the light is left, in % of the way out. */
        private const val FADE_AT = 30

        /** Where the light is gone, in % of the way out. */
        private const val GONE_AT = 65
    }

    /** The scope's signal now, 0 (silence) to 1 (full scale), smoothed. */
    private val signal by subscribingTo(MotorReadings.signal())

    override fun VDom.render() {
        // At most the trace's own colour: a loud signal at a strong glow would ask for more than all of it
        val percent = ((signal ?: 0.0) * 100 * props.strength).roundToInt().coerceIn(0, 100)
        val fading = (percent * FADE_SHARE).roundToInt()

        div {
            attributes["aria-hidden"] = "true"
            css {
                put("position", "absolute")
                put("inset", "0")
                put("pointer-events", "none")
                // 65 % of the width and 40 % of the height to each side (the maintainer, 2026-10-29: "make the glow a
                // bit narrower", then "By narrow i mean narrower vertically. Please restore the horizontal extent",
                // then from 30 %: "now to narrow vertically ... bit more please", then from 53 %: "Make it wider
                // horizontally please"). The light falls off soon: a third of it left at 30 % of the way out, none
                // from 65 % ("the glow is too much ... maybe the gradient should fade out faster"; it faded evenly to
                // the edge)
                put(
                    "background",
                    "radial-gradient(ellipse 65% 40% at center, " +
                        "color-mix(in srgb, var(--kl-scope-trace) $percent%, transparent) 0%, " +
                        "color-mix(in srgb, var(--kl-scope-trace) $fading%, transparent) $FADE_AT%, " +
                        "transparent $GONE_AT%)",
                )
            }
        }
    }
}
