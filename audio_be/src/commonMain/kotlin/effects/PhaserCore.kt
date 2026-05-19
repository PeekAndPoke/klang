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
 *
 * **Control-rate α** — `α` (and the LFO) are computed at block boundaries via
 * [prepareBlock]; per-sample [step] linearly interpolates α from block-start to
 * block-end. This removes one `sin` and one `tan` from the per-sample hot path
 * (collectively ~50–150 ns/sample on Kotlin/JS). For typical LFO rates (≤10 Hz)
 * and block sizes (≤512 samples) the per-sample α error vs. sample-accurate
 * recomputation is < 10⁻⁵ relative — inaudible. **Callers MUST call
 * [prepareBlock] before each block of [step] calls.**
 *
 * **Inlining**: [step] is `inline` so the per-sample filter math expands at the
 * call site. Without this, the previous (non-inline) method-call boundary cost
 * ~25% on JVM and ~60% on Kotlin/JS for the stereo cylinder-bus path (two
 * `step()` calls per sample). The internal state members are `internal` (not
 * `private`) so the inline body can access them at call sites within the
 * `audio_be` module — `PhaserCore` is itself `internal class`, so this exposure
 * does not leak across modules.
 */
internal class PhaserCore(
    internal val stages: Int,
    sampleRate: Int,
) {
    internal val inverseSampleRate: Double = 1.0 / sampleRate
    internal val z1 = DoubleArray(stages)
    internal var lastOutput: Double = 0.0
    internal var lfoPhase: Double = 0.0

    /** Current bilinear allpass coefficient, linearly interpolated across the block. */
    internal var alpha: Double = 0.0

    /** Per-sample α increment for the current block, set by [prepareBlock]. */
    internal var alphaIncrement: Double = 0.0

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
     * Compute α at the current LFO position, advance the LFO by [blockFrames]
     * samples, compute α at the new position, and set up the per-sample
     * increment for [step]. **Must be called before processing each block.**
     *
     * `blockFrames = 0` is a no-op (alphaIncrement set to 0; α unchanged).
     */
    fun prepareBlock(blockFrames: Int) {
        if (blockFrames <= 0) {
            alphaIncrement = 0.0
            return
        }

        val alphaStart = alphaAt(lfoPhase)

        val phaseAdvance = rate * TWO_PI * inverseSampleRate * blockFrames
        val newPhase = (lfoPhase + phaseAdvance).wrapPhase(TWO_PI)

        val alphaEnd = alphaAt(newPhase)

        alpha = alphaStart
        alphaIncrement = (alphaEnd - alphaStart) / blockFrames
        lfoPhase = newPhase
    }

    private fun alphaAt(phase: Double): Double {
        val lfoValue = (sin(phase) + 1.0) * 0.5
        var modFreq = center + (lfoValue - 0.5) * sweep
        if (modFreq < MIN_MOD_FREQ_HZ) {
            modFreq = MIN_MOD_FREQ_HZ
        } else if (modFreq > MAX_MOD_FREQ_HZ) {
            modFreq = MAX_MOD_FREQ_HZ
        }
        val tanV = tan(PI * modFreq * inverseSampleRate)
        return (tanV - 1.0) / (tanV + 1.0)
    }

    /**
     * Process one input sample using the linearly-interpolated α set up by
     * [prepareBlock]. Advances α by [alphaIncrement] for the next sample.
     * Returns the **wet** sample — wrappers decide how to mix it with dry.
     *
     * `inline` is load-bearing — the body expands at the call site so the per-
     * sample math fuses with the caller's loop. See class KDoc.
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun step(x: Double): Double {
        // Snap NaN/Inf input to 0 — never poison the cascade or feedback state.
        val safeX = if (x.isFinite()) x else 0.0

        val a = alpha
        var signal = safeX + lastOutput * feedback
        for (s in 0 until stages) {
            val y = a * signal + z1[s]
            z1[s] = (signal - a * y).flushDenormal()
            signal = y
        }
        lastOutput = signal.flushDenormal()
        alpha = a + alphaIncrement
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
        internal const val MIN_MOD_FREQ_HZ: Double = 100.0

        /** Upper clamp for LFO-modulated breakpoint frequency. Stays well below Nyquist at typical sample rates. */
        internal const val MAX_MOD_FREQ_HZ: Double = 18000.0
    }
}
