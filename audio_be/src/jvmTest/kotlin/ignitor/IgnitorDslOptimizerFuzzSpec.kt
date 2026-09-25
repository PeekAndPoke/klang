/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.shape
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.OPTIMIZER_PARITY
import io.peekandpoke.klang.audio_bridge.optimize
import kotlin.math.abs
import kotlin.random.Random

/**
 * The optimizer on graphs nobody wrote: a seeded generator composes trees from sources,
 * block-constant and modulated arithmetic, filters, shapers, envelopes, shared subtrees and the
 * kill switch, with constants that include the adversarial ones (zero, negative zero, huge,
 * infinite, NaN). Every tree is rendered authored and optimized with same-seeded streams and held
 * to `OPTIMIZER_PARITY`; the pass's laws (idempotent, never grows, sharing survives) are checked
 * on every tree; and `IgnitorRegistry` must have swallowed no failure over the run.
 *
 * This is the guard against a rule that is right on every shape a person thought of and wrong
 * on the one they did not. A failure prints the seed, which reproduces the tree.
 */
class IgnitorDslOptimizerFuzzSpec : StringSpec({

    val blockFrames = 128
    val blocks = 3
    val sr = 44100
    val trees = 1500

    /** What the generator actually produced over the run: the corpus must contain what it claims to. */
    class Stats {
        var diamonds = 0
        var reuses = 0
        var adversarialConstants = 0
        var hintsOff = 0
        var affines = 0
        var sweptFilters = 0
        var humanizedFilters = 0
    }

    fun ctx(random: Random): IgniteContext = IgniteContext(
        sampleRate = sr, voiceDurationFrames = blockFrames * 16, gateEndFrame = blockFrames * 16, releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames), random = random,
    )

    fun withinParity(a: Double, b: Double, scale: Double = 0.0): Boolean = when {
        a.isNaN() || b.isNaN() -> a.isNaN() && b.isNaN()
        a.isInfinite() || b.isInfinite() -> a == b
        else -> abs(a - b) <= OPTIMIZER_PARITY * maxOf(abs(a), abs(b), scale, 1e-300)
    }

    /**
     * The block's scale: its loudest finite sample on either side, at most full scale (1.0), what
     * the margin is relative to. The cap keeps the law in a saturated block: one sample at
     * SAFE_MAX must not buy the musical samples next to it a tolerance of 1e3.
     */
    fun scaleOf(bufA: AudioBuffer, bufB: AudioBuffer, from: Int, until: Int): Double {
        var peak = 0.0

        for (i in from until until) {
            val a = abs(bufA[i])
            val b = abs(bufB[i])

            if (a.isFinite() && a > peak) {
                peak = a
            }

            if (b.isFinite() && b > peak) {
                peak = b
            }
        }

        return if (peak > 1.0) 1.0 else peak
    }

    /** Mostly ordinary values, sometimes the ones that break a careless fold. */
    fun constant(r: Random, stats: Stats): Double {
        val pick = r.nextInt(20)

        if (pick < 7) {
            stats.adversarialConstants++
        }

        return when (pick) {
            0 -> 0.0
            1 -> -0.0
            2 -> 1e300
            3 -> Double.POSITIVE_INFINITY
            4 -> Double.NaN
            5 -> 1e-12
            6 -> -1e9
            else -> (r.nextDouble() * 4.0 - 2.0)
        }
    }

    /**
     * A random tree of at most [depth] levels. [pool] holds subtrees built so far, so a node may
     * be REUSED (a shared intermediate, refcount above one) rather than rebuilt.
     */
    fun tree(r: Random, depth: Int, pool: MutableList<IgnitorDsl>, stats: Stats): IgnitorDsl {
        if (depth <= 0 || r.nextInt(5) == 0) {
            val leaf: IgnitorDsl = when (r.nextInt(7)) {
                0 -> IgnitorDsl.Sine(freq = IgnitorDsl.Freq)
                1 -> IgnitorDsl.Sine(freq = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(1.0 + r.nextDouble())))
                2 -> IgnitorDsl.Sawtooth()
                3 -> IgnitorDsl.WhiteNoise()
                4 -> IgnitorDsl.Constant(constant(r, stats))
                5 -> IgnitorDsl.Param("p${r.nextInt(3)}", constant(r, stats))
                else -> IgnitorDsl.Freq
            }

            pool.add(leaf)

            return leaf
        }

        if (pool.isNotEmpty() && r.nextInt(6) == 0) {
            stats.reuses++

            return pool[r.nextInt(pool.size)]
        }

        val inner = tree(r, depth - 1, pool, stats)
        val k = IgnitorDsl.Constant(constant(r, stats))
        val node: IgnitorDsl = when (r.nextInt(27)) {
            0 -> IgnitorDsl.Times(inner, k)
            1 -> IgnitorDsl.Times(k, inner)
            2 -> IgnitorDsl.Plus(inner, k)
            3 -> IgnitorDsl.Minus(inner, k)
            4 -> IgnitorDsl.Div(inner, k)
            5 -> IgnitorDsl.Times(inner, tree(r, depth - 1, pool, stats))
            6 -> IgnitorDsl.Plus(inner, tree(r, depth - 1, pool, stats))
            7 -> IgnitorDsl.Neg(inner)
            8 -> IgnitorDsl.Abs(inner)
            9 -> IgnitorDsl.Tanh(inner)
            10 -> IgnitorDsl.Lowpass(inner, freq = IgnitorDsl.Constant(200.0 + r.nextDouble() * 8000.0), q = IgnitorDsl.Constant(0.5 + r.nextDouble()))
            11 -> IgnitorDsl.Highpass(inner, freq = IgnitorDsl.Constant(50.0 + r.nextDouble() * 2000.0))
            12 -> IgnitorDsl.Bandpass(inner, freq = IgnitorDsl.Constant(200.0 + r.nextDouble() * 4000.0))
            13 -> IgnitorDsl.Notch(inner, freq = IgnitorDsl.Constant(200.0 + r.nextDouble() * 4000.0))
            14 -> IgnitorDsl.Lowpass(inner, freq = IgnitorDsl.Param("cut", 1000.0), passes = 1 + r.nextInt(3))
            15 -> IgnitorDsl.Drive(inner, amount = IgnitorDsl.Constant(r.nextDouble()))
            16 -> inner.shape(listOf("soft", "tube", "hard", "asym")[r.nextInt(4)], oversample = listOf(0, 2, 4)[r.nextInt(3)])
            17 -> IgnitorDsl.Adsr(inner, attackSec = IgnitorDsl.Constant(0.001 + r.nextDouble() * 0.05), decaySec = IgnitorDsl.Constant(r.nextDouble() * 0.2))
            18 -> IgnitorDsl.Clamp(inner, lo = IgnitorDsl.Constant(-1.0), hi = IgnitorDsl.Constant(1.0))
            19 -> IgnitorDsl.Lerp(inner, tree(r, depth - 1, pool, stats), t = k)
            20 -> IgnitorDsl.Unipolar(inner)
            21 -> {
                val on = if (r.nextInt(4) == 0) 0 else 1

                if (on == 0) {
                    stats.hintsOff++
                }

                IgnitorDsl.OptimizerHint(inner, on = on)
            }

            // A diamond: one filtered intermediate under two filter parents. The shape a rule
            // that ignores sharing forks, doubling the work without changing a sample.
            22 -> {
                stats.diamonds++

                val shared = IgnitorDsl.Lowpass(inner, freq = IgnitorDsl.Constant(500.0 + r.nextDouble() * 3000.0))

                IgnitorDsl.Plus(
                    IgnitorDsl.Highpass(shared, freq = IgnitorDsl.Constant(100.0 + r.nextDouble() * 500.0)),
                    IgnitorDsl.Notch(shared, freq = IgnitorDsl.Constant(800.0 + r.nextDouble() * 2000.0)),
                )
            }

            23 -> IgnitorDsl.Lowpass(IgnitorDsl.Times(inner, k), freq = IgnitorDsl.Constant(300.0 + r.nextDouble() * 5000.0))

            // Phase 3 step 3a, and the two arms below buy DIFFERENT things, which is worth
            // saying because it is easy to assume they are a pair.
            //
            // Arm 24, the swept filter, does reach the `env.isLiteralZero()` refusal: without
            // it the filter fuses into an `EqSection`, the sweep disappears and the render
            // leaves the parity margin.
            //
            // Arm 25, the humanized one, does NOT reach the `!humanize` clause and cannot: its
            // `analog` is non-zero with probability 1 - 2^-53, so `analog.isLiteralZero()` refuses
            // first
            // and deleting `&& !humanize` from all four arms leaves this spec green. (Nor would
            // a literal-zero analog help: at `analog = 0` the lane and the tolerance are inert,
            // so a fused humanized filter renders the same anyway.) The refusal clause's home is
            // the dedicated row in `IgnitorDslOptimizerSpec`, "humanize never fuses". What arm 25
            // buys here is the other half: a humanized filter renders identically authored and
            // optimized, and its four build-time rng draws stay aligned across the two streams,
            // which is exactly what a fuzz over both trees can see and a shape spec cannot.
            24 -> {
                stats.sweptFilters++

                IgnitorDsl.Lowpass(
                    inner,
                    freq = IgnitorDsl.Constant(300.0 + r.nextDouble() * 4000.0),
                    env = IgnitorDsl.Constant(-24.0 + r.nextDouble() * 48.0),
                    attackSec = IgnitorDsl.Constant(r.nextDouble() * 0.05),
                    decaySec = IgnitorDsl.Constant(r.nextDouble() * 0.3),
                    sustainLevel = IgnitorDsl.Constant(r.nextDouble()),
                    releaseSec = IgnitorDsl.Constant(r.nextDouble() * 0.3),
                )
            }

            25 -> {
                stats.humanizedFilters++

                IgnitorDsl.Highpass(
                    inner,
                    freq = IgnitorDsl.Constant(50.0 + r.nextDouble() * 1500.0),
                    analog = IgnitorDsl.Constant(r.nextDouble() * 8.0),
                    humanize = true,
                )
            }
            // the optimizer's own node, authored (a wire may carry one; a fold must compose with it)
            else -> {
                stats.affines++

                // the shapes a fold produces: absent pre-add or add is the -0.0 identity
                IgnitorDsl.Affine(
                    inner,
                    pre = if (r.nextBoolean()) IgnitorDsl.Constant(-0.0) else IgnitorDsl.Constant(constant(r, stats)),
                    mul = k,
                    add = if (r.nextBoolean()) IgnitorDsl.Constant(-0.0) else IgnitorDsl.Constant(constant(r, stats)),
                )
            }
        }

        pool.add(node)

        return node
    }

    /** Arithmetic over leaves that are one value per block, however deep: no pass of its own. */
    fun isScalar(dsl: IgnitorDsl): Boolean = when (dsl) {
        is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> true
        is IgnitorDsl.Times, is IgnitorDsl.Plus, is IgnitorDsl.Minus, is IgnitorDsl.Div, is IgnitorDsl.Neg -> dsl.childNodes().all { isScalar(it) }
        else -> false
    }

    /**
     * The work a tree renders: one unit per node that is a pass over the block, an Eq counting
     * its sections and a filter its passes, and the scalar leaves (a Constant, a Param, Freq) counting nothing on either
     * side (a fused section hides its scalars inside EqSection, a standalone filter shows them
     * as children; counting them would bias the two sides). A rule that forks a shared filter
     * into two parents keeps the node count flat and adds a section, which this sees and a
     * sample comparison cannot (the fork renders the same bits twice).
     */
    fun workCount(dsl: IgnitorDsl): Int {
        var n = 0
        val seen = mutableListOf<IgnitorDsl>()

        fun walk(node: Any?) {
            when (node) {
                is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> {}

                is IgnitorDsl -> {
                    // each INSTANCE once: a shared node renders once (memoized), a fork is two
                    if (seen.any { it === node }) {
                        return
                    }

                    seen.add(node)
                    n += when (node) {
                        // arithmetic over scalars only is one value per block, not a pass (the
                        // reciprocal a divide folds into an Affine's coefficient is such a node)
                        is IgnitorDsl.Times, is IgnitorDsl.Plus, is IgnitorDsl.Minus, is IgnitorDsl.Div, is IgnitorDsl.Neg ->
                            if (node.childNodes().all { isScalar(it) }) 0 else 1
                        is IgnitorDsl.Eq -> node.sections.size
                        // a filter with passes = N renders N cascaded stages, which the pass
                        // expands into N sections: the same work before and after
                        is IgnitorDsl.Lowpass -> node.passes.coerceAtLeast(1)
                        is IgnitorDsl.Highpass -> node.passes.coerceAtLeast(1)
                        else -> 1
                    }

                    for (m in node.javaClass.methods) {
                        if (m.parameterCount != 0 || !m.name.startsWith("get")) {
                            continue
                        }

                        val v = runCatching { m.invoke(node) }.getOrNull()

                        if (v is IgnitorDsl || v is List<*>) {
                            walk(v)
                        }
                    }
                }

                is List<*> -> node.forEach { walk(it) }
                else -> {}
            }
        }

        walk(dsl)

        return n
    }

    fun assertParity(seed: Int, authored: IgnitorDsl, optimized: IgnitorDsl) {
        val rngA = Random(seed)
        val rngB = Random(seed)
        val a = authored.toExciter(null, random = rngA)
        val b = optimized.toExciter(null, random = rngB)
        val ca = ctx(rngA)
        val cb = ctx(rngB)
        val bufA = AudioBuffer(blockFrames)
        val bufB = AudioBuffer(blockFrames)

        withClue("seed $seed: block-constant survives") { b.isBlockConstant shouldBe a.isBlockConstant }

        for (f in listOf(220.0, 440.0)) {
            repeat(blocks) { block ->
                val offset = if (block == 0) 37 else 0

                ca.updateOffsetAndLength(offset, blockFrames - offset)
                cb.updateOffsetAndLength(offset, blockFrames - offset)
                a.generate(bufA, f, ca)
                b.generate(bufB, f, cb)

                val scale = scaleOf(bufA, bufB, offset, blockFrames)

                for (i in offset until blockFrames) {
                    withClue("seed $seed freq $f block $block sample $i: ${bufA[i]} vs ${bufB[i]} (scale $scale)") {
                        withinParity(bufA[i], bufB[i], scale) shouldBe true
                    }
                }

                ca.voiceElapsedFrames += blockFrames
                cb.voiceElapsedFrames += blockFrames
            }
        }
    }

    "generated graphs render within the margin under the pass, and the pass keeps its laws" {
        val registry = IgnitorRegistry()
        val stats = Stats()

        for (seed in 1..trees) {
            val r = Random(seed)
            val authored = tree(r, depth = 1 + r.nextInt(6), pool = mutableListOf(), stats = stats)
            val optimized = authored.optimize()

            withClue("seed $seed: idempotent") { optimized.optimize() shouldBe optimized }
            withClue("seed $seed: never grows (work units) ${workCount(authored)} -> ${workCount(optimized)}\nAUTHORED $authored\nOPTIMIZED $optimized") { (workCount(optimized) <= workCount(authored)) shouldBe true }
            // Distinct names in first-occurrence order, the contract consumers rely on (the passes
            // expansion repeats a section's Param per section, which they dedupe by name).
            withClue("seed $seed: parameters survive") {
                mutableListOf<IgnitorDsl.Param>().also { optimized.collectParams(it) }.map { it.name }.distinct() shouldBe
                    mutableListOf<IgnitorDsl.Param>().also { authored.collectParams(it) }.map { it.name }.distinct()
            }

            assertParity(seed, authored, optimized)
            registry.register("fuzz-$seed", authored)
        }

        withClue("the registry swallowed a failing optimize() during the run") { registry.optimizerFailures shouldBe 0 }

        // The corpus contains what it claims to; otherwise a law is proven on nothing.
        withClue("diamonds ${stats.diamonds}") { (stats.diamonds >= 20) shouldBe true }
        withClue("reuses ${stats.reuses}") { (stats.reuses >= 50) shouldBe true }
        withClue("adversarial constants ${stats.adversarialConstants}") { (stats.adversarialConstants >= 100) shouldBe true }
        withClue("hints off ${stats.hintsOff}") { (stats.hintsOff >= 5) shouldBe true }
        withClue("affines ${stats.affines}") { (stats.affines >= 20) shouldBe true }
        withClue("swept filters ${stats.sweptFilters}") { (stats.sweptFilters >= 20) shouldBe true }
        withClue("humanized filters ${stats.humanizedFilters}") { (stats.humanizedFilters >= 20) shouldBe true }
    }
})
