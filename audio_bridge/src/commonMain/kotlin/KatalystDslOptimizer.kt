/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

/**
 * Pure `KatalystDsl -> KatalystDsl` rewrite pass: merges runs of adjacent `eq` stages into one, without changing a
 * single rendered sample. The chain's twin of the voice's pass ([IgnitorDsl.optimize], rule R1 there), written for the
 * cylinder amp (KLANG-00468, step 2 of KLANG-00466): since the chain's filter doors (`lowpass`, `highpass`, `bandpass`,
 * `notch`) each append an `eq` stage of their own, a cabinet written as `eq(...)`, `lowpass(...)`, `highpass(...)` is
 * three stages. Merged, it is one, and every stage merged away is one effect object, one writer and its compare pass per
 * block, two banks and the swap's scratch buffers fewer, and fewer per-stage calls per block. The per-sample work is the same:
 * the same sections run on the same samples. Measured on the JVM in review round 1, the per-block time of the cabinet
 * and of a 21-stage run, written and merged, lay inside the run-to-run noise.
 *
 * ## The rule, R1: adjacent `eq` stages become one
 *
 * Walking a stage list in written order, a stage `b` is merged into the stage directly before it, `a`, when ALL of
 * this holds:
 *
 * 1. both are [KatalystStageDsl.Eq];
 * 2. no section of `b` is an [IgnitorDsl.EqSection.RawTap];
 * 3. every knob of every section of `a` and of `b` is an [IgnitorDsl.Constant].
 *
 * The merged stage is `Eq(sections = a.sections + b.sections)`, in that order, and the walk then tries to merge the
 * next stage into the merged one, so a run of any length ends as one stage.
 *
 * ## The two refusals, and why
 *
 * - **A tap in `b` (clause 2).** A tap is a boost of what goes INTO its EQ (`EqCore` snapshots the stage's input once
 *   per block, [IgnitorDsl.EqSection.RawTap]). In `b` that is what `a` put out; merged, it would be what went into
 *   `a`: a different sound and no error. A tap in `a` is fine, it reads the same input before and after.
 * - **A slot knob in either (clause 3).** A section knob that is a `Kat.param` can be moved by a pattern while the
 *   chain runs, and the EQ stage answers a move by crossfading its WHOLE bank of sections to a freshly zeroed one
 *   (`KatalystEqEffect`). Merged, a move of one knob would restart every section of the merged stage. With numbers
 *   only, nothing in the stage can ever move.
 *
 * ## The promise: the same bits
 *
 * With both refusals the merged chain renders the SAME BITS as the written one: the sections run in the same order on
 * the same samples, each in place on the bus, and a stage of constants installs its one bank at once on its first block
 * and never again. What guarantees the first block: a chain is configured before it processes anything, by its host
 * (an owner's commit on a cylinder, the output every block, a swap's arriving chain) or, when nothing has applied it
 * since it was built or reset, by itself from no state (`KatalystChain.process`, KLANG-00468 review round 1).
 * Without that, a chain processed before its first configure ran its stages off, and the next configure faded each
 * stage in from dry: three stages one after another, merged ones together, other samples. That is stricter than the
 * voice's margin (`OPTIMIZER_PARITY`), and it is why this pass needs no kill switch: there is nothing to settle by ear.
 * `KatalystDslOptimizerRenderSpec` (`audio_be`) holds it bit for bit.
 *
 * ## What it walks, and what it never does
 *
 * - **Chains inside chains**: every branch of a [KatalystStageDsl.Parallel] and the body of an
 *   [KatalystStageDsl.OversampleBy] or an [KatalystStageDsl.OversampleAtLeast] is optimized the same way. The `when`
 *   is exhaustive, so a new stage that holds chains must decide here.
 * - **Never across another stage**, whatever it is: a `gain`, a `times`, a `distort`, a `duck`, a region. A stage it
 *   does not merge is a wall, as an `Affine` is for the voice's R1.
 * - **Never moves a stage and never drops one.** An `eq` with no sections merges like any other (it holds no tap and no
 *   slot); a lone empty `eq` stays where it is.
 * - **The same instance when nothing merges**: a chain with nothing to merge comes back as itself
 *   (`optimize() === this`), and so does every nested chain that did not change. Run twice, the second run hands back
 *   the first run's instance.
 *
 * ## Deliberately left
 *
 * Folding a gain (or any arithmetic stage) into a neighbour, reordering linear stages, merging stages that hold slots,
 * dropping empty stages, merging across a duck (which the chain builder takes out of the list, so the stages on either
 * side meet at run time) or across a region that takes no step (which the builder inlines). Each would break the
 * bit-for-bit promise or needs a measurement first; KLANG-00204 (ignitor optimizer open items) keeps the list.
 *
 * ## Where it runs
 *
 * In the backend, once per registration (`KatalystRegistry.register`, decision D9 of KLANG-00466): the wire carries the
 * chain as written, and so does a chain's name (`KatalystDsl.uniqueId` is taken from the written chain). The pass is
 * pure and deterministic, so one name always finds one optimized chain.
 */
