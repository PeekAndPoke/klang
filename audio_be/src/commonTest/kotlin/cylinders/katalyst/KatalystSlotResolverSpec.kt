/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * One row per stage of the resolver contract (`docs/tasks/katalyst-dsl.md` §7) for a **declared**
 * chain: its knobs come from its own slots, and the owner voice is not a knob source
 * (the signal-flow plan §7, D4).
 *
 * Every row builds a slot-driven chain and applies it from a voice whose own bus settings are
 * LOUD and different, so each row also proves that the voice is ignored. The classic chain's
 * voice-driven writers are `KatalystChainBuilderSpec`'s subject, and the byte identity they buy is
 * `KatalystClassicMatchesUntouchedVoiceSpec`'s.
 */
class KatalystSlotResolverSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** Every bus knob set, and set to something no row below asks for. */
    fun loudVoice(): Voice = VoiceTestHelpers.createSynthVoice(
        delay = Voice.Delay(amount = 0.9, time = 0.75, feedback = 0.8, cap = 0.2),
        reverb = Voice.Reverb(amount = 0.9, size = 0.95, lowpass = 999.0),
        phaser = Voice.Phaser(rate = 9.0, depth = 0.9, center = 9000.0, sweep = 9000.0, floor = 0.1),
        compressor = Voice.Compressor(
            thresholdDb = -9.0,
            ratio = 9.0,
            kneeDb = 9.0,
            attackSeconds = 0.09,
            releaseSeconds = 0.9,
        ),
        ducking = Voice.Ducking(cylinderId = 9, attackSeconds = 0.09, depth = 0.9),
        body = FilterDef.Body(bands = listOf(FilterDef.Body.Mode(freq = 99.0, db = 9.0, q = 9.0)), mix = 0.9),
        vowel = FilterDef.Formant(
            bands = listOf(FilterDef.Formant.Band(freq = 999.0, db = 9.0, q = 99.0)),
            mix = 0.9,
        ),
    )

    /** A declared chain, built slot-driven, with the loud voice offered as its owner. */
    fun declared(vararg stages: KatalystStageDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
        voiceDriven = false,
    ).also { it.applyOwner(loudVoice()) }

    fun c(value: Double) = IgnitorDsl.Constant(value)

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────

    "delay: the slots reach the line, and the voice's own delay does not" {
        val chain = declared(
            KatalystStageDsl.Delay(time = c(0.25), feedback = c(0.4), cap = c(0.9))
        )

        val line = chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull()

        line.time shouldBe 0.25
        line.feedback shouldBe 0.4
        line.cap shouldBe 0.9
    }

    "delay: a non-finite time is OFF, and off means the orbit never rents a ring" {
        val chain = declared(
            KatalystStageDsl.Delay(time = c(SLOT_UNSET), feedback = c(0.4), cap = c(0.9))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldBeNull()
        chain.hasTail() shouldBe false
    }

    "delay: a non-finite feedback and cap take their constants" {
        val chain = declared(
            KatalystStageDsl.Delay(time = c(0.25), feedback = c(SLOT_UNSET), cap = c(SLOT_UNSET))
        )

        val line = chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull()

        line.feedback shouldBe DELAY_FEEDBACK
        line.cap shouldBe DELAY_CAP
    }

    "delay: wet 0.0 is OFF in this step, so the stage rents nothing" {
        // `wet` is the stage's ON SWITCH until step 5 makes the sends insert-style: a chain cannot
        // say HOW MUCH yet, but it must be able to say NOTHING, like the phaser's depth and the
        // duck's orbit.
        val chain = declared(
            KatalystStageDsl.Delay(wet = c(0.0), time = c(0.25))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldBeNull()
    }

    "delay: a non-finite wet is unset, which is also OFF" {
        val chain = declared(
            KatalystStageDsl.Delay(wet = c(SLOT_UNSET), time = c(0.25))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldBeNull()
    }

    // ── Reverb ───────────────────────────────────────────────────────────────────────────────────

    "reverb: the size slot is the AUTHORED 0..10 value and goes through normalizeSize" {
        val chain = declared(KatalystStageDsl.Reverb(size = c(6.0), lowpass = c(3000.0)))

        val unit = chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        unit.size shouldBe Reverb.normalizeSize(6.0)
        // The authored 6 is not the normalized value, so a resolver that forgot the conversion
        // would build a room 10x too long.
        (unit.size == 6.0) shouldBe false
        unit.lowpass shouldBe 3000.0
    }

    "reverb: a non-finite lowpass is unset, which is the engine's own damping" {
        val chain = declared(KatalystStageDsl.Reverb(size = c(6.0), lowpass = c(SLOT_UNSET)))

        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().lowpass.shouldBeNull()
    }

    "reverb: no lowpass slot at all reads the same as an unset one" {
        val chain = declared(KatalystStageDsl.Reverb(size = c(6.0), lowpass = null))

        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().lowpass.shouldBeNull()
    }

    "reverb: a non-finite size is OFF, and off means the orbit never rents a network" {
        val chain = declared(KatalystStageDsl.Reverb(size = c(SLOT_UNSET)))

        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "reverb: wet 0.0 is OFF in this step, so the stage rents nothing" {
        val chain = declared(KatalystStageDsl.Reverb(wet = c(0.0), size = c(6.0)))

        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "reverb: a non-finite wet is unset, which is also OFF" {
        val chain = declared(KatalystStageDsl.Reverb(wet = c(SLOT_UNSET), size = c(6.0)))

        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    // ── Phaser ───────────────────────────────────────────────────────────────────────────────────

    "phaser: depth IS wet, and below the gate the kernel params stay untouched" {
        val below = Phaser.MIN_ACTIVE_DEPTH / 2.0
        val chain = declared(
            KatalystStageDsl.Phaser(
                rate = c(3.0),
                wet = c(below),
                center = c(700.0),
                sweep = c(400.0),
                floor = c(0.2),
            )
        )

        val phaser = chain.phaser.shouldNotBeNull().phaser

        phaser.depth shouldBe below
        // The sweep CLOCK must keep running on whatever it had (a fresh Phaser: rate 0), or a
        // gated-off chain would freeze the LFO for the next chain that engages it.
        phaser.rate shouldBe 0.0
        phaser.center shouldBe PHASER_CENTER_HZ
        phaser.sweep shouldBe PHASER_SWEEP_HZ
    }

    "phaser: at the gate the kernel params are written, with the > 0 fallbacks" {
        val chain = declared(
            KatalystStageDsl.Phaser(
                rate = c(3.0),
                wet = c(Phaser.MIN_ACTIVE_DEPTH),
                center = c(0.0),
                sweep = c(0.0),
                floor = c(0.2),
            )
        )

        val phaser = chain.phaser.shouldNotBeNull().phaser

        phaser.depth shouldBe Phaser.MIN_ACTIVE_DEPTH
        phaser.rate shouldBe 3.0
        phaser.center shouldBe PHASER_CENTER_HZ
        phaser.sweep shouldBe PHASER_SWEEP_HZ
        phaser.floor shouldBe 0.2
        phaser.feedback shouldBe 0.5
    }

    // ── Compressor ───────────────────────────────────────────────────────────────────────────────

    "compressor: ONE finite slot switches it on, the other four take their constants" {
        val chain = declared(
            KatalystStageDsl.Compressor(
                threshold = c(SLOT_UNSET),
                ratio = c(8.0),
                knee = c(SLOT_UNSET),
                attack = c(SLOT_UNSET),
                release = c(SLOT_UNSET),
            )
        )

        val comp = chain.compressor.shouldNotBeNull().compressor.shouldNotBeNull()

        comp.ratio shouldBe 8.0
        comp.thresholdDb shouldBe COMPRESSOR_THRESHOLD_DB
        comp.kneeDb shouldBe COMPRESSOR_KNEE_DB
        comp.attackSeconds shouldBe COMPRESSOR_ATTACK_SECONDS
        comp.releaseSeconds shouldBe COMPRESSOR_RELEASE_SECONDS
    }

    "compressor: all five unset is OFF, even with a voice that carries one" {
        val chain = declared(
            KatalystStageDsl.Compressor(
                threshold = c(SLOT_UNSET),
                ratio = c(SLOT_UNSET),
                knee = c(SLOT_UNSET),
                attack = c(SLOT_UNSET),
                release = c(SLOT_UNSET),
            )
        )

        chain.compressor.shouldNotBeNull().compressor.shouldBeNull()
    }

    "compressor: the instance is reused, so the envelope follower survives the next block" {
        val chain = declared(KatalystStageDsl.Compressor(threshold = c(-21.0)))
        val comp = chain.compressor.shouldNotBeNull().compressor.shouldNotBeNull()

        chain.applyOwner(loudVoice())

        chain.compressor.shouldNotBeNull().compressor.shouldNotBeNull() shouldBeSameInstanceAs comp
    }

    // ── Duck ─────────────────────────────────────────────────────────────────────────────────────

    "duck: on when the orbit is finite and the depth above zero, cylinderId from toInt" {
        val chain = declared(
            KatalystStageDsl.Duck(orbit = c(3.7), depth = c(0.6), attack = c(0.02))
        )

        val duck = chain.duck.shouldNotBeNull()

        duck.duckCylinderId shouldBe 3
        val ducking = duck.ducking.shouldNotBeNull()
        ducking.depth shouldBe 0.6
        ducking.attackSeconds shouldBe 0.02
    }

    "duck: a non-finite attack takes its constant" {
        val chain = declared(
            KatalystStageDsl.Duck(orbit = c(3.0), depth = c(0.6), attack = c(SLOT_UNSET))
        )

        chain.duck.shouldNotBeNull().ducking.shouldNotBeNull().attackSeconds shouldBe DUCK_ATTACK_SECONDS
    }

    "duck: off without a source orbit, off without depth, both against a ducking voice" {
        declared(KatalystStageDsl.Duck(orbit = c(SLOT_UNSET), depth = c(0.6)))
            .duck.shouldNotBeNull().ducking.shouldBeNull()

        declared(KatalystStageDsl.Duck(orbit = c(3.0), depth = c(0.0)))
            .duck.shouldNotBeNull().ducking.shouldBeNull()
    }

    // ── Body and vowel ───────────────────────────────────────────────────────────────────────────

    "body and vowel: no name means the stage is OFF, whatever wet says" {
        val chain = declared(
            KatalystStageDsl.Body(material = null, wet = c(1.0)),
            KatalystStageDsl.Vowel(vowel = null, wet = c(1.0)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe false
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false
    }

    "body and vowel: a NAME engages the stage, through the audio_bridge tables" {
        val chain = declared(
            KatalystStageDsl.Body(material = "wood", wet = c(0.3)),
            KatalystStageDsl.Vowel(vowel = "a", wet = c(0.3)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe true
        chain.vowel.shouldNotBeNull().isEngaged shouldBe true
    }

    "body and vowel: the resolved def is the voice path's, up to the floor fill" {
        // Parity with `SprudelVoiceData.toVoiceData` (Katalyst step 3c): both paths read the SAME
        // [BodyMaterials] / [VowelBands] table, so this row pins the table's answer on the chain
        // side while `LangBodySpec` and `LangVowelComprehensiveSpec` pin the voice side against the
        // same landmark modes. `audio_be` does not depend on `sprudel`, so the two halves of the
        // parity cannot live in one file.
        //
        // The ONE difference between the paths is the floor FILL: a voice leaves `floor = null`,
        // which [FilterDef.Body] documents as "engine default", while a declared stage writes that
        // same default out as a number. Same filter, two spellings of one value.
        val body = KatalystSlots.bodyDef(
            stage = KatalystStageDsl.Body(material = "wood", wet = c(0.3)),
            bands = KatalystSlots.bodyModes("wood"),
        ).shouldNotBeNull()

        body.bands shouldBe BodyMaterials.modesFor("wood")
        body.bands.first() shouldBe FilterDef.Body.Mode(freq = 100.0, db = 3.0, q = 12.0)
        body.bands.size shouldBe 8
        body.mix shouldBe 0.3
        body.floor shouldBe BODY_FLOOR

        val vowel = KatalystSlots.vowelDef(
            stage = KatalystStageDsl.Vowel(vowel = "a", wet = c(0.3)),
            bands = KatalystSlots.vowelBands("a"),
        ).shouldNotBeNull()

        // A bare vowel name is the soprano register, the voice path's rule as well.
        vowel.bands shouldBe VowelBands.bandsFor("soprano:a")
        vowel.bands.first() shouldBe FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 80.0)
        vowel.bands.size shouldBe 5
        vowel.mix shouldBe 0.3
        vowel.floor shouldBe VOWEL_FLOOR
    }

    "body and vowel: the name is case-insensitive, as it is on the voice path" {
        KatalystSlots.bodyModes("Wood") shouldBe BodyMaterials.modesFor("wood")
        KatalystSlots.vowelBands("BASS:A") shouldBe VowelBands.bandsFor("bass:a")
    }

    "body and vowel: an unknown name is OFF, the rule toVoiceData follows" {
        KatalystSlots.bodyModes("unobtainium").shouldBeNull()
        KatalystSlots.vowelBands("zzz").shouldBeNull()

        // `none` is the explicit off switch on both tables.
        KatalystSlots.bodyModes("none").shouldBeNull()
        KatalystSlots.vowelBands("none").shouldBeNull()

        val chain = declared(
            KatalystStageDsl.Body(material = "unobtainium", wet = c(1.0)),
            KatalystStageDsl.Vowel(vowel = "zzz", wet = c(1.0)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe false
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false
    }

    "body and vowel: with bands, mix IS wet and a non-finite floor takes its constant" {
        val modes = listOf(FilterDef.Body.Mode(freq = 200.0, db = 0.0, q = 8.0))
        val bands = listOf(FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 90.0))

        val body = KatalystSlots.bodyDef(
            stage = KatalystStageDsl.Body(material = "wood", wet = c(0.3), floor = c(SLOT_UNSET)),
            bands = modes,
        ).shouldNotBeNull()

        body.bands shouldBe modes
        body.mix shouldBe 0.3
        body.floor shouldBe BODY_FLOOR

        val vowel = KatalystSlots.vowelDef(
            stage = KatalystStageDsl.Vowel(vowel = "a", wet = c(0.4), floor = c(SLOT_UNSET)),
            bands = bands,
        ).shouldNotBeNull()

        vowel.bands shouldBe bands
        vowel.mix shouldBe 0.4
        vowel.floor shouldBe VOWEL_FLOOR

        // A finite floor is the author's, untouched (the Motor stays raw).
        KatalystSlots.bodyDef(
            stage = KatalystStageDsl.Body(material = "wood", floor = c(0.05)),
            bands = modes,
        ).shouldNotBeNull().floor shouldBe 0.05
    }

    "body and vowel: a non-finite mix takes its constant, like the floor" {
        val modes = listOf(FilterDef.Body.Mode(freq = 200.0, db = 0.0, q = 8.0))
        val bands = listOf(FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 90.0))

        // A NaN mix reaches the wet/dry law and silences the orbit, so unset has to read as the
        // constant here exactly as it does for the floor.
        KatalystSlots.bodyDef(
            stage = KatalystStageDsl.Body(material = "wood", wet = c(SLOT_UNSET)),
            bands = modes,
        ).shouldNotBeNull().mix shouldBe BODY_WET

        KatalystSlots.vowelDef(
            stage = KatalystStageDsl.Vowel(vowel = "a", wet = c(SLOT_UNSET)),
            bands = bands,
        ).shouldNotBeNull().mix shouldBe VOWEL_WET
    }

    "body and vowel: null bands is the off switch, whatever the slots say" {
        KatalystSlots.bodyDef(KatalystStageDsl.Body(material = "wood", wet = c(1.0)), bands = null).shouldBeNull()
        KatalystSlots.vowelDef(KatalystStageDsl.Vowel(vowel = "a", wet = c(1.0)), bands = null).shouldBeNull()
    }

    // ── The value vocabulary ─────────────────────────────────────────────────────────────────────

    "a Param slot resolves to its authored default" {
        val chain = declared(
            KatalystStageDsl.Delay(time = IgnitorDsl.Param(name = "delay.time", default = 0.125))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().time shouldBe 0.125
    }

    "a foldable arithmetic node is read at control rate, not refused" {
        val chain = declared(
            KatalystStageDsl.Delay(time = IgnitorDsl.Times(c(0.1), c(3.0)))
        )

        // 0.1 * 3 is 0.30000000000000004 in binary floating point; the row pins the FOLD, so the
        // expression is the same one the resolver evaluates.
        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().time shouldBe (0.1 * 3.0)
    }

    "an oscillator handed to a knob takes the knob's default, and nothing throws" {
        val chain = declared(
            KatalystStageDsl.Delay(time = IgnitorDsl.Sine(), feedback = IgnitorDsl.Sine())
        )

        val line = chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull()

        // DELAY_TIME_SECONDS and DELAY_FEEDBACK are what a BARE `delay()` stage carries, which is
        // the only sensible reading of "this knob has no number".
        line.time shouldBe DELAY_TIME_SECONDS
        line.feedback shouldBe DELAY_FEEDBACK
    }

    "the note frequency a bus does not have: Osc.freq() resolves to the knob's constant" {
        // The resolver reads a non-leaf knob at TWO frequencies and only believes an answer that
        // is the same on both: a knob whose value depends on a note is not a bus knob.
        //
        // The knob under test is `feedback`, deliberately: on `time` a single-probe regression
        // would ask for a 440-second ring and the row would allocate a third of a gigabyte before
        // it failed.
        val chain = declared(
            KatalystStageDsl.Delay(time = c(0.25), feedback = IgnitorDsl.Freq)
        )

        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().feedback shouldBe DELAY_FEEDBACK
    }

    "a frequency-DEPENDENT subtree takes the knob's constant too, not its value at one note" {
        // `Osc.freq().mul(2)` folds at control rate, so a single probe would accept it (880.0 at 440);
        // two probes disagree (880 vs 1320) and the knob keeps its constant. This is the row the
        // first version of the resolver got wrong: `Times` scrubs a non-finite probe to 0.0
        // through `safeOut`, so a NaN-based discriminator read it as a legitimate zero.
        val chain = declared(
            KatalystStageDsl.Delay(time = c(0.25), feedback = IgnitorDsl.Times(IgnitorDsl.Freq, c(2.0)))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().feedback shouldBe DELAY_FEEDBACK
    }

    "a pitch-free fold is the author's value, zero included" {
        // `1 / 0` is a finite 0.0 in this engine (`IgnitorDsl.Div` maps a zero divisor to zero)
        // and answers the same at both probes, so it IS the author's number. On a time knob that
        // number means off, and the resolver may not second-guess it: the same rule keeps
        // `phaser(rate = 0)` meaning a standing sweep.
        val chain = declared(
            KatalystStageDsl.Delay(time = IgnitorDsl.Div(c(1.0), c(0.0)))
        )

        chain.delay.shouldNotBeNull().delayLine.shouldBeNull()
    }
})
