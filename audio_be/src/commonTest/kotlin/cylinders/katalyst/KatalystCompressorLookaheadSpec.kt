/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.Crossfade
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.CylinderSwapRig
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.AUTHORED_LIMITER_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.ORBIT_SILENCE_FLOOR
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Katalyst compressor's `lookahead` (phase 3 step 12 C2): a lookahead limiter on ANY chain, and
 * the orbit running late by it.
 *
 * **The oracles are the input's own definition and a BARE [Compressor] driven by hand**, never the
 * effect under test: a stage with a lookahead of D frames is, while it compresses nothing, a pure
 * delay (`out[k] == in[k - D]`), and while it switches it follows the decided law of
 * [KatalystCompressorEffect] with the DELAYED dry as the fade partner,
 * `out = in[k - D] + w * (comp[k] - in[k - D])`, where `comp` is a bare compressor with the same
 * lookahead run over the same blocks.
 */
class KatalystCompressorLookaheadSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** 5 ms at 44.1 kHz: 220 frames, not a multiple of the block, so a ring straddles blocks. */
    val lookahead = 0.005
    val delay = (lookahead * sampleRate).toInt()

    val fadeLen = (sampleRate * KNOB_GLIDE_SECONDS).toInt()

    fun c(value: Double) = IgnitorDsl.Constant(value)

    /** What `k.limiter(lookahead = ...)` appends (the door itself is pinned in klangscript-libs). */
    fun limiterStage(lookaheadSeconds: Double) = KatalystStageDsl.Compressor(
        threshold = c(LIMITER_THRESHOLD_DB),
        ratio = c(LIMITER_RATIO),
        knee = c(LIMITER_KNEE_DB),
        attack = c(AUTHORED_LIMITER_ATTACK_SECONDS),
        release = c(LIMITER_RELEASE_SECONDS),
        lookahead = lookaheadSeconds,
    )

    fun chain(vararg stages: KatalystStageDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    ).also { it.applyParams(null) }

    fun left(k: Int): Double =
        if (k < 0) 0.0 else 0.5 * sin(2.0 * PI * 110.0 * k / sampleRate) + 0.2 * sin(2.0 * PI * 330.0 * k / sampleRate + 0.3)

    fun right(k: Int): Double = if (k < 0) 0.0 else 0.45 * sin(2.0 * PI * 165.0 * k / sampleRate + 1.0)

    class Take(val l: DoubleArray, val r: DoubleArray)

    /** Runs [process] over [blocks] blocks of the input, calling [before] ahead of each block. */
    fun run(blocks: Int, process: (KatalystContext) -> Unit, before: (Int) -> Unit = {}): Take {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val l = DoubleArray(blocks * blockFrames)
        val r = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            before(b)

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = left(b * blockFrames + i)
                ctx.mixBuffer.right[i] = right(b * blockFrames + i)
            }

            process(ctx)
            ctx.mixBuffer.left.copyInto(destination = l, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = r, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return Take(l = l, r = r)
    }

    val settings = Voice.Compressor(
        thresholdDb = -25.0,
        ratio = 4.0,
        kneeDb = 6.0,
        attackSeconds = 0.002,
        releaseSeconds = 0.1,
    )

    /** A bare compressor with the lookahead and [settings], built through its constructor. */
    fun bare() = Compressor(
        sampleRate = sampleRate,
        thresholdDb = settings.thresholdDb,
        ratio = settings.ratio,
        kneeDb = settings.kneeDb,
        attackSeconds = settings.attackSeconds,
        releaseSeconds = settings.releaseSeconds,
        lookaheadSeconds = lookahead,
    )

    /** The bare compressor over [blocks] blocks of the input, in the effect's block grid. */
    fun bareRun(c: Compressor, blocks: Int): Take = run(blocks, process = { ctx ->
        c.process(left = ctx.mixBuffer.left, right = ctx.mixBuffer.right, blockSize = ctx.blockFrames)
    })

    fun weight(from: Double, to: Double, j: Int): Double =
        if (j >= fadeLen) to else to + (from - to) * (fadeLen - j).toDouble() / fadeLen

    // ── The latency, and that it is the stage's in every state ─────────────────────────────────

    "without a lookahead nothing changes: no latency, no tail" {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)

        effect.latencyFrames shouldBe 0
        effect.configure(settings)
        run(4, process = { effect.process(it) })
        effect.hasTail() shouldBe false
    }

    "a limiter with a lookahead on an orbit chain delays the orbit by exactly the lookahead" {
        // Below the ceiling nothing is reduced, so what comes out is the input D frames later,
        // bit for bit, and silence before it.
        val built = chain(limiterStage(lookahead))

        built.compressor.shouldNotBeNull().latencyFrames shouldBe delay

        val out = run(8, process = { built.process(it) })

        for (k in out.l.indices) {
            withClue("sample $k") {
                out.l[k] shouldBe (left(k - delay) plusOrMinus 1e-12)
                out.r[k] shouldBe (right(k - delay) plusOrMinus 1e-12)
            }
        }
    }

    "the lookahead is what holds the ceiling on a transient, on an orbit" {
        // A kick 12 dB over the ceiling. With the lookahead the limiter closes before the hit and
        // nothing passes 1.0; the same limiter without one chases it (the control, so this row
        // cannot pass on a limiter that was never late).
        fun kickOut(lookaheadSeconds: Double): DoubleArray {
            val built = chain(limiterStage(lookaheadSeconds))
            val peak = exp(12.0 / 20.0 * 2.302585092994046)
            val blocks = 60
            val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
            val out = DoubleArray(blocks * blockFrames)
            var phase = 0.0

            for (b in 0 until blocks) {
                for (i in 0 until blockFrames) {
                    val t = (b * blockFrames + i).toDouble() / sampleRate
                    val freq = 55.0 * (1.0 + 3.0 * exp(-t / 0.02))

                    phase += 2.0 * PI * freq / sampleRate

                    val v = peak * exp(-t / 0.030) * sin(phase)

                    ctx.mixBuffer.left[i] = v
                    ctx.mixBuffer.right[i] = v
                }

                built.process(ctx)
                ctx.mixBuffer.left.copyInto(destination = out, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            }

            return out
        }

        kickOut(lookahead).count { abs(it) > 1.0 } shouldBe 0
        kickOut(0.0).count { abs(it) > 1.0 } shouldBeGreaterThan 0
    }

    "switched off, a lookahead stage is still a pure delay: the latency is the stage's" {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)

        effect.configure(null)

        val out = run(6, process = { effect.process(it) })

        for (k in out.l.indices) {
            withClue("sample $k") {
                out.l[k] shouldBe (left(k - delay) plusOrMinus 1e-12)
                out.r[k] shouldBe (right(k - delay) plusOrMinus 1e-12)
            }
        }
    }

    "switching off fades against the DELAYED dry and lands on it, with no gap" {
        // ON from the first block (fresh: at once), OFF at block 10: the fade runs from the
        // compressed mix to the delayed dry over the decided law, and after it the stage is a pure
        // delay. A fade against the undelayed input, or a ring zeroed on entering Off, breaks it.
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)
        val switchBlock = 10
        val blocks = switchBlock + fadeLen / blockFrames + 6
        val out = run(blocks, process = { effect.process(it) }) { b ->
            if (b == 0) {
                effect.configure(settings)
            }

            if (b == switchBlock) {
                effect.configure(null)
            }
        }
        val comp = bareRun(bare(), blocks)
        val s = switchBlock * blockFrames

        for (k in out.l.indices) {
            val w = if (k < s) 1.0 else weight(from = 1.0, to = 0.0, j = k - s)
            val dl = left(k - delay)
            val dr = right(k - delay)

            withClue("sample $k, weight $w") {
                out.l[k] shouldBe ((dl + w * (comp.l[k] - dl)) plusOrMinus 1e-12)
                out.r[k] shouldBe ((dr + w * (comp.r[k] - dr)) plusOrMinus 1e-12)
            }
        }
    }

    "switching on from Off re-seeds the detector from the ring and fades in from the delayed dry" {
        // Off from the start (not fresh after its first block), ON at block 8. The instance ran
        // through Off at its construction knobs; at the ON edge the detector is re-seeded from the
        // ring under the NEW knobs, which makes it a fresh compressor that has heard exactly the
        // ring. So the oracle is a FRESH bare compressor at the new knobs, fed the input from D
        // samples before the switch: from the switch on, its output is what the effect compresses
        // with, and the ceiling argument holds from the new life's first sample.
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)
        val switchBlock = 8
        val blocks = switchBlock + fadeLen / blockFrames + 6

        effect.configure(null)

        val out = run(blocks, process = { effect.process(it) }) { b ->
            if (b == switchBlock) {
                effect.configure(settings)
            }
        }

        val s = switchBlock * blockFrames
        val total = blocks * blockFrames
        val oracle = bare()
        val compL = DoubleArray(total)
        val compR = DoubleArray(total)
        val bl = DoubleArray(blockFrames)
        val br = DoubleArray(blockFrames)
        var m = s - delay

        while (m < total) {
            val len = minOf(blockFrames, total - m)

            for (i in 0 until len) {
                bl[i] = left(m + i)
                br[i] = right(m + i)
            }

            oracle.process(left = bl, right = br, blockSize = len)
            bl.copyInto(destination = compL, destinationOffset = m, startIndex = 0, endIndex = len)
            br.copyInto(destination = compR, destinationOffset = m, startIndex = 0, endIndex = len)
            m += len
        }

        for (k in out.l.indices) {
            val w = if (k < s) 0.0 else weight(from = 0.0, to = 1.0, j = k - s)
            val dl = left(k - delay)
            val dr = right(k - delay)

            withClue("sample $k, weight $w") {
                out.l[k] shouldBe ((dl + w * (compL[k] - dl)) plusOrMinus 1e-12)
                out.r[k] shouldBe ((dr + w * (compR[k] - dr)) plusOrMinus 1e-12)
            }
        }
    }

    "a reset is the clean slate: the next life is a freshly built effect, bit for bit" {
        // Driven hard into reduction first (the input times 4), so the ring, the hold deque, the
        // release gains and the boxes all hold a record; after reset() the next block must be what
        // a new effect makes of it, which is only true if the instance itself was reset.
        val used = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)

        used.configure(settings)
        run(12, process = { ctx ->
            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] *= 4.0
                ctx.mixBuffer.right[i] *= 4.0
            }

            used.process(ctx)
        })
        used.reset()

        val fresh = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)

        val a = run(4, process = { used.process(it) }) { b ->
            if (b == 0) {
                used.configure(settings)
            }
        }
        val b = run(4, process = { fresh.process(it) }) { blk ->
            if (blk == 0) {
                fresh.configure(settings)
            }
        }

        for (k in a.l.indices) {
            withClue("sample $k") {
                a.l[k].toRawBits() shouldBe b.l[k].toRawBits()
                a.r[k].toRawBits() shouldBe b.r[k].toRawBits()
            }
        }
    }

    // ── The tail ────────────────────────────────────────────────────────────────────────────────

    "the ring is a tail until it has played out, and a reset empties it" {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, lookaheadSeconds = lookahead)
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        effect.configure(settings)
        effect.hasTail() shouldBe false

        ctx.mixBuffer.left.fill(0.3)
        ctx.mixBuffer.right.fill(0.3)
        effect.process(ctx)
        effect.hasTail() shouldBe true

        // 220 frames of ring: the first silent block plays 128 of them and the ring still holds
        // audio; the second plays the other 92, and then it holds nothing but silence.
        ctx.mixBuffer.left.fill(0.0)
        ctx.mixBuffer.right.fill(0.0)
        effect.process(ctx)
        effect.hasTail() shouldBe true
        // ...and what it played is the loud block, not silence: frame 35 of it, compressed.
        ctx.mixBuffer.left[blockFrames - 1] shouldBeGreaterThan 0.01

        ctx.mixBuffer.left.fill(0.0)
        ctx.mixBuffer.right.fill(0.0)
        effect.process(ctx)
        effect.hasTail() shouldBe false

        ctx.mixBuffer.left.fill(0.3)
        ctx.mixBuffer.right.fill(0.3)
        effect.process(ctx)
        effect.hasTail() shouldBe true
        effect.reset()
        effect.hasTail() shouldBe false
    }

    /** The rig's left input at its sample [k] (`CylinderSwapRig.block`: a 220 Hz sine at [level]). */
    fun rigInput(rig: CylinderSwapRig, level: Double, k: Int): Double =
        if (k < 0) 0.0 else level * sin(2.0 * PI * 220.0 * k / rig.sampleRate)

    /** The rig's RIGHT input at its sample [k]: a 223 Hz sine, NOT periodic in 50 ms. */
    fun rigInputRight(rig: CylinderSwapRig, level: Double, k: Int): Double =
        if (k < 0) 0.0 else level * sin(2.0 * PI * 223.0 * k / rig.sampleRate)

    /**
     * Renders [blocks] blocks through [rig] at [level] into [out] (left) and [outRight] (right, when
     * given), the sample index being the rig's own.
     */
    fun rigRun(
        rig: CylinderSwapRig,
        blocks: Int,
        level: Double,
        out: DoubleArray,
        fromBlock: Int,
        outRight: DoubleArray? = null,
        sidechainLevel: Double = 0.0,
        duckPass: Boolean = true,
        before: (Int) -> Unit = {},
    ) {
        for (b in 0 until blocks) {
            before(b)
            rig.block(level = level, sidechainLevel = sidechainLevel, duckPass = duckPass)
                .copyInto(out, (fromBlock + b) * rig.blockFrames)
            outRight?.let { rig.cylinder.mixBuffer.right.copyInto(it, (fromBlock + b) * rig.blockFrames) }
        }
    }

    /** The swap's ramp at [d] frames into it, the decided linear law ([Crossfade]). */
    fun ramp(d: Int, rampFrames: Int): Double =
        if (d >= rampFrames) 1.0 else if (d <= 0) 0.0 else d.toDouble() / rampFrames

    /**
     * One swap on a fresh rig: [from] installed at once, [warm] blocks, then [to] requested and
     * [after] blocks. Returns the rig and its left and right outputs.
     */
    fun swapRun(
        from: KatalystDsl,
        to: KatalystDsl,
        level: Double,
        warm: Int,
        after: Int,
    ): Triple<CylinderSwapRig, DoubleArray, DoubleArray> {
        val rig = CylinderSwapRig()
        val l = DoubleArray((warm + after) * rig.blockFrames)
        val r = DoubleArray((warm + after) * rig.blockFrames)

        rig.registry.register("from", from)
        rig.registry.register("to", to)
        rig.cylinder.requestChain("from")
        rigRun(rig, warm, level, l, fromBlock = 0, outRight = r)
        rig.cylinder.requestChain("to")
        rigRun(rig, after, level, l, fromBlock = warm, outRight = r)

        return Triple(rig, l, r)
    }

    val late50 = KatalystDsl.of(limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS))
    val late5 = KatalystDsl.of(limiterStage(lookahead))
    val dry = CylinderSwapRig.dryChain(1.0)

    "a swap between a chain without latency and a late one holds the level, in both directions" {
        // The input is a sine of exactly 11 periods in 50 ms (220 Hz), so the orbit D frames late IS
        // the input: whatever the swap does to the time, the level must not move. The weights are
        // complementary where they are HEARD, at the output, so the orbit is the input throughout.
        // With the leaving ramp left at the input undelayed, dry to late dipped to 1/6 of the level
        // for 10 ms, and late to dry swelled by 5.3 dB.
        val level = 0.3
        val warm = 60
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * 44100).toInt()

        listOf("dry to late" to (dry to late50), "late to dry" to (late50 to dry)).forEach { (name, pair) ->
            val (rig, out, _) = swapRun(from = pair.first, to = pair.second, level = level, warm = warm, after = 60)

            rig.swap.settled shouldBe true

            for (k in d until out.size) {
                withClue("$name, sample $k (swap at ${warm * rig.blockFrames})") {
                    out[k] shouldBe (rigInput(rig, level, k) plusOrMinus 1e-12)
                }
            }
        }
    }

    "a late leaving chain is kept until its delayed fade-out has come out, then retired" {
        // Late to dry: the leaving chain's fade-out, applied at its input, reaches the output 50 ms
        // later, so the swap may not let go of it before the ramp plus that latency. The row above
        // shows the level through it; this one shows the swap still holds the chain that long, and
        // that it lets go (a lookahead ring reports a tail only while it holds audio).
        val rig = CylinderSwapRig()
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * rig.sampleRate).toInt()
        val rampFrames = (Crossfade.XFADE_SECONDS * rig.sampleRate).toInt()

        rig.registry.register("late", late50)
        rig.registry.register("dry", dry)
        rig.cylinder.requestChain("late")
        rig.render(blocks = 60, level = 0.5)
        rig.cylinder.requestChain("dry")

        var held = 0

        while (held < 200) {
            rig.block(level = 0.5)

            if (rig.swap.settled) {
                break
            }

            held++
        }

        held shouldBeInRange ((rampFrames + d) / rig.blockFrames)..((rampFrames + d) / rig.blockFrames + 2)
    }

    "a swap between two late chains of different latency is the output-law crossfade, sample for sample" {
        // 50 ms to 5 ms and back: the only swaps where the leaving chain is late AND the earlier
        // one (0 < D_L < M), so the leaving input ramp is delayed by M - D_L. Below the ceiling each
        // chain is its input delayed, so the orbit is exactly the crossfade the law names, on both
        // channels: `(1 - r(k - s - M)) * x(k - D_L) + r(k - s - M) * x(k - D_A)`. The RMS row
        // below is the readable one; this one sees a weight misplaced by a single frame.
        val level = 0.3
        val warm = 60
        val d50 = (Compressor.MAX_LOOKAHEAD_SECONDS * 44100).toInt()
        val d5 = delay
        val rampFrames = (Crossfade.XFADE_SECONDS * 44100).toInt()

        listOf(
            "50 ms to 5 ms" to Triple(late50, late5, d50 to d5),
            "5 ms to 50 ms" to Triple(late5, late50, d5 to d50),
        ).forEach { (name, case) ->
            val (from, to, lat) = case
            val (dL, dA) = lat
            val m = maxOf(dL, dA)
            val (rig, outL, outR) = swapRun(from = from, to = to, level = level, warm = warm, after = 60)
            val s = warm * rig.blockFrames

            for (k in dL until outL.size) {
                val t = if (k < s) 0.0 else ramp(d = k - s - m, rampFrames = rampFrames)
                val expectL = (1.0 - t) * rigInput(rig, level, k - dL) + t * rigInput(rig, level, k - dA)
                val expectR = (1.0 - t) * rigInputRight(rig, level, k - dL) + t * rigInputRight(rig, level, k - dA)

                withClue("$name, sample $k (swap at $s), weight $t") {
                    outL[k] shouldBe (expectL plusOrMinus 1e-12)
                    outR[k] shouldBe (expectR plusOrMinus 1e-12)
                }
            }
        }
    }

    "a late chain with a tail of its own is not retired until its lookahead ring has played out" {
        // The leaving chain feeds an effect with a tail INTO a 50 ms lookahead limiter, swapped late
        // to dry. When the tail stage lets go, the limiter's ring still holds the last 50 ms of what
        // it emitted; the chain must keep draining until that has come out, which is the
        // compressor's `hasTail` at work. Oracle: the same chain built standalone and fed exactly
        // what the leaving chain is fed (the input, then the input times the leaving input ramp,
        // then silence). While the swap holds the chain, the orbit is that chain's output (the dry
        // arriving chain hears silence by then); after it retires, nothing the standalone chain
        // still emits may rise above the orbit silence floor.
        val level = 0.3
        val warm = 60
        val rampFrames = (Crossfade.XFADE_SECONDS * 44100).toInt()

        listOf(
            // A single echo 30 ms late (feedback 0): it ends at full level, so when the delay lets
            // go the ring holds the end of a loud echo.
            "delay" to KatalystStageDsl.Delay(wet = c(1.0), time = c(0.03), feedback = c(0.0)),
            // A room decays smoothly: its output is below the orbit floor well before its tail
            // ceiling lets go, so here the ring's tail is inaudible either way (checked: this case
            // alone stays green with the compressor's `hasTail` forced to false). Kept as the
            // ordinary case the rule must also get right; the echo above is the one with teeth.
            "reverb" to KatalystStageDsl.Reverb(wet = c(0.5), size = c(1.0)),
        ).forEach { (name, tailStage) ->
            val leaving = KatalystDsl.of(tailStage, limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS))
            val rig = CylinderSwapRig()
            val bf = rig.blockFrames
            val s = warm * bf
            val stopBlock = (s + rampFrames) / bf + 1
            val total = 1500
            val out = DoubleArray(total * bf)

            rig.registry.register("late", leaving)
            rig.registry.register("dry", dry)
            rig.cylinder.requestChain("late")
            rigRun(rig, warm, level, out, fromBlock = 0)
            rig.cylinder.requestChain("dry")

            var retiredAfter = -1

            for (b in warm until total) {
                rig.block(level = if (b < stopBlock) level else 0.0).copyInto(out, b * bf)

                if (retiredAfter < 0 && rig.swap.settled) {
                    retiredAfter = b
                }
            }

            withClue("$name: the swap settled") { retiredAfter shouldBeGreaterThan stopBlock }

            // The standalone twin, fed block by block what the leaving chain was fed.
            val twin = chain(tailStage, limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS))
            val ctx = KatalystContext(blockFrames = bf, mixBuffer = StereoBuffer(bf))
            val twinOut = DoubleArray(total * bf)

            for (b in 0 until total) {
                for (i in 0 until bf) {
                    val k = b * bf + i
                    val lv = if (b < stopBlock) level else 0.0
                    val u = if (k < s) 1.0 else 1.0 - ramp(d = k - s, rampFrames = rampFrames)

                    ctx.mixBuffer.left[i] = lv * sin(2.0 * PI * 220.0 * k / rig.sampleRate) * u
                    ctx.mixBuffer.right[i] = lv * sin(2.0 * PI * 223.0 * k / rig.sampleRate) * u
                }

                twin.process(ctx)
                ctx.mixBuffer.left.copyInto(destination = twinOut, destinationOffset = b * bf, startIndex = 0, endIndex = bf)
            }

            for (k in stopBlock * bf until (retiredAfter + 1) * bf) {
                withClue("$name, sample $k: the held chain is the orbit") {
                    out[k] shouldBe (twinOut[k] plusOrMinus 1e-12)
                }
            }

            for (k in (retiredAfter + 1) * bf until twinOut.size) {
                withClue("$name, sample $k: nothing audible left behind (retired after block $retiredAfter)") {
                    abs(twinOut[k]) shouldBeLessThanOrEqual ORBIT_SILENCE_FLOOR
                }
            }
        }
    }

    "every swap between latencies holds the level on a NON-periodic input, window by window" {
        // A 223 Hz sine is not periodic in 50 ms, so no row can pass by the delay being invisible.
        // The RMS over 30 ms windows, against the input's, must stay near one through the swap:
        // the linear crossfade of two copies of a sine at the phase 50 ms apart dips to cos(27
        // degrees) = 0.89 mid-fade, the ordinary comb of a crossfade; the broken laws gave 1/6
        // (dry to late), 1.84 (late to dry) and 1.75 (50 ms to 5 ms).
        val level = 0.3
        val warm = 60
        val window = 1323

        listOf(
            "dry to late" to (dry to late50),
            "late to dry" to (late50 to dry),
            "50 ms to 5 ms" to (late50 to late5),
            "5 ms to 50 ms" to (late5 to late50),
        ).forEach { (name, pair) ->
            val (rig, _, out) = swapRun(from = pair.first, to = pair.second, level = level, warm = warm, after = 60)
            val from = warm * rig.blockFrames - window

            var start = from

            while (start + window <= out.size) {
                var sumOut = 0.0
                var sumIn = 0.0

                for (k in start until start + window) {
                    val x = rigInputRight(rig, level, k)

                    sumOut += out[k] * out[k]
                    sumIn += x * x
                }

                val ratio = sqrt(sumOut / sumIn)

                withClue("$name, window at $start (swap at ${warm * rig.blockFrames}), ratio $ratio") {
                    ratio shouldBeGreaterThan 0.85
                    ratio shouldBeLessThanOrEqual 1.15
                }

                start += rig.blockFrames
            }
        }
    }

    "the duck handover follows the output law: a leaving duck stays in force until the delayed start" {
        // Dry ducked chain to a 50 ms late chain with no duck: the duck is ramped OUT over the swap
        // with the SAME weights as the chains, at the output. Until the delayed start it is fully
        // in force, then it lets go along r(k - D). Twin rigs: the same script with the duck pass
        // off gives the orbit's un-ducked mix, and the duck's steady gain g is read before the swap.
        val level = 0.3
        val warm = 60
        val after = 60
        val ducked = KatalystDsl.of(
            KatalystStageDsl.Gain(gain = c(1.0)),
            KatalystStageDsl.Duck(orbit = c(0.0), depth = c(0.8), attack = c(0.05)),
        )

        fun run(duckPass: Boolean): DoubleArray {
            val rig = CylinderSwapRig()
            val out = DoubleArray((warm + after) * rig.blockFrames)

            rig.registry.register("ducked", ducked)
            rig.registry.register("late", late50)
            rig.cylinder.requestChain("ducked")
            rigRun(rig, warm, level, out, fromBlock = 0, sidechainLevel = 0.5, duckPass = duckPass)
            rig.cylinder.requestChain("late")
            rigRun(rig, after, level, out, fromBlock = warm, sidechainLevel = 0.5, duckPass = duckPass)

            return out
        }

        val a = run(duckPass = true)
        val b = run(duckPass = false)
        val s = warm * 128
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * 44100).toInt()
        val rampFrames = (Crossfade.XFADE_SECONDS * 44100).toInt()
        val probe = (s - 128 until s).maxByOrNull { abs(b[it]) }!!
        val g = a[probe] / b[probe]

        withClue("the duck is in force before the swap") { g shouldBeLessThan 0.9 }

        for (k in s until a.size) {
            val t = ramp(d = k - s - d, rampFrames = rampFrames)

            withClue("sample $k (swap at $s), weight $t") {
                a[k] shouldBe ((b[k] * (g * (1.0 - t) + t)) plusOrMinus 1e-12)
            }
        }
    }

    "the duck handover follows the output law: an arriving duck stays out until the delayed start" {
        // Dry chain without a duck to a 50 ms late chain WITH one: the arriving duck is ramped IN
        // with the chains' weights at the output. Until the delayed start the orbit is not ducked
        // at all, then the duck comes in along r(k - D). Twin rigs as above; the duck's steady gain
        // g is read after the swap has settled.
        val level = 0.3
        val warm = 60
        val after = 60
        val lateDucked = KatalystDsl.of(
            limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS),
            KatalystStageDsl.Duck(orbit = c(0.0), depth = c(0.8), attack = c(0.05)),
        )

        fun run(duckPass: Boolean): DoubleArray {
            val rig = CylinderSwapRig()
            val out = DoubleArray((warm + after) * rig.blockFrames)

            rig.registry.register("dry", dry)
            rig.registry.register("late", lateDucked)
            rig.cylinder.requestChain("dry")
            rigRun(rig, warm, level, out, fromBlock = 0, sidechainLevel = 0.5, duckPass = duckPass)
            rig.cylinder.requestChain("late")
            rigRun(rig, after, level, out, fromBlock = warm, sidechainLevel = 0.5, duckPass = duckPass)

            return out
        }

        val a = run(duckPass = true)
        val b = run(duckPass = false)
        val s = warm * 128
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * 44100).toInt()
        val rampFrames = (Crossfade.XFADE_SECONDS * 44100).toInt()
        val probe = (a.size - 128 until a.size).maxByOrNull { abs(b[it]) }!!
        val g = a[probe] / b[probe]

        withClue("the duck is in force after the swap") { g shouldBeLessThan 0.9 }

        for (k in s until a.size) {
            val t = ramp(d = k - s - d, rampFrames = rampFrames)

            withClue("sample $k (swap at $s), weight $t") {
                a[k] shouldBe ((b[k] * ((1.0 - t) + t * g)) plusOrMinus 1e-12)
            }
        }
    }

    "a swap between two chains of the same latency is seamless: the input delayed, below the ceiling" {
        // Both chains 50 ms late. The leaving chain's fade-out, applied at its input, comes out D
        // frames later, and so must the arriving chain's fade-in: then the two weights meet at the
        // same sample and sum to one, and the orbit is the input D frames late throughout. Started
        // undelayed, the swap stepped by D / L of the signal at t = D and then swelled to 1 + D / L.
        val rig = CylinderSwapRig()
        val level = 0.3
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * rig.sampleRate).toInt()
        val warm = 60
        val after = rig.fadeBlocks + d / rig.blockFrames + 8
        val out = DoubleArray((warm + after) * rig.blockFrames)

        rig.registry.register("lateA", KatalystDsl.of(limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS)))
        rig.registry.register(
            "lateB",
            KatalystDsl.of(limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS), KatalystStageDsl.Gain(gain = c(1.0))),
        )
        rig.cylinder.requestChain("lateA")
        rigRun(rig, warm, level, out, fromBlock = 0)
        rig.cylinder.requestChain("lateB")
        rigRun(rig, after, level, out, fromBlock = warm)

        rig.swap.settled shouldBe true

        for (k in d until out.size) {
            withClue("sample $k (swap at ${warm * rig.blockFrames})") {
                out[k] shouldBe (rigInput(rig, level, k - d) plusOrMinus 1e-12)
            }
        }
    }

    "a swap from a chain without latency to a late one has no step: the two copies flam, smoothly" {
        // Dry to 50 ms late: the dry copy fades out at once, the late one fades in when its first
        // sample of the swap comes out. Each term moves no faster than the input plus its own ramp,
        // so no sample step exceeds the input's steepest step plus 2 * level / L (the two ramps'
        // slopes). Started undelayed, the late copy entered at t = D with a step of D / L of it.
        val rig = CylinderSwapRig()
        val level = 0.3
        val d = (Compressor.MAX_LOOKAHEAD_SECONDS * rig.sampleRate).toInt()
        val rampFrames = (Crossfade.XFADE_SECONDS * rig.sampleRate).toInt()
        val warm = 60
        val after = rig.fadeBlocks + d / rig.blockFrames + 8
        val out = DoubleArray((warm + after) * rig.blockFrames)

        rig.registry.register("dry", CylinderSwapRig.dryChain(1.0))
        rig.registry.register("late", KatalystDsl.of(limiterStage(Compressor.MAX_LOOKAHEAD_SECONDS)))
        rig.cylinder.requestChain("dry")
        rigRun(rig, warm, level, out, fromBlock = 0)
        rig.cylinder.requestChain("late")
        rigRun(rig, after, level, out, fromBlock = warm)

        var inputStep = 0.0

        for (k in 1 until out.size) {
            inputStep = maxOf(inputStep, abs(rigInput(rig, level, k) - rigInput(rig, level, k - 1)))
        }

        val bound = inputStep + 2.0 * level / rampFrames + 1e-12
        val from = warm * rig.blockFrames

        for (k in from + 1 until out.size) {
            withClue("sample $k (swap at $from), bound $bound") {
                abs(out[k] - out[k - 1]) shouldBeLessThanOrEqual bound
            }
        }

        // ...and it did swap: the orbit ends as the input D frames late.
        out[out.size - 1] shouldBe (rigInput(rig, level, out.size - 1 - d) plusOrMinus 1e-12)
    }

    // ── The master-shaped chain and the bound ───────────────────────────────────────────────────

    "the limiter stage is the Compressor its constructor builds from the same numbers, bit for bit" {
        // Pins what phase 3 step 12 C3 relies on: the output's limiter used to be a Compressor built
        // through its constructor, and is now this stage configured with no owner (applyParams(null),
        // a Param is its default), whose instance takes the five numbers through its setters. Both
        // paths, with and without a lookahead (the corpus has none).
        for (seconds in listOf(0.0, lookahead)) {
            val built = chain(limiterStage(seconds))
            val constructed = Compressor(
                sampleRate = sampleRate,
                thresholdDb = LIMITER_THRESHOLD_DB,
                ratio = LIMITER_RATIO,
                kneeDb = LIMITER_KNEE_DB,
                attackSeconds = AUTHORED_LIMITER_ATTACK_SECONDS,
                releaseSeconds = LIMITER_RELEASE_SECONDS,
                lookaheadSeconds = seconds,
            )

            // Loud enough to limit hard: the input times 4.
            fun loud(ctx: KatalystContext) {
                for (i in 0 until blockFrames) {
                    ctx.mixBuffer.left[i] *= 4.0
                    ctx.mixBuffer.right[i] *= 4.0
                }
            }

            val a = run(40, process = { loud(it); built.applyParams(null); built.process(it) })
            val b = run(40, process = { loud(it); constructed.process(left = it.mixBuffer.left, right = it.mixBuffer.right, blockSize = it.blockFrames) })

            for (k in a.l.indices) {
                withClue("lookahead $seconds, sample $k") {
                    a.l[k].toRawBits() shouldBe b.l[k].toRawBits()
                    a.r[k].toRawBits() shouldBe b.r[k].toRawBits()
                }
            }
        }
    }

    "the lookahead a chain can ask for is bounded, and a hostile one builds none" {
        fun latency(seconds: Double) = chain(KatalystStageDsl.Compressor(lookahead = seconds)).compressor.shouldNotBeNull().latencyFrames

        latency(10.0) shouldBe (Compressor.MAX_LOOKAHEAD_SECONDS * sampleRate).toInt()
        latency(Double.POSITIVE_INFINITY) shouldBe 0
        latency(Double.NaN) shouldBe 0
        latency(-1.0) shouldBe 0
        latency(0.0) shouldBe 0
        latency(lookahead) shouldBe delay

        Compressor.coerceLookaheadSeconds(0.02) shouldBe 0.02
        Compressor.coerceLookaheadSeconds(0.2) shouldBe Compressor.MAX_LOOKAHEAD_SECONDS
    }

    "the classic chain's compressor has no lookahead" {
        // Its slots are the orbit's, and none of them is a lookahead; the born-with orbit is not late.
        chain(*KatalystDsl.classic.stages.toTypedArray()).compressor.shouldNotBeNull().latencyFrames shouldBe 0
        KatalystDsl.classic.stages.filterIsInstance<KatalystStageDsl.Compressor>().single().lookahead shouldBe 0.0
    }
})
