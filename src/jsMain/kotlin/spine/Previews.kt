/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

/**
 * What a page plays until it is stopped, over the real song (KLANG-00406, step 6, D1): a code example in the docs or a
 * tutorial. One at a time. A preview takes the stage, but the song on the [deck] it fades out over [FADE_SEC], where it
 * plays on underneath, in time, its tails included; when no preview plays any more the song fades in again over
 * [FADE_SEC] and takes the stage back (the maintainer, 2026-11-01: "fade out over 2 seconds and in over 2 seconds
 * again"). A preview that fails to start counts as stopped. A song put on the deck stops the preview.
 *
 * The app has one, [Playbacks.previews], on [Playbacks.song]; the specs make their own on their own deck. A preview's
 * controller is built with [KlangCodePlaybackCtrl.Builder.preview], which sends its Play and its stop here.
 */
class Previews(private val deck: SongDeck) {

    init {
        // A song put on the deck stops the preview, also one still starting, which holds no stage yet
        deck.previews = this
    }

    companion object {
        /** The song's fade, out under a preview and in after it (D1). */
        const val FADE_SEC = 2.0
    }

    /** The preview that plays, or is starting; null with none. */
    private var playing: KlangCodePlaybackCtrl? = null

    /**
     * [preview] is about to play: the preview before it stops, whatever else holds the stage stops, and the song on the
     * deck fades out, playing on.
     */
    fun play(preview: KlangCodePlaybackCtrl) {
        val before = playing

        // The new one first, so the stop of the one before does not count as the last preview ending
        playing = preview

        if (before != null && before !== preview) {
            before.stop()
        }

        val song = deck.onDeck()?.ctrl

        Playbacks.requestExclusivePlayback(self = preview, spare = song)
        song?.setGain(gain = 0.0, rampSec = FADE_SEC)
    }

    /** Stops the preview that plays or is starting: a song was put on the deck. */
    fun stop() {
        playing?.stop()
    }

    /** [preview] stopped: when it was the one that played, the song on the deck fades in and takes the stage back. */
    fun stopped(preview: KlangCodePlaybackCtrl) {
        if (playing !== preview) {
            return
        }

        playing = null

        val song = deck.onDeck()?.ctrl ?: return

        song.setGain(gain = 1.0, rampSec = FADE_SEC)
        song.showOnStage()
    }
}
