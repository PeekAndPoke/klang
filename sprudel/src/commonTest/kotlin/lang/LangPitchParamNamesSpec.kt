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
})
