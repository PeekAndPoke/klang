/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.sprudel.lang.adsr
import io.peekandpoke.klang.sprudel.lang.adsrCurves
import io.peekandpoke.klang.sprudel.lang.adsrOff
import io.peekandpoke.klang.sprudel.lang.begin
import io.peekandpoke.klang.sprudel.lang.bpf
import io.peekandpoke.klang.sprudel.lang.bpfCurves
import io.peekandpoke.klang.sprudel.lang.coarse
import io.peekandpoke.klang.sprudel.lang.crush
import io.peekandpoke.klang.sprudel.lang.distort
import io.peekandpoke.klang.sprudel.lang.end
import io.peekandpoke.klang.sprudel.lang.hpf
import io.peekandpoke.klang.sprudel.lang.hpfCurves
import io.peekandpoke.klang.sprudel.lang.loop
import io.peekandpoke.klang.sprudel.lang.lpf
import io.peekandpoke.klang.sprudel.lang.lpfCurves
import io.peekandpoke.klang.sprudel.lang.notch
import io.peekandpoke.klang.sprudel.lang.notchCurves
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.oscp
import io.peekandpoke.klang.sprudel.lang.speed
import io.peekandpoke.klang.sprudel.lang.tremolo

/**
 * Sprudel's voice doors on the wire, as `classic()` slot keys (phase 3 step 8, `classicSlotParams`): one row per
 * rule of the translation, which moved here from the backend's `classicSlotBag` with its rules.
 */
