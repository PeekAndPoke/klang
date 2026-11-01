/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.layouts

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.pages.docs.tutorials.TutorialProgress
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.tutorialTrack
import io.peekandpoke.klang.spine.Recents
import io.peekandpoke.klang.storage.TutorialStorage
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.routing.Route
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import kotlinx.css.pct
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.span
import kotlin.js.Date

@Suppress("FunctionName")
fun Tag.MenuPickUp(onOpen: (Route.Bound) -> Unit) = comp(MenuPickUp.Props(onOpen = onOpen)) {
    MenuPickUp(it)
}

/**
 * "Pick up where you left off", the left half under the Motor menu's destinations (KLANG-00403, step 2): the songs
 * opened last, each with its tempo and when, and the track of the lesson opened last, with its next lesson and how far
 * along it is ([Recents], the Spine's record). A pick goes there through [Props.onOpen].
 */
class MenuPickUp(ctx: Ctx<Props>) : Component<MenuPickUp.Props>(ctx) {

    data class Props(val onOpen: (Route.Bound) -> Unit)

    /**
     * The track to go on with: its [title], the [next] lesson's title (null once all are done), [done] of [total], and
     * the [target] a pick leads to (the next lesson in the track, or the track's page once all are done).
     */
    data class Continue(
        val title: String,
        val next: String?,
        val done: Int,
        val total: Int,
        val target: Route.Bound,
    )

    companion object {
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
                title = track.title,
                next = next?.title,
                done = TutorialProgress(done = done).doneIn(track),
                total = track.lessons.size,
                target = next?.let { Nav.tutorialInTrack(slug = it.slug, trackSlug = track.slug) }
                    ?: Nav.tutorialTrack(track.slug),
            )
        }
    }

    private val entries by subscribingTo(Recents.entries)

    private val done by subscribingTo(TutorialStorage.done)

    override fun VDom.render() {
        menuPickUpView(
            // A song the app no longer has (a built-in renamed or gone in an update) is not offered
            songs = entries.filter { entry ->
                entry.kind == Recents.Kind.Song && (entry.id == Nav.NEW_SONG_ID || BuiltInSongs.songs.any { it.id == entry.id })
            },
            learning = continueOf(entries = entries, done = done),
            now = Date.now(),
            onOpen = props.onOpen,
        )
    }
}

/**
 * How [MenuPickUp] draws: the [songs] opened as tiles, the track to go on with ([learning]) as a wide tile with its
 * progress bar under the text; apart from the component, so the kitchen sink draws it in each state.
 */
fun FlowContent.menuPickUpView(
    songs: List<Recents.Entry>,
    learning: MenuPickUp.Continue?,
    now: Double,
    onOpen: (Route.Bound) -> Unit,
) {
    val styles = KlangControlStyles

    div(classes = styles.heading.toString()) { +"Pick up where you left off" }

    if (songs.isEmpty() && learning == null) {
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
                    span(classes = styles.drawerRecentTitle.toString()) { +song.title }

                    // As the songs page shows it: 40, 37.2
                    val tempo = song.rpm?.let { "$it RPM · " } ?: ""

                    span(classes = styles.drawerSub.toString()) { +"$tempo${MenuPickUp.whenText(now = now, at = song.at)}" }
                }
            }
        }
    }

    learning?.let {
        span(classes = styles.drawerCaption.toString()) { +"Continue learning" }

        menuLessonTile(learning = it, onOpen = onOpen)
    }
}

/** The track to go on with as a wide tile: its title and next lesson, the progress bar under them at its whole width. */
fun FlowContent.menuLessonTile(learning: MenuPickUp.Continue, onOpen: (Route.Bound) -> Unit) {
    val styles = KlangControlStyles

    div(classes = styles.drawerLessonRow.toString()) {
        KlangButton(onClick = { onOpen(learning.target) }, shape = KlangButton.Shape.Tile) {
            div(classes = styles.drawerLesson.toString()) {
                div(classes = styles.drawerTileText.toString()) {
                    span(classes = styles.drawerRecentTitle.toString()) { +learning.title }
                    span(classes = styles.drawerSub.toString()) {
                        +"${learning.next?.let { "Next: $it" } ?: "Every lesson done"} · ${learning.done} of ${learning.total}"
                    }
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
