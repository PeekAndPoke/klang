/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Pure `IgnitorDsl -> IgnitorDsl` rewrite pass: collapses filter chains that were authored as
 * separate nodes into fused [IgnitorDsl.Eq] sections, without changing what the graph renders.
 *
 * ## The rule that makes this safe (revised 2026-09-15)
 *
 * **Only ADJACENT nodes combine, only under linear algebra, and the result renders within
 * [OPTIMIZER_PARITY] of the authored graph.** The pass never reorders across a node it does
 * not recognise and never crosses a nonlinear one: `sine().bandpass().distort().lowpass()`
 * yields two independent one-section Eqs, because the distort between them is a wall. A shared
 * subtree (refcount above one) is never absorbed.
 *
 * Until 2026-09-15 the promise was bit-identity, which forbade any rewrite that moves a
 * multiply. The maintainer replaced it with a margin: the rendered samples of the optimized
 * graph may differ from the authored graph's by rounding, at most [OPTIMIZER_PARITY] of the
 * block's scale (-240 dB, no musical meaning), NaN for NaN and infinity for infinity. That admits folding
 * block-constant arithmetic (`x.mul(2).mul(2).add(10)` as one affine pass) and folding an
 * affine into a neighbouring linear node's gain, which bit-identity could not. Every rule is
 * held to the margin by `IgnitorDslOptimizerRenderSpec` (the shapes people write, the warmup
 * vocabulary) and `IgnitorDslOptimizerFuzzSpec` (generated graphs, adversarial constants).
 *
 * ## Two passes
 *
 * 1. **Refcount** by reference identity: how many times does each node instance appear as a
 *    child anywhere in the tree? Shared subtrees (`let t = ...; t.lowpass(a).add(t.lowpass(b))`)
 *    must not be forked, so a node with refcount > 1 is never absorbed into a fusion.
 * 2. **Post-order rewrite** with an identity memo, so a shared subtree is rewritten ONCE and
 *    the result stays shared (`===` survives). Untouched subtrees return the SAME instance,
 *    which keeps `MemoizingIgnitor`'s identity-keyed caching intact downstream.
 *
 * Rule guards consult the refcount of the ORIGINAL (pre-rewrite) child, never the freshly built
 * node: a fresh Eq is always refcount-1, so consulting it would happily fork a shared
 * intermediate and silently double the CPU it was meant to save.
 *
 * ## Scope today, and what is deliberately left
 *
 * This pass implements serial filter fusion (R1) and the arithmetic fold into [IgnitorDsl.Affine]
 * (R2, 2026-09-15; an Affine is a wall for R1 until the Eq gain fold lands). It is intentionally
 * not a complete optimizer;
 * see `docs/tasks/future/ignitor-optimizer-open-items.md` for the catalogue of cases it does NOT yet
 * claim, each with the reason. The kill switch [IgnitorDsl.OptimizerHint] disables it for a
 * whole graph so any suspicion can be settled by ear.
 */
/**
 * The margin an optimized graph may differ from its authored graph by, per sample, relative to
 * the block's scale (its loudest sample, at most full scale): rounding, not sound. NaN must stay NaN and an
 * infinity an infinity. Relative to the block and not to the sample itself since 2026-09-15: a
 * fold that is inexact by an ulp (a divide as a multiply by the reciprocal) passes through a
 * filter and lands next to a zero crossing, where the sample is tiny and the deviation is not,
 * though it is still -240 dB below the loudest sample of that block.
 */
const val OPTIMIZER_PARITY: Double = 1e-12

fun IgnitorDsl.optimize(): IgnitorDsl {
    val scan = scanTree()

    if (scan.optimizerOff) {
        return this
    }

    return rewrite(this, scan.refCounts, IdentityMemo())
}

// ═════════════════════════════════════════════════════════════════════════════
// Pass 0 — reference counting (identity, not structure)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Counts how often each node INSTANCE appears as a child in the tree.
 *
 * Identity-keyed on purpose. Two structurally equal nodes can be independent oscillators with
 * their own phase and their own RNG draws, so treating them as one would fuse a tap of B onto A
 * (audibly wrong, and invisible to a structural spec). Common Kotlin has no IdentityHashMap, so
 * this uses the linear-scan idiom already used by `IgnitorBuildCache`; trees are tens of nodes,
 * not thousands, and this runs once per registration rather than per voice.
 */
