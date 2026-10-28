/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangDiagnostics
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.px
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
 * The diagnostics' last two minutes as graphs, as a task manager shows a machine's load (the maintainer, 2026-10-28;
 * KLANG-00385): one graph per category, its metrics as lines over faint areas, each in a colour of its own from the
 * look, the newest at the right; under it a legend, a lamp in each line's colour, its label and its value now (the
 * maintainer, 2026-10-28: "Give each graph a color. Use the LED comp we have to show the color of a diagnostic", "LED +
 * Label in the legend of the graph"). The colours are the UI's, chosen here, never part of a metric (the maintainer:
 * "I would decide the colors on the ui side"). The player and the playbacks keep the metrics
 * ([KlangPlayer.diagnostics]); this only draws them.
 */
class MotorHistoryGraphs(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The graph's drawing, in the SVG's own units; it stretches to the window's width. */
        private const val WIDTH = 400.0
        private const val HEIGHT = 60.0

        /**
         * The lines' colours, in turn: hues far apart, so a line and its lamp in the legend are found at a glance (the
         * maintainer, 2026-10-28: "Choose colors that are further apart"); the look's spectrum steps were neighbours on
         * purpose and too alike for it. Fixed, as data colours: bright enough on every look's dark ground; the first
         * ones the furthest apart: cyan, amber, magenta, lime, violet, coral.
         */
        private val COLORS = listOf(
            "#4dd0e1",
            "#ffb74d",
            "#f06292",
            "#aed581",
            "#9575cd",
            "#ff8a65",
        )
    }

    /** The current player's history; null while there is none. */
    private val history by subscribingTo(Player.diagnostics.history)

    override fun VDom.render() {
        val h = history

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "16px")
            }

            if (h == null || h.series.isEmpty()) {
                div {
                    css {
                        put("font-size", "12px")
                        put("color", "var(--kl-ink-label)")
                    }
                    +"No readings yet"
                }

                return@div
            }

            // Each metric its colour by its place among all, so a colour names one line in the whole window
            val colorOf = h.series.withIndex().associate { (i, series) -> series.metric to COLORS[i % COLORS.size] }

            for ((category, series) in h.byCategory) {
                renderGraph(category = category, series = series, colorOf = { colorOf.getValue(it.metric) }, size = h.capacity)
            }
        }
    }

    private fun FlowContent.renderGraph(
        category: String,
        series: List<KlangDiagnostics.Series>,
        colorOf: (KlangDiagnostics.Series) -> String,
        size: Int,
    ) {
        // One scale for the category: its metrics share a unit, the widest range holds them all
        val min = series.minOf { it.metric.min }
        val max = series.maxOf { it.metric.max }

        div {
            key = "graph-$category"

            div {
                css {
                    put("margin-bottom", "4px")
                    put("font-size", "11px")
                    put("color", "var(--kl-ink-label)")
                }
                +category
            }

            div {
                css {
                    put("height", "${HEIGHT.toInt()}px")
                    put("background", "var(--kl-ground-sunken)")
                    put("border", "1px solid var(--kl-edge)")
                    put("border-radius", "var(--kl-radius)")
                    put("overflow", "hidden")
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

                    for (s in series) {
                        renderLine(values = s.values, min = min, max = max, size = size, color = colorOf(s))
                    }
                }
            }

            // The legend: a lamp in each line's colour, its label, its value now
            div {
                css {
                    put("display", "flex")
                    put("flex-wrap", "wrap")
                    put("column-gap", "14px")
                    put("row-gap", "4px")
                    put("margin-top", "6px")
                    put("font-size", "11px")
                    put("color", "var(--kl-ink-text)")
                }

                for (s in series) {
                    val latest = s.latest

                    span {
                        key = "legend-${s.metric.id}"
                        css {
                            put("display", "inline-flex")
                            put("align-items", "center")
                            put("gap", "6px")
                        }

                        KlangLamp(
                            state = if (latest != null) KlangLamp.State.On else KlangLamp.State.Off,
                            size = 8.px,
                            color = colorOf(s),
                        )

                        span { +s.metric.name }

                        span {
                            css {
                                put("font-family", "monospace")
                                put("color", "var(--kl-lcd-ink)")
                            }
                            +(latest?.let { "${it.roundToInt()}${s.metric.unit}" } ?: "off")
                        }
                    }
                }
            }
        }
    }

    /** One metric's line over a faint area, a stretch between its gaps each. */
    private fun FlowContent.renderLine(values: List<Double?>, min: Double, max: Double, size: Int, color: String) {
        val step = WIDTH / (size - 1)
        // The newest at the right edge: while the history fills, it starts further right
        val offset = size - values.size
        // NaN-guard: a range of nothing scales by 1
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stretches = mutableListOf<List<Pair<Double, Double>>>()
        var current = mutableListOf<Pair<Double, Double>>()

        for ((i, v) in values.withIndex()) {
            if (v == null) {
                if (current.isNotEmpty()) {
                    stretches.add(current)
                    current = mutableListOf()
                }
            } else {
                val y = HEIGHT - ((v - min) / span).coerceIn(0.0, 1.0) * HEIGHT
                current.add((offset + i) * step to y)
            }
        }

        if (current.isNotEmpty()) {
            stretches.add(current)
        }

        for (points in stretches) {
            val line = points.joinToString(" ") { (x, y) -> "${x.fmt()},${y.fmt()}" }

            custom("polygon") {
                val (first, last) = points.first().first.fmt() to points.last().first.fmt()

                attributes["points"] = "$first,$HEIGHT $line $last,$HEIGHT"
                attributes["style"] = "fill: color-mix(in srgb, $color 16%, transparent)"
            }

            custom("polyline") {
                attributes["points"] = line
                attributes["style"] = "fill: none; stroke: $color; stroke-width: 1.1"
                attributes["vector-effect"] = "non-scaling-stroke"
            }
        }
    }

    private fun Double.fmt(): String = ((this * 10).roundToInt() / 10.0).toString()
}
