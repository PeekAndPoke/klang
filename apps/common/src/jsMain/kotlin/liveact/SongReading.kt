/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_engine.KlangCycleEvent
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A song read ahead for the live act while it plays (KLANG-00449): the cycles of the [playback] that plays it, asked
 * ahead of the sound, into [song], the band's.
 *
 * The band reads what sounds (KLANG-00450): the playback answers a cycle once, for the sound and for the band alike
 * (`KlangCyclicPlayback.cycleEvents`), so a part that draws at random is played note for note, and nothing is
 * compiled a second time.
 *
 * The band needs the song a little ahead of the sound: it is read always [AHEAD] cycles ahead of what is heard (and
 * never less than [AHEAD_SEC] seconds, up to [MOST_AHEAD] cycles: a body's moves take seconds, however fast the
 * song), a whole cycle at a time.
 * A playback answers before it is started, so the first stretch can be read before the sound (a walk-in needs the
 * first notes: a player gets ready for them). What lies more than [KEEP_SEC] seconds behind what is heard is
 * forgotten again, so a long set stays as small as a short one.
 *
 * Where the playback's pattern or tempo changes while it plays (it says so: `KlangPlaybackSignal.PatternUpdated`),
 * what was read ahead will not sound as it was read. The stretch ahead is then read anew, and put in the place of
 * the old one in one go: what began before stays (it has sounded), what starts from then on is the new answer. The
 * band's bar length stays the one of the tempo the reading began at.
 *
 * Known limits, for now. The band plays the parts it knows by their tags (the ones of Der Schmetterling); a song
 * without them shows the band standing. While the page is hidden the song is read and forgotten on, but nobody
 * plans: seen again, the band takes up as if it had begun there (where a hand was, and when the frontwoman played
 * last, is not known to it). A playback that is stopped forgets who listens to it: a reading ends with its playback.
 */
