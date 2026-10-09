/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * [KatalystDsl.classic] claims to be what an orbit runs when the song never wrote a bus door. This
 * is the test that checks that claim against the engine's own gates and the wire's own values.
 *
 * **What this spec is, after Katalyst step 5b-1 (2026-09-19).** The chain stopped reading the bus
 * FIELDS, so nothing here compares two chain implementations any more: the rows that did (a
 * born-with chain against a declared one) were the migration fixture of steps 3 to 5a and are
 * gone. What is left is a CONTRACT, in two halves, and both keep an oracle that is not the code
 * under test:
 *
 *  - the classic chain's SLOT DEFAULTS against the wire's own "untouched" table, which was
 *    `VoiceFactory`'s untouched branch, where the constants of an untouched orbit were written
 *    down a second time. The delay, reverb, compressor, duck, body and vowel rows went with their
 *    fields in step 5b-3, the PHASER's with the voice strip's per-voice phaser (phase 3 step 9),
 *    as this KDoc said they would. Their untouched defaults stay pinned by
 *    `KatalystDefaultsSyncSpec` (audio_bridge), the gate facts by the phaser's off row here and by
 *    `KatalystSlotResolverSpec`.
 *  - what a chain INSTALLS from a slot state, against the bank, mix and floor a body or vowel
 *    call stands for. That half is about the engine's non-finite rule (an unset `body.wet` plays
 *    at BODY_WET) and outlives the fields. **The expected value is HAND-BUILT in those rows**, not
 *    taken from `SprudelVoiceData.toVoiceData` (this module does not depend on `sprudel`), so the
 *    `BODY_WET` / `VOWEL_WET` literal in them IS the assertion; the catalogue supplies the bands.
 *    (Until engine tidy-up step 12 (c) the expected value was the bridge's band carrier,
 *    `FilterDef.Body` / `FilterDef.Formant`, retired then.) What the DOOR writes, and therefore that
 *    the hand-built value is the one a song produces, is sprudel's `LangKatalystParamSpec`; what
 *    the two render to is `KatalystDoorFillRenderSpec`.
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
                is KatalystStageDsl.Distort -> listOf(stage.amount)
                is KatalystStageDsl.Eq -> emptyList()
                is KatalystStageDsl.Gain -> listOf(stage.gain)
                // The classic chain declares no parallel stage.
                is KatalystStageDsl.Parallel -> emptyList()
            }.forEach { knob -> knob.collectParams(params) }
        }

        return params.first { it.name == name }.default
    }

    "the classic phaser is OFF by default, by the engine's own gate rather than by looking zero" {
        (slot("phaser.wet") < Phaser.MIN_ACTIVE_DEPTH) shouldBe true
    }

    "a MATERIAL-ONLY body reaches the classic chain at the engine's own wet, not dry" {
        // The bug round 1 of step 5a-2 found, and the row that would have caught it. This is the
        // RAW `katp` shape: only the index is written, so the amount is whatever an unset
        // `body.wet` slot resolves to. With the old 0.0 default that was a fully dry mix,
        // bit-identically silent, where a material-only call plays the bank at BODY_WET. The
        // sprudel door fills the amount itself since step 5a-3, which is why this row writes the
        // slot by hand: it guards the ENGINE's non-finite rule, the door's fill is
        // `LangKatalystParamSpec`'s subject.
        val bands = BodyMaterials.modesFor("wood").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood")))
        }

        // The expected bank and mix are what a material-only call stands for, HAND-BUILT here with
        // the constant written out (this module cannot call the sprudel door), so the `BODY_WET`
        // literal is what this row asserts; the catalogue supplies the bands. The chain's installed
        // bank has to match it although nothing wrote `body.wet`.
        val installed = chain.body.shouldNotBeNull()

        withClue("the stage engages") { installed.isEngaged shouldBe true }
        withClue("the same modes") { installed.installedTable.bandList() shouldBe bodyBandList(bands) }

        // The discriminator: NOT dry. A chain that resolved its unset amount to 0.0 would install
        // the right bank at no mix at all, which is silence dressed as a body.
        withClue("the mix is audible, not the dry 0.0 a SET slot would install") {
            (installed.installedMix > 0.0) shouldBe true
        }
        withClue("...and it is the engine's own wet") { installed.installedMix shouldBe BODY_WET }
    }

    "a VOWEL-ONLY call reaches the classic chain at the engine's own wet too" {
        // The twin, on the other stage and the other constant: two writers, two substitutions.
        val bands = VowelBands.bandsFor("a").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("a")))
        }

        val installed = chain.vowel.shouldNotBeNull()

        installed.isEngaged shouldBe true
        installed.installedTable.bandList() shouldBe vowelBandList(bands)

        withClue("the mix is audible, not dry") { (installed.installedMix > 0.0) shouldBe true }
        installed.installedMix shouldBe VOWEL_WET
    }

    "the classic chain installs the same bank a body call names, slot for slot" {
        // The equivalence the step-5a-2 index slot exists for: `body(material = "wood", wet = 0.3)` must
        // reach the orbit's resonator through the INDEX slot with the bank and the mix the call
        // names. The oracle is the catalogue's own modes and the call's mix; nothing here types a
        // mode. The name-to-index half of the trip is `CatalogueIndexSpec`'s, and what the sprudel
        // door writes is `LangKatalystParamSpec`'s.
        val bands = BodyMaterials.modesFor("wood").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("body.material" to BodyMaterials.indexOf("wood"), "body.wet" to 0.3))
        }

        val installed = chain.body.shouldNotBeNull()

        withClue("the chain engages its body from the slots alone") { installed.isEngaged shouldBe true }
        withClue("the SAME modes the catalogue holds, not just some bank") {
            installed.installedTable.bandList() shouldBe bodyBandList(bands)
        }
        withClue("...and the same mix") { installed.installedMix shouldBe 0.3 }

        // The call names no floor: the classic chain's `body.floor` slot carries its knob default, BODY_FLOOR
        // (`KatalystDslSlots`), and the writer hands that finite number to the stage.
        installed.installedFloor shouldBe BODY_FLOOR
    }

    "the classic chain installs the same formant bank a vowel call names" {
        // The twin of the body row, on the other index slot. Written out rather than folded in:
        // the two stages read two catalogues, and a `vowel.vowel` wired to the body's catalogue
        // (or to no catalogue at all) would pass a body-only spec. The register is `bass`, not the
        // bare default, so a resolver that dropped the register would land on soprano and fail.
        val bands = VowelBands.bandsFor("bass:a").shouldNotBeNull()

        val chain = classicChain().also {
            it.applyParams(mapOf("vowel.vowel" to VowelBands.indexOf("bass:a"), "vowel.wet" to 0.6))
        }

        val installed = chain.vowel.shouldNotBeNull()

        withClue("the chain engages its vowel from the slots alone") { installed.isEngaged shouldBe true }
        withClue("the SAME bands, the bass register and not the soprano default") {
            installed.installedTable.bandList() shouldBe vowelBandList(bands)
        }
        withClue("...and the same mix") { installed.installedMix shouldBe 0.6 }

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
