package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.components.Component
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
) = comp(
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

    private val editorId = "codemirror-editor-${hashCode()}"
    private var editor: EditorView? by value(null)

    private fun initialize() {
        val container = dom?.querySelector("#$editorId") as? HTMLDivElement ?: return

        // Set up callback for editor changes
        js("window.codemirrorCallback = undefined")
        js("window").codemirrorCallback = { newContent: String ->
            props.onCodeChanged(newContent)
        }

        // Create update listener extension
        val updateListenerExtension = EditorView.updateListener.of(
            js(
                """(function(update) {
                if (update.docChanged && window.codemirrorCallback) {
                    window.codemirrorCallback(update.state.doc.toString());
                }
            })"""
            )
        )

        // Create extensions array - combine basicSetup with our custom extensions
        val allExtensions = basicSetup.asDynamic().concat(
            arrayOf(
                javascript(),
                updateListenerExtension
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
        val viewConfig = jsObject<EditorViewConfig>() {
            this.state = state
            this.parent = container
            this.root = null
            this.dispatch = null
        }

        // Create editor view
        editor = EditorView(viewConfig)
    }

    fun destroy() {
        editor?.destroy()
    }

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

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div("code-mirror-container") {
            div {
                id = editorId
            }
        }
    }
}
