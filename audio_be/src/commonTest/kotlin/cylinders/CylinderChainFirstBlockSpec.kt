/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.katalyst.KatalystChainBuilder
import io.peekandpoke.klang.audio_be.katalyst.KatalystContext
import io.peekandpoke.klang.audio_be.katalyst.KatalystRegistry
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.Kat
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.kat
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.random.Random

/**
 * A chain's stages are in place from the cylinder's FIRST processed block, whoever arrives first (KLANG-00468, review
 * round 1, the maintainer's "yes 1 this needs to be fixed" of 2026-11-19).
 *
 * The bug this guards: a stage is switched on only by its writer, and a cylinder applies its chain only on an owner's
 * commit. A first voice that does not claim the cylinder (past its gate when it arrives, or fading) left the declared
 * chain running with its stages off, and the owner after it faded them in from dry. `KatalystChain.process` now applies
 * a chain that nothing applied since it was built or reset, from no state, before its first block (and an idle install
 * resets the incoming chain, since a cached one comes back retired).
 *
 * The cylinder is driven the way `Cylinders.processAndMix` drives it, with real voices deciding whether they claim
 * (`Voice.claimsCylinder`, each voice rendered first so its state is the production one); the mix the voices would
 * have summed is a known input written into the cylinder's buffer, as the swap specs do. The oracle is the same chain
 * built the plain way (`KatalystChainBuilder`), configured from no state before its first block and run over the same
 * input: the cylinder must give the same samples, bit for bit. The no-change rows hold a claiming first voice to values
 * written here.
 */
class CylinderChainFirstBlockSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    /** The maintainer's coming use: an amp of fixed stages on a cylinder, the classic stages after it. */
    val amp = Kat {
        it.highpass(freq = 120.kat).distort(amount = 0.35.kat, shape = "tube")
            .lowpass(freq = 5000.kat, q = 0.707.kat) { f -> f.passes(3.0) }
            .classic()
    }

    /** A cabinet of numbers only, a second chain for the install rows. */
    val cabinet = Kat { it.eq { e -> e.band(freq = 900.kat, db = 6.kat) }.lowpass(freq = 2500.kat).highpass(freq = 200.kat) }

    /** The known mix of block [block]: seeded noise, a seed per channel and block, so left and right differ. */
    fun mixOf(block: Int): Pair<DoubleArray, DoubleArray> {
        val l = Random(1000 + block)
        val r = Random(5000 + block)

        return DoubleArray(blockFrames) { 0.3 * (2.0 * l.nextDouble() - 1.0) } to
                DoubleArray(blockFrames) { 0.3 * (2.0 * r.nextDouble() - 1.0) }
    }

    /** [dsl] the plain way: built, configured from no state before its first block, run over blocks [from] until [until]. */
    fun plain(dsl: KatalystDsl, from: Int, until: Int): Pair<DoubleArray, DoubleArray> {
        val chain = KatalystChainBuilder.build(
            dsl = dsl,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val left = DoubleArray((until - from) * blockFrames)
        val right = DoubleArray((until - from) * blockFrames)

        chain.applyParams(null)

        for (block in from until until) {
            val (l, r) = mixOf(block)
            val at = (block - from) * blockFrames

            l.copyInto(ctx.mixBuffer.left)
            r.copyInto(ctx.mixBuffer.right)
            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = left, destinationOffset = at)
            ctx.mixBuffer.right.copyInto(destination = right, destinationOffset = at)
        }

        return left to right
    }

    /**
     * One cylinder, its registry, and the voices' own render context (a voice renders before it decides to claim).
     * [adopted]: the cylinder as production rents it from the shelf (`CylinderUnits.rent` -> `adopt`), whose born-with
     * chain comes back RETIRED and so does not configure itself on its first block, where a cylinder built directly does.
     */
    class Rig(sampleRate: Int, val blockFrames: Int, adopted: Boolean = false) {
        val registry = KatalystRegistry()
        val cylinder = Cylinder(
            id = 0,
            blockFrames = blockFrames,
            sampleRate = sampleRate,
            silentBlocksBeforeTailCheck = 0,
            katalysts = registry,
        ).also {
            if (adopted) {
                it.adopt(id = 0, silentBlocksBeforeTailCheck = 0, katalysts = registry)
            }
        }
        val voiceCtx = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val left = ArrayList<Double>()
        val right = ArrayList<Double>()

        /**
         * Block [block] as `Cylinders.processAndMix` runs it: each of [voices] renders and then claims the cylinder or
         * only checks in, as `mixIntoCylinder` decides; the block's mix is [mix]; then the commit, the pending poll and
         * the chain. The cylinder's output is appended to [left] and [right].
         */
        fun block(block: Int, voices: List<Voice>, mix: Pair<DoubleArray, DoubleArray>) {
            val blockStart = (block * blockFrames).toDouble()

            voiceCtx.blockStart = blockStart

            for (voice in voices) {
                voice.render(voiceCtx)

                if (voice.claimsCylinder(blockStart)) {
                    cylinder.offer(voice, blockStart)
                } else {
                    cylinder.checkIn(blockStart)
                }
            }

            mix.first.copyInto(cylinder.mixBuffer.left)
            mix.second.copyInto(cylinder.mixBuffer.right)
            cylinder.commitOwner()
            cylinder.pollPendingChain()
            cylinder.processEffects()
            cylinder.mixBuffer.left.forEach { left.add(it) }
            cylinder.mixBuffer.right.forEach { right.add(it) }
        }

        /** The cylinder goes quiet with no voice on it and the cleanup reaches it until it deactivates (and resets). */
        fun deactivate(atBlock: Int) {
            var block = atBlock

            while (cylinder.isActive && block < atBlock + 2000) {
                cylinder.mixBuffer.clear()
                cylinder.commitOwner()
                cylinder.processEffects()
                cylinder.tryDeactivate(blockStart = (block * blockFrames).toDouble())
                block++
            }

            withClue("the cylinder deactivated") { cylinder.isActive shouldBe false }
        }

        fun output(from: Int): Pair<DoubleArray, DoubleArray> =
            left.drop(from * blockFrames).toDoubleArray() to right.drop(from * blockFrames).toDoubleArray()
    }

    /** A voice whose onset and gate end lie before [block]: it renders in its release and never claims. */
    fun lateVoice(block: Int): Voice = VoiceTestHelpers.createSynthVoice(
        startFrame = block * blockFrames - 50.0,
        gateEndFrame = block * blockFrames - 10.0,
        endFrame = 1e9,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
    )

    /** A voice that starts inside [block] with its gate open: it claims the cylinder. */
    fun claimingVoice(block: Int, katalystParams: KlangMap<Double>? = null): Voice = VoiceTestHelpers.createSynthVoice(
        startFrame = block * blockFrames + 5.0,
        gateEndFrame = 1e9,
        endFrame = 1e9,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        katalystParams = katalystParams,
    )

    /** The same double, bit for bit: a signed zero told apart, NaN for NaN. */
    fun sameBits(a: Double, b: Double): Boolean =
        if (a.isNaN() || b.isNaN()) a.isNaN() && b.isNaN() else a == b && 1.0 / a == 1.0 / b

    fun firstDifference(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { !sameBits(a[it], b[it]) } ?: -1

    fun sameAs(clue: String, got: Pair<DoubleArray, DoubleArray>, want: Pair<DoubleArray, DoubleArray>) {
        withClue("$clue: frames") { got.first.size shouldBe want.first.size }

        val left = firstDifference(got.first, want.first)

        withClue("$clue, left: first differing frame (${if (left >= 0) "${got.first[left]} vs ${want.first[left]}" else ""})") {
            left shouldBe -1
        }

        withClue("$clue, right: first differing frame") { firstDifference(got.second, want.second) shouldBe -1 }
    }

    // ── Every way a chain enters service, with a first voice that does not claim ────────────────────────────────────

    "an idle install, then a late first voice that never claims: the chain runs from the cylinder's first block" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("amp", amp)
        rig.cylinder.requestChain("amp")

        val late = lateVoice(block = 0)

        for (block in 0 until 30) {
            rig.block(block = block, voices = listOf(late), mix = mixOf(block))
        }

        sameAs(
            clue = "first block: late voice",
            got = rig.output(from = 0),
            want = plain(dsl = rig.registry.find("amp")!!, from = 0, until = 30),
        )
    }

    "a late first voice, then a claiming one: no fade at the claim, the same samples as the steady chain" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("amp", amp)
        rig.cylinder.requestChain("amp")

        val late = lateVoice(block = 0)
        val claiming = claimingVoice(block = 10)

        for (block in 0 until 40) {
            rig.block(block = block, voices = if (block < 10) listOf(late) else listOf(late, claiming), mix = mixOf(block))
        }

        withClue("the second voice claimed") { claiming.claimsCylinder(10.0 * blockFrames) shouldBe true }
        sameAs(
            clue = "first block: claim at block 10",
            got = rig.output(from = 0),
            want = plain(dsl = rig.registry.find("amp")!!, from = 0, until = 40),
        )
    }

    "a fading first voice that never claims: the chain runs from the cylinder's first block" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("cab", cabinet)
        rig.cylinder.requestChain("cab")

        // Sounding, then cut before the cylinder's first block: it renders Fading and only checks in.
        val fading = claimingVoice(block = 0)

        rig.voiceCtx.blockStart = 0.0
        fading.render(rig.voiceCtx)
        fading.cutOff(fadeStartFrame = blockFrames.toDouble())

        for (block in 1 until 20) {
            rig.block(block = block, voices = listOf(fading), mix = mixOf(block))
        }

        withClue("the voice was fading") { (fading.state is Voice.State.Fading || fading.state is Voice.State.Done) shouldBe true }
        sameAs(
            clue = "first block: fading voice",
            got = rig.output(from = 0),
            want = plain(dsl = rig.registry.find("cab")!!, from = 1, until = 20),
        )
    }

    "after a deactivation and its reset, a late first voice: the chain runs from the first block of the new life" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("amp", amp)
        rig.cylinder.requestChain("amp")

        val first = claimingVoice(block = 0)

        for (block in 0 until 5) {
            rig.block(block = block, voices = listOf(first), mix = mixOf(block))
        }

        rig.deactivate(atBlock = 5)

        val heard = rig.left.size / blockFrames
        val late = lateVoice(block = 3000)

        for (block in 3000 until 3030) {
            rig.block(block = block, voices = listOf(late), mix = mixOf(block))
        }

        sameAs(
            clue = "first block: after the reset",
            got = rig.output(from = heard),
            want = plain(dsl = rig.registry.find("amp")!!, from = 3000, until = 3030),
        )
    }

    "an idle install of a new chain, and of a chain back from the cache, each under a late first voice" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("amp", amp)
        rig.registry.register("cab", cabinet)

        // The amp serves a claiming voice, then the cylinder goes quiet.
        rig.cylinder.requestChain("amp")

        val first = claimingVoice(block = 0)

        for (block in 0 until 5) {
            rig.block(block = block, voices = listOf(first), mix = mixOf(block))
        }

        rig.deactivate(atBlock = 5)

        // A new chain, installed while idle (the amp retires), under a late first voice.
        rig.cylinder.requestChain("cab")

        var heard = rig.left.size / blockFrames

        val lateOne = lateVoice(block = 3000)

        for (block in 3000 until 3020) {
            rig.block(block = block, voices = listOf(lateOne), mix = mixOf(block))
        }

        sameAs(
            clue = "first block: new chain",
            got = rig.output(from = heard),
            want = plain(dsl = rig.registry.find("cab")!!, from = 3000, until = 3020),
        )

        rig.deactivate(atBlock = 3020)

        // The amp again: the cached chain, retired when it left, installed while idle, under a late first voice.
        rig.cylinder.requestChain("amp")
        heard = rig.left.size / blockFrames

        val lateTwo = lateVoice(block = 6000)

        for (block in 6000 until 6020) {
            rig.block(block = block, voices = listOf(lateTwo), mix = mixOf(block))
        }

        sameAs(
            clue = "first block: cached chain",
            got = rig.output(from = heard),
            want = plain(dsl = rig.registry.find("amp")!!, from = 6000, until = 6020),
        )
    }

    // ── No change for a first voice that claims ─────────────────────────────────────────────────────────────────────

    "no change: a claiming first voice on the classic chain sets its fader from the first sample (input times 0.5)" {
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)
        val owner = claimingVoice(block = 0, katalystParams = klangMapOf("gain.gain" to 0.5))

        for (block in 0 until 10) {
            rig.block(block = block, voices = listOf(owner), mix = mixOf(block))
        }

        val (left, right) = rig.output(from = 0)

        for (block in 0 until 10) {
            val (l, r) = mixOf(block)

            for (i in 0 until blockFrames) {
                val at = block * blockFrames + i

                withClue("no change, classic: block $block frame $i") {
                    sameBits(left[at], l[i] * 0.5) shouldBe true
                    sameBits(right[at], r[i] * 0.5) shouldBe true
                }
            }
        }
    }

    "no change: a claiming first voice on a declared chain with slots renders its own values from the first sample" {
        val slotted = Kat {
            it.lowpass(freq = Kat.param("amp.cut", 5000.0)).gain(Kat.param("amp.level", 1.0))
        }
        val params = klangMapOf("amp.cut" to 1200.0, "amp.level" to 0.5)
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("slotted", slotted)
        rig.cylinder.requestChain("slotted")

        val owner = claimingVoice(block = 0, katalystParams = params)

        for (block in 0 until 20) {
            rig.block(block = block, voices = listOf(owner), mix = mixOf(block))
        }

        // The oracle: the owner's numbers as CONSTANTS, configured from no state before the first block. A second
        // configure from no state cannot move it, so a chain that re-applied its defaults over the owner's values every
        // block would differ from it.
        val (left, right) = plain(dsl = Kat { it.lowpass(freq = 1200.kat).gain(0.5.kat) }, from = 0, until = 20)

        sameAs(clue = "no change, slots", got = rig.output(from = 0), want = left to right)
    }

    /** The slots a claim writes into every classic stage: reverb, delay, body, compressor, phaser and the fader. */
    fun classicParams(): KlangMap<Double> = klangMapOf(
        "reverb.wet" to 0.4, "reverb.size" to 5.0,
        "delay.wet" to 0.5, "delay.time" to 0.05, "delay.feedback" to 0.4,
        "body.material" to 1.0, "body.wet" to 0.8,
        "compressor.threshold" to -30.0, "compressor.ratio" to 4.0,
        "phaser.wet" to 0.6, "phaser.rate" to 0.7,
        "gain.gain" to 0.5,
    )

    "no change on the classic chain: a late first voice, then a claim writing its slots, as on an adopted cylinder" {
        // The one state where the first-block configure runs on every song's chain. A cylinder built directly configures
        // its classic chain from no state on its first block; one rented from the shelf (adopted) does not, its born-with
        // chain is retired. Classic at its defaults switches nothing on, so the two must render the same samples.
        val rigs = listOf(
            Rig(sampleRate = sampleRate, blockFrames = blockFrames),
            Rig(sampleRate = sampleRate, blockFrames = blockFrames, adopted = true),
        )

        for (rig in rigs) {
            val late = lateVoice(block = 0)
            val claim = claimingVoice(block = 10, katalystParams = classicParams())

            for (block in 0 until 60) {
                rig.block(block = block, voices = if (block < 10) listOf(late) else listOf(late, claim), mix = mixOf(block))
            }
        }

        val (built, adopted) = rigs.map { it.output(from = 0) }

        sameAs(clue = "classic: built against adopted", got = built, want = adopted)

        // The first ten blocks are the dry input (nothing switched on), and the claim engages the stages.
        for (block in 0 until 10) {
            val (l, _) = mixOf(block)

            withClue("classic: block $block is the dry input") {
                firstDifference(built.first.copyOfRange(block * blockFrames, (block + 1) * blockFrames), l) shouldBe -1
            }
        }

        withClue("classic: the claim engaged the stages") {
            val (l, _) = mixOf(30)

            (firstDifference(built.first.copyOfRange(30 * blockFrames, 31 * blockFrames), l) >= 0) shouldBe true
        }
    }

    "engagement control: a DECLARED chain renders other samples with the first-block configure than without it" {
        // Without the first-block configure a chain runs its stages off until the claim and then fades them in from
        // dry: written here as the plain chain RETIRED (so it does not configure itself, as before the fix) and
        // configured from the claim on. The cylinder must differ from that, or the classic row above proves nothing.
        val rig = Rig(sampleRate = sampleRate, blockFrames = blockFrames)

        rig.registry.register("cab", cabinet)
        rig.cylinder.requestChain("cab")

        val late = lateVoice(block = 0)
        val claim = claimingVoice(block = 10)

        for (block in 0 until 40) {
            rig.block(block = block, voices = if (block < 10) listOf(late) else listOf(late, claim), mix = mixOf(block))
        }

        val chain = KatalystChainBuilder.build(
            dsl = rig.registry.find("cab")!!,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val without = DoubleArray(40 * blockFrames)

        chain.retire()

        for (block in 0 until 40) {
            val (l, r) = mixOf(block)

            l.copyInto(ctx.mixBuffer.left)
            r.copyInto(ctx.mixBuffer.right)

            if (block >= 10) {
                chain.applyParams(null)
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = without, destinationOffset = block * blockFrames)
        }

        withClue("engagement: declared chain, with and without the first-block configure") {
            (firstDifference(rig.output(from = 0).first, without) >= 0) shouldBe true
        }
    }
})
