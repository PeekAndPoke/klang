package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnPattern
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import io.peekandpoke.klang.strudel.lang.parser.parseMiniNotationMnPattern
import io.peekandpoke.klang.ui.KlangUiToolContext
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.div

/**
 * Abstract base component containing all shared state and logic for mini-notation editors.
 *
 * Subclasses that also need a note staff should extend [MnEditorBase] instead.
 */
abstract class MnPatternEditorBase<P : MnPatternEditorBase.BaseProps>(ctx: Ctx<P>) : Component<P>(ctx) {

    interface BaseProps {
        val toolCtx: KlangUiToolContext
    }

    // ── State ─────────────────────────────────────────────────────────────────

    protected var text by value(initialText())
    protected var cursorOffset by value(0)

    /** Last atom the cursor was over — retained so panels survive button clicks. */
    protected var lastAtom: MnNode.Atom? = null

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
    protected val selectedAtom: MnNode.Atom? get() = pattern?.let { findAtomAt(it, cursorOffset) }
    protected val isModified: Boolean get() = text != initialText()

    // ── Initial value ─────────────────────────────────────────────────────────

    private fun initialText(): String =
        props.toolCtx.currentValue?.trim()?.removeSurrounding("\"") ?: ""

    // ── Atom finding ──────────────────────────────────────────────────────────

    /**
     * Returns the atom at [offset], including the modifier tail after the value token.
     *
     * Two passes:
     * 1. Exact: offset is within the atom's sourceRange (the value token itself).
     * 2. Modifier tail: cursor is past the value token but there is no whitespace or
     *    structural character (`[]<>,`) between the atom end and [offset].
     */
    protected fun findAtomAt(p: MnPattern, offset: Int): MnNode.Atom? {
        p.items.firstNotNullOfOrNull { findAtomInNode(it, offset) }?.let { return it }

        val nearest = collectAtoms(p)
            .filter { it.sourceRange != null && it.sourceRange.last < offset }
            .maxByOrNull { it.sourceRange!!.last }
            ?: return null

        val atomEnd = nearest.sourceRange!!.last + 1
        val between = text.substring(atomEnd.coerceAtMost(text.length), offset.coerceAtMost(text.length))
        return if (between.none { it.isWhitespace() || it in "[]<>," }) nearest else null
    }

    private fun collectAtoms(p: MnPattern): List<MnNode.Atom> = buildList {
        p.items.forEach { collectAtomsInNode(it, this) }
    }

    private fun collectAtomsInNode(node: MnNode, list: MutableList<MnNode.Atom>) {
        when (node) {
            is MnNode.Atom -> if (node.sourceRange != null) list.add(node)
            is MnNode.Group -> node.items.forEach { collectAtomsInNode(it, list) }
            is MnNode.Alternation -> node.items.forEach { collectAtomsInNode(it, list) }
            is MnNode.Stack -> node.layers.flatten().forEach { collectAtomsInNode(it, list) }
            is MnNode.Choice -> node.options.forEach { collectAtomsInNode(it, list) }
            is MnNode.Repeat -> collectAtomsInNode(node.node, list)
            is MnNode.Rest -> {}
            is MnNode.Linebreak -> {}
        }
    }

    private fun findAtomInNode(node: MnNode, offset: Int): MnNode.Atom? = when (node) {
        is MnNode.Atom -> node.sourceRange?.takeIf { offset in it }?.let { node }
        is MnNode.Group -> node.items.firstNotNullOfOrNull { findAtomInNode(it, offset) }
        is MnNode.Alternation -> node.items.firstNotNullOfOrNull { findAtomInNode(it, offset) }
        is MnNode.Stack -> node.layers.flatten().firstNotNullOfOrNull { findAtomInNode(it, offset) }
        is MnNode.Choice -> node.options.firstNotNullOfOrNull { findAtomInNode(it, offset) }
        is MnNode.Repeat -> findAtomInNode(node.node, offset)
        is MnNode.Rest -> null
        is MnNode.Linebreak -> null
    }

    // ── Atom update ───────────────────────────────────────────────────────────

