/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

/**
 * The replace path's dedup, [dropResentDuplicates], against the rule it replaced (audit L2-09, L9-10): the scheduler
 * compared every incoming voice with every active one. That quadratic matching is kept HERE as the oracle, word for
 * word, and the two must keep and drop the same voices, in the same order, on random inputs shaped to hit every case:
 * chords (several actives on one start), resends of the same voice (multiplicity), changed data, realtime voices
 * (null sources), `-0.0` against `0.0`, NaN starts.
 */
class ReplaceDedupSpec : StringSpec({

    /** The pre-L2-09 matching, the scheduler's `dedupAgainstActive` as it was: the oracle. */
    fun oracle(voices: List<ScheduledVoice>, active: List<ScheduledVoice?>): List<ScheduledVoice> {
        if (voices.isEmpty() || active.isEmpty()) return voices
        val claimed = BooleanArray(active.size)
        return voices.filter { incoming ->
            val match = active.indices.firstOrNull { i ->
                !claimed[i] && active[i]?.isDuplicate(incoming) == true
            }
            if (match != null) {
                claimed[match] = true
                false
            } else {
                true
            }
        }
    }

    val starts = listOf(0.0, -0.0, 0.125, 0.25, 0.375, 0.5, 1.0, Double.NaN)
    val notes = listOf("c", "e", "g")

    fun voice(random: Random, startPool: Int): ScheduledVoice {
        val start = starts[random.nextInt(startPool)]

        return ScheduledVoice(
            playbackId = "p",
            data = VoiceData.empty.copy(note = notes[random.nextInt(notes.size)], sound = "sine"),
            startTime = start,
            // Not part of the identity: varied so a match cannot lean on it
            gateEndTime = start + random.nextInt(3),
            playbackStartTime = 0.0,
        )
    }

    "the same voices kept, in the same order, as the quadratic rule, on 20000 random inputs" {
        val random = Random(4207)

        repeat(20000) { round ->
            val startPool = 1 + random.nextInt(starts.size)
            val active = List(random.nextInt(12)) { if (random.nextInt(5) == 0) null else voice(random, startPool) }
            // Incoming: fresh voices and resends of active ones (the frontend's resend after an edit)
            val incoming = List(random.nextInt(12)) {
                val source = active.getOrNull(random.nextInt(active.size + 1))

                if (source != null && random.nextBoolean()) source.copy(gateEndTime = source.gateEndTime + 1.0) else voice(random, startPool)
            }

            val expected = oracle(incoming, active)
            val actual = dropResentDuplicates(incoming = incoming, active = active)

            withClue("round $round") {
                actual.size shouldBe expected.size
                actual.indices.all { actual[it] === expected[it] } shouldBe true
            }
        }
    }

    "the frontend's real shape, every incoming start after every active one, keeps everything" {
        val active = List(100) { ScheduledVoice(playbackId = "p", data = VoiceData.empty, startTime = it * 0.01, gateEndTime = 9.0, playbackStartTime = 0.0) }
        val incoming = List(100) { ScheduledVoice(playbackId = "p", data = VoiceData.empty, startTime = 2.0 + it * 0.01, gateEndTime = 9.0, playbackStartTime = 0.0) }

        dropResentDuplicates(incoming = incoming, active = active) shouldBe incoming
    }

    "a resent chord drops exactly as many voices as are playing, 1 to 1" {
        val tone = ScheduledVoice(playbackId = "p", data = VoiceData.empty.copy(note = "c"), startTime = 1.0, gateEndTime = 2.0, playbackStartTime = 0.0)
        val other = tone.copy(data = tone.data.copy(note = "e"))
        val active = listOf(tone, null, other, tone)
        val incoming = listOf(tone, tone, tone, other, other)

        dropResentDuplicates(incoming = incoming, active = active) shouldBe listOf(tone, other)
    }
})
