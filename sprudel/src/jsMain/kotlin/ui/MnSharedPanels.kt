package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.lang.parser.MnNode
import io.peekandpoke.klang.sprudel.lang.parser.MnRenderer
import io.peekandpoke.klang.ui.feel.KlangLookAndFeel
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onInput
import io.peekandpoke.ultra.html.onKeyUp
import kotlinx.css.*
import kotlinx.html.*

// ── Pattern text input ────────────────────────────────────────────────────────

/**
 * Renders the mini-notation text input with an optional atom highlight overlay.
 *
 * @param text Current pattern text.
 * @param atom Currently active atom (determines highlight range).
 * @param parseError Whether the current text has a parse error.
 * @param onChange Called when the text or cursor position changes.
 */
fun FlowContent.mnPatternTextInput(
    laf: KlangLookAndFeel,
    text: String,
    atom: MnNode.Atom?,
    parseError: Boolean,
    highlightedRanges: Set<IntRange> = emptySet(),
    onChange: (newText: String, cursor: Int) -> Unit,
) {
    // Selection highlight (blue)
    val selStart = atom?.sourceRange?.first
    val selEnd = if (atom != null && selStart != null) {
        (selStart + MnRenderer.renderNode(atom).length).coerceAtMost(text.length)
    } else {
        null
    }

    // Build sorted, non-overlapping highlight spans combining selection + playback
    data class Span(val start: Int, val end: Int, val type: String) // "sel" or "play"

    val spans = mutableListOf<Span>()
    if (selStart != null && selEnd != null && selStart < selEnd) {
        spans.add(Span(selStart, selEnd, "sel"))
    }
    for (range in highlightedRanges) {
        val s = range.first.coerceAtLeast(0)
        val e = (range.last + 1).coerceAtMost(text.length)
        // Skip playback highlights that overlap with the selection
        if (s < e && (selStart == null || selEnd == null || e <= selStart || s >= selEnd)) {
            spans.add(Span(s, e, "play"))
        }
    }
    spans.sortBy { it.start }

    div {
        div(classes = "${laf.styles.darken20()} ${laf.styles.mnPatternInput()}") {

            // Highlight layer behind the textarea
            div(classes = laf.styles.mnPatternInputOverlay()) {
                if (spans.isNotEmpty()) {
                    var cursor = 0
                    for (span in spans) {
                        if (span.start > cursor) +text.substring(cursor, span.start)
                        mark {
                            css {
                                val col = if (span.type == "play") Color(laf.gold) else Color("#CCF")
                                val alpha = if (span.type == "play") 0.4 else 0.5
                                backgroundColor = col.withAlpha(alpha)
                                color = Color("transparent")
                                borderRadius = 2.px
                                put("box-shadow", "0 0 0 1px ${col.withAlpha(alpha + 0.3)}")
                            }
                            +text.substring(span.start, span.end)
                        }
                        cursor = span.end
                    }
                    if (cursor < text.length) +text.substring(cursor)
                } else {
                    +text
                }
            }

            // Editable textarea on top of highlight layer
            val textareaClasses = buildString {
                append(laf.styles.mnPatternInputTextarea())
                append(" ${laf.styles.mnPatternInputTextareaFocus()}")
                if (parseError) append(" ${laf.styles.mnPatternInputTextareaError()}")
            }
            textArea(classes = textareaClasses) {
                placeholder = "e.g. bd sd [hh cp] ~ bd*2"
                attributes["value"] = text
                attributes["spellcheck"] = "off"
                attributes["autocomplete"] = "off"
                attributes["autocorrect"] = "off"
                attributes["autocapitalize"] = "off"
                rows = (text.count { it == '\n' } + 1).toString()
                onInput { e ->
                    val el = e.target?.asDynamic()
                    val newText = el?.value as? String ?: text
                    val cursor = el?.selectionStart as? Int ?: 0
                    // Auto-resize: collapse to auto first to shrink, then expand to scrollHeight
                    el?.style?.height = "auto"
                    val scrollH = el?.scrollHeight?.toString() ?: "0"
                    el?.style?.height = "${scrollH}px"
                    onChange(newText, cursor)
                }
                onClick { e ->
                    val cursor = e.target?.asDynamic()?.selectionStart as? Int ?: 0
                    onChange(text, cursor)
                }
                onKeyUp { e ->
                    val cursor = e.target?.asDynamic()?.selectionStart as? Int ?: 0
                    onChange(text, cursor)
                }
                // Sync horizontal scroll of the highlight overlay with the textarea
                mnOnScroll { e ->
                    val scrollLeft = e.target?.asDynamic()?.scrollLeft as? Double ?: 0.0
                    val overlay = (e.target as? org.w3c.dom.Element)?.previousElementSibling
                    if (overlay != null) overlay.asDynamic().scrollLeft = scrollLeft
                }
            }
        }

        if (parseError) {
            div {
                css { color = Color(laf.critical); fontSize = 12.px; marginTop = 4.px }
                +"Invalid pattern"
            }
        } else if (atom != null) {
            div {
                css { color = Color(laf.textTertiary); fontSize = 12.px; marginTop = 4.px }
                +"Editing: "
                span {
                    css { fontFamily = "monospace"; color = Color(laf.gold); fontWeight = FontWeight.bold }
                    +atom.value
                }
            }
        } else {
            div {
                css { color = Color(laf.textTertiary); fontSize = 12.px; marginTop = 4.px }
                +"Click inside an atom token to edit its value and modifiers"
            }
        }
    }
}

