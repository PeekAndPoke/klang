/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.filters.EqSectionSpec
import io.peekandpoke.klang.audio_be.filters.eqSectionSpec
import io.peekandpoke.klang.audio_be.utils.isFiniteInline
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET

/**
 * Builds the [KatalystChain] for one [KatalystDsl]: one exhaustive `when` over
 * [KatalystStageDsl], one stage instance per declared stage, in the declared order.
 *
 * The seven classic effects are the stage instances for their variants, **unchanged** (the shells
 * over the shared DSP in `audio_be/effects/`), with the constructor arguments the cylinder used to
 * pass them: the delay rents its ring from [SizedBuffers] and the reverb its network from
 * [ReverbUnits], both on first activation and never here, so building a chain allocates no ring
 * and no network. Since 2026-09-19 the classic chain declares a `Gain` at unity, so every cylinder
 * has one, the born-with chain included: bit-transparent at unity and reachable with
 * `katp("gain.gain", x)` (signal-flow plan section 6, spot C).
 *
 * **One kind of writer, on every chain** (Katalyst step 5b-1, 2026-09-19): every knob of every
 * stage comes from the chain's own slots ([KatalystKnob]), resolved here and
 * re-resolved only when the orbit's param state changes. That state is the owner voice's
 * `katalystParams`, which `.katp` and the bus doors write (step 5a), and it is the ONE way a bus
 * knob reaches a stage, for a declared chain and for the chain a cylinder is born with alike. The
 * voice's bus FIELDS stopped being a knob source then (the signal-flow plan §7, D4: the chain is
 * the instrument) and left the wire in step 5b-3. The `voiceDriven` flag and the owner writers it
 * chose went with step 5b-1.
 *
 * A writer has TWO halves, `resolve` and `apply`, and the split is about the per-block cost: the
 * orbit's param state changes far more rarely than a block goes by, so `resolve` does the lookups
 * and allocates whatever composite a stage wants, and `apply` writes numbers that are already in
 * hand (see [KatalystSlotWriter]). `applyParams(null)` is how a chain is configured with no owner
 * voice alive, which is what a chain faded in from the pending poll needs.
 *
 * **A chain that declares two ducks keeps the LAST one** (decided with the maintainer,
 * 2026-09-17): the cylinder runs exactly one ducking effect, so the earlier declarations are
 * silently dropped rather than summed. Every other kind may appear more than once and every
 * instance runs, at its own position.
 */
object KatalystChainBuilder {

