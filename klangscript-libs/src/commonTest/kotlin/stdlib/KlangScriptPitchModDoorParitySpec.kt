/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.pitchModSemitones
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The two pitch-mod laws on both doors (pitch pipeline 7a, decision D8, maintainer 2026-10-09): `pitchModSemitones`
 * (the exponential law, `2^(mod / 12)`) and `pitchMod` (the linear law, `1 + mod`) are two words for two concepts,
 * so each door builds its own node. The script door, positional and named, and the Kotlin door (a signal and a
 * constant, on both words) build the same node. The law itself is `PitchModSemitonesSpec` in `audio_be`.
 */
class KlangScriptPitchModDoorParitySpec : StringSpec({

    fun ignitor(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    "pitchModSemitones by a constant: the script door, positional and named, and both Kotlin doors build one node" {
        val saw = ignitor("Ign.saw()")
        val expected = IgnitorDsl.PitchModSemitones(inner = saw, mod = c(7.0))

        ignitor("Ign.saw().pitchModSemitones(7)") shouldBe expected
        ignitor("Ignitor.saw().pitchModSemitones(mod = 7)") shouldBe expected
        saw.pitchModSemitones(7.0) shouldBe expected
        saw.pitchModSemitones(c(7.0)) shouldBe expected
    }

    "pitchModSemitones by a signal: the script door and the Kotlin door build one node" {
        val saw = ignitor("Ign.saw()")
        val lfo = ignitor("Ign.sine(5)")
        val expected = IgnitorDsl.PitchModSemitones(inner = saw, mod = lfo.mul(c(0.5)))

        ignitor("Ign.saw().pitchModSemitones(Ign.sine(5).mul(0.5))") shouldBe expected
        ignitor("Ign.saw().pitchModSemitones(mod = Ign.sine(5).mul(0.5))") shouldBe expected
        saw.pitchModSemitones(lfo.mul(0.5)) shouldBe expected
    }

    "two laws, two nodes: pitchMod builds the linear node on both doors and both Kotlin overloads, never the semitone one" {
        val saw = ignitor("Ign.saw()")
        val linear = IgnitorDsl.PitchMod(inner = saw, mod = c(0.5))

        ignitor("Ign.saw().pitchMod(0.5)") shouldBe linear
        ignitor("Ign.saw().pitchMod(mod = 0.5)") shouldBe linear
        saw.pitchMod(c(0.5)) shouldBe linear
        saw.pitchMod(0.5) shouldBe linear
        ignitor("Ign.saw().pitchModSemitones(0.5)") shouldNotBe linear
    }
})
