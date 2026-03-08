package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.strudel.lang.editor.MnNodeOps
import io.peekandpoke.klang.strudel.lang.editor.MnPatternTextEditor
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
 * Tree-walking operations (collect, find, replace) are delegated to [MnNodeOps] (commonMain).
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

    /**
     * Memoised parse results keyed by text — avoids redundant re-parses.
     * No longer a correctness requirement (atom identity is tracked via [MnNode.Atom.id]);
     * kept as a pure performance optimisation.
     */
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

    // ── Node queries (delegate to MnNodeOps) ─────────────────────────────────

    protected fun collectAtoms(p: MnPattern): List<MnNode.Atom> = MnNodeOps.collectAtoms(p)

    protected fun collectStaffItems(p: MnPattern): List<MnNode> = MnNodeOps.collectStaffItems(p)

    protected fun findAtomById(p: MnPattern, id: Int): MnNode.Atom? = MnNodeOps.findAtomById(p, id)

    // ── Atom update ───────────────────────────────────────────────────────────

    /** Replaces [old] with [new] in the pattern tree and re-renders the whole string. */
    protected fun updateAtom(old: MnNode.Atom, new: MnNode.Atom) {
        val p = pattern ?: return
        pushUndo()
        text = MnRenderer.render(MnNodeOps.replaceNode(p, old, new))
        val newAtom = pattern?.let { MnNodeOps.findAtomById(it, old.id) }
        cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
        lastAtom = newAtom ?: lastAtom
    }

    /** Replaces [old] (Atom or Rest) with [new] in the pattern tree and re-renders. */
    protected fun updateNode(old: MnNode, new: MnNode) {
        val p = pattern ?: return
        pushUndo()
        text = MnRenderer.render(MnNodeOps.replaceNode(p, old, new))
        if (new is MnNode.Atom) {
            val newAtom = pattern?.let { MnNodeOps.findAtomById(it, (old as? MnNode.Atom)?.id ?: -1) }
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
 * Abstract base component for note-staff editors.
 *
 * Subclasses provide the two direction mappings between atom string values and staff positions:
 * - [atomToStaffPosition]: given a raw atom value (e.g. "c4" or "0"), return a staff position
 *   (integer, C4 = 0, D4 = 1, …) or null if the value cannot be rendered on a staff.
 * - [staffPositionToAtomValue]: given a staff position, return the string value to write into
 *   the pattern (e.g. "e4" or "2").
 *
 * The shared render template shows: text input → extra controls → note staff → modifier panel → bottom bar.
 * Override [renderExtraControls] to inject anything between the text input and the staff.
 */
abstract class MnEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : MnPatternEditorBase<P>(ctx) {

    /** Maps a raw atom value to a staff position (C4 = 0, D4 = 1, …), or null if not representable. */
    protected abstract fun atomToStaffPosition(value: String): Int?

    /** Maps a staff position back to the atom value string. */
    protected abstract fun staffPositionToAtomValue(pos: Int): String

    /** Override to provide the scale name for the key signature drawn on the staff. */
    protected open fun keySignatureScaleName(): String? = null

    /** Override to render additional controls between the text input and the staff. */
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
                }) { updated -> updateAtom(atom, updated) }
            } else if (rest != null) {
                mnModifierPanel(rest, onToggleNote = {
                    updateNode(rest, MnNode.Atom(value = staffPositionToAtomValue(6)))
                    lastRest = null
                }) { updated ->
                    updateNode(rest, updated)
                    // Re-find the rest at the same source position after re-render
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

    /**
     * Renders one [NoteStaffComp] per non-empty line of [text].
     *
     * Uses atom [MnNode.Atom.sourceRange] positions to assign each atom to its
     * visual line — so linebreaks inside groups or alternations (`<a\nb>`) split
     * the display correctly without requiring top-level [MnNode.Linebreak] nodes.
     */
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
                    // Preserve tree structure for bracket rendering by filtering top-level nodes
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
                        onAction = { action ->
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

                                is NoteStaffEditor.Action.Remove -> removeNode(action.node)
                                is NoteStaffEditor.Action.InsertBetween -> insertBetween(
                                    action.leftNode,
                                    action.rightNode,
                                    action.staffPos,
                                    action.skipOpeningBrackets,
                                    action.exitBrackets
                                )
                                is NoteStaffEditor.Action.InsertAt -> insertAt(action.existingNode, action.staffPos)
                                is NoteStaffEditor.Action.Replace -> updateNode(action.old, action.new)
                            }
                        },
                    )

                    staffRendered = true
                }
            }

            lineStart += line.length + 1 // +1 for the '\n'
        }
    }

    private fun removeNode(node: MnNode) {
        pushUndo()
        val result = MnPatternTextEditor(text, ::staffPositionToAtomValue).removeNode(node)
        text = result.text
        val range = node.sourceRange
        cursorOffset = (range?.first ?: text.length).coerceAtMost(text.length)
        lastAtom = null
    }

    private fun insertBetween(leftNode: MnNode?, rightNode: MnNode?, staffPos: Int, skipOpeningBrackets: Int = 0, exitBrackets: Int = 0) {
        pushUndo()
        val result = MnPatternTextEditor(text, ::staffPositionToAtomValue).insertBetween(
            leftNode,
            rightNode,
            staffPos,
            skipOpeningBrackets,
            exitBrackets
        )
        text = result.text
        cursorOffset = text.length
    }

    private fun insertAt(existingNode: MnNode, staffPos: Int) {
        pushUndo()
        val result = MnPatternTextEditor(text, ::staffPositionToAtomValue).insertAt(existingNode, staffPos)
        text = result.text
        cursorOffset = text.length
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
        }
    }
}
