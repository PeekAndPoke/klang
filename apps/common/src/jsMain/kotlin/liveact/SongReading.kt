/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.max

/**
 * A song read ahead for the live act while it plays (KLANG-00449): its code compiled once more and asked for its
 * events as it plays, into [song], the band's.
 *
 * The band needs the song a little ahead of the sound: it is read always [AHEAD] cycles ahead of what is heard (and
 * never less than [AHEAD_SEC] seconds: a body's moves take seconds, however fast the song), a whole cycle at a time
 * (as the player asks it). It begins from the code and the tempo alone ([begin]), so it can be read before a playback
 * has begun (a walk-in needs the first notes before the sound: a player gets ready for them). What lies more than
 * [KEEP_SEC] seconds behind what is heard is forgotten again, so a long set stays as small as a short one.
 *
 * Known limits, for now. Asking a song again gives other events where the song's seed is another at every asking, so
 * a part that draws at random under it (Der Schmetterling's marimba) is not played note for note (KLANG-00450). The
 * band plays the parts it knows by their tags (the ones of Der Schmetterling); a song without them shows the band
 * standing. While the page is hidden the song is read and forgotten on, but nobody plans: seen again, the band takes
 * up as if it had begun there (where a hand was, and when the frontwoman played last, is not known to it).
 */
class SongReading private constructor(
    /** The song as far as it is read: what the band plays. */
    val song: Song,
    private val pattern: KlangPattern,
    private val cps: Double,
    /** How far it is read, in cycles. */
    private var to: Double,
) {
    /** How far ahead of what is heard it is read, in cycles. */
    private val ahead: Double = max(AHEAD, AHEAD_SEC * cps)

    /** Whether a cycle that could not be read was told of already. */
    private var failed: Boolean = false

    /** Reads on for as long as it is followed (until the coroutine is cancelled): ahead of [heardSec]. */
    suspend fun follow(heardSec: () -> Double) {
        while (true) {
            delay(FOLLOW_MS)
            readOn(heardSec())
        }
    }

    /**
     * Reads up to as far ahead of [heardSec] (the seconds of the song heard so far) as it is read, a whole cycle at a
     * time (asked in other pieces, a pattern can give other events: KLANG-00450), and lets the page breathe between
     * them. A cycle that cannot be read is left out, as the player leaves it out, and told of once.
     */
    suspend fun readOn(heardSec: Double) {
        val now = heardSec * cps

        // fallen behind what is heard (the page was hidden): what is past is not read any more
        if (to < floor(now) - 1) {
            to = floor(now) - 1
        }

        while (to < now + ahead) {
            val from = to
            val events = try {
                pattern.queryEvents(fromCycles = from, toCycles = from + 1, cps = cps).mapNotNull { event ->
                    val data = event.toVoiceData()
                    val tags = data.tags?.sorted().orEmpty()

                    if (tags.isEmpty() || data.control == true) {
                        null
                    } else {
                        RecordedEvent(
                            t = event.startCycles / cps,
                            d = event.durationCycles / cps,
                            c = event.startCycles,
                            tags = tags,
                            note = data.note,
                            freq = data.freqHz?.takeIf { it.isFinite() },
                            gain = data.gain?.takeIf { it.isFinite() },
                            sound = data.sound,
                            index = data.soundIndex,
                            pan = data.pan?.takeIf { it.isFinite() },
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!failed) {
                    failed = true
                    console.error("The live act could not read cycle ${from.toInt()} of the playing song", e)
                }

                emptyList()
            }

            // in one go with what is new, so the band plans once: what lies well behind what is heard is forgotten
            song.forget(before = now / cps - KEEP_SEC)
            song.add(events)
            to = from + 1
            delay(1)
        }
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
         * How long what was heard is kept, in seconds. The players look back a few seconds; what they need of longer
         * ago (their last note, a look the camera has begun) they keep themselves.
         */
        private const val KEEP_SEC = 16.0

        /** How often it is looked whether the song is to be read on, in milliseconds. */
        private const val FOLLOW_MS = 200L

        /**
         * Begins to read a song: [code] compiled on [engine], and its events from where [heardSec] is (0 before the
         * playback has begun) as far ahead as it is read, at [rpm]. Null where the code gives no pattern; it throws
         * where the code cannot be compiled.
         */
        suspend fun begin(
            engine: KlangScriptEngine,
            code: String,
            rpm: Double,
            heardSec: Double,
        ): SongReading? {
            // compiled once more, on an engine of its own; what the build swallows is said by whoever compiled the song
            // to play it (the studio's editor, notstrom's start screen), so it is gathered here and dropped
            val pattern: KlangPattern = SprudelDiagnostics.collectingInto(mutableListOf()) {
                SprudelPattern.compile(engine, code)
            } ?: return null
            val cps = rpm / 60.0
            val now = heardSec * cps
            // a bar back as well: a note that began just before still sounds
            val reading = SongReading(
                song = Song(rpm = rpm),
                pattern = pattern,
                cps = cps,
                to = max(0.0, floor(now) - 1),
            )

            reading.readOn(heardSec)

            return reading
        }
    }
}
