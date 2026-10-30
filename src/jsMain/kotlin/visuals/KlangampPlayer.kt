/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.comp.Motor
import io.peekandpoke.klang.comp.MotorReadings
import io.peekandpoke.klang.comp.Oscilloscope
import io.peekandpoke.klang.comp.PlayerMiniStats
import io.peekandpoke.klang.comp.Spectrumeter
import io.peekandpoke.klang.comp.VolumeKnob
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.feel.KlangRole
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.browser.window
import kotlinx.css.Color
import kotlinx.css.px
import kotlinx.html.ButtonType
import kotlinx.html.DIV
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title
import kotlin.js.Date
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.KlangampPlayer(scale: Double, onLeave: () -> Unit) = comp(
    KlangampPlayer.Props(scale = scale, onLeave = onLeave)
) {
    KlangampPlayer(it)
}

/**
 * Klangamp, the desktop player (the maintainer, 2026-10-29: "make the look winampish by arranging them", "Keep the
 * winamp but make the oscilloscope its own fill width element"): the Motor's elements in an old desktop player's
 * windows. The main window: the time and the spectrum on a display, the song's title running past, the readouts; the
 * scope across the whole width; the transport keys, the gauges and the volume. Under it the playlist.
 *
 * Drawn at [SIZE_PX] and scaled by [Props.scale] to the plate it lies on, a part of its own on the tiles.
 */
class KlangampPlayer(ctx: Ctx<Props>) : Component<KlangampPlayer.Props>(ctx) {

    data class Props(
        val scale: Double,
        val onLeave: () -> Unit,
    )

    companion object {
        /** The player's own size, square, before it is scaled to its plate. */
        const val SIZE_PX = 480

        /** How far the displays' corners are cut. */
        private const val CUT_PX = 6

        /** How often the time on the display steps. */
        private const val CLOCK_MS = 250
    }

    private val look by subscribingTo(KlangTheme.look)

    private val nowPlaying by subscribingTo(Player.nowPlaying) { KlangampDeck.noteNowPlaying(it) }

    /** The clock's now, stepped while mounted, for the time on the display. */
    private var nowMs: Double by value(Date.now())

    private var clockTimer: Int? = null

    private val cylinders by subscribingTo(MotorReadings.cylinders())

    private val heat by subscribingTo(MotorReadings.heat())

    private val voices by subscribingTo(MotorReadings.voices())

    init {
        lifecycle {
            onMount { clockTimer = window.setInterval({ nowMs = Date.now() }, CLOCK_MS) }
            onUnmount { clockTimer?.let { window.clearInterval(it) } }
        }
    }

    override fun VDom.render() {
        div {
            key = "klangamp-player"
            css {
                put("width", "${SIZE_PX}px")
                put("height", "${SIZE_PX}px")
                put("transform", "scale(${props.scale})")
                put("transform-origin", "top left")
                put("display", "flex")
                put("flex-direction", "column")
                put("gap", "6px")
                put("font-family", "monospace")
                put("text-align", "left")
            }

            playerWindow(title = "KLANG AMP", key = "main", grow = false) { renderMain() }
            playerWindow(title = "KLANG AMP PLAYLIST", key = "playlist", grow = true) {
                KlangampPlaylist(nowLine = false)
            }
        }
    }

    /** A window of the old player: brushed metal, a ridged title bar with a close key, its content under it. */
    private fun FlowContent.playerWindow(title: String, key: String, grow: Boolean, content: DIV.() -> Unit) {
        div {
            this.key = key
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("min-height", "0")
                put("flex", if (grow) "1" else "none")
                put(
                    "background",
                    "linear-gradient(180deg, color-mix(in srgb, var(--kl-metal-mid) 30%, var(--kl-ground-raised)), " +
                        "var(--kl-ground-panel))",
                )
                put("border", "1px solid var(--kl-edge)")
                put("box-shadow", "inset 0 1px 0 rgba(255, 255, 255, 0.12)")
                put("padding", "0 6px 6px")
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "8px")
                    put("height", "20px")
                    put("flex", "none")
                }

                ridges()

                span {
                    css {
                        put("font-size", "10px")
                        put("font-weight", "700")
                        put("letter-spacing", "0.25em")
                        put("color", "var(--kl-ink-strong)")
                    }
                    +title
                }

