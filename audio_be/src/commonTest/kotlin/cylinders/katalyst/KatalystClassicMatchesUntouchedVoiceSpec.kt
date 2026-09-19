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
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET
import kotlin.random.Random

/**
 * [KatalystDsl.classic] claims to be what an orbit runs when the song never wrote a bus door. This
 * is the test that checks that claim against the thing it is a claim about: a [Voice] built by the
 * real [VoiceFactory] from a [VoiceData] with no bus fields at all.
 *
 * **What this spec is, after Katalyst step 5b-1 (2026-09-19).** The chain stopped reading the bus
 * FIELDS, so nothing here compares two chain implementations any more: the rows that did (a
 * born-with chain against a declared one) were the migration fixture of steps 3 to 5a and are
 * gone. What is left is a CONTRACT, in two halves, and both keep an oracle that is not the code
 * under test:
 *
 *  - the classic chain's SLOT DEFAULTS against the wire's own "untouched" table, which is
 *    `VoiceFactory`'s untouched branch. That branch still runs (the fields carry the per-voice
 *    send amounts until step 5b-2) and it is where the constants of an untouched orbit are
 *    written down a second time, which is what makes it an oracle rather than a mirror. It expires
 *    with the fields in step 5b-3, and this spec's first half goes with it.
 *  - what a chain INSTALLS from a slot state, against the `FilterDef` a body or vowel call puts on
 *    the wire. That half is about the engine's non-finite rule (an unset `body.wet` plays at
 *    BODY_WET) and outlives the fields. **The wire value is HAND-BUILT in those rows**, not taken
 *    from `SprudelVoiceData.toVoiceData` (this module does not depend on `sprudel`), so the
 *    `BODY_WET` / `VOWEL_WET` literal in them IS the assertion and the `voiceOf(...)` build is
 *    there for the bands and for the floor's null. What the DOOR writes, and therefore that the
 *    hand-built value is the one a song produces, is sprudel's `LangKatalystParamSpec`; what the
 *    two render to is `KatalystDoorFillRenderSpec`.
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
 * `reverb.size` is compared THROUGH [Reverb.normalizeSize], the conversion `VoiceFactory` applies
 * on the authored 0..10 scale, so the two sides agree by construction rather than by the accident
 * that both happen to be zero.
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
     * Note and sound only: every delay/reverb/phaser/compressor/duck/body/vowel field stays null,
     * which is what "the song never wrote a bus door" is on the wire.
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

    "the classic delay slots ARE the untouched voice's delay, knob for knob" {
        val delay = untouchedVoice().delay

        withClue("delay.wet vs Voice.Delay.amount") { slot("delay.wet") shouldBe delay.amount }
        withClue("delay.time vs Voice.Delay.time") { slot("delay.time") shouldBe delay.time }
        withClue("delay.feedback vs Voice.Delay.feedback") { slot("delay.feedback") shouldBe delay.feedback }
        // cap is the odd one: an untouched voice still carries DELAY_CAP, because a cap is the
        // soft-clip shape of the line, not an amount. So must the chain.
        withClue("delay.cap vs Voice.Delay.cap") { slot("delay.cap") shouldBe delay.cap }
    }

    "the classic reverb slots ARE the untouched voice's reverb, on the same scale" {
        val reverb = untouchedVoice().reverb

        withClue("reverb.wet vs Voice.Reverb.amount") { slot("reverb.wet") shouldBe reverb.amount }
        // The slot is on the authored 0..10 scale and the voice field is normalized, so the
        // comparison goes through the one shared conversion. Both being zero today must not be the
        // reason this passes.
        withClue("normalizeSize(reverb.size) vs Voice.Reverb.size") {
            Reverb.normalizeSize(slot("reverb.size")) shouldBe reverb.size
        }
        // The lowpass is the nullable one: the voice carries null (the engine's fixed damping) and
        // the chain carries the non-finite marker, which is the same statement in the two type
        // systems. Pinned as an iff so neither side can drift alone.
        withClue("reverb.lowpass unset on both sides") {
            slot("reverb.lowpass").isFinite() shouldBe (reverb.lowpass != null)
            reverb.lowpass shouldBe null
        }
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

    "the untouched voice has NO compressor, iff all five classic slots are unset" {
        // `Voice.Compressor.fromParams` gates on "ANY of the five set", so this is an iff and not
        // an implication: one finite slot in the classic chain and the step-2 resolver would build
        // a compressor for every orbit of every song that never wrote `compressor(...)`.
        val compressorSlots = listOf(
            "compressor.threshold", "compressor.ratio", "compressor.knee", "compressor.attack",
            "compressor.release",
        )
        val allUnset = compressorSlots.all { !slot(it).isFinite() }

        withClue("classic slots: ${compressorSlots.associateWith { slot(it) }}") {
            (untouchedVoice().compressor == null) shouldBe allUnset
        }
    }

    "a voice that sets ONE compressor knob gets a compressor, with the constant threshold" {
        // The scenario behind the iff, and the reason the threshold cannot be the lone gate: the
        // sprudel door's tail-only call `compressor(ratio = 8)` leaves threshold untouched, and the
        // voice still compresses at COMPRESSOR_THRESHOLD_DB. A step-2 resolver that reproduced
        // `fromParams` would have to do the same from `katp("compressor.ratio", 8)`.
        val voice = voiceOf(VoiceData.empty.copy(freqHz = 440.0, sound = "triangle", compressorRatio = 8.0))
        val compressor = voice.compressor

        compressor.shouldNotBeNull()
        compressor.ratio shouldBe 8.0
        compressor.thresholdDb shouldBe COMPRESSOR_THRESHOLD_DB
        compressor.kneeDb shouldBe COMPRESSOR_KNEE_DB
        compressor.attackSeconds shouldBe COMPRESSOR_ATTACK_SECONDS
        compressor.releaseSeconds shouldBe COMPRESSOR_RELEASE_SECONDS
    }

    "the untouched voice has NO ducking, and the classic duck says so with BOTH its knobs" {
        // VoiceFactory gates ducking on `duckCylinder != null && duckDepth != null && depth > 0`,
        // so the chain has to be off on both counts: no source named AND no depth. Either one alone
        // would leave the other free to drift into an audible duck.
        untouchedVoice().ducking shouldBe null

        withClue("duck.orbit names no source") { slot("duck.orbit").isFinite() shouldBe false }
        withClue("duck.depth is zero") { slot("duck.depth") shouldBe 0.0 }
    }

    "the untouched voice has NO body and NO vowel, and the classic slots name neither" {
        // Body and vowel are the two stages whose gate is a NAME, and since Katalyst step 5a-2 the
        // name travels as the INDEX of a name in a shared catalogue: `FilterDef.Body` /
        // `FilterDef.Formant` only exist on a voice that named one, and `configure(null)` is off.
        // So the chain's off state is an UNSET index. Unset and not 0, because 0 is `none`, a real
        // catalogue entry. The two wet slots are unset as well, which the rows below assert; that
        // is not a second gate, it is what lets the engine substitute BODY_WET / VOWEL_WET.
        val voice = untouchedVoice()

        voice.body shouldBe null
        voice.vowel shouldBe null

        withClue("body.material names nothing") { slot("body.material").isFinite() shouldBe false }
        withClue("vowel.vowel names nothing") { slot("vowel.vowel").isFinite() shouldBe false }

        // The AMOUNT is unset too, and it has to be: the engine substitutes BODY_WET / VOWEL_WET
        // for an unset mix (the compressor's rule), and a 0.0 here is a SET amount that never gets
        // substituted, which is how a material-only `body("wood")` came out dry on a declared
        // chain (round 1 of step 5a-2's review). Belt: the stage is gated on the NAME, so an
        // unset amount cannot switch anything on either, which is what the two rows below assert.
        withClue("body.wet is unset, not a set zero") { slot("body.wet").isFinite() shouldBe false }
        withClue("vowel.wet is unset, not a set zero") { slot("vowel.wet").isFinite() shouldBe false }
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
        val voice = voiceOf(
            VoiceData.empty.copy(
                freqHz = 440.0,
                sound = "triangle",
                filters = FilterDefs(listOf(FilterDef.Body(bands = bands, mix = BODY_WET))),
            )
        )

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood")))
        }

        // `fromWire` is the `FilterDef.Body` a material-only call puts on the wire, HAND-BUILT
        // here with the constant written out (this module cannot call the sprudel door), so the
        // `BODY_WET` literal is what this row asserts and `voiceOf` supplies the bands and the null
        // floor. The chain's installed bank has to match it although nothing wrote `body.wet`.
        val fromWire = voice.body.shouldNotBeNull()
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
        val voice = voiceOf(
            VoiceData.empty.copy(
                freqHz = 440.0,
                sound = "triangle",
                filters = FilterDefs(listOf(FilterDef.Formant(bands = bands, mix = VOWEL_WET))),
            )
        )

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("a")))
        }

        val fromWire = voice.vowel.shouldNotBeNull()
        val installed = chain.vowel.shouldNotBeNull()

        installed.isEngaged shouldBe true
        installed.installedBands shouldBe fromWire.bands

        withClue("the mix is audible, not dry") { (installed.installedMix > 0.0) shouldBe true }
        installed.installedMix shouldBe fromWire.mix
        installed.installedMix shouldBe VOWEL_WET
    }

    "the classic chain installs the same bank the wire carries for a body call, slot for slot" {
        // The equivalence the step-5a-2 index slot exists for: `body("wood", wet = 0.3)` must
        // reach the orbit's resonator through the INDEX slot with the bank and the mix the wire
        // carries for the same call. The oracle is that wire value, built by the real
        // `VoiceFactory` from the real `FilterDef`; nothing here types a mode. The name-to-index
        // half of the trip is `CatalogueIndexSpec`'s, and what the sprudel door writes is
        // `LangKatalystParamSpec`'s.
        val bands = BodyMaterials.modesFor("wood").shouldNotBeNull()
        val voice = voiceOf(
            VoiceData.empty.copy(
                freqHz = 440.0,
                sound = "triangle",
                filters = FilterDefs(listOf(FilterDef.Body(bands = bands, mix = 0.3))),
            )
        )

        voice.body.shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 0.3))
        }

        val fromWire = voice.body.shouldNotBeNull()
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
        val voice = voiceOf(
            VoiceData.empty.copy(
                freqHz = 440.0,
                sound = "triangle",
                filters = FilterDefs(listOf(FilterDef.Formant(bands = bands, mix = 0.6))),
            )
        )

        voice.vowel.shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("bass:a"), "vowel.wet" to 0.6))
        }

        val fromWire = voice.vowel.shouldNotBeNull()
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
        // The premise of the two rows above, read off the engine rather than asserted in prose. If
        // a gate ever moves to the wet knob, the chain's family-3 slots are free to go back to
        // their constants, and this is where that shows up.
        val delayGate = KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS
        val reverbGate = KatalystReverbEffect.MIN_ACTIVE_SIZE

        (slot("delay.time") < delayGate) shouldBe true
        (Reverb.normalizeSize(slot("reverb.size")) < reverbGate) shouldBe true
    }
})
