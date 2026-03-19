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
import de.peekandpoke.ultra.html.onMouseDown
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.KlangUiToolContext
import io.peekandpoke.klang.ui.KlangUiToolEmbeddable
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
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

/** [KlangUiToolEmbeddable] for editing a pan value string (0=left, 0.5=center, 1=right). */
object StrudelPanEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Pan Editor"

    override val iconFn: SemanticIconFn = { adjust }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelPanEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelPanEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelPanEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelPanEditorComp.Props(toolCtx, embedded)) { StrudelPanEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelPanEditorComp(ctx: Ctx<Props>) : Component<StrudelPanEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            raw.toDoubleOrNull() ?: 0.5
        }

    private var pan by value(parsed)

    private var resetCounter by value(0)

    /** The bar element captured on mousedown — used by document-level move/up listeners. */
    private var dragTarget: Element? = null

    private val onDocumentMouseMove: (Event) -> Unit = { e ->
        val bar = dragTarget
        if (bar != null) {
            val me = e as MouseEvent
            val rect = bar.getBoundingClientRect()
            val ratio = ((me.clientX.toDouble() - rect.left) / rect.width).coerceIn(0.0, 1.0)
            pan = ratio.roundTo(2)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Double.fmt(): String =
        toFixed(3).trimEnd('0').trimEnd('.')

    private fun buildValue(): String = "\"${pan.fmt()}\""

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
        pan = parsed
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
        pan = ratio.roundTo(2)
        liveUpdate()
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
                ui.small.header { +"Pan" }
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
            key = "pan-editor-content-$resetCounter"

            ui.form {
                UiInputField(pan, { pan = it; liveUpdate() }) {
                    domKey("pan")
                    step(0.01)
                    label("Pan")
                }
            }
            ui.divider {}
            renderPanBar()
        }
    }

    // ── Interactive pan bar ───────────────────────────────────────────────────

    private fun FlowContent.renderPanBar() {
        val centerPct = 50.0
        val clampedPan = pan.coerceIn(0.0, 1.0)

        // Fill from center toward the pan direction
        val fillLeftPct: Double
        val fillWidthPct: Double
        if (clampedPan >= 0.5) {
            fillLeftPct = centerPct
            fillWidthPct = (clampedPan - 0.5) * 2.0 * centerPct
        } else {
            fillWidthPct = (0.5 - clampedPan) * 2.0 * centerPct
            fillLeftPct = centerPct - fillWidthPct
        }

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

            // Fill bar (from center)
            div {
                css {
                    position = Position.absolute
                    left = LinearDimension("$fillLeftPct%")
                    top = 0.px
                    height = 100.pct
                    width = LinearDimension("$fillWidthPct%")
                    backgroundColor = Color(laf.gold)
                    borderRadius = 4.px
                    pointerEvents = PointerEvents.none
                }
            }

            // Center line at pan=0.5
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
                for (i in 0..10) {
                    val v = i / 10.0
                    val pct = v * 100.0
                    val isMajor = i % 5 == 0
                    // Tick line
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
                    // Labels at 0, 0.5, 1
                    if (isMajor) {
                        val label = when (i) {
                            0 -> "L"
                            5 -> "C"
                            10 -> "R"
                            else -> ""
                        }
                        span {
                            css {
                                position = Position.absolute
                                left = LinearDimension("$pct%")
                                top = 7.px
                                fontSize = 9.px
                                color = Color("#aaa")
                                put("transform", "translateX(-50%)")
                            }
                            +label
                        }
                    }
                }
            }
        }

        // Spacer for tick marks and labels
        div { css { height = 22.px } }
    }
}
