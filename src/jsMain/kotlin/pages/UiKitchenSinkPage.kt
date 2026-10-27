/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.comp.LcdDisplay
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.storage.LookStorage
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangCaption
import io.peekandpoke.klang.ui.comp.KlangCard
import io.peekandpoke.klang.ui.comp.KlangCheckbox
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangDialog
import io.peekandpoke.klang.ui.comp.KlangDrawer
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangIconToggle
import io.peekandpoke.klang.ui.comp.KlangIgnition
import io.peekandpoke.klang.ui.comp.KlangKnob
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangLookPicker
import io.peekandpoke.klang.ui.comp.KlangMenu
import io.peekandpoke.klang.ui.comp.KlangMenuButton
import io.peekandpoke.klang.ui.comp.KlangPanel
import io.peekandpoke.klang.ui.comp.KlangScreen
import io.peekandpoke.klang.ui.comp.KlangSearchField
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.KlangSwatchButton
import io.peekandpoke.klang.ui.comp.KlangTextField
import io.peekandpoke.klang.ui.comp.KlangToolWindow
import io.peekandpoke.klang.ui.comp.KlangValueKnob
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
import io.peekandpoke.klang.ui.feel.KlangLookLayers
import io.peekandpoke.klang.ui.feel.KlangLooks
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.css.Color
import kotlinx.css.px
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.p
import kotlinx.html.span
import kotlin.time.Duration.Companion.seconds

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

    /** The drawer's current destination, its search, the menu's last choice, the tool windows' pins. */
    private var drawerAt: String by value("songs")
    private var query: String by value("")
    private var title: String by value("Final Synthasy VII Prelude")
    private var checkedOn: Boolean by value(true)
    private var menuChoice: String by value("nothing yet")
    private var dialogChoice: String by value("nothing yet")
    private var pinned: Set<String> by value(setOf("pinned"))

    /** The live ignition key: where it stands, and its glow lamp while the "Motor" gets ready. */
    private var ignitionAt: KlangIgnition.Position by value(KlangIgnition.Position.Off)
    private var ignitionGlow: Boolean by value(false)

    /** The value knob's value, the song's RPM. */
    private var rpm: Double by value(37.2)

    /** The glow lamp's 2 s, cancelled when the key goes back to OFF early. */
    private var ignitionWarmUp: Job? = null

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
            renderFields()
            renderButtons()
            renderPopups()
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
            +" \"Kitchen sink\". The look below changes the user's layer: everything repaints at once, and it is kept."
        }

        div {
            css {
                put("width", "340px")
                put("margin-bottom", "36px")
            }

            KlangLookPicker(onChosen = { LookStorage.save(it) })
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

                        // A share: the brushed finish on a patch of the frame's ground; the grain on a cap
                        KlangRole.Kind.Amount -> when (role) {
                            KlangRole.Brushed -> div(classes = KlangControlStyles.brushedSwatch.toString()) {}
                            else -> KlangButton(onClick = {}, surface = KlangButton.Surface.Domed) { +"Grain" }
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

    /** Candidates for the scope's trace, to try against the gauges while a song plays (KLANG-00344). */
    private val traceCandidates = listOf(
        "Brass" to "#c9a35a",
        "Bright brass" to "#dcb466",
        "Champagne" to "#e8d6ad",
        "Warm white" to "#f4ebdc",
        "Rim silver" to "#c9ced6",
        "Light silver" to "#f2f4f7",
    )

    /** Candidates for "selected" (`LampOn`): blues, from cool to warm, to tune against each look's metal. */
    private val selectedCandidates = listOf(
        "Accent blue" to "#528bff",
        "Azure" to "#4fa3d9",
        "Sky" to "#6cb2e6",
        "Steel" to "#5f8fc4",
        "Periwinkle" to "#8099d6",
        "Denim" to "#4f6fb8",
    )

    /** Tries [hex] for [role] on the look's Script layer (not saved), next to what the other experiments set there. */
    private fun tryRole(role: KlangRole, hex: String) {
        KlangTheme.setLayer(KlangLookLayers.Layer.Script, KlangTheme.layers[KlangLookLayers.Layer.Script] + (role to hex))
    }

    /** Ends the experiment on [role]: the look's own value again. */
    private fun untryRole(role: KlangRole) {
        KlangTheme.setLayer(KlangLookLayers.Layer.Script, KlangTheme.layers[KlangLookLayers.Layer.Script] - role)
    }

    private fun tried(role: KlangRole): String? = KlangTheme.layers[KlangLookLayers.Layer.Script][role]

    private fun FlowContent.renderGauges() {
        section("Gauges") {
            cell(
                name = "Selected (LampOn): try a colour for the current look (until a reload)",
                source = "KlangRole.LampOn, set on the look's Script layer",
            ) {
                row {
                    KlangLamp(state = KlangLamp.State.On, label = "Selected")
                    KlangMenuButton(open = true, onClick = {})
                    KlangIconToggle(icon = { book }, label = "Docs", on = true, onClick = {})
                    KlangButton(onClick = {}, shape = KlangButton.Shape.Tile, current = true, lamp = KlangLamp.State.On) {
                        +"Songs"
                    }
                }

                row {
                    KlangSwatchButton(
                        name = "Look's own",
                        swatch = listOf(look[KlangRole.LampOn]),
                        pressed = tried(KlangRole.LampOn) == null,
                        onClick = { untryRole(KlangRole.LampOn) },
                    )

                    for ((name, hex) in selectedCandidates) {
                        KlangSwatchButton(
                            name = name,
                            swatch = listOf(hex),
                            pressed = tried(KlangRole.LampOn).equals(hex, ignoreCase = true),
                            onClick = { tryRole(KlangRole.LampOn, hex) },
                        )
                    }
                }
            }

            cell(
                name = "The Motor's trace: try a colour (the idle line here; the live trace in the Motor, until a reload)",
                source = "KlangRole.ScopeTrace, set on the look's Script layer",
            ) {
                div {
                    css {
                        put("display", "flex")
                        put("align-items", "center")
                        put("gap", "12px")
                        put("padding", "10px")
                        put("background", "var(--kl-ground-deep)")
                        put("border-radius", "6px")
                    }

                    KlangGauge(name = "Motor heat", value = 0.38, size = 62.px, icon = { microchip }, hotFrom = 0.85)

                    div {
                        css {
                            put("width", "260px")
                            put("height", "52px")
                        }

                        Oscilloscope(player = Player.player, strokeColor = Color(look[KlangRole.ScopeTrace]))
                    }
                }

                row {
                    // The default: the trace follows the metal (the experiment's layer cleared)
                    KlangSwatchButton(
                        name = "Metal",
                        swatch = listOf(look[KlangRole.MetalMid]),
                        pressed = tried(KlangRole.ScopeTrace) == null,
                        onClick = { untryRole(KlangRole.ScopeTrace) },
                    )

                    for ((name, hex) in traceCandidates) {
                        KlangSwatchButton(
                            name = name,
                            swatch = listOf(hex),
                            pressed = tried(KlangRole.ScopeTrace).equals(hex, ignoreCase = true),
                            onClick = { tryRole(KlangRole.ScopeTrace, hex) },
                        )
                    }
                }
            }

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

            cell(
                name = "A value on a knob with an LCD: drag or the arrows step it, a click on the LCD lets one type it",
                source = "klangui/comp/KlangValueKnob.kt",
            ) {
                row {
                    KlangValueKnob(
                        name = "Revolutions per minute",
                        value = rpm,
                        range = 10.0..120.0,
                        step = 0.5,
                        onChange = { rpm = it },
                        unit = "RPM",
                        decimals = 2,
                        perPixel = 0.05,
                    )
                }
            }

            cell(
                name = "The ignition key: a click turns it to ON, the glow lamp burns 2 s, then it springs to START",
                source = "klangui/comp/KlangIgnition.kt",
            ) {
                row {
                    KlangIgnition(
                        position = ignitionAt,
                        glowing = ignitionGlow,
                        onTurn = {
                            ignitionAt = KlangIgnition.Position.On
                            ignitionGlow = true

                            ignitionWarmUp?.cancel()
                            ignitionWarmUp = launch {
                                delay(2.seconds)
                                ignitionGlow = false
                                ignitionAt = KlangIgnition.Position.Start
                            }
                        },
                    )

                    KlangButton(
                        onClick = {
                            ignitionWarmUp?.cancel()
                            ignitionGlow = false
                            ignitionAt = KlangIgnition.Position.Off
                        },
                    ) { +"Back to OFF" }
                }

                row {
                    KlangIgnition(position = KlangIgnition.Position.Off, glowing = false, onTurn = {}, size = 100.px)
                    KlangIgnition(position = KlangIgnition.Position.On, glowing = true, onTurn = {}, size = 100.px)
                    KlangIgnition(position = KlangIgnition.Position.Start, glowing = false, onTurn = {}, size = 100.px)
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
            cell(name = "KlangSpinner: something is happening; 16, 24 and 40", source = "klangui/comp/KlangSpinner.kt") {
                row {
                    KlangSpinner(label = "Loading")
                    KlangSpinner(label = "Loading", size = 24.px)
                    KlangSpinner(label = "Loading", size = 40.px)
                }
            }

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

    private fun FlowContent.renderFields() {
        section("Fields") {
            cell(name = "KlangTextField: the search field's well, no icon", source = "klangui/comp/KlangTextField.kt") {
                KlangTextField(label = "Song title", value = title, onInput = { title = it }, placeholder = "Song title")

                p { +"Holds: $title" }
            }

            cell(name = "KlangCheckbox: sunk, checked silver", source = "klangui/comp/KlangCheckbox.kt") {
                row {
                    KlangCheckbox(label = "Enter fullscreen", checked = checkedOn, onChange = { checkedOn = it })
                    KlangCheckbox(label = "Off", checked = false, onChange = {})
                }
            }

            cell(name = "KlangScreen: a well with cut corners, small and large", source = "klangui/comp/KlangScreen.kt") {
                row {
                    for (size in KlangScreen.Size.entries) {
                        div {
                            css {
                                put("display", "flex")
                                put("flex-direction", "column")
                                put("width", "220px")
                                put("height", "${if (size == KlangScreen.Size.Small) 70 else 140}px")
                            }

                            KlangScreen(size = size, bezel = "var(--kl-ground-app)") {
                                css {
                                    put("padding", "8px 12px")
                                    put("font-family", "monospace")
                                    put("font-size", "12px")
                                    put("color", "var(--kl-ink-text)")
                                }

                                +"sound(\"bd hh sd hh\")"
                            }
                        }
                    }
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

    private fun FlowContent.renderPopups() {
        section("Popups and windows, the popups with rivets") {
            cell(name = "The menu drawer", source = "klangui/comp/KlangDrawer.kt, KlangSearchField.kt, KlangLookPicker.kt") {
                div {
                    css { put("width", "300px") }

                    KlangDrawer(
                        title = "Where to",
                        entries = listOf(
                            KlangDrawer.Entry(id = "songs", label = "Songs", sub = "Yours and the built-ins"),
                            KlangDrawer.Entry(id = "learn", label = "Learn", sub = "Tutorials and tracks"),
                            KlangDrawer.Entry(id = "docs", label = "Docs", sub = "Lexikon, Sprudel, Stdlib"),
                            KlangDrawer.Entry(id = "samples", label = "Samples", sub = "The library"),
                            KlangDrawer.Entry(id = "midi", label = "MIDI", sub = "Playground, devices"),
                            KlangDrawer.Entry(id = "credits", label = "Credits", sub = "Who we build on"),
                            KlangDrawer.Entry(id = "community", label = "Community", sub = "With the backend", later = true),
                            KlangDrawer.Entry(id = "account", label = "Account", sub = "With the backend", later = true),
                        ),
                        current = drawerAt,
                        onPick = { drawerAt = it },
                        onClose = {},
                        search = {
                            KlangSearchField(
                                label = "Search songs, docs, samples and actions",
                                value = query,
                                onInput = { query = it },
                                placeholder = "Songs, docs, samples, actions",
                                keyHint = "Mod K",
                            )
                        },
                        footer = { KlangLookPicker(onChosen = { LookStorage.save(it) }) },
                    )
                }
            }

            cell(name = "The context menu: arrows, Home, End, Esc", source = "klangui/comp/KlangMenu.kt") {
                div {
                    css { put("width", "240px") }

                    KlangMenu(
                        label = "Code actions",
                        items = listOf(
                            KlangMenu.Item(label = "Open the param tool", hint = "Alt 2", onSelect = { menuChoice = "the param tool" }),
                            KlangMenu.Item(label = "Show the docs", hint = "Alt 1", onSelect = { menuChoice = "the docs" }),
                            KlangMenu.Item(label = "Set a bookmark", onSelect = { menuChoice = "a bookmark" }),
                            KlangMenu.Item(label = "Share (with the backend)", enabled = false, onSelect = {}),
                        ),
                        focusOnOpen = false,
                    )
                }
                span {
                    css { put("font-size", "12px") }
                    +"Chosen: $menuChoice"
                }
            }

            cell(name = "Tool windows: flying in, and pinned", source = "klangui/comp/KlangToolWindow.kt") {
                row {
                    for (id in listOf("floating", "pinned")) {
                        div {
                            css {
                                put("width", "300px")
                                put("height", "240px")
                                put("display", "flex")
                            }

                            KlangToolWindow(
                                title = "Docs",
                                chip = "follows the cursor",
                                pinned = id in pinned,
                                onPinnedChange = { pin -> pinned = if (pin) pinned + id else pinned - id },
                                onClose = {},
                            ) {
                                p {
                                    css { put("margin", "0") }
                                    +"The lowpass filter: cutoff, resonance, cascade and the cutoff envelope."
                                }
                            }
                        }
                    }
                }
            }

            cell(name = "Swatch buttons: pressed, raised, later", source = "klangui/comp/KlangSwatchButton.kt") {
                row {
                    KlangSwatchButton(name = "Copper", swatch = KlangLooks.Copper.swatch, pressed = true, onClick = {})
                    KlangSwatchButton(name = "Cream", swatch = KlangLooks.Cream.swatch, onClick = {})
                    KlangSwatchButton(
                        name = "Custom",
                        swatch = emptyList(),
                        onClick = {},
                        availability = KlangButton.Availability.Later,
                    )
                }
            }

            cell(
                name = "KlangDialog: the key row at the foot, verbs on the keys, their keys from the key manager",
                source = "klangui/comp/KlangDialog.kt",
            ) {
                div {
                    css { put("width", "340px") }

                    KlangDialog(
                        title = "Move the bookmark here?",
                        actions = listOf(
                            KlangDialog.Action(
                                label = "Keep",
                                key = KlangKeyBindings.DialogClose,
                                onClick = { dialogChoice = "kept" },
                            ),
                            KlangDialog.Action(
                                label = "Move",
                                key = KlangKeyBindings.DialogConfirm,
                                primary = true,
                                onClick = { dialogChoice = "moved" },
                            ),
                        ),
                    ) {
                        +"It is on line 12 now."
                    }
                }
                span {
                    css { put("font-size", "12px") }
                    +"Chosen: $dialogChoice"
                }
            }

            cell(name = "KlangPanel: the riveted frame alone", source = "klangui/comp/KlangPanel.kt") {
                div {
                    css { put("width", "260px") }

                    KlangPanel {
                        +"A plate held by four rivets: the frame of every popup."
                    }
                }
            }
        }
    }

    private fun FlowContent.renderToday() {
        section("Today, for comparison") {
            cell(name = "LCD", source = "comp/LcdDisplay.kt") {
                row {
                    LcdDisplay(value = 42)
                    LcdDisplay(value = 42, dim = true)
                }
            }

            cell(name = "Yes or no question", source = "klangui/comp/KlangYesNoModal.kt") {
                KlangButton(onClick = { askYesNo() }) { +"Ask" }
            }
        }
    }

    private fun askYesNo() {
        modals.show<Boolean> { handle ->
            KlangYesNoModal(
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
        h2(classes = KlangControlStyles.heading.toString()) {
            css {
                put("margin", "0 0 14px")
                put("font-size", "13px")
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

    /** One cell: a card holding the element, its caption under it. */
    private fun DIV.cell(name: String, source: String, content: DIV.() -> Unit) {
        KlangCard {
            content()
            KlangCaption(title = name, note = source)
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
