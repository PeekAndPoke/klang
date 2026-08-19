/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * The phase-pool BYPASS guarantee (docs/tasks/unison-phase-pool.md §7.4): with `phasePool` off
 * (the default), every unison oscillator must render identically to the pre-phase-pool
 * engine — same rng consumption order (phases at voice creation, jitter in `computeVoiceGains`),
 * same samples. The golden values below were captured on the engine BEFORE the phase-pool
 * change (2026-08-11, commit fc05bf78) with a seeded rng and `analog = 0` (AnalogDrift seeds from
 * `Random.Default`, so drift cannot be part of a deterministic fixture).
 *
 * Comparison is by TIGHT RELATIVE TOLERANCE (1e-9), not raw bits: the render goes through
 * `Math.pow`/`Math.sin`, which are 1-ulp-specified, not bit-reproducible across JDKs/CPUs —
 * raw-bit goldens would false-red on another architecture. A genuine bypass break reorders the
 * rng stream and changes every start phase wholesale (sample deltas many orders of magnitude
 * above 1e-9), so the guard loses nothing. JVM-only: JS floating point diverges further.
 *
 * If this spec fails, the bypass is broken — that is a defect in the change, never a reason to
 * re-capture the goldens. Re-capture is legitimate only for a DELIBERATE sound change to the
 * legacy path itself.
 */
class PhasePoolBypassGoldenSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 4

    // "name|voices|freq" -> (first sample, positionally-weighted sum), stored as the exact raw
    // bits captured on the pre-change engine and decoded via Double.fromBits for the comparison.
    val goldens = mapOf(
        "superSaw|8|82.4069" to (-4631307293752131052L to 4668944552219627424L),
        "superSaw|8|440.0" to (-4632288011870096446L to 4657319975947328143L),
        "superSaw|11|82.4069" to (-4630733241765921432L to 4666242475224723113L),
        "superSaw|11|440.0" to (-4631705577927693217L to 4652440672336574510L),
        "superRamp|8|82.4069" to (4592064743102644756L to -4554427484635148384L),
        "superRamp|8|440.0" to (4591084024984679362L to -4566052060907447665L),
        "superRamp|11|82.4069" to (4592638795088854376L to -4557129561630052695L),
        "superRamp|11|440.0" to (4591666458927082591L to -4570931364518201298L),
        "superSquare|8|82.4069" to (4598315725601515205L to -4550082607452518128L),
        "superSquare|8|440.0" to (4598315725601515205L to -4563260665614983060L),
        "superSquare|11|82.4069" to (4591372646541300384L to -4555375934017155196L),
        "superSquare|11|440.0" to (4591372646541300384L to -4567438291225263336L),
        "superTri|8|82.4069" to (-4627714646193224951L to -4557692805699273317L),
        "superTri|8|440.0" to (-4627714646193224951L to 4658129254101608967L),
        "superTri|11|82.4069" to (-4632849514720810862L to -4560998030980759210L),
        "superTri|11|440.0" to (-4632849514720810862L to 4654031146193441304L),
        "superSine|8|82.4069" to (4601372997967630998L to -4551067379210204962L),
        "superSine|8|440.0" to (4601372997967630998L to -4564736937298395274L),
        "superSine|11|82.4069" to (4597559250652864848L to -4555532228182924500L),
        "superSine|11|440.0" to (4597559250652864848L to -4569076137655663696L),
    )

    fun render(sig: Ignitor, freqHz: Double): Pair<Double, Double> {
        val buffer = AudioBuffer(blockFrames)
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = sampleRate,
            gateEndFrame = sampleRate,
            releaseFrames = blockFrames,
            voiceEndFrame = sampleRate + blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        var weighted = 0.0
        var first = 0.0
        var idx = 0
        for (b in 0 until blocks) {
            ctx.apply { offset = 0; length = blockFrames; voiceElapsedFrames = b * blockFrames }
            sig.generate(buffer, freqHz, ctx)
            for (i in 0 until blockFrames) {
                if (idx == 0) {
                    first = buffer[i]
                }
                weighted += buffer[i] * (idx + 1)
                idx++
            }
        }
        return first to weighted
    }

    fun Double.shouldMatchGolden(goldenBits: Long) {
        val golden = Double.fromBits(goldenBits)
        abs(this - golden) shouldBeLessThanOrEqual 1e-9 * max(1.0, abs(golden))
    }

    val cases = listOf<Pair<String, (Ignitor, Ignitor, Random) -> Ignitor>>(
        "superSaw" to { v, a, r -> Ignitors.superSaw(voices = v, analog = a, rng = r) },
        "superRamp" to { v, a, r -> Ignitors.superRamp(voices = v, analog = a, rng = r) },
        "superSquare" to { v, a, r -> Ignitors.superSquare(voices = v, analog = a, rng = r) },
        "superTri" to { v, a, r -> Ignitors.superTri(voices = v, analog = a, rng = r) },
        "superSine" to { v, a, r -> Ignitors.superSine(voices = v, analog = a, rng = r) },
    )

    // The trailing-rng-consumption blind spot of these sample goldens (an extra draw after voice
    // init changes no sample of THIS note but reorders every later note) is closed by the exact
    // stream-position case in PhasePoolSpec — commonTest, so JS gets that guard too.

    for ((name, make) in cases) {
        "$name - phasePool off renders identical to the pre-change engine" {
            for (voices in listOf(8, 11)) {
                for (freq in listOf(82.4069, 440.0)) {
                    val sig = make(
                        ParamIgnitor("voices", voices.toDouble()),
                        ParamIgnitor("analog", 0.0),
                        Random(42),
                    )
                    val (first, weighted) = render(sig, freq)
                    val (goldenFirst, goldenWeighted) = goldens.getValue("$name|$voices|$freq")
                    first.shouldMatchGolden(goldenFirst)
                    weighted.shouldMatchGolden(goldenWeighted)
                }
            }
        }
    }
})
