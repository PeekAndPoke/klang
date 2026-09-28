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
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.notch
import kotlin.random.Random

/**
 * The cutoff-envelope curves of the four Ignitor filter nodes (phase 3 step 3d(i); since step 3c
 * they are index knobs, written by `curves` inside the filter builder's `adsr` lambda).
 *
 * The LAW of the envelope is not pinned here: it is the engine's one envelope law, and
 * `EnvelopeLawSpec` pins it against its oracles.
 *
 * The WIRING is pinned through the runtime, on all four filter kinds: a curve on the node reaches
 * the SVF (oracle: the runtime door with the resolved `FilterEnvDef`), an unset curve is
 * `MOD_ENV_CURVE`, and a node at depth 0 renders the same whatever its curves.
 *
 * What this spec does NOT guard: the settled rule "a curve named alone does not switch the envelope
 * on" is a decision of the DOOR's compound fill (`fillFilterEnvelope` behind the Kotlin door, which
 * the script builder calls), and `KlangScriptFilterDoorParitySpec` pins it on the Kotlin door (the
 * row "the curves live INSIDE adsr's own lambda since step 3c"; the script door can no longer write
 * a curve without naming the stage). Inside the runtime the depth is the only switch (`SvfIgnitor.hasEnv`
 * is `depth != 0.0`, and no other code reads a `FilterEnvDef`), so a runtime that let a curve
 * "switch the envelope on" at depth 0 would still render `2^(0 * level) = 1` exactly: that mutant is
 * equivalent by construction, not a gap in this spec.
 */
class FilterEnvelopeCurvesSpec : StringSpec({

    val sr = 48000

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

    "an UNSET curve is MOD_ENV_CURVE, exponential: bit for bit the explicit Exponential and the resolved envelope without curves" {
        for (kind in kinds) {
            val unset = render(node(kind, 24.0, null, null, null))

            withClue("$kind: null == Exponential") {
                unset shouldBe render(node(kind, 24.0, AdsrCurve.Exponential, AdsrCurve.Exponential, AdsrCurve.Exponential))
            }
            withClue("$kind: == the runtime door with a resolved envelope that names no curve") {
                unset shouldBe renderRaw(raw(kind, FilterEnvDef(depth = 24.0, attackSec = 0.01, decaySec = 0.02, sustainLevel = 0.2, releaseSec = 0.01)))
            }
            withClue("$kind: a shaped decay is heard (the control)") {
                render(node(kind, 24.0, null, AdsrCurve.Linear, null)) shouldNotBe unset
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
