package io.peekandpoke.klang.codemirror

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.common.SourceLocation
import io.peekandpoke.kraft.addons.pixijs.PixiJsAddon
import io.peekandpoke.kraft.addons.pixijs.js.Application
import io.peekandpoke.kraft.addons.pixijs.js.Graphics
import io.peekandpoke.kraft.addons.pixijs.js.Ticker
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.utils.jsObjectOf
import io.peekandpoke.kraft.utils.launch
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.events.Event
import kotlin.js.Date
import kotlin.math.min

/**
 * PixiJS (WebGL) playback highlight buffer for CodeMirror.
 *
 * Renders playback highlights as GPU-batched quads on a single transparent `<canvas>` overlaid on
 * the editor — replacing the previous one-`<mark>`-per-event DOM overlay. The CodeMirror state is
 * never touched.
 *
 * The whole rendering surface is **one** WebGL canvas (one compositor layer), each highlight is a
 * pooled [Graphics] drawn once, and a single Pixi [Ticker] handles both the start-time scheduling
 * and the per-frame fade/scroll. The ticker is stopped while idle, so an editor that isn't playing
 * costs zero `requestAnimationFrame`.
 *
 * Why this over the old DOM marks: the old `@keyframes pulse` animated `border-color` /
 * `background-color` — paint-bound properties that force a re-rasterization of every active mark on
 * every frame, the exact path weak GPUs choke on. Here the geometry is static; only `x/y/alpha`
 * change per frame, and the fade is computed in JS rather than via CSS paint animation.
 */
