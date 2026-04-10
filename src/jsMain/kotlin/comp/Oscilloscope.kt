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
    strokeWidth: Double = 1.1,
    centerLineColor: Color? = Color.white.withAlpha(0.07),
    centerLineWidth: Double = 1.0,
    pixelSkip: Int = 3,
    expandedFrameCount: Int = 100,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
        pixelSkip = pixelSkip,
        expandedFrameCount = expandedFrameCount,
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
        val expandedFrameCount: Int,
    )

    companion object {
        private const val FRAME_SIZE = 2048
        private const val CROSSFADE_SAMPLES = 128
        private val idleBuffer = Float32Array(FRAME_SIZE)
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var waveformUnsubscribe: Unsubscribe? = null

    private var lastHistory: AnalyzerBufferHistory? = null
    private var expandedStorage: Float32Array? = null

    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

    private var expanded: Boolean by value(false)
    private var overlayCanvas: HTMLCanvasElement? = null
    private var overlayCtx2d: CanvasRenderingContext2D? = null
    private var overlayContainer: HTMLDivElement? = null

    // Deflection cache keyed by (strength, halfCurve) → (width → values)
    private val deflectionCaches = mutableMapOf<Pair<Double, Boolean>, Pair<Double, DoubleArray>>()

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
        console.log("New player", player)

        waveformUnsubscribe?.invoke()
        waveformUnsubscribe = null

        val analyzer = player?.getAnalyzer() ?: return

        console.log("Subscribing to waveform ++++++++++++++++++++++++++++++++++++++++++++++")

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
                deflectionStrength = 4.0,
            )
        } else {
            // Expanded mode — flatten history frames oldest-to-newest into expandedStorage
            canvas?.style?.visibility = "hidden"
            val ctx = overlayCtx2d ?: return
            val canvasElement = overlayCanvas ?: return
            val storage = expandedStorage ?: return

            val numFrames = minOf(history.size, props.expandedFrameCount)
            val bufSize = history.bufferSize
            val fadeLen = minOf(CROSSFADE_SAMPLES, bufSize / 2)
            var offset = 0

            for (frameIdx in 0 until numFrames) {
                val age = numFrames - 1 - frameIdx
                val frame = history[age]

                if (frameIdx == 0) {
                    // First frame — copy entirely
                    for (i in 0 until bufSize) {
                        storage[offset++] = frame[i]
                    }
                } else {
                    // Crossfade: blend end of previous frame with start of this frame
                    // Rewind into the already-written tail
                    offset -= fadeLen
                    for (i in 0 until fadeLen) {
                        val t = (i + 1).toFloat() / (fadeLen + 1).toFloat()
                        storage[offset] = storage[offset] * (1f - t) + frame[i] * t
                        offset++
                    }
                    // Copy remainder of frame
                    for (i in fadeLen until bufSize) {
                        storage[offset++] = frame[i]
                    }
                }
            }

            // Expanded mode: draw the waveform twice — right half normal,
            // left half mirrored — so sound appears to emanate from the middle.
            val fullWidth = canvasElement.width.toDouble()
            val fullHeight = canvasElement.height.toDouble()
            val halfWidth = fullWidth / 2.0
            val centerY = fullHeight / 2.0

            // Clear the whole canvas and draw the center line across both halves
            ctx.clearRect(0.0, 0.0, fullWidth, fullHeight)
            props.centerLineColor?.let { clc ->
                ctx.strokeStyle = clc.toString()
                ctx.lineWidth = props.centerLineWidth
                ctx.beginPath()
                ctx.moveTo(0.0, centerY)
                ctx.lineTo(fullWidth, centerY)
                ctx.stroke()
            }

            // Both halves are offset by pixelSkip from the centre so the first
            // drawn pixel of each half sits at ±pixelSkip, giving a symmetric
            // pattern: …, −3·step, −step, +step, +3·step, … This prevents the
            // visible shift caused by one half drawing exactly at the centre
            // while the other starts 1 pixel away.
            val seamOffset = props.pixelSkip.toDouble()

            // Right half — origin shifted to the centre, draws outward to the right.
            // Strong time warp starting right from the middle (no linear region).
            ctx.save()
            ctx.translate(halfWidth, 0.0)
            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = halfWidth,
                canvasHeight = fullHeight,
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                buffer = storage,
                bufferLength = offset,
                deflectionStrength = 6.0,
                drawBackground = false,
                reverseBuffer = true,
                halfCurve = true,
                timeWarpEnabled = true,
                timeWarpUnwarpedFraction = 0.0,
                pixelOffset = seamOffset,
            )
            ctx.restore()

            // Left half — origin at the centre, x axis flipped so the waveform
            // mirrors outward to the left. Same pixelOffset as the right half
            // so the drawn pattern is symmetric around the centre.
            ctx.save()
            ctx.translate(halfWidth, 0.0)
            ctx.scale(-1.0, 1.0)
            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = halfWidth,
                canvasHeight = fullHeight,
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                buffer = storage,
                bufferLength = offset,
                deflectionStrength = 6.0,
                drawBackground = false,
                reverseBuffer = true,
                halfCurve = true,
                timeWarpEnabled = true,
                timeWarpUnwarpedFraction = 0.0,
                pixelOffset = seamOffset,
            )
            ctx.restore()
        }
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
        reverseBuffer: Boolean = false,
        halfCurve: Boolean = false,
        timeWarpEnabled: Boolean = false,
        timeWarpUnwarpedFraction: Double = 0.0,
        pixelOffset: Double = 0.0,
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
            getOrBuildDeflectionCache(canvasWidth, deflectionStrength, halfCurve)
        } else {
            null
        }
        val step = props.pixelSkip.coerceAtLeast(1)

        // Piecewise time warp with a strong concave curve.
        //
        //   Linear region u ∈ [0, a]:   f(u) = k·u
        //   Curve  region u ∈ [a, 1]:   f(u) = k·a + (1 − k·a) · (1 − (1 − v)^p)
        //                               where v = (u − a) / (1 − a)
        //
        // The linear slope k is picked so the two pieces meet with matching
        // slopes (C1 continuous):
        //      k = p / (1 + a · (p − 1))
        //
        // With a = 0 there is no linear region and the curve spans the whole
        // half-width, giving slope = p at the centre and 0 at the outer edge —
        // waves start fast at the centre and slow down toward the sides.
        val warpA = timeWarpUnwarpedFraction.coerceIn(0.0, 1.0)
        val warpP = 2.75  // warp strength
        val warpK = warpP / (1.0 + warpA * (warpP - 1.0))
        val warpLinearEnd = warpK * warpA
        val middleEnd = (warpLinearEnd * bufferLength).toInt()

        // Base sample lookup that respects reverseBuffer.
        fun rawSample(i: Int): Float =
            if (reverseBuffer) buffer[bufferLength - 1 - i] else buffer[i]

        // Sample reader used by the drawing loops. In the unwarped middle region
        // (indices [0, middleEnd)) we average each sample with its reverse — the
        // region becomes palindromic, so the mirrored halves blend without a
        // visible seam at the centre.
        fun sampleAt(i: Int): Float {
            return if (middleEnd > 1 && i in 0 until middleEnd) {
                val mirrored = middleEnd - 1 - i
                (rawSample(i) + rawSample(mirrored)) * 0.5f
            } else {
                rawSample(i)
            }
        }

        val startPixel = pixelOffset.toInt().coerceAtLeast(0)

        if (timeWarpEnabled) {
            val oneMinusA = (1.0 - warpA).coerceAtLeast(1e-9)
            val curveSpan = 1.0 - warpLinearEnd

            fun warpedSample(u: Double): Double {
                val uc = u.coerceIn(0.0, 1.0)
                return if (uc <= warpA) {
                    warpK * uc
                } else {
                    val v = (uc - warpA) / oneMinusA
                    warpLinearEnd + curveSpan * (1.0 - (1.0 - v).pow(warpP))
                }
            }

            var isFirstPoint = true
            val lastPixel = widthInt - 1
            // Strong warp crams a lot of samples into the centre pixels, so
            // draw at twice the configured min pixel distance to avoid dense
            // overdraw.
            val warpStep = step * 2

            for (pixelX in startPixel..lastPixel step warpStep) {
                // Compute min/max for ONLY this single drawn pixel — the samples
                // belonging to the skipped pixels are intentionally excluded,
                // matching the non-warped downsample path. This keeps the
                // output from looking too dense at high warp strengths.
                val u1 = pixelX.toDouble() / widthInt.toDouble()
                val u2 = (pixelX + 1).toDouble() / widthInt.toDouble()

                val s1 = (bufferLength * warpedSample(u1)).toInt().coerceIn(0, bufferLength - 1)
                val s2Raw = (bufferLength * warpedSample(u2)).toInt().coerceAtMost(bufferLength)
                val s2 = maxOf(s1 + 1, s2Raw)

                var minVal = sampleAt(s1)
                var maxVal = minVal
                for (i in s1 + 1 until s2) {
                    val value = sampleAt(i)
                    if (value < minVal) minVal = value
                    if (value > maxVal) maxVal = value
                }

                val x = pixelX.toDouble()

                // The string mechanic must NOT be affected by the time warp —
                // look up the deflection using the pure pixel position.
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
        } else if (widthInt >= bufferLength) {
            // More pixels than samples
            val sliceWidth = canvasWidth / bufferLength.toDouble()
            var isFirstPoint = true
            val firstSample = ((startPixel / sliceWidth).toInt()).coerceIn(0, bufferLength - 1)

            for (i in firstSample until bufferLength step step) {
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

            for (pixelX in startPixel until widthInt step step) {
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
        val oscilloscopeRect = dom?.getBoundingClientRect() ?: return

        // Single overlay covering from oscilloscope left edge to viewport right edge
        val extraHeight = 20.0
        val container = document.createElement("div") as HTMLDivElement
        container.style.position = "fixed"
        container.style.top = "${oscilloscopeRect.top - extraHeight / 2}px"
        container.style.left = "${oscilloscopeRect.left}px"
        container.style.width = "calc(100vw - ${oscilloscopeRect.left}px)"
        container.style.height = "${oscilloscopeRect.height + extraHeight}px"
        container.style.asDynamic().pointerEvents = "none"
        container.style.zIndex = "9999"
        container.style.background = "transparent"

        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.style.width = "100%"
        canvas.style.height = "100%"

        container.appendChild(canvas)
        document.body?.appendChild(container)

        canvas.width = container.clientWidth
        canvas.height = container.clientHeight

        overlayContainer = container
        overlayCanvas = canvas
        overlayCtx2d = canvas.getContext("2d") as? CanvasRenderingContext2D

        expandedStorage = Float32Array(FRAME_SIZE * props.expandedFrameCount)
    }

    private fun updateOverlaySize() {
        val oscilloscopeRect = dom?.getBoundingClientRect() ?: return
        val extraHeight = 20.0
        overlayContainer?.let { container ->
            container.style.top = "${oscilloscopeRect.top - extraHeight / 2}px"
            container.style.left = "${oscilloscopeRect.left}px"
            container.style.width = "calc(100vw - ${oscilloscopeRect.left}px)"
            container.style.height = "${oscilloscopeRect.height + extraHeight}px"
            overlayCanvas?.width = (window.innerWidth - oscilloscopeRect.left).toInt()
            overlayCanvas?.height = (oscilloscopeRect.height + extraHeight).toInt()
        }
    }

    private fun cleanupOverlay() {
        overlayContainer?.let { document.body?.removeChild(it) }
        overlayContainer = null
        overlayCanvas = null
        overlayCtx2d = null
        expandedStorage = null
        deflectionCaches.clear()
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
