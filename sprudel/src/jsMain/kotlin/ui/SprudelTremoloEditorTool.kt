/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.HoverPopupCtrl
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgPolyline
import io.peekandpoke.klang.ui.svgRect
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.forms.formController
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toFixed
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.gap
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.PI
import kotlin.math.sin

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * [KlangUiToolEmbeddable] for the per-param tremolo(depth, sync, shape, skew, phase) call.
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits depth plus the optional
 *   sync-rate/shape/skew/phase params of the host call and commits the full argument list.
 *   The shape is a STRING param and commits as a quoted string literal; unset optionals stay
 *   omitted (null slots).
 * - Scalar fallback (embedded / sequence atom): edits a single depth value.
 */
object SprudelTremoloEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Tremolo Editor"

    override val iconFn: SemanticIconFn = { wave_square }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelTremoloEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelTremoloEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelTremoloEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelTremoloEditorComp.Props(toolCtx, embedded)) { SprudelTremoloEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelTremoloEditorComp(ctx: Ctx<Props>) : Component<SprudelTremoloEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    companion object {
        private val shapes = listOf("sine", "triangle", "square", "sawtooth", "ramp")

        /**
         * The engine's aliases (LfoShape.parseLfoShape) folded onto the canonical entries, so
         * a document written as `"saw"` — which is what this editor itself emitted before the
         * list was corrected — still opens with its shape selected.
         */
        private val shapeAliases = mapOf(
            "sin" to "sine",
            "tri" to "triangle",
            "sqr" to "square",
            "pulse" to "square",
            "saw" to "sawtooth",
        )

        private fun canonicalShape(raw: String?): String? =
            raw?.lowercase()?.let { shapeAliases[it] ?: it }?.takeIf { it in shapes }
    }

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val call = props.toolCtx.call

    private val initialValue = props.toolCtx.currentValue ?: ""

    private fun parseNum(text: String?, fallback: Double): Double =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull() ?: fallback

    private fun parseNumOrNull(text: String?): Double? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull()

    private fun parseStr(text: String?): String? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")

    // Whole-call mode reads the params from the host call's args; scalar mode reads the single arg.
    private val parsedDepth
        get() = parseNum(call?.args?.getOrNull(0) ?: initialValue, 0.5)

    private val parsedRate
        get() = parseNumOrNull(call?.args?.getOrNull(1))

    private val parsedShape
        get() = canonicalShape(parseStr(call?.args?.getOrNull(2)))

    private val parsedSkew
        get() = parseNumOrNull(call?.args?.getOrNull(3))

    private val parsedPhase
        get() = parseNumOrNull(call?.args?.getOrNull(4))

    private var depth by value(parsedDepth)
    private var rate by value(parsedRate)
    private var shape by value(parsedShape)
    private var skew by value(parsedSkew)
    private var phase by value(parsedPhase)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = listOf(
        parseNumOrNull(call?.args?.getOrNull(0)) != null,
        parseNumOrNull(call?.args?.getOrNull(1)) != null,
        canonicalShape(parseStr(call?.args?.getOrNull(2))) != null,
        parseNumOrNull(call?.args?.getOrNull(3)) != null,
        parseNumOrNull(call?.args?.getOrNull(4)) != null,
    )
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "${depth.fmt()}, ${rate?.fmt() ?: "-"}, ${shape ?: "-"}, ${skew?.fmt() ?: "-"}, ${phase?.fmt() ?: "-"}"
        } else {
            depth.fmt()
        }

    /**
     * Writes a slot only when that is safe: the user touched it, or the original arg parses
     * (rewriting it loses nothing). Untouched non-parseable args are preserved; untouched
     * absent slots stay absent so the engine defaults apply.
     */
    private fun put(texts: MutableList<String?>, index: Int, text: String?) {
        val original = call?.args?.getOrNull(index)
        if (index in dirty || (original != null && parseable[index])) {
            texts[index] = text
        }
    }

    private fun commitValue() {
        val c = call
        if (c != null) {
            val texts = c.args.toMutableList()
            while (texts.size < 5) texts.add(null)
            put(texts, 0, depth.fmt())
            put(texts, 1, rate?.fmt())
            // shape is a STRING param — commits as a quoted string literal
            put(texts, 2, shape?.let { "\"$it\"" })
            put(texts, 3, skew?.fmt())
            put(texts, 4, phase?.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(depth.fmt())
        }
        hasCommitted = true
        lastCommitted = buildValue()
    }

    // Built-state fingerprints: in whole-call mode [initialValue] is only the clicked arg's
    // text, so the Reset/Update buttons compare built snapshots instead (initial state and
    // last committed state); scalar mode keeps the plain text comparison.
    private val initialBuiltValue = buildValue()
    private var lastCommitted = initialBuiltValue

    private val isInitialModified
        get() = if (call != null) buildValue() != initialBuiltValue else initialValue != buildValue()

    private val isCurrentModified
        get() = if (call != null) buildValue() != lastCommitted else (props.toolCtx.currentValue ?: "") != buildValue()

    private fun liveUpdate() {
        if (props.embedded || autoUpdate) {
            commitValue()
        }
    }

    private fun onCancel() {
        if (!props.embedded && autoUpdate && hasCommitted && isInitialModified) {
            val c = call
            if (c != null) c.onCommitCall(c.args) else props.toolCtx.onCommit(initialValue)
        }
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        dirty.clear()
        depth = parsedDepth
        rate = parsedRate
        shape = parsedShape
        skew = parsedSkew
        phase = parsedPhase
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        commitValue()
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                toolHeaderWithInfo("Tremolo", props.toolCtx, infoPopup)
                renderContent()
                ui.divider {}
                ToolButtonBar(
                    isInitialModified = isInitialModified,
                    isCurrentModified = isCurrentModified,
                    onCancel = ::onCancel,
                    onReset = ::onReset,
                    onCommit = ::onCommit,
                )
            }
        }
    }

    private fun FlowContent.renderContent() {
        div {
            key = "tremolo-editor-content-$resetCounter"

            ui.form {
                ui.two.stackable.fields {
                    UiInputField(depth, { depth = it; dirty += 0; liveUpdate() }) {
                        domKey("depth")
                        step(0.01)
                        label {
                            +"Depth"
                            paramInfoIcon("depth", props.toolCtx, infoPopup)
                        }
                    }
                    if (call != null) {
                        nullableField("rate", "Rate (cycles)", 0.5, rate, subField = "sync") { rate = it; dirty += 1; liveUpdate() }
                    }
                }
            }

            // Shape buttons + skew/phase (whole-call mode only)
            if (call != null) {
                div {
                    css {
                        display = Display.flex
                        flexWrap = FlexWrap.wrap
                        gap = 6.px
                        marginTop = 8.px
                        marginBottom = 8.px
                    }
                    for (s in shapes) {
                        val isSelected = shape == s
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            key = s
                            onClick {
                                shape = if (isSelected) null else s
                                dirty += 2
                                liveUpdate()
                            }
                            +s
                        }
                    }
                }

                ui.form {
                    ui.two.stackable.fields {
                        nullableField("skew", "Skew", 0.01, skew, subField = "skew") { skew = it; dirty += 3; liveUpdate() }
                        nullableField("phase", "Phase", 0.01, phase, subField = "phase") { phase = it; dirty += 4; liveUpdate() }
                    }
                }
            }

            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderTremoloViz()
            }
        }
    }

    private fun FlowContent.nullableField(
        key: String,
        labelText: String,
        stepVal: Double,
        current: Double?,
        subField: String? = null,
        onChange: (Double?) -> Unit,
    ) {
        UiInputField.nullable(current, { onChange(it) }) {
            domKey(key)
            step(stepVal)
            if (subField != null) {
                label {
                    +labelText
                    paramInfoIcon(subField, props.toolCtx, infoPopup)
                }
            } else {
                label(labelText)
            }
            if (current == null) {
                placeholder("default")
            }
            rightLabel {
                ui.basic.icon.label {
                    css { cursor = Cursor.pointer }
                    onClick {
                        if (current != null) onChange(null) else onChange(0.0)
                    }
                    if (current != null) icon.times() else icon.plus()
                }
            }
        }
    }

    // ── SVG visualization ────────────────────────────────────────────────────

    private fun FlowContent.renderTremoloViz() {
        val w = 400.0
        val h = 80.0
        val padL = 10.0
        val padR = 10.0
        val padT = 8.0
        val padB = 8.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedDepth = depth.coerceIn(0.0, 1.0)
        val clampedRate = (rate ?: 4.0).coerceIn(0.5, 32.0)
        val clampedPhase = (phase ?: 0.0)
        val goldHex = laf.gold

        val numPoints = drawW.toInt()

        val points = buildString {
            for (i in 0 until numPoints) {
                val t = i.toDouble() / numPoints  // 0..1 (one cycle)

                // Generate LFO waveform based on shape
                val lfoPhase = (t * clampedRate + clampedPhase) % 1.0
                // Matches the engine's LfoShape: sine, triangle and square all spend the
                // first half of the cycle high (sine and triangle enter it at the midpoint
                // rising, the square already at its top), sawtooth rises and ramp mirrors it.
                // NOT yet drawn: skew, which the editor commits but the preview ignores.
                val lfoRaw = when (shape) {
                    "square" -> if (lfoPhase < 0.5) 1.0 else -1.0

                    "triangle" -> when {
                        lfoPhase < 0.25 -> 4.0 * lfoPhase
                        lfoPhase < 0.75 -> 2.0 - 4.0 * lfoPhase
                        else -> 4.0 * lfoPhase - 4.0
                    }

                    "sawtooth" -> 2.0 * lfoPhase - 1.0

                    "ramp" -> 1.0 - 2.0 * lfoPhase

                    else -> sin(lfoPhase * 2.0 * PI) // sine (default)
                }

                val x = padL + i.toDouble()
                val y = padT + drawH / 2.0 - (lfoRaw * clampedDepth * drawH / 2.0)
                if (i > 0) append(" ")
                append("$x,$y")
            }
        }

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            svgLine(padL, padT + drawH / 2.0, padL + drawW, padT + drawH / 2.0, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgPolyline(
                points = points,
                stroke = goldHex,
                strokeWidth = "1",
                strokeLinejoin = "round",
                strokeLinecap = "round",
            )
        }
    }
}
