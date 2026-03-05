package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.html.Tag
import kotlinx.html.span
import kotlinx.html.tabIndex
import kotlinx.html.textArea
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Node

/**
 * Canonical string display / editor widget.
 *
 * Display mode: a styled span rendering text with optional atom highlights.
 * Edit mode:    a textarea for in-place editing.
 *
 * Pass [blockId] + [slotIndex] to opt into highlight subscriptions.
 */
@Suppress("FunctionName")
fun Tag.KlangBlocksStringEditorComp(
    value: String,
    ctx: KlangBlocksCtx,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit = {},
    blockId: String? = null,
    slotIndex: Int? = null,
) = comp(
    KlangBlocksStringEditorComp.Props(
        value = value,
        ctx = ctx,
        onCommit = onCommit,
        onCancel = onCancel,
        blockId = blockId,
        slotIndex = slotIndex,
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
        val blockId: String? = null,
        val slotIndex: Int? = null,
    )

    // Reactive: triggers re-render on change.
    private var isEditing: Boolean by value(false)
    // Non-reactive: changes here never trigger re-renders.
    private var editText: String = props.value
    private var isCancelling: Boolean = false
    private var pendingCursorOffset: Int? = null

    // Set to true only on the display→edit transition; cleared after the first onUpdate.
    private var justStartedEditing: Boolean = false

    init {
        lifecycle {
            onUpdate {
                // Only act on the single render that switches display→edit.
                if (!justStartedEditing) return@onUpdate
                justStartedEditing = false
                val el = dom as? HTMLTextAreaElement ?: return@onUpdate
                el.focus()
                val offset = pendingCursorOffset
                if (offset != null) {
                    pendingCursorOffset = null
                    el.setSelectionRange(offset, offset)
                }
            }
        }
    }

    /** Subscribe to highlights signals */
    private val highlights = highlights(props.ctx.highlights) { signal ->
        props.blockId != null && signal?.blockId == props.blockId && signal?.slotIndex == props.slotIndex
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun startEdit(cursorOffset: Int? = null) {
        editText = props.value
        pendingCursorOffset = cursorOffset
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

    override fun VDom.render() {
        val theme = props.ctx.theme

        if (isEditing) {
            renderTextArea(theme)
        } else {
            renderDisplay(theme)
        }
    }

    // ── Display mode — span with highlight spans ──────────────────────────────

    private fun VDom.renderDisplay(theme: KlangBlocksTheme) {
        val atomRanges = highlights.activeAtoms
            .filter { it.atomStart != null && it.atomEnd != null }
            .map { it.atomStart!! until it.atomEnd!! }
            .sortedBy { it.first }
            .let { mergeRanges(it) }

        span(classes = theme.styles.stringLiteralInline()) {
            key = "d"

            css {
                backgroundColor = Color(theme.inputBackgroundIdle)
                color = Color(theme.textPrimary)
                fontFamily = "monospace"
                border = Border(1.px, BorderStyle.solid, Color(theme.inlineItemBorder))
                cursor = Cursor.text
            }

            tabIndex = "0"

            onClick { event ->
                event.stopPropagation()
                startEdit(cursorOffset = textOffsetAtPoint(event.clientX.toDouble(), event.clientY.toDouble()))
            }
            onKeyDown { event ->
                if (event.key == "Enter" || event.key == " ") {
                    event.preventDefault()
                    startEdit()
                }
            }
            onMouseDown { event -> event.stopPropagation() }

            renderWithHighlights(props.value, atomRanges, theme.styles)
        }
    }

    // ── Edit mode — textarea ──────────────────────────────────────────────────

    private fun VDom.renderTextArea(theme: KlangBlocksTheme) {
        val rows = maxOf(1, editText.count { it == '\n' } + 1)

        textArea(classes = theme.styles.stringLiteralInline()) {
            key = "e"
            this.rows = rows.toString()
            attributes["value"] = editText

            css {
                backgroundColor = Color(theme.inputBackground)
                color = Color(theme.textPrimary)
                fontFamily = "monospace"
                fontSize = 12.px
                lineHeight = LineHeight("1.8")
                padding = Padding(1.px, 4.px)
                border = Border(1.px, BorderStyle.solid, Color(theme.inputBorder))
                cursor = Cursor.auto
                resize = Resize.none
            }

            onBlur { event ->
                if (isCancelling) {
                    isCancelling = false
                    return@onBlur
                }
                editText = event.asDynamic().target.value as? String ?: editText
                commitEdit()
            }
            onInput { event ->
                editText = event.asDynamic().target.value as String
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
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the character offset within the display span's text that corresponds
     * to the viewport point ([x], [y]), using a TreeWalker to accumulate the
     * correct absolute position across plain text nodes and highlight spans.
     */
    private fun textOffsetAtPoint(x: Double, y: Double): Int? {
        return try {
            val doc = document.asDynamic()
            var range: dynamic = doc.caretRangeFromPoint(x, y)

            if (range == null) {
                val pos = doc.caretPositionFromPoint(x, y) ?: return null
                val r = document.createRange()
                r.setStart(pos.offsetNode, (pos.offset as Number).toInt())
                r.collapse(true)
                range = r
            }

            val container: Node = range.startContainer
            val localOffset: Int = (range.startOffset as Number).toInt()
            val root = dom ?: return localOffset

            // Walk all text nodes in the display span, accumulating character counts
            // until we reach the node that was clicked.
            var absolute = 0
            val walker = document.asDynamic().createTreeWalker(root, 4 /* NodeFilter.SHOW_TEXT */)
            while (true) {
                val node: Node? = walker.nextNode() as? Node
                if (node == null) break
                if (node == container) {
                    absolute += localOffset
                    break
                }
                absolute += node.textContent?.length ?: 0
            }
            absolute
        } catch (_: Throwable) {
            null
        }
    }
}
