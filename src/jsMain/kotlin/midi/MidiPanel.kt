/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.midi.MidiDevicePanel
import io.peekandpoke.klang.midi.PanelControl
import io.peekandpoke.klang.ui.svgCircle
import io.peekandpoke.klang.ui.svgLine
import io.peekandpoke.klang.ui.svgRect
import io.peekandpoke.klang.ui.svgRoot
import io.peekandpoke.klang.ui.svgText
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onPointerCancel
import io.peekandpoke.ultra.html.onPointerDown
import io.peekandpoke.ultra.html.onPointerMove
import io.peekandpoke.ultra.html.onPointerUp
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Suppress("FunctionName")
fun Tag.MidiPanel(
    panel: MidiDevicePanel,
    heldNotes: Set<Int> = emptySet(),
    activeControls: Map<String, Double> = emptyMap(),
    tints: Map<String, String> = emptyMap(),
    outlines: Map<String, String> = emptyMap(),
    noteOffset: Int = 0,
    keyLabels: Map<Int, String> = emptyMap(),
    screen: SourceId? = null,
    onInput: ((List<ControlEvent>) -> Unit)? = null,
) = comp(
    MidiPanel.Props(
        panel = panel,
        heldNotes = heldNotes,
        activeControls = activeControls,
        tints = tints,
        outlines = outlines,
        noteOffset = noteOffset,
        keyLabels = keyLabels,
        screen = screen,
        onInput = onInput,
    )
) {
    MidiPanel(it)
}

/**
 * Draws a [MidiDevicePanel] and lights up what is being played.
 *
 * Device-agnostic by construction: it renders the shapes a panel describes and knows nothing about
 * any particular hardware. A new controller is a new [MidiDevicePanel] value, not a new component.
 *
 * @see io.peekandpoke.klang.midi.KeyLabEssential49Mk3
 */
class MidiPanel(ctx: Ctx<Props>) : Component<MidiPanel.Props>(ctx) {

    data class Props(
        val panel: MidiDevicePanel,
        val heldNotes: Set<Int>,
        /** control id -> 0..1 activity, for knobs/faders/pads that are being moved or struck. */
        val activeControls: Map<String, Double>,
        /**
         * control id -> highlight colour while ACTIVE, overriding the default.
         */
        val tints: Map<String, String> = emptyMap(),
        /**
         * control id -> outline colour, drawn whether or not the control is active.
         *
         * Separate from [tints] because it answers a different question: tint says "this is being
         * played right now", outline says "this is the mode you are in", and the second has to be
         * visible when nothing is being touched at all.
         */
        val outlines: Map<String, String> = emptyMap(),
        /**
         * Semitones the drawn keybed is shifted by, so the highlighted key is the one under the
         * player's finger rather than the note number that arrived.
         *
         * The caller must supply this; it cannot be derived here. Transpose/Octave buttons change
         * what the keys transmit and send nothing themselves, and a transposed note is identical
         * to the untransposed note at that number: the offset simply is not in the data.
         */
        val noteOffset: Int = 0,
        /** The letter printed on a key of the keybed, by the note it plays: the laptop keys' (KLANG-00402). */
        val keyLabels: Map<Int, String> = emptyMap(),
        /**
         * The drawn controls as a source (KLANG-00402, step 3): with [screen] and [onInput] every key, pad, button,
         * knob, fader and wheel takes a pointer (mouse, finger), and what it does goes to [onInput] as from [screen].
         */
        val screen: SourceId? = null,
        val onInput: ((List<ControlEvent>) -> Unit)? = null,
    )

    companion object {
        private const val LIT = "#4a9eff"
        private const val KEY_WHITE = "#f4f4f0"
        private const val KEY_BLACK = "#141416"
        private const val CTRL_DARK = "#3a3a40"
        private const val CTRL_EDGE = "#55555c"
        private const val LABEL = "#9aa0aa"

        /** Readout colour for text sitting ON a control's light face, where [LIT] has no contrast. */
        private const val INK_ON_LIGHT = "#1b1e23"
    }

    /**
     * True once a CONTINUOUS control (knob, fader, wheel) is known: it has sent a value, so it is
     * bound and its position is real rather than a resting guess. Deliberately not value-based: a
     * fader pulled fully down is still bound, and should not look unbound at zero.
     */
    private fun lit(id: String): Boolean = props.activeControls.containsKey(id)

    /**
     * True while a MOMENTARY control (button, pad) is actually down.
     *
     * These report their release as value 0, so "have I ever seen it" would latch them on forever
     * after the first press.
     */
    private fun pressed(id: String): Boolean = (props.activeControls[id] ?: 0.0) > 0.0

