/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * Pitch-param unification guard (2026-08-24): params that mean SEMITONES are NAMED
 * `semitones` on every door — `pitchEnvelope`, `vibrato`, `vibratoMod`, `accelerate`
 * (unit converted from octaves, values ×12), the vibrato depth, which is the `semitones` slot of `vibrato(rate, semitones)` (it was `depth`
 * from 2026-09-07 until pitch pipeline step 2, decision D4), and the filter envelope depth, which is the `env` slot of `lpf`/`hpf`/`bpf`/`notch` since 2026-09-07 (semitones, named for the envelope it scales rather than the unit). The one-pole lowpass is
 * `onepole(freq)` in Hz on both doors (formerly sprudel `warmth(0..1 coefficient)` and
 * ignitor `warmth`/`onePoleLowpass`).
 */
class LangPitchParamNamesSpec : StringSpec({

    fun firstData(p: SprudelPattern?): SprudelVoiceData =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data

    "sprudel script door: semitone params dispatch by name" {
        firstData(SprudelPattern.compile("""note("c").vibrato(semitones = 0.5)""")).vibratoMod shouldBe 0.5
        firstData(SprudelPattern.compile("""note("c").accelerate(semitones = 12)""")).accelerate shouldBe 12.0
        firstData(SprudelPattern.compile("""note("c").lpf(freq = 800, env = 24)""")).lpenv shouldBe 24.0
        firstData(SprudelPattern.compile("""note("c").onepole(freq = 3743)""")).ignitorParams?.get("onepole") shouldBe 3743.0
    }

    "ignitor doors: semitone params on the nodes, freq on onepole" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val vib = eval("""Ignitor.saw().vibrato(rate = 5, semitones = 0.5)""") as IgnitorDsl.Vibrato
        vib.semitones shouldBe IgnitorDsl.Constant(0.5)

        val pe = eval("""Ignitor.saw().pitchEnvelope(semitones = 24)""") as IgnitorDsl.PitchEnvelope
        pe.semitones shouldBe IgnitorDsl.Constant(24.0)

        val acc = eval("""Ignitor.saw().accelerate(semitones = 12)""") as IgnitorDsl.Accelerate
        acc.semitones shouldBe IgnitorDsl.Constant(12.0)

        val op = eval("""Ignitor.saw().onepole(freq = 3743)""") as IgnitorDsl.OnePoleLowpass
        op.freq shouldBe IgnitorDsl.Constant(3743.0)
    }

    "door parity: the vibrato's semitones is one word on the sprudel door, the slot and the Ignitor node" {
        // Pitch pipeline step 2 (decision D4): sprudel's `vibrato(rate, semitones)` and its reader write and read the
        // slot `vibrato.semitones`, which `classic()` hands the node's `semitones` knob; `depth` is retired
        // (`docs/retired-names.md`). The rate likewise: `vibrato.rate` is the node's `rate`.
        var node: IgnitorDsl = IgnitorDsl.Sine().classic()

        while (node !is IgnitorDsl.Vibrato) {
            node = node.childNodes().first()
        }

        node.semitones shouldBe IgnitorDsl.Slots.vibrato.semitones
        node.rate shouldBe IgnitorDsl.Slots.vibrato.rate

        val data = firstData(SprudelPattern.compile("""note("c").vib(rate = 6, semitones = 0.4).pan(vibrato.semitones)"""))
        val bag = data.toVoiceData().ignitorParams!!

        bag[(IgnitorDsl.Slots.vibrato.semitones as IgnitorDsl.Param).name] shouldBe 0.4
        bag[(IgnitorDsl.Slots.vibrato.rate as IgnitorDsl.Param).name] shouldBe 6.0
        data.pan shouldBe 0.4
    }

    "door parity: the vibrato's rangeFrom, rangeTo and phase are one word each on the sprudel door, the slot and the Ignitor node" {
        // Pitch pipeline 7c (decision D9): sprudel's `vib(rate, semitones, rangeFrom, rangeTo, phase)` and its readers
        // write and read the slots `vibrato.rangeFrom`, `vibrato.rangeTo`, `vibrato.phase`, which `classic()` hands the
        // node's knobs of the same names (the script builder's `range(from, to)` and `phase(x)`).
        var node: IgnitorDsl = IgnitorDsl.Sine().classic()

        while (node !is IgnitorDsl.Vibrato) {
            node = node.childNodes().first()
        }

        node.rangeFrom shouldBe IgnitorDsl.Slots.vibrato.rangeFrom
        node.rangeTo shouldBe IgnitorDsl.Slots.vibrato.rangeTo
        node.phase shouldBe IgnitorDsl.Slots.vibrato.phase

        val code = """note("c").vib(rate = 6, semitones = 0.4, rangeFrom = 0, rangeTo = 0.8, phase = 0.25)"""
        val bag = firstData(SprudelPattern.compile(code)).toVoiceData().ignitorParams!!

        bag[(IgnitorDsl.Slots.vibrato.rangeFrom as IgnitorDsl.Param).name] shouldBe 0.0
        bag[(IgnitorDsl.Slots.vibrato.rangeTo as IgnitorDsl.Param).name] shouldBe 0.8
        bag[(IgnitorDsl.Slots.vibrato.phase as IgnitorDsl.Param).name] shouldBe 0.25

        val positional = firstData(SprudelPattern.compile("""note("c").vib(6, 0.4, 0, 0.8, 0.25)""")).toVoiceData().ignitorParams!!
        positional shouldBe bag

        firstData(SprudelPattern.compile("""note("c").vib(6, 0.4, 0, 0.8, 0.25).pan(vibrato.rangeTo)""")).pan shouldBe 0.8
        firstData(SprudelPattern.compile("""note("c").vib(6, 0.4, 0, 0.8, 0.25).pan(vibrato.phase)""")).pan shouldBe 0.25
        firstData(SprudelPattern.compile("""note("c").vib(6, 0.4, -0.5, 0.8, 0.25).pan(vibrato.rangeFrom)""")).pan shouldBe -0.5
    }

    "door parity: accelerate is one word on the sprudel door, the slot and the Ignitor node" {
        // Pitch pipeline step 3 (decision D4): sprudel's `accelerate(semitones)` and its reader write and read the flat
        // slot `accelerate`, which `classic()` hands the node's `semitones` knob.
        var node: IgnitorDsl = IgnitorDsl.Sine().classic()

        while (node !is IgnitorDsl.Accelerate) {
            node = node.childNodes().first()
        }

        node.semitones shouldBe IgnitorDsl.Slots.accelerate

        val data = firstData(SprudelPattern.compile("""note("c").accelerate(semitones = 0.6).pan(accelerate)"""))
        val bag = data.toVoiceData().ignitorParams!!

        bag[(IgnitorDsl.Slots.accelerate as IgnitorDsl.Param).name] shouldBe 0.6
        data.pan shouldBe 0.6
    }

    "door parity: fm's depth, ratio and envelope are one word each on the sprudel door, the slot and the Ignitor node" {
        // Pitch pipeline step 4 (decision D4): sprudel's `fm(depth, ratio, attack, decay, sustain, release)` and its
        // readers write and read the slots `fm.depth` ... `fm.release`, which `classic()` hands the node's knobs of the
        // same names; `env` and `h` are retired (`docs/retired-names.md`).
        var node: IgnitorDsl = IgnitorDsl.Sine().classic()

        while (node !is IgnitorDsl.Fm) {
            node = node.childNodes().first()
        }

        val s = IgnitorDsl.Slots.fm

        node.depth shouldBe s.depth
        node.ratio shouldBe s.ratio
        node.attack shouldBe s.attack
        node.decay shouldBe s.decay
        node.sustain shouldBe s.sustain
        node.release shouldBe s.release

        val data = firstData(
            SprudelPattern.compile(
                """note("c").fm(depth = 310, ratio = 1.5, attack = 0.01, decay = 0.2, sustain = 0.3, release = 0.4).pan(fm.release)"""
            )
        )
        val bag = data.toVoiceData().ignitorParams!!

        fun key(slot: IgnitorDsl) = (slot as IgnitorDsl.Param).name

        bag[key(s.depth)] shouldBe 310.0
        bag[key(s.ratio)] shouldBe 1.5
        bag[key(s.attack)] shouldBe 0.01
        bag[key(s.decay)] shouldBe 0.2
        bag[key(s.sustain)] shouldBe 0.3
        bag[key(s.release)] shouldBe 0.4
        data.pan shouldBe 0.4

        // The Ignitor door's builder uses the same words: `x.fm(m, ratio, depth, f => f.adsr(attack, decay, sustain, release))`.
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val fm = engine.execute("""Ignitor.saw().fm(modulator = Ignitor.sine(), ratio = 1.5, depth = 310)""").toObjectOrNull<Any>() as IgnitorDsl.Fm

        fm.ratio shouldBe IgnitorDsl.Constant(1.5)
        fm.depth shouldBe IgnitorDsl.Constant(310.0)
    }
})
