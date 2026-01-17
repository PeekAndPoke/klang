package io.peekandpoke.klang.audio_fe

/**
 * High-precision time source for autonomous event fetcher progression
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object KlangTime {
    /**
     * Returns current time in milliseconds with high precision
     */
    fun nowMs(): Double
}
