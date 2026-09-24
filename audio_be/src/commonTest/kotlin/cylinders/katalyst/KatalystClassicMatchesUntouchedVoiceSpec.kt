/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import kotlin.random.Random

/**
 * [KatalystDsl.classic] claims to be what an orbit runs when the song never wrote a bus door. This
 * is the test that checks that claim against the thing it is a claim about: a [Voice] built by the
 * real [VoiceFactory] from a [VoiceData] that never wrote a bus door.
 *
 * **What this spec is, after Katalyst step 5b-1 (2026-09-19).** The chain stopped reading the bus
 * FIELDS, so nothing here compares two chain implementations any more: the rows that did (a
 * born-with chain against a declared one) were the migration fixture of steps 3 to 5a and are
 * gone. What is left is a CONTRACT, in two halves, and both keep an oracle that is not the code
 * under test:
 *
 *  - the classic chain's SLOT DEFAULTS against the wire's own "untouched" table, which is
 *    `VoiceFactory`'s untouched branch, where the constants of an untouched orbit are written
 *    down a second time, which is what makes it an oracle rather than a mirror. Since step 5b-3
 *    only the PHASER has that branch left (its fields stay on the wire for a custom pipeline's
 *    per-voice phaser); the delay, reverb, compressor, duck, body and vowel rows went with their
 *    fields, as this KDoc said they would. Their untouched defaults stay pinned by
 *    `KatalystDefaultsSyncSpec` (audio_bridge), the gate facts by the last row here and by
 *    `KatalystSlotResolverSpec`.
 *  - what a chain INSTALLS from a slot state, against the `FilterDef` a body or vowel call puts on
 *    the wire. That half is about the engine's non-finite rule (an unset `body.wet` plays at
 *    BODY_WET) and outlives the fields. **The wire value is HAND-BUILT in those rows**, not taken
 *    from `SprudelVoiceData.toVoiceData` (this module does not depend on `sprudel`), so the
 *    `BODY_WET` / `VOWEL_WET` literal in them IS the assertion; the catalogue supplies the bands
 *    and the class the floor's null. What the DOOR writes, and therefore that the hand-built value
 *    is the one a song produces, is sprudel's `LangKatalystParamSpec`; what the two render to is
 *    `KatalystDoorFillRenderSpec`.
 *
 * Why it has to be here and not in `audio_bridge`: over there the only available comparison is
 * against hand-typed numbers, and hand-typed numbers are exactly what drifts. This spec never names
 * a value; it reads both sides.
 *
 * It exists because review round 1 got this wrong in a way no declaration-side test could catch.
 * The chain zeroed the send WET knobs and left `delay.time` and `reverb.size` at their touched
 * constants, which reads as off and is not: the engine's gates are
 * `KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS` on TIME and `KatalystReverbEffect.MIN_ACTIVE_SIZE`
 * on SIZE. A chain like that declares a running delay line fed nothing, and once the cylinder reads
 * chains (step 2) it would have rented a ring for every orbit of every song.
 *
 * `reverb.size` is compared THROUGH [Reverb.normalizeSize], the conversion the orbit's reverb
 * applies on the authored 0..10 scale, so the gate row reads the size the stage actually sees.
 */