    /** The colour a lit control should use: the caller's override, else the default highlight. */
    private fun litColor(id: String): String = props.tints[id] ?: LIT

    /** Pointer angle of a POSITION knob: its value across the usual 280° sweep, centred at rest. */
    private fun absoluteAngle(id: String): Double =
        (-140.0 + (props.activeControls[id] ?: 0.5) * 280.0) * PI / 180.0

    /**
     * Value readout, drawn only for controls that have actually moved.
     *
     * Shown as the raw 0..127 MIDI value rather than a percentage: it is what the device sends,
     * so it is the number you compare against the monitor when mapping controls. Untouched
     * controls stay blank instead of showing a wall of "64".
     */
    private fun FlowContent.renderValue(
        id: String,
        x: Int,
        y: Int,
        fill: String = LIT,
        fontSize: String = "11",
    ) {
        val v = props.activeControls[id] ?: return

        svgText(
            x = x, y = y, text = "${(v * 127).roundToInt()}",
            fill = fill, fontSize = fontSize, textAnchor = "middle", fontWeight = "600",
            key = "$id-val",
        )
    }

    init {
        lifecycle {
            // A drawing taken away (folded, its page left) sends no up for a pointer that holds a key: it lets go here
            onUnmount {
                val released = screenControls?.releaseAll(tsMs = window.performance.now()).orEmpty()

                if (released.isNotEmpty()) {
                    props.onInput?.invoke(released)
                }
            }
        }
    }

    /** The pointers on the drawing, one at a time each; made with the first, from [Props.screen]. */
    private var screenControls: ScreenControls? = null

    private fun screenControls(): ScreenControls? =
        screenControls ?: props.screen?.let { ScreenControls(source = it) }?.also { screenControls = it }

    /** A pointer's place in the panel's own units, from where it is on the drawing. */
    private fun panelPoint(e: PointerEvent): Pair<Double, Double>? {
        val rect = dom?.getBoundingClientRect() ?: return null

        if (rect.width <= 0.0 || rect.height <= 0.0) {
            return null
        }

        return ((e.clientX - rect.left) * props.panel.width / rect.width) to
            ((e.clientY - rect.top) * props.panel.height / rect.height)
    }

    private fun pointerDown(e: PointerEvent) {
        val onInput = props.onInput ?: return
        val screen = screenControls() ?: return

        // The mouse's main button, a finger, a pen
        if (e.button.toInt() != 0) {
            return
        }

        val (x, y) = panelPoint(e) ?: return
        val grip = ScreenControls.gripAt(
            panel = props.panel,
            x = x,
            y = y,
            noteOffset = props.noteOffset,
            valueOf = { props.activeControls[it] },
        ) ?: return

        // No preventDefault: it would also stop the mouse's down, and with it the focus a click gives the laptop keys.
        // The capture keeps the drag's moves and its up here. A pointer that cannot be captured is still held: its up
        // may come elsewhere, and then the next down of the same pointer, a move with no button down, or the drawing
        // going away lets it go
        try {
            dom?.setPointerCapture(e.pointerId)
        } catch (t: Throwable) {
            console.warn("[MidiPanel] the pointer could not be captured", t)
        }

        onInput(screen.down(pointer = e.pointerId, grip = grip, y = y, tsMs = e.timeStamp.toDouble()))
    }

    private fun pointerMove(e: PointerEvent) {
        val onInput = props.onInput ?: return

        // A move with no button down: its up never came here (released outside, the capture refused)
        if (e.pointerType == "mouse" && e.buttons.toInt() == 0) {
            pointerUp(e)

            return
        }

        val (_, y) = panelPoint(e) ?: return

        screenControls()?.move(pointer = e.pointerId, y = y, tsMs = e.timeStamp.toDouble())?.takeIf { it.isNotEmpty() }?.let(onInput)
    }

    private fun pointerUp(e: PointerEvent) {
        val onInput = props.onInput ?: return

        screenControls()?.up(pointer = e.pointerId, tsMs = e.timeStamp.toDouble())?.takeIf { it.isNotEmpty() }?.let(onInput)
    }

