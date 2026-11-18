/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_be.AudioAnalyzer
import io.peekandpoke.klang.audio_bridge.analyzer.SPECTRUM_FFT_SIZE
import io.peekandpoke.klang.audio_bridge.analyzer.createAnalyzerBuffer
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.Energy
import io.peekandpoke.klang.viz.liveact.sound.LiveLoudness
import io.peekandpoke.klang.viz.liveact.sound.Music
import io.peekandpoke.klang.viz.liveact.sound.Transport
import kotlinx.browser.window
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A song the engine plays, as the live act's [Music]: its events read ahead ([song], see [SongReading]), the loudness
 * the player's analyser hears, and [listen] for the transport a show listens to: [FollowedPlayback] where the music is
 * someone else's (the studio's), a [ShowPlayback] where the show plays it itself (notstrom's, with a walk-in).
 */
class PlayingMusic(
    override val song: Song?,
    /** The player's analyser as of now; null while there is none. */
    private val analyser: () -> AudioAnalyzer?,
    /** A transport for one listener; called each time a show starts to listen. */
    private val listen: () -> Transport,
) : Music {

    /** Live; said so too where the song could not be read and the band plays test notes to it. */
    override val source: String = if (song != null) "live" else "live (the song could not be read)"

    private val loudness = LiveLoudness()
    private val spectrum = createAnalyzerBuffer(SPECTRUM_FFT_SIZE / 2)
    private var heardAt = -1.0

    override fun energyAt(t: Double): Energy {
        val now = window.performance.now() / 1000

        // asked more than once a frame: heard once
        if (now - heardAt < 0.004) {
            return loudness.current()
        }

        heardAt = now

        val analyser = analyser() ?: return loudness.feed(now = now, total = 0.0, low = 0.0, mid = 0.0, high = 0.0)

        analyser.getFft(spectrum)

        // the spectrum's bins, in dB, added up as power in the three bands a recording's loudness has
        val width = SAMPLE_RATE / SPECTRUM_FFT_SIZE
        var total = 0.0
        var low = 0.0
        var mid = 0.0
        var high = 0.0

        for (i in 0 until spectrum.length) {
            val db = spectrum.asDynamic()[i].unsafeCast<Double>()

            // NaN-guard: a bin that is not a number, or silent, adds nothing
            if (!db.isFinite()) {
                continue
            }

            val power = 10.0.pow(db / 10)
            val hz: Double = i * width

            total += power

            when {
                hz < 20 -> Unit
                hz < 150 -> low += power
                hz < 2500 -> mid += power
                hz < 16000 -> high += power
                else -> Unit
            }
        }

        return loudness.feed(now = now, total = sqrt(total), low = sqrt(low), mid = sqrt(mid), high = sqrt(high))
    }

    override fun listen(): Transport = listen.invoke()

    private companion object {
        /**
         * The rate the engine is taken to run at, as the spectrum's own reading takes it. Where it runs at 44.1 kHz the
         * three bands' edges lie about a tenth lower: of no matter for a level.
         */
        const val SAMPLE_RATE = 48000.0
    }
}
