/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.adsrExpShape
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import kotlin.random.Random

/**
 * `adsrCurves` on the four Ignitor filter nodes (phase 3 step 3d(i)).
 *
 * The LAW is pinned against two oracles written out here, never against the code under test:
 *  - [headEnv] is `computeFilterEnvelope` as it was at HEAD `0f1b774f`, which had no curve term:
 *    the unshaped envelope must still be it, bit for bit, or every filter sweep in the songs moves
 *    while decision D3 is open.
 *  - [curvedEnv] is the chain `adsr`'s composition from its definition, for a SHAPED stage.
 *
 * The WIRING is pinned through the runtime, on all four filter kinds: a curve on the node reaches
 * the SVF (oracle: the runtime door with the resolved `FilterEnvDef`), an unset curve is
 * `MOD_ENV_CURVE`, and a node at depth 0 renders the same whatever its curves.
 *
 * What this spec does NOT guard: the settled rule "a curve named alone does not switch the envelope
 * on" is a decision of the DOOR's compound fill (`fillFilterEnvelope` behind the Kotlin door, which
 * the script builder calls), and `KlangScriptFilterDoorParitySpec` pins it ("adsrCurves alone does
 * NOT switch the envelope on"). Inside the runtime the depth is the only switch (`SvfIgnitor.hasEnv`
 * is `depth != 0.0`, and no other code reads a `FilterEnvDef`), so a runtime that let a curve
 * "switch the envelope on" at depth 0 would still render `2^(0 * level) = 1` exactly: that mutant is
 * equivalent by construction, not a gap in this spec.
 */
