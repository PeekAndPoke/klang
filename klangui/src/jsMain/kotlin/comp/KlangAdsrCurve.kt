/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.comp

import io.peekandpoke.klang.ui.svgCircle
import io.peekandpoke.klang.ui.svgG
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
import io.peekandpoke.ultra.streams.Stream
import kotlin.js.Date
import kotlin.math.max
import kotlin.math.round
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.KlangAdsrCurve(
    adsr: KlangAdsr,
    signals: Stream<KlangAdsrSignal?>,
    label: String = "loudness",
    clock: KlangAdsrCurve.Clock = KlangAdsrCurve.Clock.Browser,
) = comp(
    KlangAdsrCurve.Props(adsr = adsr, signals = signals, label = label, clock = clock)
) {
    KlangAdsrCurve(it)
}

/** An envelope's four numbers: attack, decay and release in seconds, sustain a level from 0 to 1. */
data class KlangAdsr(
    val attack: Double,
    val decay: Double,
    val sustain: Double,
    val release: Double,
)

/** What a [KlangAdsrCurve] follows: the notes that sound, one signal each. */
sealed interface KlangAdsrSignal {

    /**
     * One note: when it starts and when its gate ends, in seconds on the clock of `Date.now()` (as the player's voice
     * events give them), and the envelope it plays with.
     */
    data class Note(
        val startSec: Double,
        val gateEndSec: Double,
        val adsr: KlangAdsr,
    ) : KlangAdsrSignal

    /** The notes still ahead are void (the playback stopped, its code was updated): their lights never come. */
    data object Clear : KlangAdsrSignal
}

/**
 * An envelope's curve that follows the notes as they sound (KLANG-00371; the maintainer, 2026-10-27: "a bright light
 * following the curve while the sound plays"): each note a point of light that climbs the attack, falls through the
 * decay, crosses the sustain while the note is held and runs down the release; notes that overlap, several points.
 * With nothing playing the curve dims.
 *
 * It knows nothing of audio: whoever plays maps its notes to [KlangAdsrSignal]s and sends them through
 * [Props.signals], every one (a stream set directly passes equal values on; one that comes again from a new
 * subscription is over already and draws nothing). Any envelope the same way: the loudness, a filter's cutoff.
 *
 * Schematic, as the pictures in the tutorials always were: straight segments, the attack, decay and release as wide
 * as their share of the time with a least width, the sustain a fixed share. It is drawn from [Props.adsr] until a
 * note brings its own values, then from the last note begun: an edited example shows the shape that plays.
 */
