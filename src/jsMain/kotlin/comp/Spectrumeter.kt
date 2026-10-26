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
import kotlinx.css.Position
import kotlinx.css.bottom
import kotlinx.css.display
import kotlinx.css.height
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

    /** The column under the mouse at [clientX], from the spectrum's own box. */
    private fun columnAt(clientX: Double): Int? {
        val rect = dom?.getBoundingClientRect() ?: return null

        if (rect.width <= 0.0) {
            return null
        }

        return floor((clientX - rect.left) / rect.width * props.numBuckets).toInt().coerceIn(0, props.numBuckets - 1)
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

    /** The label over the floor, at the [column]'s side towards the middle, so it never runs off the edge. */
    private fun FlowContent.renderRange(column: Int) {
        val n = props.numBuckets
        val towardsRight = column < n / 2

        div {
            css {
                position = Position.absolute
                bottom = 4.px
                put("pointer-events", "none")
                put("padding", "1px 5px")
                put("border-radius", "3px")
                put("background", "rgba(0, 0, 0, 0.7)")
                put("color", "var(--kl-ink-text)")
                put("font-family", "monospace")
                put("font-size", "11px")
                put("white-space", "nowrap")

                if (towardsRight) {
                    put("left", "${column * 100.0 / n}%")
                } else {
                    put("right", "${(n - column - 1) * 100.0 / n}%")
                }
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
