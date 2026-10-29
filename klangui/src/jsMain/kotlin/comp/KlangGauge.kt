/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.kraft.vdom.custom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.LinearDimension
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangGauge(
    name: String,
    value: Double?,
    range: ClosedRange<Double> = 0.0..1.0,
    size: LinearDimension = 62.px,
    icon: SemanticIconFn? = null,
    label: String? = null,
    display: ((Double) -> String)? = null,
    hotFrom: Double? = null,
) = comp(
    KlangGauge.Props(
        name = name,
        value = value,
        range = range,
        size = size,
        icon = icon,
        label = label,
        display = display,
        hotFrom = hotFrom,
    )
) {
    KlangGauge(it)
}

/**
 * A round gauge (KLANG-00338): a silver rim, a face with its scale, the arc and the needle, an icon or a text label,
 * and the value.
 *
 * Without a value the gauge is offline: no glow, the needle at rest, the mark dim. With one it glows. With one at or
 * past [Props.hotFrom] it is hot: the arc and the glow turn critical. Screen readers hear a `meter` with the value.
 * It draws the value it gets: a caller who wants a needle that glides smooths the stream first (Ultra's `smooth()`,
 * KLANG-00345). Canvas section "Gauges".
 */
class KlangGauge(ctx: Ctx<Props>) : Component<KlangGauge.Props>(ctx) {

    companion object {
        /** The glow's blur, as a share of the gauge's size (as today's `gaugeGlowBlur`). */
        const val GLOW_SHARE = 0.25

        /** The scale's marks: one every tenth of the sweep, a long one at both ends and in the middle. */
        const val TICKS = 11

        /** The arc over the whole sweep; a reading shows it up to its place by the dash. */
        private val FULL_ARC = Dial.arc(fraction = 1.0, radius = 33.0)

        /** The needle's tip at the sweep's start; a reading turns it from there. */
        private val NEEDLE_START = Dial.point(fraction = 0.0, radius = 30.0)
    }

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** What the gauge measures, for screen readers ("Motor heat"). */
        val name: String,
        /** The reading; null while there is none (the Motor is offline). A NaN or an infinity counts as none. */
        val value: Double?,
        val range: ClosedRange<Double>,
        val size: LinearDimension,
        /** An icon between the centre and the top; [label] is printed instead when given. */
        val icon: SemanticIconFn?,
        val label: String?,
        /** The value as text, under the centre; nothing is printed when null. */
        val display: ((Double) -> String)?,
        /** Where the red zone starts, in the range's units; null for a gauge without one. */
        val hotFrom: Double?,
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** The props' reading, when it is a number: a NaN or an infinity reads as no reading at all. */
    private fun readingOf(p: Props): Double? = p.value?.takeIf { it.isFinite() }

    /** Where the needle points: the reading, or the rest while there is none. */
    private val shown: Double get() = readingOf(props) ?: props.range.start

    private val span: Double get() = props.range.endInclusive - props.range.start

    private fun fractionOf(v: Double): Double = if (span > 0.0) Dial.clamp((v - props.range.start) / span) else 0.0

    override fun VDom.render() {
        val styles = KlangControlStyles
        val value = readingOf(props)
        val online = value != null
        // No red zone on a range without a span, or at a hotFrom that is not a number
        val hotFrom = props.hotFrom?.takeIf { it.isFinite() && span > 0.0 }
        // Hot is decided in the range's units: a hotFrom past the range's end is never reached
        val hot = value != null && hotFrom != null && value >= hotFrom
        // Where the red ticks start, unclamped: past the end none are red, before the start all are
        val hotFraction = hotFrom?.let { (it - props.range.start) / span }

        val rimClasses = listOfNotNull(
            styles.gauge,
            when {
                hot -> styles.gaugeHot
                online -> styles.gaugeOnline
                else -> null
            },
        )

        div(classes = rimClasses.joinToString(" ")) {
            attributes["role"] = "meter"
            attributes["aria-label"] = props.name
            // A mouse user learns what an icon gauge measures, as the Motor's gauges always told
            attributes["title"] = props.name
            attributes["aria-valuemin"] = props.range.start.toString()
            attributes["aria-valuemax"] = props.range.endInclusive.toString()
            attributes["data-gauge"] = when {
                hot -> "hot"
                online -> "online"
                else -> "offline"
            }

            if (value != null) {
                // A meter's value stays inside its range; the text says the real reading
                attributes["aria-valuenow"] = value.coerceIn(props.range.start, props.range.endInclusive).toString()
                props.display?.let { attributes["aria-valuetext"] = it(value) }
            } else {
                // A meter must carry a value: the needle's place at rest, said as offline
                attributes["aria-valuenow"] = props.range.start.toString()
                attributes["aria-valuetext"] = "offline"
            }

            css {
                width = props.size
                height = props.size
                put("--gauge-blur", (props.size * GLOW_SHARE).toString())
            }

            div(classes = styles.gaugeFace.toString()) {}

            // The icon is printed on the face, under the needle (the maintainer, 2026-10-26); the parts stack as drawn
            renderMark(online = online, hot = hot)

            renderScale(online = online, hotFraction = hotFraction, hot = hot)

            val display = props.display

            if (value != null && display != null) {
                div(classes = styles.gaugeValue.toString()) {
                    css { fontSize = props.size * 0.17 }
                    +display(shown)
                }
            }

            // Last, so it lies over the dial and the value
            div(classes = styles.gaugeGlass.toString()) {}
        }
    }