class SongReading private constructor(
    /** The song as far as it is read: what the band plays. */
    val song: Song,
    private val playback: KlangCyclicPlayback,
    /** The next cycle to read. */
    private var to: Int,
) {
    /** Counts the playback's changes of pattern or tempo. */
    private var changes = 0

    /** The count of [changes] the stretch ahead was read at. */
    private var readAt = 0

    private var unsubscribe: (() -> Unit)? = null

    /**
     * Reads on for as long as it is followed (until the coroutine is cancelled); then it listens no more. Launch it
     * undispatched: a coroutine cancelled before it first ran would never come here, and the reading would listen on.
     */
    suspend fun follow() {
        try {
            while (true) {
                delay(FOLLOW_MS)
                readOn()
            }
        } finally {
            close()
        }
    }

    /** No longer listens to the playback's changes. Done by [follow] when it ends; for a reading never followed. */
    fun close() {
        unsubscribe?.invoke()
        unsubscribe = null
    }

    /**
     * Reads up to as far ahead of what is heard as it is read, a whole cycle at a time, and lets the page breathe
     * between two. After a change of the playback the stretch ahead is read anew first (see the class). A cycle the
     * playback cannot answer is empty, as it is for the sound; the playback says why.
     */
    suspend fun readOn() {
        while (true) {
            val round = changes

            if (round != readAt && !readAnew(round)) {
                continue
            }

            val now = playback.heardCycles() ?: 0.0

            // fallen behind what is heard (the page was hidden): what is past is not read any more
            if (to < floor(now) - 1) {
                to = floor(now).toInt() - 1
            }

            val until = now + ahead(now)

            while (to < until && changes == round) {
                val events = read(to)

                // in one go with what is new, so the band plans once: what lies well behind what is heard is forgotten
                song.forget(before = (playback.heardSec() ?: 0.0) - KEEP_SEC)
                song.add(events)
                to++
                delay(1)
            }

            if (changes == round) {
                return
            }
        }
    }

    /**
     * Reads the stretch from the cycle heard on anew and puts it in the place of what was read ahead: false where
     * the playback changed again meanwhile (nothing is put then, and it is to be read once more).
     */
    private suspend fun readAnew(round: Int): Boolean {
        val now = playback.heardCycles() ?: 0.0
        val until = now + ahead(now)
        val fresh = mutableListOf<RecordedEvent>()
        var cycle = max(0, floor(now).toInt())

        while (cycle < until) {
            fresh += read(cycle)
            cycle++
            delay(1)

            if (changes != round) {
                return false
            }
        }

        // what starts from now on is the new answer's; what began before has sounded and stays
        val from = playback.heardSec() ?: 0.0

        song.drop(from = from)
        song.add(fresh.filter { it.t >= from })
        to = cycle
        readAt = round

        return true
    }

    /** How far ahead of the cycle [now] the song is read, in cycles. */
    private fun ahead(now: Double): Double = min(MOST_AHEAD, max(AHEAD, AHEAD_SEC * playback.rpmAt(now) / 60.0))

    /** The band's events of a [cycle]: the tagged notes, as the playback places them. */
    private fun read(cycle: Int): List<RecordedEvent> = playback.cycleEvents(cycle).mapNotNull { it.forTheBand() }

    private fun KlangCycleEvent.forTheBand(): RecordedEvent? {
        val tags = data.tags?.sorted().orEmpty()

        if (tags.isEmpty() || data.control == true) {
            return null
        }

        return RecordedEvent(
            t = startSec,
            d = endSec - startSec,
            c = startCycles,
            tags = tags,
            note = data.note,
            freq = data.freqHz?.takeIf { it.isFinite() },
            gain = data.gain?.takeIf { it.isFinite() },
            sound = data.sound,
            index = data.soundIndex,
            pan = data.pan?.takeIf { it.isFinite() },
        )
    }

    companion object {
        /** How far ahead of what is heard the song is read, in cycles. */
        private const val AHEAD = 8.0

        /**
         * And never less than this, in seconds: the frontwoman must know her next note thirteen seconds ahead to come
         * back from a stroll without a jump (see TrumpetPlayer), and a look between two players takes nine.
         */
        private const val AHEAD_SEC = 14.0

        /**
         * And never more than this, in cycles (above 137 cycles a minute that is less than [AHEAD_SEC]): the playback
         * keeps a cycle's answer for the sound and the band alike in a store of 64, and what the band reads must not
         * push out what the sound has not asked yet.
         */
        private const val MOST_AHEAD = 32.0

        /**
         * How long what was heard is kept, in seconds. The players look back a few seconds; what they need of longer
         * ago (their last note, a look the camera has begun) they keep themselves.
         */
        private const val KEEP_SEC = 16.0

        /** How often it is looked whether the song is to be read on, in milliseconds. */
        private const val FOLLOW_MS = 200L

        /**
         * Begins to read the song a [playback] plays: its events from where it is heard (from its first cycle before
         * it has begun) as far ahead as it is read, at the tempo the playback has there. The playback may be one that
         * is made and not started yet: set its tempo first (`updateRpm`), so the cycles read are the ones that sound.
         * [startsAt]: the moment on the show's clock at which the playback begins to sound (a show's next song).
         */
        suspend fun begin(playback: KlangCyclicPlayback, startsAt: Double = 0.0): SongReading {
            val now = playback.heardCycles() ?: 0.0
            // a bar back as well: a note that began just before still sounds
            val reading = SongReading(
                song = Song(rpm = playback.rpmAt(now), startsAt = startsAt),
                playback = playback,
                to = max(0, floor(now).toInt() - 1),
            )

            // listening before the first cycle is asked: no change is missed
            reading.unsubscribe = playback.signals.subscribeToStream { signal ->
                if (signal is KlangPlaybackSignal.PatternUpdated) {
                    reading.changes++
                }
            }
            // what the stream says of the time before (its last signal) is no change of this reading's
            reading.readAt = reading.changes

            try {
                reading.readOn()
            } catch (e: Throwable) {
                // given up (or failed) before it was read: nobody will follow it
                reading.close()
                throw e
            }

            return reading
        }
    }
}
