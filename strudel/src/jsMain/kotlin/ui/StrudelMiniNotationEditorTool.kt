package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.html.onKeyUp
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
import kotlin.math.pow
import kotlin.math.roundToLong

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
     * Memoised parse results keyed by text — re-parses only on new input.
     * Stable object identity is required so that reference-equality checks in
     * [replaceAtomInNode] (`node === old`) correctly locate the atom to replace.
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
    private fun updateAtom(old: MnNode.Atom, new: MnNode.Atom) {
        val p = pattern ?: return
        text = MnRenderer.render(replaceAtomIn(p, old, new))
        // Try to restore cursor and lastAtom to the updated atom in the new string
        val newAtom = pattern?.let { findAtomByValue(it, new.value) }
        cursorOffset = newAtom?.sourceRange?.first ?: cursorOffset
        lastAtom = newAtom ?: lastAtom
    }

    private fun replaceAtomIn(p: MnPattern, old: MnNode.Atom, new: MnNode.Atom): MnPattern =
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

    private fun findAtomByValue(p: MnPattern, value: String): MnNode.Atom? =
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

    private fun onCancel() = props.toolCtx.onCancel()

    private fun onReset() {
        text = initialText()
        cursorOffset = 0
        lastAtom = null
    }

    private fun onCommit() = props.toolCtx.onCommit("\"$text\"")

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

            renderPatternInput(atom)

            if (atom != null) {
                ui.divider {}
                renderModifierPanel(atom)
                ui.divider {}
                renderAtomPanel(atom)
            }

            // Filler: grows to fill remaining vertical space when no atom panel is shown
            div { css { flexGrow = 1.0 } }

            ui.divider {}
            renderBottomBar()
        }
    }

    // ── Pattern text input ────────────────────────────────────────────────────

    private fun FlowContent.renderPatternInput(atom: MnNode.Atom?) {
        // Compute the character range to highlight (atom value + its modifiers).
        val highlightStart = atom?.sourceRange?.first
        val highlightEnd = if (atom != null && highlightStart != null)
            (highlightStart + MnRenderer.renderNode(atom).length).coerceAtMost(text.length)
        else null

        div {
            // Relative container so the highlight layer and textarea can overlap.
            div {
                css {
                    position = Position.relative
                    backgroundColor = Color.white
                    borderRadius = 4.px
                }

                // ── Highlight layer ─────────────────────────────────────────
                // Transparent text div positioned exactly behind the textarea.
                // The <mark> provides the visible highlight background.
                div {
                    css {
                        position = Position.absolute
                        top = 0.px; left = 0.px; right = 0.px; bottom = 0.px
                        fontFamily = "monospace"
                        fontSize = 15.px
                        padding = Padding(8.px, 10.px)
                        put("white-space", "pre-wrap")
                        put("word-break", "break-word")
                        put("pointer-events", "none")
                        put("box-sizing", "border-box")
                        put("border", "1px solid transparent")
                        color = Color("transparent")
                    }
                    if (highlightStart != null && highlightEnd != null && highlightStart < highlightEnd) {
                        +text.substring(0, highlightStart)
                        mark {
                            css {
                                backgroundColor = Color("rgba(200, 200, 255, 0.45)")
                                color = Color("transparent")
                                borderRadius = 2.px
                            }
                            +text.substring(highlightStart, highlightEnd)
                        }
                        +text.substring(highlightEnd)
                    } else {
                        +text
                    }
                }

                // ── Editable textarea (on top of highlight layer) ───────────
                textArea {
                    placeholder = "e.g. bd sd [hh cp] ~ bd*2"
                    attributes["value"] = text
                    css {
                        position = Position.relative
                        width = 100.pct
                        fontFamily = "monospace"
                        fontSize = 15.px
                        padding = Padding(8.px, 10.px)
                        borderRadius = 4.px
                        put("border", if (parseError) "1px solid #e03131" else "1px solid #ccc")
                        outline = Outline.none
                        put("box-sizing", "border-box")
                        put("resize", "none")
                        put("overflow", "hidden")
                        put("min-height", "38px")
                        backgroundColor = Color("transparent")
                        put("background", "transparent")
                        put("caret-color", "#000")
                    }
                    onInput { e ->
                        val el = e.target?.asDynamic()
                        text = el?.value as? String ?: text
                        cursorOffset = el?.selectionStart as? Int ?: 0
                        // Refresh lastAtom to a valid reference in the new parse tree.
                        // Without this, lastAtom holds a stale reference after typing and
                        // replaceAtomInNode's identity check (===) would always fail.
                        lastAtom = lastAtom?.value?.let { v -> pattern?.let { p -> findAtomByValue(p, v) } }
                        // Auto-resize: collapse to auto first to shrink, then expand to scrollHeight
                        el?.style?.height = "auto"
                        val scrollH = el?.scrollHeight?.toString() ?: "0"
                        el?.style?.height = "${scrollH}px"
                    }
                    onClick { e ->
                        cursorOffset = e.target?.asDynamic()?.selectionStart as? Int ?: 0
                    }
                    onKeyUp { e ->
                        cursorOffset = e.target?.asDynamic()?.selectionStart as? Int ?: 0
                    }
                }
            }

            if (parseError) {
                div {
                    css { color = Color("#e03131"); fontSize = 12.px; marginTop = 4.px }
                    +"Invalid pattern"
                }
            } else if (atom != null) {
                div {
                    css { color = Color("#888"); fontSize = 12.px; marginTop = 4.px }
                    +"Editing: "
                    span { css { fontFamily = "monospace"; color = Color("#333"); fontWeight = FontWeight.bold }; +atom.value }
                }
            } else {
                div {
                    css { color = Color("#999"); fontSize = 12.px; marginTop = 4.px }
                    +"Click inside an atom token to edit its value and modifiers"
                }
            }
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

    // ── Modifier panel ────────────────────────────────────────────────────────

    private fun FlowContent.renderModifierPanel(atom: MnNode.Atom) {
        val mods = atom.mods
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                alignItems = Align.center
                gap = 6.px
            }
            span {
                css { fontSize = 12.px; color = Color("#666"); fontWeight = FontWeight.w600; minWidth = 60.px }
                +"Mods"
            }

            modChip(symbol = "*", tooltip = "Multiplier — play faster", current = mods.multiplier, default = 2.0, step = 0.5) { v ->
                updateAtom(atom, atom.copy(mods = mods.copy(multiplier = v)))
            }
            modChip(symbol = "/", tooltip = "Divisor — play slower", current = mods.divisor, default = 2.0, min = 1.0, step = 1.0) { v ->
                updateAtom(atom, atom.copy(mods = mods.copy(divisor = v)))
            }
            modChip(symbol = "@", tooltip = "Weight — relative time weight", current = mods.weight, default = 2.0, step = 0.5) { v ->
                updateAtom(atom, atom.copy(mods = mods.copy(weight = v)))
            }
            modChip(
                symbol = "?",
                tooltip = "Probability — chance of playing",
                current = mods.probability,
                default = 0.5,
                min = 0.0,
                max = 1.0,
                step = 0.05
            ) { v ->
                updateAtom(atom, atom.copy(mods = mods.copy(probability = v)))
            }
            euclideanChip(atom, mods)
        }
    }

    private fun FlowContent.modChip(
        symbol: String,
        tooltip: String,
        current: Double?,
        default: Double,
        min: Double = 0.0,
        max: Double? = null,
        step: Double,
        onChange: (Double?) -> Unit,
    ) {
        div {
            css {
                display = Display.flex
                alignItems = Align.center
                gap = 4.px
                backgroundColor = Color("#f5f5f5")
                borderRadius = 6.px
                padding = Padding(4.px, 8.px)
            }
            span {
                css { fontFamily = "monospace"; fontWeight = FontWeight.bold; fontSize = 14.px; color = Color("#444") }
                attributes["title"] = tooltip
                +symbol
            }
            if (current != null) {
                val decimals = step.decimalPlaces()
                stepButton("-") {
                    val next = (current - step).roundTo(decimals).let { if (max != null) it.coerceAtMost(max) else it }.coerceAtLeast(min)
                    onChange(if (next <= min) null else next)
                }
                input {
                    type = InputType.number
                    value = current.toFixed(4)
                    attributes["min"] = min.toString()
                    if (max != null) attributes["max"] = max.toString()
                    attributes["step"] = step.toString()
                    css {
                        width = 65.px
                        fontSize = 13.px
                        padding = Padding(2.px, 4.px)
                        borderRadius = 4.px
                        put("border", "1px solid #ccc")
                    }
                    onInput { e ->
                        val v = (e.target?.asDynamic()?.value as? String)?.toDoubleOrNull() ?: return@onInput
                        onChange(if (v <= min) null else v)
                    }
                }
                stepButton("+") {
                    val next = (current + step).roundTo(decimals).let { if (max != null) it.coerceAtMost(max) else it }.coerceAtLeast(min)
                    onChange(next)
                }
                span {
                    css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                    attributes["title"] = "Remove"
                    onClick { onChange(null) }
                    +"×"
                }
            } else {
                span {
                    css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                    attributes["title"] = "Add — $tooltip"
                    onClick { onChange(default) }
                    +"+"
                }
            }
        }
    }

    private fun FlowContent.euclideanChip(atom: MnNode.Atom, mods: MnNode.Mods) {
        val e = mods.euclidean
        div {
            css {
                display = Display.flex
                alignItems = Align.center
                gap = 4.px
                backgroundColor = Color("#f5f5f5")
                borderRadius = 6.px
                padding = Padding(4.px, 8.px)
            }
            span {
                css { fontFamily = "monospace"; fontWeight = FontWeight.bold; fontSize = 14.px; color = Color("#444") }
                attributes["title"] = "Euclidean rhythm (pulses, steps, rotation)"
                +"(,)"
            }
            if (e != null) {
                euclideanInput("pulses", e.pulses, 1) { v ->
                    updateAtom(atom, atom.copy(mods = mods.copy(euclidean = e.copy(pulses = v))))
                }
                span { css { color = Color("#999"); fontSize = 12.px }; +"," }
                euclideanInput("steps", e.steps, 1) { v ->
                    updateAtom(atom, atom.copy(mods = mods.copy(euclidean = e.copy(steps = v))))
                }
                span { css { color = Color("#999"); fontSize = 12.px }; +"," }
                euclideanInput("rotation", e.rotation, 0) { v ->
                    updateAtom(atom, atom.copy(mods = mods.copy(euclidean = e.copy(rotation = v))))
                }
                span {
                    css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                    attributes["title"] = "Remove euclidean"
                    onClick { updateAtom(atom, atom.copy(mods = mods.copy(euclidean = null))) }
                    +"×"
                }
            } else {
                span {
                    css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                    attributes["title"] = "Add euclidean rhythm"
                    onClick { updateAtom(atom, atom.copy(mods = mods.copy(euclidean = MnNode.Euclidean(3, 8)))) }
                    +"+"
                }
            }
        }
    }

    private fun FlowContent.euclideanInput(tooltip: String, value: Int, min: Int, onChange: (Int) -> Unit) {
        stepButton("-") { if (value - 1 >= min) onChange(value - 1) }
        input {
            type = InputType.number
            this.value = value.toString()
            attributes["min"] = min.toString()
            attributes["step"] = "1"
            attributes["title"] = tooltip
            css {
                width = 45.px
                fontSize = 13.px
                padding = Padding(2.px, 4.px)
                borderRadius = 4.px
                put("border", "1px solid #ccc")
            }
            onInput { e ->
                val v = (e.target?.asDynamic()?.value as? String)?.toIntOrNull() ?: return@onInput
                onChange(v)
            }
        }
        stepButton("+") { onChange(value + 1) }
    }

    private fun FlowContent.stepButton(label: String, onClick: () -> Unit) {
        span {
            css {
                cursor = Cursor.pointer
                color = Color("#555")
                fontSize = 14.px
                fontWeight = FontWeight.bold
                padding = Padding(0.px, 3.px)
                userSelect = UserSelect.none
            }
            onClick { onClick() }
            +label
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Double.toFixed(decimals: Int): String {
    val s = roundTo(decimals).toString()
    val dotIdx = s.indexOf('.')
    return if (dotIdx < 0) s else s.trimEnd('0').trimEnd('.')
}

private fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToLong().toDouble() / factor
}

/** Number of significant decimal places in this value (used to match step precision). */
private fun Double.decimalPlaces(): Int {
    val s = toString()
    val dot = s.indexOf('.')
    return if (dot < 0) 0 else s.substring(dot + 1).trimEnd('0').length
}
