package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

@Serializable
data class AdsrEnvelope(
    val attack: Double? = null,
    val decay: Double? = null,
    val sustain: Double? = null,
    val release: Double? = null,
) {
    companion object {
        /** Empty envelope */
        val empty = AdsrEnvelope(
            attack = null,
            decay = null,
            sustain = null,
            release = null,
        )

        /** Standard Synth defaults (Organ-like) */
        val defaultSynth = AdsrEnvelope(
            attack = 0.01,
            decay = 0.1,
            sustain = 1.0,
            release = 0.05,
        )
    }

    /**
     * Resolved ADSR
     */
    data class Resolved(
        val attack: Double,
        val decay: Double,
        val sustain: Double,
        val release: Double,
    )

    /**
     * Merges this envelope with a fallback envelope.
     * Values in this take precedence. If null, values from [other] are used.
     */
    fun mergeWith(other: AdsrEnvelope?): AdsrEnvelope {
        if (other == null) return this

        return AdsrEnvelope(
            attack = this.attack ?: other.attack,
            decay = this.decay ?: other.decay,
            sustain = this.sustain ?: other.sustain,
            release = this.release ?: other.release,
        )
    }

    /**
     * Resolves to non-nullable values, using provided defaults as final fallback.
     */
    fun resolve(defaults: AdsrEnvelope = defaultSynth): Resolved {
        return Resolved(
            attack = this.attack ?: defaults.attack ?: 0.01,
            decay = this.decay ?: defaults.decay ?: 0.1,
            sustain = this.sustain ?: defaults.sustain ?: 1.0,
            release = this.release ?: defaults.release ?: 0.1
        )
    }
}
