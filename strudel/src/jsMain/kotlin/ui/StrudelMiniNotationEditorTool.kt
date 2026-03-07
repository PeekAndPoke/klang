package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.semanticui.icon
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
 * A [KlangUiTool] that edits mini-notation pattern strings.
 *
 * The user edits the raw mini-notation string in a text field.
 * Clicking into an atom token reveals a modifier panel and, when [atomTool] is set,
 * an inline or modal sub-tool for editing the atom's value.
 */
class StrudelMiniNotationEditorTool(
    private val atomTool: KlangUiTool? = null,
) : KlangUiTool {
    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelMiniNotationEditorComp(ctx, atomTool)
    }
}

@Suppress("FunctionName")
private fun Tag.StrudelMiniNotationEditorComp(toolCtx: KlangUiToolContext, atomTool: KlangUiTool?) =
    comp(StrudelMiniNotationEditorComp.Props(toolCtx, atomTool)) { StrudelMiniNotationEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelMiniNotationEditorComp(ctx: Ctx<Props>) : Component<StrudelMiniNotationEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val atomTool: KlangUiTool?)

    // ── State ─────────────────────────────────────────────────────────────────

    private var text by value(initialText())
    private var cursorOffset by value(0)

    /** Last atom the cursor was over — retained so modifier/atom panels survive button clicks. */
    private var lastAtom: MnNode.Atom? = null

    // ── Derived ───────────────────────────────────────────────────────────────

    /**
     * Memoised parse results keyed by text — avoids redundant re-parses.
     * No longer a correctness requirement (atom identity is tracked via [MnNode.Atom.id]);
     * kept as a pure performance optimisation.
     */
    private val patternCache = mutableMapOf<String, MnPattern?>()

    private val pattern: MnPattern?
        get() = patternCache.getOrPut(text) {
            try {
                parseMiniNotationMnPattern(text)
            } catch (_: Exception) {
                null
            }
        }

    private val parseError: Boolean get() = pattern == null && text.isNotBlank()
    private val selectedAtom: MnNode.Atom? get() = pattern?.let { findAtomAt(it, cursorOffset) }
    private val isModified: Boolean get() = text != initialText()

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
     *    This covers `bd*2`, `bd@1.5`, `bd(3,8)` etc. where the cursor is on the modifiers.
     */
    private fun findAtomAt(p: MnPattern, offset: Int): MnNode.Atom? {
        // Pass 1: cursor is directly on the atom value token
        p.items.firstNotNullOfOrNull { findAtomInNode(it, offset) }?.let { return it }

        // Pass 2: cursor is in the modifier tail — find the nearest atom whose value ends
        // before the cursor with no whitespace/structural gap in between
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
    private fun updateAtom(old: MnNode.Atom, new: MnNode.Atom) {
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

    private fun findAtomById(p: MnPattern, id: Int): MnNode.Atom? =
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

    private fun onCancel() = props.toolCtx.onCancel()

    private fun onReset() {
        text = initialText()
        cursorOffset = 0
        lastAtom = null
    }

    private fun onCommit() = props.toolCtx.onCommit(text.quoteForCommit())

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        val atom = (selectedAtom ?: lastAtom).also { if (selectedAtom != null) lastAtom = selectedAtom }

        ui.segment {
            css {
                minWidth = 60.vw
                minHeight = 60.vh
                display = Display.flex
                flexDirection = FlexDirection.column
            }

            ui.small.header { +"Mini Notation" }

            mnPatternTextInput(text, atom, parseError) { newText, cursor ->
                text = newText
                cursorOffset = cursor
                lastAtom = lastAtom?.let { a -> pattern?.let { p -> findAtomById(p, a.id) } }
            }

            if (atom != null) {
                ui.divider {}
                mnModifierPanel(atom) { updated -> updateAtom(atom, updated) }
                ui.divider {}
                renderAtomPanel(atom)
            }

            // Filler: grows to fill remaining vertical space when no atom panel is shown
            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Atom value panel ──────────────────────────────────────────────────────

    private fun FlowContent.renderAtomPanel(atom: MnNode.Atom) {
        val atomTool = props.atomTool
        when {
            atomTool == null -> renderAtomValueInput(atom)
            atomTool is KlangUiToolEmbeddable -> renderEmbeddedAtomTool(atom, atomTool)
            else -> renderAtomModalButton(atom, atomTool)
        }
    }

    private fun FlowContent.renderAtomValueInput(atom: MnNode.Atom) {
        div {
            css { display = Display.flex; alignItems = Align.center; gap = 8.px }
            span {
                css { fontSize = 12.px; color = Color("#666"); fontWeight = FontWeight.w600; minWidth = 60.px }
                +"Value"
            }
            input {
                type = InputType.text
                value = atom.value
                css {
                    fontFamily = "monospace"
                    fontSize = 14.px
                    padding = Padding(4.px, 8.px)
                    borderRadius = 4.px
                    put("border", "1px solid #ccc")
                }
                onInput { e ->
                    val v = e.target?.asDynamic()?.value as? String ?: return@onInput
                    updateAtom(atom, atom.copy(value = v))
                }
            }
        }
    }

    private fun atomSubCtx(atom: MnNode.Atom, onCancel: () -> Unit, onCommit: (String) -> Unit) =
        props.toolCtx.copy(
            currentValue = "\"${atom.value}\"",
            onCancel = onCancel,
            onCommit = onCommit,
        )

    private fun FlowContent.renderEmbeddedAtomTool(atom: MnNode.Atom, atomTool: KlangUiToolEmbeddable) {
        val subCtx = atomSubCtx(atom, onCancel = {}, onCommit = { newVal ->
            updateAtom(atom, atom.copy(value = newVal.trim().removeSurrounding("\"")))
        })
        with(atomTool) { renderEmbedded(subCtx) }
    }

    private fun FlowContent.renderAtomModalButton(atom: MnNode.Atom, atomTool: KlangUiTool) {
        ui.basic.small.button {
            onClick {
                modals.show { handle ->
                    CodeToolModal(handle) {
                        val subCtx = atomSubCtx(
                            atom,
                            onCancel = { handle.close() },
                            onCommit = { newVal ->
                                updateAtom(atom, atom.copy(value = newVal.trim().removeSurrounding("\"")))
                                handle.close()
                            },
                        )
                        with(atomTool) { render(subCtx) }
                    }
                }
            }
            icon.edit()
            +"Edit '${atom.value}'…"
        }
    }

    // ── Bottom bar ────────────────────────────────────────────────────────────

    private fun FlowContent.renderBottomBar() {
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