class KatalystClassicMatchesUntouchedVoiceSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** A voice built the production way, from [data]. */
    fun voiceOf(data: VoiceData): Voice {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = data,
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )

        return factory.makeVoice(
            scheduled = scheduled,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(
                playbackId = "test",
                ignitorRegistry = registry,
                phasePools = PhasePools(Random(1)),
            ),
            getSample = { null },
        ) ?: error("makeVoice returned null")
    }

    /**
     * Note and sound only: every phaser field stays null, which is what "the song never wrote a
     * bus door" is on the wire.
     */
    fun untouchedVoice(): Voice = voiceOf(VoiceData.empty.copy(freqHz = 440.0, sound = "triangle"))

    /**
     * The classic chain, the one a cylinder is born with and the one a `Katalyst(k => k.classic())`
     * resolves to: since step 5b-1 there is one build and one kind of writer.
     */
    fun classicChain(): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.classic,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    /** The default of one slot of the classic chain, by name. */
    fun slot(name: String): Double {
        val params = mutableListOf<IgnitorDsl.Param>()

        KatalystDsl.classic.stages.forEach { stage ->
            when (stage) {
                is KatalystStageDsl.Body -> listOf(stage.material, stage.wet, stage.floor)
                is KatalystStageDsl.Vowel -> listOf(stage.vowel, stage.wet, stage.floor)
                is KatalystStageDsl.Delay -> listOf(stage.wet, stage.time, stage.feedback, stage.cap)
                is KatalystStageDsl.Reverb -> listOfNotNull(stage.wet, stage.size, stage.lowpass)
                is KatalystStageDsl.Phaser ->
                    listOf(stage.rate, stage.wet, stage.center, stage.sweep, stage.floor)

                is KatalystStageDsl.Compressor ->
                    listOf(stage.threshold, stage.ratio, stage.knee, stage.attack, stage.release)

                is KatalystStageDsl.Duck -> listOf(stage.orbit, stage.depth, stage.attack)
                is KatalystStageDsl.Eq -> emptyList()
                is KatalystStageDsl.Gain -> listOf(stage.gain)
            }.forEach { knob -> knob.collectParams(params) }
        }

        return params.first { it.name == name }.default
    }

    "the classic phaser slots ARE the untouched voice's phaser, all five" {
        // The phaser has no touched flag in VoiceFactory: every field is filled from the constants
        // unconditionally, and what keeps it silent is `depth` being 0 under
        // `Phaser.MIN_ACTIVE_DEPTH`. So all five slots must match the voice exactly, and four of
        // them are NOT zero, which makes this the row that would catch a "zero everything" chain.
        val phaser = untouchedVoice().phaser

        withClue("phaser.rate vs Voice.Phaser.rate") { slot("phaser.rate") shouldBe phaser.rate }
        withClue("phaser.wet vs Voice.Phaser.depth") { slot("phaser.wet") shouldBe phaser.depth }
        withClue("phaser.center vs Voice.Phaser.center") { slot("phaser.center") shouldBe phaser.center }
        withClue("phaser.sweep vs Voice.Phaser.sweep") { slot("phaser.sweep") shouldBe phaser.sweep }
        withClue("phaser.floor vs Voice.Phaser.floor") { slot("phaser.floor") shouldBe phaser.floor }

        // ...and it really is off, by the engine's own gate rather than by looking zero.
        (phaser.depth < Phaser.MIN_ACTIVE_DEPTH) shouldBe true
    }

    "a MATERIAL-ONLY body reaches the classic chain at the engine's own wet, not dry" {
        // The bug round 1 of step 5a-2 found, and the row that would have caught it. This is the
        // RAW `katp` shape: only the index is written, so the amount is whatever an unset
        // `body.wet` slot resolves to. With the old 0.0 default that was a fully dry mix,
        // bit-identically silent, where the wire's own `FilterDef` plays the bank at BODY_WET. The
        // sprudel door fills the amount itself since step 5a-3, which is why this row writes the
        // slot by hand: it guards the ENGINE's non-finite rule, the door's fill is
        // `LangKatalystParamSpec`'s subject.
        val bands = BodyMaterials.modesFor("wood").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood")))
        }

        // `fromWire` is the `FilterDef.Body` a material-only call puts on the wire, HAND-BUILT
        // here with the constant written out (this module cannot call the sprudel door), so the
        // `BODY_WET` literal is what this row asserts; the catalogue supplies the bands and the
        // class its null floor. The chain's installed bank has to match it although nothing wrote
        // `body.wet`.
        val fromWire = FilterDef.Body(bands = bands, mix = BODY_WET)
        val installed = chain.body.shouldNotBeNull()

        withClue("the stage engages") { installed.isEngaged shouldBe true }
        withClue("the same modes") { installed.installedBands shouldBe fromWire.bands }

        // The discriminator: NOT dry. A chain that resolved its unset amount to 0.0 would install
        // the right bank at no mix at all, which is silence dressed as a body.
        withClue("the mix is audible, not the dry 0.0 a SET slot would install") {
            (installed.installedMix > 0.0) shouldBe true
        }
        withClue("...and it is the amount the wire carries") { installed.installedMix shouldBe fromWire.mix }
        installed.installedMix shouldBe BODY_WET
    }

    "a VOWEL-ONLY call reaches the classic chain at the engine's own wet too" {
        // The twin, on the other stage and the other constant: two writers, two substitutions.
        val bands = VowelBands.bandsFor("a").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("a")))
        }

        val fromWire = FilterDef.Formant(bands = bands, mix = VOWEL_WET)
        val installed = chain.vowel.shouldNotBeNull()

        installed.isEngaged shouldBe true
        installed.installedBands shouldBe fromWire.bands

        withClue("the mix is audible, not dry") { (installed.installedMix > 0.0) shouldBe true }
        installed.installedMix shouldBe fromWire.mix
        installed.installedMix shouldBe VOWEL_WET
    }

    "the classic chain installs the same bank the wire carries for a body call, slot for slot" {
        // The equivalence the step-5a-2 index slot exists for: `body(material = "wood", wet = 0.3)` must
        // reach the orbit's resonator through the INDEX slot with the bank and the mix the wire
        // carries for the same call. The oracle is that wire value, the real `FilterDef` over the
        // catalogue's own modes; nothing here types a mode. The name-to-index half of the trip is
        // `CatalogueIndexSpec`'s, and what the sprudel door writes is `LangKatalystParamSpec`'s.
        val bands = BodyMaterials.modesFor("wood").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 0.3))
        }

        val fromWire = FilterDef.Body(bands = bands, mix = 0.3)
        val installed = chain.body.shouldNotBeNull()

        withClue("the chain engages its body from the slots alone") { installed.isEngaged shouldBe true }
        withClue("the SAME modes the wire carries, not just some bank") {
            installed.installedBands shouldBe fromWire.bands
        }
        withClue("...and the same mix") { installed.installedMix shouldBe fromWire.mix }

        // The one formal difference, and why it is not audible: the wire leaves `floor` null,
        // which `LowPassHighPassFilters.createBody` reads as BODY_FLOOR, and the slot path writes
        // that same number out. Two spellings of one value, so the filter is identical.
        fromWire.floor shouldBe null
        installed.installedFloor shouldBe BODY_FLOOR
    }

    "the classic chain installs the same formant bank the wire carries for a vowel call" {
        // The twin of the body row, on the other index slot. Written out rather than folded in:
        // the two writers are separate classes, and a `vowel.vowel` wired to the body's catalogue
        // (or to no catalogue at all) would pass a body-only spec. The register is `bass`, not the
        // bare default, so a resolver that dropped the register would land on soprano and fail.
        val bands = VowelBands.bandsFor("bass:a").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("bass:a"), "vowel.wet" to 0.6))
        }

        val fromWire = FilterDef.Formant(bands = bands, mix = 0.6)
        val installed = chain.vowel.shouldNotBeNull()

        withClue("the chain engages its vowel from the slots alone") { installed.isEngaged shouldBe true }
        withClue("the SAME bands, the bass register and not the soprano default") {
            installed.installedBands shouldBe fromWire.bands
        }
        withClue("...and the same mix") { installed.installedMix shouldBe fromWire.mix }

        fromWire.floor shouldBe null
        installed.installedFloor shouldBe VOWEL_FLOOR
    }

    "the engine's gates really do sit on time and size, not on the wet" {
        // The premise of the classic chain's zero `delay.time` and `reverb.size`, read off the
        // engine rather than asserted in prose. If
        // a gate ever moves to the wet knob, the chain's family-3 slots are free to go back to
        // their constants, and this is where that shows up.
        val delayGate = KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS
        val reverbGate = KatalystReverbEffect.MIN_ACTIVE_SIZE

        (slot("delay.time") < delayGate) shouldBe true
        (Reverb.normalizeSize(slot("reverb.size")) < reverbGate) shouldBe true
    }
})
