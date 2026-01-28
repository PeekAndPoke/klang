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
        @Serializable
        data class Band(
            val freq: Double,
            val db: Double,
            val q: Double,
        )
    }
}
