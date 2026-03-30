package io.peekandpoke.klang.ui.codemirror

import io.peekandpoke.klang.codemirror.ext.*
import io.peekandpoke.klang.script.KlangScriptLibrary
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
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.id
import org.w3c.dom.HTMLDivElement

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
        val linterSource: (EditorView) -> Array<Diagnostic> = js("(function(view) { return []; })")
        val linterConfig = jsObject<dynamic> {
            autoPanel = true
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
                    registryProvider = { docContext.registry },
                    astIndexProvider = { docContext.lastAstIndex },
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
