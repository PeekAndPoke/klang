package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.onResize
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.streams.Stream
import de.peekandpoke.ultra.streams.Unsubscribe
import io.peekandpoke.klang.audio_bridge.analyzer.AnalyzerBufferHistory
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.*
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
    expandedFrameCount: Int = 5,
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
        private const val CROSSFADE_SAMPLES = 64
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

    // Two-level deflection cache: strength → (width → values)
    private val deflectionCaches = mutableMapOf<Double, Pair<Double, DoubleArray>>()

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
                deflectionStrength = 3.0,
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

            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = canvasElement.width.toDouble(),
                canvasHeight = canvasElement.height.toDouble(),
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                buffer = storage,
                bufferLength = offset,
                deflectionStrength = 2.0,
            )
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

    private fun getOrBuildDeflectionCache(totalWidth: Double, strength: Double): DoubleArray {
        val cached = deflectionCaches[strength]
        if (cached != null && cached.first == totalWidth) return cached.second

        val resolution = totalWidth.toInt().coerceAtLeast(1)
        val values = DoubleArray(resolution) { pixelX ->
            calculateStringDeflection(pixelX.toDouble() / totalWidth, strength)
        }
        deflectionCaches[strength] = totalWidth to values
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
    ) {
        val centerY = canvasHeight / 2.0

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

        // Draw waveform
        ctx.strokeStyle = strokeColor.toString()
        ctx.lineWidth = strokeWidth
        ctx.beginPath()

        if (bufferLength <= 0) return

        val widthInt = canvasWidth.toInt()
        val deflectionCache = if (deflectionStrength > 0.0) {
            getOrBuildDeflectionCache(canvasWidth, deflectionStrength)
        } else {
            null
        }
        val step = props.pixelSkip.coerceAtLeast(1)

        if (widthInt >= bufferLength) {
            // More pixels than samples
            val sliceWidth = canvasWidth / bufferLength.toDouble()
            var isFirstPoint = true

            for (i in 0 until bufferLength step step) {
                var value = buffer[i]
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

                var minVal = buffer[localStartIdx]
                var maxVal = buffer[localStartIdx]

                for (i in localStartIdx + 1 until localEndIdx) {
                    val value = buffer[i]
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
