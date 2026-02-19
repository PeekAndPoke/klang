package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.OnChange
import io.peekandpoke.klang.codemirror.ext.*
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.HTMLDivElement

@Suppress("FunctionName")
fun Tag.CodeMirrorComp(
    code: String,
    onCodeChanged: OnChange<String>,
): ComponentRef<CodeMirrorComp> = comp(
    CodeMirrorComp.Props(code = code, onCodeChanged = onCodeChanged)
) {
    CodeMirrorComp(it)
}

class CodeMirrorComp(ctx: Ctx<Props>) : Component<CodeMirrorComp.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val code: String,
        val onCodeChanged: OnChange<String>,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    // Highlight data structure
    data class HighlightRange(
        val from: Int,
        val to: Int,
        val id: String,
        val durationMs: Double = 300.0,
    )

    // Define StateEffect types for managing highlights
    private val addHighlightEffect: StateEffectType<HighlightRange> = StateEffect.define<HighlightRange>()
    private val removeHighlightEffect: StateEffectType<String> = StateEffect.define()
    private val clearHighlightsEffect: StateEffectType<Unit> = StateEffect.define()

    private val editorId = "codemirror-editor-${hashCode()}"
    private var editor: EditorView? by value(null)

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

    // Create the highlight StateField extension
    private fun createHighlightExtension(): Extension {
        return StateField.define(
            jsObject<StateFieldConfig<DecorationSet>> {
                // Initialize with empty decorations
                this.create = { Decoration.none }

                // Update decorations based on transactions
                this.update = { decorations, tr ->
                    // Map existing decorations through document changes
                    var updated = decorations.map(tr.changes)

                    // Process state effects
                    tr.effects.forEach { effect ->
                        when {
                            effect.`is`(addHighlightEffect) -> {
                                val range = effect.value.unsafeCast<HighlightRange>()
                                updated = updated.update(
                                    jsObject {
                                        this.add = arrayOf(
                                            jsObject {
                                                this.from = range.from
                                                this.to = range.to
                                                this.value = Decoration.mark(
                                                    jsObject {
                                                        this.`class` = "cm-highlight-playing"
                                                        this.attributes = jsObject {
                                                            this.`data-highlight-id` = range.id
                                                            this.style = "animation-duration: ${range.durationMs}ms"
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                )
                            }

                            effect.`is`(removeHighlightEffect) -> {
                                val id = effect.value.unsafeCast<String>()
                                val newRanges = mutableListOf<Range<Decoration>>()
                                updated.between(0, tr.state.doc.length) { from, to, value ->
                                    val attrs = value.asDynamic().spec?.attributes
                                    if (attrs == null || attrs["data-highlight-id"] != id) {
                                        newRanges.add(
                                            jsObject {
                                                this.from = from
                                                this.to = to
                                                this.value = value
                                            }
                                        )
                                    }
                                }
                                updated = Decoration.set(newRanges.toTypedArray(), sort = true)
                            }

                            effect.`is`(clearHighlightsEffect) -> {
                                updated = Decoration.none
                            }
                        }
                    }

                    updated
                }

                // Provide decorations to the editor view
                this.provide = { field ->
                    EditorView.decorations.from(field)
                }
            }
        ).unsafeCast<Extension>()
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
        // linter() takes TWO parameters: source function and config object
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
                createHighlightExtension(),
                linterExtension,  // Initialize lint state with autoPanel
                lintGutter()      // Add lint gutter for error markers
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

        // console.log("viewConfig", viewConfig)

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

    /**
     * Add a highlight at the specified location
     *
     * @param line 1-based line number
     * @param column 1-based column number
     * @param length Length of the highlight in characters
     * @param durationMs Duration of the highlight animation in milliseconds
     * @return Highlight ID that can be used to remove it later
     */
    fun addHighlight(line: Int, column: Int, length: Int, durationMs: Double = 300.0): String {
        val view = editor ?: return ""

        // Check if window has focus - if not, clear all highlights and return immediately
        val hasFocus = js("document.hasFocus()") as Boolean
        if (!hasFocus) {
            return ""
        }

        // Generate unique ID for this highlight
        val timestamp = js("Date.now()") as Double
        val random = js("Math.random()") as Double
        val highlightId = "highlight-${timestamp.toLong()}-${(random * 1000).toInt()}"

        try {
            // Convert line/column to position (CodeMirror uses 1-based line numbers)
            val lineObj = view.state.doc.line(line)
            val from = lineObj.from + (column - 1)
            val to = from + length

            // Ensure positions are valid
            if (from < 0 || to > view.state.doc.length) {
                console.warn("Highlight position out of bounds: from=$from, to=$to, doc.length=${view.state.doc.length}")
                return ""
            }

            // Create the highlight range and effect
            val range = HighlightRange(from, to, highlightId, durationMs)
            val effect = addHighlightEffect.of(range.unsafeCast<HighlightRange>())

            // Dispatch transaction
            val transaction = view.state.update(
                jsObject {
                    this.effects = effect
                }
            )
            view.dispatch(transaction)
        } catch (e: Throwable) {
            console.error("Error adding highlight:", e)
            return ""
        }

        return highlightId
    }

    /**
     * Remove a specific highlight by ID
     */
    fun removeHighlight(highlightId: String) {
        val view = editor ?: return

        try {
            // Create the remove effect
            val effect = removeHighlightEffect.of(highlightId.unsafeCast<String>())

            // Dispatch transaction
            val transaction = view.state.update(
                jsObject {
                    this.effects = effect
                }
            )
            view.dispatch(transaction)
        } catch (e: Throwable) {
            console.error("Error removing highlight:", e)
        }
    }

    /**
     * Clear all highlights
     */
    fun clearHighlights() {
        val view = editor ?: return

        try {
            // Create the clear effect
            val effect = clearHighlightsEffect.of(Unit.unsafeCast<Unit>())

            // Dispatch transaction
            val transaction = view.state.update(
                jsObject {
                    this.effects = effect
                }
            )
            view.dispatch(transaction)
        } catch (e: Throwable) {
            console.error("Error clearing highlights:", e)
        }
    }

    /**
     * Set errors to display in the editor
     * Call this method to show error markers and the error panel
     */
    fun setErrors(errors: List<EditorError>) {
        val view = editor ?: return

        try {
            // Convert EditorError list to CodeMirror Diagnostic array
            val diagnostics = errors.mapNotNull { error ->
                try {
                    // Get the line (1-based in EditorError, 1-based in CodeMirror's line() function)
                    val lineObj = view.state.doc.line(error.line)
                    val from = lineObj.from + (error.col - 1)
                    val to = from + error.len

                    // Ensure positions are valid
                    if (from < 0 || to > view.state.doc.length) {
                        console.warn(
                            "Diagnostic position out of bounds: from=$from, to=$to, doc.length=${view.state.doc.length}"
                        )
                        return@mapNotNull null
                    }

                    // Create diagnostic object using dynamic (since we don't have full external definitions)
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

            // setDiagnostics returns a TransactionSpec, not a StateEffect
            // Dispatch it directly
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
