package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.strudel.lang.editor.MnNodeOps
import io.peekandpoke.klang.strudel.lang.editor.sourceRange
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.div
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Abstract base component containing all shared state and logic for mini-notation editors.
 *
 * Tree operations use [MnNode.replaceById] / [MnNode.removeById] directly.
 */
abstract class MnPatternEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : Component<P>(ctx) {

    interface BaseProps {
        val toolCtx: KlangUiToolContext
    }

    // ── State ─────────────────────────────────────────────────────────────────

    protected var text by value(initialText())
    protected var cursorOffset by value(0)
    private var lastCommittedText by value(initialText())

    /** Last atom the cursor was over — retained so panels survive button clicks. */
    protected var lastAtom: MnNode.Atom? = null

    /** Last rest selected in the staff — cleared when an atom is selected. */
    protected var lastRest: MnNode.Rest? = null

    // ── Undo / redo ───────────────────────────────────────────────────────────

    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()

    /** Push current [text] onto the undo stack before a programmatic edit. */
    protected fun pushUndo() {
        undoStack.addLast(text)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(text)
        text = undoStack.removeLast()
        cursorOffset = text.length
        lastAtom = null
        triggerRedraw()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(text)
        text = redoStack.removeLast()
        cursorOffset = text.length
        lastAtom = null
        triggerRedraw()
    }

    private val keydownListener: (Event) -> Unit = listener@{ event ->
        val ke = event as? KeyboardEvent ?: return@listener
        when {
            KlangKeyBindings.isUndo(ke) -> {
                ke.preventDefault(); undo()
            }

            KlangKeyBindings.isRedo(ke) -> {
                ke.preventDefault(); redo()
            }
        }
    }

    init {
        lifecycle {
            onMount { document.addEventListener("keydown", keydownListener) }
            onUnmount { document.removeEventListener("keydown", keydownListener) }
        }
    }

    // ── Parse cache ───────────────────────────────────────────────────────────

    private val patternCache = mutableMapOf<String, MnPattern?>()

    protected val pattern: MnPattern?
        get() = patternCache.getOrPut(text) {
            try {
                parseMiniNotationMnPattern(text)
            } catch (_: Exception) {
                null
            }
        }

    protected val parseError: Boolean get() = pattern == null && text.isNotBlank()
    protected val selectedAtom: MnNode.Atom? get() = pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
    protected val isModified: Boolean get() = text != initialText()
    protected val hasUncommittedChanges: Boolean get() = text != lastCommittedText

    // ── Initial value ─────────────────────────────────────────────────────────

    private fun initialText(): String {
        val raw = props.toolCtx.currentValue?.trim() ?: return ""
        return raw.removeSurrounding("\"").removeSurrounding("`")
    }

    // ── Node queries ──────────────────────────────────────────────────────────

    protected fun collectAtoms(p: MnPattern): List<MnNode.Atom> = MnNodeOps.collectAtoms(p)

    protected fun collectStaffItems(p: MnPattern): List<MnNode> = MnNodeOps.collectStaffItems(p)

    protected fun findAtomById(p: MnPattern, id: Int): MnNode.Atom? = MnNodeOps.findAtomById(p, id)

    // ── Core edit operation ───────────────────────────────────────────────────

