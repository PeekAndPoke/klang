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
import io.peekandpoke.ultra.html.onMouseDown
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.LinearDimension
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.UserSelect
import kotlinx.css.backgroundColor
import kotlinx.css.borderRadius
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.left
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.paddingTop
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.right
import kotlinx.css.top
import kotlinx.css.userSelect
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

// ── Tool instances ───────────────────────────────────────────────────────────

/** Editor for distortion amount (0–2). */
object SprudelDistortAmountEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Distortion Amount Editor",
    iconFn = { bolt },
    fieldLabel = "Distortion Amount",
    maxValue = 2.0,
    defaultValue = 0.5,
    step = 0.01,
    centerValue = null,
)

/** Editor for room size (0–10). */
object SprudelRoomSizeEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Room Size Editor",
    iconFn = { microphone },
    fieldLabel = "Room Size",
    maxValue = 10.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for delay time in seconds (0–2). */
object SprudelDelayTimeEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Delay Time Editor",
    iconFn = { history },
    fieldLabel = "Time (s)",
    maxValue = 2.0,
    defaultValue = 0.25,
    step = 0.01,
    centerValue = null,
)

/** Editor for delay feedback (0–1). */
object SprudelDelayFeedbackEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Delay Feedback Editor",
    iconFn = { history },
    fieldLabel = "Feedback",
    maxValue = 1.0,
    defaultValue = 0.5,
    step = 0.01,
    centerValue = null,
)

// ── Filter frequency editors ───────────────────────────────────────────────────

/** Editor for notch filter centre frequency (20–20000 Hz). */
object SprudelNotchFreqEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Frequency Editor",
    iconFn = { filter },
    fieldLabel = "Frequency (Hz)",
    maxValue = 20000.0,
    defaultValue = 1000.0,
    step = 10.0,
    centerValue = null,
)

// ── Filter resonance/Q editors ───────────────────────────────────────────────

/** Editor for LP filter resonance (Q factor, 0–50). */
object SprudelLpResonanceEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Low-Pass Resonance Editor",
    iconFn = { filter },
    fieldLabel = "Resonance (Q)",
    maxValue = 50.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for HP filter resonance (Q factor, 0–50). */
object SprudelHpResonanceEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "High-Pass Resonance Editor",
    iconFn = { filter },
    fieldLabel = "Resonance (Q)",
    maxValue = 50.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for BP filter Q (0–50). */
object SprudelBpQEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Band-Pass Resonance Editor",
    iconFn = { filter },
    fieldLabel = "Resonance (Q)",
    maxValue = 50.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for notch filter Q (0–50). */
object SprudelNotchQEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Resonance Editor",
    iconFn = { filter },
    fieldLabel = "Resonance (Q)",
    maxValue = 50.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for normalized resonance (0–1). */
object SprudelNResonanceEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Normalized Resonance Editor",
    iconFn = { filter },
    fieldLabel = "Resonance",
    maxValue = 1.0,
    defaultValue = 0.5,
    step = 0.01,
    centerValue = null,
)

// ── Filter envelope depth editors ────────────────────────────────────────────

/** Editor for LP filter envelope depth (unitless ratio). */
object SprudelLpEnvEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Low-Pass Env Depth Editor",
    iconFn = { filter },
    fieldLabel = "Depth (st)",
    defaultValue = 7.0,
    step = 1.0,
    centerValue = null,
)

/** Editor for HP filter envelope depth (unitless ratio). */
object SprudelHpEnvEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "High-Pass Env Depth Editor",
    iconFn = { filter },
    fieldLabel = "Depth (st)",
    defaultValue = 7.0,
    step = 1.0,
    centerValue = null,
)

/** Editor for BP filter envelope depth (unitless ratio). */
object SprudelBpEnvEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Band-Pass Env Depth Editor",
    iconFn = { filter },
    fieldLabel = "Depth (st)",
    defaultValue = 7.0,
    step = 1.0,
    centerValue = null,
)

/** Editor for notch filter envelope depth (unitless ratio). */
object SprudelNfEnvEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Env Depth Editor",
    iconFn = { filter },
    fieldLabel = "Depth (st)",
    defaultValue = 7.0,
    step = 1.0,
    centerValue = null,
)

// ── Notch filter envelope shape editors ──────────────────────────────────────────

/** Editor for notch filter attack time (0–5 seconds). */
object SprudelNfAttackEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Attack Editor",
    iconFn = { filter },
    fieldLabel = "Attack (s)",
    maxValue = 5.0,
    defaultValue = 0.01,
    step = 0.001,
    centerValue = null,
)