private fun IgnitorDsl.scanTree(): TreeScan {
    val counts = RefCounts()
    var optimizerOff = this is IgnitorDsl.OptimizerHint && this.on == 0
    val stack = ArrayDeque<IgnitorDsl>()
    stack.addLast(this)

    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        for (child in node.childNodes()) {
            // Only descend the FIRST time an instance is seen; later sightings just bump the
            // count. Without this a diamond would be walked exponentially — and this walk runs
            // on the JS audio thread (worklet onmessage -> register), where an exponential
            // blow-up is a dead output rather than a slow build. The kill-switch scan rides
            // along on THIS traversal for exactly that reason: one visit per node, once.
            if (counts.increment(child) == 1) {
                if (child is IgnitorDsl.OptimizerHint && child.on == 0) {
                    optimizerOff = true
                }
                stack.addLast(child)
            }
        }
    }

    return TreeScan(refCounts = counts, optimizerOff = optimizerOff)
}

/** One traversal's worth of facts about a tree. */
private class TreeScan(val refCounts: RefCounts, val optimizerOff: Boolean)

/** Identity-keyed multiset over DSL nodes. Linear scan; see [scanTree] for why. */
private class RefCounts {
    private val nodes = mutableListOf<IgnitorDsl>()
    private val counts = mutableListOf<Int>()

    /** Bumps [node]'s count and returns the NEW count. */
    fun increment(node: IgnitorDsl): Int {
        for (i in nodes.indices) {
            if (nodes[i] === node) {
                counts[i] = counts[i] + 1
                return counts[i]
            }
        }
        nodes.add(node)
        counts.add(1)
        return 1
    }

    /** How many times [node] appears as a child. A tree root reports 0. */
    fun of(node: IgnitorDsl): Int {
        for (i in nodes.indices) {
            if (nodes[i] === node) {
                return counts[i]
            }
        }
        return 0
    }

    /**
     * True when [node] may be absorbed into a fusion: it feeds exactly one consumer, so
     * collapsing it cannot fork a subtree that something else still depends on.
     */
    fun isExclusivelyOwned(node: IgnitorDsl): Boolean = of(node) <= 1
}

// ═════════════════════════════════════════════════════════════════════════════
// Pass 1 — post-order rewrite
// ═════════════════════════════════════════════════════════════════════════════

/** Identity-keyed old -> new memo, so a shared subtree rewrites once and stays shared. */
private class IdentityMemo {
    private val from = mutableListOf<IgnitorDsl>()
    private val to = mutableListOf<IgnitorDsl>()

    fun getOrNull(key: IgnitorDsl): IgnitorDsl? {
        for (i in from.indices) {
            if (from[i] === key) {
                return to[i]
            }
        }
        return null
    }

    fun put(key: IgnitorDsl, value: IgnitorDsl) {
        from.add(key)
        to.add(value)
    }
}

private fun rewrite(node: IgnitorDsl, refCounts: RefCounts, memo: IdentityMemo): IgnitorDsl {
    memo.getOrNull(node)?.let { return it }

    // Children first: a rule sees already-rewritten inputs, so a chain collapses bottom-up in
    // one traversal (lowpass(lowpass(x)) becomes a 2-section Eq, not two nested 1-section Eqs).
    val oldChildren = node.childNodes()
    val newChildren = oldChildren.map { rewrite(it, refCounts, memo) }

    val childrenUnchanged = oldChildren.indices.all { oldChildren[it] === newChildren[it] }
    val rebuilt = if (childrenUnchanged) node else node.withChildNodes(newChildren)

    // An `on != 0` hint carries no information once the scan has resolved the switch, and
    // leaving it in place would act as a fusion WALL mid-chain: flipping 0 -> 1 would then
    // render a tree that production never renders, which defeats the whole point of the A/B.
    if (rebuilt is IgnitorDsl.OptimizerHint) {
        val dissolved = rebuilt.inner
        memo.put(node, dissolved)
        return dissolved
    }

    val result = foldArithmetic(fuseSerialFilters(rebuilt, node, refCounts), node, refCounts)

    memo.put(node, result)
    return result
}

