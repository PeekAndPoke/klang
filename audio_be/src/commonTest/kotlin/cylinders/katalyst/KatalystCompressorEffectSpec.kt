/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * The orbit compressor: what it does to the mix, and how it switches and changes (Katalyst 5c-7).
 *
 * **The oracles are built from a BARE [Compressor] driven by hand and the decided law, never from the
 * effect under test:** a switch is `out = dry + w * (compressed - dry)` with `w` linear over
 * [KNOB_GLIDE_SECONDS] in samples, from where it stands to 0 or 1, landing exactly; a knob change
 * follows [MoveLaw] (threshold, the ratio as its INVERSE, since the gain curve's slope `1 / ratio - 1`
 * is linear in it, and knee on one shared fraction; a glide over the knob glide's whole blocks, or a
 * release when the move reduces less at 0 dBFS), and the reference sets them on the bare compressor
 * ONE SAMPLE at a time. The input is deterministic and
 * loud enough to hold about 12 dB of reduction, with different content on the two channels (the
 * compressor links them by their peak).
 */
class KatalystCompressorEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** The switch fade, in samples: the decided law's length. */
    val fadeLen = (sampleRate * KNOB_GLIDE_SECONDS).toInt()

    /** The knob glide, in whole blocks (`audio/ref/knob-glide.md`). */
    val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()

    fun createCtx() = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    fun settings(
        thresholdDb: Double = -25.0,
        ratio: Double = 4.0,
        kneeDb: Double = 6.0,
        attackSeconds: Double = 0.005,
        releaseSeconds: Double = 0.1,
    ) = CompressorSettings(
        thresholdDb = thresholdDb,
        ratio = ratio,
        kneeDb = kneeDb,
        attackSeconds = attackSeconds,
        releaseSeconds = releaseSeconds,
    )

    fun bare(s: CompressorSettings) = Compressor(
        sampleRate = sampleRate,
        thresholdDb = s.thresholdDb,
        ratio = s.ratio,
        kneeDb = s.kneeDb,
        attackSeconds = s.attackSeconds,
        releaseSeconds = s.releaseSeconds,
    )

    fun left(k: Int): Double =
        0.5 * sin(2.0 * PI * 110.0 * k / sampleRate) + 0.2 * sin(2.0 * PI * 330.0 * k / sampleRate + 0.3)

    fun right(k: Int): Double = 0.45 * sin(2.0 * PI * 165.0 * k / sampleRate + 1.0)

    class Take(val l: DoubleArray, val r: DoubleArray)

    /** Runs [effect] over [blocks] blocks of the input, calling [before] ahead of each block. */
    fun run(effect: KatalystCompressorEffect, blocks: Int, before: (Int) -> Unit): Take {
        val ctx = createCtx()
        val l = DoubleArray(blocks * blockFrames)
        val r = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            before(b)

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = left(b * blockFrames + i)
                ctx.mixBuffer.right[i] = right(b * blockFrames + i)
            }

            effect.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = l, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = r, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return Take(l = l, r = r)
    }

    /**
     * The bare compressor [c] over samples `[from, to)` of the input, in blocks aligned to the
     * effect's (so a stretch at full weight matches bit for bit), written into [l] and [r].
     */
    fun bareRun(c: Compressor, from: Int, to: Int, l: DoubleArray, r: DoubleArray) {
        val bl = AudioBuffer(blockFrames)
        val br = AudioBuffer(blockFrames)
        var k = from

        while (k < to) {
            val len = minOf(blockFrames - k % blockFrames, to - k)

            for (i in 0 until len) {
                bl[i] = left(k + i)
                br[i] = right(k + i)
            }

            c.process(left = bl, right = br, blockSize = len)
            bl.copyInto(destination = l, destinationOffset = k, startIndex = 0, endIndex = len)
            br.copyInto(destination = r, destinationOffset = k, startIndex = 0, endIndex = len)
            k += len
        }
    }

    /** The decided switch law: the weight of the sample [j] samples into a fade from [from] to [to]. */
    fun weight(from: Double, to: Double, j: Int): Double =
        if (j >= fadeLen) to else to + (from - to) * (fadeLen - j).toDouble() / fadeLen

    /** Asserts `out == dry + w * (comp - dry)` per sample on `[from, to)`, [w] by sample index. */
    fun shouldFollowLaw(out: Take, comp: Take, from: Int, to: Int, w: (Int) -> Double) {
        for (k in from until to) {
            val dl = left(k)
            val dr = right(k)
            val wk = w(k)

            withClue("sample $k, weight $wk") {
                out.l[k] shouldBe ((dl + wk * (comp.l[k] - dl)) plusOrMinus 1e-12)
                out.r[k] shouldBe ((dr + wk * (comp.r[k] - dr)) plusOrMinus 1e-12)
            }
        }
    }

    fun shouldBeBits(actual: DoubleArray, expected: DoubleArray, from: Int, to: Int, what: String) {
        for (k in from until to) {
            withClue("$what, sample $k") {
                actual[k].toRawBits() shouldBe expected[k].toRawBits()
            }
        }
    }

    // ── What it does to the mix ─────────────────────────────────────────────────────────────────

    "does nothing when no compressor is configured" {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5)
        ctx.mixBuffer.right.fill(0.5)

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5
        ctx.mixBuffer.right[0] shouldBe 0.5
    }

    "compressor can be switched off before anything sounded, at once" {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        effect.configure(settings())

        effect.configure(null)
        effect.compressor.shouldBeNull()

        val ctx = createCtx()
        ctx.mixBuffer.left.fill(0.5)
        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5
    }

    // ── How it switches ─────────────────────────────────────────────────────────────────────────

    "the first initialisation is instant: a compressor set before the first block runs at full weight" {
        val s = settings()
        val blocks = 40
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val out = run(effect, blocks) { effect.configure(s) }

        val comp = Take(l = DoubleArray(blocks * blockFrames), r = DoubleArray(blocks * blockFrames))
        bareRun(c = bare(s), from = 0, to = blocks * blockFrames, l = comp.l, r = comp.r)

        shouldBeBits(actual = out.l, expected = comp.l, from = 0, to = blocks * blockFrames, what = "left")
        shouldBeBits(actual = out.r, expected = comp.r, from = 0, to = blocks * blockFrames, what = "right")
    }

    "switching OFF glides the gain reduction to 0 dB over the glide time, then releases the instance" {
        val s = settings()
        val offAt = 30
        val switch = offAt * blockFrames
        val landingBlock = (switch + fadeLen) / blockFrames
        val blocks = landingBlock + 5
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        var instance: Compressor? = null

        val out = run(effect, blocks) { b ->
            effect.configure(if (b < offAt) s else null)

            if (b == 0) {
                instance = effect.compressor
            }

            if (b in 1..landingBlock) {
                withClue("block $b: the instance is kept until the fade lands") {
                    effect.compressor.shouldNotBeNull() shouldBeSameInstanceAs instance
                }
            }
        }

        withClue("released once the gain reduction has landed on 0 dB") {
            effect.compressor.shouldBeNull()
        }

        val total = blocks * blockFrames
        val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
        bareRun(c = bare(s), from = 0, to = total, l = comp.l, r = comp.r)

        shouldBeBits(actual = out.l, expected = comp.l, from = 0, to = switch, what = "before the switch, full weight")
        shouldFollowLaw(out = out, comp = comp, from = switch, to = switch + fadeLen) { k -> weight(from = 1.0, to = 0.0, j = k - switch) }

        withClue("from the landing on, the output IS the dry mix (0 dB, bit for bit)") {
            for (k in switch + fadeLen until total) {
                out.l[k].toRawBits() shouldBe left(k).toRawBits()
                out.r[k].toRawBits() shouldBe right(k).toRawBits()
            }
        }
    }

    "switching ON on a sounding orbit fades a fresh instance in from dry" {
        val s = settings(attackSeconds = 0.001)
        val onAt = 20
        val switch = onAt * blockFrames
        val blocks = onAt + fadeLen / blockFrames + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)

        val out = run(effect, blocks) { b -> effect.configure(if (b < onAt) null else s) }

        // A FRESH instance at the switch: its envelope starts from rest there.
        val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
        bareRun(c = bare(s), from = switch, to = total, l = comp.l, r = comp.r)

        withClue("dry before the switch") {
            for (k in 0 until switch) {
                out.l[k].toRawBits() shouldBe left(k).toRawBits()
            }
        }

        shouldFollowLaw(out = out, comp = comp, from = switch, to = switch + fadeLen) { k -> weight(from = 0.0, to = 1.0, j = k - switch) }
        shouldBeBits(actual = out.l, expected = comp.l, from = switch + fadeLen, to = total, what = "after the fade-in, full weight")
    }

    "a life that ended by a fade starts the next one at rest, on its own knobs, like a new compressor" {
        val first = settings(thresholdDb = -25.0)
        val next = settings(thresholdDb = -14.0, ratio = 2.0, kneeDb = 1.0, attackSeconds = 0.001, releaseSeconds = 0.3)
        val offAt = 10
        val onAt = offAt + fadeLen / blockFrames + 5
        val switch = onAt * blockFrames
        val blocks = onAt + fadeLen / blockFrames + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)

        val out = run(effect, blocks) { b ->
            effect.configure(
                when {
                    b < offAt -> first
                    b < onAt -> null
                    else -> next
                }
            )
        }

        // A FRESHLY BUILT compressor at the switch: the one instance, reset and rewritten, must be it.
        val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
        bareRun(c = bare(next), from = switch, to = total, l = comp.l, r = comp.r)

        shouldFollowLaw(out = out, comp = comp, from = switch, to = switch + fadeLen) { k -> weight(from = 0.0, to = 1.0, j = k - switch) }
        shouldBeBits(actual = out.l, expected = comp.l, from = switch + fadeLen, to = total, what = "after the fade-in, full weight")
        shouldBeBits(actual = out.r, expected = comp.r, from = switch + fadeLen, to = total, what = "after the fade-in, full weight (right)")
    }

    "an owner that returns while the compressor fades out turns the fade around: same instance, continuous" {
        val s = settings()
        val offAt = 30
        val gap = 5
        val switch = offAt * blockFrames
        val back = (offAt + gap) * blockFrames
        val blocks = offAt + gap + fadeLen / blockFrames + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        var instance: Compressor? = null

        val out = run(effect, blocks) { b ->
            effect.configure(if (b in offAt until offAt + gap) null else s)

            if (b == 0) {
                instance = effect.compressor
            }

            withClue("block $b: one instance throughout, its envelope never restarts") {
                effect.compressor.shouldNotBeNull() shouldBeSameInstanceAs instance
            }
        }

        // The SAME envelope throughout: one bare compressor over the whole take.
        val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
        bareRun(c = bare(s), from = 0, to = total, l = comp.l, r = comp.r)

        val turn = weight(from = 1.0, to = 0.0, j = back - switch)

        shouldFollowLaw(out = out, comp = comp, from = switch, to = back) { k -> weight(from = 1.0, to = 0.0, j = k - switch) }
        shouldFollowLaw(out = out, comp = comp, from = back, to = back + fadeLen) { k -> weight(from = turn, to = 1.0, j = k - back) }
        shouldBeBits(actual = out.l, expected = comp.l, from = back + fadeLen, to = total, what = "after the turned-around fade, full weight")
    }

    "an OFF that arrives while the compressor fades in turns that fade around too, and then releases" {
        val s = settings()
        val onAt = 10
        val gap = 6
        val switch = onAt * blockFrames
        val back = (onAt + gap) * blockFrames
        val blocks = onAt + gap + fadeLen / blockFrames + 5
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)

        val out = run(effect, blocks) { b -> effect.configure(if (b in onAt until onAt + gap) s else null) }

        effect.compressor.shouldBeNull()

        val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
        bareRun(c = bare(s), from = switch, to = total, l = comp.l, r = comp.r)

        val turn = weight(from = 0.0, to = 1.0, j = back - switch)

        shouldFollowLaw(out = out, comp = comp, from = switch, to = back) { k -> weight(from = 0.0, to = 1.0, j = k - switch) }
        shouldFollowLaw(out = out, comp = comp, from = back, to = back + fadeLen) { k -> weight(from = turn, to = 0.0, j = k - back) }

        withClue("dry once the fade has landed") {
            for (k in back + fadeLen until total) {
                out.l[k].toRawBits() shouldBe left(k).toRawBits()
            }
        }
    }

    "reset and retire are a hard cut that leaves no stale fade and no stale glide: the next life snaps" {
        for (cut in listOf("reset", "retire")) {
            val first = settings(thresholdDb = -25.0)
            val next = settings(thresholdDb = -14.0, ratio = 2.0, kneeDb = 1.0, attackSeconds = 0.001, releaseSeconds = 0.3)
            val offAt = 20
            val cutAt = offAt + 4
            val blocks = cutAt + 30
            val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
            // A threshold rise three blocks before the switch-off, so a RELEASE still runs at the cut (review round 2):
            // the cut must forget the move, not only the fade.
            val rising = settings(thresholdDb = -5.0)

            val out = run(effect, blocks) { b ->
                when {
                    b < offAt - 3 -> effect.configure(first)
                    b < offAt -> effect.configure(rising)
                    b < cutAt -> effect.configure(null)
                    else -> {
                        if (b == cutAt) {
                            // Mid fade-out, mid glide of nothing: the orbit went silent and the
                            // cylinder cuts. The cut is synchronous.
                            if (cut == "reset") {
                                effect.reset()
                            } else {
                                effect.retire()
                            }

                            withClue("$cut: Off at once, the instance reset to rest") {
                                effect.compressor.shouldBeNull()
                            }
                        }

                        effect.configure(next)
                    }
                }
            }

            // The next life: a fresh instance with its own knobs at full weight from its first block.
            // Its attack is FAST on purpose: a slow one brings no reduction inside the first 50 ms,
            // and then a fade-in or a stale glide would render identically and pass unseen.
            val start = cutAt * blockFrames
            val total = blocks * blockFrames
            val comp = Take(l = DoubleArray(total), r = DoubleArray(total))
            bareRun(c = bare(next), from = start, to = total, l = comp.l, r = comp.r)

            shouldBeBits(actual = out.l, expected = comp.l, from = start, to = total, what = "$cut, then a new life")
            shouldBeBits(actual = out.r, expected = comp.r, from = start, to = total, what = "$cut, then a new life (right)")
        }
    }

    // ── How it changes while it runs ────────────────────────────────────────────────────────────

    /**
     * The knob law, oracle side, written from the decided law (by-ear decision 2026-10-17, review rounds 1 and 2): the
     * three knobs move as ONE move on one shared progress `p`, each from its value in force to its target. The move's law
     * is its NET effect on the curve at the fixed reference input of 0 dBFS ([curve], computed here; the
     * maintainer's decision of 2026-10-17, review round 3): a move that reduces no
     * less (more, or a tie) glides linearly, `p = k / glideBlocks` after k blocks, landing on the target. A move that
     * reduces strictly less releases: `p` is the
     * SLOWER of the glide's and the one-pole's `1 - decay^k`, `decay = exp(-blockFrames / (release * sampleRate))`,
     * landing once the glide's time has passed and `decay^k <= 1e-7`. Inside a block every knob runs linearly per
     * sample to the block's end value. A new target starts a new move from the values in force; the first values snap.
     */
    class MoveLaw(val glideBlocks: Int) {
        /** The soft-knee curve, written out from its definition: the reduction in dB at [env] dB. */
        fun curve(env: Double, threshold: Double, inverseRatio: Double, knee: Double): Double {
            val over = env - threshold
            val half = knee / 2.0
            val slope = inverseRatio - 1.0

            return when {
                over < -half -> 0.0
                over < half -> slope * (over + half) * (over + half) / (2.0 * knee)
                else -> slope * over
            }
        }

        val value = DoubleArray(3) { Double.NaN }
        val start = DoubleArray(3)
        val target = DoubleArray(3)
        var k = 0
        var releasing = false
        var moving = false
        var fraction = 1.0

        fun retarget(next: DoubleArray) {
            val env = 0.0

            if (value[0].isNaN()) {
                next.copyInto(value)
                next.copyInto(target)

                return
            }

            if ((0 until 3).all { next[it] == target[it] }) {
                return
            }

            value.copyInto(start)
            next.copyInto(target)
            releasing = curve(env, target[0], target[1], target[2]) > curve(env, value[0], value[1], value[2])
            moving = (0 until 3).any { target[it] != value[it] }
            k = 0
            fraction = 1.0
        }

        fun advance(decay: Double) {
            if (!moving) {
                return
            }

            k++

            var p = minOf(1.0, k.toDouble() / glideBlocks)
            var land = k >= glideBlocks

            if (releasing) {
                fraction *= decay
                p = minOf(p, 1.0 - fraction)
                land = land && fraction <= 1e-7
            }

            for (i in 0 until 3) {
                value[i] = if (land) target[i] else start[i] + (target[i] - start[i]) * p
            }

            if (land) {
                moving = false
            }
        }
    }

    /**
     * The bare compressor run ONE SAMPLE at a time, configured per block from [schedule] (block to settings, the first
     * at block 0): attack and release apply at once, threshold, inverse ratio and knee follow [MoveLaw] per sample.
     */
    fun knobReference(schedule: Map<Int, CompressorSettings>, blocks: Int): Take {
        val total = blocks * blockFrames
        val first = schedule.getValue(0)
        val c = bare(first)
        val ref = Take(l = DoubleArray(total), r = DoubleArray(total))
        val one = AudioBuffer(1)
        val two = AudioBuffer(1)
        val law = MoveLaw(glideBlocks = glideBlocks)
        var release = first.releaseSeconds

        for (b in 0 until blocks) {
            schedule[b]?.let { z ->
                c.attackSeconds = z.attackSeconds
                c.releaseSeconds = z.releaseSeconds
                release = z.releaseSeconds
                law.retarget(next = doubleArrayOf(z.thresholdDb, 1.0 / z.ratio, z.kneeDb))
            }

            val decay = exp(-blockFrames / (release * sampleRate))
            val t0 = law.value[0]
            val i0 = law.value[1]
            val k0 = law.value[2]

            law.advance(decay)

            val t1 = law.value[0]
            val i1 = law.value[1]
            val k1 = law.value[2]

            for (i in 0 until blockFrames) {
                val k = b * blockFrames + i
                val f = (i + 1).toDouble() / blockFrames

                c.thresholdDb = t0 + (t1 - t0) * f
                c.ratio = 1.0 / (i0 + (i1 - i0) * f)
                c.kneeDb = k0 + (k1 - k0) * f
                one[0] = left(k)
                two[0] = right(k)
                c.process(left = one, right = two, blockSize = 1)
                ref.l[k] = one[0]
                ref.r[k] = two[0]
            }
        }

        return ref
    }

    /** Runs the effect over [schedule] and asserts it against [knobReference] sample by sample. */
    fun scheduleRow(schedule: Map<Int, CompressorSettings>, blocks: Int): KatalystCompressorEffect {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        var current = schedule.getValue(0)
        val out = run(effect, blocks) { b ->
            current = schedule[b] ?: current
            effect.configure(current)
        }
        val ref = knobReference(schedule = schedule, blocks = blocks)

        for (k in 0 until blocks * blockFrames) {
            withClue("sample $k (block ${k / blockFrames}, changes at ${schedule.keys})") {
                out.l[k] shouldBe (ref.l[k] plusOrMinus 1e-9)
                out.r[k] shouldBe (ref.r[k] plusOrMinus 1e-9)
            }
        }

        return effect
    }

    fun knobRow(a: CompressorSettings, z: CompressorSettings, after: Int = glideBlocks + 10) {
        val changeAt = 25
        val effect = scheduleRow(schedule = mapOf(0 to a, changeAt to z), blocks = changeAt + after)

        withClue("the glide lands exactly: the instance holds the new knobs, and they are the knobs in force") {
            val held = effect.compressor.shouldNotBeNull()

            held.thresholdDb shouldBe z.thresholdDb
            held.ratio shouldBe z.ratio
            held.kneeDb shouldBe z.kneeDb
            effect.thresholdInForce shouldBe held.thresholdDb
            effect.inverseRatioInForce shouldBe 1.0 / held.ratio
            effect.kneeInForce shouldBe held.kneeDb
        }
    }

    /** Blocks a release at [releaseSeconds] needs to land (its fraction at 1e-7, and at least the glide), plus slack. */
    fun releaseBlocks(releaseSeconds: Double): Int =
        maxOf(glideBlocks, (ln(1e7) * releaseSeconds * sampleRate / blockFrames).toInt()) + 3

    "moves that ADD reduction glide per sample over the knob glide (a falling threshold, a higher ratio, a wider knee)" {
        knobRow(
            a = settings(thresholdDb = -12.0, ratio = 2.0, kneeDb = 0.0, attackSeconds = 0.005, releaseSeconds = 0.1),
            z = settings(thresholdDb = -25.0, ratio = 8.0, kneeDb = 12.0, attackSeconds = 0.02, releaseSeconds = 0.3),
        )
    }

    "moves that RELAX the compressor release at its release time (a rising threshold, a lower ratio, a narrower knee)" {
        // The by-ear decision of 2026-10-17. Each knob relaxes on its own here, and all three together.
        val a = settings(thresholdDb = -25.0, ratio = 8.0, kneeDb = 12.0, attackSeconds = 0.005, releaseSeconds = 0.1)

        knobRow(a = a, z = settings(thresholdDb = -12.0), after = releaseBlocks(0.1))
        knobRow(a = a, z = settings(thresholdDb = -25.0, ratio = 2.0, kneeDb = 12.0), after = releaseBlocks(0.1))
        // The knee only matters while the envelope sits inside it: at a threshold of -5 dB this input's envelope does.
        knobRow(
            a = settings(thresholdDb = -5.0, ratio = 8.0, kneeDb = 12.0),
            z = settings(thresholdDb = -5.0, ratio = 8.0, kneeDb = 0.0),
            after = releaseBlocks(0.1),
        )
        knobRow(
            a = a,
            z = settings(thresholdDb = -12.0, ratio = 2.0, kneeDb = 0.0, attackSeconds = 0.02, releaseSeconds = 0.3),
            after = releaseBlocks(0.3),
        )
    }

    "a change of mixed directions moves as one, on the release law (review round 1)" {
        knobRow(
            a = settings(thresholdDb = -25.0, ratio = 2.0, kneeDb = 0.0, attackSeconds = 0.005, releaseSeconds = 0.1),
            z = settings(thresholdDb = -12.0, ratio = 8.0, kneeDb = 12.0, attackSeconds = 0.02, releaseSeconds = 0.3),
            after = releaseBlocks(0.3),
        )
    }

    "a wide ratio swing: more reduction glides linearly in the inverse ratio, less releases" {
        knobRow(a = settings(ratio = 1.0), z = settings(ratio = 100.0))
        knobRow(a = settings(ratio = 100.0), z = settings(ratio = 1.0), after = releaseBlocks(0.1))
    }

    "the direction is judged against the value in force: a turn mid-release glides from where it stands, and back" {
        // Release 0.1 s, about 34 blocks. Rising at block 25 (a release, about -21.2 dB in force at block 40); falling below the value in force at block 40 (a linear glide from there);
        // at block 46, mid-glide, a target ABOVE the glide's target but BELOW the value in force (about -23.6 dB), which
        // is more reduction and so a fresh linear glide; at block 55, mid-glide again, a target above the value in
        // force: a release from where the glide stands.
        scheduleRow(
            schedule = mapOf(
                0 to settings(thresholdDb = -30.0),
                25 to settings(thresholdDb = -5.0),
                40 to settings(thresholdDb = -28.0),
                46 to settings(thresholdDb = -26.0),
                55 to settings(thresholdDb = -10.0),
            ),
            blocks = 55 + releaseBlocks(0.1),
        )
    }

    "a rising threshold swells at the release's time constant, not over the 50 ms glide, and without a step" {
        // The listening pair's case on a DC input, so the output IS the gain: 0.5 (-6 dBFS), threshold -40 dB to
        // 0 dB, ratio 8, knee 6, release 0.2 s. Before the fix the reduction (about 29.75 dB) was gone 50 ms after
        // the change; now the threshold in force is -40 * exp(-t / 0.2 s), and the reduction, linear in it until the
        // knee, goes from 10 % to 90 % of its change in about 1.36 time constants (0.27 s).
        val release = 0.2
        val low = settings(thresholdDb = -40.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = release)
        val high = settings(thresholdDb = 0.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = release)
        val changeAt = 40
        val blocks = changeAt + 300
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()
        val gainDb = DoubleArray(blocks * blockFrames)
        val inForce = DoubleArray(blocks)

        for (b in 0 until blocks) {
            effect.configure(if (b < changeAt) low else high)
            ctx.mixBuffer.left.fill(0.5)
            ctx.mixBuffer.right.fill(0.5)
            effect.process(ctx)
            inForce[b] = effect.thresholdInForce

            for (i in 0 until blockFrames) {
                gainDb[b * blockFrames + i] = 20.0 * log10(ctx.mixBuffer.left[i] / 0.5)
            }
        }

        val change = changeAt * blockFrames
        val before = gainDb[change - 1]
        val settled = gainDb[blocks * blockFrames - 1]

        withClue("the reduction before, and none after") {
            before shouldBe (-29.75 plusOrMinus 0.1)
            settled shouldBe (0.0 plusOrMinus 1e-4)
        }

        withClue("the threshold in force follows the release's one-pole, block by block") {
            for (m in 1..100) {
                inForce[changeAt - 1 + m] shouldBe ((-40.0 * exp(-m * blockFrames / (release * sampleRate))) plusOrMinus 1e-9)
            }
        }

        fun crossing(fraction: Double): Double {
            val level = before + (settled - before) * fraction
            val k = (change until gainDb.size).first { gainDb[it] >= level }

            return (k - change).toDouble() / sampleRate
        }

        val rise = crossing(0.9) - crossing(0.1)

        withClue("10 % to 90 % of the swell takes about 1.36 release time constants ($rise s)") {
            rise shouldBe (1.36 * release plusOrMinus 0.01)
        }

        withClue("50 ms after the change more than 20 dB of the reduction is still there (the glide had none left)") {
            (gainDb[change + (0.05 * sampleRate).toInt()] < -20.0) shouldBe true
        }

        withClue("no step: the largest one-sample move of the gain is the release's first slope, 35 dB / 0.2 s per sample") {
            var largest = 0.0

            for (k in change until gainDb.size) {
                largest = maxOf(largest, abs(gainDb[k] - gainDb[k - 1]))
            }

            // 35 dB times 1/(0.2 s * 44100) is 0.00397 dB; the linear 50 ms glide moved 0.0137 dB per sample on average, about 0.016 at its steepest.
            (largest < 0.0041) shouldBe true
        }
    }

    /** The effect's gain in dB per sample on a DC input of [level], configured per block from [schedule]. */
    fun dcGainDb(schedule: Map<Int, CompressorSettings>, blocks: Int, level: Double): DoubleArray {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()
        val gainDb = DoubleArray(blocks * blockFrames)
        var current = schedule.getValue(0)

        for (b in 0 until blocks) {
            current = schedule[b] ?: current
            effect.configure(current)
            ctx.mixBuffer.left.fill(level)
            ctx.mixBuffer.right.fill(level)
            effect.process(ctx)

            for (i in 0 until blockFrames) {
                gainDb[b * blockFrames + i] = 20.0 * log10(ctx.mixBuffer.left[i] / level)
            }
        }

        return gainDb
    }

    "a change of mixed directions never dips below the deeper end by more than the glide's own path, both ways" {
        // The reviewer's case (review round 1): (-40 dB, 2:1) to (-20 dB, 20:1), knee 6, release 0.2 s, the envelope at
        // -5 dBFS (DC). The ends reduce by 17.5 and 14.25 dB; the straight path through the curve, the old glide's,
        // dips to 18.4 dB at a third of the way. With a fraction per knob the adding ratio arrived in 50 ms while the
        // threshold lagged at the release time: 29 dB. On one shared fraction the path stays within 1.5 dB of the
        // deeper end.
        val one = settings(thresholdDb = -40.0, ratio = 2.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.2)
        val two = settings(thresholdDb = -20.0, ratio = 20.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.2)
        val level = 10.0.pow(-5.0 / 20.0)

        for ((a, z) in listOf(one to two, two to one)) {
            val changeAt = 40
            val gain = dcGainDb(schedule = mapOf(0 to a, changeAt to z), blocks = changeAt + releaseBlocks(0.2), level = level)
            val start = gain[changeAt * blockFrames - 1]
            val end = gain[gain.size - 1]
            val deepest = gain.copyOfRange(changeAt * blockFrames, gain.size).min()

            withClue("from ${a.thresholdDb} dB to ${z.thresholdDb} dB: start $start, end $end, deepest $deepest") {
                start shouldBe ((if (a === one) -17.5 else -14.25) plusOrMinus 0.05)
                end shouldBe ((if (a === one) -14.25 else -17.5) plusOrMinus 0.05)
                (deepest >= minOf(start, end) - 1.5) shouldBe true
            }
        }
    }

    "a short release is never faster than the glide: release 0 and 5 ms still take the knob glide's time" {
        // Review round 1: the glide is the click safety net, so a relaxing move moves by the slower of the release's
        // one-pole and the linear glide. The DC case of the swell row, -40 dB to 0 dB at ratio 8: the linear glide
        // alone takes the reduction from 10 to 90 percent in about 34 ms (17 blocks, 49.3 ms, the reduction gone at the
        // knee's edge); without the floor release 0 would land in one block and 5 ms would take about 7 ms.
        for (release in listOf(0.0, 0.005)) {
            val low = settings(thresholdDb = -40.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = release)
            val high = settings(thresholdDb = 0.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = release)
            val changeAt = 40
            val gain = dcGainDb(schedule = mapOf(0 to low, changeAt to high), blocks = changeAt + 60, level = 0.5)
            val change = changeAt * blockFrames
            val before = gain[change - 1]
            val settled = gain[gain.size - 1]

            fun crossing(fraction: Double): Int {
                val level = before + (settled - before) * fraction

                return (change until gain.size).first { gain[it] >= level } - change
            }

            val rise = (crossing(0.9) - crossing(0.1)).toDouble() / sampleRate

            withClue("release $release s: 10 % to 90 % in $rise s, the glide's is about 0.034 s") {
                (rise >= 0.030) shouldBe true
                settled shouldBe (0.0 plusOrMinus 1e-4)
            }
        }
    }

    "the law is the move's NET effect: a mixed move that adds reduction glides in 50 ms (review round 2)" {
        // Threshold -10 to -30 dB (adds) with ratio 8 to 7 (relaxes a little), the envelope at -6 dBFS (DC 0.5), release
        // 0.3 s. Net: about 3.5 to 20.6 dB of reduction, so the move glides and is within 1 dB of the new setting after
        // the glide (17 blocks, 49.3 ms); judged by "any knob relaxes" it ran at the release, 14.4 dB short at 50 ms.
        val a = settings(thresholdDb = -10.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.3)
        val z = settings(thresholdDb = -30.0, ratio = 7.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.3)
        val changeAt = 40
        val gain = dcGainDb(schedule = mapOf(0 to a, changeAt to z), blocks = changeAt + 60, level = 0.5)
        val settled = gain[gain.size - 1]
        val at = changeAt * blockFrames + glideBlocks * blockFrames

        withClue("settled ${settled} dB, ${gain[at]} dB at the glide's end") {
            settled shouldBe (-20.55 plusOrMinus 0.05)
            (abs(gain[at] - settled) < 1.0) shouldBe true
        }
    }

    "the law is the move's NET effect: a mixed move that relaxes on balance releases (review round 2)" {
        // The compressor's default (-20 dB, 4:1, knee 6) to the limiter's (-1 dB, 20:1, knee 2) at an envelope of
        // -6 dBFS: the ratio and the knee add, the threshold relaxes, and the net is 10.5 dB of reduction to none. The
        // threshold in force follows the release's one-pole (0.1 s), and 50 ms in more than 3 dB of reduction is left.
        val a = settings(thresholdDb = -20.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.003, releaseSeconds = 0.1)
        val z = settings(thresholdDb = -1.0, ratio = 20.0, kneeDb = 2.0, attackSeconds = 0.001, releaseSeconds = 0.1)
        val changeAt = 40
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()
        val inForce = DoubleArray(changeAt + 40)
        val gainDb = DoubleArray(inForce.size * blockFrames)

        for (b in inForce.indices) {
            effect.configure(if (b < changeAt) a else z)
            ctx.mixBuffer.left.fill(0.5)
            ctx.mixBuffer.right.fill(0.5)
            effect.process(ctx)
            inForce[b] = effect.thresholdInForce

            for (i in 0 until blockFrames) {
                gainDb[b * blockFrames + i] = 20.0 * log10(ctx.mixBuffer.left[i] / 0.5)
            }
        }

        withClue("the reduction before: ${gainDb[changeAt * blockFrames - 1]}") {
            gainDb[changeAt * blockFrames - 1] shouldBe (-10.5 plusOrMinus 0.05)
        }

        withClue("the threshold in force on the release's one-pole") {
            for (m in 1..30) {
                val fraction = exp(-m * blockFrames / (0.1 * sampleRate))

                inForce[changeAt - 1 + m] shouldBe ((-1.0 + (-20.0 + 1.0) * fraction) plusOrMinus 1e-9)
            }
        }

        withClue("50 ms in, more than 3 dB of reduction left: ${gainDb[changeAt * blockFrames + 2205]}") {
            (gainDb[changeAt * blockFrames + 2205] < -3.0) shouldBe true
        }
    }

    "a compressor-to-limiter swap that adds reduction does not overshoot the new ceiling past the glide (review round 2)" {
        // limiter(-1, 20, 2) to limiter(-6, 20, 1), release 0.1 s, a DC input at -2 dBFS: the threshold adds, the
        // narrower knee relaxes, the net adds, so it glides; judged by any knob it overshot by +2.9 dB 50 ms in.
        val a = settings(thresholdDb = -1.0, ratio = 20.0, kneeDb = 2.0, attackSeconds = 0.001, releaseSeconds = 0.1)
        val z = settings(thresholdDb = -6.0, ratio = 20.0, kneeDb = 1.0, attackSeconds = 0.001, releaseSeconds = 0.1)
        val level = 10.0.pow(-2.0 / 20.0)
        val changeAt = 40
        val gain = dcGainDb(schedule = mapOf(0 to a, changeAt to z), blocks = changeAt + 60, level = level)
        val settled = gain[gain.size - 1]
        val afterGlide = (changeAt + glideBlocks) * blockFrames

        for (k in afterGlide until gain.size) {
            withClue("sample $k, ${k - changeAt * blockFrames} after the change: ${gain[k]} dB against $settled") {
                (abs(gain[k] - settled) < 0.01) shouldBe true
            }
        }
    }

    "a tie glides: both thresholds above 0 dBFS reduce nothing at the reference level, so the move takes the glide" {
        // Review round 3: the law is judged at 0 dBFS. +3 dB to +10 dB with a 2 dB knee reduces nothing there either way,
        // a tie, so the move glides and has landed after the glide's blocks; a release would still be travelling.
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()
        val low = settings(thresholdDb = 3.0, ratio = 8.0, kneeDb = 2.0, releaseSeconds = 0.2)
        val high = settings(thresholdDb = 10.0, ratio = 8.0, kneeDb = 2.0, releaseSeconds = 0.2)

        for (b in 0 until 10 + glideBlocks) {
            effect.configure(if (b < 10) low else high)
            ctx.mixBuffer.left.fill(0.5)
            ctx.mixBuffer.right.fill(0.5)
            effect.process(ctx)
        }

        effect.thresholdInForce shouldBe 10.0
    }

    /**
     * The swell of a note after a rest (review round 3): [a] holds the orbit over a DC note of [level] for 40 blocks,
     * then silence for [restSeconds], then [z] arrives with the next note at the same level. Returns the time in
     * seconds the reduction takes from 10 to 90 percent of its recovery, from its deepest point after the onset to
     * where it settles.
     */
    fun restSwell(a: CompressorSettings, z: CompressorSettings, level: Double, restSeconds: Double): Double {
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()
        val restBlocks = (restSeconds * sampleRate / blockFrames).toInt()
        val noteAt = 40 + restBlocks
        val blocks = noteAt + 400
        val gain = DoubleArray((blocks - noteAt) * blockFrames)

        for (b in 0 until blocks) {
            effect.configure(if (b < noteAt) a else z)
            val x = if (b < 40 || b >= noteAt) level else 0.0
            ctx.mixBuffer.left.fill(x)
            ctx.mixBuffer.right.fill(x)
            effect.process(ctx)

            if (b >= noteAt) {
                for (i in 0 until blockFrames) {
                    gain[(b - noteAt) * blockFrames + i] = 20.0 * log10(ctx.mixBuffer.left[i] / level)
                }
            }
        }

        val deepestAt = gain.indices.minBy { gain[it] }
        val deepest = gain[deepestAt]
        val settled = gain[gain.size - 1]

        fun crossing(fraction: Double): Int =
            (deepestAt until gain.size).first { gain[it] >= deepest + (settled - deepest) * fraction }

        return (crossing(0.9) - crossing(0.1)).toDouble() / sampleRate
    }

    "a relaxing move after a rest swells at the release, not over the glide: the follower's envelope is not asked" {
        // Review round 3, the reviewer's cases. Judged at the follower's envelope, a rest left it near -100 dB, the
        // move read as a tie and glided, and the note met the old threshold moving linearly: 16.2 dB in 20 ms at
        // -6 dBFS, 21.3 dB in 25 ms at 0 dBFS, 11 dB in 30 ms for the second pair. Judged at 0 dBFS the move releases,
        // and the swell's 10 to 90 percent takes at least 1.2 release time constants.
        val pairA = settings(thresholdDb = -40.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.2)
        val pairZ = settings(thresholdDb = 0.0, ratio = 8.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.2)
        val otherA = settings(thresholdDb = -30.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.003, releaseSeconds = 0.1)
        val otherZ = settings(thresholdDb = -10.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.003, releaseSeconds = 0.1)

        val cases = listOf(
            Triple("pair, note at -6 dBFS", restSwell(pairA, pairZ, level = 0.5, restSeconds = 0.25), 0.2),
            Triple("pair, note at 0 dBFS", restSwell(pairA, pairZ, level = 1.0, restSeconds = 0.25), 0.2),
            Triple("(-30, 4) to (-10, 4), 50 ms rest", restSwell(otherA, otherZ, level = 0.5, restSeconds = 0.05), 0.1),
        )

        for ((name, rise, tau) in cases) {
            withClue("$name: 10 % to 90 % in $rise s, the release $tau s") {
                (rise >= 1.2 * tau) shouldBe true
            }
        }
    }

    "a modulated knob counts at its value in force on both sides of the judgement" {
        // The threshold holds a modulator (a one-block glide outside the move) and jumps in the same block in which the
        // owner moves the ratio. Judged with the threshold at its value in force on both sides, only the ratio counts:
        // 8 to 6 relaxes (a release, still travelling after the glide's blocks), 6 to 8 adds (a glide, landed). Judged
        // at the threshold's new target instead, the jump would decide the other way round both times.
        fun ratioInForceAfterGlide(ratioFrom: Double, ratioTo: Double, thresholdFrom: Double, thresholdTo: Double): Double {
            val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames, thresholdModulated = true)
            val ctx = createCtx()

            for (b in 0 until 40 + glideBlocks + 1) {
                val after = b >= 40

                effect.configure(
                    settings(
                        thresholdDb = if (after) thresholdTo else thresholdFrom,
                        ratio = if (after) ratioTo else ratioFrom,
                        releaseSeconds = 0.2,
                    )
                )
                ctx.mixBuffer.left.fill(0.5)
                ctx.mixBuffer.right.fill(0.5)
                effect.process(ctx)
            }

            return effect.inverseRatioInForce
        }

        withClue("8 to 6 with the threshold jumping -10 to -30: a release, still travelling") {
            (ratioInForceAfterGlide(8.0, 6.0, -10.0, -30.0) < 1.0 / 6.0 - 1e-6) shouldBe true
        }
        withClue("6 to 8 with the threshold jumping -30 to -10: a glide, landed") {
            ratioInForceAfterGlide(6.0, 8.0, -30.0, -10.0) shouldBe 1.0 / 8.0
        }
    }

    "a negative release releases like release 0: the move's floor is the follower's (Compressor.MIN_TIME_SECONDS)" {
        fun trace(release: Double) = dcGainDb(
            schedule = mapOf(
                0 to settings(thresholdDb = -40.0, ratio = 8.0, kneeDb = 6.0, releaseSeconds = release),
                40 to settings(thresholdDb = 0.0, ratio = 8.0, kneeDb = 6.0, releaseSeconds = release),
            ),
            blocks = 100,
            level = 0.5,
        )

        val negative = trace(-0.1)
        val zero = trace(0.0)

        for (k in negative.indices) {
            withClue("sample $k") { negative[k] shouldBe (zero[k] plusOrMinus 1e-12) }
        }

        withClue("and it lands") { negative[negative.size - 1] shouldBe (0.0 plusOrMinus 1e-4) }
    }

    "a return to the move's exact start, mid-release and after landing, glides back from where the knobs stand" {
        // Rising at block 25 (a release), back to the exact starting threshold at block 35 (more reduction: a linear
        // glide from the value in force), rising again at block 60 and, after it landed, back to the start once more.
        val a = settings(thresholdDb = -30.0)
        val z = settings(thresholdDb = -5.0)
        val landed = 60 + releaseBlocks(0.1)

        scheduleRow(
            schedule = mapOf(0 to a, 25 to z, 35 to a, 60 to z, landed to a),
            blocks = landed + glideBlocks + 10,
        )
    }

    "an unchanged config writes nothing into the instance: the knobs are written when a VALUE changes" {
        // Katalyst knob expressions step 2: the writer refills ONE holder in place, so the effect compares the five
        // values, not the object (until then a new object per resolve, compared by reference).
        val s = settings(thresholdDb = -20.0)
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()

        effect.configure(s)
        effect.process(ctx)

        // A probe written behind the effect's back survives a re-configure with the SAME settings ...
        val c = effect.compressor.shouldNotBeNull()
        c.releaseSeconds = 0.77
        effect.configure(s)
        c.releaseSeconds shouldBe 0.77

        // ... and with another object holding equal numbers ...
        effect.configure(settings(thresholdDb = -20.0))
        c.releaseSeconds shouldBe 0.77

        // ... and is overwritten when one value of the same holder changes in place: all five are written.
        s.thresholdDb = -21.0
        effect.configure(s)
        c.thresholdDb shouldBe -21.0
        c.releaseSeconds shouldBe 0.1
    }

    "each of the five knobs alone, changed in place in the same holder, reaches the instance" {
        // One knob per step, so a compare that ignores one knob, or tests another knob's value in its place, leaves that
        // knob behind. (A compare against another knob's STORED slot fails safe, writing every block: the "writes
        // nothing" and "NaN knob settles" rows catch that one.)
        // The new values are ones the setters store as they are (a ratio of at least 1, a knee of at least 0).
        val s = settings(thresholdDb = -25.0, ratio = 4.0, kneeDb = 6.0, attackSeconds = 0.005, releaseSeconds = 0.1)
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()

        effect.configure(s)
        effect.process(ctx)

        val c = effect.compressor.shouldNotBeNull()

        /** One knob: its name, its new value, how the holder takes it, how the instance reads it. */
        class Knob(val name: String, val next: Double, val write: (Double) -> Unit, val read: () -> Double)

        val knobs = listOf(
            Knob(name = "threshold", next = -18.0, write = { s.thresholdDb = it }, read = { c.thresholdDb }),
            Knob(name = "ratio", next = 3.0, write = { s.ratio = it }, read = { c.ratio }),
            Knob(name = "knee", next = 2.0, write = { s.kneeDb = it }, read = { c.kneeDb }),
            Knob(name = "attack", next = 0.008, write = { s.attackSeconds = it }, read = { c.attackSeconds }),
            Knob(name = "release", next = 0.25, write = { s.releaseSeconds = it }, read = { c.releaseSeconds }),
        )

        for (knob in knobs) {
            knob.write(knob.next)
            effect.configure(s)
            effect.process(ctx)

            withClue("${knob.name} changed alone") {
                knob.read() shouldBe knob.next
            }
        }
    }

    "a NaN knob settles: a config that repeats it writes nothing, though NaN never equals itself" {
        // The cached-config rule of `/review-loop`: a raw compare would see the NaN threshold as a change on every
        // block. The writer never hands one (it substitutes its constants); a direct caller can.
        val s = settings(thresholdDb = Double.NaN)
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()

        effect.configure(s)
        effect.process(ctx)

        val c = effect.compressor.shouldNotBeNull()
        c.releaseSeconds = 0.77
        effect.configure(s)
        effect.configure(settings(thresholdDb = Double.NaN))
        c.releaseSeconds shouldBe 0.77
    }

    "a life that ended forgets the cache: the next ON writes every knob, even the same numbers" {
        val s = settings(thresholdDb = -20.0)
        val effect = KatalystCompressorEffect(sampleRate = sampleRate, blockFrames = blockFrames)
        val ctx = createCtx()

        effect.configure(s)
        effect.process(ctx)

        val c = effect.compressor.shouldNotBeNull()

        // Off, and the fade out lands: the life ends ([compressor] reads null in Off; the instance lives on).
        effect.configure(null)

        repeat(fadeLen / blockFrames + 2) { effect.process(ctx) }

        effect.compressor.shouldBeNull()

        c.releaseSeconds = 0.77
        effect.configure(s)
        effect.compressor.shouldNotBeNull() shouldBeSameInstanceAs c
        c.releaseSeconds shouldBe 0.1
    }
})
