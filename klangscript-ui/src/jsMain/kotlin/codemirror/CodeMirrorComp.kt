/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.Diagnostic
import io.peekandpoke.klang.codemirror.ext.EditorState
import io.peekandpoke.klang.codemirror.ext.EditorStateConfig
import io.peekandpoke.klang.codemirror.ext.EditorView
import io.peekandpoke.klang.codemirror.ext.EditorViewConfig
import io.peekandpoke.klang.codemirror.ext.Extension
import io.peekandpoke.klang.codemirror.ext.autocompletion
import io.peekandpoke.klang.codemirror.ext.basicSetup
import io.peekandpoke.klang.codemirror.ext.forEachDiagnostic
import io.peekandpoke.klang.codemirror.ext.forceLinting
import io.peekandpoke.klang.codemirror.ext.javascript
import io.peekandpoke.klang.codemirror.ext.lintGutter
import io.peekandpoke.klang.codemirror.ext.linter
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.intel.toCodeMirrorSeverity
import io.peekandpoke.klang.script.intel.toOffsets
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.popups.PopupsManager
import io.peekandpoke.kraft.utils.jsObject
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.OnChange
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.events.Event

/** Backward-compatible alias. */
@Suppress("unused")
typealias CodeMirrorComp = KlangScriptEditorComp

@Suppress("FunctionName")
fun Tag.KlangScriptEditorComp(
    code: String,
    onCodeChanged: OnChange<String>,
    availableLibraries: List<KlangScriptLibrary> = emptyList(),
    autoImportedLibraries: List<KlangScriptLibrary> = emptyList(),
    hoverPopup: HoverPopupCtrl? = null,
    hoverContent: (FlowContent.(KlangSymbol) -> Unit)? = null,
    popups: PopupsManager? = null,
    onNavigate: ((doc: KlangSymbol, event: dynamic) -> Unit)? = null,
    onOpenTool: ((toolName: String, ctx: KlangUiToolContext, argFrom: Int, event: dynamic) -> Unit)? = null,
): ComponentRef<KlangScriptEditorComp> = comp(
    KlangScriptEditorComp.Props(
        code = code,
        onCodeChanged = onCodeChanged,
        availableLibraries = availableLibraries,
        autoImportedLibraries = autoImportedLibraries,
        hoverPopup = hoverPopup,
        hoverContent = hoverContent,
        popups = popups,
        onNavigate = onNavigate,
        onOpenTool = onOpenTool,
    )
) {
    KlangScriptEditorComp(it)
}