// ═════════════════════════════════════════════════════════════════════════════
// R1 — serial filter fusion
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Collapses a fusible filter into its inner [IgnitorDsl.Eq], or opens a new one.
 *
 * Because the walk is post-order, the inner node has already been rewritten, so an adjacent run
 * of filters folds into a single Eq one node at a time as the walk unwinds.
 *
 * @param original the PRE-rewrite node. The sharing guard must consult the ORIGINAL inner's
 * refcount: the rewritten one is freshly built and therefore always refcount-1, which would
 * defeat the guard entirely.
 */
private fun fuseSerialFilters(
    node: IgnitorDsl,
    original: IgnitorDsl,
    refCounts: RefCounts,
): IgnitorDsl {
    val sections = node.asFusibleSections() ?: return node
    val inner = node.filterInner() ?: return node

    // Read the pre-rewrite inner by NAME, not by child position: a constructor reorder would
    // otherwise hand the guard a cutoff modulator to refcount, and fork a shared Eq silently.
    //
    // Then see THROUGH any dissolved optimizer hints. `x.optimizer(1)` vanishes during the
    // rewrite, so the node the fresh Eq really came from is below it — and a hint is always
    // refcount-1, so consulting it would wave a shared subtree straight past the guard and
    // duplicate its per-block cost. Every link must be exclusively owned, not just the last.
    var originalInner = original.filterInner() ?: return node
    while (originalInner is IgnitorDsl.OptimizerHint && refCounts.isExclusivelyOwned(originalInner)) {
        originalInner = originalInner.inner
    }

    return when {
        // Continue an Eq that this filter sits directly on top of. Guarded on the ORIGINAL
        // inner: if that Eq is shared, appending would fork it and duplicate every section.
        inner is IgnitorDsl.Eq && refCounts.isExclusivelyOwned(originalInner) ->
            inner.copy(sections = inner.sections + sections)

        // Otherwise start a fresh Eq. A single filter converting to a one-section Eq is a
        // measured win on the deployment platform (Node 0.44 vs 0.74 us/block) and a wash on
        // the JVM, so standalone conversion is ON. See the D2a bake-off record in the plan.
        else -> IgnitorDsl.Eq(inner = inner, sections = sections)
    }
}

/**
 * The staggered per-stage q for section k of a passes-N cascade, RELATIVE to the user q:
 * the Butterworth pole ladder `√2 / (2·cos((2k+1)π/(4N)))` (identically 1.0 at N = 1) —
 * the same math as the engine's `butterworthQLadder`, duplicated here because the bridge
 * cannot depend on audio_be; `PassesLadderParitySpec` pins the two against each other.
 */
private fun passesLadderRel(k: Int, n: Int): Double =
    sqrt(2.0) / (2.0 * cos((2.0 * k + 1.0) * PI / (4.0 * n)))

/**
 * Expands one filter node into its [passes] cascade sections, each carrying its staggered q.
 *
 * [q] is passed in rather than re-derived from the node: the two call sites already hold it,
 * and a `when (this)` with an `else` arm here would silently substitute a default q for any
 * node type added later.
 */
private fun expandPasses(
    passes: IgnitorDsl.Constant,
    q: IgnitorDsl,
    build: (stageQ: IgnitorDsl) -> IgnitorDsl.EqSection,
): List<IgnitorDsl.EqSection> {
    val n = coercePasses(passes.value)
    if (n == 1) {
        return listOf(build(q))
    }
    return List(n) { k ->
        val rel = passesLadderRel(k, n)
        val stageQ = when {
            // Odd N has a middle stage at EXACTLY 1.0 (sqrt(2)/(2*cos(pi/4)); never 1.0 by
            // rounding). Passing q through unwrapped there mirrors the chained door's
            // `scaledBy(1.0) -> this` (IgnitorFilters.kt), so both doors build the SAME graph
            // rather than merely the same numbers.
            //
            // THESE TWO ARMS ARE A PAIR — keep them or remove them together. They live in
            // different modules and nothing pairs them structurally, so it is worth spelling
            // out: `safeOut(x * 1.0) == x` only for FINITE x. With a non-finite q, an
            // unwrapped q reaches `computeSvfCoeffs` raw and takes its Butterworth 0.7071
            // fallback, while `Times(q, Constant(1.0))` becomes `safeOut(NaN) = 0.0` and
            // takes the 0.1 q floor. Drop one arm alone and the middle stage of an odd-N
            // cascade is a different filter on the two doors. `IgnitorDslOptimizerRenderSpec`
            // drives NaN and +/-Inf through passes = 3 for exactly this, and BOTH mutations
            // were run on 2026-09-19: each alone turns that row red. Only ODD N touches this
            // pair, because only odd N has a ladder factor of exactly 1.0; the passes = 2 half
            // of that row guards the other invariant, that a non-literal q goes through `times`
            // on both doors.
            //
            // Where a non-finite q comes from, since 2026-09-19: NOT from `oscParams`. The
            // `IgnitorDsl.Param` leaf reads a non-finite OVERRIDE as unset and falls back to
            // the slot's default (`IgnitorDslRuntime`). Two routes keep this pair live: an
            // authored `IgnitorDsl.Param` whose DEFAULT is non-finite (a default is the
            // instrument's declaration and is not scrubbed), and arithmetic in the q expression,
            // since `Plus` and `Minus` are clamp-free by contract and two finite operands can
            // overflow to an infinity. A `ParamIgnitor` that engine code constructs directly
            // would be a third, but no production caller does that today (`scaledBy` has two
            // callers, both fed `q.noMod()`).
            rel == 1.0 -> q
            q is IgnitorDsl.Constant -> IgnitorDsl.Constant(q.value * rel)
            else -> IgnitorDsl.Times(q, IgnitorDsl.Constant(rel))
        }
        build(stageQ)
    }
}

