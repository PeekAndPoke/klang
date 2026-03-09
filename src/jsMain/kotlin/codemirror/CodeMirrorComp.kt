package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.OnChange
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal
import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.ast.SourceLocationChain
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date

@Suppress("FunctionName")
fun Tag.CodeMirrorComp(
    code: String,
    onCodeChanged: OnChange<String>,
    extraExtensions: List<Extension> = emptyList(),
): ComponentRef<CodeMirrorComp> = comp(
    CodeMirrorComp.Props(code = code, onCodeChanged = onCodeChanged, extraExtensions = extraExtensions)
) {
    CodeMirrorComp(it)
}

class CodeMirrorComp(ctx: Ctx<Props>) : Component<CodeMirrorComp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val onCodeChanged: OnChange<String>,
        val extraExtensions: List<Extension> = emptyList(),
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val editorId = "codemirror-editor-${hashCode()}"
    private var editor: EditorView? by value(null)

    // ── Overlay highlight state ─────────────────────────────────────────────

    private val maxRefreshRatePerLocation: Int = 16
    private val maxSimultaneousHighlights: Int = 100
    var maxHighlightsPerEvent: Int = 10

    private val minIntervalMs: Double get() = 1000.0 / maxRefreshRatePerLocation

    /** Overlay container — lazily created inside the editor's scroll container. */
    private var overlay: HTMLElement? = null

    /** locationKey → mark element currently in the overlay. */
    private val activeMarks = mutableMapOf<String, HTMLElement>()

    /** locationKey → last highlight time for rate-limiting. */
    private val lastHighlightTime = mutableMapOf<String, Double>()

    /** All pending setTimeout IDs for bulk cancellation. */
    private val pendingTimeouts = mutableSetOf<Int>()

    init {
        lifecycle {
            onMount {
                initialize()
            }

            onUnmount {
                cancelAllHighlights()
                destroy()
            }
        }
    }

    private fun initialize() {
        val container = dom?.querySelector("#$editorId") as? HTMLDivElement ?: return

        // Set up callback for editor changes
        val updateFn = { update: dynamic ->
            if (update.docChanged) {
                if (update.docChanged) {
                    props.onCodeChanged(update.state.doc.toString())
                }
            }
        }

        // Create update listener extension
        val updateListenerExtension = EditorView.updateListener.of(updateFn)

        // Create a linter extension with autoPanel
        val linterSource: (EditorView) -> Array<Diagnostic> = js("(function(view) { return []; })")
        val linterConfig = jsObject<dynamic> {
            autoPanel = true
        }
        val linterExtension = linter(linterSource, linterConfig)

        // Create extensions array - combine basicSetup with our custom extensions
        val allExtensions = basicSetup.asDynamic().concat(
            arrayOf(
                javascript(),
                updateListenerExtension,
                linterExtension,
                lintGutter(),
                *props.extraExtensions.toTypedArray(),
            )
        ).unsafeCast<Array<Extension>>()

        // Create editor state config
        val stateConfig = jsObject<EditorStateConfig> {
            this.doc = props.code
            this.selection = null
            this.extensions = allExtensions
        }

        // Create editor state
        val state = EditorState.create(stateConfig).unsafeCast<EditorState>()

        // Create editor view config
        val viewConfig = jsObject<EditorViewConfig> {
            this.state = state
            this.parent = container
            this.root = null
            this.dispatch = null
        }

        // Create editor view
        try {
            editor = EditorView(viewConfig)
        } catch (e: Throwable) {
            console.error("Error initializing CodeMirror:", e)
        }
    }

    fun destroy() {
        editor?.destroy()
    }

    /**
     * Manually update the code in the editor
     */
    fun setCode(newCode: String) {
        val view = editor ?: return

        if (view.state.doc.toString() == newCode) return

        view.dispatch(
            view.state.update(
                jsObject {
                    this.changes = jsObject<dynamic> {
                        this.from = 0
                        this.to = view.state.doc.length
                        this.insert = newCode
                    }
                }
            )
        )
    }

    // ── Overlay Highlight API ───────────────────────────────────────────────

    /**
     * Schedule overlay highlights for a voice event's source locations.
     */
    fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        val chain = event.sourceLocations as? SourceLocationChain ?: return
        chain.locations.asReversed().take(maxHighlightsPerEvent).forEach { location ->
            scheduleForLocation(location, event)
        }
    }

    /**
     * Cancel all pending and active highlights.
     */
    fun cancelAllHighlights() {
        for (id in pendingTimeouts) window.clearTimeout(id)
        pendingTimeouts.clear()
        activeMarks.clear()
        lastHighlightTime.clear()
        overlay?.let { it.innerHTML = "" }
    }

    // ── Overlay Highlight Internals ─────────────────────────────────────────

    private fun scheduleForLocation(
        location: SourceLocation,
        event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent,
    ) {
        val key = "${location.startLine}:${location.startColumn}:${location.endLine}:${location.endColumn}"
        val now = Date.now()

        val startTimeMs = event.startTime * 1000.0
        val endTimeMs = event.endTime * 1000.0
        val startFromNowMs = maxOf(1.0, startTimeMs - now)
        val durationMs = maxOf(200.0, minOf(10000.0, endTimeMs - startTimeMs))

        // Rate-limit
        val projectedTime = now + startFromNowMs
        val lastTime = lastHighlightTime[key]
        if (lastTime != null && (projectedTime - lastTime) < minIntervalMs) return

        // Max simultaneous
        if (activeMarks.size >= maxSimultaneousHighlights) return

        // Schedule start
        scheduleTimeout(startFromNowMs.toInt()) {
            showHighlight(key, location, durationMs)
        }

        // Schedule removal
        val removeDelay = startFromNowMs + durationMs + 50.0
        scheduleTimeout(removeDelay.toInt()) {
            removeHighlight(key)
        }
    }

    private fun scheduleTimeout(delayMs: Int, action: () -> Unit) {
        var id = 0
        id = window.setTimeout({
            pendingTimeouts.remove(id)
            action()
        }, delayMs)
        pendingTimeouts.add(id)
    }

    private fun showHighlight(key: String, location: SourceLocation, durationMs: Double) {
        val view = editor ?: return

        // Check if window has focus
        val hasFocus = js("document.hasFocus()") as Boolean
        if (!hasFocus) return

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

    // ── Error Diagnostics ───────────────────────────────────────────────────

    /**
     * Set errors to display in the editor
     */
    fun setErrors(errors: List<EditorError>) {
        val view = editor ?: return

        try {
            val diagnostics = errors.mapNotNull { error ->
                try {
                    val lineObj = view.state.doc.line(error.line)
                    val from = lineObj.from + (error.col - 1)
                    val to = from + error.len

                    if (from < 0 || to > view.state.doc.length) {
                        console.warn(
                            "Diagnostic position out of bounds: from=$from, to=$to, doc.length=${view.state.doc.length}"
                        )
                        return@mapNotNull null
                    }

                    jsObject<Diagnostic> {
                        this.from = from
                        this.to = to
                        this.severity = "error"
                        this.message = error.message
                    }
                } catch (e: Throwable) {
                    console.error("Error converting EditorError to Diagnostic:", e)
                    null
                }
            }.toTypedArray()

            val transactionSpec = setDiagnostics(view.state, diagnostics)
            view.dispatch(transactionSpec.unsafeCast<dynamic>())
        } catch (e: Throwable) {
            console.error("Error updating diagnostics:", e)
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div("code-mirror-container") {
            div {
                id = editorId
            }
        }
    }
}