    /** Replaces the node with [targetId] in the tree, re-renders, and updates cursor/selection. */
    protected fun replaceNode(targetId: Int, replacement: MnNode) {
        val p = pattern ?: return
        pushUndo()
        val newRoot = p.replaceById(targetId, replacement) as? MnPattern ?: return
        text = MnRenderer.render(newRoot)
        // Try to re-find the atom near the cursor position
        lastAtom = lastAtom?.let { a -> pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) } }
    }

    /** Removes the node with [targetId] from the tree. */
    protected fun removeNode(targetId: Int) {
        val p = pattern ?: return
        pushUndo()
        val newRoot = p.removeById(targetId) as? MnPattern ?: return
        text = MnRenderer.render(newRoot)
        cursorOffset = cursorOffset.coerceAtMost(text.length)
        lastAtom = null
    }

    /** Replaces [old] with [new] in the pattern tree (legacy convenience, uses id-based replace). */
    protected fun updateNode(old: MnNode, new: MnNode) {
        val p = pattern ?: return
        pushUndo()
        val newRoot = p.replaceById(old.id, new) as? MnPattern ?: return
        text = MnRenderer.render(newRoot)
        if (new is MnNode.Atom) {
            val newAtom = pattern?.let { MnNodeOps.findAtomAtOffset(it, text, cursorOffset) }
            cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
            lastAtom = newAtom ?: lastAtom
        } else {
            lastAtom = null
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    protected fun onCancel() = props.toolCtx.onCancel()

    protected fun onReset() {
        pushUndo()
        text = initialText()
        cursorOffset = 0
        lastAtom = null
    }

    protected fun onCommit() {
        lastCommittedText = text
        props.toolCtx.onCommit(text.quoteForCommit())
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    protected fun FlowContent.renderBottomBar() {
        div {
            css { display = Display.flex; justifyContent = JustifyContent.flexEnd; gap = 8.px }
            ui.basic.button {
                onClick { onCancel() }
                icon.times()
                +"Cancel"
            }
            ui.basic.givenNot(isModified) { disabled }.button {
                onClick { onReset() }
                icon.undo()
                +"Reset"
            }
            ui.black.givenNot(hasUncommittedChanges) { disabled }.button {
                onClick { if (hasUncommittedChanges) onCommit() }
                icon.check()
                +"Update"
            }
        }
    }
}

// ── Note staff editor base ────────────────────────────────────────────────────

/**
 * Abstract base for note-staff editors.
 *
 * Subclasses provide mappings between atom string values and staff positions:
 * - [atomToStaffPosition]: "c4" → 0, "d4" → 1, …
 * - [staffPositionToAtomValue]: 0 → "c4", 1 → "d4", …
 */
abstract class MnEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : MnPatternEditorBase<P>(ctx) {

    protected abstract fun atomToStaffPosition(value: String): Int?
    protected abstract fun staffPositionToAtomValue(pos: Int): String
    protected open fun keySignatureScaleName(): String? = null
    protected open fun FlowContent.renderExtraControls() {}

    // ── Template render ───────────────────────────────────────────────────────

    override fun VDom.render() {
        val atom = (selectedAtom ?: lastAtom).also {
            if (selectedAtom != null) {
                lastAtom = selectedAtom; lastRest = null
            }
        }

        val rest = if (atom != null) null else lastRest

        ui.segment {
            css {
                minWidth = 60.vw
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            mnPatternTextInput(text, atom, parseError) { newText, cursor ->
                text = newText
                cursorOffset = cursor
                lastAtom = lastAtom?.let { a -> pattern?.let { p -> findAtomById(p, a.id) } }
            }

            renderExtraControls()

            ui.divider {}

            renderStaves()

            ui.divider {}
            if (atom != null) {
                mnModifierPanel(atom, onToggleRest = {
                    updateNode(atom, MnNode.Rest(atom.sourceRange))
                }) { updated -> updateNode(atom, updated) }
            } else if (rest != null) {
                mnModifierPanel(rest, onToggleNote = {
                    updateNode(rest, MnNode.Atom(value = staffPositionToAtomValue(6)))
                    lastRest = null
                }) { updated ->
                    updateNode(rest, updated)
                    lastRest = pattern?.let { p ->
                        MnNodeOps.collectStaffItems(p).filterIsInstance<MnNode.Rest>()
                            .find { it.sourceRange?.first == rest.sourceRange?.first }
                    }
                }
            } else {
                mnModifierPanelDisabled()
            }

            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Staff rendering ───────────────────────────────────────────────────────

    private fun FlowContent.renderStaves() {
        val p = pattern ?: return
        val allItems = MnNodeOps.collectStaffItems(p)
        val lines = text.split('\n')
        var lineStart = 0
        var staffRendered = false

        for (line in lines) {
            val lineEnd = lineStart + line.length

            if (line.isNotBlank()) {
                val lineItems = allItems.filter { node ->
                    node.sourceRange?.first?.let { it in lineStart..lineEnd } == true
                }

                if (lineItems.isNotEmpty()) {
                    if (staffRendered) div { css { height = 8.px } }

                    val linePattern = MnPattern(lineItems)
                    val lineStructuralPattern = MnPattern(
                        p.items.mapNotNull { extractLineSubtree(it, lineStart, lineEnd) }
                    )
                    val lineActiveAtom = selectedAtom?.let { a ->
                        lineItems.filterIsInstance<MnNode.Atom>().find { it.id == a.id }
                    }

                    val lineActiveRest = lastRest?.let { r ->
                        lineItems.filterIsInstance<MnNode.Rest>().find { it.sourceRange == r.sourceRange }
                    }
                    val lineSelection: MnSelection? =
                        lineActiveAtom?.let { MnSelection.Atom(it) }
                            ?: lineActiveRest?.let { MnSelection.Rest(it) }

                    noteStaffSvg(
                        pattern = linePattern,
                        atomToPos = ::atomToStaffPosition,
                        posToValue = ::staffPositionToAtomValue,
                        scaleName = keySignatureScaleName(),
                        structuralPattern = lineStructuralPattern,
                        selection = lineSelection,
                        onAction = { action -> handleStaffAction(action) },
                    )

                    staffRendered = true
                }
            }

            lineStart += line.length + 1
        }
    }

    private fun handleStaffAction(action: NoteStaffEditor.Action) {
        when (action) {
            is NoteStaffEditor.Action.Select -> when (val sel = action.selection) {
                is MnSelection.Atom -> {
                    cursorOffset = sel.node.sourceRange?.first ?: cursorOffset
                    lastAtom = sel.node; lastRest = null
                }

                is MnSelection.Rest -> {
                    cursorOffset = sel.node.sourceRange?.first ?: cursorOffset
                    lastRest = sel.node; lastAtom = null
                }
            }

            is NoteStaffEditor.Action.Remove -> removeNode(action.nodeId)

            is NoteStaffEditor.Action.InsertChild -> {
                val p = pattern ?: return
                val parent = p.findById(action.parentId) ?: return
                val newAtom = MnNode.Atom(value = staffPositionToAtomValue(action.staffPos))
                val newParent = when (parent) {
                    is MnPattern -> parent.insertAt(action.index, newAtom)
                    is MnNode.Group -> parent.insertAt(action.index, newAtom)
                    is MnNode.Alternation -> parent.insertAt(action.index, newAtom)
                    else -> return
                }
                replaceNode(parent.id, newParent)
                cursorOffset = text.length
            }

            is NoteStaffEditor.Action.StackOnto -> {
                val p = pattern ?: return
                val node = p.findById(action.nodeId) ?: return
                val newValue = staffPositionToAtomValue(action.staffPos)
                when (node) {
                    is MnNode.Atom -> {
                        // Wrap in a stack: c4 → [c4,e4]
                        val stack = MnNode.Group(
                            items = listOf(MnNode.Stack(layers = listOf(listOf(node), listOf(MnNode.Atom(value = newValue)))))
                        )
                        replaceNode(node.id, stack)
                    }

                    is MnNode.Rest -> {
                        // Replace rest with the new note
                        replaceNode(node.id, MnNode.Atom(value = newValue))
                    }

                    else -> return
                }
                cursorOffset = text.length
            }

            is NoteStaffEditor.Action.Replace -> updateNode(action.old, action.new)
        }
    }

    private fun extractLineSubtree(node: MnNode, start: Int, end: Int): MnNode? {
        fun inRange() = node.sourceRange?.first?.let { it in start..end } == true
        fun List<MnNode>.filterLine() = mapNotNull { extractLineSubtree(it, start, end) }
        fun List<MnNode>.filterLineOrNull() = filterLine().ifEmpty { null }

        return when (node) {
            is MnNode.Atom, is MnNode.Rest -> if (inRange()) node else null
            is MnNode.Group -> node.items.filterLineOrNull()?.let { node.copy(items = it) }
            is MnNode.Alternation -> node.items.filterLineOrNull()?.let { node.copy(items = it) }
            is MnNode.Stack -> node.layers.map { it.filterLine() }.filter { it.isNotEmpty() }
                .ifEmpty { null }?.let { node.copy(layers = it) }
            is MnNode.Choice -> node.options.filterLineOrNull()?.let { node.copy(options = it) }
            is MnNode.Repeat -> extractLineSubtree(node.node, start, end)?.let { node.copy(node = it) }
            is MnNode.Linebreak -> null
            is MnPattern -> null
        }
    }
}
