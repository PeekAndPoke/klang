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
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement

@Suppress("FunctionName")
fun Tag.Spectrumeter(
    colors: List<Color> = listOf(
        Color.lightSkyBlue,
        Color.yellowGreen,
        Color.yellow,
        Color.orange,
        Color.red,
    ),
//    colors: List<Color> = listOf(Color.white),
    gap: Double = 1.0,
    boxGap: Double = 2.0,
    numBoxesInStack: Int = 30,
    numBuckets: Int = 16,
    /**
     * FFT bin range to visualize. The FFT provides 1024 frequency bins (0-1023).
     *
     * At 48kHz sample rate with 2048 FFT size:
     * - Bin 0:    0 Hz        (DC component - always high, usually skipped)
     * - Bin 1:    23 Hz       (sub-bass)
     * - Bin 10:   234 Hz      (low bass)
     * - Bin 21:   491 Hz      (bass)
     * - Bin 100:  2,340 Hz    (midrange)
     * - Bin 512:  11,988 Hz   (treble)
     * - Bin 1023: 23,977 Hz   (high treble)
     *
     * Musical ranges:
     * - Sub-bass: Bins 1-4 (20-100 Hz)
     * - Bass: Bins 5-10 (100-250 Hz)
     * - Midrange: Bins 11-170 (250-4000 Hz)
     * - Treble: Bins 171-853 (4000-20000 Hz)
     */
    binRange: IntRange = 1..1023,  // Skip bin 0 (DC component) by default
    player: () -> KlangPlayer?,
) = comp(
    Spectrumeter.Props(
        player = player,
        colors = colors,
        gap = gap,
        boxGap = boxGap,
        numBoxesInStack = numBoxesInStack,
        numBuckets = numBuckets,
        binRange = binRange,
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
        val binRange: IntRange,
    )

    private var visualizerAnimFrame: Int? = null

    // FFT size is typically 2048, resulting in 1024 frequency bins
    private val visualizerBuffer = createVisualizerBuffer(1024).also { buffer ->
        // Initialize with silence (-100 dB) so nothing displays before audio starts
        for (i in 0 until buffer.length) {
            buffer[i] = -100.0f
        }
    }

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
            // This fills the buffer with frequency data in Decibels (dB)
            player.getVisualizer()?.getFft(visualizerBuffer)
        }

        drawSpectrum()

        visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }
    }

    private fun drawSpectrum() {
        val ctx = ctx2d ?: return
        val canvasElement = canvas ?: return

        val width = canvasElement.width.toDouble()
        val height = canvasElement.height.toDouble()

        // Clear canvas
        ctx.save()
        // 'destination-out' makes new drawing "erase" existing content based on alpha
        ctx.globalCompositeOperation = "destination-out"

        // The alpha (0.1) controls the fade speed.
        // 0.1 = slow trails (long fade)
        // 0.5 = fast fade
        ctx.fillStyle = "rgba(0, 0, 0, 0.2)"
        ctx.fillRect(0.0, 0.0, width, height)

        ctx.restore() // Restore to "source-over" to draw the new bars normally

        val bufferLength = visualizerBuffer.length

        // Apply bin range (e.g., skip DC component at bin 0)
        val startBinRange = props.binRange.first.coerceIn(0, bufferLength - 1)
        val endBinRange = props.binRange.last.coerceIn(startBinRange, bufferLength - 1)
        val effectiveBinCount = endBinRange - startBinRange + 1

        // Check if there's any actual audio data (not just silence/noise)
        // Use a stricter threshold to avoid showing bars for background noise
        var hasData = false
        for (i in startBinRange..endBinRange) {
            val dbValue = visualizerBuffer[i].toDouble()
            // If any bin is above -60dB, consider it as having actual audio
            if (dbValue > -60.0) {
                hasData = true
                break
            }
        }

        // Don't draw anything if there's no audio data
        if (!hasData) return

        val numBuckets = props.numBuckets.coerceIn(1, effectiveBinCount)
        val colors = props.colors
        val numColors = colors.size.coerceAtLeast(1)

        // Group frequency bins into buckets for old-school spectrum analyzer look
        val barWidth = width / numBuckets
        val binsPerBucket = effectiveBinCount / numBuckets

        // Calculate box height dynamically based on canvas height and desired number of boxes
        val maxBoxes = props.numBoxesInStack
        val boxHeight = (height - (maxBoxes * props.boxGap)) / maxBoxes
        val boxWithGap = boxHeight + props.boxGap

        for (bucketIdx in 0 until numBuckets) {
            val startBin = startBinRange + (bucketIdx * binsPerBucket)
            val endBin =
                if (bucketIdx == numBuckets - 1) endBinRange + 1 else startBinRange + ((bucketIdx + 1) * binsPerBucket)

            // Find the maximum dB value in this bucket (shows peaks better than average)
            var maxDb = -100.0
            for (i in startBin until endBin) {
                val dbValue = visualizerBuffer[i].toDouble()
                if (dbValue > maxDb) maxDb = dbValue
            }

            // Normalize dB values to a 0.0 - 1.0 range for drawing.
            // Typical range is around -100dB (silence) to -30dB or 0dB (loud).
            // We map -100dB to 0.0 height and 0dB to 1.0 height.
            val normalized = ((maxDb + 100) / 100.0).coerceIn(0.0, 1.0)

            // Calculate how many boxes to light up for this bar
            val numBoxesToDraw = (normalized * maxBoxes).toInt()

            val x = bucketIdx * barWidth
            val effectiveWidth = if (barWidth > props.gap) barWidth - props.gap else barWidth
            val boxAlpha = 0.5 + (normalized * 0.5)

            val colorsWithAlpha = colors.map { it.withAlpha(boxAlpha) }

            // Draw stacked boxes from bottom to top
            for (boxIdx in 0 until numBoxesToDraw) {
                // Position from bottom (y increases downward in canvas)
                val boxY = height - ((boxIdx + 1) * boxWithGap)

                // Color gradient: bottom boxes (0%) use first color, top boxes (100%) use last color
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
            canvas("spectrum-canvas") {}
        }
    }
}
