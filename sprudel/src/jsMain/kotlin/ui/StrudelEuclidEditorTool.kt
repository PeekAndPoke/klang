package io.peekandpoke.klang.sprudel.ui

import de.peekandpoke.kraft.components.Component
import de.peekandpoke.kraft.components.Ctx
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.forms.formController
import de.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.semanticui.SemanticIconFn
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.ui.*
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.ui.feel.KlangTheme
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for editing a euclidean rhythm string (pulses:steps:rotation). */
object StrudelEuclidEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Euclid Editor"

    override val iconFn: SemanticIconFn = { bullseye }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        StrudelEuclidEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        StrudelEuclidEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.StrudelEuclidEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(StrudelEuclidEditorComp.Props(toolCtx, embedded)) { StrudelEuclidEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class StrudelEuclidEditorComp(ctx: Ctx<Props>) : Component<StrudelEuclidEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Parse current value from raw source text ──────────────────────────────

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    private val formCtrl = formController()

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"")
            val parts = raw.split(":").map { it.toIntOrNull() }
            Triple(
                parts.getOrNull(0) ?: 3,   // pulses
                parts.getOrNull(1) ?: 8,   // steps
                parts.getOrNull(2) ?: 0,   // rotation
            )
        }

    private var pulses by value(parsed.first)
    private var steps by value(parsed.second)
    private var rotation by value(parsed.third)

    private var resetCounter by value(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$pulses:$steps:$rotation\""

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
        pulses = parsed.first
        steps = parsed.second
        rotation = parsed.third
        formCtrl.resetAllFields()
        props.toolCtx.onCommit(initialValue)
        resetCounter++
    }

    private fun onCommit() {
        props.toolCtx.onCommit(buildValue())
    }

    /** Compute euclidean rhythm pattern: returns list of booleans for each step. */
    private fun euclideanPattern(p: Int, s: Int, r: Int): List<Boolean> {
        if (s <= 0) return emptyList()
        val clampedP = p.coerceIn(0, s)
        val base = List(s) { i -> (i * clampedP % s) < clampedP }
        return if (r == 0) {
            base
        } else {
            val rot = r % s
            base.drop(rot) + base.take(rot)
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                toolHeaderWithInfo("Euclidean Rhythm", props.toolCtx, infoPopup)
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
            key = "euclid-editor-content-$resetCounter"

            ui.form {
                ui.three.stackable.fields {
                    UiInputField(pulses.toDouble(), { pulses = it.toInt().coerceAtLeast(0); liveUpdate() }) {
                        domKey("pulses")
                        step(1.0)
                        label("Pulses")
                    }
                    UiInputField(steps.toDouble(), { steps = it.toInt().coerceAtLeast(1); liveUpdate() }) {
                        domKey("steps")
                        step(1.0)
                        label("Steps")
                    }
                    UiInputField(rotation.toDouble(), { rotation = it.toInt().coerceAtLeast(0); liveUpdate() }) {
                        domKey("rotation")
                        step(1.0)
                        label("Rotation")
                    }
                }
            }
            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderEuclidSvg()
            }
        }
    }

    // ── SVG ───────────────────────────────────────────────────────────────────

    private fun FlowContent.renderEuclidSvg() {
        val w = 220.0
        val h = 220.0
        val cx = 110.0
        val cy = 110.0
        val circleR = 80.0
        val dotR = 7.0

        val pattern = euclideanPattern(pulses, steps, rotation)
        val safeSteps = steps.coerceAtLeast(1)

        svgRoot(viewBox = "0 0 $w $h") {
            svgCircle(cx, cy, circleR, fill = "none", stroke = "#e8e8e8", strokeWidth = "1")
            for (i in 0 until safeSteps) {
                // Start at top (12 o'clock) = -PI/2, go clockwise
                val angle = -PI / 2.0 + 2.0 * PI * i / safeSteps
                val dx = cx + circleR * cos(angle)
                val dy = cy + circleR * sin(angle)
                val active = pattern.getOrElse(i) { false }
                val fill = if (active) laf.gold else "#e8e8e8"
                val stroke = if (active) laf.gold else "#ccc"
                svgCircle(dx, dy, dotR, fill = fill, stroke = stroke, strokeWidth = "1.5")
            }
        }
    }
}
