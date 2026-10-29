/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangDiagnostics
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title
import kotlin.math.floor
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.MotorHistoryGraphs(paused: Stream<Boolean> = NeverPaused) = comp(
    MotorHistoryGraphs.Props(paused = paused)
) {
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
class MotorHistoryGraphs(ctx: Ctx<Props>) : Component<MotorHistoryGraphs.Props>(ctx) {

    data class Props(
        /** While true, the graphs are not drawn again: the history goes on being kept ([NeverPaused]). */
        val paused: Stream<Boolean>,
    )


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

        /**
         * The graphs whose metrics' max is a budget, a line over it tinted red ([overBudget]); elsewhere the max only
         * ends a scale. Every graph scales to its values ([topFor]; the maintainer, 2026-10-29: "draw the CPU graph
         * between 0 and 200%", "Make the CPU max 120%", "Or also auto-scale, sorry").
         */
        private val BUDGETED = setOf("CPU")

        /**
         * A line over its metric's budget, its [KlangDiagnostics.Metric.max], is drawn in its colour tinted red, the
         * fill under it as ever (the maintainer, 2026-10-29: "over-budget parts in red taint of the graph color ...
         * just the line not the fill").
         */
        private fun overBudget(color: String): String = "color-mix(in srgb, $color 40%, #ff3b30)"

        /** A graph's scale goes in steps of this. */
        private const val SCALE_STEP = 20.0

        /**
         * Where a graph ends: the multiple of [SCALE_STEP] above the highest value in view, so the line never touches the
         * top and the scale moves only in whole steps (the maintainer, 2026-10-29: "Can the voices / cylinders graph scale
         * with the max-value on the fly?", then "make the scaling in full multiples of 20... the multiple of 20 above the
         * current max on the graph"). A quiet graph ends at the first step.
         */
        fun topFor(highest: Double): Double {
            // NaN-guard: no values, or none above nought, take the first step
            val value = highest.takeIf { it > 0.0 } ?: return SCALE_STEP

            return (floor(value / SCALE_STEP) + 1) * SCALE_STEP
        }
    }

    /** The current player's history; null while there is none. */
    private val history by subscribingTo(Player.diagnostics.history.cutoffWhen(props.paused))

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
        // One scale for the category, its metrics sharing a unit: from the lowest of them up to a round number over
        // the highest value in view
        val min = series.minOf { it.metric.min }
        val max = topFor(series.maxOf { s -> s.values.maxOf { it ?: 0.0 } })
        val budgeted = category in BUDGETED

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

            // A small screen, its corners cut as the app's screens are (the maintainer, 2026-10-29: "the graph displays
            // need the triangular corners again")
            div {
                css {
                    put("height", "${HEIGHT.toInt()}px")
                    put("display", "flex")
                    put("flex-direction", "column")
                }

                KlangScreen(size = KlangScreen.Size.Small, bezel = "var(--kl-ground-panel)") {
                    div {
                        css {
                            put("position", "relative")
                            put("flex", "1")
                            put("min-height", "0")
                            put("overflow", "hidden")
                        }

                        // Where the scale ends, in the top corner: a scale that moves says where it is
                        div {
                            css {
                                put("position", "absolute")
                                // Clear of the cut corner
                                put("top", "2px")
                                put("right", "12px")
                                put("font-size", "9px")
                                put("font-family", "monospace")
                                put("color", "var(--kl-ink-label)")
                                put("pointer-events", "none")
                            }
                            +"${max.roundToInt()}${series.first().metric.unit}"
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
                                renderLine(
                                    values = s.values,
                                    min = min,
                                    max = max,
                                    // Over the budget only on a budgeted graph, the CPU's: elsewhere a metric's max is no budget
                                    budget = if (budgeted) s.metric.max else Double.POSITIVE_INFINITY,
                                    size = size,
                                    color = colorOf(s),
                                )
                            }
                        }
                    }
                }
            }

            // The legend: a lamp in each line's colour, its label, its value now; two even columns, so nothing jumps as
            // the values change (the maintainer, 2026-10-29: "These should be shown in a Grid, 2 columns, so that they do
            // not jump. Title with ellipsis numbers right aligned")
            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(2, minmax(0, 1fr))")
                    put("column-gap", "14px")
                    put("row-gap", "4px")
                    put("margin-top", "6px")
                    put("font-size", "11px")
                    put("color", "var(--kl-ink-text)")
                }

                for (s in series) {
                    val latest = s.latest

                    div {
                        key = "legend-${s.metric.id}"
                        title = s.metric.name
                        css {
                            put("display", "grid")
                            put("grid-template-columns", "auto minmax(0, 1fr) auto")
                            put("align-items", "center")
                            put("column-gap", "6px")
                        }

                        KlangLamp(
                            state = if (latest != null) KlangLamp.State.On else KlangLamp.State.Off,
                            size = 8.px,
                            color = colorOf(s),
                        )

                        span {
                            css {
                                put("white-space", "nowrap")
                                put("overflow", "hidden")
                                put("text-overflow", "ellipsis")
                            }
                            +s.metric.name
                        }

                        span {
                            css {
                                put("min-width", "4ch")
                                put("text-align", "right")
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

    /** A point of a line: where it is drawn, and the value it stands for. */
    private class Point(val x: Double, val y: Double, val value: Double)

    /**
     * One metric's line over a faint area, a stretch between its gaps each; where it runs over its [budget] the line is
     * tinted red ([overBudget]), cut where it crosses the budget.
     */
    private fun FlowContent.renderLine(
        values: List<Double?>,
        min: Double,
        max: Double,
        budget: Double,
        size: Int,
        color: String,
    ) {
        val step = WIDTH / (size - 1)
        // The newest at the right edge: while the history fills, it starts further right
        val offset = size - values.size
        // NaN-guard: a range of nothing scales by 1
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val stretches = mutableListOf<List<Point>>()
        var current = mutableListOf<Point>()

        fun yOf(v: Double) = HEIGHT - ((v - min) / span).coerceIn(0.0, 1.0) * HEIGHT

        for ((i, v) in values.withIndex()) {
            if (v == null) {
                if (current.isNotEmpty()) {
                    stretches.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(Point(x = (offset + i) * step, y = yOf(v), value = v))
            }
        }

        if (current.isNotEmpty()) {
            stretches.add(current)
        }

        fun polyline(points: List<Pair<Double, Double>>, stroke: String) {
            custom("polyline") {
                attributes["points"] = points.joinToString(" ") { (x, y) -> "${x.fmt()},${y.fmt()}" }
                attributes["style"] = "fill: none; stroke: $stroke; stroke-width: 1.1"
                attributes["vector-effect"] = "non-scaling-stroke"
            }
        }

        for (points in stretches) {
            val line = points.joinToString(" ") { "${it.x.fmt()},${it.y.fmt()}" }

            custom("polygon") {
                val (first, last) = points.first().x.fmt() to points.last().x.fmt()

                attributes["points"] = "$first,$HEIGHT $line $last,$HEIGHT"
                attributes["style"] = "fill: color-mix(in srgb, $color 16%, transparent)"
            }

            // The line in pieces, a new one where it crosses the budget
            var over = points.first().value > budget
            var piece = mutableListOf(points.first().x to points.first().y)

            for (i in 1 until points.size) {
                val a = points[i - 1]
                val b = points[i]

                if ((b.value > budget) != over) {
                    val t = (budget - a.value) / (b.value - a.value)
                    val cross = (a.x + (b.x - a.x) * t) to yOf(budget)

                    piece.add(cross)
                    polyline(piece, stroke = if (over) overBudget(color) else color)
                    piece = mutableListOf(cross)
                    over = !over
                }

                piece.add(b.x to b.y)
            }

            polyline(piece, stroke = if (over) overBudget(color) else color)
        }
    }

    private fun Double.fmt(): String = ((this * 10).roundToInt() / 10.0).toString()
}
