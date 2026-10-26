/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.KlangGuiDance
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.span

@Suppress("FunctionName")
fun Tag.SongsPage() = comp {
    SongsPage(it)
}

/**
 * The songs (KLANG-00349; the maintainer, 2026-10-26: the song list gets "a songs page", since the column on a song
 * shows only that song): a new song, and the built-in songs to open. The drawer's "Songs" leads here.
 */
class SongsPage(ctx: NoProps) : PureComponent(ctx) {

    /** The Gui-dance's lit step: the next song after the first glows when it is "open another song" (KLANG-00350). */
    private val guiDanceLit by subscribingTo(KlangGuiDance.dance.lit)

    override fun VDom.render() {
        // The song after the first, where the first-visit trail began
        val nextSong = BuiltInSongs.songs.getOrNull(1)?.id.takeIf { guiDanceLit == KlangGuiDance.SONGS_OPEN }

        JoinedPageTitle { listOf("Songs") }

        div {
            key = "songs-page"
            css {
                put("box-sizing", "border-box")
                put("height", "100%")
                put("overflow", "auto")
                put("padding", "28px 32px")
                put("color", "var(--kl-ink-text)")
            }

            h1(classes = KlangControlStyles.heading.toString()) {
                css {
                    put("margin", "0 0 18px")
                    put("font-size", "13px")
                }

                +"Songs"
            }

            KlangButton(onClick = { router.navToUri(Nav.newSongCode()) }, surface = KlangButton.Surface.Domed) {
                +"New song"
            }

            div(classes = KlangControlStyles.heading.toString()) {
                css { put("margin", "28px 0 12px") }

                +"Built in"
            }

            div {
                css {
                    put("display", "grid")
                    put("grid-template-columns", "repeat(auto-fill, minmax(200px, 1fr))")
                    put("gap", "10px")
                }

                for (song in BuiltInSongs.songs) {
                    renderSong(title = song.title, rpm = song.rpm, beacon = song.id == nextSong) {
                        // Any song opened from here is the step, the lit one or another
                        KlangGuiDance.dance.used(KlangGuiDance.SONGS_OPEN)
                        router.navToUri(Nav.editSongCode(song.id))
                    }
                }
            }
        }
    }

    /** A song's tile: its title, and under it its tempo. */
    private fun FlowContent.renderSong(title: String, rpm: Double, beacon: Boolean, open: () -> Unit) {
        KlangButton(onClick = open, shape = KlangButton.Shape.Tile, surface = KlangButton.Surface.Domed, beacon = beacon) {
            span { +title }
            span(classes = KlangControlStyles.drawerSub.toString()) { +"$rpm RPM" }
        }
    }
}