    override fun VDom.render() {
        val p = props.panel

        div {
            // A finger drags the controls, not the page
            css {
                put("touch-action", if (props.onInput != null) "none" else "auto")
                put("user-select", "none")
            }

            onPointerDown { e -> pointerDown(e) }
            onPointerMove { e -> pointerMove(e) }
            onPointerUp { e -> pointerUp(e) }
            onPointerCancel { e -> pointerUp(e) }
            // The capture lost without an up reaching the drawing; ultra.html has no helper for this one
            consumer.onTagEvent(this, "onlostpointercapture", { e: PointerEvent -> pointerUp(e) }.asDynamic())

            renderPanel(p)
        }
    }

    private fun FlowContent.renderPanel(p: MidiDevicePanel) {
        svgRoot(viewBox = "0 0 ${p.width} ${p.height}") {
            svgRect(0, 0, p.width, p.height, fill = p.body, rx = "10")

            p.keyBed?.let { renderKeyBed(it) }

            p.controls.forEach { renderControl(it) }

            svgText(
                x = p.nameX, y = p.nameY, text = p.name.uppercase(),
                fill = "#6c7178", fontSize = "13", fontWeight = "600",
            )
        }
    }

    private fun FlowContent.renderKeyBed(bed: KeyBed) {
        // The one geometry the drawing paints and a pointer is tested against
        val layout = KeyBedLayout(bed = bed, offset = props.noteOffset)
        val whiteW = layout.whiteWidth

        layout.whites.forEach { note ->
            val x = layout.xOf(note)
            svgRect(
                x = x, y = bed.y, width = whiteW - 1.5, height = bed.height,
                fill = if (note in props.heldNotes) LIT else KEY_WHITE,
                rx = "3", stroke = "#1a1a1c", strokeWidth = "1",
                key = "w-$note",
            )
            props.keyLabels[note]?.let { label ->
                svgText(
                    x = x + whiteW / 2, y = bed.y + bed.height - 26,
                    text = label,
                    fill = INK_ON_LIGHT, fontSize = "16", textAnchor = "middle",
                    key = "wk-$note",
                )
            }

            // Octave marker on every C, the way a keyboard is read at a glance.
            if (note % 12 == 0) {
                svgText(
                    x = x + whiteW / 2, y = bed.y + bed.height - 8,
                    text = "C${note / 12 - 1}",
                    fill = "#9a9a95", fontSize = "11", textAnchor = "middle",
                    key = "wl-$note",
                )
            }
        }

        layout.blacks.forEach { note ->
            val x = layout.xOf(note)
            svgRect(
                x = x, y = bed.y, width = layout.blackWidth, height = layout.blackHeight,
                fill = if (note in props.heldNotes) LIT else KEY_BLACK,
                rx = "3", stroke = "#000", strokeWidth = "1",
                key = "b-$note",
            )

            props.keyLabels[note]?.let { label ->
                svgText(
                    x = x + layout.blackWidth / 2, y = bed.y + layout.blackHeight - 10,
                    text = label,
                    // Dark on a lit key, where white has too little contrast
                    fill = if (note in props.heldNotes) INK_ON_LIGHT else KEY_WHITE, fontSize = "14", textAnchor = "middle",
                    key = "bk-$note",
                )
            }
        }
    }

