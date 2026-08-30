/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * Ledger D13 (redesigned 2026-08-30): sharing never crosses a `detune` boundary.
 *
 * The oracle used throughout: the fork semantics are DEFINED as "identical to authoring the
 * subtree twice", and the fold semantics as "identical to not writing the detune at all" — so
 * every row compares a shared-tree render bit-exactly against its explicitly-authored twin,
 * with build AND generate randomness pinned to the same seed on both sides.
 */
class DetuneForkSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 8

    fun render(dsl: IgnitorDsl, freqHz: Double, soundIndex: Int = 0, seed: Int = 42): DoubleArray {
        val rng = Random(seed)
        val ignitor = dsl.buildExciter(soundIndex = soundIndex, random = rng, freqHz = freqHz).ignitor
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blockFrames * (blocks + 2),
            gateEndFrame = blockFrames * (blocks + 2),
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = rng,
        )
        val out = DoubleArray(blocks * blockFrames)
        val buf = AudioBuffer(blockFrames)

        repeat(blocks) { b ->
            ctx.voiceElapsedFrames = b * blockFrames
            ctx.offset = 0
            ctx.length = blockFrames
            buf.fill(0.0)
            ignitor.generate(buf, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }
        return m
    }

    "sharing never crosses a detune boundary: s + s.detune(12) is bit-identical to authoring s twice" {
        // Shared: one Sine node referenced plainly AND under a detune.
        val s = IgnitorDsl.Sine()
        val shared = IgnitorDsl.Plus(s, IgnitorDsl.Detune(s, IgnitorDsl.Constant(12.0)))

        // The twin: two distinct instances, which is what the fork must be indistinguishable from.
        val twice = IgnitorDsl.Plus(
            IgnitorDsl.Sine(),
            IgnitorDsl.Detune(IgnitorDsl.Sine(), IgnitorDsl.Constant(12.0)),
        )

        val a = render(shared, freqHz = 220.0)
        val b = render(twice, freqHz = 220.0)

        a.any { it != 0.0 } shouldBe true
        maxDiff(a, b) shouldBe 0.0
    }

    "the detune-FIRST ordering shares nothing either: s.detune(12) + s equals its authored-twice twin" {
        // Pins the context POP (review round 1): with the restore deleted, the plain reference
        // AFTER the detuned arm looks up s under the leaked child context, HITS the detuned
        // instance, and the memo double-advances it at two freqs — every other row builds its
        // detune last or symmetrically and cannot see that.
        val s = IgnitorDsl.Sine()
        val shared = IgnitorDsl.Plus(IgnitorDsl.Detune(s, IgnitorDsl.Constant(12.0)), s)

        val twice = IgnitorDsl.Plus(
            IgnitorDsl.Detune(IgnitorDsl.Sine(), IgnitorDsl.Constant(12.0)),
            IgnitorDsl.Sine(),
        )

        val a = render(shared, freqHz = 220.0)
        val b = render(twice, freqHz = 220.0)

        a.any { it != 0.0 } shouldBe true
        maxDiff(a, b) shouldBe 0.0
    }

    "the D13 headline: a stateful EFFECT shared across the boundary forks — grain clocks advance once per instance" {
        // The ledger's original defect is a shimmer whose grain scheduler ran at 2x through the
        // split memo key. The fork must make the shared authoring bit-identical to the honest
        // twice-authoring, shimmer state included.
        val s = IgnitorDsl.Shimmer(IgnitorDsl.Sine())
        val shared = IgnitorDsl.Plus(s, IgnitorDsl.Detune(s, IgnitorDsl.Constant(12.0)))

        val twice = IgnitorDsl.Plus(
            IgnitorDsl.Shimmer(IgnitorDsl.Sine()),
            IgnitorDsl.Detune(IgnitorDsl.Shimmer(IgnitorDsl.Sine()), IgnitorDsl.Constant(12.0)),
        )

        val a = render(shared, freqHz = 220.0)
        val b = render(twice, freqHz = 220.0)

        a.any { it != 0.0 } shouldBe true
        maxDiff(a, b) shouldBe 0.0
    }

    "semitones is controlled from OUTSIDE the detune scope: a node shared with the plain arm stays shared" {
        // The design decision at the Detune arm: the semitones expression builds in the OUTER
        // context. Oracle by INEQUALITY (review round 1): with a noise node n driving the
        // semitones AND playing plainly, the correct build shares ONE n — audibly different
        // from the explicitly-unshared twin R (whose extra instance draws its own stream). The
        // mutant that builds semitones inside the child context forks n and becomes R.
        val n = IgnitorDsl.WhiteNoise()
        val t = IgnitorDsl.Plus(
            IgnitorDsl.Detune(IgnitorDsl.Sine(), IgnitorDsl.Times(n, IgnitorDsl.Constant(5.0))),
            n,
        )

        val r = IgnitorDsl.Plus(
            IgnitorDsl.Detune(IgnitorDsl.Sine(), IgnitorDsl.Times(IgnitorDsl.WhiteNoise(), IgnitorDsl.Constant(5.0))),
            IgnitorDsl.WhiteNoise(),
        )

        val a = render(t, freqHz = 220.0)
        val b = render(r, freqHz = 220.0)

        (maxDiff(a, b) > 0.001) shouldBe true
    }

    "the identity fold: a pitch-free subtree under detune stays SHARED — the same noise doubled, not decorrelated" {
        // WhiteNoise has no Freq consumer, so the detune folds to identity and n + n.detune(12)
        // must be EXACTLY the one noise instance summed with itself. A fork would give the
        // second instance its own rng draws — decorrelated, audibly wider, wrong.
        val n = IgnitorDsl.WhiteNoise()
        val shared = IgnitorDsl.Plus(n, IgnitorDsl.Detune(n, IgnitorDsl.Constant(12.0)))

        val tree = render(shared, freqHz = 220.0)
        val bare = render(n, freqHz = 220.0)

        bare.any { it != 0.0 } shouldBe true

        var m = 0.0
        for (i in tree.indices) {
            m = maxOf(m, abs(tree[i] - 2.0 * bare[i]))
        }
        m shouldBe 0.0
    }

    "detune is the freq-argument multiply: Detune(tree, 12) at f equals tree at 2f — absolute-rate LFOs stay put" {
        // The runtime is untouched by D13; this row pins WHY the scoping is right: the note
        // sine reads the (multiplied) freq argument, the Constant(5) LFO ignores it. So the
        // whole detuned render at 220 must equal the plain render at 440, LFO included.
        // (Runtime CHARACTERIZATION — this row passes pre-D13 too; the fork/fold rows above
        // are the D13 guards.)
        fun tree() = IgnitorDsl.Times(
            IgnitorDsl.Sine(),
            IgnitorDsl.Sine(freq = IgnitorDsl.Constant(5.0)),
        )

        val detuned = render(IgnitorDsl.Detune(tree(), IgnitorDsl.Constant(12.0)), freqHz = 220.0)
        val plain = render(tree(), freqHz = 440.0)

        detuned.any { it != 0.0 } shouldBe true
        maxDiff(detuned, plain) shouldBe 0.0
    }

    "the Fm arm of the fold predicate: an absolute-carrier FM patch under detune still transposes" {
        // fmModIgnitor consumes the freq ARGUMENT directly (FM index = depth/freqHz, modulator
        // driven at freqHz x ratio) with no Freq leaf anywhere in the tree — the one such node.
        // Without the predicate's unconditional Fm arm this patch would FOLD and `.detune()`
        // would be silently inert on it (review round 2 found the arm unguarded).
        fun fm() = IgnitorDsl.Fm(
            carrier = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(200.0)),
            modulator = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(300.0)),
            ratio = IgnitorDsl.Constant(2.0),
            depth = IgnitorDsl.Constant(60.0),
        )

        // Detune multiplies the freq argument, so the detuned render at 220 must equal the
        // plain render at 440 — index halved, modulator an octave up, exactly as authored.
        val detuned = render(IgnitorDsl.Detune(fm(), IgnitorDsl.Constant(12.0)), freqHz = 220.0)
        val plain = render(fm(), freqHz = 440.0)

        detuned.any { it != 0.0 } shouldBe true
        maxDiff(detuned, plain) shouldBe 0.0
    }

    "a shared Detune node dedups at its own wrapper: d + d is the one detuned instance doubled" {
        // analog > 0 arms the row (review round 1): two drift-active instances wander apart, so
        // a mutant that stops caching the Detune node breaks the 2x identity — at the default
        // analog 0.0 two phase-0 sines at the same freq are bit-identical and the row was blind.
        val d = IgnitorDsl.Detune(
            IgnitorDsl.Sine(analog = IgnitorDsl.Constant(0.9)),
            IgnitorDsl.Constant(12.0),
        )
        val tree = render(IgnitorDsl.Plus(d, d), freqHz = 220.0)
        val bare = render(d, freqHz = 220.0)

        bare.any { it != 0.0 } shouldBe true

        var m = 0.0
        for (i in tree.indices) {
            m = maxOf(m, abs(tree[i] - 2.0 * bare[i]))
        }
        m shouldBe 0.0
    }

    "a nested detune's DEAD semitones does not force a fork" {
        // The inner detune folds (noise is pitch-free), so its semitones is never built — a
        // Freq leaf in there can never be consumed, and the OUTER detune must fold too (the
        // walker answers a nested Detune from its inner only).
        val n = IgnitorDsl.WhiteNoise()
        val nested = IgnitorDsl.Detune(
            IgnitorDsl.Detune(n, IgnitorDsl.Freq),
            IgnitorDsl.Constant(12.0),
        )
        val tree = render(IgnitorDsl.Plus(nested, n), freqHz = 220.0)
        val bare = render(n, freqHz = 220.0)

        bare.any { it != 0.0 } shouldBe true

        var m = 0.0
        for (i in tree.indices) {
            m = maxOf(m, abs(tree[i] - 2.0 * bare[i]))
        }
        m shouldBe 0.0
    }

    "a shared-let diamond folds through the walker's memo without decorrelating" {
        // Plus(a, a) walks node `a` twice: the first visit computes and memoizes, the second
        // READS the memo — the one path where a poisoned memo table would flip the fold
        // decision (everything else short-circuits before the table).
        val a = IgnitorDsl.WhiteNoise()
        val diamond = IgnitorDsl.Plus(a, a)
        val tree = render(IgnitorDsl.Plus(diamond, IgnitorDsl.Detune(diamond, IgnitorDsl.Constant(12.0))), freqHz = 220.0)
        val bare = render(diamond, freqHz = 220.0)

        bare.any { it != 0.0 } shouldBe true

        var m = 0.0
        for (i in tree.indices) {
            m = maxOf(m, abs(tree[i] - 2.0 * bare[i]))
        }
        m shouldBe 0.0
    }

    "Variants resolve BEFORE the fold decision: the noise variant folds while its pitched sibling forks" {
        // soundIndex picks the subtree the voice actually builds; the fold must judge THAT
        // subtree, not the union of all variants — a noise variant still folds (stays shared)
        // even when a sibling variant is pitched.
        val v = IgnitorDsl.Variants(listOf(IgnitorDsl.WhiteNoise(), IgnitorDsl.Sine()))
        val shared = IgnitorDsl.Plus(v, IgnitorDsl.Detune(v, IgnitorDsl.Constant(12.0)))

        // Index 0 (noise): fold — the doubled-single-instance oracle.
        val tree0 = render(shared, freqHz = 220.0, soundIndex = 0)
        val bare0 = render(IgnitorDsl.WhiteNoise(), freqHz = 220.0, soundIndex = 0)
        // The bare reference is a DIFFERENT WhiteNoise node (fresh uid) — sound because noise
        // draws NOTHING at build time (it captures the rng and draws per sample at generate),
        // and each render() starts a fresh Random(seed) with a zero-draw build phase on both
        // sides, so the generate-time streams start aligned (round 2 corrected the old
        // build-rng-seeding wording, which described a mechanism that does not exist).
        var m0 = 0.0
        for (i in tree0.indices) {
            m0 = maxOf(m0, abs(tree0[i] - 2.0 * bare0[i]))
        }
        m0 shouldBe 0.0

        // Index 1 (sine): fork — compared against a reference WITHOUT Variants, so a walker
        // that judges the wrong variant cannot warp both sides of the oracle the same way
        // (mutation campaign: a pick hardcoded to child 0 survived the Variants-twice twin).
        val tree1 = render(shared, freqHz = 220.0, soundIndex = 1)
        val twice1 = render(
            IgnitorDsl.Plus(
                IgnitorDsl.Sine(),
                IgnitorDsl.Detune(IgnitorDsl.Sine(), IgnitorDsl.Constant(12.0)),
            ),
            freqHz = 220.0,
            soundIndex = 1,
        )
        maxDiff(tree1, twice1) shouldBe 0.0
    }
})
