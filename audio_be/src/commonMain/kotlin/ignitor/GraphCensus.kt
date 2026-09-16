/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.coercePasses

/**
 * What one note of a graph asks of the engine, counted from the tree the runtime lowers (the
 * OPTIMIZED tree, `IgnitorRegistry.optimized`), so that a cost can be put in proportion to the
 * work it bought. Three structural numbers, all per block of one voice:
 *
 * - [passes]: passes over the block, one per node that renders a loop; an `Eq` counts its
 *   sections, a filter its `passes`, a unison stack a pass per voice (its loop is voice-major); a
 *   pitch-mod node counts the mod buffer it renders; a hint and scalar-only arithmetic (one value
 *   per block) count nothing.
 * - [traffic]: reads plus writes of a block buffer per sample. A source writes once; an in-place
 *   pass reads and writes once each (2); a pass over two signals reads both and writes one (3); a
 *   signal coefficient makes the node render every coefficient through scratch (a read each); an
 *   oversampled shaper counts its upsample, its shaper loop, its decimation stages and the copy
 *   back; a pitch-mod node writes its mod block, and the ratio loop and the source's read of it
 *   follow; a shared node renders once into a memo, and EVERY consumer copies the memo out (2
 *   each, `MemoizingIgnitor`).
 * - [bytes]: state a note HOLDS between blocks (filter states and coefficients, oversampler
 *   history, a string's ring, a memo's cache), not the pooled scratch it borrows while rendering.
 *   A model with round numbers per kind, a lower bound where a count is not a literal (a partial
 *   count that is a slot).
 *
 * Sharing is by IDENTITY, as the runtime's build cache keys it: two structurally equal subtrees
 * that are different instances build twice and are counted twice. One thing the model does not
 * see: a subtree reused under a different pitch-mod or detune context is a separate instance at
 * runtime and counted here as shared, so such a graph is under-counted.
 *
 * A model, not a measurement: the weights are read off the runtime lowering
 * (`IgnitorDslRuntime`, `Ignitor.kt`, `EqCore`, `Oversampler`) and pinned by `GraphCensusSpec`
 * on hand-counted graphs. It exists for the song benchmark's `work` columns and the ledger; it
 * is never called on the render path.
 *
 * [params] are the voice's `oscParams`: a `Param` slot (the unison `voices`) is resolved through
 * them, then through its default, so a `unison(voices = 13)` at the pattern level counts 13.
 * [soundIndex] picks the variant of a [IgnitorDsl.Variants] the way the runtime does (the index
 * modulo the count); only that variant renders.
 */
data class GraphCensus(val passes: Int, val traffic: Int, val bytes: Int) {

    operator fun plus(other: GraphCensus): GraphCensus =
        GraphCensus(passes + other.passes, traffic + other.traffic, bytes + other.bytes)

    companion object {
        val NONE = GraphCensus(0, 0, 0)

        /** Two state doubles and four coefficients: one SVF section. */
        private const val SECTION_BYTES = 48

        /** A phase, an increment, a last value: what an oscillator keeps. */
        private const val SOURCE_BYTES = 64

        /** Per unison voice of a stack: phase, increment, gain, a drift lane. */
        private const val UNISON_VOICE_BYTES = 40

        /** A plucked string's ring: both string engines allocate 2 500 doubles per string, whatever the pitch. */
        private const val STRING_BYTES = 2_500 * 8

        /** The shimmer's ring: 96 000 doubles, two seconds at 48 kHz. */
        private const val SHIMMER_BYTES = 96_000 * 8

        /** The shimmer's loop per sample: the input, the ring write, up to eight grains reading two taps each, the output. */
        private const val SHIMMER_TRAFFIC = 19

        /** The half-band FIR reads nine samples of its input per output (`Oversampler.decimate2x`). */
        private const val DECIMATOR_TAPS = 9

        fun of(dsl: IgnitorDsl, blockFrames: Int = 128, params: Map<String, Double> = emptyMap(), soundIndex: Int = 0): GraphCensus {
            val walker = Walker(blockFrames, params, soundIndex)

            walker.count(dsl)

            return walker.visit(dsl)
        }

        /** A leaf or arithmetic over leaves that is one value per block: no pass, no traffic. */
        fun isScalar(dsl: IgnitorDsl): Boolean = Walker(128, emptyMap(), 0).isScalar(dsl)

        /** The buffer traffic per input sample of an [Oversampler] round trip at [factor], without the shaper's own work. */
        fun oversampleTraffic(factor: Int): Int {
            val stages = Oversampler.factorToStages(factor)

            if (stages == 0) {
                return 0
            }

            val f = 1 shl stages
            var traffic = 1 + f // the upsample reads one and writes f
            var len = f

            for (stage in 0 until stages) {
                // a decimation stage reads nine taps per output and writes half its input's length
                traffic += DECIMATOR_TAPS * (len / 2) + len / 2
                len /= 2
            }

            return traffic + 2 // the copy back
        }

        /** What an [Oversampler] at [factor] holds: 13 history doubles per stage, the prefix view, the last sample. */
        fun oversampleBytes(factor: Int): Int {
            val stages = Oversampler.factorToStages(factor)

            return if (stages == 0) 0 else stages * 13 * 8 + 39 * 8 + 8
        }
    }