fun KatalystDsl.optimize(): KatalystDsl {
    val merged = stages.withEqRunsMerged()

    return if (merged === stages) this else KatalystDsl(stages = merged)
}

/**
 * These stages with R1 applied (see [KatalystDsl.optimize]), nested chains first. The same list instance when nothing changed, at
 * this level or below.
 *
 * One pass, linear in the stages and sections: the open run (the `eq` stage written last) gathers the sections merged
 * into it in one list, and remembers once whether all its knobs are numbers, so a long run is never copied or scanned
 * again at each merge.
 */
private fun List<KatalystStageDsl>.withEqRunsMerged(): List<KatalystStageDsl> {
    val out = ArrayList<KatalystStageDsl>(size)
    var changed = false

    // The open run: its first stage as written, the sections gathered once a later stage merged in (null until then,
    // so a run of one stays that stage's instance), and whether every knob of the run is a number.
    var runFirst: KatalystStageDsl.Eq? = null
    var runSections: MutableList<IgnitorDsl.EqSection>? = null
    var runAllNumbers = false

    fun closeRun() {
        val first = runFirst ?: return
        val gathered = runSections

        out.add(if (gathered == null) first else KatalystStageDsl.Eq(sections = gathered))
        runFirst = null
        runSections = null
    }

    for (stage in this) {
        val walked = stage.withNestedChainsOptimized()

        if (walked !== stage) {
            changed = true
        }

        // Any other stage is a wall: the run before it ends there.
        if (walked !is KatalystStageDsl.Eq) {
            closeRun()
            out.add(walked)

            continue
        }

        val allNumbers = walked.sections.all { it.knobsAreNumbers() }
        val first = runFirst

        // Clauses 2 and 3 of R1: `a` is the open run, `b` this stage.
        if (first != null && runAllNumbers && allNumbers && walked.sections.none { it is IgnitorDsl.EqSection.RawTap }) {
            val gathered = runSections ?: ArrayList(first.sections).also { runSections = it }

            gathered.addAll(walked.sections)
            changed = true
        } else {
            closeRun()
            runFirst = walked
            runAllNumbers = allNumbers
        }
    }

    closeRun()

    return if (changed) out else this
}

/**
 * True when every knob of this section is an [IgnitorDsl.Constant]: nothing in it can move while the chain runs. The
 * knobs are read through [childNodes], the section's every knob in constructor order. What guards it: a new section
 * KIND breaks the build (the `when` in [childNodes] is exhaustive); a new KNOB on an existing kind is caught by
 * `IgnitorDslWalkSpec` (against the variant's own `collectParams`) once its corpus entry holds a marker for it, and by
 * `KatalystDslOptimizerSpec`'s table once the (kind, knob) pair is added there.
 */
private fun IgnitorDsl.EqSection.knobsAreNumbers(): Boolean = childNodes().all { it is IgnitorDsl.Constant }

/** This stage with the chains it holds optimized; itself when it holds none, or none of them changed. */
private fun KatalystStageDsl.withNestedChainsOptimized(): KatalystStageDsl = when (this) {
    is KatalystStageDsl.Parallel -> {
        val walked = branches.map { it.optimize() }

        if (walked.indices.all { walked[it] === branches[it] }) this else copy(branches = walked)
    }

    is KatalystStageDsl.OversampleBy -> {
        val walked = body.optimize()

        if (walked === body) this else copy(body = walked)
    }

    is KatalystStageDsl.OversampleAtLeast -> {
        val walked = body.optimize()

        if (walked === body) this else copy(body = walked)
    }

    // Exhaustive on purpose (the chain builder's `lastDuck` is the model): a new stage that holds chains must decide here.
    is KatalystStageDsl.Body, is KatalystStageDsl.Vowel, is KatalystStageDsl.Delay, is KatalystStageDsl.Reverb,
    is KatalystStageDsl.Phaser, is KatalystStageDsl.Compressor, is KatalystStageDsl.Distort, is KatalystStageDsl.Shape,
    is KatalystStageDsl.Duck, is KatalystStageDsl.Eq, is KatalystStageDsl.Gain, is KatalystStageDsl.Plus,
    is KatalystStageDsl.Minus, is KatalystStageDsl.Times, is KatalystStageDsl.Div, is KatalystStageDsl.Pow,
    is KatalystStageDsl.Min, is KatalystStageDsl.Max, is KatalystStageDsl.Abs -> this
}
