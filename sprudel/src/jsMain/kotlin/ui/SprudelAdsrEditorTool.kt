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
import kotlinx.html.unsafe

// ── Tool singleton ────────────────────────────────────────────────────────────

/**
 * [KlangUiToolEmbeddable] for the per-param adsr(attack, decay, sustain, release) call.
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits all four envelope
 *   params of the host call and commits the full argument list.
 * - Scalar fallback (embedded / sequence atom): edits a single attack value in seconds.
 */
object SprudelAdsrEditorTool : KlangUiToolEmbeddable {
    override val title: String = "ADSR Editor"

    override val iconFn: SemanticIconFn = { chart_area }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelAdsrEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelAdsrEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelAdsrEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelAdsrEditorComp.Props(toolCtx, embedded)) { SprudelAdsrEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelAdsrEditorComp(ctx: Ctx<Props>) : Component<SprudelAdsrEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

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

    // Display fallbacks for missing args mirror the engine defaults (AdsrDef Std.defaultSynth).
    private val parsedSustain
        get() = parseNum(call?.args?.getOrNull(2), 1.0)

    private val parsedRelease
        get() = parseNum(call?.args?.getOrNull(3), 0.05)

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

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "adsr-editor"
                css { minWidth = 600.px }
                toolHeaderWithInfo("ADSR Envelope", props.toolCtx, infoPopup)
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
            key = "adsr-editor-content-$resetCounter"

            ui.form {
                key = "adsr-editor-form"
                ui.four.stackable.fields {
                    key = "adsr-editor-fields"
                    adsrRow("Attack", "sec", attack, 0.001, "attack") { attack = it; dirty += 0; liveUpdate() }
                    if (call != null) {
                        adsrRow("Decay", "sec", decay, 0.001, "decay") { decay = it; dirty += 1; liveUpdate() }
                        adsrRow("Sustain", "", sustain, 0.01, "sustain") { sustain = it; dirty += 2; liveUpdate() }
                        adsrRow("Release", "sec", release, 0.001, "release") { release = it; dirty += 3; liveUpdate() }
                    }
                }
            }
            ui.divider {
                key = "adsr-editor-divider"
            }
            div {
                key = "adsr-editor-curve"
                css { if (!props.embedded) marginBottom = 1.rem }
                unsafe { raw(buildAdsrSvg()) }
            }
        }
    }

    // ── SVG curve ─────────────────────────────────────────────────────────────

    private fun buildAdsrSvg(): String {
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

        val pts = "$x0,$yBot $x1,$yTop $x2,$ySus $x3,$ySus $x4,$yBot"
        val fill = "M$x0 ${yBot}L$x1 ${yTop}L$x2 ${ySus}L$x3 ${ySus}L$x4 ${yBot}Z"

        fun lx(x: Double) =
            """<line x1="$x" y1="$yBot" x2="$x" y2="$yTop" stroke="rgba(255,255,255,0.15)" stroke-width="1" stroke-dasharray="3,3"/>"""

        fun label(x: Double, txt: String) = """<text x="$x" y="${h - 5}" text-anchor="middle" font-size="10" fill="#ccc">$txt</text>"""

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $w $h" width="100%" style="display:block">
              <rect x="$padL" y="$padT" width="$drawW" height="$drawH" fill="rgba(0,0,0,0.2)" rx="2"/>
              ${lx(x1)} ${lx(x2)} ${lx(x3)}
              <line x1="$x0" y1="$ySus" x2="$x4" y2="$ySus" stroke="rgba(255,255,255,0.15)" stroke-width="1" stroke-dasharray="4,4"/>
              <path d="$fill" fill="${laf.gold}26" stroke="none"/>
              <polyline points="$pts" fill="none" stroke="${laf.gold}" stroke-width="1" stroke-linejoin="round" stroke-linecap="round"/>
              ${label((x0 + x1) / 2, "A")}
              ${label((x1 + x2) / 2, "D")}
              ${label((x2 + x3) / 2, "S")}
              ${label((x3 + x4) / 2, "R")}
            </svg>
        """.trimIndent()
    }

    private fun FlowContent.adsrRow(
        label: String,
        unit: String,
        value: Double,
        step: Double,
        subField: String,
        onChange: (Double) -> Unit,
    ) {
        UiInputField(value, onChange) {
            domKey(label)
            step(step)
            label {
                +label
                paramInfoIcon(subField, props.toolCtx, infoPopup)
            }
            if (unit.isNotEmpty()) rightLabel { ui.basic.label { +unit } }
        }
    }
}