class KlangScriptEditorComp(ctx: Ctx<Props>) : Component<KlangScriptEditorComp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val onCodeChanged: OnChange<String>,
        val availableLibraries: List<KlangScriptLibrary> = emptyList(),
        val autoImportedLibraries: List<KlangScriptLibrary> = emptyList(),
        val hoverPopup: HoverPopupCtrl? = null,
        val hoverContent: (FlowContent.(KlangSymbol) -> Unit)? = null,
        val popups: PopupsManager? = null,
        val onNavigate: ((doc: KlangSymbol, event: dynamic) -> Unit)? = null,
        val onOpenTool: ((toolName: String, ctx: KlangUiToolContext, argFrom: Int, event: dynamic) -> Unit)? = null,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val editorId = "codemirror-editor-${hashCode()}"
    private var editor: EditorView? by value(null)

    /** The underlying CodeMirror EditorView, exposed so the main app can attach external features (e.g. highlight buffer). */
    val editorView: EditorView? get() = editor

    private val theme = CodeMirrorTheme()

    /**
     * Runtime errors from the last compile or play attempt, published by [setErrors].
     *
     * Held rather than dispatched: the linter source is the SINGLE writer of the lint state (see
     * [lintDiagnostics]), so runtime errors and analyzer squiggles cannot overwrite each other.
     */
    private var runtimeErrors: List<EditorError> = emptyList()

    /** Set by [setErrors], cleared by [lintDiagnostics]: tells the lint plugin a re-run is due. */
    private var runtimeErrorsPending = false

    /** The analyzer diagnostics of the last run, kept so a stale analysis changes nothing. */
    private var analyzerCache: Array<Diagnostic> = emptyArray()

    /** Import-aware documentation context — owns hover docs + completion data. */
    private val docContext = EditorDocContext(
        availableLibraries = props.availableLibraries,
        autoImportedLibraries = props.autoImportedLibraries,
    ).also { it.processCodeImmediate(props.code) }

    init {
        lifecycle {
            onMount {
                initialize()
            }

            onUnmount {
                destroy()
            }
        }
    }

    private fun initialize() {
        val container = dom?.querySelector("#$editorId") as? HTMLDivElement ?: return

        // Set up callback for editor changes
        val updateFn = { update: dynamic ->
            if (update.docChanged) {
                val newCode = update.state.doc.toString()
                docContext.onCodeChanged(newCode)
                props.onCodeChanged(newCode)
            }
        }

        // Create update listener extension
        val updateListenerExtension = EditorView.updateListener.of(updateFn)

        // Create a linter extension with autoPanel
        val linterSource: (EditorView) -> Array<Diagnostic> = { view -> lintDiagnostics(view) }
        val linterConfig = jsObject<dynamic> {
            autoPanel = true
            // The source depends on more than the document: on the cached analysis, and on the
            // runtime errors [setErrors] hands it. Without this the lint plugin would only ever
            // schedule a run on a document change.
            needsRefresh = { _: dynamic -> runtimeErrorsPending }
        }
        val linterExtension = linter(linterSource, linterConfig)

        // Build DSL extensions (hover, completion) if libraries are available
        val dslExtensions = buildDslExtensions()

        // Create extensions array - combine basicSetup with our custom extensions
        val allExtensions = basicSetup.asDynamic().concat(
            arrayOf(
                theme.extension,
                javascript(),
                updateListenerExtension,
                linterExtension,
                lintGutter(),
                *dslExtensions.toTypedArray(),
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
            val view = EditorView(viewConfig)
            editor = view
        } catch (e: Throwable) {
            console.error("Error initializing CodeMirror:", e)
        }

        // Clicking an entry in the lint panel places the cursor at the START of
        // the error range and focuses the editor. The panel's own click handler
        // selects the whole range and keeps focus in its list, and the timeout runs
        // after it so our cursor/focus wins.
        //
        // The position comes from the selection the panel just made, not from a lookup of our
        // own: analyzer diagnostics are repetitive by construction ("unknown argument name" on
        // ten lines), so any key built from the message text sends every one of them to the
        // first match. The panel knows which entry was clicked; we only collapse its range.
        val panelClickListener: (Event) -> Unit = { event ->
            val diagEl = (event.target as? Element)?.closest(".cm-panel-lint .cm-diagnostic")
            if (diagEl != null) {
                window.setTimeout({
                    editor?.let { v ->
                        val from = v.state.selection.main.from
                        v.dispatch(jsObject<dynamic> {
                            this.selection = jsObject<dynamic> { this.anchor = from }
                            this.scrollIntoView = true
                        })
                        v.asDynamic().focus()
                    }
                }, 0)
            }
        }
        container.addEventListener("click", panelClickListener)
    }

    /** Builds DSL-aware extensions (hover docs, code completion) when libraries are configured. */
    private fun buildDslExtensions(): List<Extension> {
        if (props.availableLibraries.isEmpty()) return emptyList()

        val extensions = mutableListOf<Extension>()

        val hoverPopup = props.hoverPopup
        val hoverContent = props.hoverContent
        val popups = props.popups

        // Hover docs + context menu + tool badges
        if (hoverPopup != null && hoverContent != null && popups != null) {
            extensions.add(
                dslEditorExtension(
                    docProvider = { docContext.docProvider(it) },
                    analysisProvider = { docContext.lastAnalysis },
                    hoverPopup = hoverPopup,
                    hoverContent = hoverContent,
                    popups = popups,
                    onNavigate = props.onNavigate ?: { _, _ -> },
                    onOpenTool = props.onOpenTool,
                )
            )
        }

        // Code completion
        extensions.add(
            autocompletion(jsObject {
                this.override = arrayOf(dslCompletionSource(docContext))
                this.activateOnTyping = true
            })
        )

        return extensions
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

    // ── Diagnostics ─────────────────────────────────────────────────────────

    /**
     * Linter source, and the ONLY writer of the `@codemirror/lint` state.
     *
     * `setDiagnostics` replaces the whole diagnostic set rather than merging into it, so a second
     * writer silently deletes the first one's markers. [setErrors] used to be that second writer:
     * pressing Play published an empty set with no document change, which wiped every analyzer
     * squiggle, and the lint plugin only reschedules on a document change, so they stayed gone
     * until the next keystroke. Both halves are merged here instead.
     *
     * CodeMirror runs this on its own delay after a document change, so it reads whatever analysis
     * [EditorDocContext] holds at that moment: none before the first parse, and possibly a stale
     * one, since the parse is debounced and a failed parse keeps the last good AST on purpose.
     *
     * Wrapped because a source that throws produces no diagnostics at all. `@codemirror/lint`
     * hands the failure to `logException` rather than letting it escape, so the editor survives
     * either way, but the console line here says which of our two halves broke.
     */
    private fun lintDiagnostics(view: EditorView): Array<Diagnostic> {
        // Cleared first, so a throw below cannot leave `needsRefresh` permanently true and
        // re-run the source on every transaction from here on.
        runtimeErrorsPending = false

        return try {
            analyzerDiagnostics(view) + runtimeErrorDiagnostics(view)
        } catch (e: Throwable) {
            console.error("Error building diagnostics:", e)
            emptyArray()
        }
    }

    /**
     * The diagnostics of the cached `AnalyzedAst`, converted to offsets in the live document.
     *
     * `AnalyzedAst.source` is exactly the text its 1-based line/column pairs refer to. When the
     * document has moved on, re-deriving offsets from those pairs MOVES a squiggle that the lint
     * state has already re-mapped correctly through the change. So a stale analysis re-publishes
     * the previous diagnostics at the positions the lint state currently holds for them, which
     * leaves the editor looking exactly as it did.
     */
    private fun analyzerDiagnostics(view: EditorView): Array<Diagnostic> {
        val analysis = docContext.lastAnalysis ?: return remapAnalyzerDiagnostics(view)

        if (analysis.source != view.state.doc.toString()) {
            return remapAnalyzerDiagnostics(view)
        }

        val doc = CodeMirrorLinterDocument(view.state.doc)

        val built = analysis.diagnostics.mapNotNull { diagnostic ->
            val offsets = diagnostic.toOffsets(doc) ?: return@mapNotNull null

            jsObject<Diagnostic> {
                this.from = offsets.from
                this.to = offsets.to
                this.severity = diagnostic.severity.toCodeMirrorSeverity()
                this.message = diagnostic.message
            }
        }.toTypedArray()

        analyzerCache = built

        return built
    }

    /**
     * Re-publishes [analyzerCache] at the positions the lint state currently holds for it.
     *
     * Identity is the key: `forEachDiagnostic` walks the live lint state, which is built from the
     * objects the last run returned, and reports where each of them sits after the document
     * changes that followed. Anything the mapping dropped (its range fell off the end of the
     * document) drops out of the cache with it.
     */
    private fun remapAnalyzerDiagnostics(view: EditorView): Array<Diagnostic> {
        val cached = analyzerCache

        if (cached.isEmpty()) {
            return cached
        }

        val alive = mutableListOf<Diagnostic>()

        forEachDiagnostic(view.state) { diagnostic, from, to ->
            if (cached.any { it === diagnostic }) {
                // Safe to mutate: these objects are about to be handed straight back as the new
                // diagnostic set, replacing the state they were read from.
                diagnostic.from = from
                diagnostic.to = to
                alive.add(diagnostic)
            }
        }

        analyzerCache = alive.toTypedArray()

        return analyzerCache
    }

    /** The runtime errors of the last compile or play attempt, as diagnostics. */
    private fun runtimeErrorDiagnostics(view: EditorView): Array<Diagnostic> {
        return runtimeErrors.mapNotNull { error ->
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
    }

    /**
     * Set the runtime errors to display in the editor.
     *
     * Stores them and asks the lint plugin to re-run; the markers appear when [lintDiagnostics]
     * merges them with the analyzer diagnostics. Nothing is dispatched into the lint state from
     * here, so an empty list no longer erases the analyzer squiggles along with the errors.
     */
    fun setErrors(errors: List<EditorError>) {
        runtimeErrors = errors
        runtimeErrorsPending = true

        val view = editor ?: return

        try {
            // Two steps, and both are needed. `forceLinting` only shortcuts a run that is already
            // scheduled, and the lint plugin schedules one on a document change or when the
            // config's `needsRefresh` reports a change. A call here usually comes with no document
            // change at all, so the empty transaction gives the plugin an update to inspect,
            // `needsRefresh` answers for our pending flag, and `forceLinting` then skips the idle
            // delay so the marker shows up now rather than in three quarters of a second.
            view.dispatch(jsObject<dynamic> {})
            forceLinting(view)
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
