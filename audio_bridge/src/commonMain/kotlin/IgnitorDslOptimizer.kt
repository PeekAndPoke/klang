/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Pure `IgnitorDsl -> IgnitorDsl` rewrite pass: collapses filter chains that were authored as
 * separate nodes into fused [IgnitorDsl.Eq] sections, without changing a single sample.
 *
 * ## The one rule that makes this safe
 *
 * **Nothing ever moves. Only ADJACENT fusible filters collapse.**
 *
 * That is stricter than it needs to be mathematically, and deliberately so. A gain multiply
 * commutes with a linear filter on paper, but `k * lowpass(x)` and `lowpass(k * x)` do not
 * produce the same BITS, and bit-identity is a hard requirement here. So the pass never
 * reorders, never hoists, and never crosses a node it does not recognise. A chain like
 * `sine().bandpass().distort().lowpass()` yields two independent one-section Eqs, because the
 * distort between them is a wall.
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
 * This pass implements serial filter fusion (R1). It is intentionally not a complete optimizer;
 * see `docs/tasks/ignitor-optimizer-followups.md` for the catalogue of cases it does NOT yet
 * claim, each with the reason. The kill switch [IgnitorDsl.OptimizerHint] disables it for a
 * whole graph so any suspicion can be settled by ear.
 */
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

    val result = fuseSerialFilters(rebuilt, node, refCounts)

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
    val section = node.asFusibleSection() ?: return node
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
            inner.copy(sections = inner.sections + section)

        // Otherwise start a fresh Eq. A single filter converting to a one-section Eq is a
        // measured win on the deployment platform (Node 0.44 vs 0.74 us/block) and a wash on
        // the JVM, so standalone conversion is ON. See the D2a bake-off record in the plan.
        else -> IgnitorDsl.Eq(inner = inner, sections = listOf(section))
    }
}

/**
 * Maps a chained filter node to the equivalent EQ section, or null when it must NOT fuse.
 *
 * Refuses when `analog` is anything but a literal zero: a non-zero analog switches SvfLPF/SvfHPF
 * to their state-dependent saturating branch, which is deliberate nonlinear character that
 * `EqCore` does not implement. A Param-backed analog is refused too, because an osc-param could
 * turn saturation on per note and the decision is made here, once, at registration.
 *
 * `OnePoleLowpass` (and therefore `warmth`) is absent by design: there is no one-pole section
 * type, and substituting an SVF would change the sound.
 */
private fun IgnitorDsl.asFusibleSection(): IgnitorDsl.EqSection? = when (this) {
    is IgnitorDsl.Lowpass ->
        if (analog.isLiteralZero()) IgnitorDsl.EqSection.Lowpass(cutoffHz, q) else null

    is IgnitorDsl.Highpass ->
        if (analog.isLiteralZero()) IgnitorDsl.EqSection.Highpass(cutoffHz, q) else null

    is IgnitorDsl.Bandpass ->
        if (analog.isLiteralZero()) IgnitorDsl.EqSection.Bandpass(cutoffHz, q) else null

    is IgnitorDsl.Notch ->
        if (analog.isLiteralZero()) IgnitorDsl.EqSection.Notch(cutoffHz, q) else null

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

