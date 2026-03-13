package io.peekandpoke.klang.strudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.forms.formController
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.toFixed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.html.onMouseDown
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.browser.document
import kotlinx.css.*
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import org.w3c.dom.Element
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent

// ── Tool instances ───────────────────────────────────────────────────────────

/** Editor for room size (0–10). */
object StrudelRoomSizeEditorTool : KlangUiToolEmbeddable by StrudelNumericEditorTool(
    title = "Room Size Editor",
    iconFn = { microphone },
    fieldLabel = "Room Size",
    maxValue = 10.0,
    defaultValue = 1.0,
    step = 0.1,
    centerValue = null,
)

/** Editor for delay time in seconds (0–2). */
object StrudelDelayTimeEditorTool : KlangUiToolEmbeddable by StrudelNumericEditorTool(
    title = "Delay Time Editor",
    iconFn = { history },
    fieldLabel = "Time (s)",
    maxValue = 2.0,
    defaultValue = 0.25,
    step = 0.01,
    centerValue = null,
)

/** Editor for delay feedback (0–1). */
object StrudelDelayFeedbackEditorTool : KlangUiToolEmbeddable by StrudelNumericEditorTool(
    title = "Delay Feedback Editor",
    iconFn = { history },
    fieldLabel = "Feedback",
    maxValue = 1.0,
    defaultValue = 0.5,
    step = 0.01,
    centerValue = null,
)

// ── Configurable tool ────────────────────────────────────────────────────────

/**
 * A configurable [KlangUiToolEmbeddable] for editing a single numeric value.
 *
 * Instantiate with different [title], [maxValue], [step], etc. to create
 * editors for room size, delay time, delay feedback, and similar parameters.
 */
class StrudelNumericEditorTool(
    override val title: String,
    override val iconFn: SemanticIconFn,
    val fieldLabel: String,
    val maxValue: Double,
    val defaultValue: Double,
    val step: Double,
    /** If non-null, draws a center marker line at this value. */
    val centerValue: Double?,
) : KlangUiToolEmbeddable {

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelNumericEditorComp(ctx, this@StrudelNumericEditorTool, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelNumericEditorComp(ctx, this@StrudelNumericEditorTool, embedded = true)
    }
}

// ── Entry-point helpers ──────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelNumericEditorComp(
    toolCtx: KlangUiToolContext,
    config: StrudelNumericEditorTool,
    embedded: Boolean,
) = comp(StrudelNumericEditorComp.Props(toolCtx, config, embedded)) { StrudelNumericEditorComp(it) }

// ── Component ────────────────────────────────────────────────────────────────

private class StrudelNumericEditorComp(ctx: Ctx<Props>) : Component<StrudelNumericEditorComp.Props>(ctx) {

    data class Props(
        val toolCtx: KlangUiToolContext,
        val config: StrudelNumericEditorTool,
        val embedded: Boolean = false,
    )

    private val cfg get() = props.config

    private val laf by subscribingTo(KlangTheme)

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

    private val onDocumentMouseMove: (Event) -> Unit = { e ->
        val bar = dragTarget
        if (bar != null) {
            val me = e as MouseEvent
            val rect = bar.getBoundingClientRect()
            val ratio = ((me.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
            current = (ratio * cfg.maxValue).roundTo(2)
            liveUpdate()
        }
    }

    private val onDocumentMouseUp: (Event) -> Unit = {
        dragTarget = null
        document.removeEventListener("mousemove", onDocumentMouseMove)
        document.removeEventListener("mouseup", onDocumentMouseUp)
    }

    init {
        lifecycle {
            onUnmount {
                dragTarget = null
                document.removeEventListener("mousemove", onDocumentMouseMove)
                document.removeEventListener("mouseup", onDocumentMouseUp)
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
        if (props.embedded) {
            props.toolCtx.onCommit(buildValue())
        }
    }

    private fun onCancel() {
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
        val bar = e.currentTarget as? Element ?: return
        dragTarget = bar
        val rect = bar.getBoundingClientRect()
        val ratio = ((e.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
        current = (ratio * cfg.maxValue).roundTo(2)
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
                ui.small.header { +cfg.title }
                renderContent()
                ui.divider {}
                div {
                    css {
                        display = Display.flex
                        justifyContent = JustifyContent.flexEnd
                        gap = 8.px
                    }
                    ui.basic.button {
                        onClick { onCancel() }
                        icon.times()
                        +"Cancel"
                    }
                    ui.basic.givenNot(isInitialModified) { disabled }.button {
                        onClick { onReset() }
                        icon.undo()
                        +"Reset"
                    }
                    ui.black.givenNot(isCurrentModified) { disabled }.button {
                        onClick { onCommit() }
                        icon.check()
                        +"Update"
                    }
                }
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
                    label(cfg.fieldLabel)
                }
            }
            ui.divider {}
            renderBar()
        }
    }

    // ── Interactive bar ─────────────────────────────────────────────────────

    private fun FlowContent.renderBar() {
        val fillPct = (current / cfg.maxValue * 100.0).coerceIn(0.0, 100.0)

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
                    width = LinearDimension("${fillPct}%")
                    backgroundColor = Color(laf.gold)
                    borderRadius = 4.px
                    pointerEvents = PointerEvents.none
                }
            }

            // Center line (if configured)
            val center = cfg.centerValue
            if (center != null) {
                val centerPct = center / cfg.maxValue * 100.0
                div {
                    css {
                        position = Position.absolute
                        left = LinearDimension("${centerPct}%")
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
                    val v = i.toDouble() / tickCount * cfg.maxValue
                    val pct = i.toDouble() / tickCount * 100.0
                    val isMajor = i % 5 == 0
                    div {
                        css {
                            position = Position.absolute
                            left = LinearDimension("${pct}%")
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
                                left = LinearDimension("${pct}%")
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
