/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.viz.liveact.song.Envelope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Four tagged notes a cycle; at 60 rpm a cycle is a second, so the seconds read are the cycles read. */
private fun playing(heard: Double? = null): FakePlayback =
    FakePlayback(rpm = 60.0, events = { cycleOf(it) }).also { it.heard = heard }

private fun SongReading.leadTimes(): List<Double> = song.times(listOf("lead"))

/** Voice params as an event carries them. */
private fun params(vararg values: Pair<String, Double>): KlangMap<Double> = KlangMap<Double>().also { map ->
    for ((key, value) in values) {
        map[key] = value
    }
}

class SongReadingSpec : StringSpec({

    "before a playback has begun, its first stretch is read, each cycle asked once" {
        val playback = playing()
        val reading = SongReading.begin(playback)
        val times = reading.leadTimes()

        // AHEAD_SEC (14 s) is more than AHEAD (8 cycles) at a cycle a second: cycles 0 to 13, four notes each
        times.size shouldBe 14 * 4
        times.first() shouldBe 0.0
        times.last() shouldBeLessThan 14.0
        times.last() shouldBeGreaterThanOrEqual 13.0
        playback.asked shouldBe (0..13).toList()
    }

    "read on, it stays ahead of what is heard" {
        val playback = playing()
        val reading = SongReading.begin(playback)

        playback.heard = 10.0
        reading.readOn()

        reading.leadTimes().size shouldBe 24 * 4
        reading.leadTimes().last() shouldBeGreaterThanOrEqual 23.0
        playback.asked shouldBe (0..23).toList()
    }

    "fallen behind what is heard, the past is not read, and what lies well behind is forgotten" {
        val playback = playing()
        val reading = SongReading.begin(playback)

        playback.heard = 30.0
        reading.readOn()

        val times = reading.leadTimes()

        // read on from a bar before what is heard (29) to 14 s ahead, nothing of 14 to 28; and what was read before
        // (up to 14) lies before 30 - KEEP_SEC (16 s): forgotten
        times.first() shouldBe 29.0
        times.size shouldBe 15 * 4
        times.last() shouldBeGreaterThanOrEqual 43.0
    }

    "a song that plays already is read from a bar before what is heard" {
        val reading = SongReading.begin(playing(heard = 5.5))

        reading.leadTimes().first() shouldBe 4.0
    }

    "the seconds are the playback's, and a fast song is read 14 seconds ahead" {
        // 120 rpm: a cycle is half a second, 14 s are 28 cycles (more than the 8 it is read ahead at least)
        val playback = FakePlayback(rpm = 120.0, events = { cycleOf(cycle = it, rpm = 120.0, perCycle = 1) })
        val reading = SongReading.begin(playback)

        reading.leadTimes() shouldBe List(28) { it * 0.5 }
        reading.song.bpm shouldBe 480.0
    }

    "a very fast song is read 32 cycles ahead and no more: the playback's store is the sound's as well" {
        // 240 rpm: 14 s are 56 cycles
        val playback = FakePlayback(rpm = 240.0, events = { cycleOf(cycle = it, rpm = 240.0, perCycle = 1) })

        SongReading.begin(playback)

        playback.asked shouldBe (0..31).toList()
    }

    "a slow song is read 8 cycles ahead" {
        // 15 rpm: a cycle is four seconds, 14 s are 3.5 cycles
        val playback = FakePlayback(rpm = 15.0, events = { cycleOf(cycle = it, rpm = 15.0, perCycle = 1) })
        val reading = SongReading.begin(playback)

        reading.leadTimes() shouldBe List(8) { it * 4.0 }
    }

    "events without a tag are not the band's" {
        val reading = SongReading.begin(FakePlayback(events = { cycleOf(cycle = it, tag = null) }))

        reading.leadTimes().shouldBeEmpty()
        reading.song.version shouldBe 0
    }

    "control events are not the band's, even tagged: only the notes are read" {
        val playback = FakePlayback(
            events = { cycleOf(it) + cycleOf(cycle = it, perCycle = 1, control = true) },
        )

        SongReading.begin(playback).leadTimes().size shouldBe 14 * 4
    }

    "after a change of the playback, what starts from now on is the new answer, and what began before stays" {
        val playback = playing()
        val reading = SongReading.begin(playback)

        playback.heard = 2.6
        playback.events = { cycleOf(cycle = it, perCycle = 2, note = "e3") }
        playback.asked.clear()
        playback.changed()
        reading.readOn()

        val notes = reading.song.notesOf("lead")

        // the old answer up to what is heard (four notes a cycle, c3), the new one from there (two a cycle, e3)
        notes.filter { it.t < 2.6 }.map { it.t } shouldBe listOf(0.0, 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.25, 2.5)
        notes.filter { it.t < 2.6 }.map { it.midi }.distinct() shouldBe listOf(48)
        notes.filter { it.t >= 2.6 }.map { it.t } shouldBe List(28) { 3.0 + it * 0.5 }
        notes.filter { it.t >= 2.6 }.map { it.midi }.distinct() shouldBe listOf(52)
        // read anew from the cycle heard, 14 s ahead of it, and nothing twice
        playback.asked shouldBe (2..16).toList()
    }

    "without a change nothing is read anew" {
        val playback = playing()
        val reading = SongReading.begin(playback)
        val version = reading.song.version

        playback.asked.clear()
        reading.readOn()

        playback.asked.shouldBeEmpty()
        reading.song.version shouldBe version
    }

    "a change said before the reading began is none of its own: nothing is read anew" {
        val playback = playing(heard = 5.5)

        // a stream says its last signal to whoever begins to listen
        playback.changed()

        val reading = SongReading.begin(playback)

        // from a bar before what is heard, as without it; read anew, it would begin at the cycle heard (5.0)
        reading.leadTimes().first() shouldBe 4.0
        playback.asked shouldBe (4..19).toList()
    }

    "a change while the stretch is read anew: only the newest answer is put" {
        val playback = playing()
        val reading = SongReading.begin(playback)
        var again = true

        playback.heard = 2.0
        playback.asked.clear()
        playback.events = { cycle ->
            // the playback changes once more while its cycle 5 is asked
            if (again && cycle == 5) {
                again = false
                playback.events = { cycleOf(cycle = it, perCycle = 1, note = "g3") }
                playback.changed()
            }

            cycleOf(cycle = cycle, perCycle = 2, note = "e3")
        }
        playback.changed()
        reading.readOn()

        val ahead = reading.song.notesOf("lead").filter { it.t >= 2.0 }

        // nothing of the answer given up (e3, two a cycle): one g3 a cycle from what is heard on
        ahead.map { it.midi }.distinct() shouldBe listOf(55)
        ahead.map { it.t } shouldBe List(14) { 2.0 + it }
        playback.asked shouldBe (2..5).toList() + (2..15).toList()
    }

    "no longer followed, it hears no change any more" {
        val playback = playing()
        val reading = SongReading.begin(playback)
        val scope = MainScope()
        val following = scope.launch { reading.follow() }

        // let it begin to follow, then give it up
        delay(10)
        following.cancelAndJoin()
        scope.cancel()

        playback.listeners() shouldBe 0
        playback.asked.clear()
        playback.changed()
        reading.readOn()

        playback.asked.shouldBeEmpty()
    }

    "a reading given up while its first stretch is read hears no change any more" {
        val playback = playing()
        val scope = MainScope()
        val beginning = scope.launch { SongReading.begin(playback) }

        // given up in the middle of the first stretch (it breathes between two cycles)
        delay(5)
        playback.listeners() shouldBe 1
        (playback.asked.size < 14) shouldBe true
        beginning.cancelAndJoin()
        scope.cancel()

        playback.listeners() shouldBe 0
    }

    "closed, it hears no change any more" {
        val playback = playing()
        val reading = SongReading.begin(playback)

        reading.close()
        playback.asked.clear()
        playback.changed()
        reading.readOn()

        playback.asked.shouldBeEmpty()
    }

    "a voice's envelope: the pattern's, the engine's defaults where it writes none" {
        SongReading.envelopeOf(params("adsr.attack" to 0.008, "adsr.decay" to 3.0, "adsr.sustain" to 0.5, "adsr.release" to 0.2)) shouldBe
            Envelope(attack = 0.008, decay = 3.0, sustain = 0.5, release = 0.2)
        SongReading.envelopeOf(params("adsr.release" to 2.0)) shouldBe
            Envelope(attack = VOICE_ADSR_ATTACK_SEC, decay = VOICE_ADSR_DECAY_SEC, sustain = VOICE_ADSR_SUSTAIN_LEVEL, release = 2.0)
        SongReading.envelopeOf(null) shouldBe
            Envelope(attack = VOICE_ADSR_ATTACK_SEC, decay = VOICE_ADSR_DECAY_SEC, sustain = VOICE_ADSR_SUSTAIN_LEVEL, release = VOICE_ADSR_RELEASE_SEC)
    }

    "with the pattern's envelope off, the instrument's own as the song writes it, a plucked string's for the rest" {
        SongReading.envelopeOf(params("adsr.on" to 0.0, "attack" to 0.01, "decay" to 1.0, "sustain" to 0.66, "release" to 2.75)) shouldBe
            Envelope(attack = 0.01, decay = 1.0, sustain = 0.66, release = 2.75)
        // Der Schmetterling's guitars write their attack and decay only
        SongReading.envelopeOf(params("adsr.on" to 0.0, "attack" to 0.004, "decay" to 5.0, "adsr.release" to 9.0)) shouldBe
            SongReading.PLUCK.copy(attack = 0.004, decay = 5.0)
        // none of its own written: the band cannot know it
        SongReading.envelopeOf(params("adsr.on" to 0.0, "adsr.decay" to 2.0)) shouldBe null
        SongReading.envelopeOf(params("adsr.on" to 0.0, "release" to Double.NaN)) shouldBe null
    }
})
