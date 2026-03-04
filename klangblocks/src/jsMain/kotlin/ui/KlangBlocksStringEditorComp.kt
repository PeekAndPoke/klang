package io.peekandpoke.klang.blocks.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.streams.ops.filter
import kotlinx.browser.window
import kotlinx.css.*
import kotlinx.html.Tag
import kotlinx.html.span
import kotlinx.html.tabIndex
import kotlinx.html.textArea
import org.w3c.dom.HTMLElement

/**
 * Canonical string display / editor widget.
 *
 * Display mode: a styled div rendering text with optional atom highlights.
 * Edit mode:    a transparent textarea for in-place editing.
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
    private var activeAtoms: Set<KlangBlockAtomKey> by value(emptySet())

    // Non-reactive: changes here never trigger re-renders.
    private var editText: String = props.value
    private var isCancelling: Boolean = false
    private val atomTimeouts = mutableMapOf<KlangBlockAtomKey, Int>()

    @Suppress("unused")
    private val highlightSub by subscribingTo(
        props.ctx.highlights.filter { signal ->
            signal?.blockId == props.blockId && signal?.slotIndex == props.slotIndex
        }
    ) { signal ->
        if (signal != null && props.blockId != null) {
            val key = KlangBlockAtomKey(signal.slotIndex, signal.atomStart, signal.atomEnd)
            atomTimeouts[key]?.let { window.clearTimeout(it) }
            activeAtoms = activeAtoms + key
            atomTimeouts[key] = window.setTimeout({
                activeAtoms = activeAtoms - key
                atomTimeouts.remove(key)
            }, signal.durationMs.toInt())
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun startEdit() {
        editText = props.value
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

    // ── Display mode — div with highlight spans ───────────────────────────────

    private fun VDom.renderDisplay(theme: KlangBlocksTheme) {
        val atomRanges = activeAtoms
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
            }

            tabIndex = "0"

            onClick { event ->
                event.stopPropagation()
                startEdit()
            }
            onFocus { startEdit() }
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

    // ── Edit mode — transparent textarea ─────────────────────────────────────

    private fun VDom.renderTextArea(theme: KlangBlocksTheme) {
        val rows = maxOf(1, editText.count { it == '\n' } + 1)

        textArea(classes = theme.styles.stringLiteralInline() + " " + theme.styles.stringLiteralInlineEditing()) {
            key = "e"
            this.rows = rows.toString()
            attributes["value"] = editText

            css {
                backgroundColor = Color(theme.inputBackground)
                color = Color(theme.textPrimary)
                resize = Resize.none
                fontFamily = "monospace"
            }

            autoFocus = true

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
}
