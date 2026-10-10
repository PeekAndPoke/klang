/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.hasClassicRange
import io.peekandpoke.klang.audio_bridge.coercePasses
import io.peekandpoke.klang.audio_bridge.coerceUnisonVoices

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
 * [params] are the voice's `ignitorParams`: a `Param` slot (the unison `voices`) is resolved through
 * them, then through its default, so a `unison(voices = 13)` at the pattern level counts 13.
 * [soundIndex] picks the variant of a [IgnitorDsl.Variants] the way the runtime does (the index
 * modulo the count); only that variant renders.
 */
data class GraphCensus(val passes: Int, val traffic: Int, val bytes: Int) {

    operator fun plus(other: GraphCensus): GraphCensus =
        GraphCensus(passes = passes + other.passes, traffic = traffic + other.traffic, bytes = bytes + other.bytes)

    companion object {
        val NONE = GraphCensus(passes = 0, traffic = 0, bytes = 0)

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
            val walker = Walker(blockFrames = blockFrames, params = params, soundIndex = soundIndex)

            walker.count(dsl)

            return walker.visit(dsl)
        }

        /** A leaf or arithmetic over leaves that is one value per block: no pass, no traffic. */
        fun isScalar(dsl: IgnitorDsl): Boolean = Walker(blockFrames = 128, params = emptyMap(), soundIndex = 0).isScalar(dsl)

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
                is IgnitorDsl.Recip, is IgnitorDsl.Clamp,
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
                return GraphCensus(passes = 0, traffic = 2, bytes = 0)
            }

            done.add(node)

            val memo = if (shared) GraphCensus(passes = 0, traffic = 2, bytes = blockFrames * 8) else NONE

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
            is IgnitorDsl.Constant -> coerceUnisonVoices(slot.value).coerceAtLeast(1)
            is IgnitorDsl.Param -> coerceUnisonVoices(params[slot.name]?.takeIf { it.isFinite() } ?: slot.default).coerceAtLeast(1)
            else -> 1
        }

        /**
         * An `oversample` knob as the whole factor the runtime reads at voice build: a `Constant` or
         * `Param` leaf through the one conversion, [Oversampler.factorOf]; anything else is 0, which is
         * what the runtime's leaf-only read gives it. A non-finite override reads as unset and takes the
         * slot's default, as the runtime's `Param` leaf does.
         */
        private fun factorOf(knob: IgnitorDsl): Int = when (knob) {
            is IgnitorDsl.Constant -> Oversampler.factorOf(knob.value)
            is IgnitorDsl.Param -> Oversampler.factorOf(params[knob.name]?.takeIf { it.isFinite() } ?: knob.default)
            else -> 0
        }

        private fun source(bytes: Int = SOURCE_BYTES) = GraphCensus(passes = 1, traffic = 1, bytes = bytes)

        /** A moving `phase` signal is read once per sample by each of [voices] voice loops; a scalar one costs nothing. */
        private fun phaseReads(phase: IgnitorDsl, voices: Int = 1) = if (isScalar(phase)) NONE else GraphCensus(passes = 0, traffic = voices, bytes = 0)

        /**
         * One bound of a tremolo off its classic range, `1 + floored * bound`: nothing when it folds (a scalar depth and
         * bound), else the multiply (in place over a scalar side, a third stream over two signals) and the add of 1.
         */
        private fun tremoloBound(depth: IgnitorDsl, bound: IgnitorDsl): GraphCensus =
            if (isScalar(depth) && isScalar(bound)) NONE else GraphCensus(passes = 1, traffic = 1 + signals(depth, bound), bytes = 0) + inPlace()

        private fun inPlace(bytes: Int = 0) = GraphCensus(passes = 1, traffic = 2, bytes = bytes)

        /** A unison stack: its loop is voice-major, a pass per voice, the first writing and the rest adding in. */
        private fun stack(voices: IgnitorDsl): GraphCensus {
            val n = countOf(voices)

            return GraphCensus(passes = n, traffic = 2 * n - 1, bytes = SOURCE_BYTES + n * UNISON_VOICE_BYTES)
        }

        private fun shaped(oversample: Int): GraphCensus {
            // the shaper: its loop at the oversampled rate, the DC blocker, and the output pass in place
            // (the soft cap on `Shape`, the copy out on the fused `Distort`, whose drive rides in the loop)
            val f = 1 shl Oversampler.factorToStages(oversample)

            return GraphCensus(passes = 1, traffic = 2 * f + 2 + 2 + oversampleTraffic(oversample), bytes = oversampleBytes(oversample) + 24)
        }

        /**
         * A filter's `passes` knob as the count the runtime reads at voice build: a `Constant` or `Param`
         * leaf through [coercePasses]; anything else is one pass, the runtime's leaf-only answer. A
         * non-finite override reads as unset and takes the slot's default, as the `Param` leaf does.
         */
        private fun passesOf(knob: IgnitorDsl): Int = when (knob) {
            is IgnitorDsl.Constant -> coercePasses(knob.value)
            is IgnitorDsl.Param -> coercePasses(params[knob.name]?.takeIf { it.isFinite() } ?: knob.default)
            else -> 1
        }

        private fun filter(passes: IgnitorDsl): GraphCensus {
            val n = passesOf(passes)

            return GraphCensus(passes = n, traffic = 2 * n, bytes = SECTION_BYTES * n)
        }

        private fun own(node: IgnitorDsl): GraphCensus = when (node) {
            // leaves: one value per block
            is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> NONE

            // sources: one pass that writes the block (silence fills it)
            is IgnitorDsl.Silence -> GraphCensus(passes = 1, traffic = 1, bytes = 0)
            is IgnitorDsl.WhiteNoise, is IgnitorDsl.PinkNoise, is IgnitorDsl.BrownNoise,
            is IgnitorDsl.Crackle, is IgnitorDsl.Dust, is IgnitorDsl.PerlinNoise, is IgnitorDsl.BerlinNoise,
            is IgnitorDsl.Sample -> source()

            // the periodic oscillators; a moving `phase` signal adds the read of its block (a scalar one moves the
            // accumulator once per block and costs nothing per sample)
            is IgnitorDsl.Saw -> source() + phaseReads(node.phase)
            is IgnitorDsl.Square -> source() + phaseReads(node.phase)
            is IgnitorDsl.Tri -> source() + phaseReads(node.phase)
            is IgnitorDsl.Ramp -> source() + phaseReads(node.phase)
            is IgnitorDsl.Pulze -> source() + phaseReads(node.phase)
            is IgnitorDsl.RawPulze -> source() + phaseReads(node.phase)
            is IgnitorDsl.Zawtooth -> source() + phaseReads(node.phase)
            is IgnitorDsl.Zamp -> source() + phaseReads(node.phase)
            is IgnitorDsl.Impulse -> source() + phaseReads(node.phase)

            // a sine with partials is one pass over a bank; every partial keeps a phase, an increment and a gain
            is IgnitorDsl.Sine -> {
                val partials = ((node.harmonics as? IgnitorDsl.Constant)?.value ?: 0.0) +
                        ((node.octaves as? IgnitorDsl.Constant)?.value ?: 0.0) +
                        ((node.suboctaves as? IgnitorDsl.Constant)?.value ?: 0.0)

                // coarse on purpose, as the bank's own one pass: every partial's loop reads a moving phase's block, but
                // the bank is charged one read (a partial count would rank phase-modulated banks against stacks)
                source(SOURCE_BYTES + partials.toInt() * 24) + phaseReads(node.phase)
            }

            is IgnitorDsl.SuperSaw -> stack(node.voices) + phaseReads(node.phase, countOf(node.voices))
            is IgnitorDsl.SuperSine -> stack(node.voices) + phaseReads(node.phase, countOf(node.voices))
            is IgnitorDsl.SuperSquare -> stack(node.voices) + phaseReads(node.phase, countOf(node.voices))
            is IgnitorDsl.SuperTri -> stack(node.voices) + phaseReads(node.phase, countOf(node.voices))
            is IgnitorDsl.SuperRamp -> stack(node.voices) + phaseReads(node.phase, countOf(node.voices))

            // strings: the source writes, the ring is read and written per sample, a string per voice
            is IgnitorDsl.Pluck -> GraphCensus(passes = 1, traffic = 3, bytes = STRING_BYTES)
            is IgnitorDsl.SuperPluck -> {
                val n = countOf(node.voices)

                GraphCensus(passes = n, traffic = 3 * n, bytes = STRING_BYTES * n)
            }

            // pointwise unary, in place
            is IgnitorDsl.Abs, is IgnitorDsl.Neg, is IgnitorDsl.Sq, is IgnitorDsl.Sqrt, is IgnitorDsl.Tanh,
            is IgnitorDsl.Exp, is IgnitorDsl.Log, is IgnitorDsl.Sign, is IgnitorDsl.Floor, is IgnitorDsl.Ceil,
            is IgnitorDsl.Round, is IgnitorDsl.Frac, is IgnitorDsl.Recip -> inPlace()

            // binary: in place over a scalar side, a scratch render and a third stream otherwise
            is IgnitorDsl.Plus -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Minus -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Times -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Div -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Mod -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Min -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Max -> GraphCensus(passes = 1, traffic = 1 + signals(node.left, node.right), bytes = 0)
            is IgnitorDsl.Pow -> GraphCensus(passes = 1, traffic = 1 + signals(node.base, node.exp), bytes = 0)

            // one pass; a signal coefficient makes the node render every coefficient through scratch
            is IgnitorDsl.Affine -> GraphCensus(passes = 1, traffic = 2 + coefficientReads(node.pre, node.mul, node.add), bytes = 0)
            is IgnitorDsl.Clamp -> GraphCensus(passes = 1, traffic = 2 + coefficientReads(node.lo, node.hi), bytes = 0)
            is IgnitorDsl.Range -> GraphCensus(passes = 1, traffic = 2 + coefficientReads(node.from, node.to), bytes = 0)

            // a lerp folds its weight only and always renders its second signal; a select has no fold and renders both branches
            is IgnitorDsl.Lerp -> GraphCensus(passes = 1, traffic = 3 + signals(node.t), bytes = 0)
            is IgnitorDsl.Select -> GraphCensus(passes = 1, traffic = 4, bytes = 0)

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
                val copy = if (taps > 0) GraphCensus(passes = 1, traffic = 2, bytes = blockFrames * 8) else NONE

                GraphCensus(passes = node.sections.size, traffic = 2 * serial + 3 * taps, bytes = SECTION_BYTES * node.sections.size) + copy
            }

            // shapers
            is IgnitorDsl.Shape -> shaped(factorOf(node.oversample))
            // since phase 3 step 4 (D2) one fused stage, not a drive pass plus a shaper
            is IgnitorDsl.Distort -> shaped(factorOf(node.oversample))
            is IgnitorDsl.Drive -> inPlace()
            is IgnitorDsl.Crush -> inPlace()
            is IgnitorDsl.Coarse -> inPlace(16)

            // envelopes and modulation effects, in place
            is IgnitorDsl.Adsr -> inPlace(64)
            // the tremolo is a composition: its LFO oscillator writes a block, `range(1 - depth, 1)` maps it
            // in place, and the multiply reads the signal and the gain and writes one. Every depth is floored
            // (`1 - max(depth, 0)`); a control-rate depth folds that to one value per block, a signal depth adds
            // the floor and the `1 - depth` pass (each in place over a scalar side) and makes the range read both bounds
            // Off the classic range `(-1, 0)` the bounds are `1 + floored * from` and `1 + floored * to`; only what runs
            // is counted: a signal depth's floor (in place, memoized for its two readers: a copy out each), each bound
            // that does not fold, the range (reading both bounds when either is a signal) and the multiply.
            is IgnitorDsl.Tremolo -> if (!node.hasClassicRange()) {
                val floor = if (isScalar(node.depth)) NONE else inPlace() + GraphCensus(passes = 0, traffic = 2 * 2, bytes = blockFrames * 8)
                val from = tremoloBound(depth = node.depth, bound = node.rangeFrom)
                val to = tremoloBound(depth = node.depth, bound = node.rangeTo)
                val boundsScalar = from == NONE && to == NONE

                source() + floor + from + to + GraphCensus(passes = 1, traffic = if (boundsScalar) 2 else 2 + 2, bytes = 0) + GraphCensus(passes = 1, traffic = 3, bytes = 0)
            } else if (isScalar(node.depth)) {
                source() + GraphCensus(passes = 1, traffic = 2, bytes = 0) + GraphCensus(passes = 1, traffic = 3, bytes = 0)
            } else {
                source() + inPlace() + inPlace() + GraphCensus(passes = 1, traffic = 2 + 2, bytes = 0) + GraphCensus(passes = 1, traffic = 3, bytes = 0)
            }
            is IgnitorDsl.Phaser -> inPlace(4 * 16 + 32)
            is IgnitorDsl.Shimmer -> GraphCensus(passes = 1, traffic = SHIMMER_TRAFFIC, bytes = SHIMMER_BYTES)

            // pitch modulation: the mod renders a block (1 write), the ratio loop reads it and writes
            // the ratios (2), and the source reads the ratio per sample (1); a detune is a constant
            // factor folded into the source's increment
            is IgnitorDsl.Vibrato, is IgnitorDsl.Accelerate, is IgnitorDsl.PitchEnvelope, is IgnitorDsl.PitchMod,
            is IgnitorDsl.PitchModSemitones ->
                GraphCensus(passes = 1, traffic = 4, bytes = 64)

            is IgnitorDsl.Detune -> NONE

            // FM: the modulator renders (counted as a child), the ratio block is read and written, the carrier reads it
            is IgnitorDsl.Fm -> GraphCensus(passes = 1, traffic = 5, bytes = 64)

            // structure that leaves no trace at render time
            is IgnitorDsl.Variants, is IgnitorDsl.OptimizerHint -> NONE
        }
    }
}
