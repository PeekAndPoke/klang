/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.KatalystValue
import io.peekandpoke.klang.audio_bridge.uniqueId
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The `master(…)` authoring surface. The master is a [KatalystDsl], the same chain type an orbit
 * runs (phase 3 step 12 C5: the Master DSL retired, one chain type for two positions).
 *
 * The top-level form is a **control carrier** (one silent event per cycle); the mapper forms stamp
 * the reference onto sounding events.
 */
class LangMasterSpec : StringSpec({

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    val loud = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.0)))

    "master() emits one control event per cycle carrying only the master" {
        val events = master(loud).queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.master shouldBe KatalystValue.Dsl(loud)
        events[0].data.control shouldBe true
        // Nothing else: the carrier must not accidentally sound, nor declare an orbit chain.
        events[0].data.sound.shouldBeNull()
        events[0].data.freqHz.shouldBeNull()
        events[0].data.katalyst.shouldBeNull()
    }

    "master() keeps emitting across cycles" {
        val events = master(loud).queryArc(0.0, 4.0)

        events.size shouldBe 4
        events.forEach { it.data.control shouldBe true }
    }

    "each door hands ONE value to every event, so the chain's name is hashed once" {
        val carrier = master(loud).queryArc(0.0, 3.0)
        val mapper = note("c3 e3").apply(gain(0.5).master(loud)).queryArc(0.0, 2.0)

        listOf(Triple("carrier", carrier, 3), Triple("chained mapper", mapper, 4)).forEach { (label, events, size) ->
            withClue(label) {
                events.size shouldBe size
                events.forEach { it.data.master shouldBeSameInstanceAs events[0].data.master }
            }
        }
    }

    "pattern.master() stamps sounding events, they still sound" {
        val events = note("c3 e3").master(loud).queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.forEach {
            it.data.master shouldBe KatalystValue.Dsl(loud)
            // NOT a control event: the note plays, the master swap just rides along.
            it.data.control.shouldBeNull()
        }
    }

    "master dsl interface: pattern / string / chained-mapper forms agree" {
        val pat = "c3"

        listOf(
            "pattern.master(v)" to note(pat).master(loud),
            "string.master(v)" to pat.master(loud),
            "chained mapper .master(v)" to note(pat).apply(gain(0.5).master(loud)),
        ).forEach { (label, pattern) ->
            withClue(label) {
                val events = pattern.queryArc(0.0, 1.0)
                events.shouldNotBeEmpty()
                events[0].data.master shouldBe KatalystValue.Dsl(loud)
            }
        }
    }

    "door parity: every master form takes a Katalyst, KlangScript == Kotlin" {
        val dsl = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(2.5)))

        listOf(
            Triple("carrier", "master(Katalyst(k => k.gain(2.5)))", master(dsl)),
            Triple("pattern", """note("c3").master(Katalyst(k => k.gain(2.5)))""", note("c3").master(dsl)),
            Triple("string", """"c3".master(Katalyst(k => k.gain(2.5)))""", "c3".master(dsl)),
            Triple(
                "chained mapper",
                """note("c3").apply(gain(0.5).master(Katalyst(k => k.gain(2.5))))""",
                note("c3").apply(gain(0.5).master(dsl)),
            ),
        ).forEach { (label, script, kotlin) ->
            withClue(label) {
                val scriptEvents = SprudelPattern.compile(script)!!.queryArc(0.0, 1.0)
                val kotlinEvents = kotlin.queryArc(0.0, 1.0)

                scriptEvents.size shouldBe kotlinEvents.size
                scriptEvents[0].data.master shouldBe KatalystValue.Dsl(dsl)
                scriptEvents[0].data.master shouldBe kotlinEvents[0].data.master
                scriptEvents[0].data.control shouldBe kotlinEvents[0].data.control
            }
        }
    }

    "stage door parameters reach the dsl (KlangScript == Kotlin), the limiter with its own numbers" {
        // Written from literals: the limiter door appends a compressor stage with the limiter's
        // numbers (-1 dB, 20:1, 2 dB knee, 1 ms, 100 ms, no lookahead), here with one overridden.
        val expected = KatalystDsl.of(
            KatalystStageDsl.Gain(gain = c(1.5)),
            KatalystStageDsl.Compressor(
                threshold = c(-0.5), ratio = c(20.0), knee = c(2.0), attack = c(0.001), release = c(0.1),
                lookahead = 0.0,
            ),
        )
        val scriptEvents = SprudelPattern
            .compile("""master(Katalyst(k => k.gain(1.5).limiter(threshold = -0.5)))""")!!
            .queryArc(0.0, 1.0)

        scriptEvents[0].data.master shouldBe KatalystValue.Dsl(expected)
    }

    "inline master denormalizes to its synthetic name, in the ONE Katalyst namespace" {
        val voiceData = master(loud).queryArc(0.0, 1.0)[0].data.toVoiceData()

        voiceData.master shouldBe loud.uniqueId()
        voiceData.control shouldBe true

        // The same chain declared on an orbit is the same name: one registration serves both.
        katalyst(loud).queryArc(0.0, 1.0)[0].data.toVoiceData().katalyst shouldBe voiceData.master
    }

    "a pattern without master() carries none" {
        val events = note("c3").queryArc(0.0, 1.0)

        events[0].data.master.shouldBeNull()
        events[0].data.control.shouldBeNull()
        events[0].data.toVoiceData().master.shouldBeNull()
    }

    "the master and the orbit chain are separate fields: neither door writes the other" {
        val orbit = KatalystDsl.of(KatalystStageDsl.Gain(gain = c(0.5)))
        val data = note("c3").katalyst(orbit).master(loud).queryArc(0.0, 1.0)[0].data

        data.master shouldBe KatalystValue.Dsl(loud)
        data.katalyst shouldBe KatalystValue.Dsl(orbit)
        data.toVoiceData().master shouldBe loud.uniqueId()
        data.toVoiceData().katalyst shouldBe orbit.uniqueId()
    }

    "control does not merge: a master carrier cannot silence real notes" {
        val carrier = master(loud).queryArc(0.0, 1.0)[0].data
        val note = note("c3").queryArc(0.0, 1.0)[0].data

        carrier.control shouldBe true

        // Merging the carrier into a sounding event must NOT make that event control-only;
        // otherwise composing them would mute the music. (Same rule as patternId: `control` is a
        // property of the carrier itself, never inherited.)
        val merged = note.merge(carrier)
        merged.control.shouldBeNull()
        merged.master shouldBe KatalystValue.Dsl(loud)

        // ...and the in-place mirror must agree (guarded against drift by SprudelVoiceDataSpec).
        val inPlace = note("c3").queryArc(0.0, 1.0)[0].data
        inPlace.mergeFrom(carrier)
        inPlace.control.shouldBeNull()
        inPlace.master shouldBe KatalystValue.Dsl(loud)
    }

    "master(Katalyst()) is the explicit way back to unity" {
        val kotlinEvents = master(KatalystDsl(emptyList())).queryArc(0.0, 1.0)
        val scriptEvents = SprudelPattern
            .compile("""master(Katalyst())""")!!
            .queryArc(0.0, 1.0)

        // Same chain from both languages, and it really is the empty chain, the one a playback
        // runs when no master(...) is present at all.
        scriptEvents[0].data.master shouldBe kotlinEvents[0].data.master
        scriptEvents[0].data.master shouldBe KatalystValue.Dsl(KatalystDsl(emptyList()))
        scriptEvents[0].data.control shouldBe true
    }

    "the full reverb and delay vocabulary round-trips from KlangScript (== the Kotlin builder)" {
        val expected = KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = c(0.3), size = c(8.0), lowpass = c(6000.0)),
            KatalystStageDsl.Delay(wet = c(0.2), time = c(0.5), feedback = c(1.0), cap = c(3.0)),
        )
        val script = SprudelPattern.compile(
            """master(Katalyst(k => k
                 .reverb(0.3, 8, 6000)
                 .delay(0.2, 0.5, 1.0, d => d.cap(3.0))
               ))"""
        )!!.queryArc(0.0, 1.0)

        script[0].data.master shouldBe KatalystValue.Dsl(expected)
    }
})
