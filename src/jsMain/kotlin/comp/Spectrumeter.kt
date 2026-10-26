/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.onResize
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.roundWithPrecision
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.html.onMouseMove
import io.peekandpoke.ultra.maths.Ease
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.LinearDimension
import kotlinx.css.Position
import kotlinx.css.bottom
import kotlinx.css.display
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.pct
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.floor
import kotlin.math.roundToInt

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
    labelMiddle: LinearDimension = 12.px,
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
        labelMiddle = labelMiddle,
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
        /** How high over the spectrum's floor the middle of a hovered column's frequencies sits. */
        val labelMiddle: LinearDimension,
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

    /** The column under the mouse, whose frequencies the label shows; null while the mouse is elsewhere. */
    private var hovered: Int? by value(null)

    /** Accumulated "heat" driving the gold glow. Energy pumps heat in, cooling bleeds it out. */
    private var glowHeat: Double = 0.0

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
        ctx.fillStyle = "rgba(0, 0, 0, 0.66)"
        ctx.fillRect(0.0, 0.0, width, height)
        ctx.restore()

        // Process FFT data through perceptually balanced binning
        props.binning.process(fftBuffer = visualizerBuffer, out = bucketValues)

        // Total energy this frame drives a slow "heating iron" model:
        //   heat += instantEnergy * heatRate   (energy pumps heat in)
        //   heat *= cooling                    (radiative cooling)
        //   heat is clamped to [0, 1]          (saturates at peak glow)
        // heatRate=0.01, cooling=0.994 → same equilibrium glow as the original
        // 0.005/0.997 tuning (equilibrium = energy·heatRate/(1−cooling)) but the
        // dynamics run twice as fast: ramp ~3 s at 60 fps, decay half-life ≈ 1.9 s.
        var frameSum = 0.0
        for (v in bucketValues) frameSum += v
        val instantEnergy = (frameSum / bucketValues.size.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        // Ignition threshold — background noise / quiet passages below this contribute
        // no heat, so the iron only glows under real audio energy.
        val heatThreshold = 0.12
        val effectiveEnergy = ((instantEnergy - heatThreshold) / (1.0 - heatThreshold)).coerceAtLeast(0.0)
        val heatRate = 0.01
        val cooling = 0.994
        val heatCeiling = 1.8
        glowHeat = ((glowHeat + effectiveEnergy * heatRate) * cooling).coerceIn(0.0, heatCeiling)
        val glowEnergy = glowHeat

        // Gradient matches the bar palette from bottom to top, intensity scaled by heat.
        // Ease-in on heat keeps early buildup subtle before the glow really lights up.
        val heatNorm = glowEnergy / heatCeiling
        val heatCurve = Ease.In.pow(1.15).invoke(heatNorm)
        // Top-end boost — near full engagement the glow steps up a further ~20%;
        // the cubic ease-in keeps mid-heat levels virtually unaffected.
        val fullBoost = 1.0 + 0.2 * Ease.In.pow(2.0).invoke(heatNorm)
        val glow = ctx.createLinearGradient(0.0, height, 0.0, 0.0)
        val palette = props.colors
        val n = palette.size.coerceAtLeast(1)
        for (i in 0 until n) {
            val t = if (n == 1) 0.0 else i.toDouble() / (n - 1)
            // Ease-out falloff — 0.45 at bottom, reaches 0 by ~77% up (faster than full-height).
            val aMax = 0.6 * Ease.Out.quad((1.0 - t * 1.3).coerceAtLeast(0.0))

            // Sometimes parsing the color fails, so we need to round the alpha:
            // Failed to execute 'addColorStop' on 'CanvasGradient': The value provided ('rgba(209, 154, 102, 6e-320.0)') could not be parsed as a color.
            val alpha = (aMax * heatCurve * fullBoost).coerceAtMost(1.0).roundWithPrecision(5)

            glow.addColorStop(t, palette[i].withAlpha(alpha).toString())
        }
        ctx.fillStyle = glow
        ctx.fillRect(0.0, 0.0, width, height)

        val numBuckets = props.numBuckets
        val colors = props.colors
        val numColors = colors.size.coerceAtLeast(1)

        // Whole pixels per column, the rest as padding, half on the left (the maintainer, 2026-10-26)
        val columns = spectrumColumns(width = width.toInt(), count = numBuckets)

        val maxBoxes = props.numBoxesInStack
        // Whole pixels per cell and per gap, stacked up from the floor, so the cells are crisp too (the maintainer,
        // 2026-10-26); the pixels left over stay empty at the top
        val gap = props.boxGap.roundToInt()
        val boxHeight = ((height.toInt() - maxBoxes * gap) / maxBoxes.coerceAtLeast(1)).coerceAtLeast(1)
        val boxWithGap = boxHeight + gap

        for (bucketIdx in 0 until numBuckets) {
            val normalized = bucketValues[bucketIdx]

            val numBoxesToDraw = (normalized * maxBoxes).toInt()

            val x = columns.xOf(bucketIdx).toDouble()
            val effectiveWidth = if (columns.step > props.gap) columns.step - props.gap else columns.step.toDouble()
            val boxAlpha = 0.5 + (normalized * 0.5)

            val colorsWithAlpha = colors.map { it.withAlpha(boxAlpha) }

            for (boxIdx in 0 until numBoxesToDraw) {
                val boxY = height - ((boxIdx + 1) * boxWithGap)

                val colorProgress = boxIdx.toDouble() / maxBoxes.coerceAtLeast(1)
                val colorIdx = (colorProgress * (numColors - 1)).toInt().coerceIn(0, numColors - 1)
                val boxColor = colorsWithAlpha[colorIdx]

                ctx.fillStyle = boxColor.toString()
                ctx.fillRect(x, boxY, effectiveWidth, boxHeight.toDouble())
            }
        }
    }

    /** The column under the mouse at [clientX], laid out as the columns are drawn; null over the padding. */
    private fun columnAt(clientX: Double): Int? {
        val rect = dom?.getBoundingClientRect() ?: return null

        return spectrumColumns(width = rect.width.toInt(), count = props.numBuckets)
            .columnAt(x = clientX - rect.left, count = props.numBuckets)
    }

    override fun VDom.render() {
        div {
            key = "spectrum-visualizer"
            css {
                height = 100.pct
                position = Position.relative
            }

            // Hovered, a column shows the frequencies it covers (the maintainer, 2026-10-26)
            onMouseMove { e ->
                val column = columnAt(e.clientX.toDouble())

                if (column != hovered) {
                    hovered = column
                }
            }
            onMouseLeave { hovered = null }

            canvas("spectrum-canvas") {
                css {
                    display = Display.block
                }
            }

            hovered?.let { column -> renderRange(column) }
        }
    }

    /**
     * The label, centred over the spectrum, its middle at [Props.labelMiddle]: one place, whichever column is hovered,
     * so it does not jump (the maintainer, 2026-10-26).
     */
    private fun FlowContent.renderRange(column: Int) {
        // As wide as the longest of all the columns' labels, so it keeps its size from column to column (the
        // maintainer, 2026-10-26); in a monospace font a character is exactly 1ch
        val widest = (0 until props.numBuckets).maxOf { rangeLabel(props.binning.frequencyRange(it)).length }

        div {
            css {
                position = Position.absolute
                bottom = props.labelMiddle
                left = 50.pct
                // Its middle on that line, both ways
                put("transform", "translate(-50%, 50%)")
                put("pointer-events", "none")
                put("padding", "1px 5px")
                put("border-radius", "3px")
                put("background", "rgba(0, 0, 0, 0.7)")
                // The gauges' rims' silver, as the scope's trace
                put("color", "var(--kl-metal-mid)")
                put("font-family", "monospace")
                put("font-size", "11px")
                put("white-space", "nowrap")
                // The text's width alone: Fomantic's border-box would take the padding out of it
                put("box-sizing", "content-box")
                put("width", "${widest}ch")
                put("text-align", "center")
            }

            +rangeLabel(props.binning.frequencyRange(column))
        }
    }
}