    /** Reference counts by identity (the runtime's build cache keys by `===`, and `IgnitorDsl` nodes are data classes). */
    private class IdentityCounts {
        private val nodes = ArrayList<IgnitorDsl>()
        private val counts = ArrayList<Int>()

        private fun indexOf(node: IgnitorDsl): Int {
            for (i in nodes.indices) {
                if (nodes[i] === node) {
                    return i
                }
            }

            return -1
        }

        /** Counts one more reference and returns the new count. */
        fun increment(node: IgnitorDsl): Int {
            val i = indexOf(node)

            if (i < 0) {
                nodes.add(node)
                counts.add(1)

                return 1
            }

            counts[i] = counts[i] + 1

            return counts[i]
        }

        fun of(node: IgnitorDsl): Int {
            val i = indexOf(node)

            return if (i < 0) 0 else counts[i]
        }
    }

    private class Walker(
        private val blockFrames: Int,
        private val params: Map<String, Double>,
        private val soundIndex: Int,
    ) {
        private val refs = IdentityCounts()
        private val done = ArrayList<IgnitorDsl>()
        private val scalarMemo = ArrayList<IgnitorDsl>()
        private val scalarValue = ArrayList<Boolean>()

        /** The children that render: every child, except that one variant of a [IgnitorDsl.Variants] plays per note. */
        private fun renderedChildren(node: IgnitorDsl): List<IgnitorDsl> {
            if (node is IgnitorDsl.Variants) {
                if (node.children.isEmpty()) {
                    return emptyList()
                }

                return listOf(node.children[soundIndex.mod(node.children.size)])
            }

            return node.childNodes()
        }

        fun count(node: IgnitorDsl) {
            if (refs.increment(node) == 1) {
                for (child in renderedChildren(node)) {
                    count(child)
                }
            }
        }

        /** A leaf or arithmetic over leaves that is one value per block, memoized by identity (a shared scalar diamond would otherwise be walked exponentially). */
        fun isScalar(dsl: IgnitorDsl): Boolean {
            for (i in scalarMemo.indices) {
                if (scalarMemo[i] === dsl) {
                    return scalarValue[i]
                }
            }

            val scalar = when (dsl) {
                is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> true

                is IgnitorDsl.Times, is IgnitorDsl.Plus, is IgnitorDsl.Minus, is IgnitorDsl.Div, is IgnitorDsl.Mod,
                is IgnitorDsl.Min, is IgnitorDsl.Max, is IgnitorDsl.Pow, is IgnitorDsl.Neg, is IgnitorDsl.Abs,
                is IgnitorDsl.Sq, is IgnitorDsl.Sqrt, is IgnitorDsl.Exp, is IgnitorDsl.Log, is IgnitorDsl.Tanh,
                is IgnitorDsl.Sign, is IgnitorDsl.Floor, is IgnitorDsl.Ceil, is IgnitorDsl.Round, is IgnitorDsl.Frac,
                is IgnitorDsl.Recip, is IgnitorDsl.Bipolar, is IgnitorDsl.Unipolar, is IgnitorDsl.Clamp,
                is IgnitorDsl.Range, is IgnitorDsl.Lerp, is IgnitorDsl.Select, is IgnitorDsl.Affine,
                is IgnitorDsl.OptimizerHint -> dsl.childNodes().all { isScalar(it) }

                else -> false
            }

            scalarMemo.add(dsl)
            scalarValue.add(scalar)

            return scalar
        }

        fun visit(node: IgnitorDsl): GraphCensus {
            if (isScalar(node)) {
                return NONE
            }

            val shared = refs.of(node) > 1

            // a shared node renders once into a memo, and every consumer, the first included,
            // copies the memo out: a read and a write per consumer
            if (done.any { it === node }) {
                return GraphCensus(0, 2, 0)
            }

            done.add(node)

            val memo = if (shared) GraphCensus(0, 2, blockFrames * 8) else NONE

            return own(node) + children(node) + memo
        }

        private fun children(node: IgnitorDsl): GraphCensus {
            var sum = NONE

            for (child in renderedChildren(node)) {
                sum += visit(child)
            }

            return sum
        }

        /** A node whose coefficients are not all block-constant renders every coefficient through scratch: a read each. */
        private fun coefficientReads(vararg coefficients: IgnitorDsl): Int =
            if (coefficients.all { isScalar(it) }) 0 else coefficients.size

        private fun signals(vararg operands: IgnitorDsl): Int = operands.count { !isScalar(it) }

        /** A count slot: a literal, a `Param` through the voice's params or its default, else one. */
        private fun countOf(slot: IgnitorDsl): Int = when (slot) {
            is IgnitorDsl.Constant -> slot.value.toInt().coerceAtLeast(1)
            is IgnitorDsl.Param -> (params[slot.name] ?: slot.default).toInt().coerceAtLeast(1)
            else -> 1
        }

        private fun source(bytes: Int = SOURCE_BYTES) = GraphCensus(1, 1, bytes)

        private fun inPlace(bytes: Int = 0) = GraphCensus(1, 2, bytes)

        /** A unison stack: its loop is voice-major, a pass per voice, the first writing and the rest adding in. */
        private fun stack(voices: IgnitorDsl): GraphCensus {
            val n = countOf(voices)

            return GraphCensus(n, 2 * n - 1, SOURCE_BYTES + n * UNISON_VOICE_BYTES)
        }

        private fun shaped(oversample: Int, drive: Boolean): GraphCensus {
            // the shaper: its loop at the oversampled rate, the DC blocker and the soft cap in place
            val f = 1 shl Oversampler.factorToStages(oversample)
            val shape = GraphCensus(1, 2 * f + 2 + 2 + oversampleTraffic(oversample), oversampleBytes(oversample) + 24)

            return if (drive) shape + inPlace() else shape
        }

        private fun filter(passes: Int): GraphCensus {
            val n = coercePasses(passes)

            return GraphCensus(n, 2 * n, SECTION_BYTES * n)
        }

        private fun own(node: IgnitorDsl): GraphCensus = when (node) {
            // leaves: one value per block
            is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> NONE

            // sources: one pass that writes the block (silence fills it)
            is IgnitorDsl.Silence -> GraphCensus(1, 1, 0)
            is IgnitorDsl.Sawtooth, is IgnitorDsl.Square, is IgnitorDsl.Triangle, is IgnitorDsl.Ramp,
            is IgnitorDsl.Pulze, is IgnitorDsl.RawPulze, is IgnitorDsl.Zawtooth, is IgnitorDsl.Zamp,
            is IgnitorDsl.Impulse, is IgnitorDsl.WhiteNoise, is IgnitorDsl.PinkNoise, is IgnitorDsl.BrownNoise,
            is IgnitorDsl.Crackle, is IgnitorDsl.Dust, is IgnitorDsl.PerlinNoise, is IgnitorDsl.BerlinNoise -> source()

            // a sine with partials is one pass over a bank; every partial keeps a phase, an increment and a gain
            is IgnitorDsl.Sine -> {
                val partials = ((node.harmonics as? IgnitorDsl.Constant)?.value ?: 0.0) +
                        ((node.octaves as? IgnitorDsl.Constant)?.value ?: 0.0) +
                        ((node.suboctaves as? IgnitorDsl.Constant)?.value ?: 0.0)

                source(SOURCE_BYTES + partials.toInt() * 24)
            }

            is IgnitorDsl.SuperSaw -> stack(node.voices)
            is IgnitorDsl.SuperSine -> stack(node.voices)
            is IgnitorDsl.SuperSquare -> stack(node.voices)
            is IgnitorDsl.SuperTri -> stack(node.voices)
            is IgnitorDsl.SuperRamp -> stack(node.voices)

            // strings: the source writes, the ring is read and written per sample, a string per voice
            is IgnitorDsl.Pluck -> GraphCensus(1, 3, STRING_BYTES)
            is IgnitorDsl.SuperPluck -> {
                val n = countOf(node.voices)

                GraphCensus(n, 3 * n, STRING_BYTES * n)
            }

            // pointwise unary, in place
            is IgnitorDsl.Abs, is IgnitorDsl.Neg, is IgnitorDsl.Sq, is IgnitorDsl.Sqrt, is IgnitorDsl.Tanh,
            is IgnitorDsl.Exp, is IgnitorDsl.Log, is IgnitorDsl.Sign, is IgnitorDsl.Floor, is IgnitorDsl.Ceil,
            is IgnitorDsl.Round, is IgnitorDsl.Frac, is IgnitorDsl.Recip, is IgnitorDsl.Bipolar,
            is IgnitorDsl.Unipolar -> inPlace()

            // binary: in place over a scalar side, a scratch render and a third stream otherwise
            is IgnitorDsl.Plus -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Minus -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Times -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Div -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Mod -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Min -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Max -> GraphCensus(1, 1 + signals(node.left, node.right), 0)
            is IgnitorDsl.Pow -> GraphCensus(1, 1 + signals(node.base, node.exp), 0)

            // one pass; a signal coefficient makes the node render every coefficient through scratch
            is IgnitorDsl.Affine -> GraphCensus(1, 2 + coefficientReads(node.pre, node.mul, node.add), 0)
            is IgnitorDsl.Clamp -> GraphCensus(1, 2 + coefficientReads(node.lo, node.hi), 0)
            is IgnitorDsl.Range -> GraphCensus(1, 2 + coefficientReads(node.lo, node.hi), 0)

            // a lerp folds its weight only and always renders its second signal; a select has no fold and renders both branches
            is IgnitorDsl.Lerp -> GraphCensus(1, 3 + signals(node.t), 0)
            is IgnitorDsl.Select -> GraphCensus(1, 4, 0)

            // filters: a section per pass, in place, the passes clamped as the engine clamps them
            is IgnitorDsl.Lowpass -> filter(node.passes)
            is IgnitorDsl.Highpass -> filter(node.passes)
            is IgnitorDsl.Bandpass, is IgnitorDsl.Notch -> inPlace(SECTION_BYTES)
            is IgnitorDsl.OnePoleLowpass -> inPlace(16)

            // the fused equalizer: a serial section is a pass in place; a tap reads the input copy as
            // well as the chain (3), and the copy itself is a pass over the input (a read and a write)
            is IgnitorDsl.Eq -> {
                val taps = node.sections.count { it is IgnitorDsl.EqSection.RawTap }
                val serial = node.sections.size - taps
                val copy = if (taps > 0) GraphCensus(1, 2, blockFrames * 8) else NONE

                GraphCensus(node.sections.size, 2 * serial + 3 * taps, SECTION_BYTES * node.sections.size) + copy
            }

            // shapers
            is IgnitorDsl.Shape -> shaped(node.oversample, drive = false)
            is IgnitorDsl.Distort -> shaped(node.oversample, drive = true)
            is IgnitorDsl.Drive -> inPlace()
            is IgnitorDsl.Crush -> inPlace()
            is IgnitorDsl.Coarse -> inPlace(16)

            // envelopes and modulation effects, in place
            is IgnitorDsl.Adsr -> inPlace(64)
            is IgnitorDsl.Tremolo -> inPlace(32)
            is IgnitorDsl.Phaser -> inPlace(4 * 16 + 32)
            is IgnitorDsl.Shimmer -> GraphCensus(1, SHIMMER_TRAFFIC, SHIMMER_BYTES)

            // pitch modulation: the mod renders a block (1 write), the ratio loop reads it and writes
            // the ratios (2), and the source reads the ratio per sample (1); a detune is a constant
            // factor folded into the source's increment
            is IgnitorDsl.Vibrato, is IgnitorDsl.Accelerate, is IgnitorDsl.PitchEnvelope, is IgnitorDsl.PitchMod ->
                GraphCensus(1, 4, 64)

            is IgnitorDsl.Detune -> NONE

            // FM: the modulator renders (counted as a child), the ratio block is read and written, the carrier reads it
            is IgnitorDsl.Fm -> GraphCensus(1, 5, 64)

            // structure that leaves no trace at render time
            is IgnitorDsl.Variants, is IgnitorDsl.OptimizerHint -> NONE
        }
    }
}
