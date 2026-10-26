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
import kotlinx.css.Color
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Path2D
import kotlin.math.abs
import kotlin.math.pow

@Suppress("FunctionName")
fun Tag.Oscilloscope(
    player: Stream<KlangPlayer?>,
    strokeColor: Color = Color.white,
    strokeWidth: Double = 1.5,
    centerLineColor: Color? = Color.white.withAlpha(0.07),
    centerLineWidth: Double = 1.0,
    pixelSkip: Int = 3,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
        pixelSkip = pixelSkip,
    )
) {
    Oscilloscope(it)
}

/**
 * The oscilloscope: the newest frame of the player's waveform as a trace over a faint centre line, drawn as a metal
 * string lit from above (a darker shade under a brighter top). The trace eases off towards both ends, as a string
 * held at its ends, and each frame fades the last one out, a short afterglow.
 * Without a player it draws the idle line.
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
    )

    companion object {
        /** The idle frame's length, all silence. */
        private const val IDLE_FRAME_SIZE = 2048

        /** How steeply the trace eases off towards its ends. */
        private const val DEFLECTION_STRENGTH = 4.0

        /** How much of the last frame each new one fades out: the afterglow. */
        private const val AFTERGLOW_FADE = 0.66

        /** How far below the lit top the string's shade shows, in pixels. */
        private const val STRING_SHADE_OFFSET = 1.0

        /** How much darker the string's shade is than the trace's colour, in percent. */
        private const val STRING_SHADE_DARKEN = 35

        /** How much brighter the string's lit top is than the trace's colour, in percent. */
        private const val STRING_TOP_LIGHTEN = 20

        private val idleBuffer = Float32Array(IDLE_FRAME_SIZE)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var lastHistory: AnalyzerBufferHistory? = null

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

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
                    canvas?.let {
                        it.width = container.clientWidth
                        it.height = container.clientHeight
                    }

                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D
                }

                redraw()
            }

            onResize { entries ->
                for (entry in entries) {
                    canvas?.let {
                        it.width = entry.contentRect.width.toInt()
                        it.height = entry.contentRect.height.toInt()
                    }

                    // A resized canvas is cleared: draw again
                    redraw()
                }
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** The newest frame of the last waveform, else the idle line. */
    private fun redraw() {
        val history = lastHistory

        if (history != null && history.size > 0) {
            draw(buffer = history[0], bufferLength = history.bufferSize)
        } else {
            draw(buffer = idleBuffer, bufferLength = IDLE_FRAME_SIZE)
        }
    }

    private fun draw(buffer: Float32Array, bufferLength: Int) {
        val ctx = ctx2d ?: return
        val c = canvas ?: return
        val width = c.width
        val centerY = c.height / 2.0

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

        // Built once, stroked three times: the dark band, the string's shade, its lit top
        val path = Path2D()

        if (width >= bufferLength) {
            // More pixels than samples: a point per sample
            val sliceWidth = width.toDouble() / bufferLength

            for (i in 0 until bufferLength step step) {
                val x = i * sliceWidth
                val y = yOf(value = buffer[i], x = x)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
        } else {
            // More samples than pixels: each drawn pixel spans its samples' lowest and highest
            val samplesPerPixel = bufferLength / width

            for (pixelX in 0 until width step step) {
                val start = pixelX * samplesPerPixel
                val end = minOf(start + samplesPerPixel, bufferLength)

                var min = buffer[start]
                var max = min

                for (i in start + 1 until end) {
                    val value = buffer[i]

                    if (value < min) {
                        min = value
                    }

                    if (value > max) {
                        max = value
                    }
                }

                val x = pixelX.toDouble()

                if (pixelX == 0) {
                    path.moveTo(x, yOf(value = max, x = x))
                }

                path.lineTo(x, yOf(value = max, x = x))
                path.lineTo(x, yOf(value = min, x = x))
            }
        }

        // A dark band behind the trace, for contrast
        ctx.strokeStyle = Color.black.withAlpha(0.25).toString()
        ctx.lineWidth = props.strokeWidth + 5.0
        ctx.stroke(path)

        // A swinging string, lit from above as the gauges' rims are (the maintainer, 2026-10-26): its shade a little
        // lower and darker, its lit top brighter over it; both shades of the trace's own colour, so they follow the look
        ctx.save()
        ctx.translate(0.0, STRING_SHADE_OFFSET)
        ctx.strokeStyle = props.strokeColor.darken(STRING_SHADE_DARKEN).toString()
        ctx.lineWidth = props.strokeWidth
        ctx.stroke(path)
        ctx.restore()

        ctx.strokeStyle = props.strokeColor.lighten(STRING_TOP_LIGHTEN).toString()
        ctx.lineWidth = props.strokeWidth
        ctx.stroke(path)
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

    private fun CanvasRenderingContext2D.fadeout(amount: Double) {
        save()

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
