/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.steady
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import io.peekandpoke.ultra.streams.ops.ticker

/**
 * What plays in the app, next to the [Player], which keeps the engine itself (KLANG-00406, "The Spine's playbacks"):
 * the real song on its [song] deck, the code examples over it ([previews], which fade it out and in), and the stage,
 * what plays now for the visuals and Klang Amp ([nowPlaying]), and [live] play, the MIDI rig. The sounds' lane is still
 * to come.
 */
class Playbacks(
    private val engine: PlaybackEngine,
    /** What the MIDI rig keeps; the default lives and dies with these playbacks (a spec's). */
    private val midi: MidiKept = MidiKept(stores = SectionStores.inMemory()),
) {

    companion object {
        /** How often [heardSeconds] looks at the playback: a second's change shows within a quarter of a second. */
        private const val HEARD_TICK_MS = 250
    }

    /** The real song that plays: one at a time, kept while one navigates. Made on first use, as it starts the engine's status. */
    val song: SongDeck by lazy { SongDeck(engine = engine, stage = this) }

    /** What a page plays until it is stopped, over the song, which it fades out and in again ([Previews], D1). */
    val previews: Previews by lazy { Previews(deck = song, stage = this) }

    /**
     * Live play, the MIDI rig (KLANG-00402, step 4): for the app's life, playing along with whatever plays, stopping
     * nothing and stopped by nothing but a panic. Made on first use; brought up by what first shows it, or at the app's
     * start where MIDI was granted before ([MidiRig.start], [MidiRig.connectIfGranted]).
     */
    val live: MidiRig by lazy { MidiRig(engine = engine, kept = midi) }

    /**
     * Snapshot of whatever is considered the "primary" playback across the app.
     *
     * Controllers ([KlangCodePlaybackCtrl]) publish into this when they start/stop so that other UI (sidebar badges,
     * headers, the song list, ...) can reactively observe what's currently on stage without subscribing to every
     * controller.
     */
    data class NowPlaying(
        val title: String?,
        val code: String,
        val rpm: Double,
        val playback: KlangCyclicPlayback,
    )

    private val _nowPlaying = StreamSource<NowPlaying?>(null)
    val nowPlaying: Stream<NowPlaying?> = _nowPlaying.readonly

    private var nowPlayingOwner: PlaybackOwner? = null

    /**
     * The seconds the playback on stage has been heard, a value per second; null while nothing plays. Counted on the
     * backend's position, not the wall clock (KLANG-00399): after a stall it shows what played, not the time that
     * passed. Ticks only while something shows it: one clock for every display of the time (the status bar, Klang Amp).
     */
    val heardSeconds: Stream<Int?> = ticker(HEARD_TICK_MS)
        .map { nowPlaying()?.playback?.heardSec()?.toInt() }
        .distinct()

    /**
     * Publishes (or clears) the "now playing" snapshot. A clear is only honored when [owner]
     * is the current owner, so a stale controller can't wipe someone else's state.
     */
    fun publishNowPlaying(owner: PlaybackOwner, value: NowPlaying?) {
        if (value == null) {
            if (nowPlayingOwner === owner) {
                nowPlayingOwner = null
                _nowPlaying(null)
            }
        } else {
            nowPlayingOwner = owner
            _nowPlaying(value)
        }
    }

    /**
     * Clears the stage for [self]: the current [nowPlaying] owner, when it is neither [self] nor [spare], is stopped. A
     * preview spares the song it fades out ([Previews.play]).
     */
    fun requestExclusivePlayback(self: PlaybackOwner, spare: PlaybackOwner? = null) {
        val other = nowPlayingOwner

        if (other != null && other !== self && other !== spare) {
            other.stop()
        }
    }

    /** The song on the deck and the seconds it has been heard: what the status bar shows. */
    data class SongLine(val playing: SongDeck.Playing, val seconds: Int?)

    /**
     * The song on the deck and the seconds its own playback has been heard, for the status bar (KLANG-00406, step 4);
     * null while no song is on. The clock ticks only while the song plays and something shows it: in silence, and for a
     * song still starting or whose start failed, nothing ticks.
     */
    val songLine: Stream<SongLine?> by lazy {
        song.playing.switchMapNotNull { playing ->
            when {
                !playing.isPlaying -> steady(SongLine(playing = playing, seconds = null))

                else -> ticker(HEARD_TICK_MS)
                    .map { SongLine(playing = playing, seconds = song.onDeck()?.ctrl?.playback()?.heardSec()?.toInt()) }
                    .distinct()
            }
        }
    }

    /** Stops whatever is on the stage, from wherever it was started: a visual's stop button. */
    fun stopNowPlaying() {
        nowPlayingOwner?.stop()
    }

    /** A code playback of this stage, on its engine: a song page's, a code example's, the deck's own. */
    fun ctrl(): KlangCodePlaybackCtrl.Builder = KlangCodePlaybackCtrl.builder(stage = this, engine = engine)
}
