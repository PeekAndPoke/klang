/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.onResize
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.document
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
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Path2D
import org.w3c.dom.ROUND
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
    paused: Stream<Boolean> = UiWork.resting,
    swingPx: Double? = null,
    smokeRoom: Oscilloscope.SmokeRoom = Oscilloscope.SmokeRoom.None,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
        pixelSkip = pixelSkip,
        preview = preview,
        paused = paused,
        swingPx = swingPx,
        smokeRoom = smokeRoom,
    )
) {
    Oscilloscope(it)
}

/**
 * The oscilloscope: the newest frame of the player's waveform as a trace over a faint centre line, with a soft shadow
 * under it. The trace eases off towards both ends, as a string held at its ends. Under it, on a canvas of its own and
 * faint, its smoke: every frame of the trace drawn into it, and each frame what is there drawn back blurred, a little
 * bigger and a little fainter, so the trace seems to give off puffs of smoke (KLANG-00392).
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
        /** While true, nothing is drawn: the waveform is let go until it is false again ([UiWork.resting], the default). */
        val paused: Stream<Boolean>,
        /**
         * How far a full-scale signal swings from the centre line, in CSS pixels; null for half the height. Given, the
         * swing does not grow with a taller canvas, and a louder signal than there is room for is cut at the edge
         * (KLANG-00391).
         */
        val swingPx: Double?,
        /** How far past the scope its smoke may go, out over what lies round it. */
        val smokeRoom: SmokeRoom,
    )

    /**
     * How far the smoke may go past the scope, in CSS pixels: up most, it rises (the maintainer, 2026-10-30: "would
     * be nice if the smoke would cover the gauges", and in the visuals "if the smoke could come "out of the hole"").
     * [None] keeps it in the scope.
     */
    data class SmokeRoom(val top: Double, val sides: Double, val bottom: Double) {
        companion object {
            val None = SmokeRoom(top = 0.0, sides = 0.0, bottom = 0.0)
        }
    }

    companion object {
        /** The canvases' classes: the trace's, and its smoke's under it. */
        private const val TRACE_CANVAS = "oscilloscope-canvas"
        private const val SMOKE_CANVAS = "oscilloscope-smoke"

        /** The idle frame's length, all silence. */
        private const val IDLE_FRAME_SIZE = 2048

        /** How steeply the trace eases off towards its ends. */
        private const val DEFLECTION_STRENGTH = 4.0

        /**
         * How much of the smoke each frame keeps, drawn back blurred by [RADIANCE_BLUR_PX]: frame after frame it spreads
         * and fades (the maintainer, 2026-10-30: "overpaint the current contents with 90% black ... plus blurring the
         * current content on every render step"). Slow, now that the smoke has a canvas of its own and shows faint
         * ([SMOKE_OPACITY]): it lingers without smearing the trace ("a second canvas for the "smoke" ... then we do not
         * need to fade out so fast but can keep the "smoke" at a low opacity"; on the trace's canvas it kept 0.8; then
         * 0.94 faded too slowly: "a bit stronger but fade a bit faster").
         */
        private const val RADIANCE_KEEP = 0.9

        /** How much the smoke's canvas shows over the scope's ground: faint, the trace stays clear over it (0.4 before). */
        private const val SMOKE_OPACITY = 0.35

        /** The blur each frame, in CSS pixels: a few frames on, the trail is a few pixels wide. */
        private const val RADIANCE_BLUR_PX = 1.0

        /**
         * How far the last frame grows on every side each frame, in CSS pixels: drawn back a little bigger round the
         * middle, the trails drift outward like puffs of smoke (the maintainer, 2026-10-30: "draw the previous state a
         * bit bigger, so that it appears like "smoke puffs" going into all directions"). The same distance up and down
         * as sideways, so a wide scope's puffs do not only spread sideways.
         */
        private const val RADIANCE_GROW_PX = 1.15

        /**
         * How far the smoke moves up each frame, in CSS pixels, as it grows: it rises (the maintainer, 2026-10-30: "on
         * every redraw move the zoomed paint up slightly, so that it looks as if the smoke rises").
         */
        private const val SMOKE_RISE_PX = 0.1

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

    /** The smoke's canvas, under the trace's, and its context ([radiate]). */
    private var smoke: HTMLCanvasElement? = null
    private var smoke2d: CanvasRenderingContext2D? = null

    /** The smoke's last frame, copied aside to be drawn back blurred ([radiate]); the canvases' size. */
    private val lastFrame = document.createElement("canvas") as HTMLCanvasElement
    private val lastFrame2d = lastFrame.getContext("2d") as? CanvasRenderingContext2D

    /** The backing store per CSS pixel, for the blur's width in the store's pixels. */
    private var backingRatio = 1.0

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
        props.player.map { it?.getAnalyzer() }.switchMapNotNull { it.waveform }.cutoffWhen(props.paused)
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
                    canvas = container.querySelector("canvas.$TRACE_CANVAS") as HTMLCanvasElement?
                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D
                    smoke = container.querySelector("canvas.$SMOKE_CANVAS") as HTMLCanvasElement?
                    smoke2d = smoke?.getContext("2d") as? CanvasRenderingContext2D

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
        backingRatio = ratio
        c.width = (width * ratio).roundToInt()
        c.height = (height * ratio).roundToInt()
        c.style.width = "${width}px"
        c.style.height = "${height}px"
        ctx2d?.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)

        // The smoke's canvas reaches past the scope by its room, the scope's own place inside it
        smoke?.let { under ->
            val room = props.smokeRoom
            val smokeWidth = width + 2 * room.sides
            val smokeHeight = height + room.top + room.bottom

            under.width = (smokeWidth * ratio).roundToInt()
            under.height = (smokeHeight * ratio).roundToInt()
            under.style.width = "${smokeWidth}px"
            under.style.height = "${smokeHeight}px"
            under.style.left = "${-room.sides}px"
            under.style.top = "${-room.top}px"
            lastFrame.width = under.width
            lastFrame.height = under.height
            smoke2d?.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        }
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

        // The trace's canvas shows this frame alone; the smoke moves on under it
        ctx.save()
        ctx.setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        ctx.clearRect(0.0, 0.0, ctx.canvas.width.toDouble(), ctx.canvas.height.toDouble())
        ctx.restore()

        smoke2d?.radiate()

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

        fun yOf(value: Float, x: Double): Double = traceY(
            value = value * easeOff[x.toInt().coerceIn(0, easeOff.size - 1)],
            centerY = centerY,
            swingPx = props.swingPx,
        )

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

        // Built once, stroked for the smoke, the shadow, then the string
        val path = points.roundedPath()

        // The trace into the smoke, plain, in its colour, where the scope lies in the smoke's canvas
        smoke2d?.let { under ->
            under.save()
            under.translate(props.smokeRoom.sides, props.smokeRoom.top)
            under.strokeStyle = props.strokeColor.toString()
            under.lineWidth = props.strokeWidth
            under.lineJoin = CanvasLineJoin.ROUND
            under.lineCap = CanvasLineCap.ROUND
            under.stroke(path)
            under.restore()
        }

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

    /**
     * The last frame back, blurred, fainter, a little bigger and a little higher ([RADIANCE_KEEP], [RADIANCE_BLUR_PX],
     * [RADIANCE_GROW_PX], [SMOKE_RISE_PX]): copied aside, the canvas cleared, the copy drawn over it. A browser without canvas filters draws it unblurred, an afterglow that only fades.
     */
    private fun CanvasRenderingContext2D.radiate() {
        val width = canvas.width.toDouble()
        val height = canvas.height.toDouble()
        val aside = lastFrame2d

        // The whole backing store, whatever the drawing's scale
        save()
        setTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)

        if (aside == null || lastFrame.width != canvas.width || lastFrame.height != canvas.height) {
            clearRect(0.0, 0.0, width, height)
        } else {
            aside.clearRect(0.0, 0.0, width, height)
            aside.drawImage(canvas, 0.0, 0.0)

            val grow = RADIANCE_GROW_PX * backingRatio
            val rise = SMOKE_RISE_PX * backingRatio
            val room = props.smokeRoom
            // It grows round the scope's middle, not the smoke canvas's: with more room above than below, growing round
            // the canvas's middle pushed the scope's smoke down, it fell (the maintainer, 2026-10-30: "The smoke now
            // "falls" down"). The scope's edges move out by [grow], as before the room was there
            val middleX = (room.sides + cssWidth / 2.0) * backingRatio
            val middleY = (room.top + cssHeight / 2.0) * backingRatio
            // NaN-guard: a scope of no size grows by nothing
            val scaleX = 1.0 + ((grow / (cssWidth / 2.0 * backingRatio)).takeIf { it.isFinite() } ?: 0.0)
            val scaleY = 1.0 + ((grow / (cssHeight / 2.0 * backingRatio)).takeIf { it.isFinite() } ?: 0.0)

            clearRect(0.0, 0.0, width, height)
            asDynamic().filter = "blur(${RADIANCE_BLUR_PX * backingRatio}px)"
            globalAlpha = RADIANCE_KEEP
            drawImage(
                lastFrame,
                middleX - middleX * scaleX,
                middleY - middleY * scaleY - rise,
                width * scaleX,
                height * scaleY,
            )
        }

        restore()
    }

    override fun VDom.render() {
        div {
            key = "oscilloscope"

            css {
                height = 100.pct
                put("position", "relative")
            }

            // The smoke under the trace, faint
            canvas(SMOKE_CANVAS) {
                css {
                    put("position", "absolute")
                    put("left", "0")
                    put("top", "0")
                    put("opacity", SMOKE_OPACITY.toString())
                    put("pointer-events", "none")
                }
            }

            canvas(TRACE_CANVAS) {
                css {
                    put("position", "relative")
                }
            }
        }
    }
}

/**
 * Where a sample of [value] (-1 to 1 at full scale) is drawn: [swingPx] from the centre line at [centerY] at full scale,
 * up for a positive value; with no [swingPx], half the height ([centerY]).
 */
internal fun traceY(value: Double, centerY: Double, swingPx: Double?): Double {
    // NaN-guard: a swing that is not a positive number is half the height
    val swing = swingPx?.takeIf { it > 0.0 } ?: centerY

    return centerY - value * swing
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