// ── Modifier panel ────────────────────────────────────────────────────────────

/**
 * Renders the modifier chip row for an atom (multiplier, divisor, weight, probability, euclidean).
 *
 * @param atom The atom whose modifiers are shown.
 * @param disabled When true, all inputs are visually dimmed and non-interactive.
 * @param onAtomChange Called when the atom (with updated mods) should replace the original.
 */
fun FlowContent.mnModifierPanel(
    laf: KlangLookAndFeel,
    atom: MnNode.Atom,
    disabled: Boolean = false,
    onToggleRest: (() -> Unit)? = null,
    repeatCount: Int? = null,
    onRepeatChange: ((Int?) -> Unit)? = null,
    onAtomChange: (MnNode.Atom) -> Unit,
) {
    val mods = atom.mods
    div {
        css {
            display = Display.flex
            flexWrap = FlexWrap.wrap
            alignItems = Align.center
            gap = 6.px
            if (disabled) {
                opacity = 0.45
                pointerEvents = PointerEvents.none
            }
        }
        span(classes = laf.styles.mnChipLabel()) {
            +"Mods"
        }

        if (onToggleRest != null) {
            span(classes = laf.styles.mnChipToggle()) {
                attributes["title"] = "Convert to rest"
                onClick { onToggleRest() }
                +"~ rest"
            }
        }

        if (onRepeatChange != null) {
            mnRepeatChip(laf, repeatCount, onRepeatChange)
        }
        mnModChip(laf, symbol = "*", tooltip = "Multiplier — play faster", current = mods.multiplier, default = 2.0, step = 0.5) { v ->
            onAtomChange(atom.copy(mods = mods.copy(multiplier = v)))
        }
        mnModChip(laf, symbol = "/", tooltip = "Divisor — play slower", current = mods.divisor, default = 2.0, min = 1.0, step = 1.0) { v ->
            onAtomChange(atom.copy(mods = mods.copy(divisor = v)))
        }
        mnModChip(laf, symbol = "@", tooltip = "Weight — relative time weight", current = mods.weight, default = 2.0, step = 0.5) { v ->
            onAtomChange(atom.copy(mods = mods.copy(weight = v)))
        }
        mnModChip(
            laf,
            symbol = "?",
            tooltip = "Probability — chance of playing",
            current = mods.probability,
            default = 0.5,
            min = 0.0,
            max = 1.0,
            step = 0.05
        ) { v ->
            onAtomChange(atom.copy(mods = mods.copy(probability = v)))
        }
        mnEuclideanChip(laf, atom, mods, onAtomChange)
    }
}

/**
 * Renders a disabled placeholder modifier panel when no node is selected.
 * Shows the same chip layout but dimmed and non-interactive.
 */
fun FlowContent.mnModifierPanelDisabled(laf: KlangLookAndFeel) {
    val emptyAtom = MnNode.Atom(value = "")
    mnModifierPanel(laf, atom = emptyAtom, disabled = true) {}
}

/**
 * Renders the modifier chip row for a rest (multiplier, divisor, weight, probability, euclidean)
 * plus a button to convert it back to a note.
 *
 * @param rest The rest whose modifiers are shown.
 * @param onToggleNote Called when the rest should be converted to a note. Null to hide the button.
 * @param onRestChange Called when the rest (with updated mods) should replace the original.
 */