    /**
     * Builds the chain for [dsl]. Call once per cylinder and chain (see `Cylinder`), never per
     * block: the stage instances own the orbit's DSP state, and building them is where the
     * allocation lives.
     */
    fun build(
        dsl: KatalystDsl,
        sampleRate: Int,
        blockFrames: Int,
        /** The ring shelf a delay stage rents from. Required: a private shelf hidden in a default
         *  would let a caller build a chain that rents outside the engine's one warehouse. */
        rings: SizedBuffers,
        /** The unit shelf a reverb stage rents from. Required, same reason. */
        reverbs: ReverbUnits,
    ): KatalystChain = build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = rings,
        reverbs = reverbs,
        noises = KatalystNoiseOrdinals(),
    )

    /**
     * [build] with the chain's running count of noise modulators, [noises]: one counter per chain, handed to every knob
     * and on into a `parallel`'s branches, so the n-th noise of the whole chain is seeded by n (see
     * [KatalystNoiseOrdinals]).
     */
    private fun build(
        dsl: KatalystDsl,
        sampleRate: Int,
        blockFrames: Int,
        rings: SizedBuffers,
        reverbs: ReverbUnits,
        noises: KatalystNoiseOrdinals,
    ): KatalystChain {
        val pipeline = mutableListOf<KatalystEffect>()
        val writers = mutableListOf<KatalystSlotWriter>()
        var duckStage: KatalystStageDsl.Duck? = null

        // Every knob of the chain, on its sample rate and block size (a modulator's clock) and its one noise counter.
        // Each stage's knobs are built BEFORE the stage, in the order its writer lists them (the noise ordinals follow
        // that order), so the stage can glide a modulated knob over one block (`KnobGlide`'s span, step 5b, D2).
        fun knob(node: KatalystKnobDsl): KatalystKnob =
            KatalystKnob(node = node, noises = noises, sampleRate = sampleRate, blockFrames = blockFrames)

        // One arithmetic stage and, when it has an operand, its writer (ABS has none). A non-finite operand the stage
        // reads as unset: its identity lives in the stage alone.
        fun arithmetic(op: KatalystArithmeticOp, node: KatalystKnobDsl?) {
            val operand = node?.let { knob(it) }
            val fx = KatalystArithmeticEffect(
                op = op,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                modulated = operand?.isModulated == true,
            )
            pipeline.add(fx)

            if (operand != null) {
                writers.add(KatalystArithmeticWriter(fx = fx, operand = operand))
            }
        }

        for (stage in dsl.stages) {
            when (stage) {
                // Body / vowel resonators, timbre shapers of the whole orbit (moved off the
                // per-voice chain). In the classic chain they run first, so they color the dry
                // mix before the time/dynamics effects.
                is KatalystStageDsl.Body -> {
                    // The material is a slot like the rest, holding the INDEX of a name in
                    // `BodyMaterials.names` (Katalyst step 5a-2). An unset index (the bare stage's
                    // SLOT_UNSET, or a slot nobody wrote whose default is unset) leaves the stage off
                    // rather than picking a box nobody asked for. A change of it is a bank swap; the wet and
                    // the floor glide the blend in place, over one block when either holds a modulator (step 5c).
                    val material = knob(stage.material)
                    val wet = knob(stage.wet)
                    val floor = knob(stage.floor)
                    val fx = KatalystResonatorEffect(
                        kind = ResonatorKind.BODY,
                        sampleRate = sampleRate.toDouble(),
                        blockFrames = blockFrames,
                        mixModulated = wet.isModulated || floor.isModulated,
                    )
                    pipeline.add(fx)

                    writers.add(KatalystResonatorWriter(fx = fx, index = material, wet = wet, floor = floor))
                }

                // The vowel is the same stage class with the other kind, and its own instance: a chain that declares
                // both runs both (a vowel sung through a body).
                is KatalystStageDsl.Vowel -> {
                    val vowel = knob(stage.vowel)
                    val wet = knob(stage.wet)
                    val floor = knob(stage.floor)
                    val fx = KatalystResonatorEffect(
                        kind = ResonatorKind.VOWEL,
                        sampleRate = sampleRate.toDouble(),
                        blockFrames = blockFrames,
                        mixModulated = wet.isModulated || floor.isModulated,
                    )
                    pipeline.add(fx)

                    writers.add(KatalystResonatorWriter(fx = fx, index = vowel, wet = wet, floor = floor))
                }

                // No ring until the stage is asked for ([stageAskedFor], resource warehouse 2b). This used to
                // construct a 10-second DelayLine per orbit (7.68 MB, 97 % of the cylinder)
                // delay or not.
                is KatalystStageDsl.Delay -> {
                    val wet = knob(stage.wet)
                    val time = knob(stage.time)
                    val feedback = knob(stage.feedback)
                    val cap = knob(stage.cap)
                    val fx = KatalystDelayEffect(
                        rings = rings,
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                        wetModulated = wet.isModulated,
                        feedbackModulated = feedback.isModulated,
                        timeModulated = time.isModulated,
                    )
                    pipeline.add(fx)

                    // Routed through the effect's lifecycle: an off-config drains the tail out on
                    // its own timeline instead of freezing the ring (see KatalystDelayEffect).
                    //
                    // `delay.wet` is how much of the orbit mix feeds the line (step 5b-2). An
                    // AUTHORED `wet(0.0)` rents no ring (decided 2026-09-17, consistent with the
                    // phaser gated on depth and the duck on orbit), while a wet a PATTERN wrote runs
                    // the stage whatever its value. The one home of that rule is [stageAskedFor].
                    writers.add(KatalystDelayWriter(fx = fx, wet = wet, time = time, feedback = feedback, cap = cap))
                }

                // No network until the stage is asked for ([stageAskedFor], resource warehouse 2d): ~200 KB per
                // orbit otherwise.
                is KatalystStageDsl.Reverb -> {
                    val wet = knob(stage.wet)
                    val size = knob(stage.size)
                    val lowpass = knobOrUnset(
                        node = stage.lowpass,
                        noises = noises,
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                    )
                    val fx = KatalystReverbEffect(
                        units = reverbs,
                        blockFrames = blockFrames,
                        wetModulated = wet.isModulated,
                        sizeModulated = size.isModulated,
                    )
                    pipeline.add(fx)

                    // Routed through the effect's lifecycle like the delay: an off-config drains
                    // the tail out on its own timeline instead of freezing the combs (see
                    // KatalystReverbEffect). The slot carries the AUTHORED 0..10 size, so the writer
                    // passes it through the one shared conversion, and configure bounds it again
                    // at the door. `reverb.wet` is how much of the orbit mix feeds the room, and
                    // whether the stage runs is [stageAskedFor], as on the delay above.
                    writers.add(KatalystReverbWriter(fx = fx, wet = wet, size = size, lowpass = lowpass))
                }

                is KatalystStageDsl.Phaser -> {
                    val wet = knob(stage.wet)
                    val rate = knob(stage.rate)
                    val center = knob(stage.center)
                    val sweep = knob(stage.sweep)
                    val floor = knob(stage.floor)
                    val fx = KatalystPhaserEffect(
                        phaser = Phaser(sampleRate),
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                        mixModulated = wet.isModulated || floor.isModulated,
                        centerModulated = center.isModulated,
                        sweepModulated = sweep.isModulated,
                    )
                    pipeline.add(fx)

                    writers.add(
                        KatalystPhaserWriter(
                            fx = fx,
                            wet = wet,
                            rate = rate,
                            center = center,
                            sweep = sweep,
                            floor = floor,
                        )
                    )
                }

                is KatalystStageDsl.Compressor -> {
                    val threshold = knob(stage.threshold)
                    val ratio = knob(stage.ratio)
                    val knee = knob(stage.knee)
                    val attack = knob(stage.attack)
                    val release = knob(stage.release)
                    // The lookahead is fixed per chain: it sizes the instance's ring here, at build
                    // time, and the effect coerces it (bounded, non-finite is none).
                    val fx = KatalystCompressorEffect(
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                        lookaheadSeconds = stage.lookahead,
                        thresholdModulated = threshold.isModulated,
                        ratioModulated = ratio.isModulated,
                        kneeModulated = knee.isModulated,
                    )
                    pipeline.add(fx)

                    writers.add(
                        KatalystCompressorWriter(
                            fx = fx,
                            threshold = threshold,
                            ratio = ratio,
                            knee = knee,
                            attack = attack,
                            release = release,
                        )
                    )
                }

                is KatalystStageDsl.Distort -> {
                    val amount = knob(stage.amount)
                    // The shape and the oversampling factor are fixed per chain, as on a voice per note: they
                    // pick the shaper and size the oversampler here, at build time. The amount is a slot.
                    val fx = KatalystDistortEffect(
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                        shapeIndex = stage.shape,
                        oversampleFactor = stage.oversample,
                        amountModulated = amount.isModulated,
                    )
                    pipeline.add(fx)

                    writers.add(KatalystDistortWriter(fx = fx, amount = amount))
                }

                // Declared in the list, run outside it: `Cylinders` applies it after every orbit,
                // because it needs cross-orbit access to the sidechain source. The last
                // declaration wins, so a second Duck stage replaces the first; its instance is built
                // after the loop, with its knobs.
                is KatalystStageDsl.Duck -> {
                    duckStage = stage
                }

                // The mix EQ: one stage, the declared section list, over two EqCore banks. Its
                // STRUCTURE (how many sections, of which type) is the declaration's; only the
                // coefficient scalars are knobs.
                is KatalystStageDsl.Eq -> {
                    val specs = stage.sections.map { eqSectionSpec(it) }
                    val fx = KatalystEqEffect(
                        sampleRate = sampleRate.toDouble(),
                        types = IntArray(specs.size) { specs[it].type },
                        blockFrames = blockFrames,
                    )
                    pipeline.add(fx)

                    // The classic chain declares no `eq`; it does declare a `gain` at unity since
                    // 2026-09-19, which is bit-transparent and is what let `katp("gain.gain", x)`
                    // reach the fader of an orbit that declares nothing one step before every
                    // other knob could.
                    val knobs = eqKnobs(specs = specs, sampleRate = sampleRate, blockFrames = blockFrames)

                    writers.add(KatalystEqWriter(fx = fx, knobs = knobs))
                }

                // The group fader, after the inserts (the signal-flow plan's D5).
                is KatalystStageDsl.Gain -> {
                    val gain = knob(stage.gain)
                    val fx = KatalystGainEffect(
                        sampleRate = sampleRate,
                        blockFrames = blockFrames,
                        modulated = gain.isModulated,
                    )
                    pipeline.add(fx)

                    // Unity is the identity element of the stage, not a tuned value, which is why
                    // it is a literal here and in `KatalystStageDsl.Gain` rather than a shared
                    // constant (the wire KDoc says so).
                    writers.add(KatalystGainWriter(fx = fx, gain = gain))
                }

                // The arithmetic stages: the Ignitor's per-sample laws on the bus (`KatalystArithmeticEffect`).
                is KatalystStageDsl.Plus -> arithmetic(KatalystArithmeticOp.PLUS, stage.other)
                is KatalystStageDsl.Minus -> arithmetic(KatalystArithmeticOp.MINUS, stage.other)
                is KatalystStageDsl.Times -> arithmetic(KatalystArithmeticOp.TIMES, stage.other)
                is KatalystStageDsl.Div -> arithmetic(KatalystArithmeticOp.DIV, stage.other)
                is KatalystStageDsl.Pow -> arithmetic(KatalystArithmeticOp.POW, stage.exp)
                is KatalystStageDsl.Min -> arithmetic(KatalystArithmeticOp.MIN, stage.other)
                is KatalystStageDsl.Max -> arithmetic(KatalystArithmeticOp.MAX, stage.other)
                is KatalystStageDsl.Abs -> arithmetic(KatalystArithmeticOp.ABS, null)

                // Branches side by side, each a chain of its own built here (the same shelves, so a branch's delay and
                // reverb rent like the chain's). An empty stage is the bus unchanged: nothing is built. A duck in a
                // branch is the orbit's duck (its position is ignored, see the Duck arm), so it is taken out of every
                // branch and, the last one in the order written, competes with the chain's own here.
                is KatalystStageDsl.Parallel -> if (stage.branches.isNotEmpty()) {
                    val branches = Array(stage.branches.size) {
                        build(
                            dsl = stage.branches[it].withoutDucks(),
                            sampleRate = sampleRate,
                            blockFrames = blockFrames,
                            rings = rings,
                            reverbs = reverbs,
                            noises = noises,
                        )
                    }
                    val fx = KatalystParallelEffect(branches = branches, blockFrames = blockFrames)
                    pipeline.add(fx)
                    writers.add(KatalystParallelWriter(fx = fx))

                    val branchDuck = stage.lastDuck()

                    if (branchDuck != null) {
                        duckStage = branchDuck
                    }
                }
            }
        }

        // The duck, its writer and its knobs are built after the loop so that only the winning stage
        // is ever built and configured; its position among the writers is unobservable (every writer
        // touches exactly one stage), and the duck's list position is documented as ignored anyway.
        // The winning stage's slots, for the same reason: the dropped duplicate's are never read.
        val winner = duckStage
        var theDuck: KatalystDuckEffect? = null
        var duckWriter: KatalystDuckWriter? = null

        if (winner != null) {
            val orbit = knob(winner.orbit)
            val depth = knob(winner.depth)
            val attack = knob(winner.attack)
            val fx = KatalystDuckEffect(
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                depthModulated = depth.isModulated,
            )

            theDuck = fx
            duckWriter = KatalystDuckWriter(fx = fx, orbit = orbit, depth = depth, attack = attack)
            writers.add(duckWriter)
        }

        return KatalystChain(
            serial = pipeline.toTypedArray(),
            writers = writers.toTypedArray(),
            duck = theDuck,
            // The duck's own writer answers whether the stage will be configured: a chain that
            // names no orbit (or a depth of zero) declares a duck that will never be configured,
            // and the host has to be able to tell that from one that will (see
            // [KatalystChain.ducksWith]). A writer, not a build-time flag, because `.katp` can
            // name the orbit after the chain was built.
            duckWriter = duckWriter,
        )
    }

    /** The last duck declared in this stage's branches, nested stages included, in the order written. */
    private fun KatalystStageDsl.Parallel.lastDuck(): KatalystStageDsl.Duck? {
        var last: KatalystStageDsl.Duck? = null

        for (branch in branches) {
            for (stage in branch.stages) {
                // Exhaustive on purpose: a new stage kind that holds chains of its own must decide here.
                when (stage) {
                    is KatalystStageDsl.Duck -> last = stage
                    is KatalystStageDsl.Parallel -> last = stage.lastDuck() ?: last
                    is KatalystStageDsl.Body, is KatalystStageDsl.Vowel, is KatalystStageDsl.Delay, is KatalystStageDsl.Reverb,
                    is KatalystStageDsl.Phaser, is KatalystStageDsl.Compressor, is KatalystStageDsl.Distort, is KatalystStageDsl.Eq,
                    is KatalystStageDsl.Gain, is KatalystStageDsl.Plus, is KatalystStageDsl.Minus, is KatalystStageDsl.Times,
                    is KatalystStageDsl.Div, is KatalystStageDsl.Pow, is KatalystStageDsl.Min, is KatalystStageDsl.Max,
                    is KatalystStageDsl.Abs -> Unit
                }
            }
        }

        return last
    }

    /** This chain with every duck taken out, nested branches included (see the Parallel arm). */
    private fun KatalystDsl.withoutDucks(): KatalystDsl = KatalystDsl(
        stages.mapNotNull { stage ->
            when (stage) {
                is KatalystStageDsl.Duck -> null
                is KatalystStageDsl.Parallel -> KatalystStageDsl.Parallel(branches = stage.branches.map { it.withoutDucks() })
                // Exhaustive on purpose, as in [lastDuck].
                is KatalystStageDsl.Body, is KatalystStageDsl.Vowel, is KatalystStageDsl.Delay, is KatalystStageDsl.Reverb,
                is KatalystStageDsl.Phaser, is KatalystStageDsl.Compressor, is KatalystStageDsl.Distort, is KatalystStageDsl.Eq,
                is KatalystStageDsl.Gain, is KatalystStageDsl.Plus, is KatalystStageDsl.Minus, is KatalystStageDsl.Times,
                is KatalystStageDsl.Div, is KatalystStageDsl.Pow, is KatalystStageDsl.Min, is KatalystStageDsl.Max,
                is KatalystStageDsl.Abs -> stage
            }
        }
    )

    /**
     * One knob per section param, flat, in the layout [KatalystEqEffect.configure] reads: four
     * slots per section, the ones the section's type does not have left null.
     *
     * The fallback, read for a section knob [eqKnob] cannot map, is [SLOT_UNSET] rather than a
     * number. It departs from the rule of the one nullable stage knob, the reverb's `lowpass`, whose
     * missing value is the engine's default: a section's defaults live on its own wire variant (a
     * lowpass cuts at 2000, a bell sits at 1000) and are not shared constants, so copying them here
     * would be two spellings of one default. Unset is safe: the
     * coefficient helpers take a non-finite freq to 1000 Hz, a non-finite q to 0.707 and a
     * non-finite db to a transparent bell, so nothing here can put a NaN in the mix.
     */
    private fun eqKnobs(specs: List<EqSectionSpec>, sampleRate: Int, blockFrames: Int): Array<KatalystKnob?> {
        val knobs = arrayOfNulls<KatalystKnob>(specs.size * KatalystEqEffect.KNOBS_PER_SECTION)

        // A fresh noise counter each: an EQ section knob is a constant or a slot, never a modulator (Q2, E2).
        fun section(node: IgnitorDsl): KatalystKnob = knobOrUnset(
            node = eqKnob(node),
            noises = KatalystNoiseOrdinals(),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
        )

        for (i in specs.indices) {
            val spec = specs[i]
            val base = i * KatalystEqEffect.KNOBS_PER_SECTION

            knobs[base + KatalystEqEffect.KNOB_FREQ] = section(spec.freq)
            knobs[base + KatalystEqEffect.KNOB_Q] = section(spec.q)
            knobs[base + KatalystEqEffect.KNOB_DB] = spec.db?.let { section(it) }
            knobs[base + KatalystEqEffect.KNOB_GAIN] = spec.gain?.let { section(it) }
        }

        return knobs
    }

    /**
     * An EQ section's knob as a chain knob. The sections stay [IgnitorDsl.EqSection], shared with the voice and with
     * `bands` (decision Q2 of `docs/tasks/in-progress/katalyst-knob-expressions.md`, E2), so this is the one place a
     * Katalyst knob
     * is read off an Ignitor node: an [IgnitorDsl.Constant] is a constant, an [IgnitorDsl.Param] the slot of that name,
     * which is what the Katalyst door writes for a number and a `Kat.param`.
     *
     * Anything else is null, the missing knob, which reads as [SLOT_UNSET] (see [eqKnobs]): the door refuses it, so
     * only a section built by hand in Kotlin can carry one. Not an exhaustive `when`, deliberately: the two leaves are
     * the vocabulary of a bus section, and enumerating the ninety-odd [IgnitorDsl] variants would claim a meaning for
     * each that the bus does not have.
     *
     * The ASYMMETRY that buys is worth stating plainly, because it is audible: a knob the bus cannot read is one a
     * VOICE can. `lowpass(freq = sine.range(400, 800))` renders around its center per voice; a section built by hand
     * with that knob lands on a bus at the helper's fallback, 1000 Hz, which is neither the author's center nor the
     * wire default of 2000. Recorded here so nobody reads the fallback as the declared default; a moving bus knob is a
     * `katp` pattern.
     */
    private fun eqKnob(node: IgnitorDsl): KatalystKnobDsl? = when (node) {
        is IgnitorDsl.Constant -> KatalystKnobDsl.Constant(node.value)
        is IgnitorDsl.Param -> KatalystKnobDsl.Param(name = node.name, default = node.default, description = node.description)
        else -> null
    }
}

