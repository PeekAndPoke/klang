/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import kotlin.math.abs

/**
 * The chain swap of a SOUNDING orbit: the dual-chain crossfade, and the drain that lets the
 * outgoing chain ring out instead of being cut (Katalyst step 3b, `docs/tasks/katalyst-dsl.md` §6
 * and §D3).
 *
 * What a request DOES (the five cases, the cache, `retire` / `adopt`) is `CylinderChainSwapSpec`'s
 * subject; this file is about what the swap SOUNDS like and what it does to the lifecycle.
 *
 * **The probe is DC.** `MasterBusTest` measures the block ENVELOPE and the transition WIDTH rather
 * than raw sample deltas, because its probe is a 440 Hz sine where a sample-to-sample delta is
 * phase-dependent and an unfaded switch can slip under any threshold by luck. A constant is the
 * instrument that makes the raw delta measure valid: every step in the output is then the ramp's
 * own, so the measure catches the one fault an envelope cannot, a weight that moves per BLOCK
 * instead of per sample. The width measure from `MasterBusTest` is asserted as well.
 */
class CylinderChainCrossfadeSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100

    /** Blocks the ramp spans, from the shared constant, plus one for the block it completes in. */
    val fadeBlocks = (Crossfade.XFADE_SECONDS * sampleRate / blockFrames).toInt() + 2

    /** The DC probe's level: what the voices sum into the orbit's mix buffer every block. */
    val probe = 0.5

    /**
     * The per-voice send level for the send-path rows. Low enough that the delay's ring, which
     * saturates softly around 1.0, stays in its linear region at `send / (1 - feedback)`.
     */
    val send = 0.15

    /**
     * The largest sample-to-sample step the fade may show, on a DC probe of [probe].
     *
     * Derived, not borrowed: the ramp moves the incoming weight by `1 / (0.06 * 44100)` per sample,
     * so a per-SAMPLE ramp can step the output by at most `probe / 2646` = 0.00019, while a ramp
     * that moved once per block would step it by `probe * 128 / 2646` = 0.024. The threshold sits
     * 25x above the first and 5x below the second.
     */
    val clickThreshold = 0.005

    /** A chain with neither a delay nor a reverb: one pass-through stage, the "dry" chain. */
    fun dryChain(gain: Double) = KatalystDsl.of(
        KatalystStageDsl.Gain(gain = IgnitorDsl.Constant(gain))
    )

    /** A chain whose only stage is a room of its own, for the warm-up rows. */
    fun roomChain(size: Double) = KatalystDsl.of(
        KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(size))
    )

    /** A chain whose only stage is an echo, so the stage list says which chain is in service. */
    fun echoChain(time: Double) = KatalystDsl.of(
        KatalystStageDsl.Delay(time = IgnitorDsl.Constant(time))
    )

    /** A chain with a room AND an echo of its own, for the send-path rows. */
    fun wetChain(time: Double, size: Double) = KatalystDsl.of(
        KatalystStageDsl.Delay(
            wet = IgnitorDsl.Constant(0.5),
            time = IgnitorDsl.Constant(time),
            feedback = IgnitorDsl.Constant(0.5),
        ),
        KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(size)),
    )

    /** A chain whose only stage is a duck listening to [orbit]. */
    fun duckChain(depth: Double, attack: Double, orbit: Double = 0.0) = KatalystDsl.of(
        KatalystStageDsl.Duck(
            orbit = IgnitorDsl.Constant(orbit),
            depth = IgnitorDsl.Constant(depth),
            attack = IgnitorDsl.Constant(attack),
        )
    )

    /**
     * A chain whose duck takes ALL THREE knobs from the orbit's param state, which is what
     * `.duck(orbit = n, depth = d)` writes on a declared chain from Katalyst step 5a on. The
     * defaults are the classic chain's, so with no state the stage resolves to no duck at all.
     */
    fun slotDuckChain() = KatalystDsl.of(
        KatalystStageDsl.Duck(
            orbit = IgnitorDsl.Param("duck.orbit", SLOT_UNSET),
            depth = IgnitorDsl.Param("duck.depth", 0.0),
            attack = IgnitorDsl.Param("duck.attack", DUCK_ATTACK_SECONDS),
        )
    )

    /**
     * A chain whose reverb size and duck are ALL slots, with authored defaults a row can tell from
     * anything an owner would write: size 3 (a short room) and no duck at all.
     */
    fun roomSlotChain() = KatalystDsl.of(
        KatalystStageDsl.Reverb(
            wet = IgnitorDsl.Constant(0.5),
            size = IgnitorDsl.Param("reverb.size", 3.0),
        ),
        KatalystStageDsl.Duck(
            orbit = IgnitorDsl.Param("duck.orbit", SLOT_UNSET),
            depth = IgnitorDsl.Param("duck.depth", 0.0),
            attack = IgnitorDsl.Param("duck.attack", DUCK_ATTACK_SECONDS),
        ),
    )

    /** What `.duck(orbit = n, depth = d)` writes into the event's slot state. */
    fun duckState(orbit: Double, depth: Double, attack: Double = 0.05): Map<String, Double> =
        mapOf("duck.orbit" to orbit, "duck.depth" to depth, "duck.attack" to attack)

    /**
     * A chain that DECLARES a duck which will never be configured: no orbit is named, so
     * `KatalystSlots.duckSettings` resolves to nothing.
     */
    fun duckChainWithNoOrbit() = KatalystDsl.of(
        KatalystStageDsl.Duck(depth = IgnitorDsl.Constant(0.8))
    )

    /** A ring shelf that records every allocation it is asked for. */
    // Holds the allocator lambda rather than implementing the function type (forbidden on Kotlin/JS).
    class Recording {
        val asked = mutableListOf<Int>()
        val allocate: (Int) -> StereoBuffer? = { frames ->
            asked += frames
            StereoBuffer(frames)
        }
    }

    /**
     * A compressor that pulls a DC probe far down, so the two chains differ in LEVEL and the fade
     * between them is measurable. The classic chain takes it from the owner voice.
     */
    fun squashing() = Voice.Compressor(
        thresholdDb = -40.0,
        ratio = 20.0,
        kneeDb = 2.0,
        attackSeconds = 0.001,
        releaseSeconds = 0.05,
    )

    /** Peak absolute sample over one block's worth of output. */
    fun peakOf(samples: DoubleArray): Double {
        var peak = 0.0

        for (sample in samples) {
            val v = abs(sample)

            if (v > peak) {
                peak = v
            }
        }

        return peak
    }

    class Rig(
        val registry: KatalystRegistry = KatalystRegistry(),
        /** The unit shelf both chains rent their rooms from; a row can hand in a denying one. */
        val reverbs: ReverbUnits = ReverbUnits(sampleRate),
    ) {
        val alloc = Recording()
        val rings = SizedBuffers.forRings(sampleRate, allocate = alloc.allocate)

        val cylinder = Cylinder(
            id = 1,
            blockFrames = blockFrames,
            sampleRate = sampleRate,
            // 0 = one silent tryDeactivate is enough, so a row reads as "it went quiet".
            silentBlocksBeforeTailCheck = 0,
            rings = rings,
            reverbs = reverbs,
            katalysts = registry,
        )

        /** The orbit's owner voice. Replaced (with a skipped block) to hand ownership over. */
        var voice: Voice = VoiceTestHelpers.createSynthVoice()

        /** Absolute backend frame of the next block, as `Cylinders.getOrInit` passes it. */
        var blockStart: Double = 0.0

        /** What another orbit's mix hands the duck pass; filled per block from `sidechainLevel`. */
        val sidechain = StereoBuffer(blockFrames)

        /**
         * One rendered block, in the order the engine uses: the voices offer themselves and fill
         * the orbit's buffers, then `Cylinders.processAndMix` polls the queued chain and runs it.
         * Returns the left channel of what the orbit hands the fusion mix.
         */
        fun block(
            level: Double = 0.0,
            reverbSend: Double = 0.0,
            delaySend: Double = 0.0,
            sidechainLevel: Double = 0.0,
            /** False renders a block no voice offered itself on: the owner lease lapses over it. */
            owned: Boolean = true,
            /** False renders a block whose duck pass `Cylinders` skips (no sidechain orbit). */
            duckPass: Boolean = true,
        ): DoubleArray {
            if (owned) {
                cylinder.updateFromVoice(voice, blockStart)
            }

            cylinder.mixBuffer.fill(level)
            cylinder.reverbSendBuffer.fill(reverbSend)
            cylinder.delaySendBuffer.fill(delaySend)
            cylinder.pollPendingChain()
            cylinder.processEffects()

            // The duck pass `Cylinders.processAndMix` runs after every orbit, on the orbit whose
            // duck names a sidechain that resolves - the SAME gate here, `duckCylinderId != null`
            // and not just "a duck stage exists", or a row could pass on a pass the engine skips.
            // `duckPass = false` is the orbit whose sidechain has disappeared: skipped entirely.
            if (duckPass && cylinder.duck?.duckCylinderId != null) {
                sidechain.fill(sidechainLevel)
                cylinder.processDuck(sidechain)
            }

            blockStart += blockFrames

            return cylinder.mixBuffer.left.copyOf()
        }

        /** A block this orbit is not offered at all: the owner lease lapses over it. */
        fun skipBlock() {
            blockStart += blockFrames
        }

        /** [blocks] rendered blocks, every sample concatenated, so the block seams are measurable. */
        fun render(
            blocks: Int,
            level: Double = 0.0,
            reverbSend: Double = 0.0,
            delaySend: Double = 0.0,
            sidechainLevel: Double = 0.0,
            owned: Boolean = true,
            duckPass: Boolean = true,
        ): DoubleArray {
            val out = DoubleArray(blocks * blockFrames)

            for (b in 0 until blocks) {
                block(
                    level = level,
                    reverbSend = reverbSend,
                    delaySend = delaySend,
                    sidechainLevel = sidechainLevel,
                    owned = owned,
                    duckPass = duckPass,
                ).copyInto(out, b * blockFrames)
            }

            return out
        }

        /** The largest per-block peak over [blocks] rendered blocks. */
        fun peakOver(
            blocks: Int,
            level: Double = 0.0,
            reverbSend: Double = 0.0,
            delaySend: Double = 0.0,
        ): Double {
            var worst = 0.0

            for (b in 0 until blocks) {
                val peak = peakOf(block(level = level, reverbSend = reverbSend, delaySend = delaySend))

                if (peak > worst) {
                    worst = peak
                }
            }

            return worst
        }
    }

    /** True while the cylinder runs the classic chain, the only one with all seven classic effects. */
    fun Cylinder.runsClassic(): Boolean = reverb != null && delay != null && body != null && duck != null

    /** Per-block peak of what the orbit produced, the `MasterBusTest` envelope measure. */
    fun Rig.peaks(blocks: Int, level: Double = 0.0): List<Double> =
        (0 until blocks).map { peakOf(block(level = level)) }

    /**
     * The block rendered just BEFORE a swap, joined to what follows it.
     *
     * Every step measure has to include the seam AT the swap, not just the blocks after it: a
     * mechanism that drops something in the swap's first sample shows up nowhere else.
     */
    fun DoubleArray.after(rest: DoubleArray): DoubleArray = this + rest

    /** The largest sample-to-sample step in [samples], the zipper measure. */
    fun maxStep(samples: DoubleArray): Double {
        var worst = 0.0

        for (i in 1 until samples.size) {
            val step = abs(samples[i] - samples[i - 1])

            if (step > worst) {
                worst = step
            }
        }

        return worst
    }

    /**
     * How many blocks the envelope spends BETWEEN two plateau levels: `MasterBusTest`'s ramp
     * measure, preferred there over a block-to-block ratio whenever the two levels differ a lot.
     */
    fun transitionBlocks(peaks: List<Double>, low: Double, high: Double): Int =
        peaks.count { it > low * 1.25 && it < high * 0.8 }

    // ── Click-free ───────────────────────────────────────────────────────────────────────────────

    "a swap on a SOUNDING orbit ramps per sample: no step, and the level travels" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(compressor = squashing())

        // The compressor's envelope settles on the probe, so the level before the swap is a plateau.
        rig.render(blocks = 60, level = probe)
        val last = rig.block(level = probe)
        val before = peakOf(last)

        rig.cylinder.requestChain("dry")

        val faded = last.after(rig.render(blocks = fadeBlocks, level = probe))
        val after = peakOf(rig.block(level = probe))

        withClue("the swap really happened: the squashed level is gone") {
            (after / before) shouldBeGreaterThan 3.0
        }

        withClue("and no sample stepped there") {
            maxStep(faded) shouldBeLessThan clickThreshold
        }
    }

    "the level travels through the middle instead of teleporting" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(compressor = squashing())

        rig.render(blocks = 60, level = probe)
        val before = peakOf(rig.block(level = probe))

        rig.cylinder.requestChain("dry")

        val peaks = rig.peaks(blocks = fadeBlocks, level = probe)
        val after = peakOf(rig.block(level = probe))

        // A hard switch spans at most one block; a 60 ms ramp spans ~21 at 128 frames / 44.1 kHz.
        transitionBlocks(peaks, low = before, high = after) shouldBeGreaterThan 8
    }

    "the handover from the fade to the drain does not step" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        // A wet room on the classic chain, charged by the voices' reverb send.
        rig.voice = VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.5, size = 0.6))

        // The room is loud, and the voices keep feeding it right across the swap: the leaving
        // chain's send has to be ramped with its dry, or its input would drop from a constant to
        // silence in one sample at the handover and the combs would emit that edge one comb period
        // later.
        rig.render(blocks = 200, level = probe, reverbSend = probe)

        val last = rig.block(level = probe, reverbSend = probe)

        rig.cylinder.requestChain("dry")

        // The fade ends somewhere in here: if the outgoing chain's OUTPUT were ramped to zero and
        // its tail then re-added at full weight for the drain, the seam would be a step the size
        // of the whole tail.
        val across = last.after(rig.render(blocks = fadeBlocks + 12, level = probe, reverbSend = probe))

        rig.cylinder.isDraining shouldBe true
        maxStep(across) shouldBeLessThan clickThreshold
    }

    // ── The send path ────────────────────────────────────────────────────────────────────────────

    "both chains' wet fades with their dry: the returns are not doubled, and the handover holds" {
        val rig = Rig()
        // Both chains carry a room AND an echo, with the same room size and the same echo time, so
        // the two returns are as correlated as they can be: the case where running both at full
        // level would sum to +6 dB.
        // The arriving chain's echo is deliberately LONGER than the window this row measures: a
        // delay whose ring is still empty emits its first echo as a step when it is fed a
        // constant, which is the DC probe's artifact and not the swap's (it happens to any delay
        // that activates on a constant, crossfade or not).
        rig.registry.register("wet", wetChain(time = 0.25, size = 6.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(amount = 0.5, size = 0.6),
            delay = Voice.Delay(amount = 0.5, time = 0.02, feedback = 0.5),
        )

        // Long enough for the outgoing chain's room and echo to reach their steady level.
        rig.render(blocks = 400, level = probe, reverbSend = send, delaySend = send)
        val before = peakOf(rig.block(level = probe, reverbSend = send, delaySend = send))

        rig.cylinder.requestChain("wet")

        // The ramp, the handover, and the first blocks of the ring-out in one pass.
        val across = rig.render(blocks = fadeBlocks + 8, level = probe, reverbSend = send, delaySend = send)
        val during = peakOf(across.copyOfRange(0, fadeBlocks * blockFrames))

        rig.cylinder.isDraining shouldBe true

        // The other endpoint is measured past the ring-out: the drained tail is legitimate output,
        // but counting it would let the very bug this row is about inflate its own reference.
        var blocks = 0

        while (rig.cylinder.isDraining && blocks < 20000) {
            rig.block(level = probe, reverbSend = send, delaySend = send)
            blocks++
        }

        val after = rig.peakOver(blocks = 600, level = probe, reverbSend = send, delaySend = send)

        withClue("the swap is a swap: both endpoints are wet and audible") {
            before shouldBeGreaterThan probe
            after shouldBeGreaterThan probe
        }

        // What the ramp on the send path buys, in one number. The two chains' DRY paths are
        // amplitude-complementary, so anything above the louder endpoint is the two WET paths
        // overlapping: the room the old chain has already stored (which the fade must not scale,
        // that is what the drain is for) plus the echo the new one builds inside the window. That
        // overlap is 17 % here. With the sends unramped the old chain is FED at full level for the
        // whole fade instead, and the same measurement reads 32 %.
        val bound = (if (before > after) before else after) * 1.25

        withClue("the wet is not doubled mid-fade (during=$during before=$before after=$after)") {
            during shouldBeLessThan bound
        }

        // No step measure here: the ARRIVING room starts from an empty network, and a room fed a
        // constant emits its first reflections as steps, whatever caused it to start. That is the
        // DC probe's artifact, not the swap's; the handover's continuity is the row above, whose
        // arriving chain has no wet of its own.
    }

    // ── The duck ─────────────────────────────────────────────────────────────────────────────────

    "duck to duck: the envelope carries across the swap, under the ARRIVING chain's params" {
        val rig = Rig()
        // Different orbit, different depth, different attack on the two sides: what carries is the
        // envelope, what governs it is the arriving chain.
        rig.registry.register("ducked", duckChain(depth = 0.9, attack = 0.05, orbit = 5.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(cylinderId = 3, attackSeconds = 0.2, depth = 0.4),
        )

        // The sidechain pumps, then stops: the envelope is now recovering, slowly, which is the
        // state a fresh instance could not possibly guess.
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)
        rig.render(blocks = 4, level = probe)

        val last = rig.block(level = probe)

        rig.cylinder.requestChain("ducked")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe))

        withClue("a fresh envelope would start at gain 1.0 and step the whole reduction back in") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and the orbit is still ducked, so the row measured a real reduction") {
            peakOf(across) shouldBeLessThan probe
        }

        withClue("the carried envelope is governed by the chain that arrived, not the one that left") {
            val duck = rig.cylinder.duck.shouldNotBeNull()

            duck.duckCylinderId shouldBe 5
            duck.ducking.shouldNotBeNull().depth shouldBe 0.9
        }
    }

    "a duck named by the owner's SLOT STATE carries across a swap" {
        // The Katalyst 5a shape: the chain declares `duck.*` slots and the pattern's `.duck(...)`
        // door fills them through `katalystParams`. The arriving chain therefore knows it ducks
        // only once it has read that state, and `handOverDuck` asks BEFORE any writer runs.
        val rig = Rig()
        val state = duckState(orbit = 5.0, depth = 0.9)

        rig.registry.register("ducked-a", slotDuckChain())
        rig.registry.register("ducked-b", slotDuckChain())
        // katp-ONLY: no `ducking` field at all, so nothing but the chain's own slots can say that
        // this orbit ducks, and `beginFade`'s resolve is the only thing that can know it in time.
        rig.voice = VoiceTestHelpers.createSynthVoice(katalystParams = state)

        rig.cylinder.requestChain("ducked-a")

        // The sidechain pumps, then stops: the reduction is in force and recovering.
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)
        rig.render(blocks = 4, level = probe)

        val last = rig.block(level = probe)

        withClue("the orbit really is pulled down before the swap") {
            peakOf(last) shouldBeLessThan probe
        }

        rig.cylinder.requestChain("ducked-b")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe))

        withClue("asking before the arriving chain has read the state answers 'no duck': the swap") {
            withClue("then ramps the reduction out and drops a fresh one on the orbit a block later") {
                maxStep(across) shouldBeLessThan clickThreshold
            }
        }

        withClue("the envelope really carried: a fresh one would start at gain 1.0") {
            peakOf(across) shouldBeLessThan probe
        }

        withClue("and the arriving chain owns it, configured from the same state") {
            val duck = rig.cylinder.duck.shouldNotBeNull()

            duck.duckCylinderId shouldBe 5
            duck.ducking.shouldNotBeNull().depth shouldBe 0.9
        }
    }

    "the orbit's param state is aged with the lease: a dead owner configures nothing" {
        // The state is the OWNER's, so it dies with the owner (review round 2). A chain arriving
        // while the orbit merely rings out its tail must resolve from what it authored, not from a
        // voice that stopped checking in.
        val rig = Rig()
        rig.registry.register("slot-room", roomSlotChain())
        rig.voice = VoiceTestHelpers.createSynthVoice(
            katalystParams = duckState(orbit = 3.0, depth = 0.9) + mapOf("reverb.size" to 9.0),
        )

        rig.render(blocks = 8, level = probe)

        // The owner stops offering itself; the orbit keeps rendering its tail.
        rig.render(blocks = 3, level = probe, owned = false)

        rig.cylinder.requestChain("slot-room")
        rig.block(level = probe, owned = false)

        withClue("the arriving chain took the authored default, not the dead owner's 9") {
            rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull().size shouldBe
                    Reverb.normalizeSize(3.0)
        }

        withClue("and the dead owner's duck did not arrive with it") {
            rig.cylinder.duck.shouldNotBeNull().ducking.shouldBeNull()
        }
    }

    "a duck named by the owner's SLOT STATE is ramped IN when nothing ducked before" {
        val rig = Rig()

        rig.registry.register("ducked", slotDuckChain())
        // Nothing ducks the orbit before the swap, and the trigger is already sounding.
        rig.voice = VoiceTestHelpers.createSynthVoice(katalystParams = duckState(orbit = 0.0, depth = 0.8))

        rig.render(blocks = 10, level = probe, sidechainLevel = 0.5)

        val last = rig.block(level = probe, sidechainLevel = 0.5)

        withClue("nothing ducks this orbit before the swap") {
            peakOf(last) shouldBeGreaterThan probe * 0.99
        }

        rig.cylinder.requestChain("ducked")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, sidechainLevel = 0.5))

        withClue("`Ducking` pulls down in one sample, so the arriving duck has to be ramped in") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and it really did duck by the end") {
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeLessThan probe * 0.5
        }
    }

    "duck to duck with NO owner alive: the arriving chain's own slots govern the carried envelope" {
        val rig = Rig()
        rig.registry.register("first", duckChain(depth = 0.4, attack = 0.2, orbit = 3.0))
        rig.registry.register("second", duckChain(depth = 0.9, attack = 0.05, orbit = 5.0))
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("first")
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)

        // The trigger stops and the note ends: the envelope is recovering and NO voice offers
        // itself any more, so the arriving chain's writers are the only ones that will ever run.
        rig.block(level = probe, owned = false)
        val last = rig.block(level = probe, owned = false)

        rig.cylinder.requestChain("second")

        // Still unowned, all the way through: `beginFade`'s own `applyParams` is the ONLY writer
        // that will ever touch the arriving chain here.
        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, owned = false))

        withClue("the envelope carried: a fresh one would step the reduction back in") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and the arriving slots reached it, which they cannot after it is handed over") {
            val duck = rig.cylinder.duck.shouldNotBeNull()

            duck.duckCylinderId shouldBe 5
            duck.ducking.shouldNotBeNull().depth shouldBe 0.9
        }
    }

    "a ducked chain to CLASSIC under an owner that does not duck: the reduction ramps out" {
        val rig = Rig()
        // The classic chain declares a Duck stage on every cylinder, and its writer CLEARS it when
        // the owner carries none: handing it the envelope would release the whole reduction on the
        // next block.
        rig.registry.register("ducked", duckChain(depth = 0.8, attack = 0.05))
        rig.registry.register("plain", KatalystDsl.classic)
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)
        val last = rig.block(level = probe, sidechainLevel = 0.5)

        withClue("the orbit is pulled down before the swap") {
            peakOf(last) shouldBeLessThan probe * 0.5
        }

        rig.cylinder.requestChain("plain")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, sidechainLevel = 0.5))

        withClue("no step: the reduction is crossfaded out over the ramp") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and it is gone by the end of the fade") {
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeGreaterThan probe * 0.99
        }
    }

    "a ducked chain to a chain whose duck names no orbit: the reduction ramps out too" {
        val rig = Rig()
        rig.registry.register("ducked", duckChain(depth = 0.8, attack = 0.05))
        rig.registry.register("nameless", duckChainWithNoOrbit())
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)
        val last = rig.block(level = probe, sidechainLevel = 0.5)

        rig.cylinder.requestChain("nameless")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, sidechainLevel = 0.5))

        withClue("a declared duck that resolves to nothing is not a duck") {
            maxStep(across) shouldBeLessThan clickThreshold
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeGreaterThan probe * 0.99
        }
    }

    "duck to no duck: the reduction is ramped out, not released in one sample" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        // A fast duck, so the envelope's own release can follow the depth ramp down.
        rig.voice = VoiceTestHelpers.createSynthVoice(
            ducking = Voice.Ducking(cylinderId = 0, attackSeconds = 0.005, depth = 0.8),
        )

        // A steady trigger: the reduction is in force and stays there until the depth is ramped.
        rig.render(blocks = 40, level = probe, sidechainLevel = 0.5)

        val last = rig.block(level = probe, sidechainLevel = 0.5)

        withClue("the orbit really is pulled down before the swap") {
            peakOf(last) shouldBeLessThan probe * 0.5
        }

        rig.cylinder.requestChain("dry")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, sidechainLevel = 0.5))

        withClue("dropping the duck at the swap would step the whole reduction") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and by the end of the fade the orbit is at full level again") {
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeGreaterThan probe * 0.99
        }
    }

    "no duck to duck: the arriving duck is ramped in, not dropped on the orbit" {
        val rig = Rig()
        rig.registry.register("ducked", duckChain(depth = 0.8, attack = 0.05))
        // No duck on the way out, and the trigger is ALREADY sounding: `Ducking`'s duck-down is
        // instantaneous by design, so a duck applied at full from the first block would pull the
        // whole orbit down in one sample.
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.render(blocks = 10, level = probe, sidechainLevel = 0.5)
        val last = rig.block(level = probe, sidechainLevel = 0.5)

        withClue("nothing ducks this orbit before the swap") {
            peakOf(last) shouldBeGreaterThan probe * 0.99
        }

        rig.cylinder.requestChain("ducked")

        val across = last.after(rig.render(blocks = fadeBlocks + 4, level = probe, sidechainLevel = 0.5))

        withClue("the reduction arrives over the ramp, not in one sample") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and by the end of the fade the orbit is fully ducked") {
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeLessThan probe * 0.5
        }
    }

    "a katp-only ducking owner whose first claim lands in the swap block takes the envelope over" {
        val rig = Rig()
        // The two sides listen to DIFFERENT orbits, so the row can say which duck ended up in
        // charge; the rig hands the same sidechain to whichever one runs.
        rig.registry.register("ducked", duckChain(depth = 0.8, attack = 0.05, orbit = 7.0))
        rig.registry.register("plain", KatalystDsl.classic)
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)

        // Two blocks with nobody claiming, so the swap is decided with no owner in sight.
        rig.block(level = probe, sidechainLevel = 0.5, owned = false)
        val last = rig.block(level = probe, sidechainLevel = 0.5, owned = false)

        rig.cylinder.requestChain("plain")

        // And NOW a ducking voice claims, in the swap's own block: the arriving chain's duck will be
        // configured after all, so the reduction has to carry instead of ramping out and dropping.
        //
        // katp-ONLY, and that is the point: a chain arriving by name is slot-driven
        // (`Katalyst.classic()` included, 2026-09-18), so its duck reads `duck.orbit` and
        // `duck.depth` and the voice's own `ducking` field says nothing. The correction used to be
        // gated on that field, which skipped exactly this voice.
        rig.voice = VoiceTestHelpers.createSynthVoice(
            katalystParams = duckState(orbit = 0.0, depth = 0.8),
        )

        val across = last.after(rig.render(blocks = fadeBlocks + 6, level = probe, sidechainLevel = 0.5))

        withClue("no step: not at the swap, and not when the ramp would have ended") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and the orbit is still ducked, by the ARRIVING chain's duck") {
            rig.cylinder.duck.shouldNotBeNull().duckCylinderId shouldBe 0
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeLessThan probe * 0.5
        }
    }

    "a ducking owner claiming LATER in the fade keeps ramping out instead" {
        val rig = Rig()
        rig.registry.register("ducked", duckChain(depth = 0.8, attack = 0.05, orbit = 7.0))
        rig.registry.register("plain", KatalystDsl.classic)
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 20, level = probe, sidechainLevel = 0.5)

        rig.block(level = probe, sidechainLevel = 0.5, owned = false)
        val last = rig.block(level = probe, sidechainLevel = 0.5, owned = false)

        rig.cylinder.requestChain("plain")

        // Three blocks of ramp with nobody claiming: the orbit's gain is already part way out, so
        // handing the envelope over now would jump it back to the full reduction.
        val ramped = rig.render(blocks = 3, level = probe, sidechainLevel = 0.5, owned = false)

        rig.voice = VoiceTestHelpers.createSynthVoice(
            katalystParams = duckState(orbit = 0.0, depth = 0.8),
        )

        // Render while the duck that is fading OUT still governs the orbit, which is exactly the
        // ramp: orbit 7 is its sidechain, the arriving chain's duck listens to 0.
        var across = last.after(ramped)
        var ramping = 0

        while (rig.cylinder.duck?.duckCylinderId == 7 && ramping < 64) {
            across = across.after(rig.block(level = probe, sidechainLevel = 0.5))
            ramping++
        }

        withClue("the ramp finishes what it started, whoever claims halfway through") {
            maxStep(across) shouldBeLessThan clickThreshold
        }

        withClue("and it really ran to its end rather than being cut short") {
            ramping shouldBeGreaterThan 8
        }

        // Past the ramp, the arriving chain's own duck engages on its fresh envelope. That
        // duck-down is `Ducking`'s documented behaviour for a new owner, not the swap's step, so it
        // is asserted here rather than measured above.
        withClue("and the new owner's duck is live once the ramp is done") {
            peakOf(rig.block(level = probe, sidechainLevel = 0.5)) shouldBeLessThan probe * 0.5
        }
    }

    // ── Warm-up ──────────────────────────────────────────────────────────────────────────────────

    "the incoming chain warms up on real input for the whole fade" {
        val rig = Rig()
        rig.registry.register("room", roomChain(6.0))
        // No room on the way out, so the only network in play is the incoming chain's.
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.render(blocks = 10, level = probe, reverbSend = probe)
        rig.cylinder.requestChain("room")

        // The stage exists from the moment the chain is installed; its NETWORK is rented by the
        // stage's writer, which the owner runs on the fade's first block.
        val incoming = rig.cylinder.reverb.shouldNotBeNull()

        rig.block(level = probe, reverbSend = probe)

        withClue("the incoming chain's writer ran, so its network is rented") {
            incoming.reverb.shouldNotBeNull()
        }

        // Halfway through the ramp, while the incoming chain still carries less than half the
        // weight: its combs must already hold energy, which they only can if they were fed.
        rig.render(blocks = fadeBlocks / 2 - 1, level = probe, reverbSend = probe)
        rig.cylinder.isFading shouldBe true
        val atHalf = incoming.reverb.shouldNotBeNull().combPeakAbs()

        atHalf shouldBeGreaterThan 0.0

        rig.render(blocks = fadeBlocks / 2, level = probe, reverbSend = probe)
        val atEnd = incoming.reverb.shouldNotBeNull().combPeakAbs()

        withClue("and it kept charging for the rest of the fade") {
            atEnd shouldBeGreaterThan atHalf
        }
    }

    "a chain faded in with no owner alive is configured from its own slots" {
        val rig = Rig()

        // The orbit sounds, and the chain is asked for before its registration arrives.
        rig.render(blocks = 4, level = probe, reverbSend = probe)
        rig.cylinder.requestChain("room")
        rig.registry.register("room", roomChain(6.0))

        // The note has ended and the orbit is still audible, so no voice offers itself: the
        // pending poll starts the fade, and nothing will claim the lease to write the chain.
        rig.block(level = probe, reverbSend = probe, owned = false)

        rig.cylinder.isFading shouldBe true

        withClue("a declared chain resolves its own slots when it enters service, owner or not") {
            rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull()
        }
    }

    "a declared chain installed on a SILENT orbit rents nothing until a voice sounds" {
        val rig = Rig()
        rig.registry.register("room", roomChain(6.0))

        rig.cylinder.requestChain("room")

        val room = rig.cylinder.reverb.shouldNotBeNull()

        withClue("the orbit may never sound: the warehouse's laziness outranks an early configure") {
            room.reverb.shouldBeNull()
        }

        rig.block(level = probe, reverbSend = probe)

        withClue("the first voice's own writers configure it, before anything renders") {
            room.reverb.shouldNotBeNull()
        }
    }

    "a fade whose duck pass never runs leaves nothing behind" {
        val rig = Rig()
        // A room AND a duck on the way out, so the chain is still DRAINING when the row looks: a
        // chain that retires at the end of the fade would drop its duck on that path instead.
        rig.registry.register("ducked", KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = IgnitorDsl.Constant(0.5), size = IgnitorDsl.Constant(6.0)),
            KatalystStageDsl.Duck(
                orbit = IgnitorDsl.Constant(0.0),
                depth = IgnitorDsl.Constant(0.8),
                attack = IgnitorDsl.Constant(0.05),
            ),
        ))
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice()

        rig.cylinder.requestChain("ducked")
        rig.render(blocks = 200, level = probe, reverbSend = probe, sidechainLevel = 0.5)

        // The sidechain orbit disappears in the same block the swap is asked for, so the pass that
        // normally ends the duck's crossfade never runs.
        rig.cylinder.requestChain("dry")
        rig.render(blocks = fadeBlocks + 4, level = probe, reverbSend = probe, duckPass = false)

        rig.cylinder.isDraining shouldBe true

        withClue("the duck of a chain that has left service must not still govern the orbit") {
            rig.cylinder.duck.shouldBeNull()
        }
    }

    // ── Drain ────────────────────────────────────────────────────────────────────────────────────

    "the outgoing chain's tail is drained, not cut, and its unit goes back when it is done" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.5, size = 0.6))

        rig.render(blocks = 200, level = probe, reverbSend = probe)
        rig.cylinder.requestChain("dry")

        withClue("the room is on the way out, so the shelf has nothing while it still rings") {
            rig.reverbs.idleCount shouldBe 0
        }

        // Everything goes silent at the swap: the incoming chain is a pass-through on silence, so
        // whatever is left in the orbit's mix is the outgoing room and nothing else.
        rig.render(blocks = fadeBlocks)

        rig.cylinder.isDraining shouldBe true

        val firstDrained = peakOf(rig.block())

        withClue("the tail survived the fade") {
            firstDrained shouldBeGreaterThan 0.001
        }

        var blocks = 0
        var last = firstDrained

        while (rig.cylinder.isDraining && blocks < 20000) {
            last = peakOf(rig.block())
            blocks++
        }

        withClue("the drain terminates, on the effect's own closed-form countdown") {
            rig.cylinder.isDraining shouldBe false
        }

        withClue("and it lasted a real tail, not a block or two") {
            blocks shouldBeGreaterThan 100
        }

        withClue("the tail decayed on its way out") {
            last shouldBeLessThan firstDrained
        }

        withClue("the retired chain handed its network back to the shelf") {
            rig.reverbs.idleCount shouldBe 1
        }
    }

    // ── Owner writers ────────────────────────────────────────────────────────────────────────────

    "the owner configures BOTH chains while they fade, and stops at the drain" {
        val rig = Rig()
        rig.registry.register("room", roomChain(6.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(amount = 0.5, time = 0.2, feedback = 0.5),
        )

        rig.render(blocks = 4, level = probe, delaySend = probe)

        // The stage of the chain that is about to LEAVE: the cylinder's own accessors follow the
        // chain in service, so the handle has to be taken while it still is one.
        val leaving = rig.cylinder.delay.shouldNotBeNull()

        leaving.delayLine.shouldNotBeNull().time shouldBe 0.2

        rig.cylinder.requestChain("room")
        rig.cylinder.isFading shouldBe true

        // A new owner takes the orbit mid-fade (the previous one missed a block and lapsed).
        rig.voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(amount = 0.5, time = 0.35, feedback = 0.5),
        )
        rig.skipBlock()
        rig.block(level = probe, delaySend = probe)

        withClue("the chain that is still audible is still being configured") {
            leaving.delayLine.shouldNotBeNull().time shouldBe 0.35
        }

        withClue("and the incoming chain's own writer ran, so its room is rented") {
            rig.cylinder.reverb.shouldNotBeNull().reverb.shouldNotBeNull()
        }

        rig.render(blocks = fadeBlocks, level = probe, delaySend = probe)
        rig.cylinder.isDraining shouldBe true

        // A third owner, while the outgoing chain only rings out: a live config would take its
        // delay back out of the Draining state and point it at the live sends again.
        rig.voice = VoiceTestHelpers.createSynthVoice(
            delay = Voice.Delay(amount = 0.5, time = 0.05, feedback = 0.5),
        )
        rig.skipBlock()
        rig.block(level = probe, delaySend = probe)

        withClue("a draining chain takes no more orders") {
            leaving.delayLine.shouldNotBeNull().time shouldBe 0.35
        }
    }

    // ── Retarget ─────────────────────────────────────────────────────────────────────────────────

    "a request mid-fade is queued, and the LAST one is what lands" {
        val rig = Rig()
        rig.registry.register("first", dryChain(1.0))
        rig.registry.register("second", echoChain(0.25))
        rig.registry.register("third", roomChain(4.0))

        rig.render(blocks = 4, level = probe)

        rig.cylinder.requestChain("first")
        rig.cylinder.isFading shouldBe true

        rig.cylinder.requestChain("second")
        rig.cylinder.requestChain("third")

        withClue("neither queued chain cut the running fade") {
            rig.cylinder.isFading shouldBe true
            rig.cylinder.delay.shouldBeNull()
            rig.cylinder.reverb.shouldBeNull()
        }

        // The fade runs out. The outgoing classic chain has nothing charged, so it retires at once
        // and the queued name lands on the next polled block.
        rig.render(blocks = fadeBlocks + 2, level = probe)

        withClue("the third request overwrote the second: 'second' never lands") {
            rig.cylinder.reverb.shouldNotBeNull()
            rig.cylinder.delay.shouldBeNull()
        }

        withClue("and it landed through a fade of its own, not a cut") {
            rig.cylinder.isFading shouldBe true
        }
    }

    "a request mid-fade builds nothing: the cache is not even reached" {
        val rig = Rig()
        rig.registry.register("first", dryChain(1.0))
        rig.registry.register("second", echoChain(0.25))
        rig.registry.register("third", roomChain(4.0))

        rig.render(blocks = 4, level = probe)
        rig.cylinder.requestChain("first")

        rig.cylinder.cachedChainCount shouldBe 1

        rig.cylinder.requestChain("second")
        rig.cylinder.requestChain("third")

        withClue("a queued name is a string, not a built chain: nothing is built on the audio thread") {
            rig.cylinder.cachedChainCount shouldBe 1
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────────────────────────

    "tryDeactivate refuses while a chain fades or drains" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.5, size = 0.6))

        rig.render(blocks = 200, level = probe, reverbSend = probe)
        rig.cylinder.requestChain("dry")

        // Everything goes quiet at the swap, which is exactly when the cleanup would like to
        // deactivate the orbit. With a fade running, that would drop the outgoing chain at
        // whatever weight it had.
        rig.block()
        rig.cylinder.mixBuffer.clear()
        rig.cylinder.tryDeactivate()

        withClue("a running fade keeps the orbit alive") {
            rig.cylinder.isFading shouldBe true
            rig.cylinder.isActive shouldBe true
        }

        rig.render(blocks = fadeBlocks)
        rig.cylinder.isDraining shouldBe true

        rig.cylinder.mixBuffer.clear()
        rig.cylinder.tryDeactivate()

        withClue("and so does a draining one") {
            rig.cylinder.isDraining shouldBe true
            rig.cylinder.isActive shouldBe true
        }

        var blocks = 0

        while (rig.cylinder.isDraining && blocks < 20000) {
            rig.block()
            blocks++
        }

        rig.cylinder.mixBuffer.clear()
        rig.cylinder.tryDeactivate()

        withClue("once nothing rings, the orbit may go") {
            rig.cylinder.isActive shouldBe false
        }
    }

    "retire ends a running fade and strands no unit" {
        val rig = Rig()
        rig.registry.register("room", roomChain(6.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(
            reverb = Voice.Reverb(amount = 0.5, size = 0.6),
            delay = Voice.Delay(amount = 0.5, time = 0.2, feedback = 0.5),
        )

        rig.render(blocks = 20, level = probe, reverbSend = probe, delaySend = probe)
        rig.cylinder.requestChain("room")
        rig.render(blocks = 4, level = probe, reverbSend = probe, delaySend = probe)

        withClue("two chains are live, and both of their rooms are rented") {
            rig.cylinder.isFading shouldBe true
            rig.reverbs.idleCount shouldBe 0
        }

        rig.cylinder.retire()

        withClue("the fade is over and the orbit is back to the chain it was born with") {
            rig.cylinder.isFading shouldBe false
            rig.cylinder.isDraining shouldBe false
            rig.cylinder.runsClassic() shouldBe true
            rig.cylinder.isActive shouldBe false
        }

        // `startNewLife`'s `outgoing?.retire()` is a guardrail, NOT something this row covers: the
        // chain fading out is always either the classic one or a cache entry, and the lines around
        // it retire both, so no mutation of that call can turn this red. It stays because the
        // failure it guards against (a rented ring inside a shelved cylinder) is silent.
        withClue("both rooms and the ring are back on their shelves") {
            rig.reverbs.idleCount shouldBe 2
            rig.rings.shelfCount shouldBe 1
        }
    }

    "retire ends a running drain and strands no unit" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.5, size = 0.6))

        rig.render(blocks = 200, level = probe, reverbSend = probe)
        rig.cylinder.requestChain("dry")
        rig.render(blocks = fadeBlocks)

        rig.cylinder.isDraining shouldBe true

        rig.cylinder.retire()

        rig.cylinder.isDraining shouldBe false
        rig.cylinder.runsClassic() shouldBe true

        withClue("the draining room went back rather than staying inside a shelved cylinder") {
            rig.reverbs.idleCount shouldBe 1
        }
    }

    "deniedRents sums both chains while both are live" {
        // A shelf that serves no room at all: each chain that asks for one is refused exactly once
        // (the effect latches the refusal, see `KatalystReverbEffect`).
        val rig = Rig(reverbs = ReverbUnits(sampleRate, maxIdle = 0, allocate = { null }))
        rig.registry.register("room", roomChain(6.0))
        rig.voice = VoiceTestHelpers.createSynthVoice(reverb = Voice.Reverb(amount = 0.5, size = 0.6))

        rig.block(level = probe, reverbSend = probe)

        rig.cylinder.deniedRents shouldBe 1

        rig.cylinder.requestChain("room")
        rig.block(level = probe, reverbSend = probe)

        withClue("the incoming chain's refusal is added to the outgoing chain's, not swapped for it") {
            rig.cylinder.isFading shouldBe true
            rig.cylinder.deniedRents shouldBe 2
        }

        // The outgoing chain never got a room, so it has no tail and retires the moment the fade
        // ends. Its count goes with it, which is why the cylinder carries it over.
        rig.render(blocks = fadeBlocks, level = probe, reverbSend = probe)

        withClue("a retired chain's refusals stay on this cylinder's LIFE count") {
            rig.cylinder.isFading shouldBe false
            rig.cylinder.deniedRents shouldBe 2
        }
    }

    "a non-finite sample cannot poison the other chain through the ramp" {
        val rig = Rig()
        rig.registry.register("dry", dryChain(1.0))

        rig.block(level = probe)
        rig.cylinder.requestChain("dry")

        // At the ramp's endpoints one weight is exactly 0.0, and `Inf * 0.0` is NaN: a chain that
        // contributes nothing yet could still inject NaN into the orbit's output, and the master's
        // DC blocker downstream latches one for good.
        rig.cylinder.updateFromVoice(rig.voice, rig.blockStart)
        rig.cylinder.mixBuffer.fill(Double.POSITIVE_INFINITY)
        rig.cylinder.reverbSendBuffer.fill(0.0)
        rig.cylinder.delaySendBuffer.fill(0.0)
        rig.cylinder.processEffects()

        withClue("the raw engine may be loud, but it may not be NaN") {
            rig.cylinder.mixBuffer.left.all { it.isFinite() } shouldBe true
            rig.cylinder.mixBuffer.right.all { it.isFinite() } shouldBe true
        }
    }
})
