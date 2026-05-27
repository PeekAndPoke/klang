package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.ignitor.PolyAnalogDrift.Companion.BASE_STEP
import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.random.Random

/**
 * Polyphonic analog drift — independent drift state for each unison voice.
 *
 * The single-voice [AnalogDrift] is fine for mono oscillators, but in
 * unison oscillators (SuperSaw, SuperSine, etc.) a single shared drift
 * causes all unison voices to wobble in lockstep. That kills the analog
 * feel — real analog supersaws sound wide and organic precisely because
 * each VCO has its own independent pitch instability.
 *
 * This class allocates **one** [PerlinNoise] field (shared) and **N**
 * independent walking positions, one per voice. Each voice also gets a
 * slightly randomised step size (±25% of [BASE_STEP]) so the wobble
 * periods are incommensurate — no two voices ever sync back up.
 *
 * Usage in the audio hot path:
 *
 * ```kotlin
 * val muls = drift.multipliers           // hoist reference once
 * for (i in offset until end) {
 *     drift.advanceAll()                 // updates muls[0..v-1] in place
 *     for (n in 0 until v) {
 *         val dt = baseDt[n] * muls[n]
 *         // … use dt for voice n …
 *     }
 * }
 * ```
 *
 * [advanceAll] is `inline` so on Kotlin/JS its body expands at the call
 * site — zero method dispatch in the per-sample loop. The internal
 * state ([noise]/[jitter]/[pos]/[step]) is marked `@PublishedApi
 * internal` to allow inlining without leaking those fields to the
 * public surface.
 *
 * When [active] is false, callers should skip [advanceAll] — multipliers
 * stay at 1.0 (their initial value), so any later `dt * multipliers[n]`
 * is a no-op multiply that the JIT/optimizer can eliminate.
 */
class PolyAnalogDrift(analog: Double, voiceCount: Int, rng: Random = Random) {
    /** Whether analog drift is active. When false, [advanceAll] need not be called. */
    val active: Boolean = analog > 0.0

    /**
     * Per-voice phase-increment multipliers. Populated by [advanceAll].
     *
     * Initialised to `1.0` so callers that skip [advanceAll] (inactive case)
     * can still multiply by these without effect.
     */
    val multipliers: DoubleArray = DoubleArray(voiceCount) { 1.0 }

    /** Shared Perlin noise field. Every voice reads from it at its own [pos]. */
    @PublishedApi
    internal val noise: PerlinNoise = PerlinNoise(rng)

    /** Phase-increment jitter amount: `analog * 0.003`. Constant across voices. */
    @PublishedApi
    internal val jitter: Double = analog * 0.003

    /**
     * Per-voice walking position in the noise field. Each voice starts at a
     * random offset so voices walk completely different regions.
     */
    @PublishedApi
    internal val pos: DoubleArray = DoubleArray(voiceCount) { rng.nextDouble() * 256.0 }

    /**
     * Per-voice step size. Randomised ±25% around [BASE_STEP] so the wobble
     * periods of different voices are incommensurate — they never re-sync.
     */
    @PublishedApi
    internal val step: DoubleArray = DoubleArray(voiceCount) {
        BASE_STEP * (1.0 + (rng.nextDouble() - 0.5) * 0.5)
    }

    /**
     * Advance the drift for all voices by one sample. Populates [multipliers].
     *
     * `inline` so on Kotlin/JS the body expands at the caller, removing
     * per-sample method dispatch in the hot loop. The JVM JIT inlines simple
     * final methods regardless.
     *
     * Only meaningful to call when [active] is true.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun advanceAll() {
        val muls = multipliers
        val voicePos = pos
        val voiceStep = step
        val noiseField = noise
        val jit = jitter
        val n = muls.size
        for (idx in 0 until n) {
            val p = voicePos[idx]
            muls[idx] = 1.0 + jit * noiseField.noise(p)
            voicePos[idx] = p + voiceStep[idx]
        }
    }

    companion object {
        // Same base step as [AnalogDrift.STEP] — keeps the wobble character consistent.
        // Perlin field repeats every 256 units, so one full cycle ≈ 1.8s at 48kHz.
        private const val BASE_STEP = 0.003
    }
}
