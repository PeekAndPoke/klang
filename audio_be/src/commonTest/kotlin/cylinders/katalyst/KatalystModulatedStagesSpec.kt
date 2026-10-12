/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Knob expressions step 5b: one behaviour row per stage decision of the plan's per-stage table
 * (`docs/tasks/in-progress/katalyst-knob-expressions.md`, "D2 in practice", Q7, Q9, Q16): what a stage does with a knob
 * that holds a modulator, read off the stage after each block of a real chain.
 *
 * The modulator of most rows is the sine at the EIGHTH clock with a phase of 1/16 ([s]): it never lands on 0 or on a
 * peak, and it crosses 0 between blocks 3 and 4 and between 7 and 8, so the rows that care about a sign change see one
 * every four blocks: `0.383, 0.924, 0.924, 0.383, -0.383, -0.924, -0.924, -0.383`, then again. Its values are the hand
 * law ([handSine]), never a second run of the evaluator.
 */
class KatalystModulatedStagesSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128

    fun c(v: Double) = KatalystKnobDsl.Constant(v)

    /** The LFO of the rows: the sine at the eighth clock, phase 1/16. */
    val lfo = KatalystKnobDsl.Sine(freq = c(EIGHTH_HZ), phase = c(1.0 / 16.0))

    fun lfoRange(from: Double, to: Double) = KatalystKnobDsl.Range(inner = lfo, from = c(from), to = c(to))

    /** [lfo] at block [k], by hand (`fastSin` is within about 1e-11 of it). */
    fun s(k: Int): Double = handSine((k % 8) * 0.125 + 1.0 / 16.0)

    /** [lfoRange] at block [k], by hand: the range law. */
    fun ranged(k: Int, from: Double, to: Double): Double = from + (s(k) + 1.0) * 0.5 * (to - from)

    fun build(dsl: KatalystDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = dsl,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    /**
     * [blocks] blocks of a 220 Hz sine at 0.5 (or of [constant] when given) through [chain], configured once with no
     * owner (`applyParams(null)`, then nothing: only the hook moves the knobs), calling [after] after each block.
     */
    fun run(chain: KatalystChain, blocks: Int, constant: Double? = null, after: (Int, KatalystContext) -> Unit) {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        chain.applyParams(null)

        for (k in 0 until blocks) {
            for (i in 0 until blockFrames) {
                val x = constant ?: (0.5 * sin(2.0 * PI * 220.0 * (k * blockFrames + i) / sampleRate))

                ctx.mixBuffer.left[i] = x
                ctx.mixBuffer.right[i] = x
            }

            chain.process(ctx)
            after(k, ctx)
        }
    }

    fun stateOf(state: Any): String = state::class.simpleName ?: "?"

    /** A modulated knob whose value is NaN on every block: the sine plus a NaN constant (`plus` keeps a NaN). */
    val nanLfo = KatalystKnobDsl.Plus(left = lfo, right = c(Double.NaN))

    /**
     * The largest one-sample step of a stage's output over [blocks] blocks of a DC mix of 0.5, after [prepare] has run
     * the stage to a steady state. [each] configures the stage before block `k`. A DC mix makes the output's own steps
     * zero once the stage has settled, so what is left is what the configs did to the feed.
     */
    fun largestStep(
        effect: KatalystEffect,
        prepare: (KatalystContext) -> Unit,
        blocks: Int,
        each: (Int) -> Unit,
    ): Double {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        var previous = Double.NaN
        var largest = 0.0

        prepare(ctx)

        for (k in 0 until blocks) {
            each(k)
            ctx.mixBuffer.fill(0.5)
            effect.process(ctx)

            for (i in 0 until blockFrames) {
                val y = ctx.mixBuffer.left[i]

                if (previous == previous) {
                    largest = max(largest, abs(y - previous))
                }

                previous = y
            }
        }

        return largest
    }

    /** [blocks] blocks of a DC mix of 0.5 through [effect], each configured by [configure] first. */
    fun warm(effect: KatalystEffect, ctx: KatalystContext, blocks: Int, configure: () -> Unit) {
        repeat(blocks) {
            configure()
            ctx.mixBuffer.fill(0.5)
            effect.process(ctx)
        }
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────────────────────────────────────────

    "div by a modulated divisor through its pole: the level ramps linearly between the two blocks' reciprocals" {
        // The stage glides the RECIPROCAL over one block, so between a divisor of 0.383 and one of -0.383 the bus is
        // multiplied by a level ramping from 2.61 to -2.61 through 0, never by the huge levels of the divisors in
        // between that the author never wrote.
        val chain = build(KatalystDsl.of(KatalystStageDsl.Div(other = lfo)))
        var previous = 1.0 / s(0)

        run(chain, blocks = 12, constant = 1.0) { k, ctx ->
            val level = 1.0 / s(k)
            val from = if (k == 0) level else previous
            val bound = max(abs(from), abs(level))

            for (i in 0 until blockFrames) {
                val expected = level - (level - from) / blockFrames * (blockFrames - 1 - i)
                val out = ctx.mixBuffer.left[i]

                withClue("block $k, sample $i: the hand ramp from $from to $level") {
                    out shouldBe (expected plusOrMinus 1e-9)
                    abs(out) shouldBeLessThanOrEqual bound + 1e-9
                }
            }

            previous = level
        }
    }

    // ── Distort ──────────────────────────────────────────────────────────────────────────────────────────────────

    "a distort amount LFO through 0 switches through the stage's blend and never cuts: it never reaches Off (Q7)" {
        // At or below 0 the gate says off; the stage blends towards dry over 50 ms, and the LFO is back above 0 four
        // blocks later, which turns the blend around where it stands. Accepted and documented: an amount the author
        // modulates through 0 rides the switch, it does not click.
        val amount = KatalystKnobDsl.Times(left = lfo, right = c(0.5))
        val chain = build(KatalystDsl.of(KatalystStageDsl.Distort(amount = amount)))
        val distort = chain.pipeline.single() as KatalystDistortEffect
        val states = mutableListOf<String>()

        run(chain, blocks = 24) { k, _ ->
            states += stateOf(distort.currentState)

            withClue("block $k: still on") { distort.isOn.shouldBeTrue() }
        }

        withClue("blocks 4 to 7 (amount below 0) fade towards dry: $states") {
            (4..7).forEach { states[it] shouldBe "Fading" }
        }
    }

    // ── Delay ────────────────────────────────────────────────────────────────────────────────────────────────────

    "delay wet and feedback glide over one block, and a modulated wet through 0 keeps the stage running (Q7)" {
        val chain = build(
            KatalystDsl.of(KatalystStageDsl.Delay(wet = lfo, time = c(0.1), feedback = lfoRange(from = 0.2, to = 0.6))),
        )
        val delay = chain.delay.shouldNotBeNull()

        run(chain, blocks = 16) { k, _ ->
            withClue("block $k: running, the wet ${s(k)} or not") { stateOf(delay.currentState) shouldBe "Active" }
            withClue("block $k: the wet is the block's") { delay.wetInForce shouldBe (s(k) plusOrMinus 1e-9) }
            withClue("block $k: the feedback is the block's") {
                delay.delayLine.shouldNotBeNull().feedback shouldBe (ranged(k, from = 0.2, to = 0.6) plusOrMinus 1e-9)
            }
        }
    }

    "a modulated delay time steps through the 50 ms tap crossfade, the changes in between parked: not a chorus (Q9)" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Delay(wet = c(0.4), time = lfoRange(from = 0.1, to = 0.2), feedback = c(0.3)),
            ),
        )
        val delay = chain.delay.shouldNotBeNull()
        val taps = mutableListOf<Double>()
        val fading = mutableListOf<Boolean>()

        run(chain, blocks = 64) { _, _ ->
            val line = delay.delayLine.shouldNotBeNull()

            taps += line.tapInForce
            fading += line.isCrossfading
        }

        // A crossfade starts on a block where the tap moved; between starts the tap stands, whatever the time does.
        val starts = (1 until taps.size).filter { taps[it] != taps[it - 1] }

        withClue("block 0 snaps onto block 0's time") {
            taps[0] shouldBe (ranged(0, from = 0.1, to = 0.2) * sampleRate plusOrMinus 1e-6)
        }

        for (k in starts) {
            withClue("a step at block $k goes to block $k's time, and starts a crossfade") {
                taps[k] shouldBe (ranged(k, from = 0.1, to = 0.2) * sampleRate plusOrMinus 1e-6)
                fading[k].shouldBeTrue()
            }
        }

        withClue("steps, not a glide: one per crossfade, the changes between them parked: $starts") {
            (starts.size >= 3).shouldBeTrue()
            (1 until starts.size).forEach { (starts[it] - starts[it - 1] >= 18).shouldBeTrue() }
            // Parked: on block 5 the time has moved and the tap has not (the crossfade of block 1 runs).
            taps[5] shouldBe taps[1]
            ranged(5, from = 0.1, to = 0.2) * sampleRate shouldNotBe taps[5]
        }
    }

    "a modulated delay time below MIN_ACTIVE_DELAY_SECONDS fades the feed, drains the stage, and resumes, per block" {
        // 0.025 +- 0.075 s: blocks 4 to 7 are below 0.01 s. The wet is modulated too (0.5 +- 0.1, never 0), so the
        // feed's fade spans one block: block 4 stays Active while it falls to 0, block 5's off-config finds it landed
        // and drains (review round 2 of step 5b; before it the drain began at block 4).
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Delay(
                    wet = lfoRange(from = 0.4, to = 0.6),
                    time = lfoRange(from = -0.05, to = 0.1),
                    feedback = c(0.3),
                ),
            ),
        )
        val delay = chain.delay.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            val expected = if (k in 5..7) "Draining" else "Active"

            withClue("block $k at time ${ranged(k, from = -0.05, to = 0.1)}") {
                stateOf(delay.currentState) shouldBe expected
            }
        }
    }

    "the delay's time gate fades the feed out and back in: no one-sample feed edge in either direction" {
        // Round 1 of step 5b: a modulated time crossing the gate (the stage built with `timeModulated`, the configs
        // here what its writer hands over) went from Active to Draining feeding silence from the next sample on, and a
        // return fed the full wet at once; with a DC mix each was a step of the whole echo (0.4) one delay time later.
        // Now the wet glide takes the feed to 0 over its span while the stage stays Active, the drain starts where it
        // lands, and a return glides it up from 0 (round 2). The bound is a hundredth of the feed: a 50 ms ramp of
        // 0.4 moves about 2e-4 a sample. An unmodulated time keeps the hard cut (`KatalystDelayEffectSpec`, "drains
        // on schedule"). The chain-level rows below drive the same through `KatalystChainBuilder.build`.
        val warmBlocks = 400

        fun delay() = KatalystDelayEffect(
            rings = SizedBuffers.forRings(sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            timeModulated = true,
        )

        fun on(fx: KatalystDelayEffect) = fx.configure(time = 0.05, feedback = 0.0, cap = 1.0, wet = 0.8)

        fun off(fx: KatalystDelayEffect) = fx.configure(time = 0.0, feedback = 0.0, cap = 1.0, wet = 0.8)

        val goingOff = delay()
        val offEdge = largestStep(
            effect = goingOff,
            prepare = { ctx -> warm(goingOff, ctx, warmBlocks) { on(goingOff) } },
            blocks = 60,
        ) { off(goingOff) }

        val comingBack = delay()
        val states = mutableListOf<String>()
        val onEdge = largestStep(
            effect = comingBack,
            prepare = { ctx ->
                warm(comingBack, ctx, warmBlocks) { on(comingBack) }
                // Past the fade (19 blocks at 48 kHz) and the off that found it landed, the drain still running: the
                // return is from Draining.
                warm(comingBack, ctx, 24) { off(comingBack) }
                states += stateOf(comingBack.currentState)
            },
            blocks = 60,
        ) { on(comingBack) }

        println("delay feed edge: off $offEdge, on $onEdge")

        withClue("the return is from Draining: $states") { states shouldBe listOf("Draining") }
        withClue("going off: $offEdge") { offEdge shouldBeLessThanOrEqual 0.004 }
        withClue("coming back: $onEdge") { onEdge shouldBeLessThanOrEqual 0.004 }
    }

    "a modulated delay cap applies per block" {
        val chain = build(
            KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.4), time = c(0.1), cap = lfoRange(from = 0.5, to = 1.5))),
        )
        val delay = chain.delay.shouldNotBeNull()

        run(chain, blocks = 10) { k, _ ->
            withClue("block $k") {
                delay.delayLine.shouldNotBeNull().cap shouldBe (ranged(k, from = 0.5, to = 1.5) plusOrMinus 1e-9)
            }
        }
    }

    // ── Reverb ───────────────────────────────────────────────────────────────────────────────────────────────────

    "reverb wet and size glide over one block, the lowpass steps per block, a wet through 0 keeps the room (Q7)" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = lfo,
                    size = lfoRange(from = 4.0, to = 8.0),
                    lowpass = lfoRange(from = 2000.0, to = 6000.0),
                ),
            ),
        )
        val reverb = chain.reverb.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            val unit = reverb.reverb.shouldNotBeNull()

            withClue("block $k: running") { stateOf(reverb.currentState) shouldBe "Active" }
            withClue("block $k: wet") { reverb.wetInForce shouldBe (s(k) plusOrMinus 1e-9) }
            withClue("block $k: size") {
                unit.size shouldBe (Reverb.normalizeSize(ranged(k, from = 4.0, to = 8.0)) plusOrMinus 1e-9)
            }
            withClue("block $k: lowpass") {
                unit.lowpass shouldBe (ranged(k, from = 2000.0, to = 6000.0) plusOrMinus 1e-6)
            }
        }
    }

    "a modulated reverb size below MIN_ACTIVE_SIZE fades the feed, drains the room, and resumes, per block" {
        // Authored 0.1 +- 0.6: blocks 4 to 7 are below 0.1, which normalizes under 0.01. The delay's row above: a
        // modulated wet makes the fade one block, so block 4 stays Active and the drain starts at block 5.
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Reverb(wet = lfoRange(from = 0.4, to = 0.6), size = lfoRange(from = -0.5, to = 0.7)),
            ),
        )
        val reverb = chain.reverb.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            val expected = if (k in 5..7) "Draining" else "Active"

            withClue("block $k at size ${ranged(k, from = -0.5, to = 0.7)}") {
                stateOf(reverb.currentState) shouldBe expected
            }
        }
    }

    "the reverb's size gate fades the feed out and back in: no one-sample feed edge in either direction" {
        // The delay's row above, on the room, built with `sizeModulated`: a feed cut or restart in one sample steps
        // the network's output (the allpasses pass a step of their input at once). DC mix 0.5, wet 0.8, size 0.5 on
        // and 0 off. An unmodulated size keeps the hard cut (`KatalystReverbEffectSpec`, "drains on schedule").
        val warmBlocks = 900

        fun reverb() = KatalystReverbEffect(
            units = ReverbUnits(sampleRate),
            blockFrames = blockFrames,
            sizeModulated = true,
        )

        fun on(fx: KatalystReverbEffect) = fx.configure(size = 0.5, lowpass = null, wet = 0.8)

        fun off(fx: KatalystReverbEffect) = fx.configure(size = 0.0, lowpass = null, wet = 0.8)

        val goingOff = reverb()
        val offEdge = largestStep(
            effect = goingOff,
            prepare = { ctx -> warm(goingOff, ctx, warmBlocks) { on(goingOff) } },
            blocks = 60,
        ) { off(goingOff) }

        val comingBack = reverb()
        val states = mutableListOf<String>()
        val onEdge = largestStep(
            effect = comingBack,
            prepare = { ctx ->
                warm(comingBack, ctx, warmBlocks) { on(comingBack) }
                warm(comingBack, ctx, 24) { off(comingBack) }
                states += stateOf(comingBack.currentState)
            },
            blocks = 60,
        ) { on(comingBack) }

        println("reverb feed edge: off $offEdge, on $onEdge")

        // Measured: the cut 0.0104 and the restart 0.0117 in one sample, the fades 6e-5 and 1e-4; the steady room on
        // this mix moves 1.6e-8 a sample, so neither number is the room's own. The bound sits a decade from each.
        withClue("the return is from Draining: $states") { states shouldBe listOf("Draining") }
        withClue("going off: $offEdge") { offEdge shouldBeLessThanOrEqual 1e-3 }
        withClue("coming back: $onEdge") { onEdge shouldBeLessThanOrEqual 1e-3 }
    }

    "a fade feeds the unit while Active: the drain and the ceiling both cover what it wrote" {
        // The feed's fade writes into the ring or the network after the off-config arrived. Since round 2 it runs in
        // Active, where the ceiling observes every block, and the drain's countdown is measured where it lands. Built
        // to make a wrong order matter: a tiny charge before the off, then a loud mix through the fade. Both answers
        // are checked against the unit's own content: the drain may end, and the ceiling may say "no tail", only once
        // what is left is at the silence floor (1e-5; 2e-5 allows the block the answer lands on).
        val floor = 2e-5

        fun delay() = KatalystDelayEffect(
            rings = SizedBuffers.forRings(sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            timeModulated = true,
        )

        fun reverb() = KatalystReverbEffect(
            units = ReverbUnits(sampleRate),
            blockFrames = blockFrames,
            sizeModulated = true,
        )

        fun reverbOn(fx: KatalystReverbEffect) = fx.configure(size = 0.5, lowpass = null, wet = 1.0)

        fun reverbOff(fx: KatalystReverbEffect) = fx.configure(size = 0.0, lowpass = null, wet = 1.0)

        /** One block of [mix] (a charge of [impulse] at sample 0 when given) through [fx]. */
        fun block(fx: KatalystEffect, ctx: KatalystContext, mix: Double, impulse: Double = 0.0) {
            ctx.mixBuffer.fill(mix)
            ctx.mixBuffer.left[0] += impulse
            ctx.mixBuffer.right[0] += impulse
            fx.process(ctx)
        }

        /** The probe of one stage: its effect, its on and off configs, its state and what its unit holds. */
        class Probe(
            val fx: KatalystEffect,
            val on: () -> Unit,
            val off: () -> Unit,
            val state: () -> Any,
            val content: () -> Double,
            val charge: Double,
        )

        /** On, its wet glided in over silence, then [Probe.charge]: the stage as the off finds it. */
        fun charged(p: Probe, ctx: KatalystContext) {
            repeat(24) {
                p.on()
                block(p.fx, ctx, mix = 0.0)
            }

            p.on()
            block(p.fx, ctx, mix = 0.0, impulse = p.charge)
        }

        /** The off, 10 blocks of a loud mix through the fade, then silence: what the unit holds when the drain ends. */
        fun drainCut(p: Probe): Double {
            val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

            charged(p, ctx)
            p.off()
            stateOf(p.state()) shouldBe "Active"

            for (k in 0 until 10_000) {
                p.off()

                val before = p.content()

                block(p.fx, ctx, mix = if (k < 10) 1.0 else 0.0)

                if (!p.fx.hasTail()) {
                    return before
                }
            }

            error("the drain never ended")
        }

        /** The off, 5 blocks of a loud mix into the fade, the return, then silence: what is left at "no tail". */
        fun ceilingFall(p: Probe): Double {
            val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

            charged(p, ctx)
            repeat(5) {
                p.off()
                block(p.fx, ctx, mix = 1.0)
            }

            for (k in 0 until 10_000) {
                p.on()
                block(p.fx, ctx, mix = 0.0)

                if (!p.fx.hasTail()) {
                    return p.content()
                }
            }

            error("the ceiling never fell")
        }

        fun delayProbe(time: Double, feedback: Double): Probe {
            val fx = delay()

            return Probe(
                fx = fx,
                on = { fx.configure(time = time, feedback = feedback, cap = 1.0, wet = 1.0) },
                off = { fx.configure(time = 0.0, feedback = feedback, cap = 1.0, wet = 1.0) },
                state = { fx.currentState },
                content = { fx.delayLine.shouldNotBeNull().tapWindowPeakAbs() },
                charge = 1e-4,
            )
        }

        fun reverbProbe(): Probe {
            val fx = reverb()

            return Probe(
                fx = fx,
                on = { reverbOn(fx) },
                off = { reverbOff(fx) },
                state = { fx.currentState },
                content = { fx.reverb.shouldNotBeNull().combPeakAbs() },
                charge = 0.01,
            )
        }

        val delayCut = drainCut(delayProbe(time = 0.05, feedback = 0.5))
        // A countdown shorter than the fade (one period of 0.02 s, 7.5 blocks): the drain must still not end in it.
        val delayShortCut = drainCut(delayProbe(time = 0.02, feedback = 0.0))
        val delayFall = ceilingFall(delayProbe(time = 0.05, feedback = 0.5))
        val reverbCut = drainCut(reverbProbe())
        val reverbFall = ceilingFall(reverbProbe())

        println("left at the drain's end: delay $delayCut $delayShortCut, reverb $reverbCut")
        println("left at the ceiling's fall: delay $delayFall, reverb $reverbFall")

        withClue("delay, the drain's end: $delayCut") { delayCut shouldBeLessThanOrEqual floor }
        withClue("delay, a countdown shorter than the fade: $delayShortCut") {
            delayShortCut shouldBeLessThanOrEqual floor
        }
        withClue("delay, the ceiling's fall after a return: $delayFall") { delayFall shouldBeLessThanOrEqual floor }
        withClue("reverb, the drain's end: $reverbCut") { reverbCut shouldBeLessThanOrEqual floor }
        withClue("reverb, the ceiling's fall after a return: $reverbFall") { reverbFall shouldBeLessThanOrEqual floor }
    }

    "a reverb poisoned during its feed fade heals where the fade lands, as a poisoned network at the off does" {
        // A runaway finite send through the fade (`Double.MAX_VALUE`, the route `KatalystReverbEffectSpec`'s
        // "overflow-poisoned network" row names: the input guard scrubs only non-finite samples) overflows a comb cell
        // while the stage is still Active. The off-config that finds the glide landed counts down from it, the count
        // is infinite, and `Active.deactivate` resets the network into Off at once, its heal for a poisoned network.
        val fx = KatalystReverbEffect(units = ReverbUnits(sampleRate), blockFrames = blockFrames, sizeModulated = true)
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

        // On long enough for the wet to glide in from 0 (the first activation of a modulated size).
        repeat(24) {
            fx.configure(size = 0.5, lowpass = null, wet = 1.0)
            ctx.mixBuffer.fill(0.5)
            fx.process(ctx)
        }

        fx.configure(size = 0.0, lowpass = null, wet = 1.0)
        stateOf(fx.currentState) shouldBe "Active"

        repeat(60) { k ->
            fx.configure(size = 0.0, lowpass = null, wet = 1.0)
            ctx.mixBuffer.fill(if (k < 25) Double.MAX_VALUE else 0.0)
            fx.process(ctx)
        }

        stateOf(fx.currentState) shouldBe "Off"
        fx.reverb.shouldNotBeNull().combPeakAbs() shouldBe 0.0
    }

    // ── Delay and reverb gates through the builder (review round 2 of step 5b) ──────────────────────────────────

    /**
     * [blocks] blocks of a DC mix of 0.5 through [chain], configured the way an owned orbit is (`applyParams` with
     * [params] of the block, then `process`). Returns the left output's one-sample steps, one per sample (the first
     * against the dry 0.5), and the state [state] reads after each block.
     */
    fun gateRun(
        chain: KatalystChain,
        blocks: Int,
        state: () -> Any,
        params: (Int) -> KlangMap<Double>?,
    ): Pair<DoubleArray, List<String>> {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val steps = DoubleArray(blocks * blockFrames)
        val states = mutableListOf<String>()
        var previous = 0.5

        for (k in 0 until blocks) {
            ctx.mixBuffer.fill(0.5)
            chain.applyParams(params(k))
            chain.process(ctx)

            for (i in 0 until blockFrames) {
                val y = ctx.mixBuffer.left[i]

                steps[k * blockFrames + i] = abs(y - previous)
                previous = y
            }

            states += stateOf(state())
        }

        return steps to states
    }

    /** The largest of these steps from block [from] up to block [to], not included. */
    fun DoubleArray.largest(from: Int, to: Int): Double {
        var largest = 0.0

        for (i in from * blockFrames until to * blockFrames) {
            largest = max(largest, this[i])
        }

        return largest
    }

    /** The blocks where [states] changes, with the state it changes to: the scenario, for a clue. */
    fun transitions(states: List<String>): String =
        states.indices.filter { it == 0 || states[it] != states[it - 1] }.joinToString { "$it ${states[it]}" }

    /**
     * A square LFO between [low] and [high] with a period of [period] blocks: [high] over its first [highBlocks] blocks
     * from block 0, [low] over the rest. Its phase sits half a block past the rising edge, so no block start reads a
     * flank (the flank is two samples).
     */
    fun squareGate(period: Int, highBlocks: Int, low: Double, high: Double) = KatalystKnobDsl.Range(
        inner = KatalystKnobDsl.Square(
            freq = c(sampleRate / (period * blockFrames.toDouble())),
            duty = c(highBlocks / period.toDouble()),
            phase = c(0.5 / period),
        ),
        from = c(low),
        to = c(high),
    )

    "through build(): a modulated delay time through the gate fades the feed out, and every return glides it up" {
        // The builder tells the stage its time is modulated (`timeModulated = time.isModulated`). A square LFO holds
        // the time at 0.05 s over blocks 0 to 255 and from 512 on, and at 0 (finite, below the gate) over 256 to 511.
        // The wet's slot `w` is unset (NaN) over blocks 560 to 679: the stage is no longer asked for, the hard cut,
        // from which the stage returns while it still drains. DC mix 0.5, wet 0.8, feedback 0.3: the dry signal
        // stands, so a step of the output is a step of the feed (0.4 at most) one delay time later, then at 0.3 per
        // period. Every window stays under a hundredth of the feed; the hard cut's window holds its own edge, which
        // shows the measure sees one.
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Delay(
                    wet = KatalystKnobDsl.Param("w", 0.8),
                    time = squareGate(period = 512, highBlocks = 256, low = 0.0, high = 0.05),
                    feedback = c(0.3),
                ),
            ),
        )
        val delay = chain.delay.shouldNotBeNull()
        val unset = klangMapOf("w" to Double.NaN)
        val (steps, states) = gateRun(chain, blocks = 768, state = { delay.currentState }) { k ->
            if (k in 560 until 680) unset else null
        }

        val first = steps.largest(from = 0, to = 256)
        val off = steps.largest(from = 256, to = 512)
        val fromOff = steps.largest(from = 512, to = 560)
        val cut = steps.largest(from = 560, to = 680)
        val fromDraining = steps.largest(from = 680, to = 768)

        println("delay through build: ${transitions(states)}")
        println("delay feed edge: first $first, off $off, from Off $fromOff, cut $cut, from Draining $fromDraining")

        // Softly, edges first: a mutant shows every window it opens, not only the first state it moves.
        assertSoftly {
            withClue("the first activation fades in: $first") { first shouldBeLessThanOrEqual 0.004 }
            withClue("going off: $off") { off shouldBeLessThanOrEqual 0.004 }
            withClue("the return from Off: $fromOff") { fromOff shouldBeLessThanOrEqual 0.004 }
            withClue("the hard cut is one: $cut") { (cut > 0.1).shouldBeTrue() }
            withClue("the return from the hard cut's drain: $fromDraining") {
                fromDraining shouldBeLessThanOrEqual 0.004
            }
            withClue("the scenario: ${transitions(states)}") {
                states[255] shouldBe "Active"
                states[260] shouldBe "Active"
                states[300] shouldBe "Draining"
                states[511] shouldBe "Off"
                states[512] shouldBe "Active"
                states[560] shouldBe "Draining"
                states[679] shouldBe "Draining"
                states[767] shouldBe "Active"
            }
        }
    }

    "through build(): a modulated reverb size through the gate fades the feed out, and every return glides it up" {
        // The delay's row above on the room (`sizeModulated = size.isModulated`): the authored size 5 (0.5 normalized)
        // over blocks 0 to 639 and from 1920 on, 0 (below the gate) over 640 to 1919, which the drain needs to reach
        // Off; `w` unset over 2000 to 2399, long enough for the room's own echoes of the hard cut to fall a decade
        // under the bound before the return, short enough that the room still drains. The size never moves while the
        // stage runs, so its glide's span cannot show here: only the feed's fade and its glide from 0 keep each window
        // under the bound (measured in round 1 by hand: a cut 0.0104 and a restart 0.0117 in one sample, the fades
        // about 1e-4).
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Reverb(
                    wet = KatalystKnobDsl.Param("w", 0.8),
                    size = squareGate(period = 1920, highBlocks = 640, low = 0.0, high = 5.0),
                ),
            ),
        )
        val reverb = chain.reverb.shouldNotBeNull()
        val unset = klangMapOf("w" to Double.NaN)
        val (steps, states) = gateRun(chain, blocks = 2560, state = { reverb.currentState }) { k ->
            if (k in 2000 until 2400) unset else null
        }

        val first = steps.largest(from = 0, to = 640)
        val off = steps.largest(from = 640, to = 1920)
        val fromOff = steps.largest(from = 1920, to = 2000)
        val cut = steps.largest(from = 2000, to = 2400)
        val fromDraining = steps.largest(from = 2400, to = 2560)

        println("reverb through build: ${transitions(states)}")
        println("reverb feed edge: first $first, off $off, from Off $fromOff, cut $cut, from Draining $fromDraining")

        // Softly, edges first: a mutant shows every window it opens, not only the first state it moves.
        assertSoftly {
            withClue("the first activation fades in: $first") { first shouldBeLessThanOrEqual 1e-3 }
            withClue("going off: $off") { off shouldBeLessThanOrEqual 1e-3 }
            withClue("the return from Off: $fromOff") { fromOff shouldBeLessThanOrEqual 1e-3 }
            withClue("the hard cut is one: $cut") { (cut > 5e-3).shouldBeTrue() }
            withClue("the return from the hard cut's drain: $fromDraining") {
                fromDraining shouldBeLessThanOrEqual 1e-3
            }
            withClue("the scenario: ${transitions(states)}") {
                states[639] shouldBe "Active"
                states[645] shouldBe "Active"
                states[700] shouldBe "Draining"
                states[1919] shouldBe "Off"
                states[1920] shouldBe "Active"
                states[2000] shouldBe "Draining"
                states[2399] shouldBe "Draining"
                states[2400] shouldBe "Active"
            }
        }
    }

    "a NaN modulated wet reads as off, exactly as an unmodulated NaN wet does: delay and reverb" {
        // `isModulating` asks for a finite value too (round 1 of step 5b): before, `Kat.sine(1).plus(NaN)` counted as
        // asked for and ran the stage at the default wet, where a NaN constant leaves it off.
        for (wet in listOf(c(Double.NaN), nanLfo)) {
            val delayChain = build(KatalystDsl.of(KatalystStageDsl.Delay(wet = wet, time = c(0.1))))
            val reverbChain = build(KatalystDsl.of(KatalystStageDsl.Reverb(wet = wet, size = c(5.0))))

            run(delayChain, blocks = 8) { k, ctx ->
                withClue("delay, wet $wet, block $k") {
                    stateOf(delayChain.delay.shouldNotBeNull().currentState) shouldBe "Off"
                    delayChain.delay.shouldNotBeNull().delayLine shouldBe null
                    ctx.mixBuffer.left.all { it.isFinite() }.shouldBeTrue()
                }
            }
            run(reverbChain, blocks = 8) { k, ctx ->
                withClue("reverb, wet $wet, block $k") {
                    stateOf(reverbChain.reverb.shouldNotBeNull().currentState) shouldBe "Off"
                    reverbChain.reverb.shouldNotBeNull().reverb shouldBe null
                    ctx.mixBuffer.left.all { it.isFinite() }.shouldBeTrue()
                }
            }
        }
    }

    // ── Phaser ───────────────────────────────────────────────────────────────────────────────────────────────────

    "phaser wet and floor move the C4 pair in one block, the rate applies at once, center and sweep in one block" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Phaser(
                    wet = lfoRange(from = 0.3, to = 0.7),
                    rate = lfoRange(from = 0.5, to = 2.0),
                    center = lfoRange(from = 500.0, to = 1500.0),
                    sweep = lfoRange(from = 200.0, to = 600.0),
                    floor = lfoRange(from = 0.5, to = 1.0),
                ),
            ),
        )
        val phaser = chain.phaser.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            // The law's pair for the block's knobs, from a bare phaser holding them.
            val law = Phaser(sampleRate).apply {
                depth = ranged(k, from = 0.3, to = 0.7)
                floor = ranged(k, from = 0.5, to = 1.0)
            }

            withClue("block $k: dry") { phaser.dryCoeff shouldBe (law.dryCoeff() plusOrMinus 1e-9) }
            withClue("block $k: wet") { phaser.wetCoeff shouldBe (law.wetCoeff() plusOrMinus 1e-9) }
            withClue("block $k: rate") {
                phaser.phaser.rate shouldBe (ranged(k, from = 0.5, to = 2.0) plusOrMinus 1e-9)
            }
            withClue("block $k: center") {
                phaser.center shouldBe (ranged(k, from = 500.0, to = 1500.0) plusOrMinus 1e-6)
            }
            withClue("block $k: sweep") { phaser.sweep shouldBe (ranged(k, from = 200.0, to = 600.0) plusOrMinus 1e-6) }
        }
    }

    "a phaser wet LFO through the depth gate switches per block through the coefficient pair: identity below it" {
        val chain = build(KatalystDsl.of(KatalystStageDsl.Phaser(wet = lfo, rate = c(1.0))))
        val phaser = chain.phaser.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            if (k in 4..7) {
                withClue("block $k, wet ${s(k)}: identity") {
                    phaser.dryCoeff shouldBe 1.0
                    phaser.wetCoeff shouldBe 0.0
                }
            } else {
                withClue("block $k, wet ${s(k)}: engaged") { phaser.wetCoeff shouldNotBe 0.0 }
            }
        }
    }

    // ── Compressor ───────────────────────────────────────────────────────────────────────────────────────────────

    "compressor threshold, ratio and knee glide over one block, attack and release apply at once" {
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Compressor(
                    threshold = lfoRange(from = -30.0, to = -10.0),
                    ratio = lfoRange(from = 2.0, to = 6.0),
                    knee = lfoRange(from = 0.0, to = 6.0),
                    attack = lfoRange(from = 0.001, to = 0.02),
                    release = lfoRange(from = 0.05, to = 0.2),
                ),
            ),
        )
        val compressor = chain.compressor.shouldNotBeNull()

        run(chain, blocks = 12) { k, _ ->
            val instance = compressor.compressor.shouldNotBeNull()

            withClue("block $k: threshold") {
                compressor.thresholdInForce shouldBe (ranged(k, from = -30.0, to = -10.0) plusOrMinus 1e-9)
            }
            withClue("block $k: ratio") {
                compressor.inverseRatioInForce shouldBe (1.0 / ranged(k, from = 2.0, to = 6.0) plusOrMinus 1e-9)
            }
            withClue("block $k: knee") {
                compressor.kneeInForce shouldBe (ranged(k, from = 0.0, to = 6.0) plusOrMinus 1e-9)
            }
            withClue("block $k: attack") {
                instance.attackSeconds shouldBe (ranged(k, from = 0.001, to = 0.02) plusOrMinus 1e-12)
            }
            withClue("block $k: release") {
                instance.releaseSeconds shouldBe (ranged(k, from = 0.05, to = 0.2) plusOrMinus 1e-12)
            }
        }
    }

    // ── Duck ─────────────────────────────────────────────────────────────────────────────────────────────────────

    "duck: depth in one block and declared through 0 (Q7), attack at once, the orbit switching with no mechanism" {
        // The orbit flips between 1 and 2 with a square at the eighth clock (phase 0.3): high, high, low x4, high x2.
        val orbit = KatalystKnobDsl.Range(
            inner = KatalystKnobDsl.Square(freq = c(EIGHTH_HZ), phase = c(0.3)),
            from = c(1.0),
            to = c(2.0),
        )
        val chain = build(
            KatalystDsl.of(
                KatalystStageDsl.Duck(orbit = orbit, depth = lfo, attack = lfoRange(from = 0.01, to = 0.05)),
            ),
        )
        val duck = chain.duck.shouldNotBeNull()
        val sidechain = StereoBuffer(blockFrames).also { it.fill(0.5) }

        run(chain, blocks = 16) { k, ctx ->
            ctx.sidechainBuffer = sidechain
            chain.processDuck(ctx)

            val high = ((k % 8) * 0.125 + 0.3).let { if (it >= 1.0) it - 1.0 else it } < 0.5

            withClue("block $k: still declared, the depth ${s(k)} or not") { chain.ducksWith().shouldBeTrue() }
            withClue("block $k: the depth in force, coerced to 0..1 by the instance") {
                duck.depth shouldBe (s(k).coerceIn(0.0, 1.0) plusOrMinus 1e-9)
            }
            withClue("block $k: the attack") {
                val attack = duck.ducking.shouldNotBeNull().attackSeconds

                attack shouldBe (ranged(k, from = 0.01, to = 0.05) plusOrMinus 1e-12)
            }
            withClue("block $k: the source orbit") { duck.duckCylinderId shouldBe (if (high) 2 else 1) }
        }
    }

    "a NaN modulated duck depth leaves the stage undeclared and the mix finite, as a NaN constant depth does" {
        // Round 1 of step 5b: `isModulating` ignored the value, so `Kat.sine(1).plus(NaN)` declared the duck with a
        // NaN depth, `Ducking` kept it (its coerceIn keeps a NaN) and the orbit mix went NaN.
        for (depth in listOf(c(Double.NaN), nanLfo)) {
            val chain = build(KatalystDsl.of(KatalystStageDsl.Duck(orbit = c(1.0), depth = depth)))
            val sidechain = StereoBuffer(blockFrames).also { it.fill(0.5) }

            run(chain, blocks = 8) { k, ctx ->
                ctx.sidechainBuffer = sidechain
                chain.processDuck(ctx)

                withClue("depth $depth, block $k") {
                    chain.ducksWith().shouldBeFalse()
                    ctx.mixBuffer.left.all { it.isFinite() }.shouldBeTrue()
                }
            }
        }
    }

    "a duck takeover across spans clamps the carried glide: it lands inside its endpoints, within the new span (Q16)" {
        // The leaving duck's depth is a slot glide (19 blocks at 48 kHz), the arriving one's is modulated (one block).
        val leaving = KatalystDuckEffect(sampleRate = sampleRate, blockFrames = blockFrames, depthModulated = false)
        val arriving = KatalystDuckEffect(sampleRate = sampleRate, blockFrames = blockFrames, depthModulated = true)
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val sidechain = StereoBuffer(blockFrames).also { it.fill(0.5) }

        ctx.sidechainBuffer = sidechain

        fun pass(fx: KatalystDuckEffect) {
            ctx.mixBuffer.fill(0.25)
            fx.orbitBlockRan()
            fx.process(ctx)
        }

        leaving.configure(DuckSettings().apply { cylinderId = 1; depth = 0.2; attackSeconds = 0.05 })
        pass(leaving)
        leaving.configure(DuckSettings().apply { cylinderId = 1; depth = 0.9; attackSeconds = 0.05 })
        repeat(5) { pass(leaving) }

        val carried = leaving.depth

        withClue("the leaving depth is mid-glide: $carried") { (carried > 0.2 && carried < 0.9).shouldBeTrue() }

        arriving.takeOver(leaving)
        arriving.depth shouldBe carried
        pass(arriving)

        withClue("the carried glide finished within the arriving span, at its target, never past it") {
            arriving.depth shouldBe 0.9
        }
    }

    // ── Body and vowel ───────────────────────────────────────────────────────────────────────────────────────────

    "a modulated body material steps through the bank swap, a change during a fade parked: steps, never morphs" {
        // Index 2 on blocks 0 and 1, index 1 on blocks 2 to 5, index 2 from block 6 (the square at phase 0.3).
        val material = KatalystKnobDsl.Range(
            inner = KatalystKnobDsl.Square(freq = c(EIGHTH_HZ), phase = c(0.3)),
            from = c(1.0),
            to = c(2.0),
        )
        val chain = build(KatalystDsl.of(KatalystStageDsl.Body(material = material, wet = c(0.6), floor = c(0.4))))
        val body = chain.body.shouldNotBeNull()
        val one = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = 1.0)
        val two = ResonatorTables.at(kind = ResonatorKind.BODY, slotValue = 2.0)
        val tables = mutableListOf<Any?>()
        val parked = mutableListOf<Boolean>()

        one shouldNotBe two

        run(chain, blocks = 16) { _, _ ->
            tables += body.installedTable
            parked += body.isParked
        }

        withClue("block 0 installs index 2's bank at once") { tables[0] shouldBe two }
        withClue("block 2 installs index 1's bank, a swap") { tables[2] shouldBe one }
        withClue("block 6 asks for index 2 during that swap's fade: parked, index 1 still installed") {
            parked[6].shouldBeTrue()
            tables[6] shouldBe one
        }
        val landed = (7 until tables.size).first { tables[it] === two }

        withClue("the parked bank lands with the fade (7.5 blocks from block 2), not before: block $landed") {
            (landed in 9..10).shouldBeTrue()
            parked[landed].shouldBeFalse()
        }
        withClue("a whole bank per change, never one per block: ${body.installs} installs in 16 blocks") {
            (body.installs <= 4).shouldBeTrue()
        }
    }

    "a hand-built modulator on a body's wet is frozen until 5c: no bank restarts, the wet at its clock's start" {
        // The doors refuse it (a type error); a Kotlin caller who builds the node gets the knob frozen.
        val chain = build(KatalystDsl.of(KatalystStageDsl.Body(material = c(1.0), wet = lfo, floor = c(0.4))))
        val body = chain.body.shouldNotBeNull()

        run(chain, blocks = 12) { _, _ -> }

        withClue("one install, the first") { body.installs shouldBe 1 }
        body.installedMix shouldBe (s(0) plusOrMinus 1e-9)
    }
})
