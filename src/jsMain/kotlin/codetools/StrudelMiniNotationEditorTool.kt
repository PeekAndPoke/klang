package io.peekandpoke.klang.codetools

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.*
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern
import io.peekandpoke.klang.ui.KlangUiTool
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import kotlinx.css.*
import kotlinx.html.*

// ── Tool factory ──────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] that edits mini-notation pattern strings visually.
 *
 * Each atom in the pattern is rendered as a chip. When [atomTool] is set and it implements
 * [KlangUiToolEmbeddable], clicking "edit" on an atom chip expands an inline panel below the
 * chip row showing the sub-tool content with live updates on every change.
 * For non-embeddable [atomTool], the sub-tool is opened in a modal as before.
 */
class StrudelMiniNotationEditorTool(
    private val atomTool: KlangUiTool? = null,
) : KlangUiTool {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelMiniNotationEditorComp(ctx, atomTool)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────
@Suppress("FunctionName")
private fun Tag.StrudelMiniNotationEditorComp(toolCtx: KlangUiToolContext, atomTool: KlangUiTool?) =
    comp(StrudelMiniNotationEditorComp.Props(toolCtx, atomTool)) { StrudelMiniNotationEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelMiniNotationEditorComp(ctx: Ctx<Props>) : Component<StrudelMiniNotationEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val atomTool: KlangUiTool?)

    // ── Parse initial value ───────────────────────────────────────────────

    private val initialPattern: MnPattern = run {
        val raw = props.toolCtx.currentValue
            ?.trim()?.removePrefix("\"")?.removeSuffix("\"") ?: ""
        parseMiniNotationMnPattern(raw)
    }

    // ── State ─────────────────────────────────────────────────────────────

    /** Current nodes in the primary (first) layer. */
    private var nodes: List<MnNode> by value(initialPattern.items.toList())

    /** Index of the atom currently open for inline text editing, or null. */
    private var editingIndex: Int? by value(null)

    /** Live text in the inline input field. */
    private var editingText: String by value("")

    /**
     * Index of the atom whose sub-tool panel is currently expanded inline, or null.
     * Only used when [Props.atomTool] is a [KlangUiToolEmbeddable].
     */
    private var expandedIndex: Int? by value(null)

    private val initialValue: String = props.toolCtx.currentValue ?: "\"\""
    private var lastCommittedValue: String by value(initialValue)

    // ── Derived helpers ───────────────────────────────────────────────────

    private fun buildPattern(): MnPattern = MnPattern(listOf(nodes))
    private fun buildValue(): String = "\"${MnRenderer.render(buildPattern())}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = lastCommittedValue != buildValue()

    // ── Mutations ─────────────────────────────────────────────────────────

    private fun deleteNode(index: Int) {
        if (editingIndex == index) {
            editingIndex = null
            editingText = ""
        }
        // Adjust or close the expanded panel
        when {
            expandedIndex == index -> expandedIndex = null
            expandedIndex != null && expandedIndex!! > index -> expandedIndex = expandedIndex!! - 1
        }
        nodes = nodes.toMutableList().also { it.removeAt(index) }
    }

    private fun updateAtomValue(index: Int, newValue: String) {
        val atom = nodes.getOrNull(index) as? MnNode.Atom ?: return
        nodes = nodes.toMutableList().also { it[index] = atom.copy(value = newValue) }
    }

    private fun startInlineEdit(index: Int, currentText: String) {
        editingIndex = index
        editingText = currentText
    }

    private fun commitInlineEdit(index: Int) {
        updateAtomValue(index, editingText.trim())
        editingIndex = null
        editingText = ""
    }

    private fun cancelInlineEdit() {
        editingIndex = null
        editingText = ""
    }

    private fun addAtom() {
        val newIndex = nodes.size
        nodes = nodes + MnNode.Atom("")
        when {
            props.atomTool is KlangUiToolEmbeddable -> expandedIndex = newIndex
            props.atomTool != null -> openAtomTool(newIndex, onCancelAction = { deleteNode(newIndex) })
            else -> startInlineEdit(newIndex, "")
        }
    }

    /** Toggle the inline expanded panel for an embeddable atom tool. */
    private fun toggleExpandedPanel(index: Int) {
        expandedIndex = if (expandedIndex == index) null else index
    }

    private fun openAtomTool(index: Int, onCancelAction: () -> Unit = {}) {
        val atom = nodes.getOrNull(index) as? MnNode.Atom ?: return
        val subCtx = KlangUiToolContext(
            symbol = props.toolCtx.symbol,
            paramName = props.toolCtx.paramName,
            currentValue = "\"${atom.value}\"",
            onCommit = { result ->
                updateAtomValue(index, result.trim().removePrefix("\"").removeSuffix("\""))
            },
            onCancel = onCancelAction,
        )
        modals.show { handle ->
            CodeToolModal(handle) {
                props.atomTool!!.apply {
                    render(
                        subCtx.copy(
                            onCancel = { handle.close(); subCtx.onCancel() },
                        )
                    )
                }
            }
        }
    }

    // ── Outer tool actions ────────────────────────────────────────────────

    private fun onCancel() = props.toolCtx.onCancel()

    private fun onReset() {
        nodes = initialPattern.items.toList()
        editingIndex = null
        editingText = ""
        expandedIndex = null
        lastCommittedValue = initialValue
        props.toolCtx.onCommit(initialValue)
    }

    private fun onCommit() {
        val v = buildValue()
        lastCommittedValue = v
        props.toolCtx.onCommit(v)
    }

    // ── Render ────────────────────────────────────────────────────────────

    override fun VDom.render() {
        ui.segment {
            css { minWidth = 50.vw }

            ui.small.header { +"Mini-Notation-Editor" }

            // ── Chip row ─────────────────────────────────────────────────
            div {
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    alignItems = Align.center
                    gap = 6.px
                    padding = Padding(8.px)
                    backgroundColor = Color("#f8f9fa")
                    borderRadius = 4.px
                    minHeight = 48.px
                }

                nodes.forEachIndexed { i, node -> renderChip(i, node) }

                // [+] Add atom
                ui.mini.basic.circular.icon.button {
                    css { marginLeft = 2.px }
                    onClick { addAtom() }
                    icon.plus()
                }
            }

            // ── Inline sub-tool panel ─────────────────────────────────────
            val ei = expandedIndex
            val tool = props.atomTool
            if (tool is KlangUiToolEmbeddable && ei != null) {
                renderExpandedPanel(ei, tool)
            }

            ui.divider {}

            // ── Bottom bar ───────────────────────────────────────────────
            noui.basic.segment {
                css {
                    padding = Padding(0.px)
                    display = Display.flex
                    justifyContent = JustifyContent.spaceBetween
                    alignItems = Align.center
                    gap = 8.px
                }

                ui.small.basic.label { +buildValue() }

                noui.basic.segment {
                    css { padding = Padding(0.px); display = Display.flex; gap = 8.px }

                    ui.basic.button {
                        onClick { onCancel() }
                        icon.times()
                        +"Cancel"
                    }

                    ui.basic.givenNot(isInitialModified) { disabled }.button {
                        onClick { onReset() }
                        icon.undo()
                        +"Reset"
                    }

                    ui.black.givenNot(isCurrentModified) { disabled }.button {
                        onClick { onCommit() }
                        icon.check()
                        +"Update"
                    }
                }
            }
        }
    }

    // ── Expanded inline sub-tool panel ────────────────────────────────────

    private fun FlowContent.renderExpandedPanel(index: Int, tool: KlangUiToolEmbeddable) {
        val atom = nodes.getOrNull(index) as? MnNode.Atom ?: return
        val subCtx = KlangUiToolContext(
            symbol = props.toolCtx.symbol,
            paramName = props.toolCtx.paramName,
            currentValue = "\"${atom.value}\"",
            onCommit = { result ->
                updateAtomValue(index, result.trim().removePrefix("\"").removeSuffix("\""))
            },
            onCancel = { expandedIndex = null },
        )
        div {
            css {
                marginTop = 8.px
                padding = Padding(12.px)
                border = Border(1.px, BorderStyle.solid, Color("#2185d0").withAlpha(0.3))
                borderRadius = 4.px
                backgroundColor = Color("#f0f7ff")
            }
            tool.apply { renderEmbedded(subCtx) }
        }
    }

    // ── Chip dispatch ─────────────────────────────────────────────────────

    private fun FlowContent.renderChip(index: Int, node: MnNode) {
        when {
            node is MnNode.Atom && editingIndex == index -> renderAtomChipEditing(index)
            node is MnNode.Atom && props.atomTool != null -> renderAtomChipWithTool(index, node)
            node is MnNode.Atom -> renderAtomChipPlain(index, node)
            node is MnNode.Rest -> renderRestChip(index)
            else -> renderPlaceholderChip(index, node)
        }
    }

    // ── Plain atom chip — click to edit inline ────────────────────────────

    private fun FlowContent.renderAtomChipPlain(index: Int, node: MnNode.Atom) {
        div {
            css { chipBase(); cursor = Cursor.pointer }
            onClick { startInlineEdit(index, node.value) }

            span {
                css { fontFamily = "monospace"; fontSize = 13.px }
                +(node.value.ifEmpty { "…" })
            }
            chipDeleteButton(index)
        }
    }

    // ── Atom chip currently being edited inline ───────────────────────────

    private fun FlowContent.renderAtomChipEditing(index: Int) {
        div {
            css { chipBase(); borderColor = Color("#2185d0") }

            input {
                css {
                    fontFamily = "monospace"
                    fontSize = 13.px
                    border = Border.none
                    outline = Outline.none
                    minWidth = 40.px
                    width = LinearDimension.auto
                    background = "none"
                }
                value = editingText
                autoFocus = true

                onInput { e -> editingText = e.target.asDynamic().value as String }
                onKeyDown { e ->
                    when (e.key) {
                        "Enter" -> commitInlineEdit(index)
                        "Escape" -> cancelInlineEdit()
                    }
                }
                onBlur { commitInlineEdit(index) }
            }
        }
    }

    // ── Atom chip with sub-tool ────────────────────────────────────────────

    private fun FlowContent.renderAtomChipWithTool(index: Int, node: MnNode.Atom) {
        val isEmbeddable = props.atomTool is KlangUiToolEmbeddable
        val isExpanded = expandedIndex == index

        div {
            css {
                chipBase()
                if (isExpanded) borderColor = Color("#2185d0")
            }

            span {
                css { fontFamily = "monospace"; fontSize = 13.px }
                +(node.value.ifEmpty { "…" })
            }

            ui.mini.basic.given(isExpanded) { active }.button {
                css { marginLeft = 4.px; marginRight = 0.px }
                onClick {
                    if (isEmbeddable) toggleExpandedPanel(index)
                    else openAtomTool(index)
                }
                +(if (isExpanded) "▼" else "edit")
            }

            chipDeleteButton(index)
        }
    }

    // ── Rest chip ─────────────────────────────────────────────────────────

    private fun FlowContent.renderRestChip(index: Int) {
        div {
            css {
                chipBase()
                backgroundColor = Color("#f0f0f0")
                color = Color("#999")
            }
            span { css { fontFamily = "monospace"; fontSize = 13.px; fontStyle = FontStyle.italic }; +"~" }
            chipDeleteButton(index)
        }
    }

    // ── Placeholder chip for groups / alternation (Phase 3) ───────────────

    private fun FlowContent.renderPlaceholderChip(index: Int, node: MnNode) {
        val label = when (node) {
            is MnNode.Group -> "[…]"
            is MnNode.Alternation -> "<…>"
            is MnNode.Choice -> "a|b"
            else -> "?"
        }
        div {
            css {
                chipBase()
                borderStyle = BorderStyle.dashed
                color = Color("#888")
            }
            span { css { fontFamily = "monospace"; fontSize = 13.px }; +label }
            chipDeleteButton(index)
        }
    }

    // ── Shared chip helpers ───────────────────────────────────────────────

    private fun CssBuilder.chipBase() {
        display = Display.inlineFlex
        alignItems = Align.center
        gap = 4.px
        padding = Padding(4.px, 8.px)
        border = Border(1.px, BorderStyle.solid, Color("#ccc"))
        borderRadius = 4.px
        backgroundColor = Color("white")
    }

    private fun FlowContent.chipDeleteButton(index: Int) {
        span {
            css {
                marginLeft = 4.px
                color = Color("#bbb")
                cursor = Cursor.pointer
                userSelect = UserSelect.none
                put("line-height", "1")
            }
            onClick { e ->
                e.stopPropagation()
                deleteNode(index)
            }
            +"×"
        }
    }
}
