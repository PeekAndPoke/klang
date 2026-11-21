/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.studio.visuals

import io.peekandpoke.klang.apps.common.liveact.FollowedPlayback
import io.peekandpoke.klang.apps.common.liveact.LatestRead
import io.peekandpoke.klang.apps.common.liveact.PlayingMusic
import io.peekandpoke.klang.apps.common.liveact.SongReading
import io.peekandpoke.klang.apps.common.releaseName
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.studio.spine.spine
import io.peekandpoke.klang.studio.version
import io.peekandpoke.klang.viz.liveact.LiveAct
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.scores.DER_SCHMETTERLING
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.random.Random

@Suppress("FunctionName")
fun Tag.LiveActVisual() = comp {
    LiveActVisual(it)
}

/**
 * The live act for whatever plays in the app (KLANG-00449): the band Notstrom on its stage, playing the song on the
 * stage of the app (the Spine's `playbacks.nowPlaying`).
 *
 * The band needs the song a little ahead of the sound, so the playback's cycles are read ahead as it plays
 * ([SongReading], which says how far and its limits): the band plays the notes that sound (KLANG-00450). The band is
 * there as soon as the first stretch is read, and plans on as the song grows. The music itself is the app's: the live act follows the playback's time ([FollowedPlayback]) and starts and
 * stops nothing, so there is no walk-in here. Its loudness is read from the player's analyser ([PlayingMusic]).
 * Every song is played by Der Schmetterling's score ([DER_SCHMETTERLING]): each member plays the parts with that
 * score's tags, and one whose tags a song lacks stands still.
 *
 * A change of the code while it plays reaches the band with the sound: the band plays on, and what it read ahead is
 * read anew ([SongReading]). Known limits of the studio's, for now (the reading's own are [SongReading]'s). After a
 * change of the tempo while it plays the notes are right at once, but the band's bar length (its nods, the lights'
 * beat) stays the old tempo's until the song is played again: the tempo slider does not tell `nowPlaying`. And Esc
 * closes the visual here, so of the live act's two keys that give the camera back only 0 is of use.
 */
class LiveActVisual(ctx: NoProps) : PureComponent(ctx) {

    /** A show that is ready to be seen, and a key that is another one for every show. */
    private class Ready(val show: Show, val key: String)

    /** What plays, as far as it matters to the band: another playback, or one played at another tempo, is another show. */
    private data class Playing(val rpm: Double, val playback: KlangCyclicPlayback)

    private val scope = MainScope()
    private var shows = 0

    private var ready: Ready? by value(null)

    /** Whether something plays at all. */
    private var playing: Boolean by value(false)

    /** What reads the song on, for the show that is seen. */
    private var following: Job? = null

    /** The song's first stretch for what plays now; a read for what no longer plays makes no show. */
    private val songs = LatestRead<Playing, SongReading>(
        scope = scope,
        read = { now ->
            SongReading.begin(now.playback)
        },
        changed = { before, now ->
            playing = now != null

            // Another playback, or none: its band leaves at once. The same playback at another tempo: its band plays
            // on, and its song is read on, until the first stretch at the new tempo is read.
            if (now == null || now.playback !== before?.playback) {
                following?.cancel()
                following = null
                ready = null
            }
        },
        done = { now, reading ->
            following?.cancel()
            following = reading?.let {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { it.follow() }
            }

            shows++
            ready = Ready(
                show = Show(
                    seed = Random.nextInt(100_000),
                    music = PlayingMusic(
                        song = reading?.song,
                        analyser = { spine.player.player()?.getAnalyzer() },
                        listen = { FollowedPlayback(now.playback) },
                    ),
                ),
                key = "live-act-$shows",
            )
        },
        failed = { _, cause -> console.error("The live act could not read the playing song ahead", cause) },
    )

    @Suppress("unused")
    private val nowPlaying by subscribingTo(spine.playbacks.nowPlaying) { now ->
        songs.want(now?.let { Playing(rpm = it.rpm, playback = it.playback) })
    }

    init {
        lifecycle {
            onUnmount { scope.cancel() }
        }
    }

    override fun VDom.render() {
        div {
            css {
                put("position", "absolute")
                put("inset", "0")
                put("background", "#020308")
            }

            val now = ready

            if (now != null) {
                div {
                    // another show is another component: the band is made anew
                    key = now.key
                    css {
                        put("position", "absolute")
                        put("inset", "0")
                    }

                    LiveAct(show = now.show, score = DER_SCHMETTERLING, models = MODELS, version = figuresVersion())
                }
            } else {
                div {
                    css {
                        put("position", "absolute")
                        put("left", "0")
                        put("right", "0")
                        put("bottom", "18px")
                        put("text-align", "center")
                        put("color", "#9ab")
                        put("font", "14px monospace")
                    }

                    +(if (playing) "the band reads the song" else "play a song: the band plays it")
                }
            }
        }
    }

    /** The figures' release: this build's name ([releaseName]), "dev" where the build tells none. */
    private fun figuresVersion(): String {
        val now = version()

        if (!now.isAvailable) {
            return "dev"
        }

        return releaseName(version = now.version, gitRev = now.gitRev, gitDesc = now.gitDesc, date = now.date.orEmpty())
    }

    private companion object {
        /** Where the figures lie, beside the page (klangviz's resources). */
        const val MODELS = "/liveact/notstrom/"
    }
}
