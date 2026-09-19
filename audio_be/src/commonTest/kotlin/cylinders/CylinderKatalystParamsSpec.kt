/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
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
import kotlin.math.abs

/**
 * The orbit's **param state** (Katalyst step 5a): what `.katp` and the bus doors write reaches a
 * DECLARED chain's `Param` slots through the voice that holds the orbit's lease, and nothing else
 * about the orbit changes.
 *
 * Three questions, and they are separate on purpose:
 *
 *  - what a chain RESOLVES from a state, and what it costs (the chain rows);
 *  - who supplies that state and when it is re-read (the cylinder rows, where the lease lives);
 *  - that the chain a cylinder is BORN with reads it exactly like a declared one (the last rows;
 *    since step 5b-1 the map is the ONE way a bus knob reaches a stage).
 *
 * What a pattern writes into the map is `sprudel`'s `LangKatalystParamSpec`; what a slot means once
 * resolved is `KatalystSlotResolverSpec`.
 */
class CylinderKatalystParamsSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /**
     * `Katalyst(k => k.classic())`: the classic stages, so every knob is a named slot.
     *
     * It used to append a `gain(1.0)` of its own, because the classic chain had no gain stage and
     * these rows wanted one. Since 2026-09-19 it carries one at unity, so the appendix went with
     * the scaffolding rule; `declaredClassic` stays as a name for "the classic chain, by name".
     */
    val declaredClassic = KatalystDsl.classic

    fun build(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
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

        chain.applyParams(room(size = 6.0))

        val unit = chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        // The state carries the AUTHORED 0..10 size, so it goes through the one shared conversion,
        // exactly as a `Constant` slot does.
        unit.size shouldBe Reverb.normalizeSize(6.0)
        withClue("the authored 6 is not the normalized value") { (unit.size == 6.0) shouldBe false }
    }

    "the same chain with no state has its reverb off: a slot's default is the classic OFF value" {
        val chain = build(declaredClassic)

        chain.applyParams(null)

        // `reverb.wet` and `reverb.size` default to 0.0 on the classic chain, so nothing rents.
        chain.reverb.shouldNotBeNull().reverb.shouldBeNull()
    }

    "the re-resolve is gated on the map INSTANCE: a live owner costs one read, not one per block" {
        val chain = build(declaredClassic)
        val state = room(size = 6.0)

        repeat(64) { chain.applyParams(state) }

        // One read for 64 blocks. Without the identity gate this is 64, and every one of them
        // rebuilds the stages' composites on the audio thread.
        chain.resolveCount shouldBe 1
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)
    }

    "a CHANGED map re-resolves, and the new value reaches the stage" {
        val chain = build(declaredClassic)

        chain.applyParams(room(size = 6.0))
        chain.resolveCount shouldBe 1

        chain.applyParams(room(size = 2.0))

        chain.resolveCount shouldBe 2
        chain.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(2.0)
    }

    "identity, not equality: an equal map in a fresh instance is read again" {
        val chain = build(declaredClassic)

        chain.applyParams(room(size = 6.0))
        chain.applyParams(room(size = 6.0))

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

        chain.applyParams(mapOf("reverb.size" to 9.0))
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

        chain.applyParams(mapOf("reverb.size" to 9.0))

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

        chain.applyParams(mapOf("phaser.wet" to 0.8, "phaser.floor" to 0.2))

        val phaser = chain.phaser.shouldNotBeNull().phaser

        phaser.depth shouldBe 0.8
        phaser.floor shouldBe 0.2

        // An unset FLOOR, with the phaser still engaged so the kernel params are written: a NaN
        // here reaches `WetDryMix.dryCoeff` and comes out as a full notch.
        chain.applyParams(mapOf("phaser.wet" to 0.8, "phaser.floor" to SLOT_UNSET))

        phaser.floor shouldBe PHASER_FLOOR

        // An unset WET is OFF. `Phaser.depth`'s setter DROPS a non-finite value and keeps the depth
        // it had, so without the guard the phaser stays engaged at 0.8 forever.
        chain.applyParams(mapOf("phaser.wet" to SLOT_UNSET, "phaser.floor" to 0.2))

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

        chain.applyParams(mapOf("duck.orbit" to 1.0, "duck.depth" to 0.8))
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

    // ── The BORN-WITH chain reads the state, for EVERY stage (step 5b-1) ────────────────────────
    //
    // The map is the one way a bus knob reaches a stage, on a declared chain and on the chain a
    // cylinder is born with alike; the voice's bus FIELDS are not a knob source any more. The
    // rule's one home is the `katp` door's KDoc in `sprudel/lang/lang_katalyst.kt`. These two rows
    // are the pair that catches a half-done deletion: one says the map alone switches a stage ON,
    // the other says the fields alone leave it OFF.

    "the BORN-WITH chain takes its room from the state, with the voice's bus FIELDS null" {
        val rig = Rig()

        // No `requestChain`, so the cylinder runs the chain it was born with. The voice carries a
        // room in its slot state and nothing at all in its bus FIELDS (`createSynthVoice` leaves
        // `reverb` at amount 0, size 0, which is the untouched wire voice).
        rig.cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)

        rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe Reverb.normalizeSize(6.0)
    }

    "the BORN-WITH chain ignores the voice's bus FIELDS: fields set, no state, the room stays off" {
        val rig = Rig()

        // The reverse row, and the one that goes red on a half-done deletion: a voice whose
        // `Voice.Reverb` names a big room and whose `katalystParams` is null must leave the orbit
        // dry, because the fields stopped being a knob source in step 5b-1. They stay on the wire
        // for the per-voice send AMOUNT until 5b-2, which is why this voice still HAS them.
        rig.cylinder.updateFromVoice(
            VoiceTestHelpers.createSynthVoice(
                reverb = Voice.Reverb(amount = 0.5, size = 0.6),
                delay = Voice.Delay(amount = 0.5, time = 0.3, feedback = 0.2, cap = 1.0),
                katalystParams = null,
            ),
            blockStart = 0.0,
        )

        withClue("no reverb network rented") { rig.cylinder.reverb.shouldNotBeNull().reverb.shouldBeNull() }
        withClue("no delay ring rented") { rig.cylinder.delay.shouldNotBeNull().delayLine.shouldBeNull() }
    }

    "two voices on one orbit: the dry OWNER does not silence the other voice's room" {
        // The MAJOR of review round 1, built as the scenario it is about, and heard rather than
        // read off a field. `wet` is documented as a PER-VOICE send, so `reverb(0)` on the voice
        // that happens to hold the lease must not take the room away from the voice that is
        // sending 0.6 into it. Which of the two owns the orbit is first-rendered-wins, so the
        // alternative would make a song depend on the order of a `stack`'s arms.
        //
        // Both voices offer themselves in the same block, the DRY one first, so it wins the lease
        // and its slots are the ones the bus reads. The wet one's send is written into the orbit's
        // reverb send buffer the way `SendRenderer` writes it, and what the row measures is the
        // orbit's MIX after `processEffects`: the room's return, or silence.
        fun roomReturn(ownerSlots: Map<String, Double>): Double {
            val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

            // The owner: dry, but it named the stage.
            cylinder.updateFromVoice(voice(ownerSlots), blockStart = 0.0)
            // The second voice on the same orbit: its claim is refused, its SEND is not.
            cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)

            var peak = 0.0

            // Freeverb's shortest comb is 1116 frames, so a single block returns silence whatever
            // the settings: render past it and take the loudest return.
            repeat(40) {
                cylinder.mixBuffer.clear()
                cylinder.reverbSendBuffer.left.fill(0.5)
                cylinder.reverbSendBuffer.right.fill(0.5)
                cylinder.processEffects()

                for (sample in cylinder.mixBuffer.left) {
                    val level = abs(sample)

                    if (level > peak) {
                        peak = level
                    }
                }
            }

            return peak
        }

        // The owner WROTE `reverb(0, size = 6)`: dry itself, and the room runs for the orbit.
        val heard = roomReturn(mapOf("reverb.wet" to 0.0, "reverb.size" to 6.0))

        withClue("the other voice's send comes back out of the room, peak $heard") {
            heard shouldBeGreaterThan 0.01
        }

        // The control, and the half that keeps the 2026-09-17 decision: an owner that never named
        // the stage at all asked for no room, and then the same send goes nowhere.
        val silent = roomReturn(emptyMap())

        withClue("an orbit nobody asked for a room on returns nothing, peak $silent") {
            silent shouldBe 0.0
        }
    }

    "the fader halves the orbit's mix, exactly" {
        // The same rule on the CYLINDER path and at sample level: no declaration, no
        // `requestChain`, just a voice whose `katalystParams` name the group fader.
        //
        // A SCALING LAW, not an identity: both sides are cylinder renders, so what this pins is
        // that the orbit's mix is LINEAR in the slot, not that any particular sample is right.
        fun render(params: Map<String, Double>?): DoubleArray {
            val cylinder = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

            cylinder.updateFromVoice(
                VoiceTestHelpers.createSynthVoice(katalystParams = params),
                blockStart = 0.0,
            )

            for (i in 0 until blockFrames) {
                cylinder.mixBuffer.left[i] = 0.5 * (if (i % 8 < 4) 1.0 else -1.0)
                cylinder.mixBuffer.right[i] = 0.25 * (if (i % 8 < 4) 1.0 else -1.0)
            }

            cylinder.processEffects()

            return cylinder.mixBuffer.left.copyOf()
        }

        val unity = render(null)
        val halved = render(mapOf("gain.gain" to 0.5))

        withClue("not-silence floor") { unity.maxOf { abs(it) } shouldBeGreaterThan 0.1 }

        withClue("engagement: the write has to move the mix at all") {
            halved.toList() shouldNotBe unity.toList()
        }

        for (i in unity.indices) {
            withClue("sample $i") { halved[i].toRawBits() shouldBe (unity[i] * 0.5).toRawBits() }
        }
    }

    "a DECLARED classic resolves the state: the same stages, listening to the slots" {
        // `Katalyst(k => k.classic())` by name. Since step 5b-1 it resolves BACK to the instance
        // the cylinder was born with (`Cylinder.chainFor`), which is why the room it shows here is
        // the same room the born-with rows above show: the two are one chain.
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

    "a content-classic declaration installs NO second chain: the born-with instance keeps playing" {
        // The consequence of the shortcut in `Cylinder.chainFor`, and the reason it exists: with
        // both chains slot-driven, a `Katalyst(k => k.classic())` on an orbit changes nothing, so
        // it must not cost a crossfade (which is not bit-transparent: the arriving room and ring
        // warm from empty). Read off the DSP state rather than off a flag: the reverb network the
        // orbit had rented is still rented, and still the same instance, after the request.
        val rig = Rig()
        rig.registry.register("classic", KatalystDsl.classic)

        rig.cylinder.updateFromVoice(voice(room(size = 6.0)), blockStart = 0.0)

        val before = rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull()

        rig.cylinder.requestChain("classic")

        withClue("the same network, so the room was never swapped out and back in") {
            rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull() shouldBeSameInstanceAs before
        }

        // The engagement control: a chain with DIFFERENT content does swap, and the orbit's
        // reverb goes with it. Without this row the one above would pass on a `requestChain` that
        // does nothing at all.
        rig.registry.register("other", KatalystDsl.of(KatalystStageDsl.Compressor()))
        rig.cylinder.requestChain("other")

        rig.cylinder.reverb.shouldBeNull()
    }
})
