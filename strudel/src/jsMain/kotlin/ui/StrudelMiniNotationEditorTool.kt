package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import de.peekandpoke.kraft.semanticui.forms.UiInputField
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
import io.peekandpoke.klang.ui.codetools.CodeToolModal
import kotlinx.css.*
import kotlinx.html.*

// ── Tool factory ──────────────────────────────────────────────────────────────

/**
 * A [KlangUiTool] that edits mini-notation pattern strings visually.
 *
 * Supports flat atoms, groups `[ ]`, alternation `< >`, rest `~`, and modifiers.
 * When [atomTool] implements [KlangUiToolEmbeddable], clicking an atom chip expands
 * an inline editor panel below the chip row instead of opening a modal.
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

    /** Top-level nodes (first layer of the pattern). */
    private var nodes: List<MnNode> by value(initialPattern.items.toList())

    /**
     * Path to the atom currently being inline-edited, or null.
     * A path [i] points to top-level[i]; [i, j] points to top-level[i]'s child[j].
     */
    private var editingPath: List<Int>? by value(null)

    /** Live text for the inline input. */
    private var editingText: String by value("")

    /**
     * Top-level index of the atom whose sub-tool panel is expanded inline.
     * Only applies to top-level atoms when [Props.atomTool] is [KlangUiToolEmbeddable].
     */
    private var expandedIndex: Int? by value(null)

    /**
     * Specifies which [+] dropdown menu is currently open.
     * null = no menu open; emptyList() = top-level menu; [i] = inside group/alternation at index i.
     */
    private var addMenuPath: List<Int>? by value(null)

    /**
     * Path to the node whose modifier panel is open, or null.
     */
    private var modsEditPath: List<Int>? by value(null)

    private val initialValue: String = props.toolCtx.currentValue ?: "\"\""
    private var lastCommittedValue: String by value(initialValue)

    // ── Derived ───────────────────────────────────────────────────────────

    private fun buildPattern(): MnPattern = MnPattern(listOf(nodes))
    private fun buildValue(): String = "\"${MnRenderer.render(buildPattern())}\""
    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = lastCommittedValue != buildValue()

    // ── Tree helpers ──────────────────────────────────────────────────────

    private fun getNodeAt(path: List<Int>): MnNode? {
        if (path.isEmpty()) return null
        var list = nodes
        for (i in 0 until path.size - 1) {
            val node = list.getOrNull(path[i]) ?: return null
            list = node.childrenOrEmpty()
        }
        return list.getOrNull(path.last())
    }

    private fun getChildrenAt(parentPath: List<Int>): List<MnNode> =
        if (parentPath.isEmpty()) nodes
        else (getNodeAt(parentPath)?.childrenOrEmpty() ?: emptyList())

    private fun updateAt(path: List<Int>, new: MnNode) {
        nodes = updateInList(nodes, path, new)
    }

    private fun deleteAt(path: List<Int>) {
        // Close any related state
        if (editingPath == path) {
            editingPath = null; editingText = ""
        }
        if (editingPath?.startsWith(path) == true) {
            editingPath = null; editingText = ""
        }
        if (modsEditPath == path || modsEditPath?.startsWith(path) == true) modsEditPath = null
        if (path.size == 1) {
            val i = path[0]
            if (expandedIndex == i) expandedIndex = null
            else if (expandedIndex != null && expandedIndex!! > i) expandedIndex = expandedIndex!! - 1
        }
        nodes = deleteFromList(nodes, path)
    }

    private fun addAt(parentPath: List<Int>, new: MnNode) {
        nodes = addToList(nodes, parentPath, new)
    }

    // Recursive tree update helpers

    private fun updateInList(list: List<MnNode>, path: List<Int>, new: MnNode): List<MnNode> {
        if (path.size == 1) return list.toMutableList().also { it[path[0]] = new }
        val i = path[0]
        val node = list.getOrNull(i) ?: return list
        return list.toMutableList().also { it[i] = node.updateChild(path.drop(1), new) }
    }

    private fun deleteFromList(list: List<MnNode>, path: List<Int>): List<MnNode> {
        if (path.size == 1) return list.toMutableList().also { it.removeAt(path[0]) }
        val i = path[0]
        val node = list.getOrNull(i) ?: return list
        return list.toMutableList().also { it[i] = node.deleteChild(path.drop(1)) }
    }

    private fun addToList(list: List<MnNode>, parentPath: List<Int>, new: MnNode): List<MnNode> {
        if (parentPath.isEmpty()) return list + new
        val i = parentPath[0]
        val node = list.getOrNull(i) ?: return list
        return list.toMutableList().also { it[i] = node.addChild(parentPath.drop(1), new) }
    }

    private fun MnNode.childrenOrEmpty(): List<MnNode> = when (this) {
        is MnNode.Group -> layers.firstOrNull() ?: emptyList()
        is MnNode.Alternation -> items
        else -> emptyList()
    }

    private fun MnNode.withChildren(children: List<MnNode>): MnNode = when (this) {
        is MnNode.Group -> copy(layers = listOf(children))
        is MnNode.Alternation -> copy(items = children)
        else -> this
    }

    private fun MnNode.updateChild(path: List<Int>, new: MnNode): MnNode =
        withChildren(updateInList(childrenOrEmpty(), path, new))

    private fun MnNode.deleteChild(path: List<Int>): MnNode =
        withChildren(deleteFromList(childrenOrEmpty(), path))

    private fun MnNode.addChild(parentPath: List<Int>, new: MnNode): MnNode =
        withChildren(addToList(childrenOrEmpty(), parentPath, new))

    private fun List<Int>.startsWith(prefix: List<Int>): Boolean =
        size > prefix.size && subList(0, prefix.size) == prefix

    // ── Mutations ─────────────────────────────────────────────────────────

    private fun startInlineEdit(path: List<Int>, text: String) {
        editingPath = path
        editingText = text
    }

    private fun commitInlineEdit(path: List<Int>) {
        val atom = getNodeAt(path) as? MnNode.Atom ?: run { cancelInlineEdit(); return }
        updateAt(path, atom.copy(value = editingText.trim()))
        editingPath = null
        editingText = ""
    }

    private fun cancelInlineEdit() {
        editingPath = null
        editingText = ""
    }

    private fun addAtomAt(parentPath: List<Int>) {
        val newIndex = getChildrenAt(parentPath).size
        addAt(parentPath, MnNode.Atom(""))
        addMenuPath = null
        val newPath = parentPath + newIndex
        when {
            props.atomTool is KlangUiToolEmbeddable && parentPath.isEmpty() ->
                expandedIndex = newIndex

            props.atomTool != null && parentPath.isEmpty() ->
                openAtomTool(newIndex, onCancelAction = { deleteAt(newPath) })

            else ->
                startInlineEdit(newPath, "")
        }
    }

    private fun addGroupAt(parentPath: List<Int>) {
        addAt(parentPath, MnNode.Group(layers = listOf(emptyList())))
        addMenuPath = null
    }

    private fun addAlternationAt(parentPath: List<Int>) {
        addAt(parentPath, MnNode.Alternation(items = emptyList()))
        addMenuPath = null
    }

    private fun addRestAt(parentPath: List<Int>) {
        addAt(parentPath, MnNode.Rest)
        addMenuPath = null
    }

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
                val newValue = result.trim().removePrefix("\"").removeSuffix("\"")
                updateAt(listOf(index), atom.copy(value = newValue))
            },
            onCancel = onCancelAction,
        )
        modals.show { handle ->
            CodeToolModal(handle) {
                props.atomTool!!.apply {
                    render(subCtx.copy(onCancel = { handle.close(); subCtx.onCancel() }))
                }
            }
        }
    }

    // ── Outer tool actions ────────────────────────────────────────────────

    private fun onCancel() = props.toolCtx.onCancel()

    private fun onReset() {
        nodes = initialPattern.items.toList()
        editingPath = null; editingText = ""
        expandedIndex = null; addMenuPath = null; modsEditPath = null
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

            ui.small.header { +"Pattern Editor" }

            // ── Top-level chip row ────────────────────────────────────────
            chipRow(nodes, emptyList())

            // ── Inline sub-tool panel (top-level atoms only) ──────────────
            val ei = expandedIndex
            val tool = props.atomTool
            if (tool is KlangUiToolEmbeddable && ei != null) {
                renderExpandedPanel(ei, tool)
            }

            // ── Modifier editing panel ────────────────────────────────────
            val mep = modsEditPath
            val modsNode = mep?.let { getNodeAt(it) }
            if (mep != null && modsNode != null) {
                renderModsPanel(mep, modsNode)
            }

            ui.divider {}

            // ── Bottom bar ────────────────────────────────────────────────
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

    // ── Chip row (recursive) ──────────────────────────────────────────────

    private fun FlowContent.chipRow(children: List<MnNode>, parentPath: List<Int>) {
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                alignItems = Align.center
                gap = 6.px
                padding = Padding(8.px)
                if (parentPath.isEmpty()) {
                    backgroundColor = Color("#f8f9fa")
                    borderRadius = 4.px
                    minHeight = 48.px
                }
            }

            children.forEachIndexed { i, node ->
                renderChip(node, parentPath + i)
            }

            renderAddButton(parentPath)
        }
    }

    // ── Add button + dropdown ─────────────────────────────────────────────

    private fun FlowContent.renderAddButton(parentPath: List<Int>) {
        div {
            css { position = Position.relative; display = Display.inlineBlock }

            ui.mini.basic.circular.icon.button {
                css { marginLeft = 2.px }
                onClick {
                    addMenuPath = if (addMenuPath == parentPath) null else parentPath
                }
                icon.plus()
            }

            if (addMenuPath == parentPath) {
                div {
                    css {
                        position = Position.absolute
                        zIndex = 100
                        top = 28.px
                        left = 0.px
                        backgroundColor = Color.white
                        border = Border(1.px, BorderStyle.solid, Color("#ccc"))
                        borderRadius = 4.px
                        put("box-shadow", "0 2px 8px rgba(0,0,0,0.15)")
                        padding = Padding(4.px)
                        display = Display.flex
                        flexDirection = FlexDirection.column
                        gap = 2.px
                        minWidth = 170.px
                    }
                    addMenuItem("Atom") { addAtomAt(parentPath) }
                    addMenuDivider()
                    addMenuItem("Group  [ ]") { addGroupAt(parentPath) }
                    addMenuItem("Alternation  < >") { addAlternationAt(parentPath) }
                    addMenuDivider()
                    addMenuItem("Rest  ~") { addRestAt(parentPath) }
                }
            }
        }
    }

    private fun FlowContent.addMenuItem(label: String, action: () -> Unit) {
        div {
            css {
                padding = Padding(6.px, 10.px)
                cursor = Cursor.pointer
                borderRadius = 3.px
                fontFamily = "monospace"
                fontSize = 13.px
            }
            onClick { action() }
            +label
        }
    }

    private fun FlowContent.addMenuDivider() {
        div { css { height = 1.px; backgroundColor = Color("#eee"); marginTop = 2.px; marginBottom = 2.px } }
    }

    // ── Chip dispatch ─────────────────────────────────────────────────────

    private fun FlowContent.renderChip(node: MnNode, path: List<Int>) {
        when {
            node is MnNode.Atom && editingPath == path -> renderAtomChipEditing(path)
            node is MnNode.Atom && props.atomTool != null && path.size == 1 -> renderAtomChipWithTool(path, node)
            node is MnNode.Atom -> renderAtomChipPlain(path, node)
            node is MnNode.Group -> renderGroupChip(path, node)
            node is MnNode.Alternation -> renderAlternationChip(path, node)
            node is MnNode.Rest -> renderRestChip(path)
            else -> renderChoiceChip(path, node)
        }
    }

    // ── Atom chip — plain text ────────────────────────────────────────────

    private fun FlowContent.renderAtomChipPlain(path: List<Int>, node: MnNode.Atom) {
        div {
            css { chipBase(); cursor = Cursor.pointer }
            onClick { startInlineEdit(path, node.value) }
            span { css { mono13() }; +(node.value.ifEmpty { "…" }) }
            modsDisplay(node.mods, path)
            chipDeleteButton(path)
        }
    }

    // ── Atom chip — inline editing ────────────────────────────────────────

    private fun FlowContent.renderAtomChipEditing(path: List<Int>) {
        div {
            css { chipBase(); borderColor = Color("#2185d0") }
            input {
                css {
                    fontFamily = "monospace"; fontSize = 13.px
                    border = Border.none; outline = Outline.none
                    minWidth = 40.px; width = LinearDimension.auto; background = "none"
                }
                value = editingText
                autoFocus = true
                onInput { e -> editingText = e.target.asDynamic().value as String }
                onKeyDown { e ->
                    when (e.key) {
                        "Enter" -> commitInlineEdit(path)
                        "Escape" -> cancelInlineEdit()
                    }
                }
                onBlur { commitInlineEdit(path) }
            }
        }
    }

    // ── Atom chip — with sub-tool (top-level only) ────────────────────────

    private fun FlowContent.renderAtomChipWithTool(path: List<Int>, node: MnNode.Atom) {
        val index = path[0]
        val isEmbeddable = props.atomTool is KlangUiToolEmbeddable
        val isExpanded = expandedIndex == index

        div {
            css { chipBase(); if (isExpanded) borderColor = Color("#2185d0") }
            span { css { mono13() }; +(node.value.ifEmpty { "…" }) }
            modsDisplay(node.mods, path)
            ui.mini.basic.given(isExpanded) { active }.button {
                css { marginLeft = 4.px; marginRight = 0.px }
                onClick {
                    if (isEmbeddable) toggleExpandedPanel(index)
                    else openAtomTool(index)
                }
                +(if (isExpanded) "▼" else "edit")
            }
            chipDeleteButton(path)
        }
    }

    // ── Group chip  [ … ] ─────────────────────────────────────────────────

    private fun FlowContent.renderGroupChip(path: List<Int>, node: MnNode.Group) {
        val children = node.layers.firstOrNull() ?: emptyList()
        div {
            css {
                chipBase()
                padding = Padding(4.px, 6.px)
                gap = 4.px
                borderColor = Color("#888")
                flexWrap = FlexWrap.wrap
                alignItems = Align.center
            }
            span { css { mono13(); color = Color("#555"); fontWeight = FontWeight.bold }; +"[" }
            children.forEachIndexed { j, child -> renderChip(child, path + j) }
            renderAddButton(path)
            span { css { mono13(); color = Color("#555"); fontWeight = FontWeight.bold }; +"]" }
            modsDisplay(node.mods, path)
            chipDeleteButton(path)
        }
    }

    // ── Alternation chip  < … > ───────────────────────────────────────────

    private fun FlowContent.renderAlternationChip(path: List<Int>, node: MnNode.Alternation) {
        div {
            css {
                chipBase()
                padding = Padding(4.px, 6.px)
                gap = 4.px
                borderColor = Color("#888")
                borderStyle = BorderStyle.dashed
                flexWrap = FlexWrap.wrap
                alignItems = Align.center
            }
            span { css { mono13(); color = Color("#555"); fontWeight = FontWeight.bold }; +"<" }
            node.items.forEachIndexed { j, child -> renderChip(child, path + j) }
            renderAddButton(path)
            span { css { mono13(); color = Color("#555"); fontWeight = FontWeight.bold }; +">" }
            modsDisplay(node.mods, path)
            chipDeleteButton(path)
        }
    }

    // ── Rest chip  ~ ──────────────────────────────────────────────────────

    private fun FlowContent.renderRestChip(path: List<Int>) {
        div {
            css { chipBase(); backgroundColor = Color("#f0f0f0"); color = Color("#999") }
            span { css { mono13(); fontStyle = FontStyle.italic }; +"~" }
            chipDeleteButton(path)
        }
    }

    // ── Choice placeholder chip ───────────────────────────────────────────

    private fun FlowContent.renderChoiceChip(path: List<Int>, node: MnNode) {
        val label = when (node) {
            is MnNode.Choice -> "a|b"
            else -> "?"
        }
        div {
            css { chipBase(); borderStyle = BorderStyle.dashed; color = Color("#888") }
            span { css { mono13() }; +label }
            chipDeleteButton(path)
        }
    }

    // ── Modifier badges ───────────────────────────────────────────────────

    private fun FlowContent.modsDisplay(mods: MnNode.Mods, path: List<Int>) {
        if (mods.isEmpty) {
            // show a faint "+" for mods
            span {
                css {
                    marginLeft = 3.px
                    fontSize = 10.px
                    color = Color("#ccc")
                    cursor = Cursor.pointer
                    userSelect = UserSelect.none
                    put("line-height", "1")
                }
                onClick { e ->
                    e.stopPropagation()
                    modsEditPath = if (modsEditPath == path) null else path
                }
                +"⚙"
            }
            return
        }
        span {
            css {
                marginLeft = 4.px
                display = Display.inlineFlex
                gap = 2.px
                alignItems = Align.center
                cursor = Cursor.pointer
            }
            onClick { e ->
                e.stopPropagation()
                modsEditPath = if (modsEditPath == path) null else path
            }
            mods.multiplier?.let { modBadge("×${it.fmtMod()}", "#6435C9") }
            mods.divisor?.let { modBadge("÷${it.fmtMod()}", "#6435C9") }
            mods.weight?.let { modBadge("@${it.fmtMod()}", "#2185d0") }
            mods.probability?.let { modBadge("?${it.fmtMod()}", "#F2711C") }
            mods.euclidean?.let { modBadge("(${it.pulses},${it.steps})", "#21BA45") }
        }
    }

    private fun FlowContent.modBadge(text: String, hexColor: String) {
        span {
            css {
                fontSize = 10.px
                fontFamily = "monospace"
                padding = Padding(1.px, 3.px)
                borderRadius = 2.px
                backgroundColor = Color(hexColor).withAlpha(0.12)
                color = Color(hexColor)
            }
            +text
        }
    }

    private fun Double.fmtMod(): String {
        val long = toLong()
        return if (this == long.toDouble()) long.toString() else toString()
    }

    // ── Modifier editing panel ────────────────────────────────────────────

    private fun FlowContent.renderModsPanel(path: List<Int>, node: MnNode) {
        val mods = node.modsOrNull() ?: return

        div {
            css {
                marginTop = 8.px
                padding = Padding(10.px, 12.px)
                border = Border(1.px, BorderStyle.solid, Color("#ccc"))
                borderRadius = 4.px
                backgroundColor = Color("#fafafa")
            }

            div {
                css {
                    display = Display.flex
                    justifyContent = JustifyContent.spaceBetween
                    alignItems = Align.center
                    marginBottom = 8.px
                }
                ui.small.header { css { margin = Margin(0.px) }; +"Modifiers" }
                span {
                    css { cursor = Cursor.pointer; color = Color("#bbb"); fontSize = 18.px; put("line-height", "1") }
                    onClick { modsEditPath = null }
                    +"×"
                }
            }

            ui.form {
                ui.five.stackable.fields {

                    // ×  multiplier
                    modsNumberField("× Speed", mods.multiplier, step = 0.5) { v ->
                        updateAt(path, node.withMod { copy(multiplier = v) })
                    }

                    // ÷  divisor
                    modsNumberField("÷ Slow", mods.divisor, step = 0.5) { v ->
                        updateAt(path, node.withMod { copy(divisor = v) })
                    }

                    // @  weight
                    modsNumberField("@ Weight", mods.weight, step = 0.1) { v ->
                        updateAt(path, node.withMod { copy(weight = v) })
                    }

                    // ?  probability (0–1; 0 means disabled)
                    modsNumberField("? Prob", mods.probability, step = 0.05) { v ->
                        updateAt(path, node.withMod { copy(probability = v?.coerceIn(0.0, 1.0)) })
                    }

                    // Euclidean — (pulses, steps)
                    modsEuclideanField(mods.euclidean, path, node)
                }
            }
        }
    }

    private fun FlowContent.modsNumberField(
        label: String,
        value: Double?,
        step: Double = 1.0,
        onChange: (Double?) -> Unit,
    ) {
        UiInputField(value ?: 0.0, { v ->
            onChange(if (v == 0.0) null else v)
        }) {
            label(label)
            step(step)
        }
    }

    private fun FlowContent.modsEuclideanField(
        euclidean: MnNode.Euclidean?,
        path: List<Int>,
        node: MnNode,
    ) {
        ui.field {
            label { +"Euclidean (p,s)" }
            div {
                css { display = Display.flex; gap = 4.px }
                // Pulses input
                input {
                    css { fontFamily = "monospace"; fontSize = 13.px; width = 48.px; textAlign = TextAlign.center }
                    type = InputType.number
                    placeholder = "p"
                    value = euclidean?.pulses?.toString() ?: ""
                    min = "1"
                    onInput { e ->
                        val p = (e.target.asDynamic().value as String).toIntOrNull() ?: return@onInput
                        val s = euclidean?.steps ?: p
                        val r = euclidean?.rotation ?: 0
                        updateAt(path, node.withMod { copy(euclidean = MnNode.Euclidean(p, s, r)) })
                    }
                }
                span { css { alignSelf = Align.center; color = Color("#aaa") }; +"," }
                // Steps input
                input {
                    css { fontFamily = "monospace"; fontSize = 13.px; width = 48.px; textAlign = TextAlign.center }
                    type = InputType.number
                    placeholder = "s"
                    value = euclidean?.steps?.toString() ?: ""
                    min = "1"
                    onInput { e ->
                        val s = (e.target.asDynamic().value as String).toIntOrNull() ?: return@onInput
                        val p = euclidean?.pulses ?: s
                        val r = euclidean?.rotation ?: 0
                        updateAt(path, node.withMod { copy(euclidean = MnNode.Euclidean(p, s, r)) })
                    }
                }
                // Clear euclidean
                if (euclidean != null) {
                    span {
                        css { alignSelf = Align.center; color = Color("#bbb"); cursor = Cursor.pointer; marginLeft = 4.px }
                        onClick { updateAt(path, node.withMod { copy(euclidean = null) }) }
                        +"×"
                    }
                }
            }
        }
    }

    // ── Expanded inline sub-tool panel ─────────────────────────────────────

    private fun FlowContent.renderExpandedPanel(index: Int, tool: KlangUiToolEmbeddable) {
        val atom = nodes.getOrNull(index) as? MnNode.Atom ?: return
        val subCtx = KlangUiToolContext(
            symbol = props.toolCtx.symbol,
            paramName = props.toolCtx.paramName,
            currentValue = "\"${atom.value}\"",
            onCommit = { result ->
                val newValue = result.trim().removePrefix("\"").removeSuffix("\"")
                updateAt(listOf(index), atom.copy(value = newValue))
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

    private fun CssBuilder.mono13() {
        fontFamily = "monospace"
        fontSize = 13.px
    }

    private fun FlowContent.chipDeleteButton(path: List<Int>) {
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
                deleteAt(path)
            }
            +"×"
        }
    }
}