/**
 * Whether the delay or the reverb stage RUNS, which is a different question from how much of the
 * orbit mix feeds it (that is `wet` itself, since Katalyst step 5b-2).
 *
 * The stage runs when the pattern WROTE this knob (any finite value, zero and negative included:
 * [KatalystKnob.written]) or when what the chain itself authored is a positive amount.
 *
 * **Why a written 0 keeps the stage running** (step 5b-1's "touched" rule, kept by the 5b-2
 * decision): a written wet is a LEVEL, and a level glides. A pattern that moves the wet to 0 and
 * back (`reverb(wet = "0.4 0")`, `delay(wet = "0.3 0")`) fades the feed down and up again while
 * the room or the ring keeps ringing out what it holds; switching the stage off at 0 instead would start a drain
 * and snap the feed back on at the next nonzero. A running stage fed nothing costs its network's
 * processing, which it spends anyway while it rings.
 *
 * The second half keeps the decision of 2026-09-17 for authored constants: a declared
 * `k.reverb(0.0, 6)` that no pattern touches still rents nothing, because nobody
 * asked for a room, only for a chain that has one.
 *
 * Off is expressed by handing the effect a non-finite time respectively size, so the ONE threshold
 * stays inside the effect (where it also drains a live tail instead of freezing it) and this
 * function only decides whether anybody asked for the stage at all.
 *
 * **A modulated wet is asked for** while its modulators run ([KatalystKnob.isModulating], Q7 of
 * `docs/tasks/in-progress/katalyst-knob-expressions.md`, step 5b): an LFO that dips the wet to 0 or below is the
 * author's modulation, a level that glides like a written 0, and must not drain the stage and rent it again every
 * cycle. A modulated wet whose slot leaf is unset (Q5), or whose value is not finite, asks for nothing: off, as the
 * same NaN as a constant is. So this gate is true only with a finite wet.
 */
internal fun stageAskedFor(wet: KatalystKnob): Boolean {
    // NaN-guard on a value the author can write: a non-finite wet was never set, so it is neither
    // a write nor a positive amount, and an unset stage is off. That is the same reading the voice
    // path gave an untouched effect.
    return wet.written || wet.isModulating || (wet.value.isFiniteInline() && wet.value > 0.0)
}

