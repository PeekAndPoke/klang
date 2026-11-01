/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.OnKey
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.html.ButtonType
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.span
import kotlinx.html.title

@Suppress("FunctionName")
fun Tag.NowPlayingLine() = comp {
    NowPlayingLine(it)
}

/**
 * The song that plays, under the Motor on every page (KLANG-00406, step 4): its time, its title (a click opens its
 * page) and a stop key; the Stop key stops it from anywhere. Dim, with nothing in it, while no song is on, so the
 * column does not jump when one starts. It redraws once a second while a song plays, the Motor not at all.
 */
class NowPlayingLine(ctx: NoProps) : PureComponent(ctx) {

    private val line by subscribingTo(Playbacks.songLine)

    override fun VDom.render() {
        nowPlayingLineView(
            line = line,
            onOpen = { songId -> router.navToUri(Nav.song(songId)) },
            onStop = { Playbacks.song.stop() },
        ) {
            // The page's own Stop answers first on its song's page (the newest binding); here everywhere else
            OnKey(KlangKeyBindings.Stop, enabled = line != null, showHint = line != null) { Playbacks.song.stop() }
        }
    }
}

/** Seconds as m:ss. */
fun clockOf(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * The line itself, for [NowPlayingLine] and the kitchen sink: an LCD's well as the transport bar's, [line]'s time and
 * title in the LCD's ink, and the stop key; [stopKey] goes inside the stop key (the Stop key's binding and hint).
 */
fun FlowContent.nowPlayingLineView(
    line: Playbacks.SongLine?,
    onOpen: (songId: String) -> Unit,
    onStop: () -> Unit,
    stopKey: FlowContent.() -> Unit = {},
) {
    val playing = line?.playing
    val songTitle = playing?.title?.ifBlank { null } ?: "Untitled"

    div {
        key = "now-playing-line"
        css {
            put("display", "flex")
            put("align-items", "center")
            put("gap", "8px")
            put("height", "28px")
            put("margin", "0 10px 10px")
            put("padding", "0 4px 0 8px")
            put("box-sizing", "border-box")
            put("background", "var(--kl-ground-sunken)")
            put("border", "1px solid var(--kl-edge)")
            put("border-radius", "var(--kl-radius)")
            put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.6), 0 1px 0 rgba(255,255,255,0.06)")
            put("font-family", "'Courier New', 'Consolas', monospace")
            put("font-size", "13px")
            put("color", "var(--kl-lcd-ink)")

            if (playing == null) {
                put("filter", "brightness(0.7)")
            }
        }

        span {
            css {
                put("flex", "none")
                put("text-shadow", "0 0 6px color-mix(in srgb, var(--kl-lcd-ink) 60%, transparent)")
            }
            +(line?.seconds?.let { clockOf(it) } ?: "-:--")
        }

        if (playing == null) {
            span {
                css { put("flex", "1") }
                +"No song plays"
            }
        } else {
            button(type = ButtonType.button) {
                title = "Open \"$songTitle\""
                onClick { onOpen(playing.songId) }
                css {
                    put("flex", "1")
                    put("min-width", "0")
                    put("padding", "0")
                    put("border", "none")
                    put("background", "transparent")
                    put("color", "inherit")
                    put("font", "inherit")
                    put("text-align", "left")
                    put("white-space", "nowrap")
                    put("overflow", "hidden")
                    put("text-overflow", "ellipsis")
                    put("cursor", "pointer")
                }
                // A song still starting shows as one: the engine loads, or its code compiles
                +(if (playing.isPlaying) songTitle else "$songTitle ...")
            }
        }

        button(type = ButtonType.button) {
            attributes["aria-label"] = "Stop the song"
            title = "Stop the song"

            if (playing == null) {
                attributes["disabled"] = "disabled"
            }

            onClick { onStop() }
            css {
                put("flex", "none")
                put("padding", "2px 4px")
                put("border", "none")
                put("background", "transparent")
                put("color", "inherit")
                put("cursor", if (playing == null) "default" else "pointer")
            }
            stopKey()
            icon.stop()
        }
    }
}