    private fun FlowContent.renderControl(c: PanelControl) {
        when (c) {
            is PanelControl.Knob -> {
                val isPressed = c.pressId?.let { pressed(it) } ?: false

                svgCircle(
                    c.x, c.y, c.radius,
                    fill = if (c.accent) "#b9bcc2" else "#26262a",
                    stroke = if (lit(c.id) || isPressed) LIT else CTRL_EDGE,
                    // A click thickens the RING rather than filling the face: an encoder that is
                    // also a button has to show both at once, and the face holds the readout.
                    strokeWidth = if (isPressed) "5" else "2",
                    key = c.id,
                )

                if (c.relative) {
                    // No pointer: an endless encoder reports a STEP, not a position, so there is
                    // no angle to point at. The raw number is the only honest thing to show, and
                    // it goes in the middle, where the pointer would have been, in dark ink on
                    // the silver face, which the default blue has no contrast against.
                    renderValue(
                        c.id, c.x, c.y + 5,
                        fill = if (c.accent) INK_ON_LIGHT else LIT,
                        fontSize = "14",
                    )
                } else {
                    // The pointer carries the value by ROTATING, the way a real knob reads: a
                    // colour change would say "moving" and then lie about it forever. Sweep is
                    // the usual 280°.
                    val a = absoluteAngle(c.id)
                    val rr = c.radius * 0.72
                    svgLine(
                        x1 = c.x, y1 = c.y,
                        x2 = c.x + rr * sin(a), y2 = c.y - rr * cos(a),
                        stroke = if (c.accent) "#5a5d63" else "#8b9098", strokeWidth = "3",
                        key = "${c.id}-ptr",
                    )
                    renderValue(c.id, c.x, c.y - c.radius - 6)
                }
            }

            is PanelControl.Fader -> {
                // Slot, then the cap sitting at its current value (default centre when unknown).
                svgRect(
                    x = c.x - 2, y = c.y, width = 4, height = c.travel,
                    fill = "#1c1c1f", rx = "2", key = "${c.id}-slot",
                )
                val v = props.activeControls[c.id] ?: 0.5
                val capY = c.y + (c.travel - 10) * (1.0 - v)
                // Position IS the readout; the cap keeps its real colour and only the edge lights up
                // once the control is bound, so an unbound fader is visibly different from a resting one.
                svgRect(
                    x = c.x - 11, y = capY, width = 22, height = 10,
                    fill = "#d8dade", rx = "2",
                    stroke = if (lit(c.id)) LIT else "#17171a", strokeWidth = if (lit(c.id)) "2" else "1",
                    key = "${c.id}-cap",
                )
                c.label?.let {
                    svgText(
                        x = c.x, y = c.y + c.travel + 14, text = it,
                        fill = LABEL, fontSize = "11", textAnchor = "middle", key = "${c.id}-lbl",
                    )
                }
                renderValue(c.id, c.x, c.y - 6)
            }

            is PanelControl.Pad -> {
                svgRect(
                    x = c.x, y = c.y, width = c.size, height = c.size,
                    fill = if (pressed(c.id)) litColor(c.id) else "#1b1b20",
                    rx = "6",
                    stroke = if (pressed(c.id)) litColor(c.id) else (props.outlines[c.id] ?: "#4d6ea8"),
                    strokeWidth = "2",
                    key = c.id,
                )
                c.label?.let {
                    svgText(
                        x = c.x + 6, y = c.y + c.size - 6, text = it,
                        // Dark on the light of a struck pad, where the grey has no contrast
                        fill = if (pressed(c.id)) INK_ON_LIGHT else "#7f8794", fontSize = "10", key = "${c.id}-lbl",
                    )
                }
            }

            is PanelControl.Button -> {
                svgRect(
                    x = c.x, y = c.y, width = c.width, height = c.height,
                    fill = if (pressed(c.id)) litColor(c.id) else CTRL_DARK,
                    rx = "4", stroke = c.tint ?: CTRL_EDGE, strokeWidth = if (c.tint != null) "2" else "1",
                    key = c.id,
                )
                c.label?.let {
                    svgText(
                        // The text grows with the button: 9 on the KeyLab's 20 high ones
                        x = c.x + c.width / 2.0, y = c.y + c.height / 2.0 + c.height * 0.2, text = it,
                        fill = if (pressed(c.id)) INK_ON_LIGHT else c.tint ?: LABEL,
                        fontSize = "${c.height * 0.45}", textAnchor = "middle", key = "${c.id}-lbl",
                    )
                }
            }

            is PanelControl.Wheel -> {
                svgRect(
                    x = c.x, y = c.y, width = c.width, height = c.height,
                    fill = "#232327", rx = "10", stroke = CTRL_EDGE, strokeWidth = "1", key = c.id,
                )
                val v = props.activeControls[c.id] ?: 0.5
                svgRect(
                    x = c.x + 2, y = c.y + (c.height - 12) * (1.0 - v), width = c.width - 4, height = 12,
                    fill = if (lit(c.id)) LIT else "#4a4a52", rx = "3", key = "${c.id}-grip",
                )
            }

            is PanelControl.Display -> {
                svgRect(
                    x = c.x - 14, y = c.y - 28, width = c.width + 28, height = c.height + 78,
                    fill = "#141417", rx = "10", key = "${c.id}-bezel",
                )
                svgText(
                    x = c.x + c.width / 2.0, y = c.y - 12, text = "ARTURIA",
                    fill = "#d9dbdf", fontSize = "11", textAnchor = "middle", fontWeight = "600",
                    key = "${c.id}-brand",
                )
                svgRect(
                    x = c.x, y = c.y, width = c.width, height = c.height,
                    fill = "#dfe6e2", rx = "3", key = c.id,
                )
                c.text?.let {
                    svgText(
                        x = c.x + c.width / 2.0, y = c.y + c.height / 2.0 + 4, text = it,
                        fill = "#3b4a44", fontSize = "12", textAnchor = "middle", key = "${c.id}-txt",
                    )
                }
            }
        }
    }
}
