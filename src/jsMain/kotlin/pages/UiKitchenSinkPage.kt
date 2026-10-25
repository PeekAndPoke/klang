/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.comp.VolumeKnob
import io.peekandpoke.klang.comp.activeVoicesGauge
import io.peekandpoke.klang.comp.renderMotorHeatGauge
import io.peekandpoke.klang.comp.roundCylindersGauge
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangKnob
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangPanel
import io.peekandpoke.klang.ui.comp.YesNoModal
import io.peekandpoke.klang.ui.feel.KlangLookLayers
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.css.px
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.UiKitchenSinkPage() = comp {
    UiKitchenSinkPage(it)
}

/**
 * Every Klang UI element in every state, on one page (KLANG-00337, `/debug/ui-kitchen-sink`).
 *
 * The sections follow the design canvas (`docs/design/2026-10-25-ui-workbench-canvas/`, board "Kitchen sink") in
 * order and names, so the two side by side show any drift. Once a component is built, this page is its truth and the
 * canvas is refreshed from it. The last section shows today's components for comparison until they are replaced.
 */
class UiKitchenSinkPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val look by subscribingTo(KlangTheme.look)

    private var toggled by value(true)

    private var clicks by value(0)

    /** Where each knob on the page stands, by name; a knob not turned yet stands at its default. */
    private var positions: Map<String, Double> by value(emptyMap())

    //  SAMPLE LOOKS  ///////////////////////////////////////////////////////////////////////////////////////////

    /** Two looks on the user's layer, to see a theme change repaint everything at once. Samples, not themes yet. */
    private val sampleLooks: List<Pair<String, Map<KlangRole, String>>> = listOf(
        "Default" to emptyMap(),
        "Copper" to mapOf(
            KlangRole.MetalLight to "#f6d6bd",
            KlangRole.MetalMid to "#c98a5e",
            KlangRole.MetalDark to "#7a4a2c",
            KlangRole.MetalInk to "#1a0d05",
            KlangRole.AccentMuted to "#b0764c",
        ),
        "Cream on black" to mapOf(
            KlangRole.GroundApp to "#141311",
            KlangRole.GroundDeep to "#0d0c0b",
            KlangRole.GroundPanel to "#22201c",
            KlangRole.InkText to "#eadfc4",
            KlangRole.InkStrong to "#fff6dc",
            KlangRole.InkLabel to "#c6b996",
            KlangRole.MetalLight to "#fbf3dd",
            KlangRole.MetalMid to "#e3d5b0",
            KlangRole.MetalDark to "#a08f66",
        ),
    )

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div {
            key = "ui-kitchen-sink"
            css {
                put("width", "100%")
                put("height", "100vh")
                put("overflow", "auto")
                put("box-sizing", "border-box")
                put("padding", "40px 48px 80px")
                put("background", "var(--kl-ground-deep)")
                put("color", "var(--kl-ink-text)")
            }

            renderHeader()
            renderRoles()
            renderGauges()
            renderKnobs()
            renderLamps()
            renderButtons()
            renderPanels()
            renderToday()
        }
    }

    private fun FlowContent.renderHeader() {
        h1 {
            css {
                put("margin", "0 0 8px")
                put("color", "var(--kl-ink-strong)")
            }
            +"Kitchen sink"
        }

        p {
            css {
                put("margin", "0 0 20px")
                put("max-width", "900px")
                put("line-height", "22px")
            }
            +"Every Klang UI element in every state. Its order and names follow the design canvas, board"
            +" \"Kitchen sink\". The look below changes the user's layer: everything repaints at once."
        }

        div {
            css {
                put("display", "flex")
                put("gap", "8px")
                put("margin-bottom", "36px")
            }

            val current = KlangTheme.layers[KlangLookLayers.Layer.User]

            for ((name, values) in sampleLooks) {
                KlangButton(
                    onClick = { applySampleLook(values) },
                    surface = KlangButton.Surface.Domed,
                    pressed = current == values,
                ) {
                    +name
                }
            }
        }
    }

    private fun applySampleLook(values: Map<KlangRole, String>) {
        if (values.isEmpty()) {
            KlangTheme.clearLayer(KlangLookLayers.Layer.User)
        } else {
            KlangTheme.setLayer(KlangLookLayers.Layer.User, values)
        }
    }

    private fun FlowContent.renderRoles() {
        section("Theme roles") {
            for (role in KlangRole.entries) {
                cell(name = role.name, source = role.cssVar) {
                    when (role.kind) {
                        KlangRole.Kind.Color -> div {
                            css {
                                put("width", "120px")
                                put("height", "40px")
                                put("border", "1px solid var(--kl-edge)")
                                put("border-radius", "3px")
                                put("background", "var(${role.cssVar})")
                            }
                        }

                        KlangRole.Kind.Length -> div {
                            css {
                                put("width", "120px")
                                put("height", "40px")
                                put("border", "1px solid var(--kl-ink-label)")
                                put("border-radius", "var(${role.cssVar})")
                            }
                        }
                    }

                    span {
                        css {
                            put("font-family", "monospace")
                            put("font-size", "12px")
                        }
                        +look[role]
                    }
                }
            }
        }
    }

    private fun position(name: String, default: Double): Double = positions[name] ?: default

    private fun turn(name: String): (Double) -> Unit = { positions = positions + (name to it) }

    private fun FlowContent.renderGauges() {
        section("Gauges") {
            cell(name = "The Motor's three, with icons", source = "klangui/comp/KlangGauge.kt") {
                row {
                    KlangGauge(
                        name = "Active cylinders",
                        value = 12.0,
                        range = 0.0..32.0,
                        size = 72.px,
                        icon = { layer_group },
                        display = { it.toInt().toString() },
                    )
                    KlangGauge(
                        name = "Motor heat",
                        value = 0.38,
                        size = 92.px,
                        icon = { microchip },
                        display = { "${(it * 100).toInt()}%" },
                        hotFrom = 0.85,
                    )
                    KlangGauge(
                        name = "Active voices",
                        value = 24.0,
                        range = 0.0..64.0,
                        size = 72.px,
                        icon = { music },
                        display = { it.toInt().toString() },
                    )
                }
            }

            cell(name = "Offline, online, too hot, a text label", source = "KlangGauge(value = null, hotFrom, label)") {
                row {
                    KlangGauge(name = "Heat, offline", value = null, size = 72.px, icon = { microchip }, hotFrom = 0.85)
                    KlangGauge(
                        name = "Heat",
                        value = 0.38,
                        size = 72.px,
                        icon = { microchip },
                        hotFrom = 0.85,
                        display = { "${(it * 100).toInt()}%" },
                    )
                    KlangGauge(
                        name = "Heat, too hot",
                        value = 0.93,
                        size = 72.px,
                        icon = { microchip },
                        hotFrom = 0.85,
                        display = { "${(it * 100).toInt()}%" },
                    )
                    KlangGauge(name = "Heat", value = 0.38, size = 72.px, label = "HEAT", hotFrom = 0.85)
                }
            }

            cell(name = "Sizes: the Motor's 50 and 62, a large 130", source = "KlangGauge(size)") {
                row {
                    KlangGauge(name = "Small", value = 0.5, size = 50.px, icon = { music })
                    KlangGauge(name = "Middle", value = 0.5, size = 62.px, icon = { microchip })
                    KlangGauge(
                        name = "Large",
                        value = 0.62,
                        size = 130.px,
                        icon = { microchip },
                        display = { "${(it * 100).toInt()}%" },
                        hotFrom = 0.85,
                    )
                }
            }

            cell(name = "A knob drives a gauge: turn it into the red", source = "KlangKnob + KlangGauge") {
                val heat = position("demo", 0.38)

                row {
                    KlangKnob(name = "Demo", value = heat, onChange = turn("demo"), label = "TURN ME")
                    KlangGauge(
                        name = "Driven",
                        value = heat,
                        size = 92.px,
                        icon = { microchip },
                        display = { "${(it * 100).toInt()}%" },
                        hotFrom = 0.85,
                    )
                }
            }
        }
    }

    private fun FlowContent.renderKnobs() {
        section("Knobs") {
            cell(
                name = "Label: none, above, below, below with the value, above with the value below",
                source = "klangui/comp/KlangKnob.kt",
            ) {
                row {
                    KlangKnob(name = "Plain", value = position("plain", 0.62), onChange = turn("plain"))
                    KlangKnob(
                        name = "Cutoff",
                        value = position("above", 0.62),
                        onChange = turn("above"),
                        label = "CUTOFF",
                        labelPosition = KlangKnob.LabelPosition.Above,
                    )
                    KlangKnob(name = "Volume", value = position("below", 0.62), onChange = turn("below"), label = "VOLUME")
                    KlangKnob(
                        name = "Cutoff",
                        value = position("value", 0.86),
                        onChange = turn("value"),
                        label = "CUTOFF",
                        valueText = "${(200 + position("value", 0.86) * 15800).toInt()} Hz",
                    )
                    KlangKnob(
                        name = "Cutoff",
                        value = position("split", 0.86),
                        onChange = turn("split"),
                        label = "CUTOFF",
                        labelPosition = KlangKnob.LabelPosition.Above,
                        valueText = "${(200 + position("split", 0.86) * 15800).toInt()} Hz",
                    )
                }
            }

            cell(name = "An icon on the cap; the pointer becomes a dot", source = "KlangKnob(icon)") {
                row {
                    KlangKnob(name = "Volume", value = position("vol", 0.62), onChange = turn("vol"), icon = { volume_up })
                    KlangKnob(name = "Filter", value = position("filter", 0.8), onChange = turn("filter"), icon = { filter })
                    KlangKnob(
                        name = "Delay",
                        value = position("delay", 0.3),
                        onChange = turn("delay"),
                        icon = { clock },
                        label = "DELAY",
                    )
                }
            }

            cell(name = "Size and finish: the Motor's 31 without a ring, silver caps", source = "KlangKnob(size, finish, ring)") {
                row {
                    KlangKnob(name = "Small", value = position("small", 0.62), onChange = turn("small"), size = 31.px, ring = false)
                    KlangKnob(
                        name = "Gain",
                        value = position("gain", 0.62),
                        onChange = turn("gain"),
                        finish = KlangFinish.Silver,
                        label = "GAIN",
                    )
                    KlangKnob(
                        name = "Volume",
                        value = position("svol", 0.62),
                        onChange = turn("svol"),
                        finish = KlangFinish.Silver,
                        icon = { volume_up },
                    )
                    KlangKnob(
                        name = "Master",
                        value = position("master", 0.62),
                        onChange = turn("master"),
                        size = 96.px,
                        finish = KlangFinish.Silver,
                        label = "MASTER",
                    )
                }
            }
        }
    }

    private fun FlowContent.renderLamps() {
        section("Lamps") {
            cell(name = "KlangLamp: off, on, try, warning, critical", source = "klangui/comp/KlangLamp.kt") {
                row {
                    for (state in KlangLamp.State.entries) {
                        KlangLamp(state = state, size = 9.px)
                    }
                }
            }

            cell(name = "With a label", source = "KlangLamp(label = ...)") {
                row {
                    KlangLamp(state = KlangLamp.State.On, label = "Live")
                    KlangLamp(state = KlangLamp.State.Off, label = "Off")
                    KlangLamp(state = KlangLamp.State.Warning, label = "Draft")
                    KlangLamp(state = KlangLamp.State.Critical, label = "Error")
                }
            }
        }
    }

    private fun FlowContent.renderButtons() {
        section("Buttons") {
            for (finish in KlangFinish.entries) {
                for (surface in KlangButton.Surface.entries) {
                    cell(name = "$finish, $surface", source = "KlangButton(finish = $finish, surface = $surface)") {
                        row {
                            KlangButton(onClick = { clicks++ }, finish = finish, surface = surface) { +"Raised" }
                            KlangButton(onClick = {}, finish = finish, surface = surface, pressed = true) { +"Pressed" }
                            KlangButton(
                                onClick = {},
                                finish = finish,
                                surface = surface,
                                availability = KlangButton.Availability.Disabled,
                            ) { +"Disabled" }
                            KlangButton(
                                onClick = {},
                                finish = finish,
                                surface = surface,
                                availability = KlangButton.Availability.Later,
                            ) { +"Later" }
                        }

                        row {
                            KlangButton(onClick = {}, finish = finish, surface = surface, shape = KlangButton.Shape.Pill) {
                                +"Play"
                            }
                            KlangButton(
                                onClick = {},
                                finish = finish,
                                surface = surface,
                                shape = KlangButton.Shape.Round,
                                ariaLabel = "Stop",
                            ) { +"■" }
                            KlangButton(onClick = {}, finish = finish, surface = surface, beacon = true) { +"Try me" }
                        }
                    }
                }
            }

            cell(name = "Toggle with a lamp", source = "KlangButton(pressed, lamp)") {
                row {
                    KlangButton(
                        onClick = { toggled = !toggled },
                        pressed = toggled,
                        lamp = if (toggled) KlangLamp.State.On else KlangLamp.State.Off,
                    ) {
                        +"Songs"
                    }
                    KlangButton(onClick = {}, lamp = KlangLamp.State.Off) { +"Learn" }
                    KlangButton(onClick = {}, lamp = KlangLamp.State.Off, beacon = true) { +"Docs" }
                }
                span {
                    css { put("font-size", "12px") }
                    +"Clicked the first raised button $clicks times"
                }
            }
        }
    }

    private fun FlowContent.renderPanels() {
        section("Popups, all with rivets") {
            cell(name = "KlangPanel", source = "klangui/comp/KlangPanel.kt") {
                div {
                    css { put("width", "320px") }

                    KlangPanel {
                        div {
                            css {
                                put("font-size", "16px")
                                put("font-weight", "700")
                                put("color", "var(--kl-ink-strong)")
                            }
                            +"Move the bookmark here?"
                        }
                        p {
                            css { put("margin", "8px 0 16px") }
                            +"It is on line 12 now."
                        }
                        row {
                            KlangButton(onClick = {}) { +"No" }
                            KlangButton(onClick = {}, finish = KlangFinish.Silver) { +"Yes" }
                        }
                    }
                }
            }
        }
    }

    private fun FlowContent.renderToday() {
        section("Today, for comparison") {
            cell(name = "The Motor's gauges", source = "comp/gauges.kt, RoundGauge.kt") {
                row {
                    roundCylindersGauge(value = 3.0, size = 50.px)
                    renderMotorHeatGauge(value = 0.62, size = 62.px)
                    activeVoicesGauge(value = 24, size = 50.px)
                }
            }

            cell(name = "Volume knob", source = "comp/VolumeKnob.kt (turns the real master volume)") {
                VolumeKnob(size = 31.px, motorOnline = true)
            }

            cell(name = "LCD", source = "comp/LcdDisplay.kt") {
                row {
                    LcdDisplay(value = 42)
                    LcdDisplay(value = 42, dim = true)
                }
            }

            cell(name = "Yes or no question", source = "klangui/comp/YesNoModal.kt") {
                KlangButton(onClick = { askYesNo() }) { +"Ask" }
            }
        }
    }

    private fun askYesNo() {
        modals.show<Boolean> { handle ->
            YesNoModal(
                handle = handle,
                headline = "Move the bookmark here?",
                content = { +"It is on line 12 now." },
                yesText = "Move",
                noText = "Keep",
                onResult = {},
            )
        }
    }

    //  LAYOUT HELPERS  /////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.section(title: String, cells: DIV.() -> Unit) {
        h2 {
            css {
                put("margin", "0 0 14px")
                put("font-family", "monospace")
                put("font-size", "13px")
                put("letter-spacing", "0.2em")
                put("text-transform", "uppercase")
                put("color", "var(--kl-ink-label)")
            }
            +title
        }

        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("gap", "16px")
                put("align-items", "flex-start")
                put("margin-bottom", "40px")
            }
            cells()
        }
    }

    private fun DIV.cell(name: String, source: String, content: DIV.() -> Unit) {
        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "12px")
                put("padding", "18px")
                put("background", "var(--kl-ground-app)")
                put("border", "1px solid var(--kl-edge)")
                put("border-radius", "4px")
            }

            content()

            div {
                div {
                    css {
                        put("font-size", "13px")
                        put("font-weight", "700")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +name
                }
                div {
                    css {
                        put("font-family", "monospace")
                        put("font-size", "11px")
                        put("color", "var(--kl-ink-label)")
                    }
                    +source
                }
            }
        }
    }

    private fun DIV.row(content: DIV.() -> Unit) {
        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("gap", "12px")
                put("align-items", "center")
            }
            content()
        }
    }
}
