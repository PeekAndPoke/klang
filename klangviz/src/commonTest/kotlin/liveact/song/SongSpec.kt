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

    "a song that grows reads as the same song given whole, once all of it is added" {
        val events = listOf(
            event(0.0, "kick"), event(0.2, "kick"), event(0.5, "snare"), event(0.55, "hats", sound = "cr"),
            event(1.0, "kick"), event(1.1, "snare"), event(1.15, "hats", sound = "oh"), event(1.2, "hats", sound = "hh"),
            // (the loudest of a part comes first here: a strength is measured against the loudest known when it is added)
            event(1.5, "lead", note = "c4", gain = 1.0), event(2.5, "lead", note = "e4", gain = 0.5),
            event(1.5, "bass", note = "e1"), event(2.5, "bass", note = "g1"),
        )
        val whole = Song(Recording(rpm = 30.0, events = events))
        val grown = Song(rpm = 30.0)

        grown.add(events.filter { it.t < 1.0 })
        grown.add(events.filter { it.t >= 1.0 && it.t < 2.0 })
        grown.add(events.filter { it.t >= 2.0 })

        grown.drumHits() shouldBe whole.drumHits()
        grown.notesOf("lead") shouldBe whole.notesOf("lead")
        grown.strumsOf(listOf("bass")) shouldBe whole.strumsOf(listOf("bass"))
        grown.times(listOf("kick", "snare")) shouldBe whole.times(listOf("kick", "snare"))
        grown.bpm shouldBe whole.bpm
    }

    "a song that grows is read anew after each addition, and not when nothing was added" {
        val s = Song(rpm = 30.0)

        s.version shouldBe 0
        s.notesOf("lead") shouldBe emptyList()
        s.drumHits() shouldBe emptyList()
        s.strumsOf(listOf("lead")) shouldBe emptyList()

        s.add(listOf(event(1.0, "lead", note = "c4")))
        s.version shouldBe 1
        s.notesOf("lead").map { it.midi } shouldBe listOf(60)
        s.times(listOf("lead")) shouldBe listOf(1.0)

        s.add(emptyList())
        s.version shouldBe 1

        s.add(listOf(event(2.0, "lead", note = "e4"), event(2.0, "kick")))
        s.version shouldBe 2
        s.notesOf("lead").map { it.midi } shouldBe listOf(60, 64)
        s.times(listOf("lead")) shouldBe listOf(1.0, 2.0)
        s.drumHits().map { it.t } shouldBe listOf(2.0)
        s.strumsOf(listOf("lead")).map { it.midi } shouldBe listOf(60, 64)
    }

    "a part is read again only where its song has grown" {
        val s = Song(rpm = 30.0)
        val part = s.part { notesOf("lead") }

        part.grows shouldBe true
        part.refresh() shouldBe true
        part.events shouldBe emptyList()
        part.refresh() shouldBe false

        s.add(listOf(event(1.0, "lead", note = "c4")))
        part.events shouldBe emptyList()
        part.refresh() shouldBe true
        part.events.map { it.midi } shouldBe listOf(60)
        part.refresh() shouldBe false

        val fixed = Part.fixed(listOf(1, 2))

        fixed.grows shouldBe false
        fixed.refresh() shouldBe true
        fixed.events shouldBe listOf(1, 2)
        fixed.refresh() shouldBe false
        Song(Recording(rpm = 30.0, events = emptyList())).part { drumHits() }.grows shouldBe false
    }

    "a hand is busy with what it plays within the given time, whatever it plays before and after" {
        // the crash at 25.1 finds the left hand busy with the snare at 25.0, among the many before and after it
        val snares = (0 until 50).map { event(it * 1.0, "snare") }
        val crashes = listOf(event(25.1, "hats", sound = "cr"), event(60.0, "hats", sound = "cr"))
        val s = song(*(snares + crashes).toTypedArray())

        s.drumHits().filter { it.drum == Drum.Crash }.map { it.limb } shouldBe listOf(Limb.R, Limb.L)
    }

    "a hand is busy up to the given time and not at it, measured as the difference of the two moments" {
        // 1.5599999999999998 - 1.44 is a little under 0.12 as a floating number: the hand is still busy
        val s = song(event(1.44, "hats", sound = "hh"), event(1.5599999999999998, "hats", sound = "hh"))

        s.drumHits().map { it.t } shouldBe listOf(1.44)
    }

    "how hard an event is played stays as it was when a louder one is added later" {
        val s = Song(rpm = 30.0)

        s.add(listOf(event(0.0, "bass", gain = 0.2, note = "e1"), event(1.0, "bass", gain = 0.1, note = "e1")))
        s.add(listOf(event(2.0, "bass", gain = 0.4, note = "e1"), event(3.0, "bass", gain = 0.2, note = "e1")))

        s.byTag.getValue("bass").map { it.v } shouldBe listOf(1.0, 0.5, 1.0, 0.5)
    }
})