fun FlowContent.mnModifierPanel(
    laf: KlangLookAndFeel,
    rest: MnNode.Rest,
    onToggleNote: (() -> Unit)? = null,
    onRestChange: (MnNode.Rest) -> Unit,
) {
    val mods = rest.mods
    div {
        css {
            display = Display.flex
            flexWrap = FlexWrap.wrap
            alignItems = Align.center
            gap = 6.px
        }
        span(classes = laf.styles.mnChipLabel()) {
            +"Mods"
        }

        if (onToggleNote != null) {
            span(classes = laf.styles.mnChipToggle()) {
                attributes["title"] = "Convert to note"
                onClick { onToggleNote() }
                +"♩ note"
            }
        }

        mnModChip(laf, symbol = "*", tooltip = "Multiplier — play faster", current = mods.multiplier, default = 2.0, step = 0.5) { v ->
            onRestChange(rest.copy(mods = mods.copy(multiplier = v)))
        }
        mnModChip(laf, symbol = "/", tooltip = "Divisor — play slower", current = mods.divisor, default = 2.0, min = 1.0, step = 1.0) { v ->
            onRestChange(rest.copy(mods = mods.copy(divisor = v)))
        }
        mnModChip(laf, symbol = "@", tooltip = "Weight — relative time weight", current = mods.weight, default = 2.0, step = 0.5) { v ->
            onRestChange(rest.copy(mods = mods.copy(weight = v)))
        }
        mnModChip(
            laf,
            symbol = "?",
            tooltip = "Probability — chance of playing",
            current = mods.probability,
            default = 0.5,
            min = 0.0,
            max = 1.0,
            step = 0.05
        ) { v ->
            onRestChange(rest.copy(mods = mods.copy(probability = v)))
        }
        mnEuclideanChipForRest(laf, rest, mods, onRestChange)
    }
}

// ── Private chip helpers ──────────────────────────────────────────────────────

private fun FlowContent.mnRepeatChip(laf: KlangLookAndFeel, current: Int?, onChange: (Int?) -> Unit) {
    div(classes = laf.styles.mnChip()) {
        span(classes = laf.styles.mnChipSymbol()) {
            attributes["title"] = "Repeat — duplicate this step n times"
            +"!"
        }
        if (current != null) {
            mnStepButton(laf, "-") {
                val next = current - 1
                onChange(if (next < 2) null else next)
            }
            input(classes = laf.styles.mnChipInput()) {
                type = InputType.text
                attributes["inputmode"] = "numeric"
                value = current.toString()
                attributes["min"] = "2"
                attributes["step"] = "1"
                css {
                    width = 36.px
                    fontSize = 13.px
                    padding = Padding(0.px, 4.px)
                }
                onInput { e ->
                    val v = (e.target?.asDynamic()?.value as? String)?.toIntOrNull() ?: return@onInput
                    onChange(if (v < 2) null else v)
                }
            }
            mnStepButton(laf, "+") { onChange(current + 1) }
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Remove repeat"
                onClick { onChange(null) }
                +"×"
            }
        } else {
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Add repeat"
                onClick { onChange(2) }
                +"+"
            }
        }
    }
}

private fun FlowContent.mnModChip(
    laf: KlangLookAndFeel,
    symbol: String,
    tooltip: String,
    current: Double?,
    default: Double,
    min: Double = 0.0,
    max: Double? = null,
    step: Double,
    onChange: (Double?) -> Unit,
) {
    div(classes = laf.styles.mnChip()) {
        span(classes = laf.styles.mnChipSymbol()) {
            attributes["title"] = tooltip
            +symbol
        }
        if (current != null) {
            val decimals = step.decimalPlaces()
            mnStepButton(laf, "-") {
                val next = (current - step).roundTo(decimals)
                    .let { if (max != null) it.coerceAtMost(max) else it }
                    .coerceAtLeast(min)
                onChange(if (next <= min) null else next)
            }
            input(classes = laf.styles.mnChipInput()) {
                type = InputType.text
                attributes["inputmode"] = "numeric"
                value = current.toFixed(4)
                attributes["min"] = min.toString()
                if (max != null) attributes["max"] = max.toString()
                attributes["step"] = step.toString()
                css {
                    width = 46.px
                    fontSize = 13.px
                    padding = Padding(0.px, 4.px)
                }
                onInput { e ->
                    val v = (e.target?.asDynamic()?.value as? String)?.toDoubleOrNull() ?: return@onInput
                    onChange(if (v <= min) null else v)
                }
            }
            mnStepButton(laf, "+") {
                val next = (current + step).roundTo(decimals)
                    .let { if (max != null) it.coerceAtMost(max) else it }
                    .coerceAtLeast(min)
                onChange(next)
            }
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Remove"
                onClick { onChange(null) }
                +"×"
            }
        } else {
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Add — $tooltip"
                onClick { onChange(default) }
                +"+"
            }
        }
    }
}

