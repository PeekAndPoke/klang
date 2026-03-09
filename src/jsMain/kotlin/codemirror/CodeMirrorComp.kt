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
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.HTMLDivElement

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

    val highlightBuffer = CodeMirrorHighlightBuffer()

    init {
        lifecycle {
            onMount {
                initialize()
            }

            onUnmount {
                highlightBuffer.detach()
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
            val view = EditorView(viewConfig)
            editor = view
            highlightBuffer.attachTo(view)
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

    // ── Highlight API (delegates to buffer) ─────────────────────────────────

    fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        highlightBuffer.scheduleHighlight(event)
    }

    fun cancelAllHighlights() {
        highlightBuffer.cancelAll()
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
