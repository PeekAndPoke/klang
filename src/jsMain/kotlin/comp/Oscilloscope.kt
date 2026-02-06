package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import io.peekandpoke.klang.audio_bridge.createVisualizerBuffer
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.externals.ResizeObserver
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.khronos.webgl.get
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

@Suppress("FunctionName")
fun Tag.Oscilloscope(
    strokeColor: Color = Color.white,
    strokeWidth: Double = 1.2,
    centerLineColor: Color? = Color.white.withAlpha(0.2),
    centerLineWidth: Double = 1.0,
    player: () -> KlangPlayer?,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
    )
) {
    Oscilloscope(it)
}

class Oscilloscope(ctx: Ctx<Props>) : Component<Oscilloscope.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val player: () -> KlangPlayer?,
        val strokeColor: Color,
        val strokeWidth: Double,
        val centerLineColor: Color?,
        val centerLineWidth: Double,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var visualizerAnimFrame: Int? = null
    private val visualizerBuffer = createVisualizerBuffer(2048)

    private var resizeObserver: ResizeObserver? = null
    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

    init {
        lifecycle {
            onMount {
                visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }

                dom?.let { container ->
                    canvas = dom?.querySelector("canvas") as HTMLCanvasElement?
                    canvas?.let {
                        it.width = container.clientWidth
                        it.height = container.clientHeight
                    }

                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D

                    resizeObserver = ResizeObserver { entries, _ ->
                        for (entry in entries) {
                            // 'target' is the element being observed
                            val width = entry.contentRect.width
                            val height = entry.contentRect.height

                            canvas?.let {
                                it.width = width.toInt()
                                it.height = height.toInt()
                            }
                        }
                    }
                    resizeObserver?.observe(container)
                }
            }

            onUnmount {
                visualizerAnimFrame?.let { window.cancelAnimationFrame(it) }
                resizeObserver?.disconnect()
            }
        }
    }

    private fun processVisualizer() {
        props.player()?.let { player ->
            player.getVisualizer()?.getWaveform(visualizerBuffer)
        }

        drawWaveform()

        visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }
    }

    private fun drawWaveform() {
        val ctx = ctx2d ?: return
        val canvasElement = canvas ?: return

        val width = canvasElement.width.toDouble()
        val height = canvasElement.height.toDouble()
        val centerY = height / 2.0

        // Clear canvas (black background)
        ctx.clearRect(0.0, 0.0, width, height)

        // Draw center line (dark gray)
        props.centerLineColor?.let { clc ->
            ctx.strokeStyle = clc.toString()
            ctx.lineWidth = props.centerLineWidth
            ctx.beginPath()
            ctx.moveTo(0.0, centerY)
            ctx.lineTo(width, centerY)
            ctx.stroke()
        }

        // Draw waveform (classic green oscilloscope)
        ctx.strokeStyle = props.strokeColor.toString()
        ctx.lineWidth = props.strokeWidth
        ctx.beginPath()

        val bufferLength = visualizerBuffer.length
        val widthInt = width.toInt()

        if (widthInt >= bufferLength) {
            // More pixels than samples - draw all points
            val sliceWidth = width / bufferLength.toDouble()
            for (i in 0 until bufferLength) {
                val value = visualizerBuffer[i]
                val y = centerY - (value * centerY)
                val x = i * sliceWidth

                if (i == 0) {
                    ctx.moveTo(x, y)
                } else {
                    ctx.lineTo(x, y)
                }
            }
        } else {
            // More samples than pixels - downsample by showing min/max per pixel bin
            val samplesPerPixel = bufferLength / widthInt

            for (pixelX in 0 until widthInt) {
                val startIdx = pixelX * samplesPerPixel
                val endIdx = minOf(startIdx + samplesPerPixel, bufferLength)

                // Find min and max in this bin to preserve peak information
                var minVal = visualizerBuffer[startIdx]
                var maxVal = visualizerBuffer[startIdx]

                for (i in startIdx + 1 until endIdx) {
                    val value = visualizerBuffer[i]
                    if (value < minVal) minVal = value
                    if (value > maxVal) maxVal = value
                }

                val x = pixelX.toDouble()
                val yMin = centerY - (minVal * centerY)
                val yMax = centerY - (maxVal * centerY)

                if (pixelX == 0) {
                    ctx.moveTo(x, yMax)
                }

                // Draw vertical line from min to max for this pixel
                ctx.lineTo(x, yMax)
                ctx.lineTo(x, yMin)
            }
        }

        ctx.stroke()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

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
