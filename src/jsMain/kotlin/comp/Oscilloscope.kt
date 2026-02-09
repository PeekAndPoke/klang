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
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import kotlinx.html.js.onClickFunction
import org.khronos.webgl.get
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import kotlin.math.floor

@Suppress("FunctionName")
fun Tag.Oscilloscope(
    strokeColor: Color = Color.white,
    strokeWidth: Double = 1.2,
    centerLineColor: Color? = Color.white.withAlpha(0.2),
    centerLineWidth: Double = 1.0,
    expandedStrokeColor: Color = Color.black,
    expandedStrokeWidth: Double = strokeWidth * 1.5,
    player: () -> KlangPlayer?,
) = comp(
    Oscilloscope.Props(
        player = player,
        strokeColor = strokeColor,
        strokeWidth = strokeWidth,
        centerLineColor = centerLineColor,
        centerLineWidth = centerLineWidth,
        expandedStrokeColor = expandedStrokeColor,
        expandedStrokeWidth = expandedStrokeWidth,
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
        val expandedStrokeColor: Color,
        val expandedStrokeWidth: Double,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private var visualizerAnimFrame: Int? = null
    private val visualizerBuffer = createVisualizerBuffer(2048)

    private var resizeObserver: ResizeObserver? = null
    private var canvas: HTMLCanvasElement? = null
    private var ctx2d: CanvasRenderingContext2D? = null

    private var expanded: Boolean by value(false)
    private var overlayCanvas: HTMLCanvasElement? by value(null)
    private var overlayCtx2d: CanvasRenderingContext2D? by value(null)
    private var overlayContainer: org.w3c.dom.HTMLDivElement? by value(null)
    private var windowResizeListener: ((org.w3c.dom.events.Event) -> Unit)? by value(null)

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
                cleanupOverlay()
            }
        }
    }

    private fun processVisualizer() {
        props.player()?.let { player ->
            player.getVisualizer()?.getWaveform(visualizerBuffer)
        }

        drawWaveforms()

        visualizerAnimFrame = window.requestAnimationFrame { processVisualizer() }
    }

    private fun drawWaveforms() {
        val ctx = ctx2d ?: return
        val canvasElement = canvas ?: return

        if (!expanded) {
            // Normal mode - draw full waveform on original canvas
            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = canvasElement.width.toDouble(),
                canvasHeight = canvasElement.height.toDouble(),
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                bufferStartFraction = 0.0,
                bufferEndFraction = 1.0,
                clearColor = null
            )
        } else {
            // Expanded mode - split waveform between two canvases
            val overlayCtx = overlayCtx2d ?: return
            val overlayCanvasElement = overlayCanvas ?: return

            val leftWidth = canvasElement.width.toDouble()
            val rightWidth = overlayCanvasElement.width.toDouble()
            val totalWidth = leftWidth + rightWidth

            // Guard against division by zero
            if (totalWidth <= 0) return

            val splitFraction = leftWidth / totalWidth

            // Draw left canvas (original) - first portion
            drawWaveformSlice(
                ctx = ctx,
                canvasWidth = leftWidth,
                canvasHeight = canvasElement.height.toDouble(),
                strokeColor = props.strokeColor,
                strokeWidth = props.strokeWidth,
                centerLineColor = props.centerLineColor,
                centerLineWidth = props.centerLineWidth,
                bufferStartFraction = 0.0,
                bufferEndFraction = splitFraction,
                clearColor = null
            )

            // Draw right canvas (overlay) - remaining portion
            drawWaveformSlice(
                ctx = overlayCtx,
                canvasWidth = rightWidth,
                canvasHeight = overlayCanvasElement.height.toDouble(),
                strokeColor = props.expandedStrokeColor,
                strokeWidth = props.expandedStrokeWidth,
                centerLineColor = null,
                centerLineWidth = 0.0,
                bufferStartFraction = splitFraction,
                bufferEndFraction = 1.0,
                clearColor = null
            )
        }
    }

    private fun drawWaveformSlice(
        ctx: CanvasRenderingContext2D,
        canvasWidth: Double,
        canvasHeight: Double,
        strokeColor: Color,
        strokeWidth: Double,
        centerLineColor: Color?,
        centerLineWidth: Double,
        bufferStartFraction: Double,
        bufferEndFraction: Double,
        clearColor: String?,
    ) {
        val centerY = canvasHeight / 2.0

        // Clear canvas
        ctx.clearRect(0.0, 0.0, canvasWidth, canvasHeight)

        // Optionally fill with background color
        clearColor?.let {
            ctx.fillStyle = it
            ctx.fillRect(0.0, 0.0, canvasWidth, canvasHeight)
        }

        // Draw center line if color provided
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

        val bufferLength = visualizerBuffer.length
        val startIdx = floor(bufferLength * bufferStartFraction).toInt()
        val endIdx = floor(bufferLength * bufferEndFraction).toInt()
        val sliceLength = endIdx - startIdx

        if (sliceLength <= 0) return

        val widthInt = canvasWidth.toInt()

        if (widthInt >= sliceLength) {
            // More pixels than samples - draw all points
            val sliceWidth = canvasWidth / sliceLength.toDouble()
            for (i in 0 until sliceLength) {
                val bufferIdx = startIdx + i
                val value = visualizerBuffer[bufferIdx]
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
            val samplesPerPixel = sliceLength / widthInt

            for (pixelX in 0 until widthInt) {
                val localStartIdx = pixelX * samplesPerPixel
                val localEndIdx = minOf(localStartIdx + samplesPerPixel, sliceLength)

                val bufferStartIdx = startIdx + localStartIdx
                val bufferEndIdx = startIdx + localEndIdx

                // Find min and max in this bin to preserve peak information
                var minVal = visualizerBuffer[bufferStartIdx]
                var maxVal = visualizerBuffer[bufferStartIdx]

                for (i in bufferStartIdx + 1 until bufferEndIdx) {
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

        // Create overlay container
        val container = document.createElement("div") as HTMLDivElement
        container.style.position = "fixed"
        container.style.top = "${oscilloscopeRect.top}px"
        container.style.left = "${oscilloscopeRect.right}px"
        container.style.width = "calc(100vw - ${oscilloscopeRect.right}px)"
        container.style.height = "${oscilloscopeRect.height}px"
        container.style.asDynamic().pointerEvents = "none"
        container.style.zIndex = "9999"
        container.style.background = "transparent"

        // Create canvas inside container
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.style.width = "100%"
        canvas.style.height = "100%"
        canvas.width = container.clientWidth
        canvas.height = container.clientHeight

        container.appendChild(canvas)
        document.body?.appendChild(container)

        overlayContainer = container
        overlayCanvas = canvas
        overlayCtx2d = canvas.getContext("2d") as? CanvasRenderingContext2D

        // Add window resize listener to reposition overlay
        val resizeListener: (org.w3c.dom.events.Event) -> Unit = {
            val newRect = dom?.getBoundingClientRect()
            if (newRect != null) {
                container.style.top = "${newRect.top}px"
                container.style.left = "${newRect.right}px"
                container.style.width = "calc(100vw - ${newRect.right}px)"
                container.style.height = "${newRect.height}px"
                canvas.width = container.clientWidth
                canvas.height = container.clientHeight
            }
        }
        window.addEventListener("resize", resizeListener)
        windowResizeListener = resizeListener

        // Update existing resize observer to also update overlay position
        resizeObserver?.disconnect()
        dom?.let { containerElement ->
            resizeObserver = ResizeObserver { entries, _ ->
                for (entry in entries) {
                    val width = entry.contentRect.width
                    val height = entry.contentRect.height

                    this.canvas?.let {
                        it.width = width.toInt()
                        it.height = height.toInt()
                    }

                    // Update overlay position when oscilloscope resizes
                    if (expanded) {
                        val newRect = dom?.getBoundingClientRect()
                        if (newRect != null) {
                            container.style.top = "${newRect.top}px"
                            container.style.left = "${newRect.right}px"
                            container.style.width = "calc(100vw - ${newRect.right}px)"
                            container.style.height = "${newRect.height}px"
                            canvas.width = container.clientWidth
                            canvas.height = container.clientHeight
                        }
                    }
                }
            }
            resizeObserver?.observe(containerElement)
        }
    }

    private fun cleanupOverlay() {
        overlayContainer?.let { document.body?.removeChild(it) }
        windowResizeListener?.let { window.removeEventListener("resize", it) }

        overlayContainer = null
        overlayCanvas = null
        overlayCtx2d = null
        windowResizeListener = null

        // Restore original resize observer
        dom?.let { containerElement ->
            resizeObserver?.disconnect()
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
            resizeObserver?.observe(containerElement)
        }
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
