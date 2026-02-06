package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import io.peekandpoke.klang.audio_bridge.createVisualizerBuffer
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.browser.window
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
    player: () -> KlangPlayer?,
) = comp(
    Oscilloscope.Props(player = player)
) {
    Oscilloscope(it)
}

class Oscilloscope(ctx: Ctx<Props>) : Component<Oscilloscope.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val player: () -> KlangPlayer?,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var visualizerAnimFrame: Int? = null
    private val visualizerBuffer = createVisualizerBuffer(2048)

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

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
        ctx.strokeStyle = "#808080"
        ctx.lineWidth = 1.0
        ctx.beginPath()
        ctx.moveTo(0.0, centerY)
        ctx.lineTo(width, centerY)
        ctx.stroke()

        // Draw waveform (classic green oscilloscope)
        ctx.strokeStyle = "#FFFFFF"
        ctx.lineWidth = 2.0
        ctx.beginPath()

        val bufferLength = visualizerBuffer.length
        val sliceWidth = width / bufferLength.toDouble()

        for (i in 0 until bufferLength) {
            val value = visualizerBuffer[i]  // -1.0 to 1.0
            val y = centerY - (value * centerY)  // Flip Y axis (canvas Y grows downward)
            val x = i * sliceWidth

            if (i == 0) {
                ctx.moveTo(x, y)
            } else {
                ctx.lineTo(x, y)
            }
        }

        ctx.stroke()
    }

    init {
        lifecycle {
            onMount {
                visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }

                dom?.let { d ->
                    canvas = dom?.querySelector("canvas") as HTMLCanvasElement?
                    canvas?.let {
                        it.width = d.clientWidth
                        it.height = d.clientHeight
                    }

                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D
                }
            }

            onUnmount {
                visualizerAnimFrame?.let { window.cancelAnimationFrame(it) }
            }
        }
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