/**
 * Maps a chained filter node to the equivalent EQ section list, or null when it must NOT fuse.
 *
 * Refuses when `analog` is anything but a literal zero: a non-zero analog switches SvfLPF/SvfHPF
 * to their state-dependent saturating branch, which is deliberate nonlinear character that
 * `EqCore` does not implement. A Param-backed analog is refused too, because an osc-param could
 * turn saturation on per note and the decision is made here, once, at registration.
 *
 * Refuses on the same grounds when the filter carries a CUTOFF ENVELOPE (`env` anything but a
 * literal zero) or the per-voice `humanize` lane: an `EqSection` has neither field, so fusing
 * one would silently drop the sweep or the tolerance. `env` takes the Param-backed refusal too,
 * for the same reason `analog` does: a slot could switch the envelope on per note, and this
 * decision is made once, at registration. `humanize` is structural, so a plain `!humanize`
 * answers it.
 *
 * **That refusal is CONSERVATIVE.** A filter carrying an `env` slot (`classic()`'s `lpf.env`)
 * never fuses, whether or not any note ever writes the slot, because the
 * decision is made at registration and a `Param` could be written per note. The alternative is
 * deciding per note-on, which is the BUILD, not the optimizer, and which would mean carrying both
 * a fused and an unfused tree. What it costs is the Eq fusion on the classic tail, which is real
 * but is not correctness; measure it before trading the simple rule away.
 *
 * Refuses a filter whose `passes` is not a `Constant` (phase 3 step 5 made it a knob): the
 * section count is decided here, once, and a slot could change it per note. A `Constant` count
 * expands through `coercePasses`, the same rounding and bounds the runtime reads it with.
 *
 * `OnePoleLowpass` (the `onepole()` door) is absent by design: there is no one-pole section
 * type, and substituting an SVF would change the sound.
 */
private fun IgnitorDsl.asFusibleSections(): List<IgnitorDsl.EqSection>? = when (this) {
    // C5 (the D6 rule): `passes = N` EXPANDS into N sections with the staggered q ladder in
    // the SAME commit the field landed — there is never a window where a passes-2 filter
    // fuses as one section and silently loses 12 dB/oct. A Constant q folds per stage; a
    // modulated q gets a Times wrapper so every stage keeps sweeping coherently.
    is IgnitorDsl.Lowpass -> {
        val count = passes

        if (count is IgnitorDsl.Constant && analog.isLiteralZero() && env.isLiteralZero() && !humanize) {
            expandPasses(count, q) { stageQ -> IgnitorDsl.EqSection.Lowpass(freq, stageQ) }
        } else {
            null
        }
    }

    is IgnitorDsl.Highpass -> {
        val count = passes

        if (count is IgnitorDsl.Constant && analog.isLiteralZero() && env.isLiteralZero() && !humanize) {
            expandPasses(count, q) { stageQ -> IgnitorDsl.EqSection.Highpass(freq, stageQ) }
        } else {
            null
        }
    }

    is IgnitorDsl.Bandpass ->
        if (analog.isLiteralZero() && env.isLiteralZero() && !humanize) {
            listOf(IgnitorDsl.EqSection.Bandpass(freq, q))
        } else {
            null
        }

    is IgnitorDsl.Notch ->
        if (analog.isLiteralZero() && env.isLiteralZero() && !humanize) {
            listOf(IgnitorDsl.EqSection.Notch(freq, q))
        } else {
            null
        }

    else -> null
}