    /** Replaces [old] with [new] in the pattern tree and re-renders the whole string. */
    protected fun updateAtom(old: MnNode.Atom, new: MnNode.Atom) {
        val p = pattern ?: return
        text = MnRenderer.render(replaceAtomIn(p, old, new))
        val newAtom = pattern?.let { findAtomById(it, old.id) }
        cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
        lastAtom = newAtom ?: lastAtom
    }

    private fun replaceAtomIn(p: MnPattern, old: MnNode.Atom, new: MnNode.Atom): MnPattern =
        MnPattern(p.items.map { replaceAtomInNode(it, old, new) })

    private fun replaceAtomInNode(node: MnNode, old: MnNode.Atom, new: MnNode.Atom): MnNode = when (node) {
        is MnNode.Atom -> if (node.id == old.id) new else node
        is MnNode.Group -> node.copy(items = node.items.map { replaceAtomInNode(it, old, new) })
        is MnNode.Alternation -> node.copy(items = node.items.map { replaceAtomInNode(it, old, new) })
        is MnNode.Stack -> node.copy(layers = node.layers.map { l -> l.map { replaceAtomInNode(it, old, new) } })
        is MnNode.Choice -> node.copy(options = node.options.map { replaceAtomInNode(it, old, new) })
        is MnNode.Repeat -> node.copy(node = replaceAtomInNode(node.node, old, new))
        is MnNode.Rest -> node
        is MnNode.Linebreak -> node
    }

    protected fun findAtomById(p: MnPattern, id: Int): MnNode.Atom? =
        if (id < 0) null else p.items.firstNotNullOfOrNull { findAtomByIdInNode(it, id) }

    private fun findAtomByIdInNode(node: MnNode, id: Int): MnNode.Atom? = when (node) {
        is MnNode.Atom -> node.takeIf { it.id == id }
        is MnNode.Group -> node.items.firstNotNullOfOrNull { findAtomByIdInNode(it, id) }
        is MnNode.Alternation -> node.items.firstNotNullOfOrNull { findAtomByIdInNode(it, id) }
        is MnNode.Stack -> node.layers.flatten().firstNotNullOfOrNull { findAtomByIdInNode(it, id) }
        is MnNode.Choice -> node.options.firstNotNullOfOrNull { findAtomByIdInNode(it, id) }
        is MnNode.Repeat -> findAtomByIdInNode(node.node, id)
        is MnNode.Rest -> null
        is MnNode.Linebreak -> null
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    protected fun onCancel() = props.toolCtx.onCancel()

    protected fun onReset() {
        text = initialText()
        cursorOffset = 0
        lastAtom = null
    }

    protected fun onCommit() = props.toolCtx.onCommit(text.quoteForCommit())

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
            ui.black.button {
                onClick { onCommit() }
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

    /** Override to render additional controls between the text input and the staff. */
    protected open fun FlowContent.renderExtraControls() {}

    // ── Template render ───────────────────────────────────────────────────────

    override fun VDom.render() {
        val atom = (selectedAtom ?: lastAtom).also { if (selectedAtom != null) lastAtom = selectedAtom }

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

            if (atom != null) {
                ui.divider {}
                mnModifierPanel(atom) { updated -> updateAtom(atom, updated) }
            }

            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Staff rendering ───────────────────────────────────────────────────────

    /**
     * Renders one [NoteStaffComp] per line.
     *
     * The full pattern (which contains [MnNode.Linebreak] nodes) is split on those
     * linebreaks. Each non-empty segment is rendered as its own staff row.
     */
    private fun FlowContent.renderStaves() {
        val p = pattern ?: return
        var staffRendered = false

        for (linePattern in p.splitOnLinebreaks()) {
            if (linePattern.items.isEmpty()) continue

            if (staffRendered) {
                div { css { height = 8.px } }
            }

            val lineActiveAtom = selectedAtom?.let { a -> findAtomById(linePattern, a.id) }

            noteStaffSvg(
                pattern = linePattern,
                activeAtom = lineActiveAtom,
                atomToPos = ::atomToStaffPosition,
                posToValue = ::staffPositionToAtomValue,
            ) { old, new -> updateAtom(old, new) }

            staffRendered = true
        }
    }
}
