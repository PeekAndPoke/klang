/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Player
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.browser.window
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title
import kotlin.js.Date
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.KlangampPlaylist(nowLine: Boolean) = comp(
    KlangampPlaylist.Props(nowLine = nowLine)
) {
    KlangampPlaylist(it)
}

/**
 * Klangamp's playlist: the built-in songs in the display's ink, the one playing lit, a click plays one
 * ([KlangampDeck]). With [Props.nowLine] a line above them says what plays and for how long, with keys to stop and to
 * skip on: the tiles' Klangamp has no other keys.
 */
class KlangampPlaylist(ctx: Ctx<Props>) : Component<KlangampPlaylist.Props>(ctx) {

    data class Props(
        val nowLine: Boolean,
    )

    private companion object {
        /** How often the time steps. */
        const val CLOCK_MS = 250
    }

    private val nowPlaying by subscribingTo(Player.nowPlaying) { KlangampDeck.noteNowPlaying(it) }

    /** The clock's now, stepped while the time shows. */
    private var nowMs: Double by value(Date.now())

    private var clockTimer: Int? = null

    init {
        lifecycle {
            onMount {
                if (props.nowLine) {
                    clockTimer = window.setInterval({ nowMs = Date.now() }, CLOCK_MS)
                }
            }
            onUnmount { clockTimer?.let { window.clearInterval(it) } }
        }
    }

    override fun VDom.render() {
        val songs = BuiltInSongs.songs
        val now = nowPlaying
        val index = KlangampDeck.indexOf(now)

        div {
            css {
                put("display", "flex")
                put("flex-direction", "column")
                put("height", "100%")
                put("min-height", "0")
                put("font-family", "monospace")
                put("font-size", "12px")
                put("color", "var(--kl-lcd-ink)")
                put("text-align", "left")
            }

            if (props.nowLine) {
                renderNowLine(now = now, index = index)
            }

            div {
                css {
                    put("flex", "1")
                    put("min-height", "0")
                    put("overflow-y", "auto")
                }

                for ((i, song) in songs.withIndex()) {
                    div(classes = "kl-klangamp-row") {
                        key = "song-${song.id}"
                        title = "Play ${song.title}"
                        onClick { KlangampDeck.play(song) }
                        css {
                            put("display", "flex")
                            put("gap", "8px")
                            put("padding", "1px 8px")
                            put("cursor", "pointer")

                            if (i == index) {
                                put("color", "var(--kl-ink-strong)")
                                put("background", "color-mix(in srgb, var(--kl-accent) 35%, transparent)")
                            }
                        }

                        span {
                            css {
                                put("flex", "1")
                                put("white-space", "nowrap")
                                put("overflow", "hidden")
                                put("text-overflow", "ellipsis")
                            }
                            +"${i + 1}. ${song.title}"
                        }
                        span {
                            css { put("opacity", "0.7") }
                            +"${song.rpm.roundToInt()} rpm"
                        }
                    }
                }
            }
        }
    }

    /** What plays and for how long; the keys to stop it and to skip on. */
    private fun FlowContent.renderNowLine(now: Player.NowPlaying?, index: Int) {
        val seconds = KlangampDeck.secondsAt(nowMs)

        div {
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "10px")
                put("padding", "2px 8px 6px")
                put("margin-bottom", "4px")
                put("border-bottom", "1px solid color-mix(in srgb, var(--kl-lcd-ink) 25%, transparent)")
            }

            span { +(if (now != null) "▶" else "■") }
            span {
                css {
                    put("font-size", "18px")
                    put("text-shadow", "0 0 6px color-mix(in srgb, var(--kl-lcd-ink) 60%, transparent)")
                }
                +(seconds?.let { KlangampDeck.clock(it) } ?: "-:--")
            }
            span {
                css {
                    put("flex", "1")
                    put("white-space", "nowrap")
                    put("overflow", "hidden")
                    put("text-overflow", "ellipsis")
                }
                +when {
                    now == null -> "Pick a song"
                    index >= 0 -> "${index + 1}. ${now.title ?: "Untitled"}"
                    else -> now.title ?: "Untitled"
                }
            }

            lineKey(label = "Previous song", onClick = { KlangampDeck.skip(now = now, step = -1) }) { icon.step_backward() }
            lineKey(label = "Stop", onClick = { KlangampDeck.stop() }) { icon.stop() }
            lineKey(label = "Next song", onClick = { KlangampDeck.skip(now = now, step = 1) }) { icon.step_forward() }
        }
    }

    private fun FlowContent.lineKey(label: String, onClick: () -> Unit, glyph: FlowContent.() -> Unit) {
        button(type = ButtonType.button) {
            attributes["aria-label"] = label
            title = label
            onClick { onClick() }
            css {
                put("padding", "2px 4px")
                put("border", "none")
                put("background", "transparent")
                put("color", "var(--kl-lcd-ink)")
                put("cursor", "pointer")
            }
            glyph()
        }
    }
}
