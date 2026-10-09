/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.string.shouldContain
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.band
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
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

    val saw = IgnitorDsl.Saw()

    "eq: script lambda == Kotlin eq().band().tap()" {
        ks("Ignitor.saw().eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7))") shouldBe
                saw.eq().band(300.0, 1.0, -4.0).tap(850.0, 0.707, 1.7)
    }

    "eq: the Kotlin door of the stdlib takes the same lambda" {
        ks("Ignitor.saw().eq(e => e.band(300, 1.0, -4))") shouldBe
                KlangScriptIgnitorExtensions.eq(saw, configure = { it.band(300.0, 1.0, -4.0) })
    }

    "eq: a lambda that returns nothing is a script-level type error" {
        shouldThrow<KlangScriptTypeError> { ks("Ignitor.saw().eq(e => { e.band(300) })") }
    }

    "phaser: wet FIRST, then rate, and the dry floor via the lambda == the Kotlin door" {
        val dsl = ks("Ignitor.saw().phaser(0.25, 0.3, x => x.floor(0.1))") as IgnitorDsl.Phaser
        dsl shouldBe IgnitorDsl.Phaser(
            inner = saw, rate = IgnitorDsl.Constant(0.3),
            wet = IgnitorDsl.Constant(0.25), floor = IgnitorDsl.Constant(0.1),
        )
        dsl shouldBe saw.phaser(wet = 0.25, rate = 0.3).copy(floor = IgnitorDsl.Constant(0.1))
    }

    "phaser: center and sweep stay door parameters, the lambda follows them" {
        val dsl = ks("Ignitor.saw().phaser(0.4, 0.3, 800, 600, x => x.floor(0.1))") as IgnitorDsl.Phaser
        dsl shouldBe saw.phaser(0.4, 0.3, 800.0, 600.0).copy(floor = IgnitorDsl.Constant(0.1))
        dsl.floor shouldBe IgnitorDsl.Constant(0.1)
        dsl.center shouldBe IgnitorDsl.Constant(800.0)
        dsl.sweep shouldBe IgnitorDsl.Constant(600.0)
        dsl.wet shouldBe IgnitorDsl.Constant(0.4)
        dsl.rate shouldBe IgnitorDsl.Constant(0.3)
    }

    "phaser: wet and rate are both required, and the builder no longer offers wet" {
        shouldThrowAny { ks("Ignitor.saw().phaser(0.3)") }
        shouldThrowAny { ks("Ignitor.saw().phaser(0.3, 0.5, x => x.wet(0.4))") }
        shouldThrowAny { ks("Ignitor.saw().phaser(0.3, 0.5, x => x.dryFloor(0.4))") }
    }

    "shimmer: every default, wet first with today's default, pitches default when omitted" {
        val dsl = ks("Ignitor.saw().shimmer()") as IgnitorDsl.Shimmer
        dsl shouldBe IgnitorDsl.Shimmer(inner = saw)
        dsl shouldBe saw.shimmer()
        dsl.pitches shouldBe listOf(0.0, 7.0, 12.0)
        (ks("Ignitor.saw().shimmer(0.3)") as IgnitorDsl.Shimmer) shouldBe saw.shimmer(wet = 0.3)
    }

    "shimmer: explicit pitches array still binds positionally before the lambda" {
        val dsl = ks("Ignitor.saw().shimmer(0.3, 0.4, 3000, [0, 4, 7, 11], x => x.floor(0.2))") as IgnitorDsl.Shimmer
        dsl shouldBe saw.shimmer(0.3, 0.4, 3000.0, listOf(0.0, 4.0, 7.0, 11.0)).copy(floor = IgnitorDsl.Constant(0.2))
        dsl.pitches shouldBe listOf(0.0, 4.0, 7.0, 11.0)
        dsl.wet shouldBe IgnitorDsl.Constant(0.3)
        dsl.feedback shouldBe IgnitorDsl.Constant(0.4)
        dsl.tone shouldBe IgnitorDsl.Constant(3000.0)
        dsl.floor shouldBe IgnitorDsl.Constant(0.2)
        shouldThrowAny { ks("Ignitor.saw().shimmer(0.3, x => x.wet(0.4))") }
    }

    "shimmer: a pitch that is not a number is the door's typed error, as on wet, feedback and tone" {
        shouldThrow<KlangScriptTypeError> { ks("""Ignitor.saw().shimmer(0.3, 0.4, 3000, [0, "7", 12])""") }
        shouldThrow<KlangScriptTypeError> { ks("""Ignitor.saw().shimmer(0.3, 0.4, 3000, [0, true, 12])""") }
        (ks("Ignitor.saw().shimmer(0.3, 0.4, 3000, [0, 7, 12])") as IgnitorDsl.Shimmer).pitches shouldBe listOf(0.0, 7.0, 12.0)
    }

    "drive: amount only, the type is gone on both doors" {
        ks("Ignitor.saw().drive(0.5)") shouldBe saw.drive(0.5)
        ks("Ignitor.saw().drive(0.5)") shouldBe IgnitorDsl.Drive(inner = saw, amount = IgnitorDsl.Constant(0.5))
    }

    "pitchEnvelope: no lambda is the node's defaults, attack 0.01, decay 0.1, sustain 0, release 0" {
        ks("Ignitor.saw().pitchEnvelope(12)") shouldBe IgnitorDsl.PitchEnvelope(inner = saw, semitones = IgnitorDsl.Constant(12.0))
        val n = IgnitorDsl.PitchEnvelope(inner = saw)
        n.attack shouldBe IgnitorDsl.Constant(0.01)
        n.decay shouldBe IgnitorDsl.Constant(0.1)
        n.sustain shouldBe IgnitorDsl.Constant(0.0)
        n.release shouldBe IgnitorDsl.Constant(0.0)
        n.attackCurve shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
        n.decayCurve shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
        n.releaseCurve shouldBe AdsrCurves.knob(MOD_ENV_CURVE)
    }

    "pitchEnvelope: ONE adsr call with its curves lambda == the node fields" {
        ks("""Ignitor.saw().pitchEnvelope(24, x => x.adsr(0.001, 0.05, 0.2, 0.1, e => e.curves("exp", "square", "cube")))""") shouldBe
                IgnitorDsl.PitchEnvelope(
                    inner = saw,
                    semitones = IgnitorDsl.Constant(24.0),
                    attack = IgnitorDsl.Constant(0.001),
                    decay = IgnitorDsl.Constant(0.05),
                    sustain = IgnitorDsl.Constant(0.2),
                    release = IgnitorDsl.Constant(0.1),
                    attackCurve = AdsrCurves.knob(AdsrCurve.Exponential),
                    decayCurve = AdsrCurves.knob(AdsrCurve.Square),
                    releaseCurve = AdsrCurves.knob(AdsrCurve.Cube),
                )
    }

    "pitchEnvelope: the songs' migrated form is the old three-argument node" {
        // `pitchEnvelope(24, 0.001, 0.04)` before step 3d; release and anchor were at their 0 defaults.
        ks("Ignitor.saw().pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))") shouldBe IgnitorDsl.PitchEnvelope(
            inner = saw,
            semitones = IgnitorDsl.Constant(24.0),
            attack = IgnitorDsl.Constant(0.001),
            decay = IgnitorDsl.Constant(0.04),
        )
        shouldThrowAny { ks("Ignitor.saw().pitchEnvelope(24, 0.001, 0.04)") }
    }

    "pitchEnvelope: in curves an omitted argument and an unknown name both mean the default" {
        val default = AdsrCurves.knob(MOD_ENV_CURVE)

        // The second call names only the decay: it REPLACES the first call, it does not merge.
        val later = ks("""Ignitor.saw().pitchEnvelope(12, x => x.adsr(0.01, 0.1, 0, 0, e => e.curves("square", "cube", "scurve").curves(decay = "exp")))""")
            as IgnitorDsl.PitchEnvelope
        later.attackCurve shouldBe default
        later.decayCurve shouldBe AdsrCurves.knob(AdsrCurve.Exponential)
        later.releaseCurve shouldBe default

        val unknown = ks("""Ignitor.saw().pitchEnvelope(12, x => x.adsr(0.01, 0.1, 0, 0, e => e.curves("bogus", "cube", "square")))""")
            as IgnitorDsl.PitchEnvelope
        unknown.attackCurve shouldBe default
        unknown.decayCurve shouldBe AdsrCurves.knob(AdsrCurve.Cube)
        unknown.releaseCurve shouldBe AdsrCurves.knob(AdsrCurve.Square)
    }

    "pitchEnvelope: a later adsr call replaces an earlier one completely, curves included" {
        ks("""Ignitor.saw().pitchEnvelope(12, x => x.adsr(0.02, 0.2, 0, 0, e => e.curves("square", "cube", "scurve")).adsr(0.01, 0.1, 0, 0))""") shouldBe
                IgnitorDsl.PitchEnvelope(inner = saw, semitones = IgnitorDsl.Constant(12.0))
    }

    "pitchEnvelope: the 3d(i) builder knob adsrCurves is gone (it moved into the adsr lambda as curves)" {
        shouldThrowAny { ks("""Ignitor.saw().pitchEnvelope(12, x => x.adsrCurves("square", "cube", "scurve"))""") }
            .message shouldContain "has no method 'adsrCurves'"
    }

    "fm: the index envelope is ONE adsr call on the builder == the Kotlin door's env fields" {
        ks("Ignitor.saw().fm(Ignitor.sine(), 1.4, 300, x => x.adsr(0.001, 0.5, 0, 0.2))") shouldBe
                saw.fm(IgnitorDsl.Sine(), 1.4, 300.0, attack = 0.001, decay = 0.5, sustain = 0.0, release = 0.2)
    }

    "fm: without a lambda the depth is constant, the same node as the Kotlin door's defaults" {
        ks("Ignitor.saw().fm(Ignitor.sine(), 2, 200)") shouldBe saw.fm(IgnitorDsl.Sine(), 2.0, 200.0)
    }

    "fm: the builder offers adsr only (its freq stays hidden, maintainer 2026-08-30)" {
        shouldThrowAny { ks("Ignitor.saw().fm(Ignitor.sine(), 2, 200, x => x.freq(220))") }
    }
})