/** The audio-carrying child of a fusible filter, or null when this is not one. */
private fun IgnitorDsl.filterInner(): IgnitorDsl? = when (this) {
    is IgnitorDsl.Lowpass -> inner
    is IgnitorDsl.Highpass -> inner
    is IgnitorDsl.Bandpass -> inner
    is IgnitorDsl.Notch -> inner
    else -> null
}

/**
 * True only for a structural literal zero.
 *
 * Structural on purpose: a `Param` that happens to default to 0.0 can be overridden per note via
 * `oscparam`, so it is not a compile-time zero and must not fuse.
 */
private fun IgnitorDsl.isLiteralZero(): Boolean = this is IgnitorDsl.Constant && value == 0.0


// ═════════════════════════════════════════════════════════════════════════════
// R2 — the arithmetic fold (2026-09-15)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Whether a tree is a control-rate scalar: a [IgnitorDsl.Constant], a [IgnitorDsl.Param],
 * [IgnitorDsl.Freq], or pointwise arithmetic over those. Such a tree is a coefficient the runtime
 * reads once per block; anything else (a source, a filter, an envelope) is a signal.
 */
private fun IgnitorDsl.isControlRate(): Boolean = when (this) {
    is IgnitorDsl.Constant, is IgnitorDsl.Param, IgnitorDsl.Freq -> true
    is IgnitorDsl.Plus -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Minus -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Times -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Div -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Min -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Max -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Pow -> base.isControlRate() && exp.isControlRate()
    is IgnitorDsl.Mod -> left.isControlRate() && right.isControlRate()
    is IgnitorDsl.Neg -> inner.isControlRate()
    is IgnitorDsl.Abs -> inner.isControlRate()
    is IgnitorDsl.Sq -> inner.isControlRate()
    is IgnitorDsl.Sqrt -> inner.isControlRate()
    is IgnitorDsl.Exp -> inner.isControlRate()
    is IgnitorDsl.Log -> inner.isControlRate()
    is IgnitorDsl.Recip -> inner.isControlRate()
    is IgnitorDsl.Sign -> inner.isControlRate()
    is IgnitorDsl.Tanh -> inner.isControlRate()
    is IgnitorDsl.Bipolar -> inner.isControlRate()
    is IgnitorDsl.Unipolar -> inner.isControlRate()
    is IgnitorDsl.Floor -> inner.isControlRate()
    is IgnitorDsl.Ceil -> inner.isControlRate()
    is IgnitorDsl.Round -> inner.isControlRate()
    is IgnitorDsl.Frac -> inner.isControlRate()
    is IgnitorDsl.Clamp -> inner.isControlRate() && lo.isControlRate() && hi.isControlRate()
    is IgnitorDsl.Lerp -> left.isControlRate() && right.isControlRate() && t.isControlRate()
    is IgnitorDsl.Affine -> inner.isControlRate() && pre.isControlRate() && mul.isControlRate() && add.isControlRate()
    else -> false
}

/**
 * Whether a scalar operand may move to the RIGHT of the signal in the folded node. `collectParams`
 * order (first occurrence) is a contract the UI keys on, and an Affine lists its signal's params
 * before its coefficients': a left-hand scalar that carries a Param would change that order, so
 * it stays where it was written; a literal carries none and folds either way.
 */
private fun IgnitorDsl.carriesNoParams(): Boolean = mutableListOf<IgnitorDsl.Param>().also { collectParams(it) }.isEmpty()

/** The absent pre-add or add: `Constant(-0.0)`, the bitwise identity of the add (see [IgnitorDsl.Affine]). */
private val ABSENT: IgnitorDsl = IgnitorDsl.Constant(-0.0)

/** The absent-addend test lives on [IgnitorDsl.Affine] itself: the voice build reads the same encoding. */
private fun IgnitorDsl.isAbsent(): Boolean = IgnitorDsl.Affine.isAbsentAddend(this)

