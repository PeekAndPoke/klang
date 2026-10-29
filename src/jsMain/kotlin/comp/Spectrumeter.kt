/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.spine.Frames
import io.peekandpoke.klang.spine.UiWork
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
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.cutoffWhen
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
import kotlin.math.pow
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
    colorStep: Double? = null,
    numBuckets: Int = 16,
    binning: SpectrumBinning = SpectrumBinning(numBuckets = numBuckets),
    labelMiddle: LinearDimension = 12.px,
    lighting: Spectrumeter.Lighting = Spectrumeter.Lighting.Own,
    labelAtTop: Boolean = false,
    preview: List<Double>? = null,
    paused: Stream<Boolean> = UiWork.resting,
    player: () -> KlangPlayer?,
) = comp(
    Spectrumeter.Props(
        player = player,
        paused = paused,
        preview = preview,
        colors = colors,
        gap = gap,
        boxGap = boxGap,
        numBoxesInStack = numBoxesInStack,
        colorStep = colorStep,
        numBuckets = numBuckets,
        binning = binning,
        labelMiddle = labelMiddle,
        lighting = lighting,
        labelAtTop = labelAtTop,
    )
) {
    Spectrumeter(it)
}

class Spectrumeter(ctx: Ctx<Props>) : Component<Spectrumeter.Props>(ctx) {

    data class Props(
        val player: () -> KlangPlayer?,
        /** While true, the spectrum is neither read nor drawn: its loop rests until it is false again ([UiWork.resting], the default). */
        val paused: Stream<Boolean>,
        /**
         * Still levels per column, 0 to 1, drawn instead of the player's spectrum: a picture of itself (the look
         * editor's card, KLANG-00382; the maintainer, 2026-10-28: "these component implement a preview mode, when they
         * get static data"); null for the live spectrum.
         */
        val preview: List<Double>?,
        val colors: List<Color>,
        val gap: Double,
        val boxGap: Double,
        val numBoxesInStack: Int,
        /**
         * The share of a column each colour's band takes, from the floor; the last colour goes on to the top. Null
         * spreads the colours evenly over the whole column.
         */
        val colorStep: Double?,
        val numBuckets: Int,
        val binning: SpectrumBinning,
        /** How high over the spectrum's floor the middle of a hovered column's frequencies sits. */
        val labelMiddle: LinearDimension,
        /** How the columns are lit by their energy ([Lighting]). */
        val lighting: Lighting,
        /** The hovered column's frequencies at the top edge instead of at [labelMiddle] over the floor. */
        val labelAtTop: Boolean,
    )

    /**
     * How a column is lit by its energy (the maintainer, 2026-10-29: "Make this effect configurable"): [floor] for a
     * silent column, full for a loud one, [power] shaping the step between. The levels are decibels already (0 to 1
     * spans [DB_RANGE], linear in dB), so both ways measure on the log scale (the maintainer: "the decibel scale is
     * logarithmic ... so should be the lighting"):
     *
     * - not [relative]: a column by its own level, fully lit from [fullAt] of the scale on;
     * - [relative]: a column by how many dB it lies below the loudest of the frame, fully lit at the loudest, at the floor
     *   [rangeDb] below it; the light moves through the columns as a melody moves through the frequencies ("as if they
     *   are dancing"). A ratio of two levels, as first written, meant nothing on a dB scale.
     */
    data class Lighting(
        val floor: Double,
        val power: Double,
        val relative: Boolean,
        val fullAt: Double = 0.8,
        val rangeDb: Double = 24.0,
    ) {
        companion object {
            /** The span of a level from 0 to 1: −99 dB to −10 dB ([SpectrumBinning.process]'s defaults). */
            const val DB_RANGE = 89.0

            /**
             * Each column by its own level, with more contrast than the first 0.5 to 1 (the maintainer, 2026-10-29: "a bit
             * more", then "bit more on the code page"): the code page's Motor.
             */
            val Own = Lighting(floor = 0.25, power = 1.5, relative = false)

            /**
             * Against the loudest, by the dB below it: the light dances through the columns; the visual's, soft (the
             * maintainer, 2026-10-29: "too strong", twice, before it was measured in dB; the floor 0.3 then "a bit too
             * low").
             */
            val Dancing = Lighting(floor = 0.45, power = 1.5, relative = true, rangeDb = 24.0)
        }

        fun alpha(level: Double, loudest: Double): Double {
            // NaN-guard: a range or a fullAt that is not a positive number counts as the whole scale
            val measured = if (relative) {
                val belowDb = (loudest - level) * DB_RANGE
                1.0 - belowDb / (rangeDb.takeIf { it > 0.0 } ?: DB_RANGE)
            } else {
                level / (fullAt.takeIf { it > 0.0 } ?: 1.0)
            }
            val share = measured.coerceIn(0.0, 1.0)

            return floor + (1.0 - floor) * share.pow(power)
        }
    }

