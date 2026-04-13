package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.onResize
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.maths.Ease
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.cursor
import kotlinx.css.height
import kotlinx.css.pct
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import kotlinx.html.js.onClickFunction
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import kotlin.math.pow

@Suppress("FunctionName")
fun Tag.Oscilloscope(
    player: Stream<KlangPlayer?>,
    strokeColor: Color = Color.white,
    strokeWidth: Double = 1.2,
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

class Oscilloscope(ctx: Ctx<Props>) : Component<Oscilloscope.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val player: Stream<KlangPlayer?>,
        val strokeColor: Color,
        val strokeWidth: Double,
        val centerLineColor: Color?,
        val centerLineWidth: Double,
        val pixelSkip: Int,
    )

    companion object {
        private const val FRAME_SIZE = 2048
        private const val CROSSFADE_SAMPLES = 512
        private const val EXPANDED_WARP_STRENGTH = 6.0

        // Skip the flattest portion of the ease-in curve by starting at
        // t = EXPANDED_WARP_T_START instead of t = 0. The remaining range
        // [EXPANDED_WARP_T_START, 1.0] is stretched over the full half-width
        // so the centre already has a visible slope.
        private const val EXPANDED_WARP_T_START = 0.45

        // Oversample window grows from MIN at the centre (preserves signal
        // amplitude) to MAX at the edge (smooths the flicker caused by each
        // outer pixel covering thousands of warped samples).
        private const val EXPANDED_OVERSAMPLE_MIN = 16
        private const val EXPANDED_OVERSAMPLE_MAX = 64
        private const val EXPANDED_DEFLECTION_STRENGTH = 2.0
        private const val NORMAL_DEFLECTION_STRENGTH = 4.0

        // Extra vertical padding added above & below the expanded-mode overlay
        // so the waveform can bleed a bit outside the oscilloscope's own box.
        private const val OVERLAY_EXTRA_HEIGHT = 50.0
        private val idleBuffer = Float32Array(FRAME_SIZE)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var waveformUnsubscribe: Unsubscribe? = null

    private var lastHistory: AnalyzerBufferHistory? = null
    private var expandedStorage: Float32Array? = null

    // Integral image (prefix sum) over the flattened storage, ordered by
    // sample index (0 = newest). Used for O(1) moving-average queries:
    //     avg([s1, s2)) = (integral[s2] - integral[s1]) / (s2 - s1)
    // Double precision because a cumulative sum over 1M+ samples loses too
    // many bits in Float32.
    private var expandedIntegral: DoubleArray? = null

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

    private var expanded: Boolean by value(false)
    private var overlayCanvas: HTMLCanvasElement? = null
    private var overlayCtx2d: CanvasRenderingContext2D? = null
    private var overlayContainer: HTMLDivElement? = null

    // Deflection cache keyed by (strength, halfCurve) → (width → values)
    private val deflectionCaches = mutableMapOf<Pair<Double, Boolean>, Pair<Double, DoubleArray>>()

    // Pre-computed ease-in curve values for the warp — keyed by half-width.
    // Each entry maps pixelX → a normalised value in [0, 1] that, when
    // multiplied by maxSampleIdx, yields the sample index for that pixel.
    // Avoids repeating a `.pow(EXPANDED_WARP_STRENGTH)` call every frame.
    private var warpSampleCurveCache: Pair<Double, DoubleArray>? = null

    @Suppress("unused")
    private val laf by subscribingTo(KlangTheme)

    @Suppress("unused")
    private val playerSub by subscribingTo(props.player) {
        subscribeToWaveform(it)
    }

    init {
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

                drawIdleWaveform()
            }

            onUnmount {
                waveformUnsubscribe?.invoke()
                waveformUnsubscribe = null
                cleanupOverlay()
            }

            onResize { entries ->
                for (entry in entries) {
                    val width = entry.contentRect.width
                    val height = entry.contentRect.height

                    canvas?.let {
                        it.width = width.toInt()
                        it.height = height.toInt()
                    }

                    if (expanded) {
                        updateOverlaySize()
                    }

                    // Redraw after resize (canvas clear on resize)
                    val h = lastHistory
                    if (h != null) drawWaveform(h) else drawIdleWaveform()
                }
            }
        }
    }

    private fun subscribeToWaveform(player: KlangPlayer?) {
        waveformUnsubscribe?.invoke()
        waveformUnsubscribe = null

        val analyzer = player?.getAnalyzer() ?: return

        waveformUnsubscribe = analyzer.waveform.subscribeToStream { history ->
            lastHistory = history
            drawWaveform(history)
            triggerRedraw()
        }
    }

    private fun drawIdleWaveform() {
        val ctx = ctx2d ?: return
        val c = canvas ?: return
        drawWaveformSlice(
            ctx = ctx,
            canvasWidth = c.width.toDouble(),
            canvasHeight = c.height.toDouble(),
            strokeColor = props.strokeColor,
            strokeWidth = props.strokeWidth,
            centerLineColor = props.centerLineColor,
            centerLineWidth = props.centerLineWidth,
            buffer = idleBuffer,
            bufferLength = FRAME_SIZE,
        )
    }

    private fun drawWaveform(history: AnalyzerBufferHistory) {
        if (history.size == 0) return

        if (!expanded) {
            // Normal mode — draw on the small embedded canvas using most recent frame
            val ctx = ctx2d ?: return
            val canvasElement = canvas ?: return
            canvasElement.style.visibility = "visible"
            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = canvasElement.width.toDouble(),
                canvasHeight = canvasElement.height.toDouble(),
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                buffer = history[0],
                bufferLength = history.bufferSize,
                deflectionStrength = NORMAL_DEFLECTION_STRENGTH,
            )
        } else {
            canvas?.style?.visibility = "hidden"
            val ctx = overlayCtx2d ?: return
            val canvasElement = overlayCanvas ?: return

            // Flatten all history frames into contiguous storage and build
            // the prefix-sum integral image for O(1) moving-average queries.
            val (storage, offset) = flattenHistory(history)
            buildIntegralImage(storage, offset)

            val fullWidth = canvasElement.width.toDouble()
            val fullHeight = canvasElement.height.toDouble()
            val halfWidth = fullWidth / 2.0

            drawExpandedBackground(ctx, fullWidth, fullHeight)

            // Both halves share the exact centre pixel. Sample 0 is drawn
            // there by both halves with the same value, so the paths meet
            // cleanly at halfWidth — no visible gap.
            drawExpandedHalf(ctx, halfWidth, fullHeight, storage, offset, mirror = false)
            drawExpandedHalf(ctx, halfWidth, fullHeight, storage, offset, mirror = true)
        }
    }

    /**
     * Flattens all frames of [history] into [expandedStorage] (newest frame
     * last), crossfading adjacent frames to smooth out buffer seams.
     * Returns the storage and the number of valid samples written to it.
     */
    private fun flattenHistory(history: AnalyzerBufferHistory): Pair<Float32Array, Int> {
        val numFrames = history.size
        val bufSize = history.bufferSize
        val fadeLen = minOf(CROSSFADE_SAMPLES, bufSize / 2)
        val neededSamples = numFrames * bufSize - maxOf(0, numFrames - 1) * fadeLen

        val existing = expandedStorage
        val storage = if (existing == null || existing.length < neededSamples) {
            Float32Array(neededSamples).also { expandedStorage = it }
        } else {
            existing
        }

        var offset = 0
        for (frameIdx in 0 until numFrames) {
            val frame = history[numFrames - 1 - frameIdx]
            if (frameIdx == 0) {
                for (i in 0 until bufSize) storage[offset++] = frame[i]
            } else {
                // Crossfade: rewind into the already-written tail and blend.
                offset -= fadeLen
                for (i in 0 until fadeLen) {
                    val t = (i + 1).toFloat() / (fadeLen + 1).toFloat()
                    storage[offset] = storage[offset] * (1f - t) + frame[i] * t
                    offset++
                }
                for (i in fadeLen until bufSize) storage[offset++] = frame[i]
            }
        }
        return storage to offset
    }

    /**
     * Builds [expandedIntegral] as a prefix sum indexed by sample index
     * (0 = newest, [length] − 1 = oldest), enabling O(1) moving-average
     * queries: `avg([s1, s2)) = (integral[ s2 ] − integral[ s1 ]) / (s2 − s1)`.
     * Double precision to avoid losing bits over large cumulative sums.
     */
    private fun buildIntegralImage(storage: Float32Array, length: Int) {
        val size = length + 1
        val existing = expandedIntegral
        val integral = if (existing == null || existing.size < size) {
            DoubleArray(size).also { expandedIntegral = it }
        } else {
            existing
        }
        integral[0] = 0.0
        var running = 0.0
        for (i in 0 until length) {
            running += storage[length - 1 - i]
            integral[i + 1] = running
        }
    }

    private fun drawExpandedBackground(
        ctx: CanvasRenderingContext2D,
        fullWidth: Double,
        fullHeight: Double,
    ) {
        ctx.clearRect(0.0, 0.0, fullWidth, fullHeight)
        props.centerLineColor?.let { clc ->
            val centerY = fullHeight / 2.0
            ctx.strokeStyle = clc.toString()
            ctx.lineWidth = props.centerLineWidth
            ctx.beginPath()
            ctx.moveTo(0.0, centerY)
            ctx.lineTo(fullWidth, centerY)
            ctx.stroke()
        }
    }

    private fun drawExpandedHalf(
        ctx: CanvasRenderingContext2D,
        halfWidth: Double,
        fullHeight: Double,
        buffer: Float32Array,
        bufferLength: Int,
        mirror: Boolean,
    ) {
        ctx.save()
        ctx.translate(halfWidth, 0.0)
        if (mirror) ctx.scale(-1.0, 1.0)
        drawWaveformSlice(
            ctx = ctx,
            canvasWidth = halfWidth,
            canvasHeight = fullHeight,
            strokeColor = props.strokeColor,
            strokeWidth = props.strokeWidth,
            centerLineColor = props.centerLineColor,
            centerLineWidth = props.centerLineWidth,
            buffer = buffer,
            bufferLength = bufferLength,
            deflectionStrength = EXPANDED_DEFLECTION_STRENGTH,
            drawBackground = false,
            expandedMode = true,
        )
        ctx.restore()
    }

    /**
     * Calculate vibrating string deflection for expanded mode.
     * Returns a value between 0.02 (at edges) and 1.0 (at center).
     * Gentle curve so deflection stays high across most of the width.
     */
    private fun calculateStringDeflection(normalized: Double, strength: Double): Double {
        val dist = kotlin.math.abs(normalized - 0.5)
        val curveValue = (1.0 - 2.0.pow(strength) * dist.pow(strength)).coerceAtLeast(0.0)
        return 0.02 + 0.98 * curveValue
    }

    private fun getOrBuildDeflectionCache(
        totalWidth: Double,
        strength: Double,
        halfCurve: Boolean,
    ): DoubleArray {
        val key = strength to halfCurve
        val cached = deflectionCaches[key]
        if (cached != null && cached.first == totalWidth) return cached.second

        val resolution = totalWidth.toInt().coerceAtLeast(1)
        val values = DoubleArray(resolution) { pixelX ->
            val normalized = pixelX.toDouble() / totalWidth
            // In half-curve mode the left edge of the cache (local x=0) is the
            // screen centre — map it onto the peak of the underlying curve so
            // no counter force is applied there, and only the outer edge dampens.
            val input = if (halfCurve) 0.5 + normalized / 2.0 else normalized
            calculateStringDeflection(input, strength)
        }
        deflectionCaches[key] = totalWidth to values
        return values
    }

    /**
     * Returns a pre-computed ease-in curve for the expanded-mode time warp.
     * Each entry maps `pixelX → normalised [0, 1]` value that, when multiplied
     * by `maxSampleIdx`, yields the sample index for that pixel.
     */
    private fun getOrBuildWarpSampleCurve(halfScreen: Double, widthInt: Int): DoubleArray {
        val cached = warpSampleCurveCache
        if (cached != null && cached.first == halfScreen) return cached.second

        val size = widthInt.coerceAtLeast(1)
        val curve = Ease.In.pow(EXPANDED_WARP_STRENGTH)
        val tSpan = 1.0 - EXPANDED_WARP_T_START
        val values = DoubleArray(size) { px ->
            val screenX = px.toDouble() / halfScreen
            val t = (EXPANDED_WARP_T_START + screenX * tSpan).coerceIn(0.0, 1.0)
            curve(t)
        }
        warpSampleCurveCache = halfScreen to values
        return values
    }

    private fun drawWaveformSlice(
        ctx: CanvasRenderingContext2D,
        canvasWidth: Double,
        canvasHeight: Double,
        strokeColor: Color,
        strokeWidth: Double,
        centerLineColor: Color?,
        centerLineWidth: Double,
        buffer: Float32Array,
        bufferLength: Int,
        deflectionStrength: Double = 0.0,
        drawBackground: Boolean = true,
        // Expanded-mode flag: enables buffer reversal (newest → centre),
        // half-curve deflection (peak at centre), and the time-warp curve.
        expandedMode: Boolean = false,
    ) {
        val centerY = canvasHeight / 2.0

        if (drawBackground) {
            ctx.clearRect(0.0, 0.0, canvasWidth, canvasHeight)

            // Draw center line
            centerLineColor?.let { clc ->
                ctx.strokeStyle = clc.toString()
                ctx.lineWidth = centerLineWidth
                ctx.beginPath()
                ctx.moveTo(0.0, centerY)
                ctx.lineTo(canvasWidth, centerY)
                ctx.stroke()
            }
        }

        if (bufferLength <= 0) return

        // Build waveform path
        ctx.beginPath()

        val widthInt = canvasWidth.toInt()
        val deflectionCache = if (deflectionStrength > 0.0) {
            getOrBuildDeflectionCache(canvasWidth, deflectionStrength, expandedMode)
        } else {
            null
        }
        val step = props.pixelSkip.coerceAtLeast(1)

        // In expanded mode, sample index 0 is the newest sample (drawn at the
        // centre), so we read the buffer in reverse.
        fun sampleAt(i: Int): Float =
            if (expandedMode) buffer[bufferLength - 1 - i] else buffer[i]

        if (expandedMode) {
            // Time-warped drawing: the centre shows the newest samples with a
            // gentle slope, the outer edges compress the oldest samples via an
            // ease-in curve. See getOrBuildWarpSampleCurve.
            val lastPixel = widthInt - 1
            val maxSampleIdx = bufferLength - 1
            val halfScreen = lastPixel.toDouble().coerceAtLeast(1.0)
            val curveCache = getOrBuildWarpSampleCurve(halfScreen, widthInt)

            fun sampleIdxAt(pixelX: Int): Int {
                val idx = pixelX.coerceIn(0, curveCache.size - 1)
                return (curveCache[idx] * maxSampleIdx).toInt().coerceIn(0, maxSampleIdx)
            }

            // Adaptive pixel step — 1× pixelSkip at the centre, growing to
            // 2× pixelSkip at the edge. Now that we draw a single polyline
            // (no min/max bars), we can afford a much denser step.
            val minWarpStep = 3
            val maxWarpStep = step
            val warpStepSpan = (maxWarpStep - minWarpStep).toDouble()

            var isFirstPoint = true
            var pixelX = 0
            while (pixelX <= lastPixel) {
                val progress = pixelX.toDouble() / halfScreen
                val warpStep = minWarpStep + (progress * warpStepSpan).toInt()

                // Oversample via moving average — O(1) lookup into the
                // pre-built integral image. Window scales linearly from MIN
                // at the centre to MAX at the edge.
                val s1 = sampleIdxAt(pixelX)
                val s2 = sampleIdxAt(pixelX + warpStep).coerceAtMost(bufferLength)
                val oversampleSize = (EXPANDED_OVERSAMPLE_MIN +
                        progress * (EXPANDED_OVERSAMPLE_MAX - EXPANDED_OVERSAMPLE_MIN)).toInt()
                val oversampleEnd = minOf(s1 + oversampleSize, s2, bufferLength)
                val integral = expandedIntegral
                var value = if (integral != null && oversampleEnd > s1) {
                    ((integral[oversampleEnd] - integral[s1]) / (oversampleEnd - s1)).toFloat()
                } else {
                    sampleAt(s1)
                }

                val x = pixelX.toDouble()

                // Deflection is applied by pure pixel position — independent
                // of the time warp.
                deflectionCache?.let { cache ->
                    val cacheIdx = x.toInt().coerceIn(0, cache.size - 1)
                    value *= cache[cacheIdx].toFloat()
                }

                val y = centerY - (value * centerY)

                if (isFirstPoint) {
                    ctx.moveTo(x, y)
                    isFirstPoint = false
                } else {
                    ctx.lineTo(x, y)
                }

                pixelX += warpStep
            }
        } else if (widthInt >= bufferLength) {
            // More pixels than samples
            val sliceWidth = canvasWidth / bufferLength.toDouble()
            var isFirstPoint = true

            for (i in 0 until bufferLength step step) {
                var value = sampleAt(i)
                val x = i * sliceWidth

                deflectionCache?.let { cache ->
                    val cacheIdx = x.toInt().coerceIn(0, cache.size - 1)
                    value *= cache[cacheIdx].toFloat()
                }

                val y = centerY - (value * centerY)

                if (isFirstPoint) {
                    ctx.moveTo(x, y)
                    isFirstPoint = false
                } else {
                    ctx.lineTo(x, y)
                }
            }
        } else {
            // More samples than pixels — downsample with min/max per pixel
            val samplesPerPixel = bufferLength / widthInt
            var isFirstPoint = true

            for (pixelX in 0 until widthInt step step) {
                val localStartIdx = pixelX * samplesPerPixel
                val localEndIdx = minOf(localStartIdx + samplesPerPixel, bufferLength)

                var minVal = sampleAt(localStartIdx)
                var maxVal = minVal

                for (i in localStartIdx + 1 until localEndIdx) {
                    val value = sampleAt(i)
                    if (value < minVal) minVal = value
                    if (value > maxVal) maxVal = value
                }

                val x = pixelX.toDouble()

                deflectionCache?.let { cache ->
                    val cacheIdx = x.toInt().coerceIn(0, cache.size - 1)
                    minVal *= cache[cacheIdx].toFloat()
                    maxVal *= cache[cacheIdx].toFloat()
                }

                val yMin = centerY - (minVal * centerY)
                val yMax = centerY - (maxVal * centerY)

                if (isFirstPoint) {
                    ctx.moveTo(x, yMax)
                    isFirstPoint = false
                }

                ctx.lineTo(x, yMax)
                ctx.lineTo(x, yMin)
            }
        }

        // Stroke shadow behind for contrast
        ctx.strokeStyle = KlangTheme.good.withAlpha(0.15).toString()
        ctx.lineWidth = strokeWidth + 5.0
        ctx.stroke()

        // Gold highlight
        ctx.strokeStyle = KlangTheme.gold.withAlpha(0.25).toString()
        ctx.lineWidth = strokeWidth + 3
        ctx.stroke()

        // Stroke foreground waveform
        ctx.strokeStyle = strokeColor.toString()
        ctx.lineWidth = strokeWidth
        ctx.stroke()
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    private fun toggleExpanded() {
        if (!expanded) {
            createOverlay()
        } else {
            cleanupOverlay()
        }
        expanded = !expanded
    }

    private fun createOverlay() {
        val container = document.createElement("div") as HTMLDivElement
        container.style.position = "fixed"
        container.style.asDynamic().pointerEvents = "none"
        container.style.zIndex = "9999"
        container.style.background = "transparent"

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.style.width = "100%"
        canvas.style.height = "100%"

        container.appendChild(canvas)
        document.body?.appendChild(container)

        overlayContainer = container
        overlayCanvas = canvas
        overlayCtx2d = canvas.getContext("2d") as? CanvasRenderingContext2D
        // expandedStorage is allocated lazily in drawWaveform based on the
        // actual history size so we use ALL of it regardless of source capacity.

        updateOverlaySize()
    }

    private fun updateOverlaySize() {
        val oscilloscopeRect = dom?.getBoundingClientRect() ?: return
        val container = overlayContainer ?: return
        container.style.top = "${oscilloscopeRect.top - OVERLAY_EXTRA_HEIGHT / 2}px"
        container.style.left = "${oscilloscopeRect.left}px"
        container.style.width = "calc(100vw - ${oscilloscopeRect.left}px)"
        container.style.height = "${oscilloscopeRect.height + OVERLAY_EXTRA_HEIGHT}px"
        overlayCanvas?.width = (window.innerWidth - oscilloscopeRect.left).toInt()
        overlayCanvas?.height = (oscilloscopeRect.height + OVERLAY_EXTRA_HEIGHT).toInt()
    }

    private fun cleanupOverlay() {
        overlayContainer?.let { document.body?.removeChild(it) }
        overlayContainer = null
        overlayCanvas = null
        overlayCtx2d = null
        expandedStorage = null
        expandedIntegral = null
        deflectionCaches.clear()
        warpSampleCurveCache = null
    }

    override fun VDom.render() {
        div {
            key = "oscilloscope"

            css {
                height = 100.pct
                cursor = Cursor.pointer
            }

            onClickFunction = { toggleExpanded() }

            canvas("oscilloscope-canvas") {}
        }
    }
}