/** Editor for notch filter decay time (0–5 seconds). */
object SprudelNfDecayEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Decay Editor",
    iconFn = { filter },
    fieldLabel = "Decay (s)",
    maxValue = 5.0,
    defaultValue = 0.1,
    step = 0.001,
    centerValue = null,
)

/** Editor for notch filter sustain level (0–1). */
object SprudelNfSustainEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Sustain Editor",
    iconFn = { filter },
    fieldLabel = "Sustain",
    maxValue = 1.0,
    defaultValue = 0.8,
    step = 0.01,
    centerValue = null,
)

/** Editor for notch filter release time (0–5 seconds). */
object SprudelNfReleaseEditorTool : KlangUiToolEmbeddable by SprudelNumericEditorTool(
    title = "Notch Release Editor",
    iconFn = { filter },
    fieldLabel = "Release (s)",
    maxValue = 5.0,
    defaultValue = 0.3,
    step = 0.001,
    centerValue = null,
)

// ── Configurable tool ────────────────────────────────────────────────────────

/**
 * A configurable [KlangUiToolEmbeddable] for editing a single numeric value.
 *
 * Instantiate with different [title], [maxValue], [step], etc. to create
 * editors for room size, delay time, delay feedback, and similar parameters.
 */
class SprudelNumericEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
    val fieldLabel: String,
    val defaultValue: Double,
    val step: Double,
    /** If non-null, the drag bar uses this as the lower bound. */
    val minValue: Double? = null,
    /** If non-null, the drag bar uses this as the upper bound. When null the drag bar is hidden. */
    val maxValue: Double? = null,
    /** If non-null, draws a center marker line at this value. */
    val centerValue: Double? = null,
) : KlangUiToolEmbeddable {

    /** Scalar single-value editor: opens as an inline popover (C0.3 popover tier). */
    override val prefersPopover: Boolean get() = true

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelNumericEditorComp(ctx, this@SprudelNumericEditorTool, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelNumericEditorComp(ctx, this@SprudelNumericEditorTool, embedded = true)
    }
}

// ── Entry-point helpers ──────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelNumericEditorComp(
    toolCtx: KlangUiToolContext,
    config: SprudelNumericEditorTool,
    embedded: Boolean,
) = comp(SprudelNumericEditorComp.Props(toolCtx, config, embedded)) { SprudelNumericEditorComp(it) }

// ── Component ────────────────────────────────────────────────────────────────