class KlangAdsrCurve(ctx: Ctx<Props>) : Component<KlangAdsrCurve.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        /** The shape before any note sounds: what the code beside it writes. */
        val adsr: KlangAdsr,
        val signals: Stream<KlangAdsrSignal?>,
        /** What the envelope moves, along the left edge: "loudness", "cutoff". */
        val label: String,
        val clock: Clock,
    )

    /** The time the lights are drawn at, and the next frame to draw them in; a spec gives its own. */
    interface Clock {
        /** Now, in seconds on the clock of `Date.now()`. */
        fun nowSec(): Double

        fun nextFrame(onFrame: () -> Unit)

        object Browser : Clock {
            override fun nowSec(): Double = Date.now() / 1000.0

            override fun nextFrame(onFrame: () -> Unit) {
                window.requestAnimationFrame { onFrame() }
            }
        }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    /** The notes sounding or still to come, oldest first. */
    private val notes = mutableListOf<KlangAdsrSignal.Note>()

    /** When the last note's release ended: the curve stays lit a moment after, so it does not blink in the rests. */
    private var lastEndSec = Double.NEGATIVE_INFINITY

    /** The shape drawn last: kept while the notes of a new one are still ahead. */
    private var shownAdsr: KlangAdsr = props.adsr

    private var framesRunning = false

    private var mounted = true

    @Suppress("unused")
    private val signal by subscribingTo(props.signals) { signal -> take(signal) }

    init {
        lifecycle {
            onUnmount { mounted = false }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun take(signal: KlangAdsrSignal?) {
        when (signal) {
            null -> return

            is KlangAdsrSignal.Clear -> {
                val now = props.clock.nowSec()

                // A note begun sounds on; only those still ahead are void
                notes.removeAll { it.startSec > now }
            }

            // One over already (the last value again, for a new subscriber) leaves at the next drawing
            is KlangAdsrSignal.Note -> notes.add(signal)
        }

        runFrames()
    }

    /** A frame after another while a note sounds or is still to come; then one more, to draw it gone. */
    private fun runFrames() {
        if (framesRunning) {
            triggerRedraw()
            return
        }

        framesRunning = true
        triggerRedraw()

        fun frame() {
            if (!mounted) {
                framesRunning = false
                return
            }

            triggerRedraw()

            if (notes.isEmpty() && props.clock.nowSec() > lastEndSec + LIT_AFTER_SEC) {
                framesRunning = false
            } else {
                props.clock.nextFrame(::frame)
            }
        }

        props.clock.nextFrame(::frame)
    }

    override fun VDom.render() {
        val now = props.clock.nowSec()

        // The notes whose light is out leave here, where the time is read for the drawing
        notes.filter { it.endSec() <= now }.forEach { lastEndSec = max(lastEndSec, it.endSec()) }
        notes.removeAll { it.endSec() <= now }

        // The shape that plays: the last note begun brings it; before any, the one written
        val shape = notes.lastOrNull { it.startSec <= now }?.adsr ?: shownAdsr
        shownAdsr = shape

        val geometry = KlangAdsrGeometry(shape)
        val lit = notes.any { it.startSec <= now } || now <= lastEndSec + LIT_AFTER_SEC

        div {
            attributes["data-lit"] = lit.toString()
            css {
                put("max-width", "420px")
                put("margin", "12px 0")
            }

            svgRoot(viewBox = "0 0 ${KlangAdsrGeometry.WIDTH} ${KlangAdsrGeometry.HEIGHT}") {
                // The floor: silence, the resting level
                svgLine(
                    x1 = geometry.points[0].x,
                    y1 = KlangAdsrGeometry.FLOOR,
                    x2 = KlangAdsrGeometry.WIDTH - KlangAdsrGeometry.PAD_RIGHT,
                    y2 = KlangAdsrGeometry.FLOOR,
                    stroke = "color-mix(in srgb, var(--kl-ink-label) 30%, transparent)",
                    strokeWidth = "1",
                )

                svgG(style = "opacity: ${if (lit) "1" else "0.55"}; transition: opacity 0.3s") {
                    svgPath(d = geometry.pathD() + " Z", fill = "var(--kl-accent)", opacity = "0.15")

                    svgPolyline(
                        points = geometry.polylinePoints(),
                        stroke = "var(--kl-accent)",
                        strokeWidth = "2",
                        strokeLinejoin = "round",
                        strokeLinecap = "round",
                    )
                }

                // Each note a white point with a halo in the lit lamp's colour
                svgG(style = "filter: drop-shadow(0 0 3px var(--kl-lamp-on)) drop-shadow(0 0 6px var(--kl-lamp-on))") {
                    for (note in notes) {
                        val at = geometry.lightAt(note = note, nowSec = now) ?: continue

                        svgCircle(cx = at.x.format(), cy = at.y.format(), r = 3.2, fill = "#ffffff")
                    }
                }

                listOf("A", "D", "S", "R").forEachIndexed { index, letter ->
                    svgText(
                        x = ((geometry.points[index].x + geometry.points[index + 1].x) / 2).format(),
                        y = KlangAdsrGeometry.FLOOR + 14,
                        text = letter,
                        fill = "var(--kl-ink-label)",
                        fontSize = "9",
                        textAnchor = "middle",
                    )
                }

                // What the envelope moves, turned along the left edge
                val labelY = KlangAdsrGeometry.PAD_TOP + KlangAdsrGeometry.DRAW_HEIGHT / 2

                svgText(
                    x = 10,
                    y = labelY,
                    text = props.label,
                    fill = "var(--kl-ink-label)",
                    fontSize = "8",
                    textAnchor = "middle",
                    transform = "rotate(-90 10 $labelY)",
                )
            }
        }
    }

    /** Two decimals: a point moves by hundredths of the drawing, more digits only churn the DOM. */
    private fun Double.format(): Double = round(this * 100.0) / 100.0

    private companion object {
        /** How long the curve stays lit after the last note's release: longer than a rest, shorter than a pause. */
        const val LIT_AFTER_SEC = 1.0
    }
}

/** When a note's light is out: its release run down. */
internal fun KlangAdsrSignal.Note.endSec(): Double = gateEndSec + max(0.0, adsr.release)

/** A point in the curve's drawing. */
internal data class KlangAdsrPoint(val x: Double, val y: Double)

/**
 * The curve of [adsr] in the drawing's units, and where a note's light is at a time. Its five corners: the start on
 * the floor, the attack's peak, the decay's end at the sustain level, the gate's end there, the release's end on the
 * floor.
 */
internal class KlangAdsrGeometry(private val adsr: KlangAdsr) {

    companion object {
        const val WIDTH = 320.0
        const val HEIGHT = 92.0
        const val PAD_LEFT = 24.0
        const val PAD_RIGHT = 8.0
        const val PAD_TOP = 8.0
        const val DRAW_HEIGHT = 60.0
        const val FLOOR = PAD_TOP + DRAW_HEIGHT

        /** The sustain has no time of its own in the picture: a fixed share of the width. */
        private const val HOLD_SHARE = 0.30

        /** A stage of nearly no time still shows as an edge. */
        private const val MIN_WIDTH = 3.0
    }

    private val attack = max(0.0, adsr.attack)
    private val decay = max(0.0, adsr.decay)
    private val release = max(0.0, adsr.release)
    private val sustainY = PAD_TOP + (1.0 - adsr.sustain.coerceIn(0.0, 1.0)) * DRAW_HEIGHT

    val points: List<KlangAdsrPoint> = run {
        val drawWidth = WIDTH - PAD_LEFT - PAD_RIGHT
        val holdWidth = drawWidth * HOLD_SHARE
        val flexWidth = drawWidth - holdWidth
        val total = attack + decay + release

        fun width(seconds: Double): Double {
            val share = if (total > 0.0) seconds / total else 1.0 / 3.0

            return MIN_WIDTH + (flexWidth - 3 * MIN_WIDTH) * share
        }

        val x1 = PAD_LEFT + width(attack)
        val x2 = x1 + width(decay)
        val x3 = x2 + holdWidth

        listOf(
            KlangAdsrPoint(PAD_LEFT, FLOOR),
            KlangAdsrPoint(x1, PAD_TOP),
            KlangAdsrPoint(x2, sustainY),
            KlangAdsrPoint(x3, sustainY),
            KlangAdsrPoint(x3 + width(release), FLOOR),
        )
    }

    fun pathD(): String = "M " + points.joinToString(" L ") { "${it.x} ${it.y}" }

    fun polylinePoints(): String = points.joinToString(" ") { "${it.x},${it.y}" }

    /**
     * Where [note]'s light is at [nowSec], on this curve; null before the note starts and once its release is over.
     * The light crosses the sustain in the time the gate is open, so it reaches the release's corner when the note
     * ends. A note that ends before its decay has run releases from where it is, straight to the curve's end.
     */
    fun lightAt(note: KlangAdsrSignal.Note, nowSec: Double): KlangAdsrPoint? {
        val start = note.startSec
        val gateEnd = note.gateEndSec
        val own = note.adsr
        val ownAttack = max(0.0, own.attack)
        val ownDecay = max(0.0, own.decay)
        val ownRelease = max(0.0, own.release)

        if (nowSec < start || nowSec > gateEnd + ownRelease) {
            return null
        }

        fun held(t: Double): KlangAdsrPoint {
            val decayEnd = start + ownAttack + ownDecay

            return when {
                t < start + ownAttack -> along(0, fraction(t - start, ownAttack))
                t < decayEnd -> along(1, fraction(t - start - ownAttack, ownDecay))
                else -> along(2, fraction(t - decayEnd, gateEnd - decayEnd))
            }
        }

        if (nowSec <= gateEnd) {
            return held(nowSec)
        }

        val from = held(gateEnd)
        val to = points[4]
        val f = fraction(nowSec - gateEnd, ownRelease)

        return KlangAdsrPoint(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f)
    }

    /** The point [f] of the way along the segment from corner [index] to the next. */
    private fun along(index: Int, f: Double): KlangAdsrPoint {
        val a = points[index]
        val b = points[index + 1]

        return KlangAdsrPoint(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)
    }

    // A stage of no time is over at once; NaN-guard: a span of zero or less gives 1, never 0/0
    private fun fraction(elapsed: Double, span: Double): Double =
        if (span <= 0.0) 1.0 else (elapsed / span).coerceIn(0.0, 1.0)
}
