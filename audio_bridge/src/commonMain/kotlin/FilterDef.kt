package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class FilterDef {
    @Serializable
    @SerialName("low-pass")
    data class LowPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("high-pass")
    data class HighPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("band-pass")
    data class BandPass(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("notch")
    data class Notch(
        val cutoffHz: Double,
        val q: Double?,
        val envelope: FilterEnvelope? = null,
    ) : FilterDef()

    @Serializable
    @SerialName("formant")
    data class Formant(
        val bands: List<Band>,
    ) : FilterDef() {
        /**
         * One formant band — a single SVF bandpass tuned to a vowel formant peak.
         *
         * **Gain semantic (constant-skirt SVF convention):** the actual peak gain at
         * `freq` is `Q · 10^(db/20)`. The user-facing `db` is *additional* gain on top
         * of the BPF's intrinsic Q peak — a band with `db = 0, q = 10` produces
         * **+20 dB** at `freq`, not 0 dB. F1 is conventionally `db = 0`; upper formants
         * use negative dB to compensate for their own Q-driven peak.
         *
         * **Q range**: SVF accepts `q ∈ [0.1, 200.0]`. Vowel tables typically use 60–130.
         */
        @Serializable
        data class Band(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }
}
