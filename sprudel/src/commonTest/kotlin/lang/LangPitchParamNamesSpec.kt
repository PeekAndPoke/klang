/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * Pitch-param unification guard (2026-08-24): params that mean SEMITONES are NAMED
 * `semitones` on every door — `pitchEnvelope`, `vibrato`, `vibratoMod`, `accelerate`
 * (unit converted from octaves, values ×12), `lpe`/`hpe`/`bpe`. The one-pole lowpass is
 * `onepole(freq)` in Hz on both doors (formerly sprudel `warmth(0..1 coefficient)` and
 * ignitor `warmth`/`onePoleLowpass`). Old param/function names must FAIL, not alias.
 */
class LangPitchParamNamesSpec : StringSpec({

    fun firstData(p: SprudelPattern?): SprudelVoiceData =
        (p ?: error("no pattern")).queryArc(0.0, 1.0).first().data

    "sprudel script door: semitone params dispatch by name" {
        firstData(SprudelPattern.compile("""note("c").vibratoMod(semitones = 0.5)""")).vibratoMod shouldBe 0.5
        firstData(SprudelPattern.compile("""note("c").accelerate(semitones = 12)""")).accelerate shouldBe 12.0
        firstData(SprudelPattern.compile("""note("c").lpf(800).lpe(semitones = 24)""")).lpenv shouldBe 24.0
        firstData(SprudelPattern.compile("""note("c").onepole(freq = 3743)""")).oscParams?.get("onepole") shouldBe 3743.0
    }

    "sprudel script door: the OLD param names fail dispatch" {
        shouldThrowAny { SprudelPattern.compile("""note("c").vibratoMod(depth = 0.5)""") }
        shouldThrowAny { SprudelPattern.compile("""note("c").accelerate(amount = 12)""") }
        shouldThrowAny { SprudelPattern.compile("""note("c").lpf(800).lpe(depth = 24)""") }
        shouldThrowAny { SprudelPattern.compile("""note("c").warmth(0.5)""") }
    }

    "ignitor doors: semitone params on the nodes, freq on onepole" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        fun eval(code: String): Any? = engine.execute(code).toObjectOrNull<Any>()

        val vib = eval("""Osc.saw().vibrato(rate = 5, semitones = 0.5)""") as IgnitorDsl.Vibrato
        vib.semitones shouldBe IgnitorDsl.Constant(0.5)

        val pe = eval("""Osc.saw().pitchEnvelope(semitones = 24)""") as IgnitorDsl.PitchEnvelope
        pe.semitones shouldBe IgnitorDsl.Constant(24.0)

        val acc = eval("""Osc.saw().accelerate(semitones = 12)""") as IgnitorDsl.Accelerate
        acc.semitones shouldBe IgnitorDsl.Constant(12.0)

        val op = eval("""Osc.saw().onepole(freq = 3743)""") as IgnitorDsl.OnePoleLowpass
        op.cutoffHz shouldBe IgnitorDsl.Constant(3743.0)

        // old names are gone
        shouldThrowAny { engine.execute("""Osc.saw().pitchEnvelope(amount = 24)""") }
        shouldThrowAny { engine.execute("""Osc.saw().vibrato(rate = 5, depth = 0.5)""") }
        shouldThrowAny { engine.execute("""Osc.saw().warmth(3000)""") }
        shouldThrowAny { engine.execute("""Osc.saw().onePoleLowpass(3000)""") }
    }
})