class FilterEnvelopeCurvesSpec : StringSpec({

    val sr = 48000

    fun headLevelAt(absPos: Int, af: Int, df: Int, s: Double): Double = when {
        absPos < af -> {
            val attRate = if (af > 0) 1.0 / af else 1.0
            absPos * attRate
        }

        absPos < af + df -> {
            val decPos = absPos - af
            val decRate = if (df > 0) (1.0 - s) / df else 0.0
            1.0 - (decPos * decRate)
        }

        else -> s
    }

    fun headEnv(absPos: Int, gateEnd: Int, a: Double, d: Double, s: Double, r: Double): Double {
        val af = (a.coerceAtLeast(0.0) * sr).toInt()
        val df = (d.coerceAtLeast(0.0) * sr).toInt()
        val rf = (r.coerceAtLeast(0.0) * sr).toInt()
        val cs = s.coerceIn(0.0, 1.0)
        val v = if (absPos >= gateEnd) {
            val level = headLevelAt(gateEnd, af, df, cs)
            val relRate = if (rf > 0) level / rf else 1.0
            level - ((absPos - gateEnd) * relRate)
        } else {
            headLevelAt(absPos, af, df, cs)
        }
        return v.coerceIn(0.0, 1.0)
    }

    fun shape(curve: AdsrCurve, x: Double): Double = when (curve) {
        AdsrCurve.Linear -> x
        AdsrCurve.Square -> x * x
        AdsrCurve.Cube -> x * x * x
        AdsrCurve.SCurve -> if (x < 0.5) 2.0 * x * x else 1.0 - 2.0 * (1.0 - x) * (1.0 - x)
        AdsrCurve.InvSquare -> x * (2.0 - x)
        AdsrCurve.Exponential -> adsrExpShape(x)
    }

    /** The chain's composition for a shaped stage; an unshaped (Linear) stage is HEAD's form. */
    fun curvedLevelAt(absPos: Int, af: Int, df: Int, s: Double, ac: AdsrCurve, dc: AdsrCurve): Double = when {
        absPos < af -> shape(ac, absPos * (if (af > 0) 1.0 / af else 1.0))
        absPos < af + df -> if (dc == AdsrCurve.Linear) {
            headLevelAt(absPos, af, df, s)
        } else {
            s + (1.0 - s) * shape(dc, 1.0 - (absPos - af) * (if (df > 0) 1.0 / df else 1.0))
        }
        else -> s
    }

    fun curvedEnv(
        absPos: Int, gateEnd: Int, a: Double, d: Double, s: Double, r: Double,
        ac: AdsrCurve, dc: AdsrCurve, rc: AdsrCurve,
    ): Double {
        val af = (a * sr).toInt()
        val df = (d * sr).toInt()
        val rf = (r * sr).toInt()
        val v = if (absPos >= gateEnd) {
            val level = curvedLevelAt(gateEnd, af, df, s, ac, dc)
            val rel = absPos - gateEnd

            if (rc == AdsrCurve.Linear) {
                level - rel * (if (rf > 0) level / rf else 1.0)
            } else {
                val denom = if (rf > 1) rf - 1.0 else 1.0
                val off = if (rf > 1) 0.0 else 1.0
                level * shape(rc, 1.0 - minOf((rel + off) / denom, 1.0))
            }
        } else {
            curvedLevelAt(absPos, af, df, s, ac, dc)
        }
        return v.coerceIn(0.0, 1.0)
    }

    fun envAt(absPos: Int, gateEnd: Int, a: Double, d: Double, s: Double, r: Double, ac: AdsrCurve, dc: AdsrCurve, rc: AdsrCurve): Double {
        val c = IgniteContext(
            sampleRate = sr, voiceDurationFrames = gateEnd + sr, gateEndFrame = gateEnd, releaseFrames = 0,
            scratchBuffers = ScratchBuffers(128),
        )
        c.voiceElapsedFrames = absPos
        return computeFilterEnvelope(c, a, d, s, r, ac, dc, rc)
    }

    // Stage times include 0 and a single frame (the degenerate ramps) and gate ends inside the
    // attack, inside the decay and in the sustain.
    val shapes = listOf(
        doubleArrayOf(0.005, 0.02, 0.2, 0.03),
        doubleArrayOf(0.0, 0.01, 0.5, 0.0),
        doubleArrayOf(1.0 / 48000, 1.0 / 48000, 0.0, 1.0 / 48000),
        doubleArrayOf(0.002, 0.0, 0.7, 0.01),
        doubleArrayOf(0.01, 0.1, 1.0, 0.1),
    )
    val gates = listOf(50, 700, 3000)

    "UNSHAPED is HEAD's filter envelope, bit for bit, at every position" {
        for (sh in shapes) {
            for (gate in gates) {
                for (pos in 0 until gate + 3000 step 7) {
                    val (a, d, s, r) = sh.toList()
                    withClue("a=$a d=$d s=$s r=$r gate=$gate pos=$pos") {
                        envAt(pos, gate, a, d, s, r, AdsrCurve.Linear, AdsrCurve.Linear, AdsrCurve.Linear).toRawBits() shouldBe
                            headEnv(pos, gate, a, d, s, r).toRawBits()
                    }
                }
            }
        }
    }

    "a SHAPED stage takes the chain's composition, every curve on every stage" {
        for (curve in AdsrCurve.entries) {
            for (stage in 0..2) {
                val ac = if (stage == 0) curve else AdsrCurve.Linear
                val dc = if (stage == 1) curve else AdsrCurve.Linear
                val rc = if (stage == 2) curve else AdsrCurve.Linear

                for (sh in shapes) {
                    for (gate in gates) {
                        for (pos in 0 until gate + 3000 step 11) {
                            val (a, d, s, r) = sh.toList()
                            withClue("$curve on stage $stage, a=$a d=$d s=$s r=$r gate=$gate pos=$pos") {
                                envAt(pos, gate, a, d, s, r, ac, dc, rc).toRawBits() shouldBe
                                    curvedEnv(pos, gate, a, d, s, r, ac, dc, rc).toRawBits()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Through the runtime, all four filter kinds ───────────────────────────────────────────────

    val blockFrames = 128
    val blocks = 24
    val freqHz = 220.0
    val saw: IgnitorDsl = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    fun ctx(rng: Random) = IgniteContext(
        sampleRate = sr, voiceDurationFrames = blockFrames * blocks / 2, gateEndFrame = blockFrames * blocks / 2,
        releaseFrames = blockFrames, scratchBuffers = ScratchBuffers(blockFrames), random = rng,
    ).apply {
        updateOffsetAndLength(0, blockFrames)
        voiceElapsedFrames = 0
    }

    fun renderIgnitor(ignitor: Ignitor, rng: Random): List<Long> {
        val out = DoubleArray(blockFrames * blocks)
        val buffer = AudioBuffer(blockFrames)
        val context = ctx(rng)

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, context)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buffer[i]
            }

            context.voiceElapsedFrames += blockFrames
        }

        return out.map { it.toRawBits() }
    }

    fun render(dsl: IgnitorDsl): List<Long> {
        val rng = Random(7)
        return renderIgnitor(dsl.buildExciter(random = rng, freqHz = freqHz, sampleRate = sr, blockFrames = blockFrames).ignitor, rng)
    }

    fun renderRaw(wrap: (Ignitor) -> Ignitor): List<Long> {
        val rng = Random(7)
        return renderIgnitor(wrap(saw.buildExciter(random = rng, freqHz = freqHz).ignitor), rng)
    }

    val kinds = listOf("lowpass", "highpass", "bandpass", "notch")

    fun node(kind: String, env: Double, ac: AdsrCurve?, dc: AdsrCurve?, rc: AdsrCurve?): IgnitorDsl = when (kind) {
        "lowpass" -> saw.lowpass(900.0, env = env, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01, attackCurve = ac, decayCurve = dc, releaseCurve = rc)
        "highpass" -> saw.highpass(300.0, env = env, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01, attackCurve = ac, decayCurve = dc, releaseCurve = rc)
        "bandpass" -> saw.bandpass(900.0, env = env, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01, attackCurve = ac, decayCurve = dc, releaseCurve = rc)
        else -> saw.notch(900.0, env = env, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01, attackCurve = ac, decayCurve = dc, releaseCurve = rc)
    }

    fun raw(kind: String, env: FilterEnvDef): (Ignitor) -> Ignitor = { src ->
        when (kind) {
            "lowpass" -> src.lowpass(ConstantIgnitor(900.0), ConstantIgnitor(0.707), env = env)
            "highpass" -> src.highpass(ConstantIgnitor(300.0), ConstantIgnitor(0.707), env = env)
            "bandpass" -> src.bandpass(ConstantIgnitor(900.0), ConstantIgnitor(0.707), env = env)
            else -> src.notch(ConstantIgnitor(900.0), ConstantIgnitor(0.707), env = env)
        }
    }

    "each curve field of the node reaches the SVF, on all four kinds (oracle: the runtime door)" {
        for (kind in kinds) {
            // Three different curves, so a build arm that swaps two of them goes red.
            val dsl = node(kind, 24.0, AdsrCurve.Square, AdsrCurve.Exponential, AdsrCurve.Cube)
            val oracle = raw(
                kind,
                FilterEnvDef(
                    depth = 24.0, attackSec = 0.01, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01,
                    attackCurve = AdsrCurve.Square, decayCurve = AdsrCurve.Exponential, releaseCurve = AdsrCurve.Cube,
                ),
            )

            withClue(kind) { render(dsl) shouldBe renderRaw(oracle) }
        }
    }

    "an UNSET curve is MOD_ENV_CURVE, linear: bit for bit the explicit Linear and HEAD's sweep" {
        for (kind in kinds) {
            val unset = render(node(kind, 24.0, null, null, null))

            withClue("$kind: null == Linear") {
                unset shouldBe render(node(kind, 24.0, AdsrCurve.Linear, AdsrCurve.Linear, AdsrCurve.Linear))
            }
            withClue("$kind: == the runtime door with the envelope it always had") {
                unset shouldBe renderRaw(raw(kind, FilterEnvDef(depth = 24.0, attackSec = 0.01, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01)))
            }
            withClue("$kind: a shaped decay is heard (the control)") {
                render(node(kind, 24.0, null, AdsrCurve.Exponential, null)) shouldNotBe unset
            }
        }
    }

    // A characterization row, not a mutation target: depth 0 is the SVF's off switch, so the curves
    // cannot reach the output here by construction (see the class KDoc). It stays as the record that
    // an explicit `env = 0` with curves is the plain filter.
    "a node at depth 0 renders the same whatever its curves: the depth is the only switch" {
        for (kind in kinds) {
            withClue(kind) {
                render(node(kind, 0.0, AdsrCurve.Square, AdsrCurve.Exponential, AdsrCurve.Cube)) shouldBe
                    render(node(kind, 0.0, null, null, null))
            }
        }
    }
})
