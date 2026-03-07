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
abstract class MnEditorBase<P : MnEditorBase.BaseProps>(ctx: Ctx<P>) : Component<P>(ctx) {

    interface BaseProps {
        val toolCtx: KlangUiToolContext
    }

    // ── State ─────────────────────────────────────────────────────────────────

    protected var text by value(initialText())
    protected var cursorOffset by value(0)

    /** Last atom the cursor was over — retained so modifier panel survives button clicks. */
    protected var lastAtom: MnNode.Atom? = null

    // ── Parse cache ───────────────────────────────────────────────────────────

    /**
     * Memoised parse results keyed by text — re-parses only on new input.
     * Stable object identity is required so that reference-equality checks in
     * [replaceAtomInNode] (`node === old`) correctly locate the atom to replace.
     */
    protected val patternCache = mutableMapOf<String, MnPattern?>()

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

    // ── Abstract: position mapping ────────────────────────────────────────────

    /** Maps a raw atom value to a staff position (C4 = 0, D4 = 1, …), or null if not representable. */
    protected abstract fun atomToStaffPosition(value: String): Int?

    /** Maps a staff position back to the atom value string. */
    protected abstract fun staffPositionToAtomValue(pos: Int): String

    // ── Optional hooks ────────────────────────────────────────────────────────

    /** Override to render additional controls between the text input and the staff. */
    protected open fun FlowContent.renderExtraControls() {}

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

    protected fun collectAtoms(p: MnPattern): List<MnNode.Atom> = buildList {
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
    }

    // ── Atom update ───────────────────────────────────────────────────────────

    /** Replaces [old] with [new] in the pattern tree and re-renders the whole string. */
    protected fun updateAtom(old: MnNode.Atom, new: MnNode.Atom) {
        val p = pattern ?: return
        text = MnRenderer.render(replaceAtomIn(p, old, new))
        val newAtom = pattern?.let { findAtomByValue(it, new.value) }
        cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
        lastAtom = newAtom ?: lastAtom
    }

    protected fun replaceAtomIn(p: MnPattern, old: MnNode.Atom, new: MnNode.Atom): MnPattern =
        MnPattern(p.items.map { replaceAtomInNode(it, old, new) })

    private fun replaceAtomInNode(node: MnNode, old: MnNode.Atom, new: MnNode.Atom): MnNode = when (node) {
        is MnNode.Atom -> if (node === old) new else node
        is MnNode.Group -> node.copy(items = node.items.map { replaceAtomInNode(it, old, new) })
        is MnNode.Alternation -> node.copy(items = node.items.map { replaceAtomInNode(it, old, new) })
        is MnNode.Stack -> node.copy(layers = node.layers.map { l -> l.map { replaceAtomInNode(it, old, new) } })
        is MnNode.Choice -> node.copy(options = node.options.map { replaceAtomInNode(it, old, new) })
        is MnNode.Repeat -> node.copy(node = replaceAtomInNode(node.node, old, new))
        is MnNode.Rest -> node
    }

    protected fun findAtomByValue(p: MnPattern, value: String): MnNode.Atom? =
        p.items.firstNotNullOfOrNull { findAtomByValueInNode(it, value) }

    private fun findAtomByValueInNode(node: MnNode, value: String): MnNode.Atom? = when (node) {
        is MnNode.Atom -> node.takeIf { it.value == value }
        is MnNode.Group -> node.items.firstNotNullOfOrNull { findAtomByValueInNode(it, value) }
        is MnNode.Alternation -> node.items.firstNotNullOfOrNull { findAtomByValueInNode(it, value) }
        is MnNode.Stack -> node.layers.flatten().firstNotNullOfOrNull { findAtomByValueInNode(it, value) }
        is MnNode.Choice -> node.options.firstNotNullOfOrNull { findAtomByValueInNode(it, value) }
        is MnNode.Repeat -> findAtomByValueInNode(node.node, value)
        is MnNode.Rest -> null
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    protected fun onCancel() = props.toolCtx.onCancel()

    protected fun onReset() {
        text = initialText()
        cursorOffset = 0
        lastAtom = null
    }

    protected fun onCommit() = props.toolCtx.onCommit(text.quoteForCommit())

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
                lastAtom = lastAtom?.value?.let { v -> pattern?.let { p -> findAtomByValue(p, v) } }
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
     * Renders one [noteStaffSvg] per line of [text].
     *
     * Each line is parsed independently so notes stay in their own staff row.
     * The active atom for each line is found by mapping [cursorOffset] to a
     * line-local character offset.
     */
    private fun FlowContent.renderStaves() {
        val lines = text.split('\n')
        var lineStart = 0
        var staffRendered = false

        for ((lineIdx, line) in lines.withIndex()) {
            if (line.isBlank()) {
                lineStart += line.length + 1
                continue
            }

            if (staffRendered) {
                div { css { height = 8.px } }
            }

            // Parse the line independently, reusing the shared cache
            val linePattern = patternCache.getOrPut(line) {
                try {
                    parseMiniNotationMnPattern(line)
                } catch (_: Exception) {
                    null
                }
            }

            // Map cursor to this line's local offset (only if cursor is within this line)
            val lineEnd = lineStart + line.length
            val lineActiveAtom = if (cursorOffset in lineStart..lineEnd) {
                linePattern?.let { findAtomAt(it, cursorOffset - lineStart) }
            } else null

            noteStaffSvg(
                pattern = linePattern,
                activeAtom = lineActiveAtom,
                atomToPos = ::atomToStaffPosition,
                posToValue = ::staffPositionToAtomValue,
            ) { old, new ->
                val updatedLine = linePattern?.let {
                    MnRenderer.render(replaceAtomIn(it, old, new))
                } ?: line
                val newLines = lines.toMutableList()
                newLines[lineIdx] = updatedLine
                text = newLines.joinToString("\n")
                lastAtom = lastAtom?.value?.let { v -> pattern?.let { p -> findAtomByValue(p, v) } }
            }

            staffRendered = true
            lineStart += line.length + 1 // +1 for the '\n'
        }
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
            ui.black.button {
                onClick { onCommit() }
                icon.check()
                +"Update"
            }
        }
    }
}
