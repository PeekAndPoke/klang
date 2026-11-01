/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.Song
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.ui.codemirror.EditorError
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The one owner of the real song that plays (KLANG-00406, "SongDeck"): one [KlangCodePlaybackCtrl], made once and
 * kept by the Spine, with the song's identity. A new song stops the one before; the deck takes the stage from any
 * other playback that holds it ([Playbacks.requestExclusivePlayback]). Live play is no stage owner, so it plays on.
 *
 * The app has one, [Playbacks.song]; the specs make their own on a player without a backend.
 */
class SongDeck(engine: PlaybackEngine = Player) {

    private val ctrl = KlangCodePlaybackCtrl.builder().exclusive().engine(engine).build()

    private val _onDeck = StreamSource<Song?>(null)

    /** The song on the deck, as it was put on; null when none is, also after something else took the stage. */
    val onDeck: Stream<Song?> = _onDeck.readonly

    /** The code, the RPM, whether it plays, the cycle: the [KlangCodePlaybackCtrl]'s state. */
    val state: Stream<KlangCodePlaybackCtrl.State> = ctrl.state

    /** The errors of the last compile. */
    val errors: Stream<List<EditorError>> = ctrl.errors

    /** The playback's signals; null while nothing plays. */
    val signals: Stream<KlangPlaybackSignal?> = ctrl.signals

    /** The playback; null while nothing plays. */
    val playback: Stream<KlangCyclicPlayback?> = ctrl.playback

    /** Whether the ctrl played: its stop, from wherever, takes the song off the deck. */
    private var wasPlaying = false

    init {
        // For the app's life, as the deck is the Spine's
        ctrl.state.subscribeToStream { state ->
            if (wasPlaying && !state.isPlaying) {
                _onDeck(null)
            }

            wasPlaying = state.isPlaying
        }
    }

    /** Puts [song] on the deck and plays it: the song before stops, as does what held the stage. */
    fun play(song: Song) {
        ctrl.stop()
        ctrl.setCode(song.code)
        ctrl.setRpm(song.rpm)
        ctrl.setTitle(song.title)
        _onDeck(song)
        ctrl.play()
    }

    /** Changes the song on the deck as it plays, live: the song page's Update. Nothing when no song is on. */
    fun update(code: String, rpm: Double) {
        if (_onDeck() == null) {
            return
        }

        ctrl.setCode(code)
        ctrl.setRpm(rpm)
        ctrl.play()
    }

    /** Stops the song and takes it off the deck. */
    fun stop() {
        ctrl.stop()
        _onDeck(null)
    }
}
