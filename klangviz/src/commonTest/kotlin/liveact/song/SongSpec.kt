/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.song

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private fun event(t: Double, tag: String, sound: String? = null, note: String? = null, freq: Double? = null, gain: Double? = null, d: Double = 0.1) =
    RecordedEvent(t = t, d = d, tags = listOf(tag), note = note, freq = freq, gain = gain, sound = sound)

private fun song(vararg events: RecordedEvent) = Song(Recording(rpm = 30.0, events = events.toList()))

class SongSpec : StringSpec({

    "a recording is read from its JSON, fields it does not know left aside" {
        val recording = Recording.parse("""{"rpm": 36.5, "more": 1, "events": [{"t": 1.5, "d": 0.25, "tags": ["kick"], "new": true}]}""")

        recording.rpm shouldBe 36.5
        recording.events.single().tags shouldBe listOf("kick")
        Song(recording).bpm shouldBe 146.0
    }

    "an event's midi number comes from its frequency, else from its note's name" {
        Song.midiOf(event(t = 0.0, tag = "x", freq = 440.0)) shouldBe 69
        Song.midiOf(event(t = 0.0, tag = "x", freq = 146.83, note = "C1")) shouldBe 50
        Song.midiOf(event(t = 0.0, tag = "x", note = "c4")) shouldBe 60
        Song.midiOf(event(t = 0.0, tag = "x", note = "F#5")) shouldBe 78
        Song.midiOf(event(t = 0.0, tag = "x", note = "eb4")) shouldBe 63
        Song.midiOf(event(t = 0.0, tag = "x", note = "c-1")) shouldBe 0
        Song.midiOf(event(t = 0.0, tag = "x", note = "h4")) shouldBe null
        Song.midiOf(event(t = 0.0, tag = "x")) shouldBe null
    }

    "how hard an event is played is its gain against the loudest of its tag, never under a quarter" {
        val s = song(event(0.0, "bass", gain = 0.2, note = "e1"), event(1.0, "bass", gain = 0.1, note = "e1"), event(2.0, "bass", gain = 0.01, note = "e1"))

        s.byTag.getValue("bass").map { it.v } shouldBe listOf(1.0, 0.5, 0.25)
    }

    "a part doubled left and right is one hit: a kick 30 ms or less after another is left out" {
        val s = song(event(1.0, "kick"), event(1.004, "kick"), event(1.031, "kick"), event(2.0, "kick"))

        // 1.031 follows 1.004 by 27 ms and is left out too: each is measured against the one before it
        s.drumHits().map { it.t } shouldBe listOf(1.0, 2.0)
    }

    "the kick is played with both feet: the right one leads, and in a run the two take turns" {
        val s = song(event(1.0, "kick"), event(1.2, "kick"), event(1.4, "kick"), event(2.0, "kick"), event(2.44, "kick"))

        s.drumHits().map { it.limb } shouldBe listOf(Limb.KickR, Limb.KickL, Limb.KickR, Limb.KickR, Limb.KickL)
    }

    "a fast run on the snare is played by both hands in turn, a slow one by the left" {
        val s = song(event(1.0, "snare"), event(1.1, "snare"), event(1.2, "snare"), event(2.0, "snare"), event(2.3, "snare"))

        s.drumHits().map { it.limb } shouldBe listOf(Limb.L, Limb.R, Limb.L, Limb.L, Limb.L)
    }

    "the hats: which cymbal by the sound's name, the right hand's, a crash the left hand's when it is free" {
        val s = song(
            event(1.0, "hats", sound = "hh"), event(2.0, "hats", sound = "oh"), event(3.0, "hats", sound = "cr"),
            event(4.0, "hats", sound = "rd"), event(5.0, "countin", sound = "oh"),
            // a snare just then: the left hand is not free, the crash is the right hand's
            event(6.0, "snare"), event(6.1, "hats", sound = "cr"),
        )
        val hits = s.drumHits().filter { it.drum != Drum.Snare }

        hits.map { it.drum } shouldBe listOf(Drum.Hat, Drum.Hat2, Drum.Crash, Drum.Ride, Drum.Hat2, Drum.Crash)
        hits.map { it.limb } shouldBe listOf(Limb.R, Limb.L, Limb.L, Limb.R, Limb.L, Limb.R)
    }

    "a cymbal a hand cannot reach in time is left out" {
        val s = song(event(1.0, "hats", sound = "hh"), event(1.1, "hats", sound = "rd"), event(1.2, "hats", sound = "rd"))

        // the ride at 1.1 comes 100 ms after the hat, on the same hand: left out; the one at 1.2 is played
        s.drumHits().map { it.t } shouldBe listOf(1.0, 1.2)
    }

    "a player's notes: those of its tag with a pitch, doubles of the same pitch merged" {
        val s = song(
            event(1.0, "lead", note = "c4", d = 0.5), event(1.01, "lead", note = "c4"), event(1.02, "lead", note = "e4"),
            event(2.0, "lead"), event(3.0, "other", note = "g4"),
        )

        s.notesOf("lead") shouldBe listOf(Note(t = 1.0, midi = 60, v = 1.0, d = 0.5), Note(t = 1.02, midi = 64, v = 1.0, d = 0.1))
        s.notesOf("none") shouldBe emptyList()
    }

    "a string player's strums: what sounds together is one, its lowest note leading, across its tags" {
        val s = song(
            event(1.0, "guitar2", note = "e3", gain = 0.5, d = 0.2), event(1.01, "guitar3", note = "c3", gain = 1.0, d = 0.4),
            event(1.02, "guitar2", note = "e3"), event(2.0, "guitar2", note = "g3", gain = 0.5),
        )
        val strums = s.strumsOf(listOf("guitar2", "guitar3"))

        strums.size shouldBe 2
        strums[0] shouldBe Strum(t = 1.0, d = 0.4, midi = 48, v = 1.0, all = listOf(52, 48))
        strums[1].all shouldBe listOf(55)
    }

    "when the events with some tags start, sorted across the tags" {
        val s = song(event(3.0, "a"), event(1.0, "b"), event(2.0, "a"), event(9.0, "c"))

        s.times(listOf("a", "b")) shouldBe listOf(1.0, 2.0, 3.0)
    }
})
