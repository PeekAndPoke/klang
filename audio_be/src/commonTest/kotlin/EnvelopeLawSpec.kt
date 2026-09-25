/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.SvfCoeffs
import io.peekandpoke.klang.audio_be.filters.computeSvfCoeffs
import io.peekandpoke.klang.audio_be.ignitor.FilterEnvDef
import io.peekandpoke.klang.audio_be.ignitor.FreqIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.Ignitors
import io.peekandpoke.klang.audio_be.ignitor.ParamIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.adsr
import io.peekandpoke.klang.audio_be.ignitor.fmModIgnitor
import io.peekandpoke.klang.audio_be.ignitor.lowpass
import io.peekandpoke.klang.audio_be.ignitor.pitchEnvelopeModIgnitor
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.calculateControlRateEnvelope
import io.peekandpoke.klang.audio_be.voices.strip.filter.EnvelopeRenderer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * THE envelope law ([EnvelopeCore], phase 3 decision D3), pinned against ORACLES written out in this
 * file, on the core and then on every host that can show its level: the Ignitor chain `adsr`, the strip
 * VCA, the Ignitor FM index envelope, the Ignitor pitch envelope and the strip's control-rate envelope.
 *
 * Why oracles and not a parity spec: the hosts share one core, so a mutation INSIDE it moves every host
 * together and a host-against-host comparison stays green (step 4's lesson, `StripLawCoresSpec`). Each
 * oracle below is the rule the way the D3 brief states it, as plain arithmetic:
 *
 *  - attack and decay count FRACTIONAL frames; the release counts `floor(N)` frames and divides by
 *    `floor(N) - 1`, so it is exactly 0.0 on its last rendered frame, and a release under two frames is
 *    0.0 on the gate frame itself;
 *  - attack `g(p)`, decay `s + (1 - s) g(1 - p)`, release `L g(1 - p)` where L is the attack-decay-sustain
 *    level AT the gate frame (stateless), `g` the stage's curve;
 *  - a non-positive or NaN stage time, or one too short to have a finite reciprocal, is a zero-length
 *    stage; the sustain is raw;
 *  - a gate at or before the onset releases from 0.0;
 *  - per destination: an amplitude floors at 0, a filter or FM depth is clamped to [0, 1], a pitch is raw.
 */
class EnvelopeLawSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val far = 1_000_000

    /** The Exponential curve at the engine's bend of 3, written the plain way (the core uses `fastExp`). */
    fun expCurve(x: Double): Double = (exp(3.0 * x) - 1.0) / (exp(3.0) - 1.0)

    fun core(
        a: Double,
        d: Double,
        s: Double,
        r: Double,
        gate: Int,
        curve: AdsrCurve = AdsrCurve.Linear,
    ): EnvelopeCore = EnvelopeCore().apply { prepare(a, d, s, r, gate, curve, curve, curve) }

    // ── The core ──────────────────────────────────────────────────────────────────────────────────

    "attack and decay count fractional frames: 240.5 attack frames make frame 240 still attack" {
        val c = core(a = 240.5, d = 100.0, s = 0.5, r = 1000.0, gate = far)

        c.at(240) shouldBe (240.0 / 240.5 plusOrMinus 1e-15)
        // decay begins at 240.5, so frame 241 is half a frame into it
        c.at(241) shouldBe (0.9975 plusOrMinus 1e-15)
        c.at(340) shouldBe (0.5025 plusOrMinus 1e-15)
        // the sustain begins at 340.5
        c.at(341) shouldBe 0.5
    }

    "the release starts from the attack-decay-sustain level AT the gate frame" {
        // gate at frame 50 of a 100-frame linear attack: the level there is 0.5, not frame 49's 0.49
        val c = core(a = 100.0, d = 100.0, s = 0.2, r = 11.0, gate = 50)

        c.at(49) shouldBe (0.49 plusOrMinus 1e-15)
        c.at(50) shouldBe (0.5 plusOrMinus 1e-15)
        c.at(55) shouldBe (0.25 plusOrMinus 1e-15)
        c.at(60) shouldBe 0.0
        c.at(61) shouldBe 0.0
    }

    "a fractional release counts floor(N) frames and ends at exactly 0.0 on the last one, for every curve" {
        for (curve in AdsrCurve.entries) {
            withClue("curve = $curve") {
                // the gate at frame 1: a gate at or before the onset has its own rule (released from 0)
                val c = core(a = 0.0, d = 0.0, s = 1.0, r = 10.7, gate = 1, curve = curve)

                c.at(1) shouldBe 1.0
                c.at(10) shouldBe 0.0
            }
        }

        core(a = 0.0, d = 0.0, s = 1.0, r = 10.7, gate = 1).at(9) shouldBe (1.0 / 9.0 plusOrMinus 1e-15)
    }

    "a release shorter than two frames is 0.0 on the gate frame" {
        for (r in listOf(0.0, 1.0, 1.9)) {
            withClue("release $r frames") { core(a = 0.0, d = 0.0, s = 1.0, r = r, gate = 1).at(1) shouldBe 0.0 }
        }

        val two = core(a = 0.0, d = 0.0, s = 1.0, r = 2.0, gate = 1)

        two.at(1) shouldBe 1.0
        two.at(2) shouldBe 0.0
    }

    "a non-positive or NaN stage time is a zero-length stage, an infinite one never ends" {
        for (a in listOf(0.0, -5.0, Double.NaN)) {
            withClue("attack $a: frame 0 is the top of the decay") {
                core(a = a, d = 100.0, s = 0.5, r = 10.0, gate = far).at(0) shouldBe 1.0
            }
        }

        for (d in listOf(0.0, -5.0, Double.NaN)) {
            withClue("decay $d: the sustain follows the attack") {
                core(a = 10.0, d = d, s = 0.5, r = 10.0, gate = far).at(10) shouldBe 0.5
            }
        }

        for (r in listOf(-3.0, Double.NaN)) {
            withClue("release $r: 0.0 on the gate frame") { core(a = 0.0, d = 0.0, s = 1.0, r = r, gate = 1).at(1) shouldBe 0.0 }
        }

        core(a = Double.POSITIVE_INFINITY, d = 100.0, s = 0.5, r = 10.0, gate = far).at(100_000) shouldBe 0.0
    }

    "the sustain is raw: above 1 and below 0 pass through" {
        val high = core(a = 0.0, d = 10.0, s = 1.5, r = 10.0, gate = far)

        high.at(5) shouldBe (1.25 plusOrMinus 1e-15)
        high.at(20) shouldBe 1.5

        val low = core(a = 0.0, d = 10.0, s = -0.5, r = 10.0, gate = far)

        low.at(5) shouldBe (0.25 plusOrMinus 1e-15)
        low.at(20) shouldBe -0.5
    }

    "each stage takes its own curve: g(p) up, s + (1 - s) g(1 - p) down, L g(1 - p) on release" {
        val expected = mapOf(
            AdsrCurve.Linear to 0.25,
            AdsrCurve.Square to 0.0625,
            AdsrCurve.Cube to 0.015625,
            AdsrCurve.SCurve to 0.125,
            AdsrCurve.InvSquare to 0.4375,
        )

        for ((curve, g) in expected) {
            withClue("attack $curve at a quarter") {
                EnvelopeCore().apply { prepare(100.0, 100.0, 0.2, 101.0, far, curve, AdsrCurve.Linear, AdsrCurve.Linear) }
                    .at(25) shouldBe (g plusOrMinus 1e-15)
            }
            withClue("decay $curve at three quarters (g at a quarter)") {
                EnvelopeCore().apply { prepare(0.0, 100.0, 0.2, 101.0, far, AdsrCurve.Linear, curve, AdsrCurve.Linear) }
                    .at(75) shouldBe (0.2 + 0.8 * g plusOrMinus 1e-15)
            }
            withClue("release $curve at three quarters (g at a quarter)") {
                EnvelopeCore().apply { prepare(0.0, 0.0, 0.8, 101.0, 1, AdsrCurve.Linear, AdsrCurve.Linear, curve) }
                    .at(76) shouldBe (0.8 * g plusOrMinus 1e-15)
            }
        }

        val e = core(a = 100.0, d = 100.0, s = 0.25, r = 101.0, gate = 150, curve = AdsrCurve.Exponential)
        val atGate = 0.25 + 0.75 * expCurve(0.5)

        e.at(50) shouldBe (expCurve(0.5) plusOrMinus 1e-9)
        e.at(150) shouldBe (atGate plusOrMinus 1e-9)
        e.at(200) shouldBe (atGate * expCurve(0.5) plusOrMinus 1e-9)
    }

    // ── The hosts: each one carries the law, and maps the level onto its destination ─────────────

    /** Renders [ig] for [total] frames in blocks, the gate at [gate]; the voice's own clock. */
    fun renderNode(ig: Ignitor, total: Int, gate: Int, freqHz: Double = 100.0): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
            releaseFrames = 0, scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = DoubleArray(total)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0

        while (pos < total) {
            val n = minOf(blockFrames, total - pos)

            ctx.updateOffsetAndLength(0, n)
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, freqHz = freqHz, ctx = ctx)

            for (i in 0 until n) {
                out[pos + i] = tmp[i]
            }

            pos += n
        }

        return out
    }

    fun sec(frames: Double): Double = frames / sampleRate

    fun dc(): Ignitor = ParamIgnitor("dc", 1.0)

    "host: the Ignitor chain adsr (fractional attack, stateless release, raw sustain, amplitude floor at 0)" {
        val lin = AdsrCurve.Linear

        val frac = renderNode(dc().adsr(sec(240.5), sec(100.0), 0.5, sec(12.0), lin, lin, lin, 0.0), 400, gate = far / 2)

        frac[240] shouldBe (240.0 / 240.5 plusOrMinus 1e-12)

        val gated = renderNode(dc().adsr(sec(100.0), sec(100.0), 0.2, sec(11.0), lin, lin, lin, 0.0), 80, gate = 50)

        gated[50] shouldBe (0.5 plusOrMinus 1e-12)
        gated[60] shouldBe 0.0

        renderNode(dc().adsr(0.0, sec(10.0), 1.5, sec(12.0), lin, lin, lin, 0.0), 40, gate = far / 2)[20] shouldBe 1.5
        renderNode(dc().adsr(0.0, sec(10.0), -0.5, sec(12.0), lin, lin, lin, 0.0), 40, gate = far / 2)[20] shouldBe 0.0
    }

    /** Renders a constant 1.0 through the strip VCA without its de-click. */
    fun renderVca(env: Voice.Envelope, total: Int, gate: Int): AudioBuffer {
        val buf = AudioBuffer(total)

        for (i in 0 until total) {
            buf[i] = 1.0
        }

        val ctx = BlockContext(
            audioBuffer = buf,
            freqModBuffer = DoubleArray(total),
            scratchBuffers = ScratchBuffers(total),
            sampleRate = sampleRate,
            startFrame = 0.0,
            endFrame = far.toDouble(),
            gateEndFrame = gate.toDouble(),
            freqHz = 100.0,
            signal = Ignitors.silence(),
            signalCtx = IgniteContext(
                sampleRate = sampleRate, voiceDurationFrames = gate, gateEndFrame = gate,
                releaseFrames = 0, scratchBuffers = ScratchBuffers(total),
            ),
            cylinders = Cylinders(blockFrames = total, sampleRate = sampleRate),
        ).apply { updateOffsetAndLength(0, total); blockStart = 0.0 }

        EnvelopeRenderer(env, startFrame = 0.0, declickSeconds = 0.0).render(ctx)

        return buf
    }

    "host: the strip VCA (fractional attack, stateless release, a NaN sustain reads as unset 1.0)" {
        val lin = AdsrCurve.Linear

        renderVca(Voice.Envelope(240.5, 100.0, 0.5, 12.0, lin, lin, lin), 400, gate = far)[240] shouldBe
            (240.0 / 240.5 plusOrMinus 1e-12)

        val gated = renderVca(Voice.Envelope(100.0, 100.0, 0.2, 11.0, lin, lin, lin), 80, gate = 50)

        gated[50] shouldBe (0.5 plusOrMinus 1e-12)
        gated[60] shouldBe 0.0

        renderVca(Voice.Envelope(0.0, 10.0, Double.NaN, 12.0, lin, lin, lin), 40, gate = far)[20] shouldBe
            (1.0 plusOrMinus 1e-12)
    }

    "host: the Ignitor FM index envelope (fractional attack, raw sustain, the depth clamped to [0, 1])" {
        // A constant modulator of 1.0 with depth == freq makes the output exactly 1 + envelope. The FM node
        // has no curve knob, so every stage runs the modulation default, Exponential (decision D3): the
        // oracles below are `expCurve`, written out in this spec. 1e-9: the core bends through `fastExp`.
        fun fm(a: Double, d: Double, s: Double, r: Double): Ignitor = fmModIgnitor(
            modulator = ParamIgnitor("m", 1.0),
            ratio = ParamIgnitor("ratio", 1.0),
            depth = ParamIgnitor("depth", 100.0),
            envAttackSec = ParamIgnitor("a", sec(a)),
            envDecaySec = ParamIgnitor("d", sec(d)),
            envSustainLevel = ParamIgnitor("s", s),
            envReleaseSec = ParamIgnitor("r", sec(r)),
            freq = FreqIgnitor,
        )

        renderNode(fm(240.5, 100.0, 0.5, 12.0), 400, gate = far / 2)[240] - 1.0 shouldBe (expCurve(240.0 / 240.5) plusOrMinus 1e-9)

        val gated = renderNode(fm(100.0, 100.0, 0.2, 11.0), 80, gate = 50)

        gated[50] - 1.0 shouldBe (expCurve(0.5) plusOrMinus 1e-9)
        gated[60] shouldBe 1.0

        // The sustain is raw: -0.5 over a 10-frame decay is -0.5 + 1.5 g(0.8) at frame 2 (a clamped sustain
        // of 0 would give g(0.8) there), and the host clamps what goes below 0 (the raw -0.5 at frame 20)...
        val low = renderNode(fm(0.0, 10.0, -0.5, 12.0), 40, gate = far / 2)

        low[2] - 1.0 shouldBe (-0.5 + 1.5 * expCurve(0.8) plusOrMinus 1e-9)
        low[20] shouldBe 1.0
        // ...and what goes above 1: a sustain of 1.5 is 1.5 - 0.5 g(0.5) at frame 5 in the law, a full depth here.
        renderNode(fm(0.0, 10.0, 1.5, 12.0), 40, gate = far / 2)[5] shouldBe (2.0 plusOrMinus 1e-12)
    }

    "host: the Ignitor pitch envelope (fractional attack, raw sustain, the release on floor(N))" {
        // 12 semitones: the output ratio is 2^level, so log2 of it is the level.
        fun pitch(a: Double, d: Double, s: Double, r: Double): Ignitor = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", sec(a)),
            decaySec = ParamIgnitor("d", sec(d)),
            releaseSec = ParamIgnitor("r", sec(r)),
            semitones = ParamIgnitor("st", 12.0),
            sustainLevel = ParamIgnitor("s", s),
            attackCurve = AdsrCurve.Linear,
            decayCurve = AdsrCurve.Linear,
            releaseCurve = AdsrCurve.Linear,
        )

        fun level(ratio: Double): Double = ln(ratio) / ln(2.0)

        level(renderNode(pitch(240.5, 100.0, 0.5, 12.0), 400, gate = far / 2)[240]) shouldBe (240.0 / 240.5 plusOrMinus 1e-9)
        level(renderNode(pitch(0.0, 10.0, 1.5, 12.0), 400, gate = far / 2)[300]) shouldBe (1.5 plusOrMinus 1e-9)

        // a 10.7-frame release is floor(10.7) = 10 frames: back on the note on its tenth frame
        val released = renderNode(pitch(0.0, 0.0, 1.0, 10.7), 40, gate = 20)

        released[28] shouldBe (2.0.pow(1.0 / 9.0) plusOrMinus 1e-9)
        released[29] shouldBe 1.0

        // a block that starts after the gate but inside the release still follows the release, frame by
        // frame: 300 frames from the gate at 20, frame 150 is 130 of 299 frames down
        level(renderNode(pitch(0.0, 0.0, 1.0, 300.0), 200, gate = 20)[150]) shouldBe (1.0 - 130.0 / 299.0 plusOrMinus 1e-9)
    }

    "host: the Ignitor filter envelope (read at each block's two ends, clamped to [0, 1], coefficients interpolated)" {
        // Oracle: the envelope written out here, read at each block's first frame and one past its last,
        // clamped to [0, 1], turned into SVF coefficients by the shared `computeSvfCoeffs` and stepped
        // linearly across the block through the plain TPT lowpass recursion. Only the coefficient helper
        // is shared with the node. Two envelopes: three different curves (Square up, Exponential down,
        // Cube on release, so a swap of any two is heard), and linear curves with a raw sustain of 1.5,
        // which only the host's [0, 1] clamp keeps from sweeping past the full depth. A third names no
        // curve and is checked against the Exponential oracle: the modulation default (decision D3).
        val a = 300.5
        val d = 200.0
        val r = 400.0
        val gate = 700
        val base = 400.0
        val depth = 24.0
        val q = 0.707
        val total = 1400

        fun curve(c: AdsrCurve, x: Double): Double = when (c) {
            AdsrCurve.Linear -> x
            AdsrCurve.Square -> x * x
            AdsrCurve.Cube -> x * x * x
            AdsrCurve.Exponential -> expCurve(x)
            else -> error("not used here")
        }

        fun check(sus: Double, ac: AdsrCurve, dc: AdsrCurve, rc: AdsrCurve, nameCurves: Boolean = true) {
            fun ads(p: Int): Double = when {
                p < a -> curve(ac, p / a)
                p < a + d -> sus + (1.0 - sus) * curve(dc, 1.0 - (p - a) / d)
                else -> sus
            }

            fun oracleLevel(pos: Int): Double =
                if (pos >= gate) ads(gate) * curve(rc, 1.0 - minOf((pos - gate) / (r - 1.0), 1.0)) else ads(pos)

            val input = DoubleArray(total) { i -> sin(2.0 * PI * 1500.0 * i / sampleRate) }
            val expected = DoubleArray(total)
            val c0 = SvfCoeffs()
            val c1 = SvfCoeffs()
            var ic1 = 0.0
            var ic2 = 0.0
            var start = 0

            while (start < total) {
                val n = minOf(blockFrames, total - start)

                computeSvfCoeffs(base * 2.0.pow(depth / 12.0 * oracleLevel(start).coerceIn(0.0, 1.0)), q, sampleRate.toDouble(), c0)
                computeSvfCoeffs(base * 2.0.pow(depth / 12.0 * oracleLevel(start + n).coerceIn(0.0, 1.0)), q, sampleRate.toDouble(), c1)

                var a1 = c0.a1
                var a2 = c0.a2
                var a3 = c0.a3

                for (i in 0 until n) {
                    val v3 = input[start + i] - ic2
                    val v1 = a1 * ic1 + a2 * v3
                    val v2 = ic2 + a2 * ic1 + a3 * v3

                    ic1 = (2.0 * v1 - ic1).flushState()
                    ic2 = (2.0 * v2 - ic2).flushState()
                    expected[start + i] = v2
                    a1 += (c1.a1 - c0.a1) / n
                    a2 += (c1.a2 - c0.a2) / n
                    a3 += (c1.a3 - c0.a3) / n
                }

                start += n
            }

            val source = object : Ignitor {
                private var cursor = 0

                override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
                    for (i in ctx.offset until ctx.windowEnd) {
                        buffer[i] = input[cursor]
                        cursor++
                    }
                }
            }
            val unnamed = FilterEnvDef(depth = depth, attackSec = sec(a), decaySec = sec(d), sustainLevel = sus, releaseSec = sec(r))
            val env = if (nameCurves) unnamed.copy(attackCurve = ac, decayCurve = dc, releaseCurve = rc) else unnamed
            val node = source.lowpass(ParamIgnitor("f", base), ParamIgnitor("q", q), env)
            val out = renderNode(node, total, gate = gate)

            for (i in 0 until total) {
                // 1e-9: the core bends Exponential through `fastExp` (relative error under 1e-10), the
                // oracle through the library `exp`.
                withClue("sustain $sus, $ac / $dc / $rc (named: $nameCurves), frame $i") { out[i] shouldBe (expected[i] plusOrMinus 1e-9) }
            }
        }

        check(0.3, AdsrCurve.Square, AdsrCurve.Exponential, AdsrCurve.Cube)
        check(1.5, AdsrCurve.Linear, AdsrCurve.Linear, AdsrCurve.Linear)
        check(0.3, AdsrCurve.Exponential, AdsrCurve.Exponential, AdsrCurve.Exponential, nameCurves = false)
    }

    "host: the strip's control-rate envelope (the offset on a zero release, the level clamped to [0, 1])" {
        val lin = AdsrCurve.Linear

        fun level(env: Voice.Envelope, blockStart: Double, gate: Double): Double =
            calculateControlRateEnvelope(env, blockStart, 0.0, gate, EnvelopeCore())

        level(Voice.Envelope(240.5, 100.0, 0.5, 12.0, lin, lin, lin), 240.0, far.toDouble()) shouldBe
            (240.0 / 240.5 plusOrMinus 1e-12)
        level(Voice.Envelope(0.0, 0.0, 1.0, 0.0, lin, lin, lin), 128.0, 128.0) shouldBe 0.0
        level(Voice.Envelope(0.0, 10.0, 1.5, 12.0, lin, lin, lin), 5.0, far.toDouble()) shouldBe 1.0

        // Each stage takes its own curve: a Square decay and a Cube release, a quarter into each
        // (g at a quarter is 0.0625 for Square, 0.015625 for Cube).
        val shaped = Voice.Envelope(0.0, 100.0, 0.2, 101.0, lin, AdsrCurve.Square, AdsrCurve.Cube)

        level(shaped, 75.0, far.toDouble()) shouldBe (0.2 + 0.8 * 0.0625 plusOrMinus 1e-15)
        level(shaped, 275.0, 200.0) shouldBe (0.2 * 0.015625 plusOrMinus 1e-15)
    }

    "a gate at or before the onset releases from exactly 0.0, for every curve, with and without an attack" {
        // Oracle: the voice was never open, so the envelope is 0.0 on every frame. Written out, the
        // unguarded law would release from the attack curve extrapolated to the gate: a Square attack of 0
        // frames at a gate of -50 would release from 2500, an attack of 0 at a gate of 0 from the decay top.
        // Every case is checked and every failing one is named, so a mutant shows WHICH curves break.
        val notSilent = mutableListOf<String>()

        for (curve in AdsrCurve.entries) {
            for (attack in listOf(0.0, 100.0)) {
                for (gate in listOf(-50, 0)) {
                    val c = EnvelopeCore().apply { prepare(attack, 100.0, 0.5, 200.0, gate, curve, curve, curve) }
                    val loudest = (0 until 400).maxOf { abs(c.at(it)) }

                    if (c.levelAtGate != 0.0 || loudest != 0.0) {
                        notSilent += "$curve, attack $attack, gate $gate: released from ${c.levelAtGate}, peak $loudest"
                    }
                }
            }
        }

        notSilent shouldBe emptyList()

        // And on a host: the chain adsr with a Square attack and a negative gate renders silence.
        val silent = renderNode(
            dc().adsr(0.0, sec(100.0), 1.0, sec(200.0), AdsrCurve.Square, AdsrCurve.Square, AdsrCurve.Square, 0.0),
            400,
            gate = -50,
        )

        silent.all { it == 0.0 } shouldBe true
    }

    "a stage too short to have a finite reciprocal is a zero-length stage: no NaN on any host" {
        // Oracle: a zero-length stage. Each host renders a Double.MIN_VALUE attack and decay exactly as it
        // renders an attack and decay of 0. A NaN level cannot hide behind a host whose output sanitizes it
        // (`safeOut` on the pitch and FM nodes, the filter's coefficient guard): the sanitized value is not
        // the sustain's. Soft, so a mutant names every host it breaks, not only the first.
        val tiny = Double.MIN_VALUE
        val lin = AdsrCurve.Linear

        fun fm(t: Double): Ignitor = fmModIgnitor(
            modulator = ParamIgnitor("m", 1.0), ratio = ParamIgnitor("ratio", 1.0), depth = ParamIgnitor("depth", 100.0),
            envAttackSec = ParamIgnitor("a", t), envDecaySec = ParamIgnitor("d", t),
            envSustainLevel = ParamIgnitor("s", 0.5), envReleaseSec = ParamIgnitor("r", sec(12.0)),
            freq = FreqIgnitor,
        )

        fun pitch(t: Double): Ignitor = pitchEnvelopeModIgnitor(
            attackSec = ParamIgnitor("a", t), decaySec = ParamIgnitor("d", t), releaseSec = ParamIgnitor("r", sec(12.0)),
            semitones = ParamIgnitor("st", 12.0), sustainLevel = ParamIgnitor("s", 0.5),
        )

        fun filter(t: Double): Ignitor {
            val env = FilterEnvDef(depth = 24.0, attackSec = t, decaySec = t, sustainLevel = 0.5, releaseSec = sec(12.0))

            return dc().lowpass(ParamIgnitor("f", 400.0), ParamIgnitor("q", 0.707), env)
        }

        val hosts: List<Pair<String, (Double) -> List<Double>>> = listOf(
            "core" to { t -> EnvelopeCore().apply { prepare(t, t, 0.5, 12.0, 20, lin, lin, lin) }.let { c -> (0 until 40).map { c.at(it) } } },
            "chain adsr" to { t -> renderNode(dc().adsr(t, t, 0.5, sec(12.0), lin, lin, lin, 0.0), 40, gate = 20).toList() },
            "strip VCA" to { t -> renderVca(Voice.Envelope(t, t, 0.5, 12.0, lin, lin, lin), 40, gate = 20).toList() },
            "strip control-rate envelope" to { t ->
                (0 until 40).map { calculateControlRateEnvelope(Voice.Envelope(t, t, 0.5, 12.0), it.toDouble(), 0.0, 20.0, EnvelopeCore()) }
            },
            "FM index envelope" to { t -> renderNode(fm(t), 40, gate = 20).toList() },
            "pitch envelope" to { t -> renderNode(pitch(t), 40, gate = 20).toList() },
            "filter envelope" to { t -> renderNode(filter(t), 400, gate = 200).toList() },
        )

        assertSoftly {
            val c = EnvelopeCore().apply { prepare(tiny, tiny, 0.5, 12.0, far, lin, lin, lin) }

            withClue("core: the sustain from frame 0") { c.at(0) shouldBe 0.5 }
            withClue("core: the sustain on frame 1") { c.at(1) shouldBe 0.5 }

            for ((name, render) in hosts) {
                val out = render(tiny)

                withClue("$name: finite") { out.all { it.isFinite() } shouldBe true }
                withClue("$name: renders as a zero-length attack and decay") { out shouldBe render(0.0) }
            }
        }
    }

})
