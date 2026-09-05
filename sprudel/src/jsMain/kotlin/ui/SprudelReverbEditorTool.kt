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
import io.peekandpoke.klang.ui.svgPath
import io.peekandpoke.klang.ui.svgRect
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.klang.ui.svgText
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
import kotlinx.css.cursor
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.exp

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * [KlangUiToolEmbeddable] for the per-param roomWet(wet, size, fade, lowpass, dim) / reverb(wet, ...) call.
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits the reverb send (roomWet) and size
 *   plus the optional fade/lowpass/dim params of the host call and commits the full argument
 *   list. Unset optionals stay omitted (null slots).
 * - Scalar fallback (embedded / sequence atom): edits a single wet (send) value.
 */
object SprudelReverbEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Reverb Editor"

    override val iconFn: SemanticIconFn = { sync_alternate }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelReverbEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelReverbEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelReverbEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelReverbEditorComp.Props(toolCtx, embedded)) { SprudelReverbEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelReverbEditorComp(ctx: Ctx<Props>) : Component<SprudelReverbEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

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

    // Whole-call mode reads the params from the host call's args; scalar mode reads the single arg.
    private val parsedRoom
        get() = parseNum(call?.args?.getOrNull(0) ?: initialValue, 0.5)

    private val parsedSize
        get() = parseNum(call?.args?.getOrNull(1), 1.0)

    private val parsedFade
        get() = parseNumOrNull(call?.args?.getOrNull(2))

    private val parsedLowpass
        get() = parseNumOrNull(call?.args?.getOrNull(3))

    private val parsedDim
        get() = parseNumOrNull(call?.args?.getOrNull(4))

    private var room by value(parsedRoom)
    private var size by value(parsedSize)
    private var fade by value(parsedFade)
    private var lowpass by value(parsedLowpass)
    private var dim by value(parsedDim)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = List(5) { parseNumOrNull(call?.args?.getOrNull(it)) != null }
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "${room.fmt()}, ${size.fmt()}, ${fade?.fmt() ?: "-"}, ${lowpass?.fmt() ?: "-"}, ${dim?.fmt() ?: "-"}"
        } else {
            room.fmt()
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
            put(texts, 0, room.fmt())
            put(texts, 1, size.fmt())
            put(texts, 2, fade?.fmt())
            put(texts, 3, lowpass?.fmt())
            put(texts, 4, dim?.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(room.fmt())
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
        room = parsedRoom
        size = parsedSize
        fade = parsedFade
        lowpass = parsedLowpass
        dim = parsedDim
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
                toolHeaderWithInfo("Reverb", props.toolCtx, infoPopup)
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
            key = "reverb-editor-content-$resetCounter"

            ui.form {
                ui.five.stackable.fields {
                    UiInputField(room, { room = it; dirty += 0; liveUpdate() }) {
                        domKey("room")
                        step(0.01)
                        label {
                            +"Room (send)"
                            paramInfoIcon("wet", props.toolCtx, infoPopup)
                        }
                    }
                    if (call != null) {
                        UiInputField(size, { size = it; dirty += 1; liveUpdate() }) {
                            domKey("size")
                            step(0.1)
                            label {
                                +"Size (0–10)"
                                paramInfoIcon("size", props.toolCtx, infoPopup)
                            }
                        }
                        nullableField("fade", "Fade (s)", 0.01, fade, subField = "fade") { fade = it; dirty += 2; liveUpdate() }
                        nullableField("lowpass", "Lowpass (Hz)", 100.0, lowpass, subField = "lowpass") { lowpass = it; dirty += 3; liveUpdate() }
                        nullableField("dim", "Dim (Hz)", 100.0, dim, subField = "dim") { dim = it; dirty += 4; liveUpdate() }
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderReverbCurve()
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

    // ── SVG curve ─────────────────────────────────────────────────────────────

    private fun FlowContent.renderReverbCurve() {
        val w = 400
        val h = 90
        val padL = 22.0
        val padR = 6.0
        val padT = 6.0
        val padB = 24.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val clampedRoom = room.coerceIn(0.0, 1.0)
        val clampedSize = size.coerceIn(0.1, 10.0)
        // Room (wet/dry) controls the starting level.
        // Size controls the tail length: higher size = longer, softer decay.
        // Fade overrides size for the decay rate when set.
        val effectiveFade = fade ?: clampedSize
        // Normalize to 0..1 for the decay curve (size is 0..10)
        val normalizedFade = (effectiveFade / 10.0).coerceIn(0.01, 1.0)
        // Decay rate: higher normalizedFade = slower decay (longer tail)
        val decayRate = 1.0 + normalizedFade * 9.0 // 1..10: stretch factor for the tail

        val numPoints = 100
        val points = (0..numPoints).map { i ->
            val t = i.toDouble() / numPoints
            val x = padL + t * drawW
            // Exponential decay shaped by size — bigger room = gentler slope
            val envelope = exp(-t * 10.0 / decayRate)
            val level = (clampedRoom * envelope).coerceIn(0.0, 1.0)
            val y = padT + drawH - drawH * level
            x to y
        }

        val pathData = points.mapIndexed { i, (x, y) ->
            if (i == 0) "M $x $y" else "L $x $y"
        }.joinToString(" ")

        val fillPath = "$pathData L ${padL + drawW} ${padT + drawH} L $padL ${padT + drawH} Z"

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")

            // Y-axis grid lines and tick labels
            for (v in listOf(0.0, 0.5, 1.0)) {
                val y = padT + drawH - drawH * v
                svgLine(padL, y, padL + drawW, y, stroke = "rgba(255,255,255,0.2)", strokeWidth = "0.5")
                svgText(padL - 3, y + 2, v.fmt(), fill = "#ccc", fontSize = "5", textAnchor = "end")
            }

            // X-axis tick marks and labels
            for (i in 0..10) {
                val t = i / 10.0
                val x = padL + t * drawW
                val tickH = if (i % 5 == 0) 4.0 else 2.0
                svgLine(
                    x1 = x,
                    y1 = padT + drawH,
                    x2 = x,
                    y2 = padT + drawH + tickH,
                    stroke = if (i % 5 == 0) "#bbb" else "#ddd",
                    strokeWidth = "0.5"
                )
                if (i % 5 == 0) {
                    val label = when (i) {
                        0 -> "0"
                        5 -> "0.5"
                        10 -> "1"
                        else -> ""
                    }
                    svgText(x = x, y = padT + drawH + 10, text = label, fill = "#ccc", fontSize = "5", textAnchor = "middle")
                }
            }

            // Fill under curve
            svgPath(d = fillPath, fill = "${laf.gold}33")

            // Curve line
            svgPath(d = pathData, stroke = laf.gold, strokeWidth = "1")

            // Y-axis label (rotated)
            svgText(
                x = 4, y = padT + drawH / 2,
                text = "Level", fill = "#ccc", fontSize = "5", textAnchor = "middle",
                transform = "rotate(-90, 4, ${padT + drawH / 2})",
            )

            // X-axis label
            svgText(x = padL + drawW / 2, y = h - 2, text = "Time", fill = "#ccc", fontSize = "5", textAnchor = "middle")
        }
    }
}
