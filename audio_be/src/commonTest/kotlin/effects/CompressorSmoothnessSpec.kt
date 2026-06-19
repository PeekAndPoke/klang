package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.math.pow

/**
 * Guards for the gain-smoothing pass (`docs/tasks/compressor-gain-smoothing.md`):
 *  - Fix 2: the gain-skip cutoff no longer snaps a small-but-real reduction to a hard 1.0 step.
 *  - Fix 1: the smoothstep attack<->release coefficient blend keeps the master limiter fast + bounded.
 *
 * Steady-state assertions only (the envelope is settled first), so they stay tau-agnostic — the blend
 * width and skip cutoff can be tuned by ear without breaking these. The blend's *feel* improvement is a
 * by-ear matter; these only pin the safety properties.
 */
class CompressorSmoothnessSpec : StringSpec({

    val sampleRate = 44100

    "a tiny reduction is applied, not snapped to 1.0 (gain-skip step removed)" {
        // Settled reduction ≈ -0.005 dB — between the old -0.01 dB cutoff (which would clamp the
        // gain to a hard 1.0) and the new -1e-4 cutoff (which applies it continuously).
        val thresholdDb = -20.0
        val ratio = 4.0
        val overshootDb = 0.006667                  // reduction = (1/4 - 1) * overshoot ≈ -0.005 dB
        val inputDb = thresholdDb + overshootDb
        val level = 10.0.pow(inputDb / 20.0)

        val compressor = Compressor(
            sampleRate = sampleRate,
            thresholdDb = thresholdDb,
            ratio = ratio,
            kneeDb = 0.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        val buffer = AudioBuffer(4000) { level }    // long enough to fully settle the follower
        compressor.process(buffer, 0, 4000)

        val settledGain = buffer[3999] / level
        settledGain shouldBeLessThan 1.0            // not snapped open (old code returned exactly 1.0 here)
        settledGain shouldBeGreaterThan 0.999       // but only a whisker of reduction
    }

    "master limiter still bounds a hot sustained signal (blend is limiter-safe)" {
        val limiter = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -1.0,
            ratio = 20.0,
            kneeDb = 2.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        val level = 2.0                             // +6 dBFS — well over the -1 dB ceiling
        val buffer = AudioBuffer(8000) { level }
        limiter.process(buffer, 0, 8000)

        val settled = abs(buffer[7999])
        settled shouldBeLessThan 1.0                // never clips to full scale
        settled shouldBeLessThan 0.95               // clearly pulled down toward the ceiling
    }

    "limiter attack still engages quickly after the blend" {
        val limiter = Compressor(
            sampleRate = sampleRate,
            thresholdDb = -1.0,
            ratio = 20.0,
            kneeDb = 2.0,
            attackSeconds = 0.001,
            releaseSeconds = 0.1,
        )

        val level = 2.0
        val buffer = AudioBuffer(2000) { level }    // cold start: envelope at SILENCE_DB
        limiter.process(buffer, 0, 2000)

        // From a cold start the error is huge, so the blend uses full attackCoeff — the gain must be
        // below full scale well within 10 ms (441 frames @ 44.1k). The blend only softens the final
        // ~dB of approach, never the initial transient.
        abs(buffer[441]) shouldBeLessThan 1.0
    }
})