                ridges()

                button(type = ButtonType.button) {
                    attributes["aria-label"] = "Leave Klang Amp"
                    this.title = "Leave Klang Amp (Esc)"
                    onClick { props.onLeave() }
                    css {
                        put("width", "14px")
                        put("height", "14px")
                        put("padding", "0")
                        put("border", "1px solid var(--kl-edge)")
                        put("background", "var(--kl-ground-sunken)")
                        put("color", "var(--kl-ink-text)")
                        put("font-size", "10px")
                        put("line-height", "12px")
                        put("cursor", "pointer")
                    }
                    +"×"
                }
            }

            if (grow) {
                display(key = "$key-display", style = mapOf("flex" to "1", "min-height" to "0", "padding" to "4px 0")) {
                    content()
                }
            } else {
                div { content() }
            }
        }
    }

    /** The title bar's ridges, either side of its title. */
    private fun FlowContent.ridges() {
        div {
            css {
                put("flex", "1")
                put("height", "8px")
                put("opacity", "0.5")
                put("background", "repeating-linear-gradient(180deg, var(--kl-metal-light) 0 1px, transparent 1px 3px)")
            }
        }
    }

    /** A display: black glass, its corners cut, sunk into the window. */
    private fun FlowContent.display(key: String, style: Map<String, String> = emptyMap(), content: DIV.() -> Unit) {
        div {
            this.key = key
            css {
                put("position", "relative")
                put("background", "#000")
                put("clip-path", VisualsParts.cutCorners(CUT_PX))
                put("box-shadow", "inset 0 3px 8px rgba(0, 0, 0, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.06)")
                put("color", "var(--kl-lcd-ink)")
                put("overflow", "hidden")
                style.forEach { (k, v) -> put(k, v) }
            }

            content()
        }
    }

    private fun DIV.renderMain() {
        val now = nowPlaying
        val index = KlangampDeck.indexOf(now)
        val seconds = KlangampDeck.secondsAt(nowMs)

        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "190px 1fr")
                put("gap", "6px")
            }

            // The time and the spectrum
            display(key = "time", style = mapOf("height" to "84px", "padding" to "4px 8px")) {
                div {
                    css {
                        put("display", "flex")
                        put("align-items", "baseline")
                        put("gap", "10px")
                    }
                    span {
                        css { put("font-size", "11px") }
                        +(if (now != null) "▶" else "■")
                    }
                    span {
                        css {
                            put("font-size", "26px")
                            put("letter-spacing", "0.05em")
                            put("text-shadow", "0 0 6px color-mix(in srgb, var(--kl-lcd-ink) 60%, transparent)")
                        }
                        +(seconds?.let { KlangampDeck.clock(it) } ?: "-:--")
                    }
                }
                div {
                    css {
                        put("position", "absolute")
                        put("left", "8px")
                        put("right", "8px")
                        put("bottom", "5px")
                        put("height", "38px")
                    }

                    Spectrumeter(
                        colors = Motor.SPECTRUM_ROLES.map { Color(look[it]) },
                        numBoxesInStack = 12,
                        colorStep = Motor.SPECTRUM_COLOR_STEP,
                        numBuckets = 19,
                        lighting = Spectrumeter.Lighting.Dancing,
                    ) { Player.get() }
                }
            }

            div {
                css {
                    put("display", "flex")
                    put("flex-direction", "column")
                    put("gap", "6px")
                    put("min-width", "0")
                }

                // The song's title, running past
                display(key = "marquee", style = mapOf("height" to "22px")) {
                    val text = when {
                        now == null -> "Klang Amp  ***  pick a song from the playlist  ***  "
                        index >= 0 -> "${index + 1}. ${now.title ?: "Untitled"}  ***  "
                        else -> "${now.title ?: "Untitled"}  ***  "
                    }

                    div {
                        css {
                            put("position", "absolute")
                            put("top", "4px")
                            put("white-space", "pre")
                            put("font-size", "12px")
                            put("animation", "kl-klangamp-marquee 14s linear infinite")
                        }
                        // Four times: half of it runs past, then it starts over where it looks the same
                        +"$text$text$text$text"
                    }
                }

                // The readouts, as the old player's kbps and kHz
                div {
                    css {
                        put("display", "flex")
                        put("flex-wrap", "wrap")
                        put("gap", "6px")
                        put("font-size", "10px")
                    }
                    readout(label = "RPM", value = now?.rpm?.roundToInt()?.toString() ?: "--")
                    readout(label = "SONG", value = if (index >= 0) "${index + 1}/${BuiltInSongs.songs.size}" else "--")
                    readout(label = "STEREO", value = null)
                }

                // The keys, up beside the time, at its foot (the maintainer, 2026-10-29: "move the playback buttons up")
                div {
                    css {
                        put("display", "flex")
                        put("gap", "6px")
                        put("margin-top", "auto")
                    }

                    transportKey(label = "Previous song", onClick = { KlangampDeck.skip(now = now, step = -1) }) {
                        icon.step_backward()
                    }
                    transportKey(
                        label = "Play",
                        onClick = {
                            if (index >= 0) KlangampDeck.play(BuiltInSongs.songs[index]) else KlangampDeck.skip(now, 0)
                        },
                    ) {
                        icon.play()
                    }
                    transportKey(label = "Stop", onClick = { KlangampDeck.stop() }) { icon.stop() }
                    transportKey(label = "Next song", onClick = { KlangampDeck.skip(now = now, step = 1) }) {
                        icon.step_forward()
                    }
                }
            }
        }

        // The scope across the whole width (the maintainer, 2026-10-29: "make the oscilloscope its own fill width
        // element")
        display(key = "scope", style = mapOf("height" to "46px", "margin-top" to "6px")) {
            Oscilloscope(
                player = Player.player,
                strokeColor = Color(look[KlangRole.ScopeTrace]),
                strokeWidth = 1.5,
                pixelSkip = 3,
            )
        }

        // The volume on the left, as on the code page's Motor, right beside the gauges; the gauges in the middle; a
        // place kept on the right (the maintainer, 2026-10-29: "Put the gauges centered and the volume knob left as in
        // the Code SongPage. Put a "placeholder" on the right of the gauges", then "Move the volume button there")
        div {
            css {
                put("display", "grid")
                put("grid-template-columns", "1fr auto 1fr")
                put("align-items", "center")
                put("margin-top", "6px")
            }

            div {
                css {
                    put("justify-self", "end")
                    put("margin-right", "6px")
                }
                VolumeKnob(size = 40.px)
            }

            div {
                css {
                    put("display", "flex")
                    put("align-items", "center")
                    put("gap", "6px")
                }

                gauges()
            }

            // The placeholder: the knob's room, empty for now
            div {
                key = "placeholder"
                attributes["aria-hidden"] = "true"
                css {
                    put("justify-self", "start")
                    put("margin-left", "6px")
                    put("width", "40px")
                    put("height", "40px")
                }
            }
        }
    }

    /** The Motor's three gauges: cylinders, heat, voices. */
    private fun FlowContent.gauges() {
        KlangGauge(
            name = "Active cylinders",
            value = cylinders,
            range = 0.0..32.0,
            size = 44.px,
            icon = { layer_group },
            display = { it.roundToInt().toString() },
        )
        KlangGauge(
            name = "Motor heat",
            value = heat,
            size = 52.px,
            icon = { microchip },
            display = { "${(it * 100).roundToInt()}" },
            hotFrom = PlayerMiniStats.HEAT_HOT_FROM,
        )
        KlangGauge(
            name = "Active voices",
            value = voices,
            range = 0.0..100.0,
            size = 44.px,
            icon = { music },
            display = { it.roundToInt().toString() },
        )
    }

    /** A small readout: its label, its value; a value of null is a lamp, lit. */
    private fun FlowContent.readout(label: String, value: String?) {
        display(key = "readout-$label", style = mapOf("padding" to "2px 6px")) {
            if (value == null) {
                +label
            } else {
                span {
                    css { put("opacity", "0.6") }
                    +"$label "
                }
                +value
            }
        }
    }

    private fun FlowContent.transportKey(label: String, onClick: () -> Unit, glyph: FlowContent.() -> Unit) {
        KlangButton(
            onClick = onClick,
            finish = KlangFinish.Silver,
            shape = KlangButton.Shape.Rect,
            ariaLabel = label,
        ) {
            title = label
            glyph()
        }
    }
}