class ClassicSlotParamsSpec : StringSpec({

    fun wire(p: SprudelPattern?) = (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data.toVoiceData()

    fun slots(p: SprudelPattern?): Map<String, Double> = wire(p).ignitorParams ?: emptyMap()

    "no voice door written: the wire bag is the event's own bag, and no bag at all without one" {
        wire(note("c")).ignitorParams shouldBe null
        slots(note("c").oscp("voices", 3)) shouldBe mapOf("voices" to 3.0)
    }

    "only a FINITE value is written: a non-finite door value leaves the slot unset" {
        createSprudelVoiceData { crush = Double.NaN; release = Double.POSITIVE_INFINITY; cutoff = 800.0; resonance = Double.NaN }
            .toVoiceData().ignitorParams shouldBe mapOf("lpf.freq" to 800.0)
    }

    "the typed door wins over an oscp of the same key, in either order" {
        slots(note("c").oscp("lpf.freq", 500).lpf(800))["lpf.freq"] shouldBe 800.0
        slots(note("c").lpf(800).oscp("lpf.freq", 500))["lpf.freq"] shouldBe 800.0
        withClue("an oscp of a key no door on the event wrote stays") {
            slots(note("c").oscp("adsr.release", 0.4).lpf(800))["adsr.release"] shouldBe 0.4
        }
    }

    "a filter is written only with its cutoff: q, passes and the envelope without a freq write nothing" {
        slots(note("c").lpf(q = 3, passes = 2, env = 12, attack = 0.1)) shouldBe emptyMap()
        slots(note("c").notch(q = 3, env = 12)) shouldBe emptyMap()
    }

    "a filter writes q and passes only when named (the slot defaults are 0.707 and 1); passes coerced and rounded; bpf and notch have no passes" {
        slots(note("c").lpf(800)) shouldBe mapOf("lpf.freq" to 800.0)
        slots(note("c").hpf(200, passes = 2.6))["hpf.passes"] shouldBe 3.0
        slots(note("c").hpf(200, passes = 40))["hpf.passes"] shouldBe 16.0
        slots(note("c").bpf(1000)) shouldBe mapOf("bpf.freq" to 1000.0)
        slots(note("c").notch(1500, 2)) shouldBe mapOf("notch.freq" to 1500.0, "notch.q" to 2.0)
    }

    "an explicit oscp of q or passes is never overwritten by a door that did not name it; a door that names it wins" {
        slots(note("c").oscp("lpf.q", 4).oscp("lpf.passes", 3).lpf(800)) shouldBe
            mapOf("lpf.q" to 4.0, "lpf.passes" to 3.0, "lpf.freq" to 800.0)
        slots(note("c").oscp("lpf.q", 4).lpf(800, q = 2))["lpf.q"] shouldBe 2.0
        slots(note("c").lpf(800, q = 2).oscp("lpf.q", 4))["lpf.q"] shouldBe 2.0
    }

    "the envelope travels only when one of its five knobs is set, and the curves only with it" {
        withClue("a curve alone switches no envelope on") {
            slots(note("c").lpf(800).lpfCurves("linear")).keys shouldBe setOf("lpf.freq")
        }

        slots(note("c").lpf(800, attack = 0.2).lpfCurves("linear", "cube")) shouldBe mapOf(
            "lpf.freq" to 800.0,
            "lpf.attack" to 0.2, "lpfCurves.attack" to 0.0, "lpfCurves.decay" to 2.0,
        )
        withClue("the depth is not filled here: the slot layer answers it (step 5)") {
            slots(note("c").lpf(800, attack = 0.2)).containsKey("lpf.env") shouldBe false
        }
    }

    "names travel as their catalogue index, a flag as 1.0 or 0.0" {
        slots(note("c").distort(0.3, "tube"))["distort.shape"] shouldBe 10.0
        slots(note("c").distort(0.3, "no-such-shape"))["distort.shape"] shouldBe 0.0
        slots(note("c").tremolo(0.5, 4, "square")) shouldBe mapOf("tremolo.depth" to 0.5, "tremolo.rate" to 4.0, "tremolo.shape" to 2.0)
        slots(note("c").adsrCurves("linear", "square", "scurve")) shouldBe mapOf(
            "adsrCurves.attack" to 0.0, "adsrCurves.decay" to 1.0, "adsrCurves.release" to 3.0,
        )
        slots(note("c").adsrOff()) shouldBe mapOf("adsr.on" to 0.0)
        slots(note("c").loop()) shouldBe mapOf("loop" to 1.0)
    }

    "the crush and coarse oversample travel under keys nothing reads; the distort one under its classic() slot" {
        slots(note("c").crush(4, 2)) shouldBe mapOf("crush.amount" to 4.0, "crush.oversample" to 2.0)
        slots(note("c").coarse(3, 2)) shouldBe mapOf("coarse.amount" to 3.0, "coarse.oversample" to 2.0)
        slots(note("c").distort(0.5, oversample = 4))["distort.oversample"] shouldBe 4.0
    }

    "every door writes every knob under its own key, with its own value: the full literal map, and every key a slot an instrument places" {
        // One DISTINCT value per knob (and a distinct curve triple per door, a distinct curve per stage), so a
        // translation that writes one knob's value under another knob's key is a red row here, not only a key
        // that is misspelled. Curve indices: linear 0, square 1, cube 2, scurve 3, invsquare 4, exponential 5.
        val written = slots(
            note("c")
                .lpf(801, 1.1, 2, 11, 0.011, 0.012, 0.013, 0.014).lpfCurves("linear", "square", "cube")
                .hpf(101, 1.2, 3, 12, 0.021, 0.022, 0.023, 0.024).hpfCurves("square", "cube", "scurve")
                .bpf(901, 1.3, 13, 0.031, 0.032, 0.033, 0.034).bpfCurves("cube", "scurve", "invsquare")
                .notch(1501, 1.4, 14, 0.041, 0.042, 0.043, 0.044).notchCurves("scurve", "invsquare", "exponential")
                .adsr(0.051, 0.052, 0.53, 0.054).adsrCurves("invsquare", "exponential", "linear").adsrOff()
                .crush(4.1, 2).coarse(3.1, 4).distort(0.31, "tube", 8).tremolo(0.61, 4.1, "square")
                .begin(0.11).end(0.91).speed(2.1).loop()
        )

        written shouldBe mapOf(
            "lpf.freq" to 801.0, "lpf.q" to 1.1, "lpf.passes" to 2.0, "lpf.env" to 11.0,
            "lpf.attack" to 0.011, "lpf.decay" to 0.012, "lpf.sustain" to 0.013, "lpf.release" to 0.014,
            "lpfCurves.attack" to 0.0, "lpfCurves.decay" to 1.0, "lpfCurves.release" to 2.0,
            "hpf.freq" to 101.0, "hpf.q" to 1.2, "hpf.passes" to 3.0, "hpf.env" to 12.0,
            "hpf.attack" to 0.021, "hpf.decay" to 0.022, "hpf.sustain" to 0.023, "hpf.release" to 0.024,
            "hpfCurves.attack" to 1.0, "hpfCurves.decay" to 2.0, "hpfCurves.release" to 3.0,
            "bpf.freq" to 901.0, "bpf.q" to 1.3, "bpf.env" to 13.0,
            "bpf.attack" to 0.031, "bpf.decay" to 0.032, "bpf.sustain" to 0.033, "bpf.release" to 0.034,
            "bpfCurves.attack" to 2.0, "bpfCurves.decay" to 3.0, "bpfCurves.release" to 4.0,
            "notch.freq" to 1501.0, "notch.q" to 1.4, "notch.env" to 14.0,
            "notch.attack" to 0.041, "notch.decay" to 0.042, "notch.sustain" to 0.043, "notch.release" to 0.044,
            "notchCurves.attack" to 3.0, "notchCurves.decay" to 4.0, "notchCurves.release" to 5.0,
            "adsr.attack" to 0.051, "adsr.decay" to 0.052, "adsr.sustain" to 0.53, "adsr.release" to 0.054, "adsr.on" to 0.0,
            "adsrCurves.attack" to 4.0, "adsrCurves.decay" to 5.0, "adsrCurves.release" to 0.0,
            "crush.amount" to 4.1, "crush.oversample" to 2.0,
            "coarse.amount" to 3.1, "coarse.oversample" to 4.0,
            "distort.amount" to 0.31, "distort.shape" to 10.0, "distort.oversample" to 8.0,
            "tremolo.depth" to 0.61, "tremolo.rate" to 4.1, "tremolo.shape" to 2.0,
            "begin" to 0.11, "end" to 0.91, "speed" to 2.1, "loop" to 1.0,
        )

        val placed = mutableListOf<IgnitorDsl.Param>()
        IgnitorDsl.Sawtooth().classic().collectParams(placed)
        val s = IgnitorDsl.Slots.sample
        val names = placed.map { it.name }.toSet() +
            listOf(s.begin, s.end, s.speed, s.loop).map { (it as IgnitorDsl.Param).name } +
            setOf("crush.oversample", "coarse.oversample") // no reader, by decision (Q5)

        withClue("every key is a slot an instrument places") { (written.keys - names) shouldBe emptySet() }
    }

    "SMOKE, not the two-doors guard: the KlangScript and the Kotlin call put the same slots on the wire (one function serves both, so this cannot fail apart from the translation)" {
        val script = SprudelPattern.compile(
            """note("c").lpf(freq = 800, q = 2, env = 12).hpf(100).adsr(0.01, 0.1, 0.5, 0.2).crush(4).distort(0.3, "tube").tremolo(0.5, 4).speed(2)"""
        )
        val kotlin = note("c").lpf(freq = 800, q = 2, env = 12).hpf(100).adsr(0.01, 0.1, 0.5, 0.2).crush(4).distort(0.3, "tube").tremolo(0.5, 4).speed(2)

        slots(script) shouldBe slots(kotlin)
        slots(kotlin)["lpf.env"] shouldBe 12.0
    }
})
