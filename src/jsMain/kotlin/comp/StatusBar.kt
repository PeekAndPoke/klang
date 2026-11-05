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
 * The status bar, on the Motor's screen, a line of LCD text on its floor (the maintainer, 2026-11-02: "call it
 * StatusBar", then, after a full-width bar and a field in the column, "try the lcd"). It shows the song that plays
 * (KLANG-00406, step 4): its time, its title (a click or Alt+P opens its page, KLANG-00412) and a stop key, on every
 * page but the song's own; the Stop key stops it from there. While no song is on, the engine's name, version and
 * revision roll through it, dim. It redraws once a second while a song plays, the Motor not at all.
 */
class StatusBar(ctx: NoProps) : PureComponent(ctx) {

    private val line by subscribingTo(Playbacks.songLine)
    private val versionInfo by subscribingTo(version)
    private val currentRoute by subscribingTo(router.current)

    /**
     * True on the page of the song [songId] when that page put it on the deck: the page then shows its own time and
     * Stop. A song Klang Amp put on is not the page's (`SongDeck.ctrlFor`).
     */
    private fun onPageOf(songId: String): Boolean {
        if (Playbacks.song.onDeck()?.fromPage != true) {
            return false
        }

        return Nav.songIdOn(currentRoute) == songId
    }

    override fun VDom.render() {
        val playing = line?.playing
        // Not on the playing song's own page, whose transport is there; it fades out and in (the maintainer,
        // 2026-11-02: "show it only when not on the page of the song", "Fade it in and out")
        val shown = playing == null || !onPageOf(playing.songId)
        val info = versionInfo.takeIf { it.isAvailable }

        statusBarView(
            line = line,
            shown = shown,
            idleText = info?.let { "Klangmotor v${it.version} revision: ${it.gitRev}" } ?: "Klangmotor",
            onOpen = { songId -> router.navToUri(Nav.song(songId)) },
            onStop = { Playbacks.song.stop() },
            openKey = true,
        ) {
            // Bound before any song page's own Stop, which is the newest binding there and answers first once it is on;
            // while the page's is off (its song still starting) this one stops the song. Its hint only while shown
            OnKey(KlangKeyBindings.Stop, enabled = line != null, showHint = line != null && shown) {
                Playbacks.song.stop()
            }
        }
    }
}

/** Seconds as m:ss. */
fun clockOf(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * The bar itself, for [StatusBar] and the kitchen sink: LCD text on the Motor's screen. [line]'s time and title in the
 * LCD's ink, the stop key at the right end. With no song, [idleText] rolls through the whole line, and there is no time
 * and no stop key (the maintainer, 2026-11-02). Not [shown], it fades out and takes no clicks. [stopKey] goes into the
 * song's place, which is there with or without a song: the Stop key's binding and its hint. With [openKey], Alt+P opens
 * the song's page as a click on its title does, while a song shows (KLANG-00412); the kitchen sink's bars bind no keys.
 */
fun FlowContent.statusBarView(
    line: Playbacks.SongLine?,
    idleText: String,
    shown: Boolean = true,
    onOpen: (songId: String) -> Unit,
    onStop: () -> Unit,
    openKey: Boolean = false,
    stopKey: FlowContent.() -> Unit = {},
) {
    val playing = line?.playing
    val songTitle = playing?.title?.ifBlank { null } ?: "Untitled"

    div {
        key = "status-bar"
        css {
            // LCD text on the Motor's screen, no box of its own (the maintainer, 2026-11-02: "try the lcd"): a light
            // shade under it, rising from the floor, keeps it readable over the spectrum's boxes, which still show
            // through ("it must not be fully opaque at the bottom")
            put("display", "flex")
            put("align-items", "center")
            put("height", "24px")
            put("padding", "0 10px")
            put("box-sizing", "border-box")
            put("overflow", "hidden")
            put("background", "linear-gradient(to top, rgba(0,0,0,0.4), rgba(0,0,0,0.15) 70%, transparent)")
            put("font-family", "'Courier New', 'Consolas', monospace")
            put("font-size", "12px")
            put("color", "var(--kl-lcd-ink)")
            put("text-shadow", "0 0 6px color-mix(in srgb, var(--kl-lcd-ink) 60%, transparent)")
            // Faded out it ends hidden: no clicks, out of the Tab order (hidden only at the fade's end, visible at
            // once at a fade in's start: a discrete transition)
            put("transition", "opacity 0.5s ease, visibility 0.5s")
            put("opacity", if (shown) "1" else "0")
            put("visibility", if (shown) "visible" else "hidden")
            put("pointer-events", "auto")
        }

        // The song: its time and title on the left, the stop key at the field's right end (the maintainer, 2026-11-02:
        // "Now the stop button can go back to the right"); a title too long for the field cut short. With no song it
        // takes no room and keeps only the Stop key, so the key stays where it was bound
        div {
            key = "song"
            css {
                put("display", "flex")
                put("align-items", "center")
                put("gap", "8px")
                put("flex", if (playing == null) "0 1 auto" else "1")
                put("min-width", "0")
            }

            stopKey()

            if (playing != null) {
                span {
                    css {
                        put("flex", "none")
                    }
                    +(line.seconds?.let { clockOf(it) } ?: "-:--")
                }

                button(type = ButtonType.button) {
                    title = "Open \"$songTitle\""
                    onClick { onOpen(playing.songId) }

                    // Faded out (on the song's own page) the key is off and shows no hint, as the click takes none
                    if (openKey) {
                        OnKey(KlangKeyBindings.OpenPlayingSong, enabled = shown, showHint = shown) {
                            onOpen(playing.songId)
                        }
                    }

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
                    // No margin and no box wider than the glyph: the square ends where the field's inset begins
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
                    // Dim while no song plays
                    put("filter", "brightness(0.7)")
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
