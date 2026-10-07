/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.ADSR_EXP_NORM
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_be.utils.fastExp2
import io.peekandpoke.klang.audio_be.utils.safeOut
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.ADSR_EXP_K
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * The Ignitor pitch envelope on ADSR fields (phase 3 step 3d(i)).
 *
 * One ORACLE, written out here from its definition and never calling the envelope under test (it
 * shares only the output primitives `fastExp2` and `safeOut`, and `adsrExpShape` for the Exponential
 * curve): [adsrLaw], the engine's envelope law (`EnvelopeCore`) with this host's raw mapping, i.e.
 * the chain `adsr`'s composition (decay `s + (1 - s) * shape(1 - p)`, release
 * `levelAtGate * shape(1 - p)`), fractional attack and decay frames with progress by a reciprocal,
 * and the release on `floor(N) - 1` frames. (The step 3d(i) identity against the pre-ADSR envelope
 * retired with phase 3's envelope law, which changed the progress arithmetic by an ulp.)
 *
 * Every comparison is on raw bits.
 */
class PitchEnvelopeAdsrSpec : StringSpec({

    val blockFrames = 128

    fun shape(curve: AdsrCurve, x: Double): Double = when (curve) {
        AdsrCurve.Linear -> x
        AdsrCurve.Square -> x * x
        AdsrCurve.Cube -> x * x * x
        AdsrCurve.SCurve -> if (x < 0.5) 2.0 * x * x else 1.0 - 2.0 * (1.0 - x) * (1.0 - x)
        AdsrCurve.InvSquare -> x * (2.0 - x)
        AdsrCurve.Exponential -> adsrExpShape(x = x, k = ADSR_EXP_K, norm = ADSR_EXP_NORM)
    }

    class Env(
        val amount: Double, val a: Double, val d: Double, val s: Double, val r: Double,
        val ac: AdsrCurve = AdsrCurve.Linear, val dc: AdsrCurve = AdsrCurve.Linear, val rc: AdsrCurve = AdsrCurve.Linear,
    )

    fun adsrLevel(relPos: Double, e: Env, sr: Int): Double {
        val af = e.a * sr
        val df = e.d * sr

        return if (relPos < af) {
            shape(e.ac, relPos * (1.0 / af))
        } else if (relPos < af + df) {
            e.s + (1.0 - e.s) * shape(e.dc, 1.0 - (relPos - af) * (1.0 / df))
        } else {
            e.s
        }
    }

    fun adsrLaw(absPos: Int, gateEnd: Int, e: Env, sr: Int): Double {
        val level = if (absPos >= gateEnd) {
            val rf = floor(e.r * sr)
            val denom = if (rf > 1.0) rf - 1.0 else 1.0
            val off = if (rf > 1.0) 0.0 else 1.0
            val p = minOf(((absPos - gateEnd) + off) / denom, 1.0)
            adsrLevel(gateEnd.toDouble(), e, sr) * shape(e.rc, 1.0 - p)
        } else {
            adsrLevel(absPos.toDouble(), e, sr)
        }
        return safeOut(fastExp2(e.amount * level / 12.0))
    }

    fun ctx(sr: Int, gateEnd: Int) = IgniteContext(
        sampleRate = sr, voiceDurationFrames = gateEnd + 10 * blockFrames, gateEndFrame = gateEnd,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = testRandom,
    )

    fun mod(e: Env) = pitchEnvelopeModIgnitor(
        attackSec = ParamIgnitor("a", e.a),
        decaySec = ParamIgnitor("d", e.d),
        releaseSec = ParamIgnitor("r", e.r),
        semitones = ParamIgnitor("amount", e.amount),
        sustainLevel = ParamIgnitor("s", e.s),
        attackCurve = e.ac,
        decayCurve = e.dc,
        releaseCurve = e.rc,
    )

    /** Renders [blocks] blocks of [ig]; odd blocks start 16 frames in (a mid-block onset window). */
    fun renderMod(ig: Ignitor, c: IgniteContext, blocks: Int, check: (absPos: Int, value: Double) -> Unit) {
        val buf = AudioBuffer(blockFrames)

        repeat(blocks) { b ->
            val offset = if (b % 2 == 0) 0 else 16
            c.updateOffsetAndLength(offset = offset, length = blockFrames - offset)
            c.voiceElapsedFrames = b * blockFrames
            buf.fill(-1.0)
            ig.generate(buf, 440.0, c)

            for (i in offset until blockFrames) {
                check(b * blockFrames + (i - offset), buf[i])
            }
        }
    }

    // The twelve shipped calls, `(semitones, attack, decay)`, and one that hits a FRACTIONAL frame
    // count (0.005 s at 44100 Hz is 220.5 frames) with a negative sweep.
    val shipped = listOf(
        Triple(0.5, 0.001, 0.02), Triple(9.0, 0.001, 0.10), Triple(0.4, 0.001, 0.04), Triple(0.15, 0.01, 0.03),
        Triple(0.5, 0.003, 0.02), Triple(1.0, 0.02, 0.1), Triple(24.0, 0.001, 0.04), Triple(48.0, 0.001, 0.05),
        Triple(0.3, 0.001, 0.02), Triple(-12.0, 0.005, 0.05),
    )

    "the shipped calls (sustain 0, linear curves) render the law bit for bit, at both rates" {
        for (sr in listOf(44100, 48000)) {
            for ((amount, a, d) in shipped) {
                // The gate exactly at the end of the sweep, and well past it; the release 0 (every
                // migrated call) and a real one, which must not matter while the level is 0.
                val sweepEnd = ceil((a + d) * sr).toInt()

                for (gateEnd in listOf(sweepEnd, sweepEnd + 1000)) {
                    for (r in listOf(0.0, 0.05)) {
                        val e = Env(amount = amount, a = a, d = d, s = 0.0, r = r)

                        renderMod(mod(e), ctx(sr = sr, gateEnd = gateEnd), blocks = 72) { pos, v ->
                            withClue("sr=$sr ($amount, $a, $d) gate=$gateEnd r=$r pos=$pos") {
                                v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr).toRawBits()
                            }
                        }
                    }
                }
            }
        }
    }

    "a gate that ends INSIDE the sweep releases, as the chain's envelope does" {
        val sr = 48000
        val e = Env(amount = 24.0, a = 0.001, d = 0.04, s = 0.0, r = 0.0)
        val gateEnd = 1000 // 20.8 ms, inside the 41 ms sweep
        val c = ctx(sr = sr, gateEnd = gateEnd)

        renderMod(mod(e), c, blocks = 24) { pos, v ->
            withClue("pos=$pos") { v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr).toRawBits() }

            if (pos >= gateEnd) {
                withClue("release 0 returns to the note at the gate frame, pos=$pos") { v shouldBe safeOut(fastExp2(0.0)) }
            }

        }

        val withRelease = Env(amount = 24.0, a = 0.001, d = 0.04, s = 0.0, r = 0.03)
        renderMod(mod(withRelease), ctx(sr = sr, gateEnd = gateEnd), blocks = 24) { pos, v ->
            withClue("with release, pos=$pos") { v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = withRelease, sr = sr).toRawBits() }
        }
    }

    "sustain holds and the release returns to the note from the gate (settled blocks included)" {
        for (sr in listOf(44100, 48000)) {
            for (gateEnd in listOf(3000, 3050, 20 * blockFrames)) {
                val e = Env(amount = 12.0, a = 0.005, d = 0.03, s = 0.5, r = 0.02)
                renderMod(mod(e), ctx(sr = sr, gateEnd = gateEnd), blocks = 60) { pos, v ->
                    withClue("sr=$sr gate=$gateEnd pos=$pos") { v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr).toRawBits() }
                }
            }
        }
    }

    "a NON-FINITE sustain reads as unset: the sustain-0 law, never NaN and never a frozen ratio" {
        // The house rule of the chain `adsr` (`finiteOr`): NaN, +Inf and -Inf all take the node's
        // default 0. Without it a NaN reaches `fastExp2` and `safeOut` turns the settled ratio into
        // 0.0, a pitch frozen at 0 Hz; an infinity poisons the decay the same way.
        val sr = 48000

        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            for (gateEnd in listOf(1000, 3050, 20 * blockFrames)) {
                val substituted = Env(amount = 12.0, a = 0.005, d = 0.03, s = 0.0, r = 0.02)

                renderMod(mod(Env(amount = 12.0, a = 0.005, d = 0.03, s = bad, r = 0.02)), ctx(sr = sr, gateEnd = gateEnd), blocks = 40) { pos, v ->
                    withClue("sustain=$bad gate=$gateEnd pos=$pos") {
                        v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = substituted, sr = sr).toRawBits()
                    }
                }
            }
        }
    }

    "a release from level 0 renders bit for bit the per-sample law" {
        // The fast path for every migrated call (sustain 0, gate after the sweep): each sample
        // would be `0 * shape(x)`, a signed zero, and `fastExp2` gives exactly 1.0 for +0.0 and
        // -0.0 alike. Every release curve (the Exponential one computes `fastExp(k x) - 1`), both
        // signs of the sweep, a release still running when the block starts, and a -0.0 sustain.
        val sr = 48000

        for (curve in AdsrCurve.entries) {
            for (amount in listOf(24.0, -12.0)) {
                for (sustain in listOf(0.0, -0.0)) {
                    for (gateEnd in listOf(2000, 2050, 20 * blockFrames)) {
                        val e = Env(amount = amount, a = 0.004, d = 0.02, s = sustain, r = 0.05, rc = curve)

                        renderMod(mod(e), ctx(sr = sr, gateEnd = gateEnd), blocks = 40) { pos, v ->
                            withClue("$curve amount=$amount sustain=$sustain gate=$gateEnd pos=$pos") {
                                v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr).toRawBits()
                            }
                        }
                    }
                }
            }
        }
    }

    "every curve on every stage follows the chain's shape" {
        val sr = 48000
        val gateEnd = 2600

        for (curve in AdsrCurve.entries) {
            val envs = listOf(
                Env(amount = 12.0, a = 0.01, d = 0.02, s = 0.3, r = 0.02, ac = curve),
                Env(amount = 12.0, a = 0.01, d = 0.02, s = 0.3, r = 0.02, dc = curve),
                Env(amount = 12.0, a = 0.01, d = 0.02, s = 0.3, r = 0.02, rc = curve),
            )
            for ((stage, e) in envs.withIndex()) {
                renderMod(mod(e), ctx(sr = sr, gateEnd = gateEnd), blocks = 40) { pos, v ->
                    withClue("$curve on stage $stage, pos=$pos") { v.toRawBits() shouldBe adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr).toRawBits() }
                }
            }
        }
    }

    // ── Through the runtime: the node's fields reach the envelope ────────────────────────────────

    /** A voice's samples: [dsl] built by the runtime, or a plain sine with [ratio] as the strip's phaseMod. */
    fun renderVoice(dsl: IgnitorDsl, sr: Int, gateEnd: Int, blocks: Int, ratio: ((Int) -> Double)? = null): DoubleArray {
        val rng = Random(7)
        val ignitor = dsl.buildExciter(soundIndex = 0, random = rng, freqHz = 220.0).ignitor
        val c = IgniteContext(
            sampleRate = sr, voiceDurationFrames = gateEnd + 10 * blockFrames, gateEndFrame = gateEnd,
            scratchBuffers = ScratchBuffers(blockFrames), random = rng,
        )
        val out = DoubleArray(blocks * blockFrames)
        val buf = AudioBuffer(blockFrames)
        val mod = DoubleArray(blockFrames)

        repeat(blocks) { b ->
            c.voiceElapsedFrames = b * blockFrames
            c.updateOffsetAndLength(offset = 0, length = blockFrames)

            if (ratio != null) {
                for (i in 0 until blockFrames) {
                    mod[i] = ratio(b * blockFrames + i)
                }
                c.phaseMod = mod
            }

            buf.fill(0.0)
            ignitor.generate(buf, 220.0, c)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    "the runtime hands every field of the node to the envelope: a PitchEnvelope voice == a sine driven by the oracle" {
        val sr = 48000
        val gateEnd = 3000
        fun c(v: Double) = IgnitorDsl.Constant(v)

        val cases = listOf(
            Env(amount = 24.0, a = 0.001, d = 0.04, s = 0.0, r = 0.0),
            Env(amount = 12.0, a = 0.004, d = 0.02, s = 0.4, r = 0.015, ac = AdsrCurve.Square, dc = AdsrCurve.Exponential, rc = AdsrCurve.Cube),
        )

        for (e in cases) {
            val dsl = IgnitorDsl.PitchEnvelope(
                inner = IgnitorDsl.Sine(),
                semitones = c(e.amount), attackSec = c(e.a), decaySec = c(e.d), sustainLevel = c(e.s), releaseSec = c(e.r),
                attackCurve = AdsrCurves.knob(e.ac),
                decayCurve = AdsrCurves.knob(e.dc),
                releaseCurve = AdsrCurves.knob(e.rc),
            )
            val viaNode = renderVoice(dsl = dsl, sr = sr, gateEnd = gateEnd, blocks = 40)
            val viaOracle = renderVoice(dsl = IgnitorDsl.Sine(), sr = sr, gateEnd = gateEnd, blocks = 40) { pos -> adsrLaw(absPos = pos, gateEnd = gateEnd, e = e, sr = sr) }

            for (i in viaNode.indices) {
                withClue("case s=${e.s} frame $i") { viaNode[i].toRawBits() shouldBe viaOracle[i].toRawBits() }
            }
        }
    }

    "the pitch release does NOT extend the voice's life" {
        IgnitorDsl.PitchEnvelope(
            inner = IgnitorDsl.Sine(), semitones = IgnitorDsl.Constant(12.0), releaseSec = IgnitorDsl.Constant(5.0),
        ).buildExciter(freqHz = 440.0, random = testRandom).releaseTailSec.shouldBeNull()
    }
})
