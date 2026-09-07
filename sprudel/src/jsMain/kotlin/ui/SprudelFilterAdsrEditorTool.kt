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
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Configurable tool class ─────────────────────────────────────────────────

/**
 * Configurable [KlangUiToolEmbeddable] written for the retired per-param calls
 * lpadsr/hpadsr/bpadsr/nfadsr(attack, decay, sustain, release). Since 2026-09-07 the envelope
 * stages are the `attack, decay, sustain, release` slots of lpf/hpf/bpf/notch, at positions 4..7
 * (3..6 for bpf/notch); this tool still edits argument slots 0..3 of its host call, so it is bound
 * to no door until the tools rework (`docs/tasks/editor-tools-named-arguments.md`).
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits argument slots 0..3 of
 *   the host call as attack, decay, sustain, release and commits the full argument list.
 * - Scalar fallback (embedded / sequence atom): edits a single attack value in seconds.
 */
class SprudelFilterAdsrEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
) : KlangUiToolEmbeddable {

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelFilterAdsrEditorComp(ctx, embedded = false, title = title)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelFilterAdsrEditorComp(ctx, embedded = true, title = title)
    }
}

// ── Singleton instances ─────────────────────────────────────────────────────

object SprudelLpAdsrEditorTool : KlangUiToolEmbeddable by SprudelFilterAdsrEditorTool(
    title = "LP Filter Envelope",
    iconFn = { chart_area },
)

object SprudelHpAdsrEditorTool : KlangUiToolEmbeddable by SprudelFilterAdsrEditorTool(
    title = "HP Filter Envelope",
    iconFn = { chart_area },
)

object SprudelBpAdsrEditorTool : KlangUiToolEmbeddable by SprudelFilterAdsrEditorTool(
    title = "BP Filter Envelope",
    iconFn = { chart_area },
)

object SprudelNfAdsrEditorTool : KlangUiToolEmbeddable by SprudelFilterAdsrEditorTool(
    title = "Notch Filter Envelope",
    iconFn = { chart_area },
)

// ── Entry-point helper ──────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelFilterAdsrEditorComp(
    toolCtx: KlangUiToolContext,
    embedded: Boolean,
    title: String,
) = comp(
    SprudelFilterAdsrEditorComp.Props(toolCtx, embedded, title)
) {
    SprudelFilterAdsrEditorComp(it)
}

// ── Component ───────────────────────────────────────────────────────────────

