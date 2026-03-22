package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.onResize
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

@Suppress("FunctionName")
fun Tag.Spectrumeter(
    colors: List<Color> = listOf(
        KlangTheme.excellent,
        KlangTheme.good,
        KlangTheme.moderate,
        KlangTheme.warning,
        KlangTheme.critical,
    ),
    gap: Double = 1.0,
    boxGap: Double = 2.0,
    numBoxesInStack: Int = 30,
    numBuckets: Int = 16,
    binning: SpectrumBinning = SpectrumBinning(numBuckets = numBuckets),
    player: () -> KlangPlayer?,
) = comp(
    Spectrumeter.Props(
        player = player,
        colors = colors,
        gap = gap,
        boxGap = boxGap,
        numBoxesInStack = numBoxesInStack,
        numBuckets = numBuckets,
        binning = binning,
    )
) {
    Spectrumeter(it)
}

class Spectrumeter(ctx: Ctx<Props>) : Component<Spectrumeter.Props>(ctx) {

    data class Props(
        val player: () -> KlangPlayer?,
        val colors: List<Color>,
        val gap: Double,
        val boxGap: Double,
        val numBoxesInStack: Int,
        val numBuckets: Int,
        val binning: SpectrumBinning,
    )

    private var visualizerAnimFrame: Int? = null

    // FFT size is typically 2048, resulting in 1024 frequency bins
    private val visualizerBuffer = createAnalyzerBuffer(1024).also { buffer ->
        // Initialize with silence (-100 dB) so nothing displays before audio starts
        for (i in 0 until buffer.length) {
            buffer[i] = -100.0f
        }
    }

    private val bucketValues = DoubleArray(props.numBuckets)

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
                }
            }

            onUnmount {
                visualizerAnimFrame?.let { window.cancelAnimationFrame(it) }
            }

            onResize { entries ->
                for (entry in entries) {
                    val width = entry.contentRect.width
                    val height = entry.contentRect.height

                    canvas?.let {
                        it.width = width.toInt()
                        it.height = height.toInt()
                    }
                }
            }
        }
    }

    private fun processVisualizer() {
        props.player()?.let { player ->
            // This fills the buffer with frequency data in Decibels (dB)
            player.getAnalyzer()?.getFft(visualizerBuffer)
        }

        drawSpectrum()

        visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }
    }

    private fun drawSpectrum() {
        val ctx = ctx2d ?: return
        val canvasElement = canvas ?: return

        val width = canvasElement.width.toDouble()
        val height = canvasElement.height.toDouble()

        // Clear canvas with fade trail effect
        ctx.save()
        ctx.globalCompositeOperation = "destination-out"
        ctx.fillStyle = "rgba(0, 0, 0, 0.25)"
        ctx.fillRect(0.0, 0.0, width, height)
        ctx.restore()

        // Process FFT data through perceptually balanced binning
        props.binning.process(fftBuffer = visualizerBuffer, out = bucketValues)

        val numBuckets = props.numBuckets
        val colors = props.colors
        val numColors = colors.size.coerceAtLeast(1)

        val barWidth = width / numBuckets

        val maxBoxes = props.numBoxesInStack
        val boxHeight = (height - (maxBoxes * props.boxGap)) / maxBoxes
        val boxWithGap = boxHeight + props.boxGap

        for (bucketIdx in 0 until numBuckets) {
            val normalized = bucketValues[bucketIdx]

            val numBoxesToDraw = (normalized * maxBoxes).toInt()

            val x = bucketIdx * barWidth
            val effectiveWidth = if (barWidth > props.gap) barWidth - props.gap else barWidth
            val boxAlpha = 0.5 + (normalized * 0.5)

            val colorsWithAlpha = colors.map { it.withAlpha(boxAlpha) }

            for (boxIdx in 0 until numBoxesToDraw) {
                val boxY = height - ((boxIdx + 1) * boxWithGap)

                val colorProgress = boxIdx.toDouble() / maxBoxes.coerceAtLeast(1)
                val colorIdx = (colorProgress * (numColors - 1)).toInt().coerceIn(0, numColors - 1)
                val boxColor = colorsWithAlpha[colorIdx]

                ctx.fillStyle = boxColor.toString()
                ctx.fillRect(x, boxY, effectiveWidth, boxHeight)
            }
        }
    }

    override fun VDom.render() {
        div {
            key = "spectrum-visualizer"
            css {
                height = 100.pct
            }
            canvas("spectrum-canvas") {
                css {
                    display = Display.block
                }
            }
        }
    }
}
