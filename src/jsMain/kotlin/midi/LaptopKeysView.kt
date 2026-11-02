/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.peekandpoke.klang.spine.MidiRig
import io.peekandpoke.klang.spine.MidiSettings
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onBlur
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onFocus
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onKeyUp
import io.peekandpoke.ultra.semanticui.noui
import io.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import org.w3c.dom.events.KeyboardEvent

@Suppress("FunctionName")
fun Tag.LaptopKeysView() = comp {
    LaptopKeysView(it)
}

/**
 * The laptop's keys (KLANG-00402, step 2), drawn as a panel with each key's letter: they play while the drawing has the
 * focus (a click into it, D8), so an editor or a page's own key never meets them. Folded to a strip while a KeyLab is
 * connected (D5), and opened from it by hand. Plays through the rig ([Playbacks.live]); the letters come from
 * [MidiSettings]. Drawn by the MIDI page and the MIDI tool alike.
 */
class LaptopKeysView(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////

    private val rig = Playbacks.live

    private val rigState by subscribingTo(rig.state)

    private val keyLabConnected by subscribingTo(
        rig.devices.map { state -> state.devices.any { it.contains("KeyLab", ignoreCase = true) } }.distinct()
    ) { connected ->
        // A KeyLab plugged in folds the keys away (D5): a removed element sends no blur, so they let go here
        if (connected && !open) {
            release()
        }
    }

    private val keyLayout by subscribingTo(MidiSettings.layout)

    private val letterOf by subscribingTo(MidiSettings.letters)

    private var focused: Boolean by value(false)

    /** Opened by hand while a KeyLab is connected, when the keys fold to a strip (D5). */
    private var open: Boolean by value(false)

    init {
        lifecycle {
            onMount {
                MidiSettings.readBrowserLetters()
            }

            onUnmount {
                // The drawing goes, and a removed element sends no blur
                release()
            }
        }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////

    /**
     * The keys let go of everything they hold: a key held when the focus goes never sends its up. Only from the drawing
     * that has the focus, the one they play through: another drawing of them (the MIDI page's and the tool's at once)
     * leaves a chord held there alone.
     */
    private fun release() {
        if (!focused) {
            return
        }

        focused = false
        rig.laptopFocusLost(tsMs = window.performance.now())
    }

    /** A key on the drawing: through the rig; a key that is the laptop's does not reach the browser too. */
    private fun key(e: KeyboardEvent, down: Boolean) {
        val handled = when (down) {
            true -> rig.laptopKeyDown(
                code = e.code,
                repeat = e.repeat,
                modified = e.ctrlKey || e.altKey || e.metaKey,
                shift = e.shiftKey,
                tsMs = e.timeStamp.toDouble(),
            )

            false -> rig.laptopKeyUp(code = e.code, tsMs = e.timeStamp.toDouble())
        }

        // "Automatic" learns each key's letter as it is first pressed, where the browser does not tell the layout; not
        // with Shift, which prints another sign (Shift and the comma is "<" on an English board)
        if (handled) {
            e.preventDefault()

            if (down && !e.shiftKey && e.key.length == 1) {
                MidiSettings.learn(code = e.code, letter = e.key.uppercase())
            }
        }
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        val shown = !keyLabConnected || open
        val octave = rigState.laptopOctave
        val velocity = rigState.laptopVelocity

        div {
            ui.horizontal.list {
                noui.item { noui.header { +"Laptop keys" } }

                noui.item {
                    +when {
                        !shown -> "Folded while a KeyLab is connected"
                        focused -> "Playing: octave C${4 + octave}, velocity $velocity"
                        else -> "Click the keyboard, then play"
                    }
                }

                noui.item {
                    ui.mini.buttons {
                        layoutButton(layout = null, title = "Automatic")

                        KeyLayout.entries.forEach { layoutButton(layout = it, title = it.title) }
                    }
                }

                if (keyLabConnected) {
                    noui.item {
                        ui.mini.basic.button {
                            onClick {
                                // Folding takes the drawing away, and a removed element sends no blur
                                if (open) {
                                    release()
                                }

                                open = !open
                            }
                            +(if (open) "Fold" else "Open")
                        }
                    }
                }
            }

            if (shown) {
                div {
                    attributes["tabindex"] = "0"
                    attributes["role"] = "application"
                    attributes["aria-label"] = "Laptop keys: click, then play"
                    css {
                        put("outline", if (focused) "2px solid #4a9eff" else "1px dashed #55555c")
                        put("border-radius", "4px")
                        put("max-width", "1000px")
                    }

                    onKeyDown { e -> key(e, down = true) }
                    onKeyUp { e -> key(e, down = false) }
                    onFocus { focused = true }
                    // A key held when the focus goes never sends its up: everything the keys hold lets go
                    onBlur { release() }

                    MidiPanel(
                        panel = laptopKeyboardPanel(label = letterOf),
                        heldNotes = rigState.heldNotes(laptopSide = true),
                        activeControls = rigState.pressed(laptopSide = true),
                        noteOffset = octave * 12,
                        keyLabels = LaptopKeys.keyLabels(octave = octave, label = letterOf),
                        screen = MidiRig.LAPTOP_SCREEN,
                        onInput = { events -> events.forEach { rig.handle(it) } },
                    )
                }

                noui.small.text {
                    +"Shift+Tab leaves the keyboard (Tab is the sustain, Esc the panic). Many laptop keyboards drop the third or "
                    +"fourth key of some chords: that is the keyboard, not Klang."
                }
            }
        }
    }

    private fun FlowContent.layoutButton(layout: KeyLayout?, title: String) {
        ui.given(keyLayout == layout) { primary }.button {
            key = "layout-${layout?.name ?: "auto"}"
            onClick { MidiSettings.chooseLayout(layout) }
            +title
        }
    }
}