private fun FlowContent.mnEuclideanChip(
    laf: KlangLookAndFeel,
    atom: MnNode.Atom,
    mods: MnNode.Mods,
    onAtomChange: (MnNode.Atom) -> Unit,
) {
    val e = mods.euclidean
    div(classes = laf.styles.mnChip()) {
        span(classes = laf.styles.mnChipSymbol()) {
            attributes["title"] = "Euclidean rhythm (pulses, steps, rotation)"
            +"(,)"
        }
        if (e != null) {
            mnEuclideanInput(laf, "pulses", e.pulses, 1) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(pulses = v))))
            }
            span(classes = laf.styles.mnChipSeparator()) { +"," }
            mnEuclideanInput(laf, "steps", e.steps, 1) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(steps = v))))
            }
            span(classes = laf.styles.mnChipSeparator()) { +"," }
            mnEuclideanInput(laf, "rotation", e.rotation, 0) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(rotation = v))))
            }
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Remove euclidean"
                onClick { onAtomChange(atom.copy(mods = mods.copy(euclidean = null))) }
                +"×"
            }
        } else {
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Add euclidean rhythm"
                onClick { onAtomChange(atom.copy(mods = mods.copy(euclidean = MnNode.Euclidean(3, 8)))) }
                +"+"
            }
        }
    }
}

private fun FlowContent.mnEuclideanChipForRest(
    laf: KlangLookAndFeel,
    rest: MnNode.Rest,
    mods: MnNode.Mods,
    onRestChange: (MnNode.Rest) -> Unit,
) {
    val e = mods.euclidean
    div(classes = laf.styles.mnChip()) {
        span(classes = laf.styles.mnChipSymbol()) {
            attributes["title"] = "Euclidean rhythm (pulses, steps, rotation)"
            +"(,)"
        }
        if (e != null) {
            mnEuclideanInput(laf, "pulses", e.pulses, 1) { v ->
                onRestChange(rest.copy(mods = mods.copy(euclidean = e.copy(pulses = v))))
            }
            span(classes = laf.styles.mnChipSeparator()) { +"," }
            mnEuclideanInput(laf, "steps", e.steps, 1) { v ->
                onRestChange(rest.copy(mods = mods.copy(euclidean = e.copy(steps = v))))
            }
            span(classes = laf.styles.mnChipSeparator()) { +"," }
            mnEuclideanInput(laf, "rotation", e.rotation, 0) { v ->
                onRestChange(rest.copy(mods = mods.copy(euclidean = e.copy(rotation = v))))
            }
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Remove euclidean"
                onClick { onRestChange(rest.copy(mods = mods.copy(euclidean = null))) }
                +"×"
            }
        } else {
            span(classes = laf.styles.mnChipAction()) {
                attributes["title"] = "Add euclidean rhythm"
                onClick { onRestChange(rest.copy(mods = mods.copy(euclidean = MnNode.Euclidean(3, 8)))) }
                +"+"
            }
        }
    }
}

private fun FlowContent.mnEuclideanInput(laf: KlangLookAndFeel, tooltip: String, value: Int, min: Int, onChange: (Int) -> Unit) {
    mnStepButton(laf, "-") { if (value - 1 >= min) onChange(value - 1) }
    input(classes = laf.styles.mnChipInput()) {
        type = InputType.number
        this.value = value.toString()
        attributes["min"] = min.toString()
        attributes["step"] = "1"
        attributes["title"] = tooltip
        css {
            width = 32.px
            fontSize = 13.px
            padding = Padding(2.px, 4.px)
        }
        onInput { e ->
            val v = (e.target?.asDynamic()?.value as? String)?.toIntOrNull() ?: return@onInput
            onChange(v)
        }
    }
    mnStepButton(laf, "+") { onChange(value + 1) }
}

private fun FlowContent.mnStepButton(laf: KlangLookAndFeel, label: String, onClick: () -> Unit) {
    span(classes = laf.styles.mnChipStep()) {
        onClick { onClick() }
        +label
    }
}

// ── Scroll helper ─────────────────────────────────────────────────────────────

private fun CommonAttributeGroupFacade.mnOnScroll(handler: (org.w3c.dom.events.Event) -> Unit) {
    consumer.onTagEvent(this, "onscroll", handler.asDynamic())
}
