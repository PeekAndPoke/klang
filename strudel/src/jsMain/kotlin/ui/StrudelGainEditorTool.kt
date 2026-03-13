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

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a single gain value string. */
object StrudelGainEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Gain Editor"

    override val iconFn: SemanticIconFn = { volume_up }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelGainEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelGainEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelGainEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelGainEditorComp.Props(toolCtx, embedded)) { StrudelGainEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelGainEditorComp(ctx: Ctx<Props>) : Component<StrudelGainEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            raw.toDoubleOrNull() ?: 1.0
        }

    private var gain by value(parsed)

    private var resetCounter by value(0)

    /** The bar element captured on mousedown — used by document-level move/up listeners. */
    private var dragTarget: Element? = null

    private val onDocumentMouseMove: (Event) -> Unit = { e ->
        val bar = dragTarget
        if (bar != null) {
            val me = e as MouseEvent
            val rect = bar.getBoundingClientRect()
            val ratio = ((me.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
            gain = (ratio * 2.0).roundTo(2)
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
                // Clean up in case the component unmounts mid-drag
                dragTarget = null
                document.removeEventListener("mousemove", onDocumentMouseMove)
                document.removeEventListener("mouseup", onDocumentMouseUp)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${gain.fmt()}\""

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
        gain = parsed
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
        // Set initial value from click position
        val rect = bar.getBoundingClientRect()
        val ratio = ((e.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
        gain = (ratio * 2.0).roundTo(2)
        liveUpdate()
        // Attach document-level listeners so drag continues outside the bar
        document.addEventListener("mousemove", onDocumentMouseMove)
        document.addEventListener("mouseup", onDocumentMouseUp)
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                ui.small.header { +"Gain" }
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
            key = "gain-editor-content-$resetCounter"

            ui.form {
                UiInputField(gain, { gain = it; liveUpdate() }) {
                    domKey("gain")
                    step(0.01)
                    label("Gain")
                }
            }
            ui.divider {}
            renderGainBar()
        }
    }

    // ── Interactive gain bar ──────────────────────────────────────────────────

    private fun FlowContent.renderGainBar() {
        val maxGain = 2.0
        val fillPct = (gain / maxGain * 100.0).coerceIn(0.0, 100.0)
        val centerPct = 1.0 / maxGain * 100.0 // 50%

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

            // Center line at gain=1.0
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

            // Tick marks and labels
            div {
                css {
                    position = Position.absolute
                    top = 100.pct
                    left = 0.px
                    right = 0.px
                    paddingTop = 1.px
                    pointerEvents = PointerEvents.none
                }
                for (i in 0..20) {
                    val v = i / 10.0
                    val pct = v / maxGain * 100.0
                    val isMajor = i % 5 == 0
                    // Tick line
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
                    // Label for major ticks (0, 0.5, 1, 1.5, 2)
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
