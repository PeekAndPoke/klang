/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.onResize
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.w3c.dom.CanvasLineCap
import org.w3c.dom.CanvasLineJoin
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.ROUND
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Path2D
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.Oscilloscope(
    player: Stream<KlangPlayer?>,
    strokeColor: Color = Color.white,
    strokeWidth: Double = 3.0,
    centerLineColor: Color? = Color.white.withAlpha(0.07),
    centerLineWidth: Double = 1.0,
    pixelSkip: Int = 5,
    preview: Float32Array? = null,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
        pixelSkip = pixelSkip,
        preview = preview,
    )
) {
    Oscilloscope(it)
}

/**
 * The oscilloscope: the newest frame of the player's waveform as a trace over a faint centre line, with a soft shadow
 * under it. The trace eases off towards both ends, as a string held at its ends, and each frame fades the last one
 * out, a short afterglow.
 * Without a player it draws the idle line. With [Props.preview] it draws that frame and nothing else, a still picture
 * of itself (the look editor's card, KLANG-00382; the maintainer, 2026-10-28: "these component implement a preview
 * mode, when they get static data").
 */
class Oscilloscope(ctx: Ctx<Props>) : Component<Oscilloscope.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val player: Stream<KlangPlayer?>,
        val strokeColor: Color,
        val strokeWidth: Double,
        val centerLineColor: Color?,
        val centerLineWidth: Double,
        /** Draw every n-th pixel (or sample) only. */
        val pixelSkip: Int,
        /** A still frame to draw instead of the player's waveform; null for the live trace. */
        val preview: Float32Array?,
    )

    companion object {
        /** The idle frame's length, all silence. */
        private const val IDLE_FRAME_SIZE = 2048

        /** How steeply the trace eases off towards its ends. */
        private const val DEFLECTION_STRENGTH = 4.0

        /** How much of the last frame each new one fades out: the afterglow. */
        private const val AFTERGLOW_FADE = 0.95

        /** How far the shade's stroke lies below the string's, a share of the string's width. */
        private const val SHADE_OFFSET = 0.3

        /** The light's width, a share of the string's. */
        private const val HIGHLIGHT_WIDTH = 0.35

        /** How far above the string's middle the light runs, a share of the string's width. */
        private const val HIGHLIGHT_LIFT = 0.25

        /** How bright the light is: white at this opacity over the string's colour. */
        private const val HIGHLIGHT_ALPHA = 0.85

        /** How dark the shade is: black at this opacity over the string's colour. */
        private const val SHADE_ALPHA = 0.65

        /** How far below the string its shadow falls, in CSS pixels. */
        private const val STRING_SHADOW_OFFSET = 1.0

        /**
         * The canvas's backing store per screen pixel: drawn at twice the screen's resolution and scaled down by the
         * browser, 2x oversampling (the maintainer, 2026-10-28: "making the draw area twice the actual pixel size and
         * then scale down").
         */
        private const val OVERSAMPLING = 2.0

        /** The backing store per CSS pixel at the most, so a dense, large screen does not get a huge canvas. */
        private const val MAX_BACKING_SCALE = 4.0

        private val idleBuffer = Float32Array(IDLE_FRAME_SIZE)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var lastHistory: AnalyzerBufferHistory? = null

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

    /** The canvas's size in CSS pixels, which the drawing works in. */
    private var cssWidth = 0
    private var cssHeight = 0

    /** The ease-off per pixel, for the width it was made for. */
    private var deflectionCache: Pair<Int, DoubleArray>? = null

    /** The trace's colour changed (a new look): the next update draws the last frame again, in the new colour. */
    private var restroke = false

    /** The player's waveform, followed to the next player; null (no player, or a player without an analyzer) keeps the last frame. */
    @Suppress("unused")
    private val waveform by subscribingTo(
        props.player.map { it?.getAnalyzer() }.switchMapNotNull { it.waveform }
    ) { history ->
        if (history != null) {
            lastHistory = history
            redraw()
        }
    }

    init {
        lifecycle {
            onNextProps { next, old ->
                if (next.strokeColor != old.strokeColor) {
                    restroke = true
                }
            }

            onUpdate {
                if (restroke) {
                    restroke = false
                    redraw()
                }
            }

            onMount {
                dom?.let { container ->
                    canvas = container.querySelector("canvas") as HTMLCanvasElement?
                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D

                    sizeCanvas(width = container.clientWidth.toDouble(), height = container.clientHeight.toDouble())
                }

                redraw()
            }

            onResize { entries ->
                for (entry in entries) {
                    sizeCanvas(width = entry.contentRect.width, height = entry.contentRect.height)

                    // A resized canvas is cleared: draw again
                    redraw()
                }
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sizes the canvas to [width] by [height] CSS pixels, its backing store [OVERSAMPLING] times the screen's pixels
     * (the screen's density times that, [MAX_BACKING_SCALE] at the most), and the drawing goes on in CSS pixels. The
     * browser scales the store down to the screen, and that smooths the string in motion (the maintainer, 2026-10-26:
     * "Can we make the canvas 2x the css size so that we get more pixel interpolation"); a store at the screen's own
     * density did not look nice when moving, and a fixed 2x was no oversampling on a dense screen.
     */
    private fun sizeCanvas(width: Double, height: Double) {
        val c = canvas ?: return
        // NaN-guard: a density that is not a number counts as 1
        val density = window.devicePixelRatio.takeIf { it > 0.0 } ?: 1.0
        val ratio = (density * OVERSAMPLING).coerceAtMost(MAX_BACKING_SCALE)

        cssWidth = width.toInt()
        cssHeight = height.toInt()
        c.width = (width * ratio).roundToInt()
        c.height = (height * ratio).roundToInt()
        c.style.width = "${width}px"
        c.style.height = "${height}px"
        ctx2d?.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
    }

    /** The preview's frame, else the newest frame of the last waveform, else the idle line. */
    private fun redraw() {
        val history = lastHistory
        val preview = props.preview

        if (preview != null) {
            draw(buffer = preview, bufferLength = preview.length)
        } else if (history != null && history.size > 0) {
            draw(buffer = history[0], bufferLength = history.bufferSize)
        } else {
            draw(buffer = idleBuffer, bufferLength = IDLE_FRAME_SIZE)
        }
    }

    private fun draw(buffer: Float32Array, bufferLength: Int) {
        val ctx = ctx2d ?: return
        val width = cssWidth
        val centerY = cssHeight / 2.0

        ctx.fadeout(AFTERGLOW_FADE)

        props.centerLineColor?.let { line ->
            ctx.strokeStyle = line.toString()
            ctx.lineWidth = props.centerLineWidth
            ctx.beginPath()
            ctx.moveTo(0.0, centerY)
            ctx.lineTo(width.toDouble(), centerY)
            ctx.stroke()
        }

        if (bufferLength <= 0 || width <= 0) {
            return
        }

        val easeOff = deflectionFor(width)
        val step = props.pixelSkip.coerceAtLeast(1)

        fun yOf(value: Float, x: Double): Double {
            val eased = value * easeOff[x.toInt().coerceIn(0, easeOff.size - 1)]

            return centerY - eased * centerY
        }

        // The trace's points first, then one rounded path through them
        val points = TracePoints(capacity = 2 * maxOf(width, bufferLength) + 2)

        if (width >= bufferLength) {
            // More pixels than samples: a point per sample
            val sliceWidth = width.toDouble() / bufferLength

            for (i in 0 until bufferLength step step) {
                val x = i * sliceWidth

                points.add(x = x, y = yOf(value = buffer[i], x = x))
            }
        } else {
            // More samples than pixels: each drawn pixel spans its samples' lowest and highest, in the order the
            // signal reached them. Always highest then lowest drew, from every pixel's lowest to the next one's
            // highest, a line rising to the right whatever the wave did: spikes (the maintainer, 2026-10-27)
            val samplesPerPixel = bufferLength / width

            for (pixelX in 0 until width step step) {
                val start = pixelX * samplesPerPixel
                val end = minOf(start + samplesPerPixel, bufferLength)

                val (first, last) = extremesInOrder(buffer = buffer, start = start, end = end)
                val x = pixelX.toDouble()

                points.add(x = x, y = yOf(value = first, x = x))
                points.add(x = x, y = yOf(value = last, x = x))
            }
        }

        // Built once, stroked twice: the shadow, then the string
        val path = points.roundedPath()

        // A dark band behind the trace, a little lower, so it reads as the string's shadow (the maintainer, 2026-10-26)
        strokeShifted(
            path = path,
            dy = STRING_SHADOW_OFFSET,
            color = Color.black.withAlpha(0.5),
            width = props.strokeWidth + 2.0,
        )

        // The string lit from above, drawn bottom to top (the maintainer, 2026-10-28: "we draw from bottom to top: dark,
        // middle, bright"): its shade, a stroke of its width a little lower, then the string over it, so the shade is
        // its lowest edge and nothing bright shows below it; then the light along its top, a stroke of its own width
        // ("the highlight might need its own width"; all three of one width looked "pretty flat")
        strokeToned(path = path, dy = props.strokeWidth * SHADE_OFFSET, tint = Color.black.withAlpha(SHADE_ALPHA))
        strokeToned(path = path, dy = 0.0, tint = null)
        strokeShifted(
            path = path,
            dy = -props.strokeWidth * HIGHLIGHT_LIFT,
            color = Color.white.withAlpha(HIGHLIGHT_ALPHA),
            width = props.strokeWidth * HIGHLIGHT_WIDTH,
        )
    }

    /** The ease-off for each pixel of [width]: 1 in the middle, down to 0.02 at the ends. */
    private fun deflectionFor(width: Int): DoubleArray {
        deflectionCache?.takeIf { it.first == width }?.let { return it.second }

        val values = DoubleArray(width) { pixelX ->
            val dist = abs(pixelX.toDouble() / width - 0.5)
            val curve = (1.0 - 2.0.pow(DEFLECTION_STRENGTH) * dist.pow(DEFLECTION_STRENGTH)).coerceAtLeast(0.0)

            0.02 + 0.98 * curve
        }

        deflectionCache = width to values

        return values
    }

    /** The string's stroke moved down by [dy] CSS pixels (up when negative), in its colour, with [tint] over it. */
    private fun strokeToned(path: Path2D, dy: Double, tint: Color?) {
        strokeShifted(path = path, dy = dy, color = props.strokeColor, width = props.strokeWidth)

        tint?.let { strokeShifted(path = path, dy = dy, color = it, width = props.strokeWidth) }
    }

    /** Strokes [path] moved down by [dy] CSS pixels (up when negative). */
    private fun strokeShifted(path: Path2D, dy: Double, color: Color, width: Double) {
        val ctx = ctx2d ?: return

        ctx.save()
        ctx.translate(0.0, dy)
        ctx.strokeStyle = color.toString()
        ctx.lineWidth = width
        // No corner pokes out of the thick string or its shadow
        ctx.lineJoin = CanvasLineJoin.ROUND
        ctx.lineCap = CanvasLineCap.ROUND
        ctx.stroke(path)
        ctx.restore()
    }

    private fun CanvasRenderingContext2D.fadeout(amount: Double) {
        save()

        // The whole backing store, whatever the drawing's scale
        setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        globalCompositeOperation = "destination-in"
        fillStyle = Color.black.withAlpha(1.0 - amount).toString()
        fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())

        restore()
    }

    override fun VDom.render() {
        div {
            key = "oscilloscope"

            css {
                height = 100.pct
            }

            canvas("oscilloscope-canvas") {}
        }
    }
}

/**
 * The lowest and the highest of [buffer] from [start] until [end], in the order the signal reached them: the one
 * reached first first.
 */
internal fun extremesInOrder(buffer: Float32Array, start: Int, end: Int): Pair<Float, Float> {
    var min = buffer[start]
    var max = min
    var minAt = start
    var maxAt = start

    for (i in start + 1 until end) {
        val value = buffer[i]

        if (value < min) {
            min = value
            minAt = i
        }

        if (value > max) {
            max = value
            maxAt = i
        }
    }

    return if (minAt <= maxAt) min to max else max to min
}

/**
 * The trace's points, in drawing order, and the path through them: round (the maintainer, 2026-10-28: "i do not like the
 * spikeness of the osci ... can we get the mountains and valleys more "round"?"). The path runs through the midpoints
 * between the points with quadratic curves, each point their control: a peak becomes an arc, the wave keeps its shape.
 */
private class TracePoints(capacity: Int) {
    private val xs = DoubleArray(capacity)
    private val ys = DoubleArray(capacity)
    private var size = 0

    fun add(x: Double, y: Double) {
        if (size < xs.size) {
            xs[size] = x
            ys[size] = y
            size++
        }
    }

    fun roundedPath(): Path2D {
        val path = Path2D()

        if (size == 0) {
            return path
        }

        path.moveTo(xs[0], ys[0])

        for (i in 1 until size - 1) {
            path.quadraticCurveTo(xs[i], ys[i], (xs[i] + xs[i + 1]) / 2.0, (ys[i] + ys[i + 1]) / 2.0)
        }

        if (size > 1) {
            path.lineTo(xs[size - 1], ys[size - 1])
        }

        return path
    }
}