class CodeMirrorHighlightBuffer(
    private val maxRefreshRatePerLocation: Int = 300,
    private val maxSimultaneousHighlights: Int = 2000,
    var maxHighlightsPerEvent: Int = 10,
) {
    /**
     * Source identity of the file currently displayed in the editor.
     *
     * Highlight events whose [SourceLocation.source] doesn't match are silently dropped — that's how
     * the editor avoids rendering ghost marks at line/column positions that come from imported
     * libraries (e.g. `peekandpoke/tetris`) but don't exist in the file the user is currently
     * looking at.
     *
     * `null` means "the main script" — matches main-script locations whose `source` field is `null`.
     */
    var currentSource: String? = null

    private val minIntervalMs: Double get() = 1000.0 / maxRefreshRatePerLocation

    private var view: EditorView? = null

    // ── PixiJS state ────────────────────────────────────────────────────────

    private var addon: PixiJsAddon? = null
    private var app: Application? = null
    private var initializing = false
    private var tickerRunning = false

    /** Recycled [Graphics] objects, reused across highlights to avoid GC churn during dense playback. */
    private val pool = ArrayDeque<Graphics>()

    /** Fill / stroke styles, reused for every drawn highlight (matches the old gold pulse colours). */
    private val fillStyle: dynamic = jsObjectOf("color" to 0xE8B84B, "alpha" to 0.1)
    private val strokeStyle: dynamic = jsObjectOf("width" to 1.0, "color" to 0xFFDC64, "alpha" to 1.0)

    /** Single ticker callback instance — stored so it can be `remove`d on [detach]. */
    private val tickCallback: (Ticker) -> Unit = { tick() }

    /** Window blur handler — stored so it can be removed on [detach]. */
    private val blurHandler: (Event) -> Unit = { cancelAll() }

    /** locationKey → the live highlight currently on the stage. */
    private val activeMarks = mutableMapOf<String, ActiveMark>()

    /** locationKey → last highlight time for rate-limiting. */
    private val lastHighlightTime = mutableMapOf<String, Double>()

    private class ActiveMark(
        val graphics: Graphics,
        /** Content-absolute position (independent of current scroll); offset by live scroll each frame. */
        val contentX: Double,
        val contentY: Double,
        val w: Double,
        val h: Double,
        val startMs: Double,
        val durationMs: Double,
    )

    // ── Batched scheduling ──────────────────────────────────────────────────

    private class PendingShow(
        val timeMs: Double,
        val key: String,
        val location: SourceLocation,
        val durationMs: Double,
    )

    /** Pending show operations, drained by the ticker once their start time arrives. */
    private val pendingOps = mutableListOf<PendingShow>()

    /** Max age for a show op before it is silently dropped (ms) — drops marks that piled up while backgrounded. */
    private val maxOverdueMs = 300.0

    /** Cap on the recycle pool so a one-off burst doesn't permanently retain thousands of Graphics. */
    private val maxPoolSize = 64

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun attachTo(editorView: EditorView) {
        view = editorView
        window.addEventListener("blur", blurHandler)
        ensurePixiApp()
    }

    /**
     * Supplies the lazily-loaded PixiJS addon. Called whenever the addon subscription emits — the
     * addon arrives asynchronously after mount, so this may be invoked once the editor is already up.
     * Idempotent.
     */
    fun setPixiAddon(newAddon: PixiJsAddon?) {
        if (newAddon == null || addon === newAddon) return
        addon = newAddon
        ensurePixiApp()
    }

    fun detach() {
        cancelAll()
        window.removeEventListener("blur", blurHandler)
        app?.let { application ->
            application.ticker.remove(tickCallback)
            val canvas = application.canvas
            canvas.parentElement?.removeChild(canvas)
            application.destroy(rendererDestroy = true)
        }
        pool.forEach { it.destroy() }
        pool.clear()
        app = null
        tickerRunning = false
        view = null
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        val chain = event.sourceLocations ?: return
        chain.locations.asReversed()
            .filter { it.isValid() }
            .filter { it.source == currentSource }
            .distinct()
            .take(maxHighlightsPerEvent).forEach { location ->
                scheduleForLocation(location, event)
            }
    }

    fun cancelAll() {
        pendingOps.clear()
        activeMarks.values.forEach { recycleMark(it) }
        activeMarks.clear()
        lastHighlightTime.clear()
        if (tickerRunning) {
            tickerRunning = false
            app?.ticker?.stop()
        }
    }

    // ── PixiJS setup ──────────────────────────────────────────────────────────

    private fun ensurePixiApp() {
        val view = this.view ?: return
        val addon = this.addon ?: return
        if (app != null || initializing) return

        initializing = true
        launch {
            try {
                val application = addon.createApplication()

                val opts: dynamic = jsObject()
                opts.width = view.dom.clientWidth
                opts.height = view.dom.clientHeight
                opts.backgroundAlpha = 0          // transparent — editor text shows through
                opts.antialias = true
                opts.preference = "webgl"         // broadest weak-GPU support
                opts.autoDensity = true
                opts.resolution = min(window.devicePixelRatio, 2.0)
                opts.resizeTo = view.dom          // Pixi auto-resizes the renderer to the editor
                application.init(opts).await()

                // Bail if we were detached (or reattached elsewhere) while init was in flight.
                if (this.view !== view) {
                    application.destroy(rendererDestroy = true)
                    initializing = false
                    return@launch
                }

                // `.cm-editor` is the non-scrolling root; the canvas overlays it and is offset by
                // scroll in software (keeps the WebGL framebuffer viewport-sized).
                if (window.getComputedStyle(view.dom).position == "static") {
                    view.dom.style.position = "relative"
                }

                val canvas = application.canvas
                canvas.style.apply {
                    position = "absolute"
                    top = "0"
                    left = "0"
                    setProperty("pointer-events", "none")
                    zIndex = "5"
                }
                view.dom.appendChild(canvas)

                application.ticker.add(tickCallback)
                application.ticker.stop()         // idle until a highlight appears
                app = application
                initializing = false

                // Kick the ticker if work queued up while the addon was loading.
                if (pendingOps.isNotEmpty() || activeMarks.isNotEmpty()) ensureTickerRunning()
            } catch (t: Throwable) {
                initializing = false
                console.error("Failed to initialize PixiJS highlight overlay", t)
            }
        }
    }

    private fun ensureTickerRunning() {
        val app = this.app ?: return
        if (tickerRunning) return
        tickerRunning = true
        app.ticker.start()
    }

    // ── Scheduling ────────────────────────────────────────────────────────────

    private fun scheduleForLocation(
        location: SourceLocation,
        event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent,
    ) {
        if (!document.hasFocus()) return

        val key = "${location.startLine}:${location.startColumn}:${location.endLine}:${location.endColumn}"
        val now = Date.now()

        val startTimeMs = event.startTime * 1000.0
        val endTimeMs = event.endTime * 1000.0
        val showAtMs = now + maxOf(1.0, startTimeMs - now)
        val durationMs = maxOf(200.0, minOf(10000.0, endTimeMs - startTimeMs))

        // Rate-limit
        val lastTime = lastHighlightTime[key]
        if (lastTime != null && (showAtMs - lastTime) < minIntervalMs) return

        // Max simultaneous
        if (activeMarks.size >= maxSimultaneousHighlights) return

        pendingOps.add(PendingShow(showAtMs, key, location, durationMs))
        ensureTickerRunning()
    }

    // ── Render loop ─────────────────────────────────────────────────────────

    private fun tick() {
        val app = this.app ?: return
        val view = this.view

        if (view == null) {
            if (tickerRunning) {
                tickerRunning = false
                app.ticker.stop()
            }
            return
        }

        val now = Date.now()

        // 1) Drain show ops whose start time has arrived.
        if (pendingOps.isNotEmpty()) {
            val iter = pendingOps.iterator()
            while (iter.hasNext()) {
                val op = iter.next()
                if (op.timeMs <= now) {
                    iter.remove()
                    // Drop ops that piled up while backgrounded.
                    if (now - op.timeMs <= maxOverdueMs) {
                        showHighlight(op.key, op.location, op.durationMs, now)
                    }
                }
            }
        }

        // 2) Animate + expire active marks. Cached content rects are offset by live scroll — no
        //    per-frame coordsAtPos (which would force a layout reflow).
        if (activeMarks.isNotEmpty()) {
            val scrollLeft = view.scrollDOM.scrollLeft.toDouble()
            val scrollTop = view.scrollDOM.scrollTop.toDouble()
            val canvasW = app.screen.width
            val canvasH = app.screen.height

            val expired = mutableListOf<String>()
            activeMarks.forEach { (key, mark) ->
                val elapsed = now - mark.startMs
                if (elapsed >= mark.durationMs) {
                    expired.add(key)
                    return@forEach
                }

                val x = mark.contentX - scrollLeft
                val y = mark.contentY - scrollTop
                val visible = x + mark.w > 0 && x < canvasW && y + mark.h > 0 && y < canvasH

                val g = mark.graphics
                g.visible = visible
                if (visible) {
                    g.x = x
                    g.y = y
                    g.alpha = pulseAlpha(elapsed / mark.durationMs)
                }
            }
            expired.forEach { key -> activeMarks.remove(key)?.let { recycleMark(it) } }
        }

        // 3) Idle-stop: nothing pending and nothing active → stop the rAF loop entirely.
        if (pendingOps.isEmpty() && activeMarks.isEmpty()) {
            tickerRunning = false
            app.ticker.stop()
        }
    }

    private fun showHighlight(key: String, location: SourceLocation, durationMs: Double, now: Double) {
        val view = this.view ?: return
        val app = this.app ?: return
        val addon = this.addon ?: return

        if (!document.hasFocus()) return

        // Deduplicate: recycle any existing mark for this location.
        activeMarks.remove(key)?.let { recycleMark(it) }

        // Resolve document positions.
        val from = lineColToPos(view, location.startLine, location.startColumn) ?: return
        val to = if (location.startLine == location.endLine) {
            lineColToPos(view, location.endLine, location.endColumn) ?: return
        } else {
            // Multi-line: highlight to end of start line.
            lineColToPos(view, location.startLine, location.startColumn + 2) ?: return
        }

        val fromCoords = view.coordsAtPos(from) ?: return
        val toCoords = view.coordsAtPos(to) ?: return

        // Content-absolute coordinates relative to the (non-scrolling) editor root. Adding the
        // current scroll back in makes them scroll-independent; the ticker subtracts live scroll.
        val editorRect = view.dom.getBoundingClientRect()
        val scrollLeft = view.scrollDOM.scrollLeft.toDouble()
        val scrollTop = view.scrollDOM.scrollTop.toDouble()

        val contentX = (fromCoords.left - editorRect.left) + scrollLeft
        val contentY = (fromCoords.top - editorRect.top) + scrollTop + 1.0
        val w = (toCoords.right - fromCoords.left) + 5.0
        val h = (fromCoords.bottom - fromCoords.top) + 2.0
        if (w <= 0 || h <= 0) return

        val g = pool.removeLastOrNull() ?: addon.createGraphics()
        g.clear()
        g.roundRect(0.0, 0.0, w, h, 3.0)
        g.fill(fillStyle)
        g.stroke(strokeStyle)
        g.x = contentX - scrollLeft
        g.y = contentY - scrollTop
        g.alpha = 1.0
        g.visible = true
        app.stage.addChild(g)

        activeMarks[key] = ActiveMark(g, contentX, contentY, w, h, now, durationMs)
        lastHighlightTime[key] = now
    }

    private fun recycleMark(mark: ActiveMark) {
        val g = mark.graphics
        g.removeFromParent()
        g.visible = false
        if (pool.size < maxPoolSize) {
            pool.addLast(g)
        } else {
            g.destroy()
        }
    }

    /**
     * Reproduces the old ease-out gold pulse as a container alpha over normalized lifetime [t] in
     * `[0, 1]`. The fill (0.1) and stroke (1.0) alphas are baked into the Graphics, so the border
     * fades `1.0 → 0.4 → 0.0` and the fill tracks it proportionally.
     */
    private fun pulseAlpha(t: Double): Double {
        val p = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)   // ease-out-cubic on the timeline
        return if (p < 0.7) 1.0 - (p / 0.7) * 0.6 else 0.4 * (1.0 - (p - 0.7) / 0.3)
    }

    private fun lineColToPos(view: EditorView, line: Int, column: Int): Int? {
        return try {
            val lineObj = view.state.doc.line(line)
            val pos = lineObj.from + (column - 1)
            if (pos in 0..view.state.doc.length) pos else null
        } catch (_: Throwable) {
            null
        }
    }
}
