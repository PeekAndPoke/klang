/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
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
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.WhiteSpace
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.gap
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.whiteSpace
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div

// ── Tool singleton ───────────────────────────────────────────────────────────

/**
 * [KlangUiToolEmbeddable] for the per-param sndSuperSaw(voices, spread) call
 * (also serves the other sndSuper* functions sharing that signature).
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits voices AND spread of the
 *   host call (incl. presets) and commits the full argument list. voices commits as an integer
 *   literal (no decimal point).
 * - Scalar fallback (embedded / sequence atom): edits a single voices count.
 */
object SprudelSuperSawEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Super Saw Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelSuperSawEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelSuperSawEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelSuperSawEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelSuperSawEditorComp.Props(toolCtx, embedded)) { SprudelSuperSawEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class SuperSawPreset(
    val name: String,
    val voices: Int,
    val detune: Double,
)

private val PRESETS = listOf(
    SuperSawPreset("Thin", 3, 0.1),
    SuperSawPreset("Classic", 5, 0.2),
    SuperSawPreset("Fat", 7, 0.3),
    SuperSawPreset("Wide", 5, 0.5),
    SuperSawPreset("Tight", 7, 0.08),
    SuperSawPreset("Massive", 9, 0.4),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelSuperSawEditorComp(ctx: Ctx<Props>) : Component<SprudelSuperSawEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

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

    // Whole-call mode reads voices/spread from the host call's args; scalar mode reads the single arg.
    private val parsedVoices
        get() = parseNum(call?.args?.getOrNull(0) ?: currentValue, 5.0).toInt()

    private val parsedDetune
        get() = parseNum(call?.args?.getOrNull(1), 0.2)

    private var voices by value(parsedVoices)
    private var detune by value(parsedDetune)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = List(2) { parseNumOrNull(call?.args?.getOrNull(it)) != null }
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "$voices, ${detune.fmt()}"
        } else {
            voices.toString()
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
            while (texts.size < 2) texts.add(null)
            // voices is an integer param — no decimal point
            put(texts, 0, voices.toString())
            put(texts, 1, detune.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(voices.toString())
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
        voices = parsedVoices
        detune = parsedDetune
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        commitValue()
    }

    private fun applyPreset(preset: SuperSawPreset) {
        voices = preset.voices
        detune = preset.detune
        dirty += 0..1
        formCtrl.resetAllFields()
        resetCounter++
        liveUpdate()
    }

    // ── Render ───────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                key = "supersaw-editor"
                css { minWidth = 400.px }
                toolHeaderWithInfo("Super Saw", props.toolCtx, infoPopup)
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
            key = "supersaw-editor-content-$resetCounter"

            // Presets set both params, so they only make sense in whole-call mode
            if (call != null) {
                div {
                    key = "supersaw-presets"
                    css {
                        display = Display.flex
                        flexWrap = FlexWrap.wrap
                        gap = 4.px
                        marginBottom = 8.px
                    }
                    val matchedPreset = PRESETS.find {
                        it.voices == voices && it.detune == detune
                    }

                    for (preset in PRESETS) {
                        val isSelected = preset === matchedPreset
                        ui.mini.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                            css { whiteSpace = WhiteSpace.nowrap }
                            onClick { applyPreset(preset) }
                            +preset.name
                        }
                    }

                    val isCustom = matchedPreset == null
                    ui.mini.givenNot(isCustom) { basic }.given(isCustom) { with(laf.styles.goldButton()) }.button {
                        css { whiteSpace = WhiteSpace.nowrap }
                        +"Custom"
                    }
                }

                ui.divider()
            }

            ui.form {
                key = "supersaw-editor-form"
                ui.two.stackable.fields {
                    key = "supersaw-editor-fields"
                    UiInputField(voices, { voices = it; dirty += 0; liveUpdate() }) {
                        domKey("voices")
                        step(1)
                        label {
                            +"Voices"
                            paramInfoIcon("voices", props.toolCtx, infoPopup)
                        }
                    }
                    if (call != null) {
                        UiInputField(detune, { detune = it; dirty += 1; liveUpdate() }) {
                            domKey("spread")
                            step(0.01)
                            label {
                                +"Spread"
                                paramInfoIcon("spread", props.toolCtx, infoPopup)
                            }
                        }
                    }
                }
            }
        }
    }
}
