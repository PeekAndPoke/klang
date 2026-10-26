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
import kotlinx.browser.window
import kotlinx.css.LinearDimension
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.abs
import kotlin.math.pow

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
    smoothing: Double = 0.0,
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
        smoothing = smoothing,
    )
) {
    KlangGauge(it)
}

/**
 * A round gauge (KLANG-00338): a silver rim, a face with its scale, the arc and the needle, an icon or a text label,
 * and the value.
 *
 * Without a value the gauge is offline: no glow, the needle at rest, the mark dim. With one it glows. With the needle
 * at or past [Props.hotFrom] it is hot (with smoothing, once the needle gets there): the arc and the glow turn
 * critical. Screen readers hear a `meter` with the raw value; the needle shows a smoothed one when [Props.smoothing]
 * is above 0, as `RoundGauge` does: it glides towards the reading by itself, a step each frame, also while the
 * reading holds still. Canvas section "Gauges".
 */
class KlangGauge(ctx: Ctx<Props>) : Component<KlangGauge.Props>(ctx) {

    companion object {
        /** The glow's blur, as a share of the gauge's size (as today's `gaugeGlowBlur`). */
        const val GLOW_SHARE = 0.25

        /** The scale's marks: one every tenth of the sweep, a long one at both ends and in the middle. */
        const val TICKS = 11

        /** The time [Props.smoothing] is the share kept for. */
        const val STEP_MS = 10.0

        /** A usual frame, for the first step of a glide. */
        const val FRAME_MS = 1000.0 / 60.0
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
        /** 0 moves the needle at once; towards 1 it follows more slowly: the share it keeps per 10 ms (0.933 the Motor's). */
        val smoothing: Double,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** Where the needle points, in the range's units. */
    private var shown: Double by value(readingOf(props) ?: props.range.start)

    /** Where the needle heads: the reading, or the rest while offline. */
    private var target: Double = shown

    /** A frame is asked for: the needle is on its way to [target]. */
    private var stepping = false

    /** When the last step was taken, from the frame's clock; null before the first step of a glide. */
    private var lastStepAt: Double? = null

    private var mounted = false

    init {
        lifecycle {
            onMount {
                mounted = true
            }

            onUnmount {
                mounted = false
            }

            // Kraft hands the new props first, the previous ones second
            onNextProps { next, _ ->
                val reading = readingOf(next)
                val smoothing = next.smoothing

                when {
                    // Offline: the needle goes to rest at once, it does not wait for more props to get there
                    reading == null -> jumpTo(next.range.start)
                    // NaN-guard: a NaN smoothing counts as none, as does a needle that is not finite
                    smoothing != smoothing || smoothing <= 0.0 || !shown.isFinite() -> jumpTo(reading)
                    else -> {
                        target = reading
                        glide()
                    }
                }
            }
        }
    }

    private fun jumpTo(v: Double) {
        target = v
        shown = v
    }

    /**
     * Starts the needle's glide towards [target], unless one is on: the needle moves by itself, also while a reading
     * holds still (props that do not change bring no step; KLANG-00344).
     */
    private fun glide() {
        if (stepping || !mounted) {
            return
        }

        stepping = true
        lastStepAt = null

        window.requestAnimationFrame { at -> step(at) }
    }

    /**
     * One step a frame, sized by the time since the last one: [Props.smoothing] is the share kept per [STEP_MS], so the
     * needle moves at the same pace on any display and whatever the readings do (the old Motor's needles took about
     * a step every 10 ms, its ticker and its readings together).
     */
    private fun step(at: Double) {
        stepping = false

        if (!mounted) {
            return
        }

        // NaN-guard: the first step of a glide, or a frame clock that stands or goes backwards, counts as one usual
        // frame; a long gap (a tab in the background) is time that passed, the needle moves on by it
        val elapsed = lastStepAt?.let { at - it }?.takeIf { it > 0.0 } ?: FRAME_MS
        val share = props.smoothing.coerceIn(0.0, 0.999).pow(elapsed / STEP_MS)
        val next = shown * share + target * (1.0 - share)
        // Close enough to stop: a thousandth of the sweep, or of 1 on a range without a span; a NaN or a step that
        // moves nothing any more counts as there, so the frames never run on for good
        val near = if (span > 0.0) span * 0.001 else 0.001
        val done = !(abs(next - target) > near) || next == shown

        lastStepAt = at
        shown = if (done) target else next

        if (!done) {
            stepping = true
            window.requestAnimationFrame { nextAt -> step(nextAt) }
        }
    }

    /** The props' reading, when it is a number: a NaN or an infinity reads as no reading at all. */
    private fun readingOf(p: Props): Double? = p.value?.takeIf { it.isFinite() }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private val span: Double get() = props.range.endInclusive - props.range.start

    private fun fractionOf(v: Double): Double = if (span > 0.0) Dial.clamp((v - props.range.start) / span) else 0.0

    override fun VDom.render() {
        val styles = KlangControlStyles
        val value = readingOf(props)
        val online = value != null
        // No red zone on a range without a span, or at a hotFrom that is not a number
        val hotFrom = props.hotFrom?.takeIf { it.isFinite() && span > 0.0 }
        // Hot is decided in the range's units, on what the needle shows: a spike the smoothing evens out never turns the
        // gauge red (the maintainer, 2026-10-26); a hotFrom past the range's end is never reached
        val hot = value != null && hotFrom != null && shown >= hotFrom
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

    private fun FlowContent.renderScale(online: Boolean, hotFraction: Double?, hot: Boolean) {
        val styles = KlangControlStyles

        div(classes = styles.gaugeScale.toString()) {
            svgRoot(viewBox = "0 0 100 100", style = "display:block;height:100%;") {
                for (i in 0 until TICKS) {
                    val fraction = i.toDouble() / (TICKS - 1)
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

                if (online) {
                    custom("path") {
                        attributes["class"] = if (hot) "${styles.gaugeArc} ${styles.gaugeArcHot}" else "${styles.gaugeArc}"
                        attributes["d"] = Dial.arc(fraction = fractionOf(shown), radius = 33.0)
                        attributes["stroke-width"] = "4"
                    }
                }

                val (nx, ny) = Dial.point(fraction = fractionOf(shown), radius = 30.0)

                custom("line") {
                    attributes["class"] = styles.gaugeNeedle.toString()
                    attributes["data-needle"] = "true"
                    attributes["x1"] = "50"
                    attributes["y1"] = "50"
                    attributes["x2"] = nx.toString()
                    attributes["y2"] = ny.toString()
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