    // One value per frequency bin, as many as the binning reads (half the analyser's FFT size)
    private val visualizerBuffer = createAnalyzerBuffer(props.binning.fftBinCount).also { buffer ->
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
        // A step a frame, on the page's frames ([Frames.all]), none while it rests ([Props.paused]); the subscription
        // ends with the component (the maintainer, 2026-10-29: "spine exposes a single animationFrame stream and
        // components subscribe to it and call their step function()")
        Frames.all.cutoffWhen(props.paused).subscribe { processVisualizer() }

        lifecycle {
            onMount {
                dom?.let { container ->
                    canvas = dom?.querySelector("canvas") as HTMLCanvasElement?
                    canvas?.let {
                        it.width = container.clientWidth
                        it.height = container.clientHeight
                    }

                    ctx2d = canvas?.getContext("2d") as? CanvasRenderingContext2D
                }
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

        val preview = props.preview

        if (preview != null) {
            for (i in bucketValues.indices) {
                // NaN-guard: a level that is not a number counts as silence
                bucketValues[i] = preview.getOrNull(i)?.takeIf { it >= 0.0 }?.coerceAtMost(1.0) ?: 0.0
            }
        } else {
            // Process FFT data through perceptually balanced binning
            props.binning.process(fftBuffer = visualizerBuffer, out = bucketValues)
        }

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
            // Ease-out falloff, reaching 0 half way up; 0.35 at most (the maintainer, 2026-10-29: "The heat glow behind
            // the spectrumeter should be more subtle", "should not reach so far up"; 0.6, and 0 at ~77 % up, before)
            val aMax = 0.35 * Ease.Out.quad((1.0 - t * 2.0).coerceAtLeast(0.0))

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
        // NaN-guard: a step that is not a positive number counts as none, the colours even over the column
        val colorStep = props.colorStep?.takeIf { it > 0.0 } ?: (1.0 / numColors)
        // Whole cells per colour, so each band is as high as the others: 15 % of 31 cells cut at 4.65 left one band a
        // cell short (the maintainer, 2026-10-28: "the middle row is only 4 cells high")
        val boxesPerColor = (maxBoxes * colorStep).roundToInt().coerceAtLeast(1)
        // Whole pixels per cell and per gap, stacked up from the floor, so the cells are crisp too (the maintainer,
        // 2026-10-26); the pixels left over stay empty at the top
        val gap = props.boxGap.roundToInt()
        val boxHeight = ((height.toInt() - maxBoxes * gap) / maxBoxes.coerceAtLeast(1)).coerceAtLeast(1)
        val boxWithGap = boxHeight + gap

        // The loudest column this frame, for a lighting relative to it
        var loudest = 0.0

        for (v in bucketValues) {
            if (v > loudest) {
                loudest = v
            }
        }

        for (bucketIdx in 0 until numBuckets) {
            val normalized = bucketValues[bucketIdx]

            val numBoxesToDraw = (normalized * maxBoxes).toInt()

            val x = columns.xOf(bucketIdx).toDouble()
            val effectiveWidth = if (columns.step > props.gap) columns.step - props.gap else columns.step.toDouble()
            val boxAlpha = props.lighting.alpha(level = normalized, loudest = loudest)

            val colorsWithAlpha = colors.map { it.withAlpha(boxAlpha) }

            for (boxIdx in 0 until numBoxesToDraw) {
                val boxY = height - ((boxIdx + 1) * boxWithGap)

                // Each colour a band of the same height, the last one on to the top: the red was never reached when
                // the bands were cut by one colour less (the maintainer, 2026-10-26: "never red")
                val colorIdx = (boxIdx / boxesPerColor).coerceIn(0, numColors - 1)
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
                left = 50.pct

                if (props.labelAtTop) {
                    // At the top edge, over the columns (the visual's; the maintainer, 2026-10-29)
                    put("top", "4px")
                    put("transform", "translateX(-50%)")
                } else {
                    bottom = props.labelMiddle
                    // Its middle on that line, both ways
                    put("transform", "translate(-50%, 50%)")
                }
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
