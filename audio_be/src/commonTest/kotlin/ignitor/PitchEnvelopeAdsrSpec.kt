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
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_be.fastExp2
import io.peekandpoke.klang.audio_be.safeOut
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.ceil
import kotlin.random.Random

/**
 * The Ignitor pitch envelope on ADSR fields (phase 3 step 3d(i)).
 *
 * Two ORACLES, both written out here from their definitions and never calling the envelope under
 * test (they share only the output primitives `fastExp2` and `safeOut`, and `adsrExpShape` for
 * the Exponential curve):
 *  - [headLaw] is the envelope as it was at HEAD `0f1b774f`, transcribed from its generate loop
 *    with the anchor at its default 0: attack from 0 to 1 by DIVISION on FRACTIONAL frame counts,
 *    decay back to 0, hold. Every shipped call is `(semitones, attack, decay)` in that shape.
 *  - [adsrLaw] is the new envelope from its definition: the chain `adsr`'s composition (decay
 *    `s + (1 - s) * shape(1 - p)`, release `levelAtGate * shape(1 - p)` on the chain's
 *    N-1 time base), with this envelope's fractional frames.
 *
 * Every comparison is on raw bits.
 */
class PitchEnvelopeAdsrSpec : StringSpec({

    val blockFrames = 128

    fun headLaw(relPos: Double, amount: Double, attackFrames: Double, decayFrames: Double): Double {
        var envLevel = 0.0
        if (relPos < attackFrames) {
            val progress = if (attackFrames > 0) relPos / attackFrames else 1.0
            envLevel = 0.0 + (1.0 - 0.0) * progress
        } else if (relPos < (attackFrames + decayFrames)) {
            val decayProgress = if (decayFrames > 0) (relPos - attackFrames) / decayFrames else 1.0
            envLevel = 1.0 - (1.0 - 0.0) * decayProgress
        }
        return safeOut(fastExp2(amount * envLevel / 12.0))
    }

    fun shape(curve: AdsrCurve, x: Double): Double = when (curve) {
        AdsrCurve.Linear -> x
        AdsrCurve.Square -> x * x
        AdsrCurve.Cube -> x * x * x
        AdsrCurve.SCurve -> if (x < 0.5) 2.0 * x * x else 1.0 - 2.0 * (1.0 - x) * (1.0 - x)
        AdsrCurve.InvSquare -> x * (2.0 - x)
        AdsrCurve.Exponential -> adsrExpShape(x)
    }

    class Env(
        val amount: Double, val a: Double, val d: Double, val s: Double, val r: Double,
        val ac: AdsrCurve = AdsrCurve.Linear, val dc: AdsrCurve = AdsrCurve.Linear, val rc: AdsrCurve = AdsrCurve.Linear,
    )

    fun adsrLevel(relPos: Double, e: Env, sr: Int): Double {
        val af = e.a * sr
        val df = e.d * sr

        return if (relPos < af) {
            shape(e.ac, if (af > 0) relPos / af else 1.0)
        } else if (relPos < af + df) {
            e.s + (1.0 - e.s) * shape(e.dc, 1.0 - (if (df > 0) (relPos - af) / df else 1.0))
        } else {
            e.s
        }
    }

    fun adsrLaw(absPos: Int, gateEnd: Int, e: Env, sr: Int): Double {
        val level = if (absPos >= gateEnd) {
            val rf = e.r * sr
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
        sampleRate = sr, voiceDurationFrames = gateEnd + 10 * blockFrames, gateEndFrame = gateEnd, releaseFrames = 0,
        scratchBuffers = ScratchBuffers(blockFrames),
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
            c.updateOffsetAndLength(offset, blockFrames - offset)
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

    "IDENTITY: sustain 0 and linear curves render HEAD's pitch envelope bit for bit when the gate outlasts attack + decay" {
        for (sr in listOf(44100, 48000)) {
            for ((amount, a, d) in shipped) {
                val af = a * sr
                val df = d * sr
                // The gate exactly at the end of the sweep, and well past it; the release 0 (every
                // migrated call) and a real one, which must not matter while the level is 0.
                val sweepEnd = ceil(af + df).toInt()

                for (gateEnd in listOf(sweepEnd, sweepEnd + 1000)) {
                    for (r in listOf(0.0, 0.05)) {
                        val c = ctx(sr, gateEnd)

                        renderMod(mod(Env(amount, a, d, 0.0, r)), c, blocks = 72) { pos, v ->
                            withClue("sr=$sr ($amount, $a, $d) gate=$gateEnd r=$r pos=$pos") {
                                v.toRawBits() shouldBe headLaw(pos.toDouble(), amount, af, df).toRawBits()
                            }
                        }
                    }
                }
            }
        }
    }

    "a gate that ends INSIDE the sweep now releases, as the chain's envelope does (not HEAD's law any more)" {
        val sr = 48000
        val e = Env(24.0, 0.001, 0.04, 0.0, 0.0)
        val gateEnd = 1000 // 20.8 ms, inside the 41 ms sweep
        val c = ctx(sr, gateEnd)
        var differs = 0

        renderMod(mod(e), c, blocks = 24) { pos, v ->
            withClue("pos=$pos") { v.toRawBits() shouldBe adsrLaw(pos, gateEnd, e, sr).toRawBits() }

            if (pos >= gateEnd) {
                withClue("release 0 returns to the note at the gate frame, pos=$pos") { v shouldBe safeOut(fastExp2(0.0)) }
            }

            if (v.toRawBits() != headLaw(pos.toDouble(), e.amount, e.a * sr, e.d * sr).toRawBits()) {
                differs++
            }
        }

        withClue("HEAD's law carried on sweeping after the gate") { differs shouldNotBe 0 }

        val withRelease = Env(24.0, 0.001, 0.04, 0.0, 0.03)
        renderMod(mod(withRelease), ctx(sr, gateEnd), blocks = 24) { pos, v ->
            withClue("with release, pos=$pos") { v.toRawBits() shouldBe adsrLaw(pos, gateEnd, withRelease, sr).toRawBits() }
        }
    }

    "sustain holds and the release returns to the note from the gate (settled blocks included)" {
        for (sr in listOf(44100, 48000)) {
            for (gateEnd in listOf(3000, 3050, 20 * blockFrames)) {
                val e = Env(12.0, 0.005, 0.03, 0.5, 0.02)
                renderMod(mod(e), ctx(sr, gateEnd), blocks = 60) { pos, v ->
                    withClue("sr=$sr gate=$gateEnd pos=$pos") { v.toRawBits() shouldBe adsrLaw(pos, gateEnd, e, sr).toRawBits() }
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
                val substituted = Env(12.0, 0.005, 0.03, 0.0, 0.02)

                renderMod(mod(Env(12.0, 0.005, 0.03, bad, 0.02)), ctx(sr, gateEnd), blocks = 40) { pos, v ->
                    withClue("sustain=$bad gate=$gateEnd pos=$pos") {
                        v.toRawBits() shouldBe adsrLaw(pos, gateEnd, substituted, sr).toRawBits()
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
                        val e = Env(amount, 0.004, 0.02, sustain, 0.05, rc = curve)

                        renderMod(mod(e), ctx(sr, gateEnd), blocks = 40) { pos, v ->
                            withClue("$curve amount=$amount sustain=$sustain gate=$gateEnd pos=$pos") {
                                v.toRawBits() shouldBe adsrLaw(pos, gateEnd, e, sr).toRawBits()
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
                Env(12.0, 0.01, 0.02, 0.3, 0.02, ac = curve),
                Env(12.0, 0.01, 0.02, 0.3, 0.02, dc = curve),
                Env(12.0, 0.01, 0.02, 0.3, 0.02, rc = curve),
            )
            for ((stage, e) in envs.withIndex()) {
                renderMod(mod(e), ctx(sr, gateEnd), blocks = 40) { pos, v ->
                    withClue("$curve on stage $stage, pos=$pos") { v.toRawBits() shouldBe adsrLaw(pos, gateEnd, e, sr).toRawBits() }
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
            sampleRate = sr, voiceDurationFrames = gateEnd + 10 * blockFrames, gateEndFrame = gateEnd, releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames), random = rng,
        )
        val out = DoubleArray(blocks * blockFrames)
        val buf = AudioBuffer(blockFrames)
        val mod = DoubleArray(blockFrames)

        repeat(blocks) { b ->
            c.voiceElapsedFrames = b * blockFrames
            c.updateOffsetAndLength(0, blockFrames)

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
            Env(24.0, 0.001, 0.04, 0.0, 0.0),
            Env(12.0, 0.004, 0.02, 0.4, 0.015, ac = AdsrCurve.Square, dc = AdsrCurve.Exponential, rc = AdsrCurve.Cube),
        )

        for (e in cases) {
            val dsl = IgnitorDsl.PitchEnvelope(
                inner = IgnitorDsl.Sine(),
                semitones = c(e.amount), attackSec = c(e.a), decaySec = c(e.d), sustainLevel = c(e.s), releaseSec = c(e.r),
                attackCurve = e.ac.takeUnless { it == AdsrCurve.Linear },
                decayCurve = e.dc.takeUnless { it == AdsrCurve.Linear },
                releaseCurve = e.rc.takeUnless { it == AdsrCurve.Linear },
            )
            val viaNode = renderVoice(dsl, sr, gateEnd, blocks = 40)
            val viaOracle = renderVoice(IgnitorDsl.Sine(), sr, gateEnd, blocks = 40) { pos -> adsrLaw(pos, gateEnd, e, sr) }

            for (i in viaNode.indices) {
                withClue("case s=${e.s} frame $i") { viaNode[i].toRawBits() shouldBe viaOracle[i].toRawBits() }
            }
        }
    }

    "the pitch release does NOT extend the voice's life" {
        IgnitorDsl.PitchEnvelope(
            inner = IgnitorDsl.Sine(), semitones = IgnitorDsl.Constant(12.0), releaseSec = IgnitorDsl.Constant(5.0),
        ).buildExciter(freqHz = 440.0).releaseTailSec.shouldBeNull()
    }
})
