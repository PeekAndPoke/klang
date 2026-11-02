/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.Song
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull

/**
 * The one owner of the real song that plays (KLANG-00406, "SongDeck"): the [KlangCodePlaybackCtrl] that plays it, kept
 * by the Spine, so the song plays on while one navigates. A new song stops the one before; the deck takes the stage
 * from any other playback that holds it ([Playbacks.requestExclusivePlayback]). Live play is no stage owner, so it plays
 * on.
 *
 * A song page puts its own controller on the deck with its Play, and takes it back when it opens again on that song
 * ([ctrlFor]): the editor, its highlights and Update go on where they were. Klang Amp puts a song on with a controller
 * the deck makes. A controller is released when nothing holds it any more: neither the deck nor a page that shows it
 * ([show]).
 *
 * The app has one, [Playbacks.song]; the specs make their own on a player without a backend.
 */
class SongDeck(private val engine: PlaybackEngine = Player) {

    /** What is on the deck: the song's id, the controller that plays it, and whether a song page put it on. */
    data class OnDeck(val songId: String, val ctrl: KlangCodePlaybackCtrl, val fromPage: Boolean)

    /** The song on the deck as a display shows it: its id, its title as it is now, whether it plays yet. */
    data class Playing(val songId: String, val title: String?, val isPlaying: Boolean)

    private val _onDeck = StreamSource<OnDeck?>(null)

    /**
     * What is on the deck: a song playing, starting, or one whose start failed (its errors on its controller); null once
     * it stopped, from here or because another playback took the stage.
     */
    val onDeck: Stream<OnDeck?> = _onDeck.readonly

    /** The song on the deck for a display (the status bar); a new title or the start shows at once. Null with none. */
    val playing: Stream<Playing?> = onDeck
        .switchMapNotNull { on -> on.ctrl.state.map { Playing(songId = on.songId, title = it.title, isPlaying = it.isPlaying) } }
        .distinct()

    /** The pages showing a controller, by controller: one page, or two while the router fades one out. */
    private val shown = mutableMapOf<KlangCodePlaybackCtrl, Int>()

    /** The previews over this deck ([Previews]), which register here: a song put on stops theirs. */
    internal var previews: Previews? = null

    /** Ends the deck's watch on what is on it. */
    private var watching: (() -> Unit)? = null

    /**
     * The controller a song page put on the deck for [songId], to show again: the editor goes on with it. Null when
     * another song is on, or this one came from Klang Amp (it plays the original; the page shows the user's version).
     */
    fun ctrlFor(songId: String): KlangCodePlaybackCtrl? =
        _onDeck()?.takeIf { it.songId == songId && it.fromPage }?.ctrl

    /**
     * A page shows [ctrl] from now; the function returned says it no longer does. A controller neither on the deck nor
     * shown is released then.
     */
    fun show(ctrl: KlangCodePlaybackCtrl): () -> Unit {
        shown[ctrl] = (shown[ctrl] ?: 0) + 1

        var hidden = false

        return {
            if (!hidden) {
                hidden = true

                val left = (shown[ctrl] ?: 1) - 1

                if (left > 0) {
                    shown[ctrl] = left
                } else {
                    shown.remove(ctrl)

                    if (_onDeck()?.ctrl !== ctrl) {
                        ctrl.release()
                    }
                }
            }
        }
    }

    /** Puts [song] on the deck and plays it, Klang Amp's way: with a controller of the deck's own. */
    fun play(song: Song): KlangCodePlaybackCtrl {
        val ctrl = KlangCodePlaybackCtrl.builder()
            .code(song.code)
            .rpm(song.rpm)
            .title(song.title)
            .engine(engine)
            .build()

        play(OnDeck(songId = song.id, ctrl = ctrl, fromPage = false))

        return ctrl
    }

    /**
     * Plays a song page's [ctrl] on the deck, as the song [songId]: the song before stops. Already on, it plays its
     * code now: a live update when it plays, a start when it does not.
     */
    fun play(songId: String, ctrl: KlangCodePlaybackCtrl) {
        play(OnDeck(songId = songId, ctrl = ctrl, fromPage = true))
    }

    /** Stops the song and takes it off the deck. */
    fun stop() {
        letGo(stop = true)
    }

    private fun play(next: OnDeck) {
        if (_onDeck()?.ctrl !== next.ctrl) {
            letGo(stop = true)
            // A song put on ends the preview over the one before, also one still starting (KLANG-00406, D1)
            previews?.stop()
            _onDeck(next)
            watch(next.ctrl)
            // The song takes the stage: what held it (a code example, a page's own playback) stops
            Playbacks.requestExclusivePlayback(self = next.ctrl)
        }

        next.ctrl.play()
    }

    /** Watches [ctrl] on the deck: a stop from anywhere (its Stop, another playback taking the stage) takes it off. */
    private fun watch(ctrl: KlangCodePlaybackCtrl) {
        var wasPlaying = ctrl.state().isPlaying

        watching = ctrl.state.subscribeToStream { state ->
            if (wasPlaying && !state.isPlaying) {
                letGo(stop = false)
            }

            wasPlaying = state.isPlaying
        }
    }

    /** Takes what is on the deck off it, [stop]ping it first; released when no page shows it. */
    private fun letGo(stop: Boolean) {
        val before = _onDeck() ?: return

        watching?.invoke()
        watching = null
        _onDeck(null)

        if (stop) {
            before.ctrl.stop()
        }

        // Off the deck no preview fades it: a page that plays it again plays it at full gain
        before.ctrl.setGain(gain = 1.0, rampSec = 0.0)

        if (shown[before.ctrl] == null) {
            before.ctrl.release()
        }
    }
}
