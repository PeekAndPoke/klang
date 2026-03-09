package io.peekandpoke.klang.codemirror

import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.js.Date

/**
 * Overlay-based playback highlight buffer for CodeMirror.
 *
 * Renders absolutely-positioned `<mark>` elements in an overlay div inside
 * the editor's scroll container. Only lightweight DOM mutations are performed —
 * the CodeMirror state is never touched.
 *
 * Pending show/remove operations are batched into a single `requestAnimationFrame`
 * loop so that highlights due at the same time appear in the same paint frame.
 *
 * Owned by [CodeMirrorComp]; call [attachTo] once the EditorView is ready.
 */
class CodeMirrorHighlightBuffer(
    private val maxRefreshRatePerLocation: Int = 60,
    private val maxSimultaneousHighlights: Int = 500,
    var maxHighlightsPerEvent: Int = 100,
) {
    private val minIntervalMs: Double get() = 1000.0 / maxRefreshRatePerLocation

    private var view: EditorView? = null

    /** Overlay container — lazily created inside the editor's scroll container. */
    private var overlay: HTMLElement? = null

    /** locationKey → mark element currently in the overlay. */
    private val activeMarks = mutableMapOf<String, HTMLElement>()

    /** locationKey → last highlight time for rate-limiting. */
    private val lastHighlightTime = mutableMapOf<String, Double>()

    // ── Batched scheduling ──────────────────────────────────────────────────

    private sealed class PendingOp(val timeMs: Double) {
        class Show(timeMs: Double, val key: String, val location: SourceLocation, val durationMs: Double) :
            PendingOp(timeMs)

        class Remove(timeMs: Double, val key: String) : PendingOp(timeMs)
    }

    /** All pending operations sorted by target time. */
    private val pendingOps = mutableListOf<PendingOp>()

    /** Whether the frame loop is currently running. */
    private var frameLoopActive = false

    /** Max age for a Show op before it is silently dropped (ms). */
    private val maxOverdueMs = 300.0

    // ── Lifecycle ───────────────────────────────────────────────────────────

    fun attachTo(editorView: EditorView) {
        view = editorView
        window.addEventListener("blur", { cancelAll() })
    }

    fun detach() {
        cancelAll()
        overlay?.parentNode?.removeChild(overlay!!)
        overlay = null
        view = null
    }

    // ── Public API ──────────────────────────────────────────────────────────

    fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        val chain = event.sourceLocations as? SourceLocationChain ?: return
        chain.locations.asReversed().take(maxHighlightsPerEvent).forEach { location ->
            scheduleForLocation(location, event)
        }
    }

    fun cancelAll() {
        pendingOps.clear()
        frameLoopActive = false
        activeMarks.clear()
        lastHighlightTime.clear()
        overlay?.let { it.innerHTML = "" }
    }

    // ── Internals ───────────────────────────────────────────────────────────

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

        // Enqueue show + remove
        pendingOps.add(PendingOp.Show(showAtMs, key, location, durationMs))
        pendingOps.add(PendingOp.Remove(showAtMs + durationMs + 150.0, key))

        ensureFrameLoop()
    }

    private fun ensureFrameLoop() {
        if (frameLoopActive) return
        frameLoopActive = true
        window.requestAnimationFrame { tick() }
    }

    private fun tick() {
        if (!frameLoopActive) return
        if (pendingOps.isEmpty()) {
            frameLoopActive = false
            return
        }

        val now = Date.now()

        // Process all ops that are due
        val iter = pendingOps.iterator()
        while (iter.hasNext()) {
            val op = iter.next()
            if (op.timeMs <= now) {
                iter.remove()
                when (op) {
                    is PendingOp.Show -> {
                        // Drop Show ops that are more than 1s overdue — they accumulated while backgrounded
                        if (now - op.timeMs <= maxOverdueMs) {
                            showHighlight(op.key, op.location, op.durationMs)
                        }
                    }
                    is PendingOp.Remove -> removeHighlight(op.key)
                }
            }
        }

        // Continue loop if there's more pending
        if (pendingOps.isNotEmpty()) {
            window.requestAnimationFrame { tick() }
        } else {
            frameLoopActive = false
        }
    }

    private fun showHighlight(key: String, location: SourceLocation, durationMs: Double) {
        val view = this.view ?: return

        if (!document.hasFocus()) return

        // Remove existing mark for this location (deduplication)
        activeMarks.remove(key)?.let { it.parentNode?.removeChild(it) }

        // Resolve document positions
        val from = lineColToPos(view, location.startLine, location.startColumn) ?: return
        val to = if (location.startLine == location.endLine) {
            lineColToPos(view, location.endLine, location.endColumn) ?: return
        } else {
            // Multi-line: highlight to end of start line
            lineColToPos(view, location.startLine, location.startColumn + 2) ?: return
        }

        // Get pixel coordinates relative to the content
        val fromCoords = view.coordsAtPos(from) ?: return
        val toCoords = view.coordsAtPos(to) ?: return

        // Get the overlay parent's bounding rect for relative positioning
        // The overlay sits inside contentDOM.parentElement, so we must offset against that — not contentDOM itself
        val containerRect = view.contentDOM.parentElement?.getBoundingClientRect() ?: return

        val left = (fromCoords.left - containerRect.left)
        val top = (fromCoords.top - containerRect.top) + 1
        val width = (toCoords.right - fromCoords.left) + 5
        val height = (fromCoords.bottom - fromCoords.top) + 2

        if (width <= 0 || height <= 0) return

        // Create mark element
        val mark = document.createElement("mark") as HTMLElement
        mark.className = "cm-highlight-playing"
        mark.style.apply {
            position = "absolute"
            this.left = "${left}px"
            this.top = "${top}px"
            this.width = "${width}px"
            this.height = "${height}px"
            setProperty("animation-duration", "${durationMs}ms")
            setProperty("pointer-events", "none")
        }

        ensureOverlay(view).appendChild(mark)
        activeMarks[key] = mark
        lastHighlightTime[key] = Date.now()
    }

    private fun removeHighlight(key: String) {
        val mark = activeMarks.remove(key) ?: return
        mark.parentNode?.removeChild(mark)
    }

    private fun ensureOverlay(view: EditorView): HTMLElement {
        overlay?.let { return it }

        val el = document.createElement("div") as HTMLElement
        el.className = "cm-highlight-overlay"
        el.style.apply {
            position = "absolute"
            this.top = "0"
            this.left = "0"
            this.right = "0"
            this.bottom = "0"
            setProperty("pointer-events", "none")
            zIndex = "5"
        }

        // Insert into contentDOM's parent so it scrolls with the content
        view.contentDOM.parentElement?.appendChild(el)
        overlay = el
        return el
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
