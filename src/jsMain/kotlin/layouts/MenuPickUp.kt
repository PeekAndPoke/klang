/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.comp.clockOf
import io.peekandpoke.klang.pages.docs.tutorials.TutorialPage
import io.peekandpoke.klang.pages.docs.tutorials.TutorialProgress
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.tutorialTrack
import io.peekandpoke.klang.spine.Playbacks
import io.peekandpoke.klang.spine.Recents
import io.peekandpoke.klang.spine.spine
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.ActiveRoute
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.semanticui.icon
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlin.js.Date

@Suppress("FunctionName")
fun Tag.MenuPickUp(currentSongId: String? = null, currentTrackSlug: String? = null, onOpen: (Route.Bound) -> Unit) = comp(
    MenuPickUp.Props(currentSongId = currentSongId, currentTrackSlug = currentTrackSlug, onOpen = onOpen)
) {
    MenuPickUp(it)
}

/**
 * "Pick up where you left off", the left half under the Motor menu's destinations (KLANG-00403, step 2): the song that
 * plays on the deck at the top, with its time and a stop key (KLANG-00406), then the songs opened last, each with its
 * tempo and when, and the track of the lesson opened last, with its next lesson and how far along it is ([Recents], the
 * Spine's record). A pick goes there through [Props.onOpen].
 */
class MenuPickUp(ctx: Ctx<Props>) : Component<MenuPickUp.Props>(ctx) {

    data class Props(
        /** The song whose page one is on ([Nav.songIdOn]): its tile's lamp is lit. */
        val currentSongId: String?,
        /** The track one is in ([trackSlugOn]): the lesson tile's lamp is lit when it is its track. */
        val currentTrackSlug: String?,
        val onOpen: (Route.Bound) -> Unit,
    )

    /**
     * The track to go on with: its [trackSlug] and [title], the [next] lesson's title (null once all are done), [done] of
     * [total], and the [target] a pick leads to (the next lesson in the track, or the track's page once all are done).
     */
    data class Continue(
        val trackSlug: String,
        val title: String,
        val next: String?,
        val done: Int,
        val total: Int,
        val target: Route.Bound,
    )

    companion object {
        /** The track one is in on the page [current] ([trackSlugOf]); null outside the lessons. */
        fun trackSlugOn(current: ActiveRoute): String? = trackSlugOf(
            route = current.route,
            slug = current.matchedRoute["slug"],
            trackParam = current.matchedRoute.queryParams[TutorialPage.PARAM_TRACK],
        )

        /**
         * The track one is in on [route], [slug] its path's slug: a lesson's page, in the track it was opened in ([trackParam])
         * or else the main path, as the lesson's page itself resolves it; a track's page; null on any other page.
         */
        fun trackSlugOf(route: Route, slug: String?, trackParam: String?): String? = when (route) {
            Nav.tutorial -> allTutorials.firstOrNull { it.slug == slug }?.let { tutorialTrack(trackSlug = trackParam, tutorial = it)?.slug }
            Nav.tutorialTrack -> slug
            else -> null
        }

        /** When something was opened, said plainly: "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago". */
        fun whenText(now: Double, at: Double): String {
            val minutes = ((now - at) / 60_000).toInt()

            return when {
                minutes < 1 -> "just now"
                minutes < 60 -> "$minutes min ago"
                minutes < 24 * 60 -> "${minutes / 60} h ago"
                minutes < 48 * 60 -> "yesterday"
                else -> "${minutes / (24 * 60)} days ago"
            }
        }

        /**
         * The track of the lesson opened last ([entries], the newest first) with what is [done]: the track it was opened
         * in, else the main path. A lesson the app no longer has is passed over for the one before; null when none is left.
         */
        fun continueOf(entries: List<Recents.Entry>, done: Set<String>): Continue? {
            val (last, lesson) = entries.filter { it.kind == Recents.Kind.Tutorial }
                .firstNotNullOfOrNull { entry -> allTutorials.firstOrNull { it.slug == entry.id }?.let { entry to it } }
                ?: return null
            val track = tutorialTrack(trackSlug = last.track, tutorial = lesson) ?: return null
            val next = track.lessons.firstOrNull { it.slug !in done }

            return Continue(
                trackSlug = track.slug,
                title = track.title,
                next = next?.title,
                done = TutorialProgress(done = done).doneIn(track),
                total = track.lessons.size,
                target = next?.let { Nav.tutorialInTrack(slug = it.slug, trackSlug = track.slug) }
                    ?: Nav.tutorialTrack(track.slug),
            )
        }
    }

    private val entries by subscribingTo(spine.recents.entries)

    private val done by subscribingTo(TutorialStorage.done)

    private val songLine by subscribingTo(Playbacks.songLine)

    override fun VDom.render() {
        menuPickUpView(
            // A song the app no longer has (a built-in renamed or gone in an update) is not offered
            songs = entries.filter { entry ->
                entry.kind == Recents.Kind.Song && (entry.id == Nav.NEW_SONG_ID || BuiltInSongs.songs.any { it.id == entry.id })
            },
            learning = continueOf(entries = entries, done = done),
            now = Date.now(),
            songLine = songLine,
            onStop = { Playbacks.song.stop() },
            currentSongId = props.currentSongId,
            currentTrackSlug = props.currentTrackSlug,
            onOpen = props.onOpen,
        )
    }
}

/** A tile's lamp and the gap after it: the subtitle is indented by both, under the title's text. */
private const val LAMP_PX = 7
private const val LAMP_GAP_PX = 6

