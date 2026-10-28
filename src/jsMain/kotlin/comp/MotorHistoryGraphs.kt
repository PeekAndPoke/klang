/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangMotorDiagnostics
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.MotorHistoryGraphs() = comp {
    MotorHistoryGraphs(it)
}

/**
 * The Motor's last two minutes as graphs, one per gauge, in the Motor tool window, as a task manager shows a machine's
 * load (the maintainer, 2026-10-28): a grid, the readings as a line over a filled area in the look's gauge colour, the
 * newest at the right, and the reading now beside the name. The player keeps the history
 * ([KlangPlayer.diagnostics]); this only draws it.
 */
class MotorHistoryGraphs(ctx: NoProps) : PureComponent(ctx) {

    /** One gauge's graph: its readings, oldest first, null where the Motor gave none. */
    private class Gauge(val name: String, val max: Double, val display: (Double) -> String, val values: List<Double?>)

    companion object {
        /** The graph's drawing, in the SVG's own units; it stretches to the window's width. */
        private const val WIDTH = 400.0
        private const val HEIGHT = 60.0
    }

    /** The current player's history; null while there is none. */
    private val history by subscribingTo(Player.diagnostics.history)

    private val gauges: List<Gauge>
        get() {
            val h = history

            return listOf(
                Gauge(
                    name = "Motor heat",
                    max = 1.0,
                    display = { "${(it * 100).roundToInt()}%" },
                    values = h?.heat.orEmpty(),
                ),
                Gauge(
                    name = "Active cylinders",
                    max = 32.0,
                    display = { it.roundToInt().toString() },
                    values = h?.cylinders.orEmpty().map { it?.toDouble() },
                ),
                Gauge(
                    name = "Active voices",
                    max = 100.0,
                    display = { it.roundToInt().toString() },
                    values = h?.voices.orEmpty().map { it?.toDouble() },
                ),
            )
        }

    override fun VDom.render() {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "14px")
            }

            for (gauge in gauges) {
                renderGraph(gauge)
            }
        }
    }

    private fun FlowContent.renderGraph(gauge: Gauge) {
        div {
            div {
                css {
                    put("display", "flex")
                    put("justify-content", "space-between")
                    put("margin-bottom", "4px")
                    put("font-size", "11px")
                    put("color", "var(--kl-ink-label)")
                }

                span { +gauge.name }

                span {
                    css {
                        put("font-family", "monospace")
                        put("color", "var(--kl-lcd-ink)")
                    }
                    +(gauge.values.lastOrNull()?.let(gauge.display) ?: "off")
                }
            }

            div {
                css {
                    put("height", "${HEIGHT.toInt()}px")
                    put("background", "var(--kl-ground-sunken)")
                    put("border", "1px solid var(--kl-edge)")
                    put("border-radius", "var(--kl-radius)")
                    put("overflow", "hidden")
                }

                graph(gauge)
            }
        }
    }

    /** The graph: a grid, then per stretch of readings between the gaps an area and its line. */
    private fun FlowContent.graph(gauge: Gauge) {
        val size = KlangMotorDiagnostics.TWO_MINUTES
        val step = WIDTH / (size - 1)
        // The newest at the right edge: while the history fills, it starts further right
        val offset = size - gauge.values.size
        val stretches = mutableListOf<List<Pair<Double, Double>>>()
        var current = mutableListOf<Pair<Double, Double>>()

        for ((i, v) in gauge.values.withIndex()) {
            // A gap (the Motor off) ends a stretch
            if (v == null) {
                if (current.isNotEmpty()) {
                    stretches.add(current)
                    current = mutableListOf()
                }
            } else {
                val y = HEIGHT - (v / gauge.max).coerceIn(0.0, 1.0) * HEIGHT
                current.add((offset + i) * step to y)
            }
        }

        if (current.isNotEmpty()) {
            stretches.add(current)
        }

        svgRoot(viewBox = "0 0 $WIDTH $HEIGHT", style = "display:block;height:100%;") {
            attributes["preserveAspectRatio"] = "none"

            for (i in 1..3) {
                val y = (HEIGHT * i / 4).toString()

                custom("line") {
                    attributes["x1"] = "0"
                    attributes["y1"] = y
                    attributes["x2"] = WIDTH.toString()
                    attributes["y2"] = y
                    attributes["style"] = "stroke: var(--kl-edge); stroke-width: 1"
                    attributes["vector-effect"] = "non-scaling-stroke"
                }
            }

            for (points in stretches) {
                val line = points.joinToString(" ") { (x, y) -> "${x.fmt()},${y.fmt()}" }

                custom("polygon") {
                    val (first, last) = points.first().first.fmt() to points.last().first.fmt()

                    attributes["points"] = "$first,$HEIGHT $line $last,$HEIGHT"
                    attributes["style"] = "fill: color-mix(in srgb, var(--kl-gauge-arc) 22%, transparent)"
                }

                custom("polyline") {
                    attributes["points"] = line
                    attributes["style"] = "fill: none; stroke: var(--kl-gauge-arc); stroke-width: 1.5"
                    attributes["vector-effect"] = "non-scaling-stroke"
                }
            }
        }
    }

    private fun Double.fmt(): String = ((this * 10).roundToInt() / 10.0).toString()
}