/** A finite literal multiply coefficient, the only kind a run composes. */
private fun IgnitorDsl.literalOrNull(): Double? = (this as? IgnitorDsl.Constant)?.value?.takeIf { it.isFinite() }

/**
 * The output of a clamping op is bounded by `SAFE_MAX`; an attenuating run composes over it only.
 * Every `Times` path clamps; an `Affine` clamps at its multiply and adds AFTER it, so only an
 * Affine without an add is bounded (`2x + 8e15` is not).
 */
private fun IgnitorDsl.isClamped(): Boolean =
    (this is IgnitorDsl.Affine && add.isAbsent()) || this is IgnitorDsl.Times || this is IgnitorDsl.Div

/** Sees through dissolved hints (as [fuseSerialFilters] does) and answers whether [original] may be absorbed. */
private fun RefCounts.owns(original: IgnitorDsl): Boolean {
    var node = original

    while (node is IgnitorDsl.OptimizerHint && isExclusivelyOwned(node)) {
        node = node.inner
    }

    return isExclusivelyOwned(node)
}

/**
 * Folds block-constant arithmetic into [IgnitorDsl.Affine], one node per authored shape
 * `x [.add(p)] .mul(m) [.add(a)]`, on the post-order walk: a `Times` with exactly one
 * control-rate side folds its signal side, absorbing a directly preceding constant add or
 * subtract as the pre-add and composing a literal run of multiplies where the chain's clamp
 * cannot differ from the fold's; a `Plus` with a control-rate side fills an owned Affine's
 * absent add. Never across an addition, never without a multiply, never a modulated operand,
 * never a shared node (the guard consults the ORIGINAL child's refcount, like R1).
 *
 * [original] is the pre-rewrite node; its children correspond to [node]'s by position.
 */
private fun foldArithmetic(node: IgnitorDsl, original: IgnitorDsl, refCounts: RefCounts): IgnitorDsl = when (node) {
    is IgnitorDsl.Times -> {
        val orig = original as? IgnitorDsl.Times
        val leftScalar = node.left.isControlRate()
        val rightScalar = node.right.isControlRate()

        when {
            orig == null || leftScalar == rightScalar -> node
            rightScalar -> foldMultiply(node.left, node.right, orig.left, refCounts)
            node.left.carriesNoParams() -> foldMultiply(node.right, node.left, orig.right, refCounts)
            else -> node
        }
    }

    is IgnitorDsl.Plus -> {
        val orig = original as? IgnitorDsl.Plus
        val leftScalar = node.left.isControlRate()
        val rightScalar = node.right.isControlRate()

        when {
            orig == null || leftScalar == rightScalar -> node
            rightScalar -> foldAdd(node, node.left, node.right, orig.left, refCounts)
            node.left.carriesNoParams() -> foldAdd(node, node.right, node.left, orig.right, refCounts)
            else -> node
        }
    }

    // x - k is x + (-k), bitwise. k - x stays a Minus: it would be -1 · (x + (-k)), a clamp on a
    // bare subtract (NaN to 0, an infinity to SAFE_MAX) and more work than the subtract.
    is IgnitorDsl.Minus -> {
        val orig = original as? IgnitorDsl.Minus

        when {
            orig == null || node.left.isControlRate() || !node.right.isControlRate() -> node
            else -> foldAdd(node, node.left, node.right.negated(), orig.left, refCounts)
        }
    }

    // x / k is x · (1 / k); the reciprocal stays an expression so the runtime's own divisor guard
    // applies to it once per block. A literal zero divisor is a multiply by a literal zero: the
    // engine renders neither (a dead branch), and the subtree is still BUILT on both sides, so
    // the build-time draws of everything after it (a phase pool, a noise table) stay in step.
    is IgnitorDsl.Div -> {
        val orig = original as? IgnitorDsl.Div

        when {
            orig == null || node.left.isControlRate() || !node.right.isControlRate() -> node
            node.right.isLiteralZero() -> foldMultiply(node.left, IgnitorDsl.Constant(0.0), orig.left, refCounts)
            else -> foldMultiply(node.left, IgnitorDsl.Constant(1.0).div(node.right), orig.left, refCounts)
        }
    }

    // -x is x · -1: the runtime lowers a negation to that multiply, clamp included
    is IgnitorDsl.Neg -> {
        val orig = original as? IgnitorDsl.Neg

        when {
            orig == null || node.inner.isControlRate() -> node
            else -> foldMultiply(node.inner, IgnitorDsl.Constant(-1.0), orig.inner, refCounts)
        }
    }

    else -> node
}