    /**
     * The scale: its ticks and track drawn once ([KlangGaugeDial], redrawn only when its red zone changes), over them
     * the arc and the needle, which a new reading moves by one value each: the arc's dash, the needle's turn (the
     * maintainer, 2026-10-29: the gauges "draw the dial once, move only the needle"; every reading, every frame while
     * the needles ease, drew the whole dial again before).
     */
    private fun FlowContent.renderScale(online: Boolean, hotFraction: Double?, hot: Boolean) {
        val styles = KlangControlStyles
        val fraction = fractionOf(shown)

        div(classes = styles.gaugeScale.toString()) {
            KlangGaugeDial(hotFraction = hotFraction)

            svgRoot(viewBox = "0 0 100 100", style = "display:block;height:100%;position:absolute;inset:0;") {
                // Nothing at nought, as the arc of old: a dash of no length still wears its round caps
                if (online && fraction > 0.0) {
                    // The whole sweep, shown up to the reading by its dash: a length of 100 along the path. The gap is
                    // longer than the path, so no next dash starts at its end: one of no length drew a dot there, near
                    // the end of the scale, at a reading of 0 (the maintainer, 2026-10-29)
                    custom("path") {
                        attributes["class"] = if (hot) "${styles.gaugeArc} ${styles.gaugeArcHot}" else "${styles.gaugeArc}"
                        attributes["d"] = FULL_ARC
                        attributes["pathLength"] = "100"
                        attributes["stroke-dasharray"] = "100 200"
                        attributes["stroke-dashoffset"] = (100.0 * (1.0 - fraction)).toString()
                        attributes["stroke-width"] = "4"
                    }
                }

                // Drawn at the sweep's start, turned to the reading
                custom("line") {
                    attributes["class"] = styles.gaugeNeedle.toString()
                    attributes["data-needle"] = "true"
                    attributes["x1"] = "50"
                    attributes["y1"] = "50"
                    attributes["x2"] = NEEDLE_START.first.toString()
                    attributes["y2"] = NEEDLE_START.second.toString()
                    attributes["transform"] = "rotate(${fraction * Dial.SWEEP_DEGREES} 50 50)"
                    attributes["stroke-width"] = "2.4"
                }

                custom("circle") {
                    attributes["class"] = styles.gaugeHub.toString()
                    attributes["cx"] = "50"
                    attributes["cy"] = "50"
                    attributes["r"] = "4.5"
                    attributes["stroke-width"] = "0.8"
                }
            }
        }
    }

    private fun FlowContent.renderMark(online: Boolean, hot: Boolean) {
        val styles = KlangControlStyles
        val markClasses = listOfNotNull(
            styles.gaugeMark,
            when {
                hot -> styles.gaugeMarkHot
                online -> styles.gaugeMarkLit
                else -> null
            },
        ).joinToString(" ")

        val label = props.label
        val iconFn = props.icon

        if (label != null) {
            div(classes = markClasses) {
                attributes["aria-hidden"] = "true"
                css { fontSize = props.size * 0.11 }
                +label
            }
        } else if (iconFn != null) {
            icon.iconFn().with(markClasses).render {
                attributes["aria-hidden"] = "true"
                css { fontSize = props.size * 0.13 }
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.KlangGaugeDial(hotFraction: Double?) = comp(
    KlangGaugeDial.Props(hotFraction = hotFraction)
) {
    KlangGaugeDial(it)
}

/**
 * A gauge's dial: its ticks, the red ones from [Props.hotFraction] on, and the track under the arc. Its props change only
 * with the red zone, so a new reading leaves it alone ([KlangGauge.renderScale]).
 */
private class KlangGaugeDial(ctx: Ctx<Props>) : Component<KlangGaugeDial.Props>(ctx) {

    data class Props(
        /** Where the red ticks start, as a share of the sweep, unclamped; null for none. */
        val hotFraction: Double?,
    )

    override fun VDom.render() {
        val styles = KlangControlStyles
        val hotFraction = props.hotFraction

        svgRoot(viewBox = "0 0 100 100", style = "display:block;height:100%;position:absolute;inset:0;") {
            for (i in 0 until KlangGauge.TICKS) {
                val fraction = i.toDouble() / (KlangGauge.TICKS - 1)
                val major = i % 5 == 0
                val (x1, y1) = Dial.point(fraction = fraction, radius = 44.0)
                val (x2, y2) = Dial.point(fraction = fraction, radius = if (major) 37.0 else 40.0)
                val tickHot = hotFraction != null && fraction >= hotFraction

                custom("line") {
                    attributes["class"] = if (tickHot) "${styles.gaugeTick} ${styles.gaugeTickHot}" else "${styles.gaugeTick}"
                    attributes["x1"] = x1.toString()
                    attributes["y1"] = y1.toString()
                    attributes["x2"] = x2.toString()
                    attributes["y2"] = y2.toString()
                    attributes["stroke-width"] = if (major) "1.8" else "1"
                }
            }

            custom("path") {
                attributes["class"] = styles.gaugeTrack.toString()
                attributes["d"] = Dial.arc(fraction = 1.0, radius = 33.0)
                attributes["stroke-width"] = "4"
            }
        }
    }
}