/** A column's frequencies as one reads them: "120 to 180 Hz", "800 Hz to 1 kHz", "1.2 to 1.8 kHz". */
internal fun rangeLabel(range: ClosedFloatingPointRange<Double>): String {
    val low = range.start
    val high = range.endInclusive

    fun hz(f: Double) = f.roundToInt().toString()

    fun khz(f: Double) = ((f / 100.0).roundToInt() / 10.0).toString()

    return when {
        high < 1000.0 -> "${hz(low)} to ${hz(high)} Hz"
        low >= 1000.0 -> "${khz(low)} to ${khz(high)} kHz"
        else -> "${hz(low)} Hz to ${khz(high)} kHz"
    }
}

/** Where the spectrum's columns go: each [step] whole pixels wide, after [left] pixels of padding. */
internal data class SpectrumColumns(val step: Int, val left: Int) {

    /** The left edge of [column]. */
    fun xOf(column: Int): Int = left + column * step

    /** The column at [x] pixels from the left, of [count]; null over the padding or without room for a column. */
    fun columnAt(x: Double, count: Int): Int? {
        if (step <= 0) {
            return null
        }

        val column = floor((x - left) / step).toInt()

        return column.takeIf { it in 0 until count }
    }
}

/** [count] columns in [width] pixels, pixel exact: as wide as fits whole, the pixels left over half on either side. */
internal fun spectrumColumns(width: Int, count: Int): SpectrumColumns {
    val step = if (count > 0) width.coerceAtLeast(0) / count else 0

    return SpectrumColumns(step = step, left = (width - step * count).coerceAtLeast(0) / 2)
}
