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
 * One row per stage of the resolver contract (`docs/tasks/katalyst-dsl.md` §7): what a chain's
 * stage is configured with, given what the chain AUTHORED and what the orbit's param state says.
 *
 * Most rows configure from no state at all ([declared]) and are therefore about the authored
 * values; the rows that name a map are about what a write does to them. What the CLASSIC chain's
 * own slot defaults are is `KatalystClassicMatchesUntouchedVoiceSpec`'s subject, and that the bus
 * FIELDS of a voice reach nothing is `CylinderKatalystParamsSpec`'s.
 */
class KatalystSlotResolverSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /**
     * A chain built from [stages] and configured from NO param state, so every knob resolves to
     * what the chain itself authored. That is what these rows are about: one row per stage of the
     * resolver contract, on the authored values.
     *
     * It used to hand `applyParams` the `katalystParams` of a voice whose seven bus FIELDS were all
     * set to loud values, to show that those fields were ignored. Since step 5b-1 the chain's
     * signature cannot see a field at all, so the demonstration was empty (the map of that voice was
     * null, so this was already `applyParams(null)` through a whole `VoiceFactory` build). The claim
     * has a home where it can still be made: `CylinderKatalystParamsSpec`'s "the BORN-WITH chain
     * ignores the voice's bus FIELDS" row, which offers such a voice to a real cylinder.
     */
    fun declared(vararg stages: KatalystStageDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ).also { it.applyParams(null) }

    fun c(value: Double) = IgnitorDsl.Constant(value)

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────

    "delay: the authored slots reach the line" {
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
        // `wet` decides WHETHER the stage runs until step 5b-2 makes the sends insert-style
        // (`sendStageRuns` is the one home): a chain cannot
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

    "reverb: an AUTHORED wet of 0.0 that no pattern writes rents nothing" {
        // Half of the send gate (`sendStageRuns`), and the half decided on 2026-09-17: a chain
        // that says `wet(0.0)` asked for a chain that HAS a room, not for a room. Nothing in the
        // param state, so `KatalystKnob.written` is false and only the authored amount speaks.
        val chain = declared(KatalystStageDsl.Reverb(wet = c(0.0), size = c(6.0)))

        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "reverb: a WRITTEN wet of 0.0 keeps the room running, fed nothing" {
        // The other half, put back in review round 1 of Katalyst step 5b-1 and kept by the 5b-2
        // decision (`sendStageRuns`): a written wet is the orbit's amount, a LEVEL that glides, so
        // a written 0 runs the stage with nothing fed in and a later amount glides up from there
        // instead of switching a room on. `VoiceFactory` ran the stage on a TOUCHED field, a
        // written 0 included, and the slot twin of touched is "the map carries the key with a
        // finite value".
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Param("reverb.wet", 0.0),
                    size = IgnitorDsl.Param("reverb.size", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain.applyParams(mapOf("reverb.wet" to 0.0, "reverb.size" to 6.0))

        val unit = chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        unit.size shouldBe Reverb.normalizeSize(6.0)

        // The discriminator, on a FRESH chain so no drain state can answer for it: the same size,
        // with the wet key ABSENT from the map, rents nothing. What decides is the write, not the
        // number, and not the presence of a size.
        val untouched = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Param("reverb.wet", 0.0),
                    size = IgnitorDsl.Param("reverb.size", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        untouched.applyParams(mapOf("reverb.size" to 6.0))

        untouched.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "delay: a WRITTEN wet of 0.0 keeps the line running, the reverb row's twin" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Delay(
                    wet = IgnitorDsl.Param("delay.wet", 0.0),
                    time = IgnitorDsl.Param("delay.time", 0.0),
                    feedback = IgnitorDsl.Param("delay.feedback", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain.applyParams(mapOf("delay.wet" to 0.0, "delay.time" to 0.25, "delay.feedback" to 0.3))

        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().time shouldBe 0.25

        val untouched = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Delay(
                    wet = IgnitorDsl.Param("delay.wet", 0.0),
                    time = IgnitorDsl.Param("delay.time", 0.0),
                    feedback = IgnitorDsl.Param("delay.feedback", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        untouched.applyParams(mapOf("delay.time" to 0.25, "delay.feedback" to 0.3))

        untouched.delay.shouldNotBeNull().delayLine.shouldBeNull()
    }

    "a NEGATIVE written wet runs the stage too: the wire's rule is touched, not positive" {
        // `VoiceFactory` read a negative send field as touched, and a negative wet feeds the stage
        // phase-inverted (raw). The gate must not second-guess the sign.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Param("reverb.wet", 0.0),
                    size = IgnitorDsl.Param("reverb.size", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain.applyParams(mapOf("reverb.wet" to -0.5, "reverb.size" to 6.0))

        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)
    }

    "reverb: a non-finite WRITTEN wet is unset, which is neither touched nor an amount, so OFF" {
        // The one non-finite family that is NOT a strict improvement, pinned deliberately. At HEAD
        // the FIELD was non-null, so the effect counted as touched and `VoiceFactory`'s `orDefault`
        // swallowed the NaN: the room ran at the constants and every voice on the orbit was heard
        // in it. Here the stage is off for the whole orbit, so a second voice sending into it loses
        // its room. What that buys is the slot vocabulary being consistent with itself, "non-finite
        // is unset" on EVERY knob, which is what lets a cleared slot read as untouched at all. No
        // ordinary spelling reaches it: it takes a string atom (`"NaN"`) or a hand-written `katp`.
        // Recorded in `audio/MEMORY.md` with the same honesty.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Param("reverb.wet", 0.0),
                    size = IgnitorDsl.Param("reverb.size", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        chain.applyParams(mapOf("reverb.wet" to SLOT_UNSET, "reverb.size" to 6.0))

        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "reverb: an AUTHORED non-finite wet is OFF as well" {
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

        chain.applyParams(null)

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

    "body and vowel: an UNSET index means the stage is OFF, whatever wet says" {
        val chain = declared(
            KatalystStageDsl.Body(material = c(SLOT_UNSET), wet = c(1.0)),
            KatalystStageDsl.Vowel(vowel = c(SLOT_UNSET), wet = c(1.0)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe false
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false
    }

    "body and vowel: an INDEX engages the stage, through the audio_bridge catalogues" {
        val chain = declared(
            KatalystStageDsl.Body(material = c(BodyMaterials.indexOf("wood")), wet = c(0.3)),
            KatalystStageDsl.Vowel(vowel = c(VowelBands.indexOf("a")), wet = c(0.3)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe true
        chain.vowel.shouldNotBeNull().isEngaged shouldBe true
    }

    "body and vowel: the index slot is what the pattern doors move, and it picks the right box" {
        // The step 5a-2 contract: `katp("body.material", n)` (which `body(material = ...)` writes
        // for you) selects a material on a live chain. The index is read off the catalogue rather
        // than typed here, and the modes are compared against the catalogue's own answer, so this
        // row cannot pass with the lookup pointing one box along.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Body(
                    material = IgnitorDsl.Param("body.material", SLOT_UNSET),
                    wet = IgnitorDsl.Param("body.wet", 0.0),
                ),
                KatalystStageDsl.Vowel(
                    vowel = IgnitorDsl.Param("vowel.vowel", SLOT_UNSET),
                    wet = IgnitorDsl.Param("vowel.wet", 0.0),
                ),
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        // Nothing written: both stages off, which is what an untouched classic orbit is.
        chain.applyParams(null)
        chain.body.shouldNotBeNull().isEngaged shouldBe false
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false

        // The doors write index plus wet, and the stage comes on with THAT material's modes.
        chain.applyParams(
            mapOf(
                "body.material" to BodyMaterials.indexOf("wood"),
                "body.wet" to 0.3,
                "vowel.vowel" to VowelBands.indexOf("bass:a"),
                "vowel.wet" to 0.4,
            )
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe true
        chain.body.shouldNotBeNull().installedBands shouldBe BodyMaterials.modesFor("wood")
        chain.vowel.shouldNotBeNull().isEngaged shouldBe true
        chain.vowel.shouldNotBeNull().installedBands shouldBe VowelBands.bandsFor("bass:a")

        // A moved index re-resolves to the other box, and index 0 (`none`) switches it back off.
        chain.applyParams(
            mapOf(
                "body.material" to BodyMaterials.indexOf("glass"),
                "body.wet" to 0.3,
                "vowel.vowel" to 0.0,
                "vowel.wet" to 0.4,
            )
        )

        chain.body.shouldNotBeNull().installedBands shouldBe BodyMaterials.modesFor("glass")
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false
    }

    "body and vowel: the def is rebuilt only when the param map INSTANCE changes" {
        // The cost rule of the param state: `apply` runs every block and writes a def already in
        // hand, and only a new map instance costs a lookup. A writer that resolved in `apply`
        // would do a catalogue lookup and allocate a FilterDef per block per orbit.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Body(
                    material = IgnitorDsl.Param("body.material", SLOT_UNSET),
                    wet = IgnitorDsl.Param("body.wet", 0.0),
                )
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        val first = mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 0.3)

        chain.applyParams(first)
        chain.resolveCount shouldBe 1
        chain.body.shouldNotBeNull().installedBands shouldBe BodyMaterials.modesFor("wood")

        // The SAME instance again does not re-resolve: the gate is identity, so a live owner
        // re-offering its map every block costs one reference compare and no lookup.
        chain.applyParams(first)
        chain.applyParams(first)
        chain.resolveCount shouldBe 1
        chain.body.shouldNotBeNull().installedBands shouldBe BodyMaterials.modesFor("wood")

        // A DIFFERENT map with a different index does, and lands on the other box.
        chain.applyParams(mapOf("body.material" to BodyMaterials.indexOf("bell"), "body.wet" to 0.3))
        chain.resolveCount shouldBe 2
        chain.body.shouldNotBeNull().installedBands shouldBe BodyMaterials.modesFor("bell")
    }

    "body and vowel: a signal-rate node on the index slot is coerced, never refused" {
        // The two-probe rule, on the index knob like on every other one. A pitch-free fold IS the
        // author's number, so `0.5 * 2` picks the first material; an oscillator has no bus value,
        // so it takes the knob's fallback, which is unset, which is the stage off.
        val folded = declared(
            KatalystStageDsl.Body(material = IgnitorDsl.Times(c(0.5), c(2.0)), wet = c(0.3))
        )

        folded.body.shouldNotBeNull().isEngaged shouldBe true
        folded.body.shouldNotBeNull().installedBands shouldBe
                BodyMaterials.modesFor(BodyMaterials.names[1])

        val oscillated = declared(
            KatalystStageDsl.Body(material = IgnitorDsl.Sine(), wet = c(0.3)),
            KatalystStageDsl.Vowel(vowel = IgnitorDsl.Sine(), wet = c(0.3)),
        )

        oscillated.body.shouldNotBeNull().isEngaged shouldBe false
        oscillated.vowel.shouldNotBeNull().isEngaged shouldBe false

        // And a note-DEPENDENT knob, the case one probe cannot see: two probes disagree, so it
        // falls back to unset rather than to whatever the material index is at 440 Hz.
        val perNote = declared(
            KatalystStageDsl.Body(material = IgnitorDsl.Freq, wet = c(0.3))
        )

        perNote.body.shouldNotBeNull().isEngaged shouldBe false
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
            bands = BodyMaterials.modesAt(BodyMaterials.indexOf("wood")),
            mix = 0.3,
            floor = BODY_FLOOR,
        ).shouldNotBeNull()

        body.bands shouldBe BodyMaterials.modesFor("wood")
        body.bands.first() shouldBe FilterDef.Body.Mode(freq = 100.0, db = 3.0, q = 12.0)
        body.bands.size shouldBe 8
        body.mix shouldBe 0.3
        body.floor shouldBe BODY_FLOOR

        val vowel = KatalystSlots.vowelDef(
            bands = VowelBands.bandsAt(VowelBands.indexOf("a")),
            mix = 0.3,
            floor = VOWEL_FLOOR,
        ).shouldNotBeNull()

        // A bare vowel name is the soprano register, the voice path's rule as well.
        vowel.bands shouldBe VowelBands.bandsFor("soprano:a")
        vowel.bands.first() shouldBe FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 80.0)
        vowel.bands.size shouldBe 5
        vowel.mix shouldBe 0.3
        vowel.floor shouldBe VOWEL_FLOOR
    }

    "body and vowel: an index that names nothing is OFF, the rule toVoiceData follows for a name" {
        // The index door is `BodyMaterials.indexOf` / `VowelBands.indexOf`, which answers 0 for an
        // unknown name, and 0 IS `none`. So a name the catalogue does not know arrives here as an
        // index that resolves to no bands, which is the stage off. The catalogue's own edges are
        // `CatalogueIndexSpec`'s; what this row pins is that the resolver reads them as off.
        BodyMaterials.modesAt(BodyMaterials.indexOf("unobtainium")).shouldBeNull()
        VowelBands.bandsAt(VowelBands.indexOf("zzz")).shouldBeNull()
        BodyMaterials.modesAt(BodyMaterials.indexOf("none")).shouldBeNull()
        VowelBands.bandsAt(VowelBands.indexOf("none")).shouldBeNull()
        BodyMaterials.modesAt(SLOT_UNSET).shouldBeNull()
        VowelBands.bandsAt(SLOT_UNSET).shouldBeNull()

        val chain = declared(
            KatalystStageDsl.Body(material = c(BodyMaterials.indexOf("unobtainium")), wet = c(1.0)),
            KatalystStageDsl.Vowel(vowel = c(VowelBands.indexOf("zzz")), wet = c(1.0)),
        )

        chain.body.shouldNotBeNull().isEngaged shouldBe false
        chain.vowel.shouldNotBeNull().isEngaged shouldBe false
    }

    "body and vowel: with bands, mix IS wet and a non-finite floor takes its constant" {
        val modes = listOf(FilterDef.Body.Mode(freq = 200.0, db = 0.0, q = 8.0))
        val bands = listOf(FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 90.0))

        val body = KatalystSlots.bodyDef(bands = modes, mix = 0.3, floor = SLOT_UNSET).shouldNotBeNull()

        body.bands shouldBe modes
        body.mix shouldBe 0.3
        body.floor shouldBe BODY_FLOOR

        val vowel = KatalystSlots.vowelDef(bands = bands, mix = 0.4, floor = SLOT_UNSET).shouldNotBeNull()

        vowel.bands shouldBe bands
        vowel.mix shouldBe 0.4
        vowel.floor shouldBe VOWEL_FLOOR

        // A finite floor is the author's, untouched (the Motor stays raw).
        KatalystSlots.bodyDef(bands = modes, mix = BODY_WET, floor = 0.05)
            .shouldNotBeNull().floor shouldBe 0.05
    }

    "body and vowel: a non-finite mix takes its constant, like the floor" {
        val modes = listOf(FilterDef.Body.Mode(freq = 200.0, db = 0.0, q = 8.0))
        val bands = listOf(FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 90.0))

        // A NaN mix reaches the wet/dry law and silences the orbit, so unset has to read as the
        // constant here exactly as it does for the floor.
        KatalystSlots.bodyDef(bands = modes, mix = SLOT_UNSET, floor = BODY_FLOOR)
            .shouldNotBeNull().mix shouldBe BODY_WET

        KatalystSlots.vowelDef(bands = bands, mix = SLOT_UNSET, floor = VOWEL_FLOOR)
            .shouldNotBeNull().mix shouldBe VOWEL_WET
    }

    "body and vowel: null bands is the off switch, whatever the slots say" {
        KatalystSlots.bodyDef(bands = null, mix = 1.0, floor = BODY_FLOOR).shouldBeNull()
        KatalystSlots.vowelDef(bands = null, mix = 1.0, floor = VOWEL_FLOOR).shouldBeNull()
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
