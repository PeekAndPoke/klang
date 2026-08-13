/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.MasterDsl
import io.peekandpoke.klang.audio_bridge.MasterStageDsl
import io.peekandpoke.klang.audio_bridge.MasterValue
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The `master(…)` authoring surface — see `docs/tasks/master-dsl.md`.
 *
 * The top-level form is a **control carrier** (one silent event per cycle); the mapper forms stamp
 * the reference onto sounding events.
 */
class LangMasterSpec : StringSpec({

    val loud = MasterDsl.of(MasterStageDsl.Gain(gain = 2.0))

    "master() emits one control event per cycle carrying only the master" {
        val events = master(loud).queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.master shouldBe MasterValue.Dsl(loud)
        events[0].data.control shouldBe true
        // Nothing else — the carrier must not accidentally sound.
        events[0].data.sound.shouldBeNull()
        events[0].data.freqHz.shouldBeNull()
    }

    "master() keeps emitting across cycles" {
        val events = master(loud).queryArc(0.0, 4.0)

        events.size shouldBe 4
        events.forEach { it.data.control shouldBe true }
    }

    "pattern.master() stamps sounding events — they still sound" {
        val events = note("c3 e3").master(loud).queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.forEach {
            it.data.master shouldBe MasterValue.Dsl(loud)
            // NOT a control event: the note plays, the master swap just rides along.
            it.data.control.shouldBeNull()
        }
    }

    "master dsl interface — pattern / string / chained-mapper forms agree" {
        val pat = "c3"

        listOf(
            "pattern.master(v)" to note(pat).master(loud),
            "string.master(v)" to pat.master(loud),
            "chained mapper .master(v)" to note(pat).apply(gain(0.5).master(loud)),
        ).forEach { (label, pattern) ->
            withClue(label) {
                val events = pattern.queryArc(0.0, 1.0)
                events.shouldNotBeEmpty()
                events[0].data.master shouldBe MasterValue.Dsl(loud)
            }
        }
    }

    "master() is available from KlangScript with the same result" {
        val kotlinEvents = master(MasterDsl.of(MasterStageDsl.Gain(gain = 2.5))).queryArc(0.0, 1.0)
        val scriptEvents = SprudelPattern
            .compile("""master(Master.of(MasterFx.gain(2.5)))""")!!
            .queryArc(0.0, 1.0)

        scriptEvents.size shouldBe kotlinEvents.size
        scriptEvents[0].data.master shouldBe kotlinEvents[0].data.master
        scriptEvents[0].data.control shouldBe true
    }

    "chained MasterFx config reaches the dsl (KlangScript == Kotlin)" {
        val expected = MasterDsl.of(
            MasterStageDsl.Gain(gain = 1.5),
            MasterStageDsl.Limiter(thresholdDb = -0.5),
        )
        val scriptEvents = SprudelPattern
            .compile("""master(Master.of(MasterFx.gain(1.5), MasterFx.limiter().thresholdDb(-0.5)))""")!!
            .queryArc(0.0, 1.0)

        scriptEvents[0].data.master shouldBe MasterValue.Dsl(expected)
    }

    "inline master denormalizes to its synthetic name on the wire" {
        val events = master(loud).queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.master shouldBe loud.uniqueId()
        voiceData.control shouldBe true
    }

    "a pattern without master() carries none" {
        val events = note("c3").queryArc(0.0, 1.0)

        events[0].data.master.shouldBeNull()
        events[0].data.control.shouldBeNull()
        events[0].data.toVoiceData().master.shouldBeNull()
    }

    "control does not merge — a master carrier cannot silence real notes" {
        val carrier = master(loud).queryArc(0.0, 1.0)[0].data
        val note = note("c3").queryArc(0.0, 1.0)[0].data

        carrier.control shouldBe true

        // Merging the carrier into a sounding event must NOT make that event control-only;
        // otherwise composing them would mute the music. (Same rule as patternId: `control` is a
        // property of the carrier itself, never inherited.)
        val merged = note.merge(carrier)
        merged.control.shouldBeNull()
        merged.master shouldBe MasterValue.Dsl(loud)

        // ...and the in-place mirror must agree (guarded against drift by SprudelVoiceDataSpec).
        val inPlace = note("c3").queryArc(0.0, 1.0)[0].data
        inPlace.mergeFrom(carrier)
        inPlace.control.shouldBeNull()
        inPlace.master shouldBe MasterValue.Dsl(loud)
    }

    "Master.default() is the explicit way back to unity" {
        val kotlinEvents = master(MasterDsl.default).queryArc(0.0, 1.0)
        val scriptEvents = SprudelPattern
            .compile("""master(Master.default())""")!!
            .queryArc(0.0, 1.0)

        // Same chain from both languages, and it really is the unity chain — the one a playback
        // runs when no master(...) is present at all.
        scriptEvents[0].data.master shouldBe kotlinEvents[0].data.master
        scriptEvents[0].data.master shouldBe MasterValue.Dsl(MasterDsl(emptyList()))
        scriptEvents[0].data.control shouldBe true
    }

    "the full reverb vocabulary round-trips from KlangScript (== the Kotlin builder)" {
        val expected = MasterDsl.of(
            MasterStageDsl.Reverb(
                wet = 0.3, roomSize = 8.0, damp = 0.4,
                roomFade = 0.12, roomLp = 6000.0,
            ),
            MasterStageDsl.Delay(wet = 0.2, timeSeconds = 0.5, feedback = 1.0, cap = 3.0),
        )
        val script = SprudelPattern.compile(
            """master(Master.of(
                 MasterFx.reverb().wet(0.3).roomSize(8).damp(0.4).roomFade(0.12).roomLp(6000),
                 MasterFx.delay().wet(0.2).time(0.5).feedback(1.0).cap(3.0)
               ))"""
        )!!.queryArc(0.0, 1.0)

        script[0].data.master shouldBe MasterValue.Dsl(expected)
    }
})
