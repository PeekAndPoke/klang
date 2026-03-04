package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.*
import org.w3c.dom.HTMLElement

/**
 * Canonical string display / editor widget.
 */
@Suppress("FunctionName")
fun Tag.KlangBlocksStringEditorComp(
    value: String,
    ctx: KlangBlocksCtx,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit = {},
) = comp(
    KlangBlocksStringEditorComp.Props(
        value = value,
        ctx = ctx,
        onCommit = onCommit,
        onCancel = onCancel,
    )
) {
    KlangBlocksStringEditorComp(it)
}

class KlangBlocksStringEditorComp(ctx: Ctx<Props>) : Component<KlangBlocksStringEditorComp.Props>(ctx) {

    data class Props(
        val value: String,
        val ctx: KlangBlocksCtx,
        val onCommit: (String) -> Unit,
        val onCancel: () -> Unit,
    )

    // Reactive: switching this swaps the DOM element (key="d" ↔ key="e").
    private var isEditing: Boolean by value(false)

    // Non-reactive: changes here never trigger re-renders.
    private var editText: String = props.value
    private var isCancelling: Boolean = false

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun startEdit(x: Double? = null, y: Double? = null) {
        editText = props.value
        isEditing = true
        if (x != null && y != null) {
            tryPositionCursorAtPoint(x, y)
        }
    }

    private fun commitEdit() {
        if (!isEditing) return
        console.log("commitEdit:", editText)
        props.onCommit(editText)
        isEditing = false
    }

    private fun cancelEdit() {
        isCancelling = true
        editText = props.value
        isEditing = false
        props.onCancel()
    }

    override fun VDom.render() {
        val theme = props.ctx.theme

        div(classes = theme.styles.stringLiteralInline()) {
            key = "edit" + props.value.hashCode()

            tabIndex = "0"

            if (isEditing) {
                classes += theme.styles.stringLiteralInlineEditing()

                contentEditable = true

                onBlur { event ->
                    if (isCancelling) {
                        isCancelling = false
                        return@onBlur
                    }
                    editText = readInnerText(event.asDynamic().target)
                    commitEdit()
                }
                onInput { event ->
                    editText = readInnerText(event.asDynamic().target)
                }
                onKeyDown { event ->
                    when (event.key) {
                        "Enter" if !event.shiftKey -> {
                            event.preventDefault()
                            (event.currentTarget as? HTMLElement)?.blur()
                        }

                        "Escape" -> {
                            event.preventDefault()
                            cancelEdit()
                            (event.currentTarget as? HTMLElement)?.blur()
                        }
                    }
                }
                onMouseOut { event -> event.stopPropagation() }
                onMouseDown { event -> event.stopPropagation() }
            } else {
                onClick { event ->
                    event.stopPropagation()
                    startEdit(event.clientX.toDouble(), event.clientY.toDouble())
                }
                onFocus { startEdit() }
                onKeyDown { event ->
                    if (event.key == "Enter" || event.key == " ") {
                        event.preventDefault()
                        startEdit()
                    }
                }
                onMouseDown { event -> event.stopPropagation() }
            }

            console.log("editText: ", editText)

            +editText
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readInnerText(target: HTMLElement): String {
        console.log("innerText: ", target.innerText)
        return target.innerText
    }

    /**
     * Tries to place the cursor at viewport coordinates ([x], [y]) inside the edit div.
     * Uses `caretRangeFromPoint` (Chrome/Safari) with a fallback to `caretPositionFromPoint` (Firefox).
     */
    private fun tryPositionCursorAtPoint(x: Double, y: Double): Boolean {
        return try {
            val doc = document.asDynamic()
            var range: dynamic = doc.caretRangeFromPoint(x, y)

            if (range == null) {
                val pos = doc.caretPositionFromPoint(x, y) ?: return false
                val r = document.createRange()
                r.setStart(pos.offsetNode, (pos.offset as Number).toInt())
                r.collapse(true)
                range = r
            }

            val sel = window.asDynamic().getSelection()

            if (sel != null) {
                sel.removeAllRanges(); sel.addRange(range)
            }

            true
        } catch (_: Throwable) {
            false
        }
    }
}
