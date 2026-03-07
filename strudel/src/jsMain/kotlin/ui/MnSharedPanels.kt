package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onInput
import de.peekandpoke.ultra.html.onKeyUp
import io.peekandpoke.klang.strudel.lang.parser.MnNode
import io.peekandpoke.klang.strudel.lang.parser.MnRenderer
import kotlinx.css.*
import kotlinx.html.*
import kotlin.math.pow
import kotlin.math.roundToLong

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
    text: String,
    atom: MnNode.Atom?,
    parseError: Boolean,
    onChange: (newText: String, cursor: Int) -> Unit,
) {
    val highlightStart = atom?.sourceRange?.first
    val highlightEnd = if (atom != null && highlightStart != null)
        (highlightStart + MnRenderer.renderNode(atom).length).coerceAtMost(text.length)
    else null

    div {
        div {
            css {
                position = Position.relative
                backgroundColor = Color.white
                borderRadius = 4.px
            }

            // Highlight layer behind the textarea
            div {
                css {
                    position = Position.absolute
                    top = 0.px; left = 0.px; right = 0.px; bottom = 0.px
                    fontFamily = "monospace"
                    fontSize = 15.px
                    padding = Padding(8.px, 10.px)
                    put("white-space", "pre")
                    put("pointer-events", "none")
                    put("box-sizing", "border-box")
                    put("border", "1px solid transparent")
                    color = Color("transparent")
                }
                if (highlightStart != null && highlightEnd != null && highlightStart < highlightEnd) {
                    +text.substring(0, highlightStart)
                    mark {
                        css {
                            val col = Color("#CCF")
                            backgroundColor = col.withAlpha(0.5)
                            color = Color("transparent")
                            borderRadius = 2.px
                            border = Border(1.px, BorderStyle.solid, col.withAlpha(0.8))
                            padding = Padding(1.px, 2.px)
                            marginTop = (-1).px
                            marginLeft = (-3).px
                        }
                        +text.substring(highlightStart, highlightEnd)
                    }
                    +text.substring(highlightEnd)
                } else {
                    +text
                }
            }

            // Editable textarea on top of highlight layer
            textArea {
                placeholder = "e.g. bd sd [hh cp] ~ bd*2"
                attributes["value"] = text
                rows = (text.count { it == '\n' } + 1).toString()
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
                    put("white-space", "pre")
                    put("overflow-x", "auto")
                    put("overflow-y", "hidden")
                    put("min-height", "38px")
                    backgroundColor = Color("transparent")
                    put("background", "transparent")
                    put("caret-color", "#000")
                }
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

// ── Modifier panel ────────────────────────────────────────────────────────────

/**
 * Renders the modifier chip row for an atom (multiplier, divisor, weight, probability, euclidean).
 *
 * @param atom The atom whose modifiers are shown.
 * @param onAtomChange Called when the atom (with updated mods) should replace the original.
 */
fun FlowContent.mnModifierPanel(
    atom: MnNode.Atom,
    onToggleRest: (() -> Unit)? = null,
    onAtomChange: (MnNode.Atom) -> Unit,
) {
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

        if (onToggleRest != null) {
            span {
                css {
                    cursor = Cursor.pointer
                    fontFamily = "monospace"
                    fontSize = 13.px
                    color = Color("#888")
                    backgroundColor = Color("#f5f5f5")
                    borderRadius = 6.px
                    padding = Padding(4.px, 10.px)
                }
                attributes["title"] = "Convert to rest"
                onClick { onToggleRest() }
                +"~ rest"
            }
        }

        mnModChip(symbol = "*", tooltip = "Multiplier — play faster", current = mods.multiplier, default = 2.0, step = 0.5) { v ->
            onAtomChange(atom.copy(mods = mods.copy(multiplier = v)))
        }
        mnModChip(symbol = "/", tooltip = "Divisor — play slower", current = mods.divisor, default = 2.0, min = 1.0, step = 1.0) { v ->
            onAtomChange(atom.copy(mods = mods.copy(divisor = v)))
        }
        mnModChip(symbol = "@", tooltip = "Weight — relative time weight", current = mods.weight, default = 2.0, step = 0.5) { v ->
            onAtomChange(atom.copy(mods = mods.copy(weight = v)))
        }
        mnModChip(
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
        mnEuclideanChip(atom, mods, onAtomChange)
    }
}

// ── Private chip helpers ──────────────────────────────────────────────────────

private fun FlowContent.mnModChip(
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
            mnStepButton("-") {
                val next = (current - step).roundTo(decimals)
                    .let { if (max != null) it.coerceAtMost(max) else it }
                    .coerceAtLeast(min)
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
            mnStepButton("+") {
                val next = (current + step).roundTo(decimals)
                    .let { if (max != null) it.coerceAtMost(max) else it }
                    .coerceAtLeast(min)
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

private fun FlowContent.mnEuclideanChip(
    atom: MnNode.Atom,
    mods: MnNode.Mods,
    onAtomChange: (MnNode.Atom) -> Unit,
) {
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
            mnEuclideanInput("pulses", e.pulses, 1) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(pulses = v))))
            }
            span { css { color = Color("#999"); fontSize = 12.px }; +"," }
            mnEuclideanInput("steps", e.steps, 1) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(steps = v))))
            }
            span { css { color = Color("#999"); fontSize = 12.px }; +"," }
            mnEuclideanInput("rotation", e.rotation, 0) { v ->
                onAtomChange(atom.copy(mods = mods.copy(euclidean = e.copy(rotation = v))))
            }
            span {
                css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                attributes["title"] = "Remove euclidean"
                onClick { onAtomChange(atom.copy(mods = mods.copy(euclidean = null))) }
                +"×"
            }
        } else {
            span {
                css { cursor = Cursor.pointer; color = Color("#aaa"); fontSize = 14.px; padding = Padding(0.px, 2.px) }
                attributes["title"] = "Add euclidean rhythm"
                onClick { onAtomChange(atom.copy(mods = mods.copy(euclidean = MnNode.Euclidean(3, 8)))) }
                +"+"
            }
        }
    }
}

private fun FlowContent.mnEuclideanInput(tooltip: String, value: Int, min: Int, onChange: (Int) -> Unit) {
    mnStepButton("-") { if (value - 1 >= min) onChange(value - 1) }
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
    mnStepButton("+") { onChange(value + 1) }
}

private fun FlowContent.mnStepButton(label: String, onClick: () -> Unit) {
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

// ── String helpers ────────────────────────────────────────────────────────────

/**
 * Wraps a mini-notation string in the appropriate quote style for committing back to source.
 * Multi-line strings use backtick quotes; single-line strings use double quotes.
 */
internal fun String.quoteForCommit(): String =
    if (contains('\n')) "`$this`" else "\"$this\""

// ── Numeric helpers ───────────────────────────────────────────────────────────

internal fun Double.toFixed(decimals: Int): String {
    val s = roundTo(decimals).toString()
    val dotIdx = s.indexOf('.')
    return if (dotIdx < 0) s else s.trimEnd('0').trimEnd('.')
}

internal fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToLong().toDouble() / factor
}

internal fun Double.decimalPlaces(): Int {
    val s = toString()
    val dot = s.indexOf('.')
    return if (dot < 0) 0 else s.substring(dot + 1).trimEnd('0').length
}
