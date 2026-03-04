package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.contentEditable
import kotlinx.html.div
import kotlinx.html.tabIndex
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
    autoFocus: Boolean = false,
) = comp(
    KlangBlocksStringEditorComp.Props(
        value = value, ctx = ctx,
        onCommit = onCommit, onCancel = onCancel,
        autoFocus = autoFocus,
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
        val autoFocus: Boolean,
    )

    // Reactive: switching this swaps the DOM element (key="d" ↔ key="e").
    private var isEditing: Boolean by value(props.autoFocus)

    // Non-reactive: changes here never trigger re-renders.
    private var editText: String = props.value
    private var isCancelling: Boolean = false
    private var justStartedEditing: Boolean = props.autoFocus
    private var pendingClickX: Double? = null
    private var pendingClickY: Double? = null

    init {
        lifecycle {
            // After Preact mounts/updates the component, focus the edit div if we just entered
            // edit mode. Using both hooks covers autoFocus=true (onMount) and click-to-edit (onUpdate).
            onMount { handleFocusAfterEditStart() }
            onUpdate { handleFocusAfterEditStart() }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun startEdit(x: Double? = null, y: Double? = null) {
        editText = props.value
        pendingClickX = x
        pendingClickY = y
        justStartedEditing = true
        isEditing = true
    }

    private fun commitEdit() {
        if (!isEditing) return
        props.onCommit(editText)
        isEditing = false
    }

    private fun cancelEdit() {
        isCancelling = true
        editText = props.value
        isEditing = false
        props.onCancel()
    }

    // ── Focus management ──────────────────────────────────────────────────────

    private fun handleFocusAfterEditStart() {
        if (!justStartedEditing) return
        justStartedEditing = false
        val el = dom ?: return
        el.focus()
        val x = pendingClickX.also { pendingClickX = null }
        val y = pendingClickY.also { pendingClickY = null }
        if (x == null || y == null || !tryPositionCursorAtPoint(x, y)) {
            moveCursorToEnd(el)
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        val theme = props.ctx.theme
        if (isEditing) {
            div(classes = "${theme.styles.stringLiteralInline()} ${theme.styles.stringLiteralInlineEditing()}") {
                key = "e"
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
                onMouseDown { event -> event.stopPropagation() }

                +editText
            }
        } else {
            div(classes = theme.styles.stringLiteralInline()) {
                key = "d"
                tabIndex = "0"
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

                +props.value
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun readInnerText(target: dynamic): String =
        (target.innerText as? String)?.trimEnd('\n') ?: editText

    private fun moveCursorToEnd(el: HTMLElement) {
        try {
            val range = document.createRange()

            range.selectNodeContents(el)
            range.collapse(false)

            val sel = window.asDynamic().getSelection()

            if (sel != null) {
                sel.removeAllRanges(); sel.addRange(range)
            }
        } catch (_: Throwable) { /* ignore */
        }
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