/**
 * How [MenuPickUp] draws: the song on the deck ([songLine]) at the top as a wide tile with its stop key beside it, the
 * [songs] opened as tiles, the track to go on with ([learning]) as a wide tile with its progress bar under the text;
 * apart from the component, so the kitchen sink draws it in each state. Each tile has a lamp, lit on the item whose page
 * one is on ([currentSongId]; the maintainer, 2026-11-02: "a small led that is lit for the item the user is currently
 * on").
 */
fun FlowContent.menuPickUpView(
    songs: List<Recents.Entry>,
    learning: MenuPickUp.Continue?,
    now: Double,
    songLine: Playbacks.SongLine? = null,
    currentSongId: String? = null,
    currentTrackSlug: String? = null,
    onStop: () -> Unit = {},
    onOpen: (Route.Bound) -> Unit,
) {
    val styles = KlangControlStyles

    // No heading of its own: its captions say what each group is (the maintainer, 2026-11-02: "Remove this headline
    // please, it does not serve any purpose")
    songLine?.let { menuNowPlaying(line = it, lit = it.playing.songId == currentSongId, onStop = onStop, onOpen = onOpen) }

    if (songs.isEmpty() && learning == null) {
        if (songLine != null) {
            return
        }

        span(classes = styles.drawerCaption.toString()) { +"The songs and lessons you open wait here." }

        return
    }

    if (songs.isNotEmpty()) {
        span(classes = styles.drawerCaption.toString()) { +"Last opened songs" }

        div(classes = styles.drawerRecentGrid.toString()) {
            for (song in songs) {
                KlangButton(
                    onClick = { onOpen(Nav.song(song.id)) },
                    shape = KlangButton.Shape.Tile,
                ) {
                    tileTitle(title = song.title, lit = song.id == currentSongId)

                    // As the songs page shows it: 40, 37.2
                    val tempo = song.rpm?.let { "$it RPM · " } ?: ""

                    tileSub(text = "$tempo${MenuPickUp.whenText(now = now, at = song.at)}")
                }
            }
        }
    }

    learning?.let {
        span(classes = styles.drawerCaption.toString()) { +"Continue learning" }

        menuLessonTile(learning = it, lit = it.trackSlug == currentTrackSlug, onOpen = onOpen)
    }
}

/**
 * The song on the deck as a wide tile, a pick opening its page, with its stop key beside it (KLANG-00406: "The Motor
 * menu shows it at the top of 'Pick up where you left off'"): its title, and how long it has been heard, or that it
 * still starts.
 */
private fun FlowContent.menuNowPlaying(line: Playbacks.SongLine, lit: Boolean, onStop: () -> Unit, onOpen: (Route.Bound) -> Unit) {
    val styles = KlangControlStyles
    val playing = line.playing
    val title = playing.title?.ifBlank { null } ?: "Untitled"

    span(classes = styles.drawerCaption.toString()) { +"Playing now" }

    div(classes = styles.drawerNowRow.toString()) {
        // Its title and line straight in the tile, as the last opened songs': a long title ends in an ellipsis
        KlangButton(onClick = { onOpen(Nav.song(playing.songId)) }, shape = KlangButton.Shape.Tile) {
            tileTitle(title = title, lit = lit)
            // A song still starting shows as one, as the status bar shows it: the engine loads, or its code compiles
            tileSub(text = if (playing.isPlaying) "Playing · ${line.seconds?.let { clockOf(it) } ?: "-:--"}" else "Starting ...")
        }

        KlangButton(onClick = onStop, shape = KlangButton.Shape.Tile, ariaLabel = "Stop the song") {
            icon.stop { attributes["aria-hidden"] = "true" }
        }
    }
}

/** The track to go on with as a wide tile: its title and next lesson, the progress bar under them at its whole width. */
fun FlowContent.menuLessonTile(learning: MenuPickUp.Continue, lit: Boolean = false, onOpen: (Route.Bound) -> Unit) {
    val styles = KlangControlStyles

    div(classes = styles.drawerLessonRow.toString()) {
        KlangButton(onClick = { onOpen(learning.target) }, shape = KlangButton.Shape.Tile) {
            div(classes = styles.drawerLesson.toString()) {
                div(classes = styles.drawerTileText.toString()) {
                    // Its lamp lit while one is in its track (the maintainer, 2026-11-02: "give the continue learning an
                    // led too")
                    tileTitle(title = learning.title, lit = lit)
                    tileSub(text = "${learning.next?.let { "Next: $it" } ?: "Every lesson done"} · ${learning.done} of ${learning.total}")
                }

                // The count is in the text: a button's children are presentational, a progressbar role inside is lost
                span(classes = styles.drawerProgress.toString()) {
                    attributes["aria-hidden"] = "true"

                    span {
                        css { width = (if (learning.total == 0) 0 else learning.done * 100 / learning.total).pct }
                    }
                }
            }
        }
    }
}

/**
 * A tile's title with its lamp before it, lit on the item one is on (the maintainer, 2026-11-02: "a small led that is lit
 * for the item the user is currently on"); a long title ends in an ellipsis.
 */
private fun FlowContent.tileTitle(title: String, lit: Boolean) {
    span {
        css {
            put("display", "flex")
            put("align-items", "center")
            put("gap", "${LAMP_GAP_PX}px")
            put("min-width", "0")
            put("max-width", "100%")
        }

        KlangLamp(state = if (lit) KlangLamp.State.On else KlangLamp.State.Off, size = LAMP_PX.px)
        span(classes = KlangControlStyles.drawerRecentTitle.toString()) {
            css { put("min-width", "0") }
            +title
        }
    }
}

/** A tile's second line, under the title's text past its lamp (the maintainer, 2026-11-02: "indent the 'subtitle'"). */
private fun FlowContent.tileSub(text: String) {
    span(classes = KlangControlStyles.drawerSub.toString()) {
        css { put("padding-left", "${LAMP_PX + LAMP_GAP_PX}px") }
        +text
    }
}