/**
 * `-k` for a block-constant [this], BARE: a literal negates in place, anything else becomes
 * `-0.0 - k`, which is `-k` bitwise for every k (signed zeros, NaN and infinities included) and
 * carries no clamp. A `Neg` node would not do: it is a multiply by -1 at runtime, so it would
 * scrub a NaN and clamp at SAFE_MAX where the subtract it replaces passes both through.
 */
private fun IgnitorDsl.negated(): IgnitorDsl = when (this) {
    is IgnitorDsl.Constant -> IgnitorDsl.Constant(-value)
    else -> IgnitorDsl.Minus(IgnitorDsl.Constant(-0.0), this)
}

/** `signal · k`, [signal] already rewritten, [originalSignal] its pre-rewrite node. */
private fun foldMultiply(signal: IgnitorDsl, k: IgnitorDsl, originalSignal: IgnitorDsl, refCounts: RefCounts): IgnitorDsl {
    if (refCounts.owns(originalSignal)) {
        // a constant add or subtract directly under the multiply becomes the pre-add
        if (signal is IgnitorDsl.Plus) {
            val leftScalar = signal.left.isControlRate()
            val rightScalar = signal.right.isControlRate()

            if (rightScalar && !leftScalar) {
                return IgnitorDsl.Affine(signal.left, pre = signal.right, mul = k, add = ABSENT)
            }

            if (leftScalar && !rightScalar && signal.left.carriesNoParams()) {
                return IgnitorDsl.Affine(signal.right, pre = signal.left, mul = k, add = ABSENT)
            }
        }

        if (signal is IgnitorDsl.Minus && signal.right.isControlRate() && !signal.left.isControlRate()) {
            return IgnitorDsl.Affine(signal.left, pre = signal.right.negated(), mul = k, add = ABSENT)
        }

        // a literal run of multiplies composes into the inner node's coefficient while the
        // chain's per-op clamp and the fold's single clamp cannot differ: a growing run always
        // (both saturate at SAFE_MAX), an attenuating run only over an input a clamping op has
        // already bounded, never a mixed run (the condition is on x · product, not on the product)
        if (signal is IgnitorDsl.Affine && signal.add.isAbsent() && signal.pre.isAbsent()) {
            val m = signal.mul.literalOrNull()
            val factor = k.literalOrNull()

            if (m != null && factor != null) {
                val product = m * factor
                val growing = abs(m) >= 1.0 && abs(factor) >= 1.0
                val attenuating = abs(m) <= 1.0 && abs(product) <= 1.0 && signal.inner.isClamped()

                // an outer sign flip or identity: -(m · x) is (-m) · x for every finite sample
                // and the clamp sees the same magnitudes (a NaN sample is scrubbed to a zero
                // whose sign the two forms may not share; only a negative odd `pow` after it
                // could tell). Not the INNER one: k · safeOut(-x) clamps x before an
                // attenuating k, (-k) · x does not (the attenuating rule above covers it when the
                // input is clamped)
                val flip = abs(factor) == 1.0

                // the composed constant must be a normal double: an overflow saturates where the
                // chain does not, a subnormal carries a rounding error far above the margin, and
                // a product that UNDERFLOWS to zero would be a dead branch the chain never was
                // (a zero is composed only when one factor is a zero already)
                val zeroFactor = m == 0.0 || factor == 0.0
                val normal = product.isFinite() && ((product == 0.0 && zeroFactor) || abs(product) >= 2.2250738585072014e-308)

                if ((growing || attenuating || flip) && normal) {
                    return signal.copy(mul = IgnitorDsl.Constant(product))
                }
            }
        }
    }

    return IgnitorDsl.Affine(signal, pre = ABSENT, mul = k, add = ABSENT)
}

/** `signal + k`: fills an owned Affine's absent add; any other add stays a Plus (no multiply, no Affine). */
private fun foldAdd(node: IgnitorDsl, signal: IgnitorDsl, k: IgnitorDsl, originalSignal: IgnitorDsl, refCounts: RefCounts): IgnitorDsl {
    if (signal is IgnitorDsl.Affine && signal.add.isAbsent() && refCounts.owns(originalSignal)) {
        return signal.copy(add = k)
    }

    return node
}
