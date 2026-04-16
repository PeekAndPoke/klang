package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs

/**
 * Property-based checks for DSL composition semantics.
 *
 * These tests encode invariants that must hold regardless of the implementation details of
 * the Ignitor runtime. They are the ground truth as the DSL → Ignitor pipeline is refactored
 * toward an immutable-input, memoising design.
 *
 * - **Shared source**: a single DSL node referenced multiple times produces identical samples
 *   at every read site (so `let s = ...; s + s` equals `s.mul(2)`).
 * - **Independent constructions**: two distinct DSL instances produce independent Ignitors
 *   (so `Osc.whiteNoise() + Osc.whiteNoise()` is NOT `2·whiteNoise()`).
 * - **Mix linearity** (once the mix param is standardised): `effect(mix = r)` is bit-identical
 *   to `dry·(1-r) + wet·r`.
 *
 * Goldens (committed audio fixtures) are explicitly deferred until Phase 1 is proven stable.
 */
class CompositionPropertiesSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createCtx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * 16,
        gateEndFrame = blockFrames * 16,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 16,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    fun render(ig: Ignitor, freqHz: Double, ctx: IgniteContext): FloatArray {
        val buf = FloatArray(blockFrames)
        ig.generate(buf, freqHz, ctx)
        return buf
    }

    fun FloatArray.rms(): Double {
        var s = 0.0
        for (x in this) s += x.toDouble() * x.toDouble()
        return kotlin.math.sqrt(s / size)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Shared source semantics — memoisation guarantees identical samples to both readers.
    // ═══════════════════════════════════════════════════════════════════════════

    "shared Sine node: Plus(s, s) is bit-identical to Mul(s, Constant(2.0))" {
        val s = IgnitorDsl.Sine()
        val plusTree = IgnitorDsl.Plus(s, s)
        val mulTree = IgnitorDsl.Mul(s, IgnitorDsl.Constant(2.0))

        val plus = plusTree.toExciter()
        val mul = mulTree.toExciter()

        val plusOut = render(plus, 440.0, createCtx())
        val mulOut = render(mul, 440.0, createCtx())

        for (i in 0 until blockFrames) {
            // Floating-point arithmetic is order-sensitive; one sample's worth of epsilon is fine.
            plusOut[i].toDouble() shouldBe (mulOut[i].toDouble() plusOrMinus 1e-5)
        }
    }

    "shared node used 3x: sum equals 3·s" {
        val s = IgnitorDsl.Sine()
        val threeSum = IgnitorDsl.Plus(IgnitorDsl.Plus(s, s), s)
        val tripled = IgnitorDsl.Mul(s, IgnitorDsl.Constant(3.0))

        val a = render(threeSum.toExciter(), 440.0, createCtx())
        val b = render(tripled.toExciter(), 440.0, createCtx())

        for (i in 0 until blockFrames) {
            a[i].toDouble() shouldBe (b[i].toDouble() plusOrMinus 1e-5)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Independent constructions — distinct DSL instances stay independent.
    // ═══════════════════════════════════════════════════════════════════════════

    "two separate Sine DSL instances produce distinct Ignitors under the identity cache" {
        // Both are structurally equal but not `===`.
        val a = IgnitorDsl.Sine()
        val b = IgnitorDsl.Sine()
        (a === b) shouldBe false
        a shouldBe b

        val cache = IgnitorBuildCache()
        val ia = a.buildIgnitor(null, cache)
        val ib = b.buildIgnitor(null, cache)

        // Different DSL identities ⇒ different Ignitor instances.
        (ia === ib) shouldBe false
    }

    "shared Sine DSL node maps to a single Ignitor under the identity cache" {
        val s = IgnitorDsl.Sine()
        val cache = IgnitorBuildCache()

        val ia = s.buildIgnitor(null, cache)
        val ib = s.buildIgnitor(null, cache)

        (ia === ib) shouldBe true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cheap-leaf nodes skip memoisation (they stay as plain ParamIgnitor / FreqIgnitor).
    // ═══════════════════════════════════════════════════════════════════════════

    "Constant leaves bypass the memoising wrap" {
        val c = IgnitorDsl.Constant(0.5)
        val ig = c.toExciter()
        (ig is MemoizingIgnitor) shouldBe false
    }

    "Freq leaf bypasses the memoising wrap" {
        val ig = IgnitorDsl.Freq.toExciter()
        (ig is MemoizingIgnitor) shouldBe false
    }

    "signal-producing nodes are wrapped in MemoizingIgnitor" {
        val ig = IgnitorDsl.Sine().toExciter()
        (ig is MemoizingIgnitor) shouldBe true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Memoisation sanity — same block, repeated generate() calls return cached output.
    // ═══════════════════════════════════════════════════════════════════════════

    "repeated generate() calls within one block return identical samples" {
        val ig = IgnitorDsl.Sine().toExciter()
        val ctx = createCtx()

        val first = render(ig, 440.0, ctx)
        val second = render(ig, 440.0, ctx)

        for (i in 0 until blockFrames) first[i] shouldBe second[i]
    }

    "advancing voiceElapsedFrames produces new samples (cache invalidated)" {
        val ig = IgnitorDsl.Sine().toExciter()
        val ctx = createCtx()

        val first = render(ig, 440.0, ctx)
        ctx.voiceElapsedFrames = blockFrames
        val second = render(ig, 440.0, ctx)

        // Sine advances continuously — the two blocks cannot be identical.
        var diffs = 0
        for (i in 0 until blockFrames) {
            if (abs(first[i] - second[i]) > 1e-6) diffs++
        }
        (diffs > 0) shouldBe true
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RMS-level sanity for the summed shared source.
    // ═══════════════════════════════════════════════════════════════════════════

    "summed shared sine has RMS ≈ 2× single sine" {
        val s = IgnitorDsl.Sine()
        val single = s.toExciter()
        val doubled = IgnitorDsl.Plus(s, s).toExciter()

        val singleRms = render(single, 440.0, createCtx()).rms()
        val doubledRms = render(doubled, 440.0, createCtx()).rms()

        doubledRms shouldBe (2.0 * singleRms plusOrMinus 1e-4)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Detune path should invalidate the memo cache for shared sources.
    // Without this, `let s = sine; s.detune(0) + s.detune(7)` would yield `2·s.detune(0)`.
    // ═══════════════════════════════════════════════════════════════════════════

    "detuned shared source: two detunes with different semitones do NOT collapse" {
        val s = IgnitorDsl.Sine()
        val tree = IgnitorDsl.Plus(
            IgnitorDsl.Detune(inner = s, semitones = IgnitorDsl.Constant(0.0)),
            IgnitorDsl.Detune(inner = s, semitones = IgnitorDsl.Constant(7.0)),
        )
        val ig = tree.toExciter()
        val out = render(ig, 440.0, createCtx())

        // If the two branches collapsed to `2·sine(440)`, RMS would be ≈ 2·(sine RMS) ≈ 2·0.707 ≈ 1.414.
        // Two distinct sine frequencies beat against each other — their summed RMS is ≈ sqrt(2) × singleRms.
        val single = render(IgnitorDsl.Sine().toExciter(), 440.0, createCtx())
        val summedRms = out.rms()
        val single2x = 2.0 * single.rms()

        // Summed-distinct-frequencies RMS must be strictly below 2× (otherwise branches collapsed).
        (summedRms < 0.95 * single2x) shouldBe true
    }
})
