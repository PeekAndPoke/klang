/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChain
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * The orbit's **param state** (Katalyst step 5a): what `.katp` and the bus doors write reaches a
 * DECLARED chain's `Param` slots through the voice that holds the orbit's lease, and nothing else
 * about the orbit changes.
 *
 * Three questions, and they are separate on purpose:
 *
 *  - what a declared chain RESOLVES from a state, and what it costs (the chain rows);
 *  - who supplies that state and when it is re-read (the cylinder rows, where the lease lives);
 *  - what the CLASSIC chain does with it, which is nothing (the last row, the byte identity every
 *    song that declares no chain depends on).
 *
 * What a pattern writes into the map is `sprudel`'s `LangKatalystParamSpec`; what a slot means once
 * resolved is `KatalystSlotResolverSpec`.
 */
class CylinderKatalystParamsSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** `Katalyst(k => k.classic().gain(1.0))`: the classic stages, so every knob is a named slot. */
    val declaredClassic = KatalystDsl(
        KatalystDsl.classic.stages + KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(1.0))
    )

    fun build(dsl: KatalystDsl, voiceDriven: Boolean = false): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
        voiceDriven = voiceDriven,
    )

    /** A voice with no bus fields of its own, carrying only the orbit's slot state. */
    fun voice(params: Map<String, Double>?): Voice =
        VoiceTestHelpers.createSynthVoice(katalystParams = params)

    /** What `.reverb(wet = 0.5, size = 6)` writes: the slot the door fills plus the one it names. */
    fun room(size: Double): Map<String, Double> = mapOf("reverb.wet" to 0.5, "reverb.size" to size)

    class Rig(val registry: KatalystRegistry = KatalystRegistry()) {
        val cylinder = Cylinder(
            id = 0,
            blockFrames = 128,
            sampleRate = 44100,
            silentBlocksBeforeTailCheck = 0,
            katalysts = registry,
        )
    }

    // ── What a declared chain resolves, and what it costs ────────────────────────────────────────

    "a declared classic chain takes its room from the owner's slot state" {
        val chain = build(declaredClassic)

        chain.applyOwner(voice(room(size = 6.0)))

        val unit = chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        // The state carries the AUTHORED 0..10 size, so it goes through the one shared conversion,
        // exactly as a `Constant` slot does.
        unit.size shouldBe Reverb.normalizeSize(6.0)
        withClue("the authored 6 is not the normalized value") { (unit.size == 6.0) shouldBe false }
    }

    "the same chain with no state has its reverb off: a slot's default is the classic OFF value" {
        val chain = build(declaredClassic)

        chain.applyOwner(voice(null))

        // `reverb.wet` and `reverb.size` default to 0.0 on the classic chain, so nothing rents.
        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "the re-resolve is gated on the map INSTANCE: a live owner costs one read, not one per block" {
        val chain = build(declaredClassic)
        val state = room(size = 6.0)
        val owner = voice(state)

        repeat(64) { chain.applyOwner(owner) }

        // One read for 64 blocks. Without the identity gate this is 64, and every one of them
        // rebuilds the stages' composites on the audio thread.
        chain.resolveCount shouldBe 1
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)
    }

    "a CHANGED map re-resolves, and the new value reaches the stage" {
        val chain = build(declaredClassic)

        chain.applyOwner(voice(room(size = 6.0)))
        chain.resolveCount shouldBe 1

        chain.applyOwner(voice(room(size = 2.0)))

        chain.resolveCount shouldBe 2
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(2.0)
    }

    "identity, not equality: an equal map in a fresh instance is read again" {
        val chain = build(declaredClassic)

        chain.applyOwner(voice(room(size = 6.0)))
        chain.applyOwner(voice(room(size = 6.0)))

        // Two instances, one value. The gate is a reference compare by design (a per-block map
        // comparison would cost more than the read it saves), so this is two reads and the same
        // sound. Recorded rather than asserted-away: it is the cost of the cheap gate.
        chain.resolveCount shouldBe 2
    }

    "applyParams(null) resolves every Param to its authored default, whatever an owner wrote before" {
        // `wet` a constant, so the stage stays on and the SIZE is the observable.
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = IgnitorDsl.Constant(0.5),
                    size = IgnitorDsl.Param("reverb.size", default = 3.0),
                )
            )
        )

        chain.applyParams(null)
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(3.0)

        chain.applyOwner(voice(mapOf("reverb.size" to 9.0)))
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(9.0)

        // No owner, no state: back to what the chain itself says.
        chain.applyParams(null)
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(3.0)
    }

    "a Constant knob is not a slot: a state entry of the same name never moves it" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(4.0))
            )
        )

        chain.applyOwner(voice(mapOf("reverb.size" to 9.0)))

        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(4.0)
    }

    "a non-finite phaser slot is unset, not 'keep what the setter had'" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Phaser(
                    wet = IgnitorDsl.Param("phaser.wet", 0.0),
                    floor = IgnitorDsl.Param("phaser.floor", 1.0),
                )
            )
        )

        chain.applyOwner(voice(mapOf("phaser.wet" to 0.8, "phaser.floor" to 0.2)))

        val phaser = chain.phaser.shouldNotBeNull().phaser

        phaser.depth shouldBe 0.8
        phaser.floor shouldBe 0.2

        // An unset FLOOR, with the phaser still engaged so the kernel params are written: a NaN
        // here reaches `WetDryMix.dryCoeff` and comes out as a full notch.
        chain.applyOwner(voice(mapOf("phaser.wet" to 0.8, "phaser.floor" to SLOT_UNSET)))

        phaser.floor shouldBe PHASER_FLOOR

        // An unset WET is OFF. `Phaser.depth`'s setter DROPS a non-finite value and keeps the depth
        // it had, so without the guard the phaser stays engaged at 0.8 forever.
        chain.applyOwner(voice(mapOf("phaser.wet" to SLOT_UNSET, "phaser.floor" to 0.2)))

        phaser.depth shouldBe PHASER_WET

        withClue("and a gated-off phaser keeps its kernel params, so the sweep clock runs on") {
            phaser.floor shouldBe PHASER_FLOOR
        }
    }

    "a chain leaving service forgets the state: a cached duck must not claim an envelope" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Duck(
                    orbit = IgnitorDsl.Param("duck.orbit", default = Double.NaN),
                    depth = IgnitorDsl.Param("duck.depth", default = 0.0),
                )
            )
        )

        chain.applyOwner(voice(mapOf("duck.orbit" to 1.0, "duck.depth" to 0.8)))
        chain.ducksWith() shouldBe true

        // Back on the shelf, and back in the cache. The host asks this BEFORE the arriving chain's
        // writers run (`Cylinder.handOverDuck`), so a remembered "yes" here hands a live envelope
        // to a stage that is never configured.
        chain.retire()

        chain.ducksWith() shouldBe false
    }

    // ── The lease supplies the state ─────────────────────────────────────────────────────────────

    "on a cylinder: the OWNER's state configures the orbit, and an owner change hands it over" {
        val rig = Rig()
        rig.registry.register("bus", declaredClassic)
        rig.cylinder.requestChain("bus")

        rig.cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)
        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)

        // The owner misses more than a block, so the next voice takes the lease with ITS state.
        rig.cylinder.updateFromVoice(voice(room(size = 2.0)), blockStart = 4.0 * blockFrames)
        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(2.0)
    }

    "on a cylinder: a NON-owner's state is ignored while the owner is alive" {
        val rig = Rig()
        rig.registry.register("bus", declaredClassic)
        rig.cylinder.requestChain("bus")

        val owner = voice(room(size = 6.0))

        rig.cylinder.updateFromVoice(owner, blockStart = 0.0)
        rig.cylinder.updateFromVoice(voice(room(size = 2.0)), blockStart = 0.0)

        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)
    }

    // ── The BORN-WITH chain ignores it, a DECLARED classic does not ──────────────────────────────

    "the BORN-WITH chain ignores the state: it is voice-driven until step 5b" {
        val rig = Rig()

        // No `requestChain`, so the cylinder runs the chain it was born with. The voice carries a
        // loud room in its slot state and nothing in its bus FIELDS.
        rig.cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)

        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "a DECLARED classic resolves the state: the same stages, listening to the slots" {
        // The rule of 2026-09-18 (review round 2): voice-driven is only what a cylinder is BORN
        // with, so `Katalyst.classic()` by name is a declaration and its knobs are slots. Before
        // it, `chainFor` handed the born-with instance back for that content and every `katp` on
        // the orbit went nowhere.
        val rig = Rig()
        rig.registry.register("classic", KatalystDsl.classic)
        rig.cylinder.requestChain("classic")

        rig.cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)

        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)

        withClue("and it really is the historical stage list, not a reverb on its own") {
            rig.cylinder.pipeline.size shouldBe KatalystDsl.classic.stages.size - 1 // the duck runs outside
            rig.cylinder.duck.shouldNotBeNull()
        }
    }

    "the born-with chain renders byte-identically with and without a state" {
        fun render(params: Map<String, Double>?): DoubleArray {
            val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

            cylinder.updateFromVoice(
                VoiceTestHelpers.createSynthVoice(
                    delay = Voice.Delay(amount = 0.3, time = 0.05, feedback = 0.4, cap = 1.0),
                    reverb = Voice.Reverb(amount = 0.3, size = 0.5),
                    katalystParams = params,
                ),
                blockStart = 0.0,
            )

            for (i in 0 until blockFrames) {
                val v = 0.5 * (if (i % 8 < 4) 1.0 else -1.0)
                cylinder.mixBuffer.left[i] = v
                cylinder.mixBuffer.right[i] = v
                cylinder.reverbSendBuffer.left[i] = v * 0.3
                cylinder.reverbSendBuffer.right[i] = v * 0.3
            }

            cylinder.processEffects()

            return cylinder.mixBuffer.left.copyOf()
        }

        render(mapOf("reverb.size" to 9.0, "delay.time" to 0.4, "compressor.ratio" to 12.0)) shouldBe render(null)
    }
})
