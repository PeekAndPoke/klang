package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.wrapPhase
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

/**
 * Single per-channel phaser kernel — `stages`-count first-order allpass cascade with
 * sine-LFO-modulated breakpoint and feedback. Used by all three Phaser surfaces:
 *   - [Phaser]                                          — cylinder bus, 2× PhaserCore for stereo
 *   - `voices/strip/filter/StripPhaserRenderer.kt`      — per-voice mono `BlockRenderer`
 *   - `ignitor/IgnitorEffects.kt::PhaserIgnitor`        — Ignitor DSL, mono, lazy-init from `ctx.sampleRate`
 *
 * **Topology** — bilinear 1st-order allpass per stage:
 *   - `α = (tan(π·f/fs) − 1) / (tan(π·f/fs) + 1)`
 *   - per-stage update: `y = α·x + s;  s = x − α·y`
 *   - cascade with feedback: `input + lastOutput · feedback → cascade → output → lastOutput`
 *
 * The LFO sweeps the breakpoint frequency through `[MIN_MOD_FREQ_HZ, MAX_MOD_FREQ_HZ]`
 * around `center`. The kernel is **mono** and stateful; instantiate one per channel.
 *
 * Wrappers own their own input/output mixing (additive vs. crossfade) and stereo
 * dispatch — this kernel just produces the wet sample.
 */
internal class PhaserCore(
    private val stages: Int,
    sampleRate: Int,
) {
    private val inverseSampleRate: Double = 1.0 / sampleRate
    private val z1 = DoubleArray(stages)
    private var lastOutput: Double = 0.0
    private var lfoPhase: Double = 0.0

    /** LFO frequency in Hz. Setter silently ignores non-finite values. */
    var rate: Double = 0.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Center breakpoint frequency in Hz around which the LFO sweeps. */
    var center: Double = 1000.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** LFO sweep width in Hz. Effective range = `[center - sweep/2, center + sweep/2]` clamped to `[MIN_MOD_FREQ_HZ, MAX_MOD_FREQ_HZ]`. */
    var sweep: Double = 1000.0
        set(value) {
            if (!value.isFinite()) return
            field = value
        }

    /** Feedback amount, clamped to `[0, MAX_FEEDBACK]`. NaN/Inf silently ignored. */
    var feedback: Double = 0.5
        set(value) {
            if (!value.isFinite()) return
            field = value.coerceIn(0.0, MAX_FEEDBACK)
        }

    /**
     * Process one input sample. Advances the LFO, computes the allpass coefficient,
     * runs the cascade with feedback, and updates state. Returns the **wet** sample —
     * wrappers decide how to mix it with the dry input.
     */
    fun step(x: Double): Double {
        // Snap NaN/Inf input to 0 — never poison the cascade or feedback state.
        val safeX = if (x.isFinite()) x else 0.0

        // 1. LFO advance — `wrapPhase` handles extreme rates safely (can't skip-wrap).
        lfoPhase = wrapPhase(lfoPhase + rate * TWO_PI * inverseSampleRate, TWO_PI)
        val lfoValue = (sin(lfoPhase) + 1.0) * 0.5

        // 2. Modulated breakpoint frequency, clamped to safe range.
        var modFreq = center + (lfoValue - 0.5) * sweep
        if (modFreq < MIN_MOD_FREQ_HZ) {
            modFreq = MIN_MOD_FREQ_HZ
        } else if (modFreq > MAX_MOD_FREQ_HZ) {
            modFreq = MAX_MOD_FREQ_HZ
        }

        // 3. Bilinear allpass coefficient.
        val tanV = tan(PI * modFreq * inverseSampleRate)
        val alpha = (tanV - 1.0) / (tanV + 1.0)

        // 4. Allpass cascade with feedback.
        var signal = safeX + lastOutput * feedback
        for (s in 0 until stages) {
            val y = alpha * signal + z1[s]
            z1[s] = flushDenormal(signal - alpha * y)
            signal = y
        }
        lastOutput = flushDenormal(signal)
        return signal
    }

    /** Clear allpass state and `lastOutput`. LFO phase is preserved for cross-note continuity. */
    fun reset() {
        for (i in 0 until stages) {
            z1[i] = 0.0
        }
        lastOutput = 0.0
    }

    companion object {
        /** Canonical phaser stage count — 2 notches, "Phase 90" / "Small Stone" character. */
        const val DEFAULT_STAGES: Int = 4

        /** Maximum stable feedback amount before self-oscillation. */
        const val MAX_FEEDBACK: Double = 0.95

        /** Lower clamp for LFO-modulated breakpoint frequency. */
        private const val MIN_MOD_FREQ_HZ: Double = 100.0

        /** Upper clamp for LFO-modulated breakpoint frequency. Stays well below Nyquist at typical sample rates. */
        private const val MAX_MOD_FREQ_HZ: Double = 18000.0
    }
}
