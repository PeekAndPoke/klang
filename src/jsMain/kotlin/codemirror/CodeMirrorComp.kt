package io.peekandpoke.klang.codemirror

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.ComponentRef
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.utils.jsObject
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.OnChange
import io.peekandpoke.klang.codemirror.ext.*
import kotlinx.browser.document
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

    /**
     * Add a highlight at the specified location
     *
     * @param line 1-based line number
     * @param column 1-based column number
     * @param length Length of the highlight in characters
     * @return Highlight ID that can be used to remove it later
     */
    fun addHighlight(line: Int, column: Int, length: Int): String {
        val view = editor ?: return ""

        console.log("Adding highlight at line=$line, column=$column, length=$length")

        // Generate unique ID for this highlight
        val timestamp = js("Date.now()") as Double
        val random = js("Math.random()") as Double
        val highlightId = "highlight-${timestamp.toLong()}-${(random * 1000).toInt()}"

        try {
            // Convert line/column to position (CodeMirror uses 0-based line numbers)
            val lineObj = view.state.doc.line(line)
            val from = lineObj.from + (column - 1)
            val to = from + length

            // Ensure positions are valid
            if (from < 0 || to > view.state.doc.length) {
                console.warn("Highlight position out of bounds: from=$from, to=$to, doc.length=${view.state.doc.length}")
                return ""
            }

            // For now, use direct DOM manipulation
            // TODO: Implement proper StateField-based decorations
            val domPos = view.domAtPos(from)
            val element = domPos.node.asDynamic()

            console.log("domPos:", domPos)
            console.log("element:", element)

            if (element.nodeType == 3.toShort()) { // Text node
                // Wrap in a span
                val parent = element.parentNode
                if (parent != null) {
                    val span = document.createElement("span")
                    span.className = "cm-highlight-playing"
                    span.setAttribute("data-highlight-id", highlightId)

                    // This is simplified - proper implementation would need range handling
                    parent.replaceChild(span, element)
                    span.appendChild(element)
                }
            } else if (element.classList) {
                element.classList.add("cm-highlight-playing")
                element.setAttribute("data-highlight-id", highlightId)
            }

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
            // Find and remove element with this highlight ID
            val elements = view.dom.querySelectorAll("[data-highlight-id='$highlightId']")
            for (i in 0 until elements.length) {
                val element = elements.item(i)?.asDynamic()
                if (element?.classList) {
                    element.classList.remove("cm-highlight-playing")
                    element.removeAttribute("data-highlight-id")
                }
            }
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
            val elements = view.dom.querySelectorAll(".cm-highlight-playing")
            for (i in 0 until elements.length) {
                val element = elements.item(i)?.asDynamic()
                if (element?.classList) {
                    element.classList.remove("cm-highlight-playing")
                    element.removeAttribute("data-highlight-id")
                }
            }
        } catch (e: Throwable) {
            console.error("Error clearing highlights:", e)
        }
    }

    init {
        lifecycle {
            onMount {
                launch {
                    initialize()
                }
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