private class SprudelFilterAdsrEditorComp(ctx: Ctx<Props>) : Component<SprudelFilterAdsrEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val embedded: Boolean = false,
        val title: String,
    )

    // ── Parse current value from raw source text ────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val call = props.toolCtx.call

    private val initialValue = props.toolCtx.currentValue ?: ""
    private var currentValue by value(initialValue)

    private fun parseNum(text: String?, fallback: Double): Double =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull() ?: fallback

    private fun parseNumOrNull(text: String?): Double? =
        text?.trim()?.removePrefix("\"")?.removeSuffix("\"")?.toDoubleOrNull()

    // Whole-call mode reads the envelope from the host call's args; scalar mode reads the single arg.
    private val parsedAttack
        get() = parseNum(call?.args?.getOrNull(0) ?: currentValue, 0.01)

    private val parsedDecay
        get() = parseNum(call?.args?.getOrNull(1), 0.1)

    // Display fallbacks for missing args mirror the engine defaults (FilterEnvDef.resolve).
    private val parsedSustain
        get() = parseNum(call?.args?.getOrNull(2), 1.0)

    private val parsedRelease
        get() = parseNum(call?.args?.getOrNull(3), 0.1)

    private var attack by value(parsedAttack)
    private var decay by value(parsedDecay)
    private var sustain by value(parsedSustain)
    private var release by value(parsedRelease)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = List(4) { parseNumOrNull(call?.args?.getOrNull(it)) != null }
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "${attack.fmt()}, ${decay.fmt()}, ${sustain.fmt()}, ${release.fmt()}"
        } else {
            attack.fmt()
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
            while (texts.size < 4) texts.add(null)
            put(texts, 0, attack.fmt())
            put(texts, 1, decay.fmt())
            put(texts, 2, sustain.fmt())
            put(texts, 3, release.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(attack.fmt())
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
        get() = if (call != null) buildValue() != lastCommitted else currentValue != buildValue()

    /** Called after every slider change in embedded mode — propagates live updates to the host. */
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
        currentValue = initialValue
        attack = parsedAttack
        decay = parsedDecay
        sustain = parsedSustain
        release = parsedRelease
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        commitValue()
    }

    // ── Render ──────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "filter-adsr-editor"
                css { minWidth = 600.px }
                toolHeaderWithInfo(props.title, props.toolCtx, infoPopup)
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
            key = "filter-adsr-editor-content-$resetCounter"

            ui.form {
                key = "filter-adsr-editor-form"
                ui.four.stackable.fields {
                    key = "filter-adsr-editor-fields"
                    adsrRow("Attack", "sec", attack, 0.001) { attack = it; dirty += 0; liveUpdate() }
                    if (call != null) {
                        adsrRow("Decay", "sec", decay, 0.001) { decay = it; dirty += 1; liveUpdate() }
                        adsrRow("Sustain", "", sustain, 0.01) { sustain = it; dirty += 2; liveUpdate() }
                        adsrRow("Release", "sec", release, 0.001) { release = it; dirty += 3; liveUpdate() }
                    }
                }
            }
            ui.divider {
                key = "filter-adsr-editor-divider"
            }
            div {
                key = "filter-adsr-editor-curve"
                css { if (!props.embedded) marginBottom = 1.rem }
                renderAdsrSvg()
            }
        }
    }

    // ── SVG curve ───────────────────────────────────────────────────────────

    private fun FlowContent.renderAdsrSvg() {
        val w = 560.0
        val h = 120.0
        val padL = 12.0
        val padR = 12.0
        val padT = 10.0
        val padB = 22.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        // Sustain hold is a fixed visual segment so the shape is always readable
        val sustainHold = maxOf(attack + decay, 0.3)
        val totalTime = attack + decay + sustainHold + release
        val scale = drawW / totalTime

        val x0 = padL
        val x1 = x0 + attack * scale
        val x2 = x1 + decay * scale
        val x3 = x2 + sustainHold * scale
        val x4 = x3 + release * scale

        val yBot = padT + drawH
        val yTop = padT
        val ySus = padT + drawH * (1.0 - sustain.coerceIn(0.0, 1.0))

        val linePath = "M$x0 ${yBot}L$x1 ${yTop}L$x2 ${ySus}L$x3 ${ySus}L$x4 $yBot"
        val fillPath = "${linePath}Z"

        svgRoot(viewBox = "0 0 $w $h") {
            // Background
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            // Dashed guide lines at phase boundaries
            svgLine(x1, yBot, x1, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x2, yBot, x2, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x3, yBot, x3, yTop, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // Sustain level line
            svgLine(x0, ySus, x4, ySus, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // Fill under envelope
            svgPath(d = fillPath, fill = "${laf.gold}26")
            // Envelope line
            svgPath(d = linePath, stroke = laf.gold, strokeWidth = "1")
            // Phase labels
            svgText((x0 + x1) / 2, h - 5, "A", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x1 + x2) / 2, h - 5, "D", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x2 + x3) / 2, h - 5, "S", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText((x3 + x4) / 2, h - 5, "R", fill = "#ccc", fontSize = "7", textAnchor = "middle")
        }
    }

    private fun FlowContent.adsrRow(
        label: String,
        unit: String,
        value: Double,
        step: Double,
        onChange: (Double) -> Unit,
    ) {
        UiInputField(value, onChange) {
            domKey(label)
            step(step)
            label(label)
            if (unit.isNotEmpty()) rightLabel { ui.basic.label { +unit } }
        }
    }
}
