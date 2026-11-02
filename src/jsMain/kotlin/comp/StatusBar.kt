/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.version
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
fun Tag.StatusBar() = comp {
    StatusBar(it)
}

/**
 * The status bar, across the whole window at its foot on every page (the maintainer, 2026-11-02: "call it StatusBar",
 * "make the status bar full width"). On its left the song that plays (KLANG-00406, step 4): its time, its title (a
 * click opens its page) and a stop key; the Stop key stops it from anywhere. While no song is on, the engine's name,
 * version and revision roll through it, dim. It redraws once a second while a song plays, the Motor not at all.
 */
class StatusBar(ctx: NoProps) : PureComponent(ctx) {

    private val line by subscribingTo(Playbacks.songLine)
    private val versionInfo by subscribingTo(version)

    override fun VDom.render() {
        val info = versionInfo.takeIf { it.isAvailable }

        statusBarView(
            line = line,
            idleText = info?.let { "Klangmotor v${it.version} revision: ${it.gitRev}" } ?: "Klangmotor",
            onOpen = { songId -> router.navToUri(Nav.song(songId)) },
            onStop = { Playbacks.song.stop() },
        ) {
            // Mounted with the bar, from the app's start, so a song page's own Stop, bound later, is the newest binding
            // and answers first on its song's page; here everywhere else
            OnKey(KlangKeyBindings.Stop, enabled = line != null, showHint = line != null) { Playbacks.song.stop() }
        }
    }
}

/**
 * The song's place on the bar's left, under the frame's 10px and the column's 320px: it starts at the bar's 16px inset,
 * 6px past the frame, and ends 6px inside the column's edge, the same space on both sides as the eye reads it.
 */
private const val SONG_PLACE_PX = 10 + 320 - 16 - 6

/** Seconds as m:ss. */
fun clockOf(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * The bar itself, for [StatusBar] and the kitchen sink: an LCD's well as the transport bar's. On its left, under the
 * column above it, [line]'s time and title in the LCD's ink and the stop key. With no song, [idleText] rolls
 * through the whole bar, and there is no time and no stop key (the maintainer, 2026-11-02). [stopKey] goes into the
 * song's place, which is there with or without a song: the Stop key's binding and its hint.
 */
fun FlowContent.statusBarView(
    line: Playbacks.SongLine?,
    idleText: String,
    onOpen: (songId: String) -> Unit,
    onStop: () -> Unit,
    stopKey: FlowContent.() -> Unit = {},
) {
    val playing = line?.playing
    val songTitle = playing?.title?.ifBlank { null } ?: "Untitled"

    div {
        key = "status-bar"
        css {
            put("display", "flex")
            put("align-items", "center")
            put("height", "28px")
            // Edge to edge, square (the maintainer, 2026-11-02: "put it to the very bottom, no padding, also left and
            // right no padding"); the text keeps its inset, a little more on the left ("The text needs a little padding
            // on the left")
            put("padding", "0 4px 0 16px")
            put("box-sizing", "border-box")
            put("background", "var(--kl-ground-sunken)")
            put("border-top", "1px solid var(--kl-edge)")
            put("box-shadow", "inset 0 2px 4px rgba(0,0,0,0.6), 0 1px 0 rgba(255,255,255,0.06)")
            put("font-family", "'Courier New', 'Consolas', monospace")
            put("font-size", "13px")
            put("color", "var(--kl-lcd-ink)")

            if (playing == null) {
                put("filter", "brightness(0.7)")
            }
        }

        // The song on the left, under the column above it (the maintainer: "The song must still be shown on the
        // left"); with no song it takes no room, and keeps only the Stop key, so the key stays where it was bound
        div {
            key = "song"
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "8px")
                put("flex", "none")
                // The stop key as far inside the column's edge as the text is inside the frame's (the maintainer,
                // 2026-11-02: "the playbutton needs the same padding on the right")
                put("width", if (playing == null) "0" else "${SONG_PLACE_PX}px")
                put("max-width", "100%")
            }

            stopKey()

            if (playing != null) {
                span {
                    css {
                        put("flex", "none")
                        put("text-shadow", "0 0 6px color-mix(in srgb, var(--kl-lcd-ink) 60%, transparent)")
                    }
                    +(line?.seconds?.let { clockOf(it) } ?: "-:--")
                }

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

                button(type = ButtonType.button) {
                    attributes["aria-label"] = "Stop the song"
                    title = "Stop the song"

                    onClick { onStop() }
                    css {
                        put("flex", "none")
                        put("padding", "2px 0")
                        put("border", "none")
                        put("background", "transparent")
                        put("color", "inherit")
                        put("cursor", "pointer")
                    }
                    // Its square ends where the song's place ends: no padding, no margin, no wider box than the glyph
                    icon.stop {
                        css {
                            put("margin", "0")
                            put("width", "auto")
                        }
                    }
                }
            }
        }

        if (playing == null) {
            span {
                key = "idle"
                css {
                    put("flex", "1")
                    put("min-width", "0")
                    put("overflow", "hidden")
                    put("white-space", "nowrap")
                }

                span(classes = KlangControlStyles.marquee.toString()) {
                    +idleText
                }
            }
        }
    }
}
