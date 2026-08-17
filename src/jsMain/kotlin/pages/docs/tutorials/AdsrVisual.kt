/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgPath
import io.peekandpoke.klang.ui.svgPolyline
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.klang.ui.svgText
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.maxWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.max

@Suppress("FunctionName")
fun Tag.AdsrVisual(
    value: String,
    label: String = "loudness",
) = comp(
    AdsrVisual.Props(value = value, label = label)
) {
    AdsrVisual(it)
}

/**
 * Schematic envelope diagram for a [Block.Visual.Adsr] block, drawn from the
 * same "a:d:s:r" colon string the neighbouring code uses (drift is lint-enforced
 * in TutorialCurriculumSpec). Deliberately schematic: straight segments and
 * proportional-ish widths draw the mental model — attack up, decay down to the
 * sustain level, hold, release out — never the engine's actual curves.
 */
class AdsrVisual(ctx: Ctx<Props>) : Component<AdsrVisual.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val value: String,
        val label: String,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val parts = props.value.split(":").map { it.trim().toDoubleOrNull() }

        // Malformed values render nothing — the curriculum lint catches them at build time
        if (parts.size != 4 || parts.any { it == null }) return

        val attack = max(0.0, parts[0]!!)
        val decay = max(0.0, parts[1]!!)
        val sustain = parts[2]!!.coerceIn(0.0, 1.0)
        val release = max(0.0, parts[3]!!)

        // Geometry (viewBox units)
        val w = 320.0
        val h = 92.0
        val padL = 24.0 // room for the rotated axis label
        val padR = 8.0
        val padT = 8.0
        val drawH = 60.0
        val floorY = padT + drawH
        val lettersY = floorY + 14.0

        val drawW = w - padL - padR
        val holdW = drawW * 0.30 // the hold has no duration parameter — fixed share
        val flexW = drawW - holdW

        // Attack/decay/release widths: proportional to their seconds, with a
        // minimum so near-zero times stay visible as a (near-vertical) edge
        val minW = 3.0
        val total = attack + decay + release
        fun segW(t: Double): Double {
            val share = if (total > 0.0) t / total else 1.0 / 3.0
            return minW + (flexW - 3 * minW) * share
        }

        val x0 = padL
        val x1 = x0 + segW(attack)
        val x2 = x1 + segW(decay)
        val x3 = x2 + holdW
        val x4 = x3 + segW(release)

        val sustainY = padT + (1.0 - sustain) * drawH

        div {
            css {
                maxWidth = 420.px
                marginTop = 0.75.rem
                marginBottom = 0.75.rem
            }

            svgRoot(viewBox = "0 0 $w $h") {
                // Baseline (silence / resting level)
                svgLine(x0, floorY, x0 + drawW, floorY, stroke = laf.overlayBackground, strokeWidth = "1")

                // Filled area under the envelope
                svgPath(
                    d = "M $x0 $floorY L $x1 $padT L $x2 $sustainY L $x3 $sustainY L $x4 $floorY Z",
                    fill = laf.accent,
                    opacity = "0.15",
                )

                // The envelope itself
                svgPolyline(
                    points = "$x0,$floorY $x1,$padT $x2,$sustainY $x3,$sustainY $x4,$floorY",
                    stroke = laf.accent,
                    strokeWidth = "2",
                    strokeLinejoin = "round",
                    strokeLinecap = "round",
                )

                // Segment letters, centered under their spans
                svgText((x0 + x1) / 2, lettersY, "A", fill = laf.textSecondary, fontSize = "9", textAnchor = "middle")
                svgText((x1 + x2) / 2, lettersY, "D", fill = laf.textSecondary, fontSize = "9", textAnchor = "middle")
                svgText((x2 + x3) / 2, lettersY, "S", fill = laf.textSecondary, fontSize = "9", textAnchor = "middle")
                svgText((x3 + x4) / 2, lettersY, "R", fill = laf.textSecondary, fontSize = "9", textAnchor = "middle")

                // What the envelope drives (loudness, cutoff, ...) — rotated along the left edge
                svgText(
                    x = 10, y = padT + drawH / 2,
                    text = props.label,
                    fill = laf.textTertiary,
                    fontSize = "8",
                    textAnchor = "middle",
                    transform = "rotate(-90 10 ${padT + drawH / 2})",
                )
            }
        }
    }
}
