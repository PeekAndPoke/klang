package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

/**
 * Filter envelope for dynamic filter cutoff modulation.
 *
 * Uses ADSR envelope parameters plus a depth parameter to control the amount of modulation.
 */
@Serializable
data class FilterEnvelope(
    /** Attack time in seconds - time to reach peak modulation */
    val attack: Double? = null,
    /** Decay time in seconds - time to reach sustain level */
    val decay: Double? = null,
    /** Sustain level (0.0 to 1.0) - level maintained during note hold */
    val sustain: Double? = null,
    /** Release time in seconds - time to return to baseline after note off */
    val release: Double? = null,
    /** Modulation depth (0.0 to 1.0) - amount of filter envelope effect */
    val depth: Double? = null,
) {
    /**
     * Resolved envelope with all non-null values.
     */
    data class Resolved(
        val attack: Double,
        val decay: Double,
        val sustain: Double,
        val release: Double,
        val depth: Double,
    )

    /**
     * Merges this envelope with a fallback, using fallback values for any null fields.
     */
    fun mergeWith(fallback: FilterEnvelope): FilterEnvelope {
        return FilterEnvelope(
            attack = attack ?: fallback.attack,
            decay = decay ?: fallback.decay,
            sustain = sustain ?: fallback.sustain,
            release = release ?: fallback.release,
            depth = depth ?: fallback.depth,
        )
    }

    /**
     * Resolves this envelope to non-null values using defaults.
     */
    fun resolve(): Resolved {
        return Resolved(
            attack = attack ?: 0.01,
            decay = decay ?: 0.1,
            sustain = sustain ?: 1.0,
            release = release ?: 0.1,
            depth = depth ?: 0.5,
        )
    }

    companion object {
        /**
         * Default filter envelope settings.
         */
        val default = FilterEnvelope(
            attack = 0.01,
            decay = 0.1,
            sustain = 1.0,
            release = 0.1,
            depth = 0.5,
        )
    }
}