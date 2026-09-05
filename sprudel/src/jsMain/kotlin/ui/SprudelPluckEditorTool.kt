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
 * [KlangUiToolEmbeddable] for the per-param sndPluck(decay, brightness, pickPosition, stiffness) call.
 *
 * Two modes (C0.3 two-tool-tier design):
 * - Whole-call modal: when [KlangUiToolContext.call] is present, edits all four params of the
 *   host call (incl. presets) and commits the full argument list.
 * - Scalar fallback (embedded / sequence atom): edits a single decay value.
 */
object SprudelPluckEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Pluck Editor"

    override val iconFn: SemanticIconFn = { music }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelPluckEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelPluckEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helper ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelPluckEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelPluckEditorComp.Props(toolCtx, embedded)) { SprudelPluckEditorComp(it) }

// ── Presets ──────────────────────────────────────────────────────────────────

private data class PluckPreset(
    val name: String,
    val decay: Double,
    val brightness: Double,
    val pickPosition: Double,
    val stiffness: Double,
)

private val PRESETS = listOf(
    PluckPreset("Guitar", 0.996, 0.5, 0.5, 0.1),
    PluckPreset("Pizzicato", 0.93, 0.3, 0.5, 0.0),
    PluckPreset("Harp", 0.998, 0.7, 0.6, 0.05),
    PluckPreset("Sitar", 0.997, 0.8, 0.15, 0.4),
    PluckPreset("Banjo", 0.99, 0.9, 0.2, 0.3),
    PluckPreset("Koto", 0.995, 0.6, 0.3, 0.2),
    PluckPreset("Steel String", 0.996, 0.7, 0.4, 0.35),
    PluckPreset("Nylon String", 0.997, 0.3, 0.55, 0.0),
)

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelPluckEditorComp(ctx: Ctx<Props>) : Component<SprudelPluckEditorComp.Props>(ctx) {

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

    // Whole-call mode reads the params from the host call's args; scalar mode reads the single arg.
    private val parsedDecay
        get() = parseNum(call?.args?.getOrNull(0) ?: currentValue, 0.996)

    private val parsedBrightness
        get() = parseNum(call?.args?.getOrNull(1), 0.5)

    private val parsedPickPosition
        get() = parseNum(call?.args?.getOrNull(2), 0.5)

    private val parsedStiffness
        get() = parseNum(call?.args?.getOrNull(3), 0.0)

    private var decay by value(parsedDecay)
    private var brightness by value(parsedBrightness)
    private var pickPosition by value(parsedPickPosition)
    private var stiffness by value(parsedStiffness)

    // Slot bookkeeping: an untouched arg that fails the parse (pattern, variable, expression)
    // must never be overwritten, and untouched absent slots stay absent (engine defaults apply).
    private val parseable: List<Boolean> = List(4) { parseNumOrNull(call?.args?.getOrNull(it)) != null }
    private val dirty = mutableSetOf<Int>()
    private var hasCommitted = false

    private var resetCounter by value(0)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String =
        if (call != null) {
            "${decay.fmt()}, ${brightness.fmt()}, ${pickPosition.fmt()}, ${stiffness.fmt()}"
        } else {
            decay.fmt()
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
            put(texts, 0, decay.fmt())
            put(texts, 1, brightness.fmt())
            put(texts, 2, pickPosition.fmt())
            put(texts, 3, stiffness.fmt())
            c.onCommitCall(texts)
        } else {
            props.toolCtx.onCommit(decay.fmt())
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
        decay = parsedDecay
        brightness = parsedBrightness
        pickPosition = parsedPickPosition
        stiffness = parsedStiffness
        formCtrl.resetAllFields()
        commitValue()
        resetCounter++
    }

    private fun onCommit() {
        currentValue = buildValue()
        commitValue()
    }

    private fun applyPreset(preset: PluckPreset) {
        decay = preset.decay
        brightness = preset.brightness
        pickPosition = preset.pickPosition
        stiffness = preset.stiffness
        dirty += 0..3
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
                key = "pluck-editor"
                css { minWidth = 480.px }
                toolHeaderWithInfo("Pluck", props.toolCtx, infoPopup)
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
            key = "pluck-editor-content-$resetCounter"

            // Presets set all four params, so they only make sense in whole-call mode
            if (call != null) {
                div {
                    key = "pluck-presets"
                    css {
                        display = Display.flex
                        flexWrap = FlexWrap.wrap
                        gap = 4.px
                        marginBottom = 8.px
                    }
                    val matchedPreset = PRESETS.find {
                        it.decay == decay && it.brightness == brightness &&
                                it.pickPosition == pickPosition && it.stiffness == stiffness
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
                key = "pluck-editor-form"
                ui.four.stackable.fields {
                    key = "pluck-editor-fields"
                    UiInputField(decay, { decay = it; dirty += 0; liveUpdate() }) {
                        domKey("decay")
                        step(0.001)
                        label {
                            +"Decay"
                            paramInfoIcon("decay", props.toolCtx, infoPopup)
                        }
                    }
                    if (call != null) {
                        UiInputField(brightness, { brightness = it; dirty += 1; liveUpdate() }) {
                            domKey("brightness")
                            step(0.05)
                            label {
                                +"Brightness"
                                paramInfoIcon("brightness", props.toolCtx, infoPopup)
                            }
                        }
                        UiInputField(pickPosition, { pickPosition = it; dirty += 2; liveUpdate() }) {
                            domKey("pickPosition")
                            step(0.05)
                            label {
                                +"Pick Pos"
                                paramInfoIcon("pickPosition", props.toolCtx, infoPopup)
                            }
                        }
                        UiInputField(stiffness, { stiffness = it; dirty += 3; liveUpdate() }) {
                            domKey("stiffness")
                            step(0.05)
                            label {
                                +"Stiffness"
                                paramInfoIcon("stiffness", props.toolCtx, infoPopup)
                            }
                        }
                    }
                }
            }
        }
    }
}
