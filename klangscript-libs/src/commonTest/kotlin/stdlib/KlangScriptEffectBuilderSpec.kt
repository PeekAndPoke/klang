/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.drive
import io.peekandpoke.klang.audio_bridge.eq
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.phaser
import io.peekandpoke.klang.audio_bridge.shimmer
import io.peekandpoke.klang.audio_bridge.tap
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/**
 * Door parity for the wrappers with a builder: `.eq(configure)`, `.phaser(wet, rate, ...,
 * configure)`, `.shimmer(wet, ..., configure)`, `.pitchEnvelope(semitones, configure)`,
 * `.fm(modulator, ratio, depth, configure)`, and `.drive(amount)`. The script form and the Kotlin
 * form (the audio_bridge extensions, or the node data class) must build equal nodes. The four
 * filters have their own spec, `KlangScriptFilterDoorParitySpec`.
 */
class KlangScriptEffectBuilderSpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<IgnitorDsl>()
    }

    val saw = IgnitorDsl.Sawtooth()

    "eq: script lambda == Kotlin eq().band().tap()" {
        ks("Osc.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7))") shouldBe
                saw.eq().band(300.0, 1.0, -4.0).tap(850.0, 0.707, 1.7)
    }

    "eq: the Kotlin door of the stdlib takes the same lambda" {
        ks("Osc.saw().eq(e => e.band(300, 1.0, -4))") shouldBe
                KlangScriptOscExtensions.eq(saw, configure = { it.band(300.0, 1.0, -4.0) })
    }

    "eq: a lambda that returns nothing is a script-level type error" {
        shouldThrow<KlangScriptTypeError> { ks("Osc.saw().eq(e => { e.band(300) })") }
    }

    "phaser: wet FIRST, then rate, and the dry floor via the lambda == the Kotlin door" {
        val dsl = ks("Osc.saw().phaser(0.25, 0.3, x => x.floor(0.1))") as IgnitorDsl.Phaser
        dsl shouldBe IgnitorDsl.Phaser(
            inner = saw, rate = IgnitorDsl.Constant(0.3),
            wet = IgnitorDsl.Constant(0.25), floor = IgnitorDsl.Constant(0.1),
        )
        dsl shouldBe saw.phaser(wet = 0.25, rate = 0.3).copy(floor = IgnitorDsl.Constant(0.1))
    }

    "phaser: center and sweep stay door parameters, the lambda follows them" {
        val dsl = ks("Osc.saw().phaser(0.4, 0.3, 800, 600, x => x.floor(0.1))") as IgnitorDsl.Phaser
        dsl shouldBe saw.phaser(0.4, 0.3, 800.0, 600.0).copy(floor = IgnitorDsl.Constant(0.1))
        dsl.floor shouldBe IgnitorDsl.Constant(0.1)
        dsl.center shouldBe IgnitorDsl.Constant(800.0)
        dsl.sweep shouldBe IgnitorDsl.Constant(600.0)
        dsl.wet shouldBe IgnitorDsl.Constant(0.4)
        dsl.rate shouldBe IgnitorDsl.Constant(0.3)
    }

    "phaser: wet and rate are both required, and the builder no longer offers wet" {
        shouldThrowAny { ks("Osc.saw().phaser(0.3)") }
        shouldThrowAny { ks("Osc.saw().phaser(0.3, 0.5, x => x.wet(0.4))") }
        shouldThrowAny { ks("Osc.saw().phaser(0.3, 0.5, x => x.dryFloor(0.4))") }
    }

    "shimmer: every default, wet first with today's default, pitches default when omitted" {
        val dsl = ks("Osc.saw().shimmer()") as IgnitorDsl.Shimmer
        dsl shouldBe IgnitorDsl.Shimmer(inner = saw)
        dsl shouldBe saw.shimmer()
        dsl.pitches shouldBe listOf(0.0, 7.0, 12.0)
        (ks("Osc.saw().shimmer(0.3)") as IgnitorDsl.Shimmer) shouldBe saw.shimmer(wet = 0.3)
    }

    "shimmer: explicit pitches array still binds positionally before the lambda" {
        val dsl = ks("Osc.saw().shimmer(0.3, 0.4, 3000, [0, 4, 7, 11], x => x.floor(0.2))") as IgnitorDsl.Shimmer
        dsl shouldBe saw.shimmer(0.3, 0.4, 3000.0, listOf(0.0, 4.0, 7.0, 11.0)).copy(floor = IgnitorDsl.Constant(0.2))
        dsl.pitches shouldBe listOf(0.0, 4.0, 7.0, 11.0)
        dsl.wet shouldBe IgnitorDsl.Constant(0.3)
        dsl.feedback shouldBe IgnitorDsl.Constant(0.4)
        dsl.tone shouldBe IgnitorDsl.Constant(3000.0)
        dsl.floor shouldBe IgnitorDsl.Constant(0.2)
        shouldThrowAny { ks("Osc.saw().shimmer(0.3, x => x.wet(0.4))") }
    }

    "drive: amount only, the type is gone on both doors" {
        ks("Osc.saw().drive(0.5)") shouldBe saw.drive(0.5)
        ks("Osc.saw().drive(0.5)") shouldBe IgnitorDsl.Drive(inner = saw, amount = IgnitorDsl.Constant(0.5))
    }

    "pitchEnvelope: no lambda is the node's defaults, attack 0.01, decay 0.1, sustain 0, release 0" {
        ks("Osc.saw().pitchEnvelope(12)") shouldBe IgnitorDsl.PitchEnvelope(inner = saw, semitones = IgnitorDsl.Constant(12.0))
        val n = IgnitorDsl.PitchEnvelope(inner = saw)
        n.attackSec shouldBe IgnitorDsl.Constant(0.01)
        n.decaySec shouldBe IgnitorDsl.Constant(0.1)
        n.sustainLevel shouldBe IgnitorDsl.Constant(0.0)
        n.releaseSec shouldBe IgnitorDsl.Constant(0.0)
        n.attackCurve shouldBe null
        n.decayCurve shouldBe null
        n.releaseCurve shouldBe null
    }

    "pitchEnvelope: ONE adsr call and adsrCurves == the node fields" {
        ks("""Osc.saw().pitchEnvelope(24, x => x.adsr(0.001, 0.05, 0.2, 0.1).adsrCurves("exp", "square", "cube"))""") shouldBe
                IgnitorDsl.PitchEnvelope(
                    inner = saw,
                    semitones = IgnitorDsl.Constant(24.0),
                    attackSec = IgnitorDsl.Constant(0.001),
                    decaySec = IgnitorDsl.Constant(0.05),
                    sustainLevel = IgnitorDsl.Constant(0.2),
                    releaseSec = IgnitorDsl.Constant(0.1),
                    attackCurve = AdsrCurve.Exponential,
                    decayCurve = AdsrCurve.Square,
                    releaseCurve = AdsrCurve.Cube,
                )
    }

    "pitchEnvelope: the songs' migrated form is the old three-argument node" {
        // `pitchEnvelope(24, 0.001, 0.04)` before step 3d; release and anchor were at their 0 defaults.
        ks("Osc.saw().pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))") shouldBe IgnitorDsl.PitchEnvelope(
            inner = saw,
            semitones = IgnitorDsl.Constant(24.0),
            attackSec = IgnitorDsl.Constant(0.001),
            decaySec = IgnitorDsl.Constant(0.04),
        )
        shouldThrowAny { ks("Osc.saw().pitchEnvelope(24, 0.001, 0.04)") }
    }

    "pitchEnvelope: in adsrCurves an omitted argument and an unknown name both mean the default" {
        // The second call names only the decay: it REPLACES the first call, it does not merge.
        val later = ks("""Osc.saw().pitchEnvelope(12, x => x.adsrCurves("square", "cube", "scurve").adsrCurves(decayCurve = "exp"))""")
            as IgnitorDsl.PitchEnvelope
        later.attackCurve shouldBe null
        later.decayCurve shouldBe AdsrCurve.Exponential
        later.releaseCurve shouldBe null

        val unknown = ks("""Osc.saw().pitchEnvelope(12, x => x.adsrCurves("scurve", "cube", "scurve").adsrCurves("bogus", "cube", "square"))""")
            as IgnitorDsl.PitchEnvelope
        unknown.attackCurve shouldBe null
        unknown.decayCurve shouldBe AdsrCurve.Cube
        unknown.releaseCurve shouldBe AdsrCurve.Square
    }

    "fm: the index envelope is ONE adsr call on the builder == the Kotlin door's env fields" {
        ks("Osc.saw().fm(Osc.sine(), 1.4, 300, x => x.adsr(0.001, 0.5, 0, 0.2))") shouldBe
                saw.fm(IgnitorDsl.Sine(), 1.4, 300.0, envAttackSec = 0.001, envDecaySec = 0.5, envSustainLevel = 0.0, envReleaseSec = 0.2)
    }

    "fm: without a lambda the depth is constant, the same node as the Kotlin door's defaults" {
        ks("Osc.saw().fm(Osc.sine(), 2, 200)") shouldBe saw.fm(IgnitorDsl.Sine(), 2.0, 200.0)
    }

    "fm: the builder offers adsr only (its freq stays hidden, maintainer 2026-08-30)" {
        shouldThrowAny { ks("Osc.saw().fm(Osc.sine(), 2, 200, x => x.freq(220))") }
    }
})
