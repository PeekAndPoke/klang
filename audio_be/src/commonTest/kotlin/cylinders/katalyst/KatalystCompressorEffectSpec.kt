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
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

/**
 * The orbit compressor: what it does to the mix, and how it switches and changes (Katalyst 5c-7).
 *
 * **The oracles are built from a BARE [Compressor] driven by hand and the decided law, never from the
 * effect under test:** a switch is `out = dry + w * (compressed - dry)` with `w` linear over
 * [KNOB_GLIDE_SECONDS] in samples, from where it stands to 0 or 1, landing exactly; a knob change
 * moves threshold and knee linearly per sample over the knob glide (whole blocks) and the ratio
 * linearly in its INVERSE (the gain curve's slope `1 / ratio - 1` is linear in it), and the
 * reference sets them on the bare compressor ONE SAMPLE at a time. The input is deterministic and
 * loud enough to hold about 12 dB of reduction, with different content on the two channels (the
 * compressor links them by their peak).
 */
class KatalystCompressorEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    /** The switch fade, in samples: the decided law's length. */
    val fadeLen = (sampleRate * KNOB_GLIDE_SECONDS).toInt()

    /** The knob glide, in whole blocks (`docs/plans/knob-glide.md`). */
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
    ) = Voice.Compressor(
        thresholdDb = thresholdDb,
        ratio = ratio,
        kneeDb = kneeDb,
        attackSeconds = attackSeconds,
        releaseSeconds = releaseSeconds,
    )

    fun bare(s: Voice.Compressor) = Compressor(
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
            ctx.mixBuffer.left.copyInto(l, b * blockFrames, 0, blockFrames)
            ctx.mixBuffer.right.copyInto(r, b * blockFrames, 0, blockFrames)
        }

        return Take(l, r)
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

            c.process(bl, br, len)
            bl.copyInto(l, k, 0, len)
            br.copyInto(r, k, 0, len)
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
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
        val ctx = createCtx()

        ctx.mixBuffer.left.fill(0.5)
        ctx.mixBuffer.right.fill(0.5)

        effect.process(ctx)

        ctx.mixBuffer.left[0] shouldBe 0.5
        ctx.mixBuffer.right[0] shouldBe 0.5
    }

    "compresses loud signal when compressor is configured" {
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
        effect.configure(settings(thresholdDb = -20.0, ratio = 4.0, kneeDb = 0.0, attackSeconds = 0.0001, releaseSeconds = 0.1))

        val ctx = createCtx()
        val loudSignal = 0.9

        // Process enough blocks for envelope to converge
        repeat(20) {
            ctx.mixBuffer.left.fill(loudSignal)
            ctx.mixBuffer.right.fill(loudSignal)
            effect.process(ctx)
        }

        // Last sample should be compressed (quieter than input)
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (outputLevel < loudSignal) shouldBe true
    }

    "quiet signal passes through uncompressed" {
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
        effect.configure(settings(thresholdDb = -6.0, ratio = 4.0, kneeDb = 0.0, attackSeconds = 0.001, releaseSeconds = 0.1))

        val ctx = createCtx()
        // Very quiet signal, well below threshold
        val quietSignal = 0.01
        ctx.mixBuffer.left.fill(quietSignal)
        ctx.mixBuffer.right.fill(quietSignal)

        effect.process(ctx)

        // Should pass through essentially unchanged
        val outputLevel = abs(ctx.mixBuffer.left[blockFrames - 1])
        (abs(outputLevel - quietSignal) < 0.001) shouldBe true
    }

    "compressor can be switched off before anything sounded, at once" {
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
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
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
        val out = run(effect, blocks) { effect.configure(s) }

        val comp = Take(DoubleArray(blocks * blockFrames), DoubleArray(blocks * blockFrames))
        bareRun(bare(s), 0, blocks * blockFrames, comp.l, comp.r)

        shouldBeBits(out.l, comp.l, 0, blocks * blockFrames, "left")
        shouldBeBits(out.r, comp.r, 0, blocks * blockFrames, "right")
    }

    "switching OFF glides the gain reduction to 0 dB over the glide time, then releases the instance" {
        val s = settings()
        val offAt = 30
        val switch = offAt * blockFrames
        val landingBlock = (switch + fadeLen) / blockFrames
        val blocks = landingBlock + 5
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
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
        val comp = Take(DoubleArray(total), DoubleArray(total))
        bareRun(bare(s), 0, total, comp.l, comp.r)

        shouldBeBits(out.l, comp.l, 0, switch, "before the switch, full weight")
        shouldFollowLaw(out, comp, switch, switch + fadeLen) { k -> weight(1.0, 0.0, k - switch) }

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
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)

        val out = run(effect, blocks) { b -> effect.configure(if (b < onAt) null else s) }

        // A FRESH instance at the switch: its envelope starts from rest there.
        val comp = Take(DoubleArray(total), DoubleArray(total))
        bareRun(bare(s), switch, total, comp.l, comp.r)

        withClue("dry before the switch") {
            for (k in 0 until switch) {
                out.l[k].toRawBits() shouldBe left(k).toRawBits()
            }
        }

        shouldFollowLaw(out, comp, switch, switch + fadeLen) { k -> weight(0.0, 1.0, k - switch) }
        shouldBeBits(out.l, comp.l, switch + fadeLen, total, "after the fade-in, full weight")
    }

    "a life that ended by a fade starts the next one at rest, on its own knobs, like a new compressor" {
        val first = settings(thresholdDb = -25.0)
        val next = settings(thresholdDb = -14.0, ratio = 2.0, kneeDb = 1.0, attackSeconds = 0.001, releaseSeconds = 0.3)
        val offAt = 10
        val onAt = offAt + fadeLen / blockFrames + 5
        val switch = onAt * blockFrames
        val blocks = onAt + fadeLen / blockFrames + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)

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
        val comp = Take(DoubleArray(total), DoubleArray(total))
        bareRun(bare(next), switch, total, comp.l, comp.r)

        shouldFollowLaw(out, comp, switch, switch + fadeLen) { k -> weight(0.0, 1.0, k - switch) }
        shouldBeBits(out.l, comp.l, switch + fadeLen, total, "after the fade-in, full weight")
        shouldBeBits(out.r, comp.r, switch + fadeLen, total, "after the fade-in, full weight (right)")
    }

    "an owner that returns while the compressor fades out turns the fade around: same instance, continuous" {
        val s = settings()
        val offAt = 30
        val gap = 5
        val switch = offAt * blockFrames
        val back = (offAt + gap) * blockFrames
        val blocks = offAt + gap + fadeLen / blockFrames + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
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
        val comp = Take(DoubleArray(total), DoubleArray(total))
        bareRun(bare(s), 0, total, comp.l, comp.r)

        val turn = weight(1.0, 0.0, back - switch)

        shouldFollowLaw(out, comp, switch, back) { k -> weight(1.0, 0.0, k - switch) }
        shouldFollowLaw(out, comp, back, back + fadeLen) { k -> weight(turn, 1.0, k - back) }
        shouldBeBits(out.l, comp.l, back + fadeLen, total, "after the turned-around fade, full weight")
    }

    "an OFF that arrives while the compressor fades in turns that fade around too, and then releases" {
        val s = settings()
        val onAt = 10
        val gap = 6
        val switch = onAt * blockFrames
        val back = (onAt + gap) * blockFrames
        val blocks = onAt + gap + fadeLen / blockFrames + 5
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)

        val out = run(effect, blocks) { b -> effect.configure(if (b in onAt until onAt + gap) s else null) }

        effect.compressor.shouldBeNull()

        val comp = Take(DoubleArray(total), DoubleArray(total))
        bareRun(bare(s), switch, total, comp.l, comp.r)

        val turn = weight(0.0, 1.0, back - switch)

        shouldFollowLaw(out, comp, switch, back) { k -> weight(0.0, 1.0, k - switch) }
        shouldFollowLaw(out, comp, back, back + fadeLen) { k -> weight(turn, 0.0, k - back) }

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
            val effect = KatalystCompressorEffect(sampleRate, blockFrames)

            val out = run(effect, blocks) { b ->
                when {
                    b < offAt -> effect.configure(first)
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
            val comp = Take(DoubleArray(total), DoubleArray(total))
            bareRun(bare(next), start, total, comp.l, comp.r)

            shouldBeBits(out.l, comp.l, start, total, "$cut, then a new life")
            shouldBeBits(out.r, comp.r, start, total, "$cut, then a new life (right)")
        }
    }

    // ── How it changes while it runs ────────────────────────────────────────────────────────────

    /**
     * The knob law, oracle side: a bare compressor run ONE SAMPLE at a time from [a], switched to
     * [z] at sample [change]: attack and release at once, threshold and knee linear per sample, the
     * ratio linear in its INVERSE (the curve's slope `1 / ratio - 1` is linear in it), each over the
     * knob glide's whole blocks and landing on the target.
     */
    fun knobReference(a: Voice.Compressor, z: Voice.Compressor, change: Int, total: Int): Take {
        val glide = glideBlocks * blockFrames
        val c = bare(a)
        val ref = Take(DoubleArray(total), DoubleArray(total))
        val one = AudioBuffer(1)
        val two = AudioBuffer(1)

        for (k in 0 until total) {
            if (k == change) {
                c.attackSeconds = z.attackSeconds
                c.releaseSeconds = z.releaseSeconds
            }

            if (k >= change) {
                val j = k - change
                val f = if (j >= glide) 1.0 else (j + 1).toDouble() / glide
                val inverse = 1.0 / a.ratio + (1.0 / z.ratio - 1.0 / a.ratio) * f

                c.thresholdDb = a.thresholdDb + (z.thresholdDb - a.thresholdDb) * f
                c.ratio = 1.0 / inverse
                c.kneeDb = a.kneeDb + (z.kneeDb - a.kneeDb) * f
            }

            one[0] = left(k)
            two[0] = right(k)
            c.process(one, two, 1)
            ref.l[k] = one[0]
            ref.r[k] = two[0]
        }

        return ref
    }

    fun knobRow(a: Voice.Compressor, z: Voice.Compressor) {
        val changeAt = 25
        val change = changeAt * blockFrames
        val blocks = changeAt + glideBlocks + 10
        val total = blocks * blockFrames
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)

        val out = run(effect, blocks) { b -> effect.configure(if (b < changeAt) a else z) }
        val ref = knobReference(a, z, change, total)

        for (k in 0 until total) {
            withClue("sample $k (the change at $change, the glide ${glideBlocks * blockFrames} samples)") {
                out.l[k] shouldBe (ref.l[k] plusOrMinus 1e-9)
                out.r[k] shouldBe (ref.r[k] plusOrMinus 1e-9)
            }
        }

        withClue("the glide lands exactly: the instance holds the new knobs") {
            val held = effect.compressor.shouldNotBeNull()

            held.thresholdDb shouldBe z.thresholdDb
            held.ratio shouldBe z.ratio
            held.kneeDb shouldBe z.kneeDb
        }
    }

    "threshold, ratio and knee glide per sample over the knob glide; attack and release apply at once" {
        knobRow(
            a = settings(thresholdDb = -25.0, ratio = 2.0, kneeDb = 0.0, attackSeconds = 0.005, releaseSeconds = 0.1),
            z = settings(thresholdDb = -12.0, ratio = 8.0, kneeDb = 12.0, attackSeconds = 0.02, releaseSeconds = 0.3),
        )
    }

    "a wide ratio swing glides linearly in the inverse ratio, both ways" {
        knobRow(a = settings(ratio = 1.0), z = settings(ratio = 100.0))
        knobRow(a = settings(ratio = 100.0), z = settings(ratio = 1.0))
    }

    "an unchanged owner writes nothing into the instance: the knobs are written when the settings change" {
        val s = settings(thresholdDb = -20.0)
        val effect = KatalystCompressorEffect(sampleRate, blockFrames)
        val ctx = createCtx()

        effect.configure(s)
        effect.process(ctx)

        // A probe written behind the effect's back survives a re-configure with the SAME settings
        // (the writer hands the same object every block of one owner) ...
        val c = effect.compressor.shouldNotBeNull()
        c.releaseSeconds = 0.77
        effect.configure(s)
        c.releaseSeconds shouldBe 0.77

        // ... and is overwritten by a new settings object, even one with equal numbers.
        effect.configure(settings(thresholdDb = -20.0))
        c.releaseSeconds shouldBe 0.1
    }
})
