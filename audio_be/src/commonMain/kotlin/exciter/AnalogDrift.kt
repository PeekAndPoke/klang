package io.peekandpoke.klang.audio_be.exciter

import io.peekandpoke.klang.common.math.PerlinNoise
import kotlin.random.Random

/**
 * Analog oscillator drift simulation using 1D Perlin noise.
 *
 * Simulates the micro-pitch instabilities of real analog VCOs:
 * - **Perlin noise** provides smooth, organic drift — no sudden jumps,
 *   band-limited randomness, naturally correlated over time.
 * - Values cluster near zero (small deviations common, large ones rare).
 * - Each instance walks through its own region of the noise field at a slow rate,
 *   producing independent drift per oscillator/voice.
 *
 * Per-sample cost: floor + 2 table lookups + fade (5 muls) + lerp + scale.
 * No Random.nextDouble() in the hot path, no allocations.
 *
 * When [analog] is 0.0, [active] is false. Oscillators should branch on [active]
 * to skip the drift path entirely (zero overhead).
 *
 * Usage in oscillator hot loop:
 * ```
 * phase += inc * drift.nextMultiplier()
 * ```
 */
class AnalogDrift(analog: Double) {
    /** Whether analog drift is active. Check this to skip the drift path entirely. */
    val active = analog > 0.0

    private val noise = PerlinNoise(Random)
    private val jitter = analog * 0.003

    // Current position in the noise field. Random start offset so each instance
    // (each voice in a super oscillator) walks a different region.
    private var pos = Random.nextDouble() * 256.0

    /**
     * Returns a multiplier for the phase increment.
     * Perlin noise provides smooth, organic values centered around 1.0.
     *
     * Only call when [active] is true.
     */
    fun nextMultiplier(): Double {
        val n = noise.noise(pos)
        pos += STEP
        return 1.0 + jitter * n
    }

    companion object {
        // Step size per sample through the noise field.
        // At 48kHz: ~0.003 * 48000 = 144 noise-units per second.
        // Perlin repeats every 256 units, so full cycle ≈ 1.8 seconds — nice slow organic wander.
        private const val STEP = 0.003
    }
}
