/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystBuilder
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.kat

/**
 * The stage probe's MEASURING functions ([measureOutput], [measureStage], [worstOf]) on synthetic stages whose answers
 * are known by hand, so the numbers the runner prints are proven: a pass-through, a pure delay, a one-pole leaky
 * integrator, a NaN latch, a stage that recovers after a fixed count, a stage with a subnormal tail, a DC offset, and
 * the two stereo shapes of a poison (kept to its channel, mixed across). Two rows prove the engine adapters route the
 * signal: a Katalyst gain of 2 and an all-off `classic()` over the sample leaf. The oracles are written from each
 * synthetic stage's definition, never from the probe.
 */
class StageProbeSpec : StringSpec({

    // 2 s of tail keeps the spec quick and still holds every answer below (the leaky integrator's slowest, its
    // subnormal onset, sits at about 1.47 s).
    val signals = standardProbeSignals(tailSeconds = 2.0).associateBy { it.name }

    fun signal(name: String): ProbeSignal = signals.getValue(name)

    /** A mono stage from a fresh per-sample law per render. */
    fun mono(make: () -> (Double) -> Double): ProbeStage = ProbeStage { s ->
        val law = make()

        ProbeOutput(left = DoubleArray(s.frames) { law(s.left[it]) }, right = null)
    }

    val passThrough = mono { { x -> x } }

    val doubled = mono { { x -> 2.0 * x } }

    /** y[n] = x[n - 10]: an impulse comes out at frame 10 and nothing after it. */
    val delayed10 = mono {
        val ring = DoubleArray(10)
        var at = 0

        val law: (Double) -> Double = { x ->
            val y = ring[at]
            ring[at] = x
            at = (at + 1) % 10
            y
        }
        law
    }

    /** y[n] = x[n] + 0.99 * y[n - 1]: impulse response 0.99^n, DC gain 100. */
    val leaky = mono {
        var y = 0.0

        val law: (Double) -> Double = { x ->
            y = x + 0.99 * y
            y
        }
        law
    }

    /** Pass-through until the first non-finite input, NaN for ever after. */
    val nanLatch = mono {
        var latched = false

        val law: (Double) -> Double = { x ->
            if (!x.isFinite()) {
                latched = true
            }

            if (latched) Double.NaN else x
        }
        law
    }

    /** NaN for exactly 50 frames from a non-finite input on (the bad frame included), then pass-through again. */
    val recovers50 = mono {
        var bad = 0

        val law: (Double) -> Double = { x ->
            if (!x.isFinite()) {
                bad = 50
            }

            if (bad > 0) {
                bad--
                Double.NaN
            } else {
                x
            }
        }
        law
    }

    /** Pass-through, and after a non-zero input, 100 frames of the subnormal 1e-310 before exact zero. */
    val subnormalTail = mono {
        var countdown = 0

        val law: (Double) -> Double = { x ->
            when {
                x != 0.0 -> {
                    countdown = 100
                    x
                }

                countdown > 0 -> {
                    countdown--
                    1e-310
                }

                else -> 0.0
            }
        }
        law
    }

    val dcOffset = mono { { x -> x + 0.25 } }

    /** Stereo, each channel through its own NaN latch: a poison on the left stays there. */
    val latchPerChannel = ProbeStage { s ->
        val l = nanLatch.render(s).left
        val r = nanLatch.render(ProbeSignal(name = s.name, left = s.right, right = s.right, inputEndFrame = s.inputEndFrame)).left

        ProbeOutput(left = l, right = r)
    }

    /** Stereo, both outputs the mean of the two inputs: a poison on the left reaches the right. */
    val mixesChannels = ProbeStage { s ->
        val mid = DoubleArray(s.frames) { 0.5 * (s.left[it] + s.right[it]) }

        ProbeOutput(left = mid, right = mid.copyOf())
    }

    fun facts(stage: ProbeStage, name: String): OutputFacts = measureStage(stage = stage, signal = signal(name)).left

    // ── The signals ─────────────────────────────────────────────────────────────────────────────

    "the signals are whole blocks, and each is what its name says" {
        signals.keys.toList() shouldBe listOf("impulse", "step", "sweep", "silence", "noise", "nan", "+inf", "1e30")

        for (s in signals.values) {
            withClue(s.name) {
                (s.frames % PROBE_BLOCK_FRAMES) shouldBe 0
                s.right.size shouldBe s.frames
            }
        }

        PROBE_BLOCK_FRAMES shouldBe 128

        val impulse = signal("impulse")
        impulse.left[0] shouldBe 1.0
        impulse.left.drop(1).all { it == 0.0 } shouldBe true
        impulse.inputEndFrame shouldBe 1

        val step = signal("step")
        step.left.take(48000).all { it == 1.0 } shouldBe true
        step.left.drop(48000).all { it == 0.0 } shouldBe true

        signal("silence").left.all { it == 0.0 } shouldBe true
        signal("silence").inputEndFrame shouldBe 0

        val noise = signal("noise")
        noise.left.take(48000).all { it >= -1.0 && it <= 1.0 } shouldBe true
        noise.left.take(48000) shouldNotBe noise.right.take(48000)
        standardProbeSignals(tailSeconds = 2.0)[4].left.toList() shouldBe noise.left.toList()

        val nan = signal("nan")
        nan.poisonFrame shouldBe PROBE_POISON_FRAME
        nan.left[PROBE_POISON_FRAME].isNaN() shouldBe true
        nan.right[PROBE_POISON_FRAME].isFinite() shouldBe true
        signal("+inf").left[PROBE_POISON_FRAME] shouldBe Double.POSITIVE_INFINITY
        signal("1e30").left[PROBE_POISON_FRAME] shouldBe 1e30
    }

    // ── Peak ────────────────────────────────────────────────────────────────────────────────────

    "peak: a pass-through of the step peaks at 1, a doubling of the noise at twice the input's peak, the leaky integrator at its DC gain" {
        facts(passThrough, "step").peak shouldBe 1.0
        facts(passThrough, "step").gainDb shouldBe (0.0 plusOrMinus 1e-12)

        val noise = facts(doubled, "noise")
        noise.peak shouldBe 2.0 * noise.inputPeak
        noise.gainDb shouldBe (6.0206 plusOrMinus 1e-4)

        // sum of 0.99^k for k < 48000 = 100 * (1 - 0.99^48000), and 0.99^48000 is far below 1e-12.
        val leakyStep = facts(leaky, "step")
        leakyStep.peak shouldBe (100.0 plusOrMinus 1e-9)
        leakyStep.gainDb shouldBe (40.0 plusOrMinus 1e-9)

        // A finite huge sample is a peak like any other.
        facts(passThrough, "1e30").peak shouldBe 1e30
    }

    // ── DC ──────────────────────────────────────────────────────────────────────────────────────

    "dc: a +0.25 offset reads 1.25 under the step and 0.25 deep in the silence; a pass-through reads 1 and 0" {
        val offsetStep = facts(dcOffset, "step")
        offsetStep.dcInput shouldBe (1.25 plusOrMinus 1e-12)
        offsetStep.dcEnd shouldBe (0.25 plusOrMinus 1e-12)

        val offsetSilence = facts(dcOffset, "silence")
        offsetSilence.dcInput.isNaN() shouldBe true
        offsetSilence.dcEnd shouldBe (0.25 plusOrMinus 1e-12)

        val plain = facts(passThrough, "step")
        plain.dcInput shouldBe 1.0
        plain.dcEnd shouldBe 0.0
    }

    // ── Finite ──────────────────────────────────────────────────────────────────────────────────

    "finite: one NaN and one +Inf through a pass-through are one non-finite sample at the poison frame; 1e30 is finite" {
        for (name in listOf("nan", "+inf")) {
            withClue(name) {
                val f = facts(passThrough, name)
                f.allFinite shouldBe false
                f.nonFiniteCount shouldBe 1
                f.firstNonFiniteFrame shouldBe PROBE_POISON_FRAME
                f.lastNonFiniteFrame shouldBe PROBE_POISON_FRAME
            }
        }

        facts(passThrough, "1e30").allFinite shouldBe true
        facts(passThrough, "noise").nonFiniteCount shouldBe 0
        facts(passThrough, "noise").firstNonFiniteFrame shouldBe NEVER
    }

    // ── Recovery ────────────────────────────────────────────────────────────────────────────────

    "recovery: a pass-through is finite again 1 frame after the poison, the 50-frame stage after 50, the latch never" {
        facts(passThrough, "nan").finiteAgainFrames shouldBe 1
        facts(passThrough, "noise").finiteAgainFrames shouldBe 0

        val fifty = facts(recovers50, "+inf")
        fifty.finiteAgainFrames shouldBe 50
        fifty.nonFiniteCount shouldBe 50
        // Back to silence where the input ends: the sine stops at frame 24000 and the stage passes it through.
        fifty.tailFrames shouldBe 0

        val latch = facts(nanLatch, "nan")
        latch.finiteAgainFrames shouldBe NEVER
        latch.nonFiniteCount shouldBe latch.frames - PROBE_POISON_FRAME
        latch.tailFrames shouldBe NEVER
        latch.zeroFrames shouldBe NEVER
    }

    // ── Tail ────────────────────────────────────────────────────────────────────────────────────

    "tail: a pure delay of 10 frames rings 10 frames past the impulse, the leaky integrator 1145 frames, a pass-through none" {
        val delay = facts(delayed10, "impulse")
        delay.tailFrames shouldBe 10
        delay.zeroFrames shouldBe 10

        // 0.99^n < 1e-5 first at n = ceil(ln(1e-5) / ln(0.99)) = ceil(1145.51) = 1146, so the last loud frame is 1145
        // and the impulse's input ends at frame 1.
        facts(leaky, "impulse").tailFrames shouldBe 1145

        facts(passThrough, "impulse").tailFrames shouldBe 0
        facts(passThrough, "silence").tailFrames shouldBe 0
        facts(dcOffset, "silence").tailFrames shouldBe NEVER
    }

    // ── Exact zero ──────────────────────────────────────────────────────────────────────────────

    "exact zero: the subnormal tail reaches 0.0 after its 100 frames; the leaky integrator stalls in the subnormals and never does" {
        facts(subnormalTail, "impulse").zeroFrames shouldBe 100
        facts(passThrough, "step").zeroFrames shouldBe 0

        // Gradual underflow: once y is below about 50 ulps of the smallest subnormal, 0.99 * y rounds back to y, so the
        // integrator holds a non-zero subnormal for ever while its tail (to 1e-5) ended long before.
        val leakyImpulse = facts(leaky, "impulse")
        leakyImpulse.zeroFrames shouldBe NEVER
        leakyImpulse.tailFrames shouldBe 1145
        facts(dcOffset, "step").zeroFrames shouldBe NEVER
    }

    // ── Subnormal ───────────────────────────────────────────────────────────────────────────────

    "subnormal: the tail stage emits exactly 100 subnormals from frame 1; the leaky integrator crosses into them near 0.99^n = 2.2e-308" {
        val tail = facts(subnormalTail, "impulse")
        tail.subnormalCount shouldBe 100
        tail.firstSubnormalFrame shouldBe 1
        tail.minTailAbs shouldBe 1e-310

        // ln(2.2250738585072014e-308) / ln(0.99) = 70484.4; repeated rounding may move it by a frame or two.
        val leakyImpulse = facts(leaky, "impulse")
        leakyImpulse.firstSubnormalFrame shouldBeInRange 70480..70490
        leakyImpulse.subnormalCount shouldBe leakyImpulse.frames - leakyImpulse.firstSubnormalFrame

        facts(passThrough, "sweep").subnormalCount shouldBe 0
        facts(passThrough, "sweep").firstSubnormalFrame shouldBe NEVER
    }

    // ── Stereo ──────────────────────────────────────────────────────────────────────────────────

    "stereo: a poison kept to its channel does not cross, a channel mix carries it across, and the worst row is the poisoned one" {
        val kept = measureStage(stage = latchPerChannel, signal = signal("nan"))
        kept.crossed shouldBe false
        kept.worst.finiteAgainFrames shouldBe NEVER
        kept.right?.allFinite shouldBe true

        val mixed = measureStage(stage = mixesChannels, signal = signal("nan"))
        mixed.crossed shouldBe true
        mixed.worst.nonFiniteCount shouldBe 2

        measureStage(stage = passThrough, signal = signal("nan")).crossed shouldBe null

        val a = facts(dcOffset, "step")
        val b = facts(nanLatch, "nan")
        val w = worstOf(a, b)
        w.tailFrames shouldBe NEVER
        w.dcEnd.isNaN() shouldBe true
        w.peak shouldBe maxOf(a.peak, b.peak)
        w.firstNonFiniteFrame shouldBe PROBE_POISON_FRAME
    }

    // ── The engine adapters route the signal ────────────────────────────────────────────────────

    "the Katalyst adapter: a gain of 2 doubles both channels of the noise sample for sample" {
        val stage = katalystProbeStage(dsl = KatalystBuilder().gain(2.0.kat).node)
        val s = signal("noise")
        val out = stage.render(s)

        out.left.toList() shouldBe s.left.map { 2.0 * it }
        out.right?.toList() shouldBe s.right.map { 2.0 * it }
    }

    "the Ignitor adapter: classic() over the sample leaf with every stage off and the adsr off is the input, bit for bit" {
        val stage = ignitorProbeStage(tree = IgnitorDsl.Sample.classic(), params = mapOf("adsr.on" to 0.0))
        val s = signal("sweep")
        val out = stage.render(s)

        out.right shouldBe null
        out.left.toList() shouldBe s.left.toList()

        // and a stage switched on by its slot changes it: the probe really runs the tree.
        val lowpassed = ignitorProbeStage(tree = IgnitorDsl.Sample.classic(), params = mapOf("adsr.on" to 0.0, "lpf.freq" to 100.0)).render(s)
        measureOutput(output = lowpassed.left, input = s.left, inputEndFrame = s.inputEndFrame, poisonFrame = -1).peak shouldBeGreaterThan 0.5
        lowpassed.left.toList() shouldNotBe s.left.toList()
    }
})