private class SprudelNumericEditorComp(ctx: Ctx<Props>) : Component<SprudelNumericEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val config: SprudelNumericEditorTool,
        val embedded: Boolean = false,
    )

    private val cfg get() = props.config

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            raw.toDoubleOrNull() ?: cfg.defaultValue
        }

    private var current by value(parsed)

    private var resetCounter by value(0)

    private var dragTarget: Element? = null

    /** True once the active drag has actually moved; plain clicks stay unaffected. */
    private var dragMoved = false

    private val barMin get() = cfg.minValue ?: 0.0
    private val barMax get() = cfg.maxValue
    private val hasBar get() = barMax != null

    private val onDocumentMouseMove: (Event) -> Unit = { e ->
        val bar = dragTarget
        val max = barMax
        if (bar != null && max != null) {
            dragMoved = true
            val me = e as MouseEvent
            val rect = bar.getBoundingClientRect()
            val ratio = ((me.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
            current = (barMin + ratio * (max - barMin)).roundTo(2)
            liveUpdate()
        }
    }

    private val onDocumentMouseUp: (Event) -> Unit = {
        val moved = dragMoved
        dragTarget = null
        dragMoved = false
        document.removeEventListener("mousemove", onDocumentMouseMove)
        document.removeEventListener("mouseup", onDocumentMouseUp)
        if (moved) {
            suppressNextClick()
        }
    }

    private val onSuppressedClick: (Event) -> Unit = { e ->
        e.stopPropagation()
        removeSuppressClickListener()
    }

    private fun removeSuppressClickListener() {
        document.removeEventListener("click", onSuppressedClick, true)
    }

    /**
     * A drag that ends with the pointer outside the popover makes the browser fire a synthetic
     * click on the common ancestor (body). That click bypasses the popover's stopPropagation
     * guard and would reach kraft PopupsStage's document click listener, closing the popover.
     * Swallow exactly that one click with a one-shot capture-phase listener; the timeout
     * removes it again in case no click fires at all.
     */
    private fun suppressNextClick() {
        document.addEventListener("click", onSuppressedClick, true)
        window.setTimeout({ removeSuppressClickListener() }, 0)
    }

    init {
        lifecycle {
            onUnmount {
                dragTarget = null
                dragMoved = false
                document.removeEventListener("mousemove", onDocumentMouseMove)
                document.removeEventListener("mouseup", onDocumentMouseUp)
                removeSuppressClickListener()
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${current.fmt()}\""

    private val isInitialModified get() = initialValue != buildValue()
    private val isCurrentModified get() = (props.toolCtx.currentValue ?: "") != buildValue()

    private fun liveUpdate() {
        if (props.embedded || autoUpdate) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() {
        if (!props.embedded && autoUpdate && isInitialModified) {
            props.toolCtx.onCommit(initialValue)
        }
        props.toolCtx.onCancel()
    }

    private fun onReset() {
        current = parsed
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(initialValue)
        resetCounter++
    }

    private fun onCommit() {
        props.toolCtx.onCommit(buildValue())
    }

    private fun startDrag(e: MouseEvent) {
        val max = barMax ?: return
        val bar = e.currentTarget as? Element ?: return
        dragTarget = bar
        dragMoved = false
        val rect = bar.getBoundingClientRect()
        val ratio = ((e.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
        current = (barMin + ratio * (max - barMin)).roundTo(2)
        liveUpdate()
        document.addEventListener("mousemove", onDocumentMouseMove)
        document.addEventListener("mouseup", onDocumentMouseUp)
    }

    // ── Render ───────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                toolHeaderWithInfo(cfg.title, props.toolCtx, infoPopup)
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
            key = "numeric-editor-content-$resetCounter"

            ui.form {
                UiInputField(current, { current = it; liveUpdate() }) {
                    domKey("value")
                    step(cfg.step)
                    label {
                        +cfg.fieldLabel
                        paramInfoIcon(props.toolCtx.paramName, props.toolCtx, infoPopup)
                    }
                }
            }
            if (hasBar) {
                ui.divider {}
                renderBar()
            }
        }
    }

    // ── Interactive bar ─────────────────────────────────────────────────────

    private fun FlowContent.renderBar() {
        val max = barMax ?: return
        val min = barMin
        val range = max - min
        val fillPct = if (range > 0.0) ((current - min) / range * 100.0).coerceIn(0.0, 100.0) else 0.0

        div {
            css {
                position = Position.relative
                height = 28.px
                backgroundColor = Color("#e8e8e8")
                borderRadius = 4.px
                cursor = Cursor.pointer
                userSelect = UserSelect.none
                if (!props.embedded) marginBottom = 1.rem
            }
            onMouseDown { e -> e.preventDefault(); startDrag(e) }

            // Fill bar
            div {
                css {
                    position = Position.absolute
                    left = 0.px
                    top = 0.px
                    height = 100.pct
                    width = LinearDimension("$fillPct%")
                    backgroundColor = Color(laf.gold)
                    borderRadius = 4.px
                    pointerEvents = PointerEvents.none
                }
            }

            // Center line (if configured)
            val center = cfg.centerValue
            if (center != null && range > 0.0) {
                val centerPct = (center - min) / range * 100.0
                div {
                    css {
                        position = Position.absolute
                        left = LinearDimension("$centerPct%")
                        top = 0.px
                        height = 100.pct
                        width = 1.px
                        backgroundColor = Color("#888")
                        pointerEvents = PointerEvents.none
                    }
                }
            }

            // Tick marks and labels
            val tickCount = 20
            div {
                css {
                    position = Position.absolute
                    top = 100.pct
                    left = 0.px
                    right = 0.px
                    paddingTop = 1.px
                    pointerEvents = PointerEvents.none
                }
                for (i in 0..tickCount) {
                    val v = min + i.toDouble() / tickCount * range
                    val pct = i.toDouble() / tickCount * 100.0
                    val isMajor = i % 5 == 0
                    div {
                        css {
                            position = Position.absolute
                            left = LinearDimension("$pct%")
                            top = 0.px
                            width = 1.px
                            height = if (isMajor) 6.px else 3.px
                            backgroundColor = Color(if (isMajor) "#999" else "#ccc")
                        }
                    }
                    if (isMajor) {
                        span {
                            css {
                                position = Position.absolute
                                left = LinearDimension("$pct%")
                                top = 7.px
                                fontSize = 9.px
                                color = Color("#aaa")
                                put("transform", "translateX(-50%)")
                            }
                            +v.toFixed(1).removeSuffix(".0")
                        }
                    }
                }
            }
        }

        // Spacer for tick marks and labels
        div { css { height = 22.px } }
    }
}
