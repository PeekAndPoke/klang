/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.sprudel.SprudelBodyMaterials
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
import io.peekandpoke.kraft.popups.PopupsManager.Companion.popups
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.SemanticIconFn
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.FontStyle
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.fontStyle
import kotlinx.css.gap
import kotlinx.css.marginBottom
import kotlinx.css.minWidth
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

// ── Tool singleton ────────────────────────────────────────────────────────────

/** [KlangUiToolEmbeddable] for selecting a `body()` resonator material. */
object SprudelBodyEditorTool : KlangUiToolEmbeddable {
    override val title: String = "Body"

    override val iconFn: SemanticIconFn = { chart_area }

    override fun FlowContent.render(ctx: KlangUiToolContext) {
        SprudelBodyEditorComp(ctx, embedded = false)
    }

    override fun FlowContent.renderEmbedded(ctx: KlangUiToolContext) {
        SprudelBodyEditorComp(ctx, embedded = true)
    }
}

// ── Entry-point helpers ───────────────────────────────────────────────────────

@Suppress("FunctionName")
private fun Tag.SprudelBodyEditorComp(toolCtx: KlangUiToolContext, embedded: Boolean) =
    comp(SprudelBodyEditorComp.Props(toolCtx, embedded)) { SprudelBodyEditorComp(it) }

// ── Component ─────────────────────────────────────────────────────────────────

private class SprudelBodyEditorComp(ctx: Ctx<Props>) : Component<SprudelBodyEditorComp.Props>(ctx) {

    data class Props(val toolCtx: KlangUiToolContext, val embedded: Boolean = false)

    // ── Available materials ───────────────────────────────────────────────────

    private val materials = SprudelBodyMaterials.names

    private val laf by subscribingTo(KlangTheme)
    private val autoUpdate by subscribingTo(KlangToolAutoUpdate)
    private val infoPopup = HoverPopupCtrl(popups)

    // ── Parse current value from raw source text ──────────────────────────────

    private val initialValue = props.toolCtx.currentValue ?: ""

    private val parsed
        get() = run {
            val raw = initialValue.trim().removePrefix("\"").removeSuffix("\"").lowercase()
            if (raw in materials) raw else materials.first()
        }

    private var material by value(parsed)

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildValue(): String = "\"$material\""

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
        material = parsed
        props.toolCtx.onCommit(initialValue)
    }

    private fun onCommit() {
        props.toolCtx.onCommit(buildValue())
    }

    // ── Render ────────────────────────────────────────────────────────────────

    override fun VDom.render() {
        if (props.embedded) {
            renderContent()
        } else {
            ui.segment {
                css { minWidth = 400.px }
                toolHeaderWithInfo("Body", props.toolCtx, infoPopup)
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
            key = "body-editor-content"

            div {
                css {
                    display = Display.flex
                    flexWrap = FlexWrap.wrap
                    gap = 6.px
                    marginBottom = 8.px
                }
                for (mat in materials) {
                    val isSelected = material == mat
                    ui.givenNot(isSelected) { basic }.given(isSelected) { with(laf.styles.goldButton()) }.button {
                        key = mat
                        onClick { material = mat; liveUpdate() }
                        +mat
                    }
                }
            }

            // Character description of the selected material.
            div {
                css {
                    fontStyle = FontStyle.italic
                    marginBottom = 8.px
                }
                +(SprudelBodyMaterials.descriptions[material] ?: "")
            }

            ui.divider {}
            div {
                css { if (!props.embedded) marginBottom = 1.rem }
                renderResponseSvg()
            }
        }
    }

    // ── SVG modal-response curve ───────────────────────────────────────────────

    /**
     * Sum of the material's parallel bandpass modes — the "fingerprint" the ear reads. Each mode is
     * a constant-skirt BPF (peak 1 at its centre) scaled by its linear `db` gain; the sum is
     * normalized to its own max so every material fills the plot. Log-frequency axis (20 Hz–20 kHz).
     */
    private fun FlowContent.renderResponseSvg() {
        val w = 400.0
        val h = 110.0
        val padL = 20.0
        val padR = 20.0
        val padT = 10.0
        val padB = 20.0
        val drawW = w - padL - padR
        val drawH = h - padT - padB

        val logMin = ln(20.0)
        val logMax = ln(20000.0)

        val modes = SprudelBodyMaterials.modesFor(material) ?: emptyList()

        fun response(f: Double): Double {
            var sum = 0.0
            for (mode in modes) {
                val q = mode.q.coerceAtLeast(0.1)
                val r = f / mode.freq
                val bw = 1.0 / q
                val bp = (bw * r) / sqrt((r * r - 1.0).pow(2.0) + (bw * r).pow(2.0))
                sum += bp * 10.0.pow(mode.db / 20.0)
            }
            return sum
        }

        // Sample once, find the peak, then map to the plot.
        val step = 2.0
        val samples = ArrayList<Pair<Double, Double>>()
        run {
            var x = padL
            while (x <= padL + drawW) {
                val f = exp(logMin + (x - padL) / drawW * (logMax - logMin))
                samples.add(x to response(f))
                x += step
            }
        }
        val maxResp = (samples.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1e-9)

        fun yOf(resp: Double) = padT + drawH * (1.0 - (resp / maxResp).coerceIn(0.0, 1.0))

        val curve = buildString {
            samples.forEachIndexed { i, (x, resp) ->
                append(if (i == 0) "M" else "L")
                append("$x ${yOf(resp)} ")
            }
        }
        val fill = buildString {
            append("M$padL ${padT + drawH} ")
            samples.forEach { (x, resp) -> append("L$x ${yOf(resp)} ") }
            append("L${padL + drawW} ${padT + drawH} Z")
        }

        fun freqX(freq: Double) = padL + (ln(freq) - logMin) / (logMax - logMin) * drawW

        val x100 = freqX(100.0)
        val x1k = freqX(1000.0)
        val x10k = freqX(10000.0)

        svgRoot(viewBox = "0 0 $w $h") {
            svgRect(padL, padT, drawW, drawH, fill = "rgba(0,0,0,0.2)", rx = "2")
            // Frequency grid
            svgLine(x100, padT, x100, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x1k, padT, x1k, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            svgLine(x10k, padT, x10k, padT + drawH, stroke = "rgba(255,255,255,0.15)", strokeWidth = "0.5")
            // A faint tick at each modal centre — shows where the resonances sit.
            for (mode in modes) {
                val mx = freqX(mode.freq)
                svgLine(mx, padT, mx, padT + drawH, stroke = "rgba(255,255,255,0.12)", strokeWidth = "0.5")
            }
            // Response
            svgPath(d = fill, fill = "${laf.gold}26")
            svgPath(d = curve.trim(), stroke = laf.gold, strokeWidth = "1")
            // Frequency labels
            svgText(x100, h - 4, "100", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(x1k, h - 4, "1k", fill = "#ccc", fontSize = "7", textAnchor = "middle")
            svgText(x10k, h - 4, "10k", fill = "#ccc", fontSize = "7", textAnchor = "middle")
        }
    }
}
